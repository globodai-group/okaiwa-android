package io.okaiwa.features.auth.data.repositories

import io.okaiwa.core.errors.AppError
import io.okaiwa.core.errors.toAppError
import io.okaiwa.features.auth.data.crypto.PhoneHasher
import io.okaiwa.features.auth.data.crypto.SignalIdentityKeys
import io.okaiwa.features.auth.data.remote.AuthApi
import io.okaiwa.features.auth.data.remote.RefreshRequest
import io.okaiwa.features.auth.data.remote.RegisterRequest
import io.okaiwa.features.auth.data.remote.SessionTokenResponse
import io.okaiwa.features.auth.data.remote.VerifyRequest
import io.okaiwa.features.auth.data.session.Session
import io.okaiwa.features.auth.data.session.SessionStore
import io.okaiwa.features.auth.domain.entities.User
import io.okaiwa.features.auth.domain.repositories.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production-path implementation of [AuthRepository] — speaks to the
 * identity service deployed at `AppConfig.environment.apiBaseUrl`.
 *
 * Flow summary:
 *   1. `requestOtp(phoneE164)` — hashes the number, generates a Signal
 *      key bundle, calls `POST /v1/auth/register`. The backend creates
 *      an unverified account and triggers OTP delivery (Brevo SMS +
 *      email in production; bypass `000000` in dev). We stash the
 *      phone hash in the session store under an "in-flight" record so
 *      [verifyOtp] can re-submit it.
 *   2. `verifyOtp(_, code)` — calls `POST /v1/auth/verify` with the
 *      stashed phone hash + the code. On success the response carries
 *      the opaque access + refresh tokens which we persist.
 *   3. `refreshToken()` — posts the refresh token to `POST /v1/auth/
 *      refresh` and rotates the stored session.
 *
 * The Signal key bundle is generated on-device by [SignalIdentityKeys],
 * which wraps libsignal-android (real Curve25519 identity keys + signed
 * pre-key + one-time pre-key pool). The keys are persisted in an
 * encrypted store so the same identity survives app restarts — peers
 * who have cached our PreKeyBundle can still establish a session.
 */
