package io.okaiwa.core.errors

/**
 * Sealed hierarchy of application errors.
 *
 * Provides type-safe error handling across the entire application.
 * Each error variant carries contextual information for debugging
 * and user-facing error messages.
 */
sealed class AppError(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    /** HTTP/API server errors. */
    sealed class Server(message: String, cause: Throwable? = null) : AppError(message, cause) {
        data class Http(val code: Int, val body: String?) :
            Server("HTTP $code: ${body ?: "Unknown error"}")

        data class Timeout(override val cause: Throwable? = null) :
            Server("Request timed out", cause)

        data class Unauthorized(val reason: String = "Session expired") :
            Server("Unauthorized: $reason")

        data class RateLimited(val retryAfterSeconds: Int) :
            Server("Rate limited. Retry after $retryAfterSeconds seconds.")
    }

    /** Cryptographic operation errors. */
    sealed class Crypto(message: String, cause: Throwable? = null) : AppError(message, cause) {
        data class KeyGenerationFailed(override val cause: Throwable? = null) :
            Crypto("Failed to generate cryptographic keys", cause)

        data class EncryptionFailed(override val cause: Throwable? = null) :
            Crypto("Message encryption failed", cause)

        data class DecryptionFailed(override val cause: Throwable? = null) :
            Crypto("Message decryption failed", cause)

        data class SignatureInvalid(val detail: String = "") :
            Crypto("Invalid cryptographic signature. $detail".trim())

        data class SessionNotFound(val recipientId: String) :
            Crypto("No Signal session found for $recipientId")
    }

    /** Network connectivity errors. */
    sealed class Network(message: String, cause: Throwable? = null) : AppError(message, cause) {
        data class NoConnection(override val cause: Throwable? = null) :
            Network("No internet connection", cause)

        data class DnsResolutionFailed(val hostname: String) :
            Network("DNS resolution failed for $hostname")

        data class SslPinningFailed(val hostname: String) :
            Network("Certificate pinning validation failed for $hostname")

        data class WebSocketDisconnected(val code: Int, val reason: String) :
            Network("WebSocket disconnected: $code $reason")
    }

    /** Local cache/storage errors. */
    sealed class Cache(message: String, cause: Throwable? = null) : AppError(message, cause) {
        data class DatabaseCorrupted(override val cause: Throwable? = null) :
            Cache("Encrypted database is corrupted", cause)

        data class MigrationFailed(val fromVersion: Int, val toVersion: Int) :
            Cache("Database migration failed from v$fromVersion to v$toVersion")

        data class DiskFull(override val cause: Throwable? = null) :
            Cache("Insufficient storage space", cause)
    }

    /** Authentication and authorization errors. */
    sealed class Auth(message: String, cause: Throwable? = null) : AppError(message, cause) {
        data class InvalidCredentials(val detail: String = "") :
            Auth("Invalid credentials. $detail".trim())

        data class BiometricFailed(val errorCode: Int, val errorMessage: String) :
            Auth("Biometric authentication failed: $errorMessage")

        data class SessionExpired(override val cause: Throwable? = null) :
            Auth("Session has expired. Please sign in again.", cause)

        data class RegistrationFailed(val reason: String) :
            Auth("Registration failed: $reason")

        data class KeystoreUnavailable(override val cause: Throwable? = null) :
            Auth("Android Keystore is unavailable", cause)
    }
}

/**
 * Extension to convert any [Throwable] into an [AppError].
 * Unknown exceptions are wrapped as [AppError.Server.Http] with code 0.
 */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is java.net.UnknownHostException -> AppError.Network.NoConnection(this)
    is java.net.SocketTimeoutException -> AppError.Server.Timeout(this)
    is javax.net.ssl.SSLPeerUnverifiedException -> AppError.Network.SslPinningFailed(message ?: "unknown")
    else -> AppError.Server.Http(0, message)
}
