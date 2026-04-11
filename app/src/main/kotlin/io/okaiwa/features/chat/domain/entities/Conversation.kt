package io.okaiwa.features.chat.domain.entities

import kotlinx.serialization.Serializable

/**
 * Conversation entity.
 *
 * Represents a 1:1 or group conversation. Contains metadata about
 * participants, last message preview, and encryption state.
 */
@Serializable
data class Conversation(
    val id: String,
    val type: ConversationType,
    val title: String?,
    val avatarUrl: String?,
    val participants: List<Participant>,
    val lastMessage: MessagePreview?,
    val unreadCount: Int = 0,
    val isMuted: Boolean = false,
    val isPinned: Boolean = false,
    val isArchived: Boolean = false,
    val muteExpiresAt: Long? = null,
    val disappearingDurationSeconds: Long? = null,
    val folder: ConversationFolder = ConversationFolder.All,
    val createdAt: Long,
    val updatedAt: Long,
) {
    /**
     * Display title: conversation title for groups, participant name for 1:1.
     */
    val displayTitle: String
        get() = title ?: participants.firstOrNull()?.displayName ?: "Unknown"

    /**
     * Whether this is a verified conversation (all participants have verified keys).
     */
    val isFullyVerified: Boolean
        get() = participants.all { it.isKeyVerified }
}

@Serializable
enum class ConversationType {
    OneToOne,
    Group,
}

/**
 * Conversation participant.
 */
@Serializable
data class Participant(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val role: ParticipantRole = ParticipantRole.Member,
    val isKeyVerified: Boolean = false,
    val joinedAt: Long,
) {
    val isAdmin: Boolean
        get() = role == ParticipantRole.Admin || role == ParticipantRole.Owner
}

@Serializable
enum class ParticipantRole {
    Owner,
    Admin,
    Member,
}

/**
 * Lightweight message preview for conversation list display.
 */
@Serializable
data class MessagePreview(
    val messageId: String,
    val senderName: String?,
    val content: String,
    val type: MessageType = MessageType.Text,
    val timestamp: Long,
)

/**
 * Organizational folder for conversations (aligned with iOS).
 */
@Serializable
enum class ConversationFolder {
    All,
    Personal,
    Work,
    Crypto,
    Archived,
}
