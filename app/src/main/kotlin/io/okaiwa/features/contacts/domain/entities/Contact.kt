package io.okaiwa.features.contacts.domain.entities

import kotlinx.serialization.Serializable

/**
 * Contact entity.
 *
 * Represents a contact discovered through the user's address book.
 * Phone numbers are hashed (SHA-256) before being sent to the server
 * for discovery — the server never sees plaintext phone numbers.
 */
@Serializable
data class Contact(
    val id: String,
    val phoneHash: String,
    val username: String? = null,
    val displayName: String,
    val avatarUrl: String?,
    val isRegistered: Boolean,
    val isBlocked: Boolean = false,
    val identityPublicKey: String? = null,
    val isKeyVerified: Boolean = false,
    val keyVerifiedAt: Long? = null,
    val lastSeenAt: Long? = null,
    val addedAt: Long = 0L,
) {
    /**
     * Whether the contact is an Okaiwa user who can receive encrypted messages.
     */
    val isReachable: Boolean
        get() = isRegistered && !isBlocked && identityPublicKey != null

    /**
     * Initials for avatar placeholder.
     */
    val initials: String
        get() = displayName
            .split(" ")
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercase() }
            .joinToString("")
}
