package io.okaiwa.features.profile.domain.entities

import kotlinx.serialization.Serializable

/**
 * User profile entity.
 *
 * Contains the user's public-facing profile information.
 * Profile data is encrypted at rest and only shared with
 * contacts who have a Signal Protocol session established.
 */
@Serializable
data class Profile(
    val userId: String,
    val displayName: String,
    val about: String?,
    val avatarUrl: String?,
    val avatarEncryptionKey: ByteArray? = null,
    val phoneHash: String,
    val identityPublicKey: String,
    val walletAddresses: List<String> = emptyList(),
    val isVerified: Boolean = false,
    val joinedAt: Long,
    val updatedAt: Long,
) {
    /**
     * Initials for avatar fallback.
     */
    val initials: String
        get() = displayName
            .split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercase() }
            .joinToString("")

    /**
     * Short identity key fingerprint for verification.
     * Displays the first 12 hex characters of the public key.
     */
    val shortFingerprint: String
        get() = identityPublicKey
            .take(12)
            .chunked(4)
            .joinToString(" ")

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Profile) return false
        return userId == other.userId && updatedAt == other.updatedAt
    }

    override fun hashCode(): Int = userId.hashCode()
}
