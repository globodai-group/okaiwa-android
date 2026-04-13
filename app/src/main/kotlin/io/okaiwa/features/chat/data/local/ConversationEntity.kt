package io.okaiwa.features.chat.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for a 1:1 conversation row.
 *
 * The peer's cryptographic identity (accountId / deviceId / registrationId /
 * identityKey base64) is duplicated here so the chat layer can build a
 * [org.signal.libsignal.protocol.SignalProtocolAddress] without a second
 * round-trip to discovery when the user taps an existing thread.
 *
 * All writes go through SQLCipher — see [OkaiwaDatabase].
 */
@Entity(
    tableName = "conversations",
    indices = [
        Index(value = ["peerAccountId"], unique = true),
        Index(value = ["peerDeviceId"]),
        Index(value = ["lastMessageAt"]),
    ],
)
data class ConversationEntity(
    @PrimaryKey val id: String,
    val peerAccountId: String,
    val peerUsername: String?,
    val peerDisplayName: String?,
    val peerDeviceId: String,
    val peerRegistrationId: Int,
    /** Base64-encoded Curve25519 identity public key of the peer. */
    val peerIdentityKey: String,
    val lastMessageId: String? = null,
    val lastMessagePreview: String? = null,
    val lastMessageAt: Long = 0L,
    val unreadCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)
