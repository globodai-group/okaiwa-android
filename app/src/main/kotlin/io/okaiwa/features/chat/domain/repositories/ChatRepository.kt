package io.okaiwa.features.chat.domain.repositories

import io.okaiwa.features.chat.domain.entities.Conversation
import io.okaiwa.features.chat.domain.entities.Message
import kotlinx.coroutines.flow.Flow

/**
 * Chat repository interface.
 *
 * Defines the contract for conversation and message operations.
 * Implementations coordinate between local encrypted storage (Room + SQLCipher),
 * the API, and the WebSocket connection for real-time message delivery.
 */
interface ChatRepository {

    /**
     * Observe all conversations ordered by last activity.
     * Emits a new list whenever conversations are created, updated, or deleted.
     */
    fun observeConversations(): Flow<List<Conversation>>

    /**
     * Observe messages for a specific conversation in chronological order.
     * Emits new messages in real-time as they arrive via WebSocket.
     *
     * @param conversationId The conversation to observe.
     */
    fun observeMessages(conversationId: String): Flow<List<Message>>

    /**
     * Send an encrypted message.
     * The message is encrypted using the Signal Protocol session with each
     * recipient, then transmitted via the API and WebSocket.
     *
     * @param conversationId Target conversation.
     * @param content Plaintext message content.
     * @param replyToMessageId Optional message ID being replied to.
     * @return The sent [Message] with status [MessageStatus.Sent].
     */
    suspend fun sendMessage(
        conversationId: String,
        content: String,
        replyToMessageId: String? = null,
    ): Message

    /**
     * Send a crypto payment message.
     *
     * @param conversationId Target conversation.
     * @param transactionHash Blockchain transaction hash.
     * @param chain Blockchain network identifier.
     * @param amount Amount sent.
     * @param tokenSymbol Token symbol (e.g., "ETH", "USDC").
     * @return The sent [Message] with type [MessageType.CryptoPayment].
     */
    suspend fun sendCryptoPaymentMessage(
        conversationId: String,
        transactionHash: String,
        chain: String,
        amount: String,
        tokenSymbol: String,
    ): Message

    /**
     * Mark all messages in a conversation as read.
     * Sends read receipts to other participants.
     */
    suspend fun markAsRead(conversationId: String)

    /**
     * Delete a message locally. If [forEveryone] is true, also request
     * server-side deletion for all participants.
     */
    suspend fun deleteMessage(messageId: String, forEveryone: Boolean = false)

    /**
     * Create a new 1:1 conversation with the given user.
     * Establishes a Signal Protocol session if one doesn't exist.
     *
     * @param recipientUserId The other participant's user ID.
     * @return The created or existing [Conversation].
     */
    suspend fun createConversation(recipientUserId: String): Conversation

    /**
     * Load older messages for pagination.
     *
     * @param conversationId The conversation.
     * @param beforeMessageId Load messages before this message ID.
     * @param limit Maximum number of messages to load.
     * @return List of older messages.
     */
    suspend fun loadOlderMessages(
        conversationId: String,
        beforeMessageId: String,
        limit: Int = 50,
    ): List<Message>

    /**
     * Set the disappearing message timer for a conversation.
     *
     * @param conversationId The conversation.
     * @param durationSeconds Timer duration, or 0 to disable.
     */
    suspend fun setDisappearingTimer(conversationId: String, durationSeconds: Long)
}
