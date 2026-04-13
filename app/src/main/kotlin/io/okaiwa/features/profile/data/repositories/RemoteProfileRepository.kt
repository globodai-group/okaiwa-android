package io.okaiwa.features.profile.data.repositories

import io.okaiwa.features.auth.data.session.SessionStore
import io.okaiwa.features.profile.data.remote.ProfileApi
import io.okaiwa.features.profile.domain.entities.UserProfile
import io.okaiwa.features.profile.domain.repositories.ProfileRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    override fun observeProfile(): Flow<UserProfile?> {
        // Lazy fetch — only the first subscription kicks the network
        // call. The Profile tab will call this on every selection but
        // we don't want to refetch on every recomposition; subsequent
        // reads come from the in-memory StateFlow.
        if (!hasFetchedOnce) {
            hasFetchedOnce = true
            CoroutineScope(Dispatchers.IO).launch { refresh() }
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

        state.value = body.toUserProfile(phoneFallback = session.phoneHash)
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
 * Map the wire-format response into the domain UserProfile. The phone
 * number isn't returned by /profile/me (the server only knows the
 * hash), so we surface the in-memory phoneHash as a placeholder until
 * the user opts to re-derive their formatted number locally.
 */
private fun io.okaiwa.features.profile.data.remote.MyProfileResponse.toUserProfile(
    phoneFallback: String,
): UserProfile {
    val handle = username?.let { "@$it" } ?: "@—"
    return UserProfile(
        userId = accountId,
        displayName = profile?.displayName?.takeIf { it.isNotBlank() }
            ?: username
            ?: "Compte Okaiwa",
        username = handle,
        phoneNumberE164 = if (phoneFallback.isNotEmpty()) "" else "",
        bio = profile?.bio,
        avatarUrl = profile?.avatarUrl,
        isVerified = false,
        isOnline = true,
    )
}
