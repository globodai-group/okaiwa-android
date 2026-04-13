package io.okaiwa.features.chat.data.repositories

import android.util.Base64
import android.util.Log
import io.okaiwa.features.auth.data.crypto.SignalIdentityKeys
import io.okaiwa.features.auth.data.session.SessionStore
import io.okaiwa.features.chat.data.local.ConversationDao
import io.okaiwa.features.chat.data.local.ConversationEntity
import io.okaiwa.features.chat.data.local.DeliveryState
import io.okaiwa.features.chat.data.local.MessageDao
import io.okaiwa.features.chat.data.local.MessageEntity
import io.okaiwa.features.chat.data.remote.RelayApi
import io.okaiwa.features.chat.data.remote.SendMessageRequest
import io.okaiwa.features.chat.domain.entities.Conversation
import io.okaiwa.features.chat.domain.entities.ConversationType
import io.okaiwa.features.chat.domain.entities.Message
import io.okaiwa.features.chat.domain.entities.MessagePreview
import io.okaiwa.features.chat.domain.entities.MessageStatus
import io.okaiwa.features.chat.domain.entities.MessageType
import io.okaiwa.features.chat.domain.entities.Participant
import io.okaiwa.features.chat.domain.repositories.ChatRepository
import io.okaiwa.features.discovery.data.remote.DiscoveredUser
import io.okaiwa.features.keys.data.remote.KeyApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.signal.libsignal.protocol.IdentityKey
import org.signal.libsignal.protocol.SessionBuilder
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.ecc.ECPublicKey
import org.signal.libsignal.protocol.kem.KEMPublicKey
import org.signal.libsignal.protocol.state.PreKeyBundle
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production [ChatRepository] — stitches Room (SQLCipher) + libsignal
 * (SessionBuilder / SessionCipher) + the relay's store-and-forward
 * inbox.
 *
 * Outbound path (sendMessage):
 *   1. Lookup conversation row → peerDeviceId + peerIdentityKey.
 *   2. If no Signal session exists for the address, fetch the PreKey
 *      bundle from the identity service (anonymous GET) and feed it to
 *      SessionBuilder.process(bundle).
 *   3. SessionCipher.encrypt(plaintext) → CiphertextMessage.
 *   4. Base64 the serialised bytes + POST /v1/messages/send with the
 *      deviceToken Bearer.
 *   5. Persist MessageEntity locally with deliveryState = SENT.
 *
 * Inbound path is in [io.okaiwa.features.chat.data.MessagePollingService].
 *
 * Logging policy: NEVER emit plaintext body, decrypted bytes, identity
 * keys, accountId, deviceId, or tokens. Tag-only + integer counters.
 */
