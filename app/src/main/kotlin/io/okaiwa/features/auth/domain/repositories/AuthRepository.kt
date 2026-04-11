package io.okaiwa.features.auth.domain.repositories

import io.okaiwa.features.auth.domain.entities.User
import kotlinx.coroutines.flow.Flow

/**
 * Authentication repository interface.
 *
 * Defines the contract for registration, login, session management, and
 * cryptographic identity operations. Implementations handle API calls,
 * local key storage, and Signal Protocol key bundle generation.
 */
interface AuthRepository {

    /**
     * Observe the currently authenticated user.
     * Emits null when no user is signed in.
     */
    fun observeCurrentUser(): Flow<User?>

    /**
     * Request an OTP code for the given phone number.
     * The phone is hashed client-side before transmission.
     *
     * @param phoneNumber E.164 formatted phone number.
     * @return Session token for OTP verification.
     */
    suspend fun requestOtp(phoneNumber: String): String

    /**
     * Verify the OTP code and complete registration.
     * Generates Signal Protocol key bundles and uploads pre-keys.
     *
     * @param sessionToken Token from [requestOtp].
     * @param otpCode The 6-digit OTP code.
     * @return Authenticated [User].
     */
    suspend fun verifyOtp(sessionToken: String, otpCode: String): User

    /**
     * Register a new device for an existing user.
     * Generates a new identity key pair and registration ID.
     *
     * @param phoneNumber E.164 formatted phone number.
     * @return Authenticated [User] with new device ID.
     */
    suspend fun registerDevice(phoneNumber: String): User

    /**
     * Refresh the authentication token.
     *
     * @return New access token.
     */
    suspend fun refreshToken(): String

    /**
     * Upload a batch of Signal Protocol pre-keys to the server.
     *
     * @param preKeys Serialized pre-key bundle.
     */
    suspend fun uploadPreKeys(preKeys: ByteArray)

    /**
     * Sign out and clear all local session data.
     * Does NOT delete encryption keys (those require explicit account deletion).
     */
    suspend fun signOut()

    /**
     * Delete the account and all associated data from the server.
     * Wipes local keys and database.
     */
    suspend fun deleteAccount()
}
