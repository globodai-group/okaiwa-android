package io.okaiwa.features.auth.domain.entities

import kotlinx.serialization.Serializable

/**
 * Authenticated user entity.
 *
 * Represents the current user's identity and cryptographic state.
 * The [phoneHash] is a SHA-256 hash of the E.164 phone number — the server
 * never receives the plaintext phone number.
 */
@Serializable
data class User(
    val id: String,
    val phoneNumberHash: String,
    val username: String,
    val displayName: String?,
    val avatarUrl: String?,
    val identityPublicKey: String,
    val signedPreKeyId: Int,
    val deviceId: Int,
    val createdAt: Long,
    val lastPreKeyUpload: Long? = null,
) {
    /**
     * Whether the user has completed profile setup (username chosen).
     */
    val isProfileComplete: Boolean
        get() = username.isNotBlank()
}