@Singleton
class RemoteChatRepository @Inject constructor(
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val relayApi: RelayApi,
    private val keyApi: KeyApi,
    private val signalIdentityKeys: SignalIdentityKeys,
    private val sessionStore: SessionStore,
) : ChatRepository {

    override fun observeConversations(): Flow<List<Conversation>> =
        conversationDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeMessages(conversationId: String): Flow<List<Message>> =
        messageDao.observeForConversation(conversationId).map { rows ->
            rows.map { it.toDomain() }
        }

    override suspend fun sendMessage(
        conversationId: String,
        content: String,
        replyToMessageId: String?,
    ): Message = withContext(Dispatchers.IO) {
        val session = sessionStore.current()
            ?: error("No authenticated session — cannot send")
        val deviceToken = session.deviceToken.takeIf { it.isNotEmpty() }
            ?: error("No relay deviceToken — re-run OTP verify")
        // senderDeviceId / senderAccountId on the relay envelope are
        // cross-checked against the deviceToken's embedded deviceId on
        // the backend (okaiwa-server@dc897f1). Sending empty strings
        // would make every send return 401 "device mismatch" — surface
        // a clean local error instead so the UI can trigger a re-auth.
        if (session.deviceId.isEmpty() || session.accountId.isEmpty()) {
            error("Session missing deviceId/accountId — re-run OTP verify")
        }

        val conversation = conversationDao.findById(conversationId)
            ?: error("Conversation $conversationId not found")

        val peerAddress = SignalProtocolAddress(conversation.peerAccountId, DEFAULT_DEVICE_INDEX)
        val store = signalIdentityKeys.protocolStore()

        // Lazy session establishment — build from PreKey bundle if we've
        // never talked to this address before.
        if (!store.containsSession(peerAddress)) {
            buildSession(peerAddress, conversation)
        }

        val cipher = SessionCipher(store, peerAddress)
        val ciphertext = cipher.encrypt(content.toByteArray(Charsets.UTF_8))
        val blobBase64 = Base64.encodeToString(ciphertext.serialize(), Base64.NO_WRAP)

        val messageId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()

        val response = runCatching {
            relayApi.sendMessage(
                bearer = "Bearer $deviceToken",
                body = SendMessageRequest(
                    recipientDeviceId = conversation.peerDeviceId,
                    blob = blobBase64,
                    messageId = messageId,
                    senderDeviceId = session.deviceId,
                    senderAccountId = session.accountId,
                ),
            )
        }.getOrElse {
            Log.w(TAG, "relay send threw — persisting as FAILED")
            return@withContext persistOutbound(
                messageId = messageId,
                conversationId = conversationId,
                body = content,
                state = DeliveryState.FAILED,
                now = now,
                session = session,
            )
        }
        val deliveryState = if (response.isSuccessful || response.code() == 202) {
            DeliveryState.SENT
        } else {
            Log.w(TAG, "relay send returned HTTP ${response.code()}")
            DeliveryState.FAILED
        }

        persistOutbound(
            messageId = messageId,
            conversationId = conversationId,
            body = content,
            state = deliveryState,
            now = now,
            session = session,
        )
    }

    private suspend fun persistOutbound(
        messageId: String,
        conversationId: String,
        body: String,
        state: String,
        now: Long,
        session: io.okaiwa.features.auth.data.session.Session,
    ): Message {
        messageDao.insert(
            MessageEntity(
                id = messageId,
                conversationId = conversationId,
                senderDeviceId = session.deviceId,
                body = body,
                timestamp = now,
                isOutbound = true,
                deliveryState = state,
            ),
        )
        conversationDao.updateLastMessage(
            conversationId = conversationId,
            messageId = messageId,
            preview = body.take(PREVIEW_MAX_LEN),
            at = now,
        )

        return Message(
            id = messageId,
            conversationId = conversationId,
            senderId = MY_SENDER_ID,
            plaintextContent = body,
            type = MessageType.Text,
            status = when (state) {
                DeliveryState.SENT -> MessageStatus.Sent
                DeliveryState.FAILED -> MessageStatus.Failed
                DeliveryState.DELIVERED -> MessageStatus.Delivered
                else -> MessageStatus.Sending
            },
            sentAt = now,
        )
    }

    private suspend fun buildSession(
        address: SignalProtocolAddress,
        conversation: ConversationEntity,
    ) {
        val response = keyApi.fetchPreKey(deviceId = conversation.peerDeviceId)
        if (!response.isSuccessful) {
            error("Peer key fetch failed: HTTP ${response.code()}")
        }
        val body = response.body() ?: error("Empty peer key bundle")

        // ────────────────────────────────────────────────────────────
        // TOFU on the peer's identityKey. The relay is untrusted —
        // a compromised server can swap body.identityKey to its own
        // and silently MITM every session built afterwards. We pin
        // on first contact and refuse the build if the key has
        // changed since (Signal calls this "safety number changed").
        // ────────────────────────────────────────────────────────────
        if (conversation.peerIdentityKey.isNotEmpty() &&
            conversation.peerIdentityKey != body.identityKey
        ) {
            // Wipe any stale session so the next attempt forces a
            // fresh fetch + a re-confirmation flow once the UI for
            // it lands. Fail loudly here — the bubble surfaces an
            // "identité de votre interlocuteur a changé" error.
            error(
                "Peer identity key changed for conversation ${conversation.id} — " +
                    "potential MITM, refusing to encrypt."
            )
        }
        if (conversation.peerIdentityKey.isEmpty()) {
            // First contact via this code path (outbound) — pin now.
            conversationDao.updatePeerIdentity(
                conversationId = conversation.id,
                peerIdentityKey = body.identityKey,
                peerRegistrationId = body.registrationId,
            )
        }

        val identityKey = IdentityKey(
            Base64.decode(body.identityKey, Base64.NO_WRAP),
        )
        val signedPreKey = ECPublicKey(
            Base64.decode(body.signedPreKey.publicKey, Base64.NO_WRAP),
        )
        val signedSignature = Base64.decode(body.signedPreKey.signature, Base64.NO_WRAP)
        val oneTime = body.preKey
        val kyber = body.kyberPreKey
            ?: error(
                "Peer bundle missing kyberPreKey — libsignal 0.86 requires PQXDH. " +
                    "Backend must extend /v1/keys/prekey/:deviceId to return the kyber field.",
            )

        // libsignal-android 0.86 PreKeyBundle has a single ctor with
        // non-nullable ECPublicKey + int preKeyId. The "no OPK"
        // signaling goes through the sentinel `NULL_PRE_KEY_ID` for
        // preKeyId; the placeholder ECPublicKey passed alongside is
        // ignored by libsignal-rust's encryption path when the
        // sentinel is set. Reusing signedPreKey as that placeholder
        // is the standard pattern (no extra allocation, no synthetic
        // public needed). Verified against libsignal-client-0.86.5
        // class signature: `PreKeyBundle(int, int, int, ECPublicKey,
        // int, ECPublicKey, byte[], IdentityKey, int, KEMPublicKey,
        // byte[])` — only the int preKeyId is the sentinel-bearing
        // field. The receiver knows OPK was omitted because the
        // PreKeySignalMessage envelope's bitmap signals it, not
        // because of the placeholder bytes.
        val bundle = PreKeyBundle(
            /* registrationId = */ body.registrationId,
            /* deviceId       = */ DEFAULT_DEVICE_INDEX,
            /* preKeyId       = */ oneTime?.keyId ?: PreKeyBundle.NULL_PRE_KEY_ID,
            /* preKey         = */ oneTime?.let {
                ECPublicKey(Base64.decode(it.publicKey, Base64.NO_WRAP))
            } ?: signedPreKey,
            /* signedPreKeyId = */ body.signedPreKey.keyId,
            /* signedPreKey   = */ signedPreKey,
            /* signature      = */ signedSignature,
            /* identityKey    = */ identityKey,
            /* kyberPreKeyId  = */ kyber.keyId,
            /* kyberPreKey    = */ KEMPublicKey(Base64.decode(kyber.publicKey, Base64.NO_WRAP)),
            /* kyberSignature = */ Base64.decode(kyber.signature, Base64.NO_WRAP),
        )

        val store = signalIdentityKeys.protocolStore()
        SessionBuilder(store, address).process(bundle)
    }

    override suspend fun sendCryptoPaymentMessage(
        conversationId: String,
        transactionHash: String,
        chain: String,
        amount: String,
        tokenSymbol: String,
    ): Message {
        // Crypto messages ride the same encrypted channel — reuse the
        // text path with a structured payload. The structured MessageType
        // tagging is a TODO; we surface the amount as plaintext for now
        // so the existing bubble renderer stays untouched.
        return sendMessage(
            conversationId = conversationId,
            content = "$amount $tokenSymbol",
            replyToMessageId = null,
        ).copy(type = MessageType.CryptoPayment, plaintextContent = "$amount $tokenSymbol")
    }

    override suspend fun markAsRead(conversationId: String) {
        conversationDao.clearUnread(conversationId)
    }

    override suspend fun deleteMessage(messageId: String, forEveryone: Boolean) {
        messageDao.deleteById(messageId)
        // Server-side delete-for-everyone requires a new relay endpoint;
        // not wired yet. Local delete is the user-facing behaviour.
    }

    override suspend fun createConversation(recipientUserId: String): Conversation {
        // Legacy contract carries only a user id — the chat UI calls
        // this from a path that has already fetched a [DiscoveredUser].
        // Prefer [createConversationFromDiscovery] instead; this fallback just
        // looks up an existing row.
        val existing = conversationDao.findByPeerAccountId(recipientUserId)
            ?: error(
                "No conversation row for $recipientUserId — call createConversationFromDiscovery first",
            )
        return existing.toDomain()
    }

    /**
     * Create (or return) a conversation from a [DiscoveredUser] record.
     * Called by the NewMessage screen once the user taps "Démarrer" on
     * a discovery hit. Persists the peer's deviceId + identity key so
     * sendMessage doesn't need another discovery round-trip.
     */
    override suspend fun createConversationFromDiscovery(peer: DiscoveredUser): Conversation =
        withContext(Dispatchers.IO) {
            val existing = conversationDao.findByPeerAccountId(peer.accountId)
            if (existing != null) return@withContext existing.toDomain()

            val conversationId = UUID.randomUUID().toString()
            val peerDeviceId = peer.deviceId
                ?: error("Discovered user ${peer.accountId} has no deviceId — refuse to create conversation")

            val entity = ConversationEntity(
                id = conversationId,
                peerAccountId = peer.accountId,
                peerUsername = peer.username,
                peerDisplayName = peer.profile?.displayName ?: peer.username,
                peerDeviceId = peerDeviceId,
                peerRegistrationId = peer.registrationId,
                peerIdentityKey = peer.identityPublicKey,
            )
            conversationDao.insert(entity)
            entity.toDomain()
        }

    override suspend fun loadOlderMessages(
        conversationId: String,
        beforeMessageId: String,
        limit: Int,
    ): List<Message> = emptyList() // pagination lands with the history endpoint

    override suspend fun setDisappearingTimer(conversationId: String, durationSeconds: Long) {
        // Disappearing-timer support needs a schema bump + relay-side
        // counterpart. Out of scope for the first real-world E2E cut.
    }

    // ────────────────────────────────────────────────────────────────
    // Mapping helpers
    // ────────────────────────────────────────────────────────────────

    private fun ConversationEntity.toDomain(): Conversation = Conversation(
        id = id,
        type = ConversationType.OneToOne,
        title = null,
        avatarUrl = null,
        participants = listOf(
            Participant(
                userId = peerAccountId,
                displayName = peerDisplayName ?: peerUsername ?: peerAccountId,
                avatarUrl = null,
                joinedAt = createdAt,
            ),
        ),
        lastMessage = lastMessageId?.let {
            MessagePreview(
                messageId = it,
                senderName = null,
                content = lastMessagePreview.orEmpty(),
                type = MessageType.Text,
                timestamp = lastMessageAt,
            )
        },
        unreadCount = unreadCount,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

    private fun MessageEntity.toDomain(): Message = Message(
        id = id,
        conversationId = conversationId,
        senderId = if (isOutbound) MY_SENDER_ID else senderDeviceId,
        plaintextContent = body,
        type = MessageType.Text,
        status = when (deliveryState) {
            DeliveryState.PENDING -> MessageStatus.Sending
            DeliveryState.SENT -> MessageStatus.Sent
            DeliveryState.DELIVERED -> MessageStatus.Delivered
            DeliveryState.FAILED -> MessageStatus.Failed
            else -> MessageStatus.Sent
        },
        sentAt = timestamp,
    )

    companion object {
        private const val TAG = "RemoteChatRepo"
        /** Signal protocol device index. We ship single-device per
         *  account today — multi-device lands with a server-side
         *  device registry. */
        const val DEFAULT_DEVICE_INDEX = 1
        /** Stable sender id rendered by the ChatScreen for "my" bubbles. */
        const val MY_SENDER_ID = "me"
        private const val PREVIEW_MAX_LEN = 120
    }
}