@Singleton
class RemoteAuthRepository @Inject constructor(
    private val api: AuthApi,
    private val sessionStore: SessionStore,
    private val signalIdentityKeys: SignalIdentityKeys,
) : AuthRepository {

    override fun observeCurrentUser(): Flow<User?> =
        sessionStore.sessionFlow.map { it?.toUserStub() }

    /**
     * Opens a registration: hashes the phone, generates a Signal key
     * bundle and calls `/v1/auth/register`. Stashes the phone hash so
     * [verifyOtp] can re-use it. The session token string we return
     * here is a synthetic placeholder — the UI only needs something
     * non-empty to navigate to the OTP screen, and verify uses the
     * stashed hash rather than this value.
     */
    override suspend fun requestOtp(phoneNumber: String): String {
        val phoneHash = PhoneHasher.hashE164(phoneNumber)
        // Real libsignal Curve25519 identity bundle. `registrationBundle()`
        // generates fresh keys on first launch and persists them; later
        // calls hydrate the cached ones so re-registering uses the same
        // device identity.
        val bundle = signalIdentityKeys.registrationBundle()

        val response = api.register(
            RegisterRequest(
                phoneHash = phoneHash,
                identityPublicKey = bundle.identityPublicKey,
                signedPreKey = bundle.signedPreKey,
                registrationId = bundle.registrationId,
                username = null,
            )
        )

        val body = response.requireBody { "register" }

        sessionStore.save(
            Session(
                accountId = body.accountId,
                phoneHash = phoneHash,
                accessToken = "",
                refreshToken = "",
                expiresAtEpochSeconds = 0L,
                deviceId = body.deviceId ?: "",
                deviceToken = "",
            )
        )

        return body.accountId
    }

    override suspend fun verifyOtp(sessionToken: String, otpCode: String): User {
        val pending = sessionStore.current()
            ?: throw AppError.Auth.InvalidCredentials("No pending registration")

        val response = api.verify(VerifyRequest(phoneHash = pending.phoneHash, code = otpCode))
        val body = response.requireBody { "verify" }

        persistSession(pending.accountId, pending.phoneHash, body)

        return Session(
            accountId = pending.accountId,
            phoneHash = pending.phoneHash,
            accessToken = body.sessionToken,
            refreshToken = body.refreshToken,
            expiresAtEpochSeconds = (System.currentTimeMillis() / 1000L) + body.expiresIn,
            deviceId = body.deviceId ?: pending.deviceId,
            deviceToken = body.deviceToken ?: "",
        ).toUserStub()
    }

    override suspend fun registerDevice(phoneNumber: String): User {
        // Same shape as the first registration — the backend doesn't
        // yet distinguish device re-registration from a brand-new
        // account. Once we add the multi-device endpoint on the server
        // (POST /v1/auth/device/register), this method will call it.
        requestOtp(phoneNumber)
        throw AppError.Auth.RegistrationFailed("Device re-registration requires OTP verification")
    }

    override suspend fun refreshToken(): String {
        val pending = sessionStore.current()
            ?: throw AppError.Auth.SessionExpired()

        val response = api.refresh(RefreshRequest(refreshToken = pending.refreshToken))
        val body = response.requireBody { "refresh" }

        persistSession(pending.accountId, pending.phoneHash, body)
        return body.sessionToken
    }

    override suspend fun uploadPreKeys(preKeys: ByteArray) {
        // Pre-key upload routes (POST /v1/keys/prekeys) land alongside
        // the libsignal FFI — the random bundle we register with isn't
        // usable for session setup anyway, so there is nothing useful
        // to upload yet.
    }

    override suspend fun signOut() {
        sessionStore.clear()
    }

    override suspend fun deleteAccount() {
        // DELETE /v1/profile + DELETE /v1/auth/account land on the
        // server next. For now sign-out is the user-facing effect.
        sessionStore.clear()
    }

    private fun persistSession(
        accountId: String,
        phoneHash: String,
        tokens: SessionTokenResponse,
    ) {
        val previous = sessionStore.current()
        sessionStore.save(
            Session(
                accountId = accountId,
                phoneHash = phoneHash,
                accessToken = tokens.sessionToken,
                refreshToken = tokens.refreshToken,
                expiresAtEpochSeconds = (System.currentTimeMillis() / 1000L) + tokens.expiresIn,
                // Refresh re-issues the session token but NOT the
                // deviceToken (which is long-lived per the relay's
                // MAX_TOKEN_AGE_SECONDS check). Keep the existing one.
                deviceId = tokens.deviceId ?: previous?.deviceId.orEmpty(),
                deviceToken = tokens.deviceToken ?: previous?.deviceToken.orEmpty(),
            )
        )
    }

    /**
     * Unwrap a Retrofit response into its body or throw a typed
     * [AppError]. Keeps every remote call free of the 4xx / 5xx
     * branching boilerplate.
     */
    private inline fun <T> Response<T>.requireBody(endpoint: () -> String): T {
        if (!isSuccessful) {
            throw when (code()) {
                401, 403 -> AppError.Auth.InvalidCredentials("${endpoint()} → HTTP ${code()}")
                409 -> AppError.Auth.RegistrationFailed("Phone already registered")
                429 -> AppError.Server.RateLimited(retryAfterSeconds = 30)
                in 500..599 -> AppError.Server.Http(code(), errorBody()?.string())
                else -> AppError.Server.Http(code(), errorBody()?.string())
            }
        }
        return body() ?: throw AppError.Server.Http(code(), "Empty body from ${endpoint()}")
    }

    /**
     * Synthesize a minimal [User] from the persisted session. The real
     * user profile lands via `GET /v1/profile/:username` in a follow-up
     * commit — this stub keeps the observer contract satisfiable today.
     */
    private fun Session.toUserStub(): User = User(
        id = accountId,
        phoneNumberHash = phoneHash,
        username = "",
        displayName = null,
        avatarUrl = null,
        identityPublicKey = "",
        signedPreKeyId = 0,
        deviceId = 1,
        createdAt = System.currentTimeMillis() / 1000L,
    )
}

/**
 * Extend the AppError toAppError conversion for Retrofit-origin
 * exceptions. Used by call sites that wrap the repository in
 * runCatching {}.
 */
fun Throwable.toAuthAppError(): AppError = toAppError()
