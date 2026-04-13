package io.okaiwa.features.profile.data.repositories

import io.okaiwa.features.auth.data.session.SessionStore
import io.okaiwa.features.profile.data.remote.ProfileApi
import io.okaiwa.features.profile.domain.entities.UserProfile
import io.okaiwa.features.profile.domain.repositories.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production-path [ProfileRepository] — backs the Profile tab with the
 * real `/v1/profile/me` data instead of the placeholder text the
 * MockProfileRepository surfaced (Kevin / @asmista / Globodai bio).
 *
 * Strategy:
 *   - In-memory StateFlow as the single source of truth for the UI.
 *   - On first subscription (and on every refresh()) we call
 *     `GET /v1/profile/me` and overwrite the flow.
 *   - Mutations (setDisplayName / setBio / setUsername) call
 *     `PUT /v1/profile` and merge the server response back into the
 *     flow on success — local optimistic update would be wrong here
 *     because we want the canonical server-side value (server may
 *     normalize, lowercase, reject for collision).
 *
 * Auth: every authenticated call sends `Authorization: Bearer
 * <accessToken>`. The token is the HMAC-signed value minted by
 * /v1/auth/verify; the SessionAuthMiddleware on the server validates
 * it and derives accountId so the client never sends accountId in
 * a header (which would be a forgeable bypass).
 */
