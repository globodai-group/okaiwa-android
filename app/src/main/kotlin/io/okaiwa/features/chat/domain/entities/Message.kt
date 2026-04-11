package io.okaiwa.features.chat.domain.entities

import kotlinx.serialization.Serializable

/**
 * Chat message entity.
 *
 * Represents a single message within a conversation. Messages are always
 * stored encrypted locally (SQLCipher) and transmitted encrypted (Signal Protocol).
 * The [plaintextContent] is only available after decryption in memory.
 */
@Serializable
data class Message(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val plaintextContent: String?,
    val encryptedPayload: ByteArray? = null,
    val type: MessageType = MessageType.Text,
    val status: MessageStatus = MessageStatus.Sending,
    val replyToMessageId: String? = null,
    val attachments: List<Attachment> = emptyList(),
    val disappearingDurationSeconds: Long? = null,
    val sentAt: Long,
    val deliveredAt: Long? = null,
    val readAt: Long? = null,
    val editedAt: Long? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Message) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

@Serializable
enum class MessageType {
    Text,
    Image,
    Video,
    Audio,
    VoiceNote,
    File,
    Location,
    Contact,
    CryptoPayment,
    System,
}

@Serializable
enum class MessageStatus {
    Sending,
    Sent,
    Delivered,
    Read,
    Failed,
}

/**
 * File attachment metadata.
 */
@Serializable
data class Attachment(
    val id: String,
    val fileName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val encryptedUrl: String?,
    val thumbnailUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val encryptionKey: ByteArray? = null,
    val digest: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Attachment) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
