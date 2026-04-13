package io.okaiwa.features.chat.data.repositories

import io.okaiwa.features.chat.domain.entities.Conversation
import io.okaiwa.features.chat.domain.entities.Message
import io.okaiwa.features.chat.domain.entities.MessageStatus
import io.okaiwa.features.chat.domain.entities.MessageType
import io.okaiwa.features.chat.domain.repositories.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub implementation of [ChatRepository].
 *
 * Emits empty conversation and message lists. Send operations
 * return a synthetic "sent" message so the composer UI can be
 * exercised end-to-end in the test build.
 */
@Singleton
class StubChatRepository @Inject constructor() : ChatRepository {

    override fun observeConversations(): Flow<List<Conversation>> = flowOf(emptyList())

    override fun observeMessages(conversationId: String): Flow<List<Message>> = flowOf(emptyList())

    override suspend fun sendMessage(
        conversationId: String,
        content: String,
        replyToMessageId: String?,
    ): Message {
        return Message(
            id = "stub-${System.currentTimeMillis()}",
            conversationId = conversationId,
            senderId = "me",
            plaintextContent = content,
            type = MessageType.Text,
            status = MessageStatus.Sent,
            replyToMessageId = replyToMessageId,
            sentAt = System.currentTimeMillis(),
        )
    }

    override suspend fun sendCryptoPaymentMessage(
        conversationId: String,
        transactionHash: String,
        chain: String,
        amount: String,
        tokenSymbol: String,
    ): Message {
        return Message(
            id = "stub-tx-${System.currentTimeMillis()}",
            conversationId = conversationId,
            senderId = "me",
            plaintextContent = "$amount $tokenSymbol",
            type = MessageType.CryptoPayment,
            status = MessageStatus.Sent,
            sentAt = System.currentTimeMillis(),
        )
    }

    override suspend fun markAsRead(conversationId: String) {
        // no-op in stub
    }

    override suspend fun deleteMessage(messageId: String, forEveryone: Boolean) {
        // no-op in stub
    }

    override suspend fun createConversation(recipientUserId: String): Conversation {
        throw NotImplementedError("Conversation creation requires identity service wiring")
    }

    override suspend fun createConversationFromDiscovery(
        peer: io.okaiwa.features.discovery.data.remote.DiscoveredUser,
    ): Conversation = throw NotImplementedError("Stub doesn't support discovery-based creation")

    override suspend fun loadOlderMessages(
        conversationId: String,
        beforeMessageId: String,
        limit: Int,
    ): List<Message> = emptyList()

    override suspend fun setDisappearingTimer(conversationId: String, durationSeconds: Long) {
        // no-op in stub
    }
}