@Singleton
class RemoteProfileRepository @Inject constructor(
    private val api: ProfileApi,
    private val sessionStore: SessionStore,
) : ProfileRepository {

    private val state = MutableStateFlow<UserProfile?>(null)
    private var hasFetchedOnce = false

    /**
     * Owned scope so we can cancel any in-flight `refresh()` the
     * moment the user signs out. Without this, a PUT /profile or
     * GET /profile/me started just before clear() would land its
     * response into `state.value` AFTER the wipe — resurrecting the
     * previous user's data on the next observer (cross-account leak,
     * P1 flagged in the security review on the logout commit).
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var refreshJob: Job? = null

    init {
        // Auto-clear the cache as soon as the SessionStore signals a
        // sign-out. Same trigger covers both the kebab-menu Logout and
        // any future "session expired" path that calls clear() —
        // single source of truth for the cache lifecycle.
        scope.launch {
            sessionStore.sessionFlow.collect { session ->
                if (session == null) {
                    refreshJob?.cancel()
                    state.value = null
                    hasFetchedOnce = false
                }
            }
        }
    }

    override fun observeProfile(): Flow<UserProfile?> {
        // Lazy fetch — only the first subscription kicks the network
        // call. The Profile tab will call this on every selection but
        // we don't want to refetch on every recomposition; subsequent
        // reads come from the in-memory StateFlow.
        if (!hasFetchedOnce) {
            hasFetchedOnce = true
            refreshJob = scope.launch { refresh() }
        }
        return state.asStateFlow()
    }

    override suspend fun setDisplayName(newName: String) {
        push(displayName = newName)
    }

    override suspend fun setBio(newBio: String) {
        push(bio = newBio.takeIf { it.isNotBlank() } ?: "")
    }

    override suspend fun setUsername(newUsername: String) {
        // Strip the @ prefix the UI carries so the server validator
        // sees just the alphanumeric handle.
        val normalized = newUsername.removePrefix("@")
        push(username = normalized)
    }

    override suspend fun setAvatar(jpegBytes: ByteArray) {
        // Avatar upload uses POST /v1/profile/avatar (multipart) which
        // isn't wired yet — the bytes need to land in the encrypted
        // attachment store first. Tracked separately.
    }

    override suspend fun removeAvatar() {
        push(avatarUrl = "")
    }

    override suspend fun refresh() {
        fetchAndCache()
    }

    /**
     * Eager fetch used by the OnboardingFlow right after a successful
     * /v1/auth/verify to decide whether to land on Main (profile
     * already exists server-side — reinstall / multi-device) or
     * ProfileSetup (first-time account).
     *
     * Typed result matters here: folding 401/network-failure into the
     * same "no username" bucket that 404 uses would push the user
     * through ProfileSetup on a transient failure and immediately hit
     * HTTP 409 on the username PUT — exactly the bug this bootstrap
     * exists to prevent.
     *
     *   - [Existing] — server returned a non-blank username, user goes
     *     straight to Main. The cache is populated as a side-effect.
     *   - [NewUser]  — server returned 404 or 200 with null/blank
     *     username, user goes through ProfileSetup.
     *   - [AuthFailure]  — token was rejected (401/403). Block
     *     navigation; the OTP screen will surface a re-auth path.
     *   - [TransientFailure] — network / 5xx / parse error. Block
     *     navigation; same OTP screen retry path as above.
     */
    sealed class ProfileBootstrap {
        data class Existing(val username: String) : ProfileBootstrap()
        data object NewUser : ProfileBootstrap()
        data object AuthFailure : ProfileBootstrap()
        data object TransientFailure : ProfileBootstrap()
    }

    suspend fun fetchUsernameForBootstrap(): ProfileBootstrap {
        val session = sessionStore.current() ?: return ProfileBootstrap.AuthFailure
        val accessToken = session.accessToken.takeIf { it.isNotEmpty() }
            ?: return ProfileBootstrap.AuthFailure

        val response = runCatching {
            api.getMyProfile(bearer = "Bearer $accessToken")
        }.getOrNull() ?: return ProfileBootstrap.TransientFailure

        return when {
            response.code() == 401 || response.code() == 403 -> ProfileBootstrap.AuthFailure
            response.code() == 404 -> ProfileBootstrap.NewUser
            !response.isSuccessful -> ProfileBootstrap.TransientFailure
            else -> {
                val body = response.body() ?: return ProfileBootstrap.TransientFailure
                // Cross-check the accountId echoed by the server against
                // the one we derived from the token at verify time. If
                // they don't match, something is very wrong (proxy
                // mis-routing, replay, compromised backend) and
                // propagating someone else's profile into the local
                // cache would be a cross-account leak.
                if (session.accountId.isNotEmpty() &&
                    body.accountId != session.accountId
                ) {
                    return ProfileBootstrap.TransientFailure
                }
                state.value = body.toUserProfile(phoneE164 = session.phoneE164)
                val handle = body.username?.trim()?.takeIf { it.isNotBlank() }
                if (handle != null) ProfileBootstrap.Existing(handle) else ProfileBootstrap.NewUser
            }
        }
    }

    /**
     * Pull the canonical profile from the server and merge into the
     * in-memory flow. Called on first subscription and from refresh().
     */
    private suspend fun fetchAndCache() {
        val session = sessionStore.current() ?: return
        val accessToken = session.accessToken.takeIf { it.isNotEmpty() } ?: return

        val response = runCatching {
            api.getMyProfile(bearer = "Bearer $accessToken")
        }.getOrNull() ?: return

        if (!response.isSuccessful) return
        val body = response.body() ?: return

        state.value = body.toUserProfile(phoneE164 = session.phoneE164)
    }

    /**
     * Push a partial update (any subset of fields) and refresh the
     * local state with whatever the server returns. Returns silently
     * on transport / 4xx failure — the caller's UI surfaces the error
     * separately via the screen's submit handler.
     */
    private suspend fun push(
        username: String? = null,
        displayName: String? = null,
        bio: String? = null,
        avatarUrl: String? = null,
        visibility: String? = null,
    ) {
        val session = sessionStore.current() ?: return
        val accessToken = session.accessToken.takeIf { it.isNotEmpty() } ?: return

        val response = runCatching {
            api.updateProfile(
                bearer = "Bearer $accessToken",
                body = io.okaiwa.features.profile.data.remote.UpdateProfileRequest(
                    username = username,
                    displayName = displayName,
                    bio = bio,
                    avatarUrl = avatarUrl,
                    visibility = visibility,
                ),
            )
        }.getOrNull() ?: return

        if (!response.isSuccessful) return
        // Re-fetch the canonical state so any server-side normalization
        // (lowercase username, trimming) is reflected in the UI.
        fetchAndCache()
    }
}

/**
 * Map the wire-format response into the domain UserProfile.
 *
 * The phone number isn't returned by `/profile/me` — the server only
 * stores its SHA-256 hash. We surface the device-local copy stashed
 * in the SessionStore at register time so the Profile tab shows the
 * user's own number without a server roundtrip.
 */
private fun io.okaiwa.features.profile.data.remote.MyProfileResponse.toUserProfile(
    phoneE164: String,
): UserProfile {
    val handle = username?.let { "@$it" } ?: "@—"
    return UserProfile(
        userId = accountId,
        displayName = profile?.displayName?.takeIf { it.isNotBlank() }
            ?: username
            ?: "Compte Okaiwa",
        username = handle,
        phoneNumberE164 = phoneE164,
        bio = profile?.bio,
        avatarUrl = profile?.avatarUrl,
        isVerified = false,
        isOnline = true,
    )
}
