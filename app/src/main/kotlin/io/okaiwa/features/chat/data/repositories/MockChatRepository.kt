package io.okaiwa.features.chat.data.repositories

import io.okaiwa.features.chat.domain.entities.Conversation
import io.okaiwa.features.chat.domain.entities.ConversationType
import io.okaiwa.features.chat.domain.entities.Message
import io.okaiwa.features.chat.domain.entities.MessagePreview
import io.okaiwa.features.chat.domain.entities.MessageStatus
import io.okaiwa.features.chat.domain.entities.MessageType
import io.okaiwa.features.chat.domain.entities.Participant
import io.okaiwa.features.chat.domain.repositories.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock [ChatRepository] shipping a realistic conversation graph so the
 * UX can be exercised end-to-end without the identity + relay services
 * wired up.
 *
 * Everything here is compiled into the debug APK only — once the real
 * HTTP + Signal Protocol clients land, this file is replaced with the
 * production repository, and the `@Binds` in `RepositoryModule` is
 * switched to point at it.
 *
 * The fixture emphasizes the kinds of rows we want to stress-test in
 * the design review:
 *   - Short and long preview text (ellipsis behaviour)
 *   - Unread counts (1, multi-digit, 99+)
 *   - Pinned vs regular ordering
 *   - Crypto payment preview (TypeBox icon on the wallet flow)
 *   - Voice note, image, and system-message previews
 *   - Mix of French and English senders to reflect the user base
 */
@Singleton
class MockChatRepository @Inject constructor() : ChatRepository {

    private val conversationsFlow = MutableStateFlow(seedConversations())
    private val messagesByConversation: MutableMap<String, MutableStateFlow<List<Message>>> =
        mutableMapOf()

    override fun observeConversations(): Flow<List<Conversation>> = conversationsFlow.asStateFlow()

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        messagesFlowFor(conversationId).asStateFlow()

    override suspend fun sendMessage(
        conversationId: String,
        content: String,
        replyToMessageId: String?,
    ): Message {
        val message = Message(
            id = "m-${System.currentTimeMillis()}",
            conversationId = conversationId,
            senderId = ME_USER_ID,
            plaintextContent = content,
            type = MessageType.Text,
            status = MessageStatus.Sent,
            replyToMessageId = replyToMessageId,
            sentAt = System.currentTimeMillis(),
        )
        messagesFlowFor(conversationId).update { it + message }

        // Bubble the new message into the conversation list preview.
        conversationsFlow.update { list ->
            list.map { conv ->
                if (conv.id == conversationId) {
                    conv.copy(
                        lastMessage = MessagePreview(
                            messageId = message.id,
                            senderName = "Moi",
                            content = content,
                            timestamp = message.sentAt,
                        ),
                        updatedAt = message.sentAt,
                    )
                } else conv
            }.sortedByDescending { it.updatedAt }
        }
        return message
    }

    override suspend fun sendCryptoPaymentMessage(
        conversationId: String,
        transactionHash: String,
        chain: String,
        amount: String,
        tokenSymbol: String,
    ): Message {
        val message = Message(
            id = "m-tx-${System.currentTimeMillis()}",
            conversationId = conversationId,
            senderId = ME_USER_ID,
            plaintextContent = "$amount $tokenSymbol",
            type = MessageType.CryptoPayment,
            status = MessageStatus.Sent,
            sentAt = System.currentTimeMillis(),
        )
        messagesFlowFor(conversationId).update { it + message }
        return message
    }

    override suspend fun markAsRead(conversationId: String) {
        conversationsFlow.update { list ->
            list.map { conv ->
                if (conv.id == conversationId) conv.copy(unreadCount = 0) else conv
            }
        }
    }

    override suspend fun deleteMessage(messageId: String, forEveryone: Boolean) {
        messagesByConversation.values.forEach { flow ->
            flow.update { it.filterNot { m -> m.id == messageId } }
        }
    }

    override suspend fun createConversation(recipientUserId: String): Conversation {
        val now = System.currentTimeMillis()
        val conversation = Conversation(
            id = "c-new-$now",
            type = ConversationType.OneToOne,
            title = null,
            avatarUrl = null,
            participants = listOf(
                Participant(
                    userId = recipientUserId,
                    displayName = "Nouveau contact",
                    avatarUrl = null,
                    joinedAt = now,
                ),
            ),
            lastMessage = null,
            unreadCount = 0,
            createdAt = now,
            updatedAt = now,
        )
        conversationsFlow.update { listOf(conversation) + it }
        return conversation
    }

    override suspend fun loadOlderMessages(
        conversationId: String,
        beforeMessageId: String,
        limit: Int,
    ): List<Message> = emptyList()

    override suspend fun setDisappearingTimer(conversationId: String, durationSeconds: Long) {
        conversationsFlow.update { list ->
            list.map { conv ->
                if (conv.id == conversationId) {
                    conv.copy(disappearingDurationSeconds = durationSeconds.takeIf { it > 0 })
                } else conv
            }
        }
    }

    private fun messagesFlowFor(conversationId: String): MutableStateFlow<List<Message>> =
        messagesByConversation.getOrPut(conversationId) {
            MutableStateFlow(seedMessages(conversationId))
        }

    // ------------------------------------------------------------------
    // Fixtures
    // ------------------------------------------------------------------

    private companion object {
        const val ME_USER_ID = "me"
        val now: Long get() = System.currentTimeMillis()
        const val MINUTE = 60_000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR
    }

