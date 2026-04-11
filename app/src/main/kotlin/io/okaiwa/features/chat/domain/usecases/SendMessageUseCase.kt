package io.okaiwa.features.chat.domain.usecases

import io.okaiwa.core.errors.AppError
import io.okaiwa.features.chat.domain.entities.Message
import io.okaiwa.features.chat.domain.repositories.ChatRepository
import javax.inject.Inject

/**
 * Use case for sending an encrypted message.
 *
 * Orchestrates the message sending flow:
 * 1. Validate message content (non-empty, within size limits).
 * 2. Encrypt the message using the Signal Protocol session.
 * 3. Store the message locally with "Sending" status.
 * 4. Transmit via API/WebSocket.
 * 5. Update local status to "Sent" on success or "Failed" on error.
 *
 * The actual encryption is handled by the repository implementation
 * which delegates to the Signal Protocol JNI layer.
 */
class SendMessageUseCase @Inject constructor(
    private val chatRepository: ChatRepository,
) {

    companion object {
        /** Maximum plaintext message length in characters. */
        const val MAX_MESSAGE_LENGTH = 10_000
    }

    /**
     * Send a text message to a conversation.
     *
     * @param conversationId Target conversation ID.
     * @param content Plaintext message content.
     * @param replyToMessageId Optional message being replied to.
     * @return Result containing the sent [Message].
     */
    suspend operator fun invoke(
        conversationId: String,
        content: String,
        replyToMessageId: String? = null,
    ): Result<Message> = runCatching {
        val trimmedContent = content.trim()

        if (trimmedContent.isEmpty()) {
            throw AppError.Crypto.EncryptionFailed(
                IllegalArgumentException("Message content cannot be empty")
            )
        }

        if (trimmedContent.length > MAX_MESSAGE_LENGTH) {
            throw AppError.Crypto.EncryptionFailed(
                IllegalArgumentException(
                    "Message exceeds maximum length of $MAX_MESSAGE_LENGTH characters"
                )
            )
        }

        chatRepository.sendMessage(
            conversationId = conversationId,
            content = trimmedContent,
            replyToMessageId = replyToMessageId,
        )
    }
}