    private fun seedConversations(): List<Conversation> {
        val t = now
        return listOf(
            conv(
                id = "c-marie",
                name = "Marie Dubois",
                lastText = "À demain à 19h alors 😊",
                lastAt = t - 3 * MINUTE,
                unread = 2,
                pinned = true,
            ),
            conv(
                id = "c-chris",
                name = "Chris Sabian",
                lastText = "Envoyé 250 USDC ✅",
                lastAt = t - 14 * MINUTE,
                unread = 0,
                pinned = true,
                previewType = MessageType.CryptoPayment,
            ),
            conv(
                id = "c-team",
                name = "Team Okaiwa",
                lastText = "Leo: on se voit en visio cet après-midi ?",
                lastAt = t - 42 * MINUTE,
                unread = 5,
                type = ConversationType.Group,
            ),
            conv(
                id = "c-hao",
                name = "Hao Tensei",
                lastText = "Tu as lu le whitepaper ? Je trouve que la section 4 mérite d'être reprise parce que la description de l'architecture zero-knowledge manque de précision.",
                lastAt = t - 2 * HOUR,
                unread = 1,
            ),
            conv(
                id = "c-oussama",
                name = "Oussama",
                lastText = "🎉🎉",
                lastAt = t - 4 * HOUR,
                unread = 0,
            ),
            conv(
                id = "c-payrolless",
                name = "Payrolless - Cortx",
                lastText = "Vous venez de recevoir une nouvelle candidature.",
                lastAt = t - 6 * HOUR,
                unread = 27,
            ),
            conv(
                id = "c-fidelio",
                name = "Board FIDELIO",
                lastText = "Denis: mission accomplie 🔥",
                lastAt = t - 9 * HOUR,
                unread = 0,
                type = ConversationType.Group,
            ),
            conv(
                id = "c-tapbit",
                name = "TapbitBotX",
                lastText = "Parfait ! J'ai coupé l'automatisation X comme demandé.",
                lastAt = t - DAY,
                unread = 0,
            ),
            conv(
                id = "c-leader",
                name = "LEADER DIABY",
                lastText = "Soyez disponibles, on va lancer le nouveau module.",
                lastAt = t - DAY - 2 * HOUR,
                unread = 0,
            ),
            conv(
                id = "c-signal",
                name = "Okaiwa",
                lastText = "Bienvenue sur Okaiwa. Vos messages sont chiffrés de bout en bout.",
                lastAt = t - 2 * DAY,
                unread = 0,
                type = ConversationType.Group,
            ),
            conv(
                id = "c-luca",
                name = "Luca Ferrari",
                lastText = "Voice note",
                lastAt = t - 3 * DAY,
                unread = 0,
                previewType = MessageType.VoiceNote,
            ),
            conv(
                id = "c-sarah",
                name = "Sarah Benoît",
                lastText = "Photo",
                lastAt = t - 4 * DAY,
                unread = 0,
                previewType = MessageType.Image,
            ),
        )
    }

    private fun conv(
        id: String,
        name: String,
        lastText: String,
        lastAt: Long,
        unread: Int,
        pinned: Boolean = false,
        type: ConversationType = ConversationType.OneToOne,
        previewType: MessageType = MessageType.Text,
    ): Conversation = Conversation(
        id = id,
        type = type,
        title = if (type == ConversationType.Group) name else null,
        avatarUrl = null,
        participants = listOf(
            Participant(
                userId = "u-$id",
                displayName = name,
                avatarUrl = null,
                joinedAt = lastAt,
            ),
        ),
        lastMessage = MessagePreview(
            messageId = "mp-$id",
            senderName = null,
            content = lastText,
            type = previewType,
            timestamp = lastAt,
        ),
        unreadCount = unread,
        isPinned = pinned,
        createdAt = lastAt - 7 * DAY,
        updatedAt = lastAt,
    )

    private fun seedMessages(conversationId: String): List<Message> {
        val t = now
        val them = conversationId.removePrefix("c-")
        return listOf(
            msg(conversationId, them, "Hey ! Tu as testé la beta ?", t - 2 * HOUR, mine = false),
            msg(conversationId, them, "Oui, franchement le design est propre. La bar flottante est classe.", t - 2 * HOUR + MINUTE, mine = true),
            msg(conversationId, them, "Carrément ! Et le wallet intégré dans les chats, c'est le move.", t - 2 * HOUR + 2 * MINUTE, mine = false),
            msg(conversationId, them, "On pourra envoyer de l'USDC entre potes sans avoir à copier-coller d'adresse.", t - 2 * HOUR + 3 * MINUTE, mine = false),
            msg(conversationId, them, "Exactement l'idée. Signal + Telegram + TrustWallet en un.", t - HOUR - 40 * MINUTE, mine = true),
            msg(conversationId, them, "J'attends la release stable 🚀", t - HOUR, mine = false),
            msg(conversationId, them, "Début de la semaine pro normalement.", t - 30 * MINUTE, mine = true),
            msg(conversationId, them, "Tu me tiens au courant ?", t - 10 * MINUTE, mine = false),
            msg(conversationId, them, "Évidemment 💪", t - 5 * MINUTE, mine = true),
        )
    }

    private fun msg(
        conversationId: String,
        otherId: String,
        content: String,
        at: Long,
        mine: Boolean,
    ): Message = Message(
        id = "m-${conversationId}-$at",
        conversationId = conversationId,
        senderId = if (mine) ME_USER_ID else otherId,
        plaintextContent = content,
        type = MessageType.Text,
        status = MessageStatus.Read,
        sentAt = at,
        deliveredAt = at,
        readAt = at,
    )
}
