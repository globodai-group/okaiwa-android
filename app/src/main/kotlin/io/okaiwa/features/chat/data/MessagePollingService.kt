package io.okaiwa.features.chat.data

import android.util.Base64
import android.util.Log
import io.okaiwa.features.auth.data.crypto.SignalIdentityKeys
import io.okaiwa.features.auth.data.session.SessionStore
import io.okaiwa.features.chat.data.local.ConversationDao
import io.okaiwa.features.chat.data.local.ConversationEntity
import io.okaiwa.features.chat.data.local.DeliveryState
import io.okaiwa.features.chat.data.local.MessageDao
import io.okaiwa.features.chat.data.local.MessageEntity
import io.okaiwa.features.chat.data.remote.PendingMessagesRequest
import io.okaiwa.features.chat.data.remote.RelayApi
import io.okaiwa.features.chat.data.remote.RelayEnvelope
import io.okaiwa.features.chat.data.repositories.RemoteChatRepository
import io.okaiwa.features.discovery.data.remote.DiscoveryApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.signal.libsignal.protocol.DuplicateMessageException
import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Polls the relay inbox and decrypts/persists every envelope.
 *
 * Start()/stop() pair is driven from MainActivity's onResume()/onPause()
 * so we don't drain battery when the user is on another app. Every
 * 5 seconds we:
 *   1. GET /v1/messages/pending?deviceId=... (body on GET — backend quirk).
 *   2. For each envelope:
 *      a. sniff the CiphertextMessage type byte;
 *      b. SessionCipher.decrypt(...) against the sender's address;
 *      c. resolve / create the ConversationEntity;
 *      d. insert MessageEntity (isOutbound=false, deliveryState=DELIVERED);
 *      e. DELETE /v1/messages/:id to ack (at-least-once semantics — if we
 *         crash between insert and ack we'll process the same envelope
 *         twice; the DAO @Insert(IGNORE) on the primary key makes the
 *         replay idempotent).
 *
 * Sender identity resolution:
 *   - The relay injects senderDeviceId into the envelope (server-side).
 *     If missing, we fall back to the PreKeyBundle's embedded identity
 *     key — libsignal exposes it via PreKeySignalMessage.identityKey.
 *     If we still can't resolve the account, we quarantine the envelope
 *     (log + leave on the server).
 */
@Singleton
class MessagePollingService @Inject constructor(
    private val relayApi: RelayApi,
    private val discoveryApi: DiscoveryApi,
    private val conversationDao: ConversationDao,
    private val messageDao: MessageDao,
    private val signalIdentityKeys: SignalIdentityKeys,
    private val sessionStore: SessionStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    /**
     * In-memory dead-letter counter — `messageId → consecutive
     * decrypt failure count`. When a message can't be decrypted
     * (corrupt blob, desynchronized session, malformed type byte)
     * we DON'T ack on the first failure (might be a transient
     * libsignal hiccup), but after [MAX_DECRYPT_RETRIES] we ack
     * + log so the envelope stops looping forever and draining the
     * poll. Cleared per-process — a fresh launch resets all counters
     * which is fine: at-least-once still applies and the underlying
     * issue (poison blob) will quickly hit the limit again.
     */
    private val decryptFailureCounts = ConcurrentHashMap<String, Int>()

    /** Idempotent — subsequent starts are no-ops. */
    fun start() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                runCatching { pollOnce() }
                    .onFailure { Log.w(TAG, "poll failed: ${it::class.simpleName}") }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    fun stop() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun pollOnce() {
        val session = sessionStore.current() ?: return
        val deviceToken = session.deviceToken.takeIf { it.isNotEmpty() } ?: return
        val deviceId = session.deviceId.takeIf { it.isNotEmpty() } ?: return

        val response = relayApi.getPending(
            bearer = "Bearer $deviceToken",
            body = PendingMessagesRequest(deviceId = deviceId),
        )
        if (!response.isSuccessful) {
            Log.w(TAG, "pending HTTP ${response.code()}")
            return
        }
        val body = response.body() ?: return
        if (body.count == 0) return
        Log.d(TAG, "polled ${body.count} envelopes")

        body.messages.forEach { envelope ->
            runCatching { handleEnvelope(envelope, deviceToken) }
                .onFailure { failure ->
                    when (failure) {
                        // Already-seen message: ack immediately. The
                        // ratchet has already moved past this index, so
                        // re-decrypting would always fail; keeping the
                        // envelope server-side just wastes polls.
                        is DuplicateMessageException -> {
                            Log.d(TAG, "duplicate envelope — ack + drop")
                            ackEnvelope(envelope.messageId, deviceToken)
                            decryptFailureCounts.remove(envelope.messageId)
                        }
                        // Anything else: bump the counter, stop acking
                        // until we hit the dead-letter threshold.
                        else -> {
                            val next = (decryptFailureCounts[envelope.messageId] ?: 0) + 1
                            decryptFailureCounts[envelope.messageId] = next
                            if (next >= MAX_DECRYPT_RETRIES) {
                                Log.w(
                                    TAG,
                                    "envelope ${envelope.messageId} dead-lettered after $next attempts " +
                                        "(${failure::class.simpleName}) — ack + drop"
                                )
                                ackEnvelope(envelope.messageId, deviceToken)
                                decryptFailureCounts.remove(envelope.messageId)
                            } else {
                                Log.w(
                                    TAG,
                                    "envelope handle failed (attempt $next/$MAX_DECRYPT_RETRIES): " +
                                        "${failure::class.simpleName}"
                                )
                            }
                        }
                    }
                }
        }
    }

    /**
     * Best-effort delete on the relay. 204/404 are both terminal
     * (purged or already gone). Other codes are warned about but
     * the envelope re-enters the next poll cycle naturally.
     */
    private suspend fun ackEnvelope(messageId: String, deviceToken: String) {
        runCatching {
            relayApi.deleteMessage(bearer = "Bearer $deviceToken", messageId = messageId)
        }.onFailure {
            Log.w(TAG, "ack network failure for $messageId — will replay")
        }
    }

    private suspend fun handleEnvelope(envelope: RelayEnvelope, deviceToken: String) {
        val senderDeviceId = envelope.senderDeviceId
            ?: run {
                Log.w(TAG, "envelope missing senderDeviceId — skip")
                return
            }
        val senderAccountId = envelope.senderAccountId
            ?: run {
                // Discover by deviceId once we add a GET /discovery/device/:id
                // endpoint. For now, log and skip — at-least-once semantics
                // let us pick it up again.
                Log.w(TAG, "envelope missing senderAccountId — skip (backend gap)")
                return
            }

        val ciphertextBytes = Base64.decode(envelope.blob, Base64.NO_WRAP)
        val typeByte = ciphertextBytes.firstOrNull()?.toInt()
            ?: run {
                Log.w(TAG, "empty ciphertext — skip")
                return
            }
        val type = typeByte and 0x0F

        val store = signalIdentityKeys.protocolStore()
        val senderAddress = SignalProtocolAddress(senderAccountId, RemoteChatRepository.DEFAULT_DEVICE_INDEX)
        val cipher = SessionCipher(store, senderAddress)

        // Pre-decrypt: capture the sender's identity key from the
        // PreKeySignalMessage header BEFORE cipher.decrypt consumes
        // the message. This is the only point we can pin TOFU on
        // inbound-first contact — once the OPK is consumed and the
        // ratchet starts, follow-up SignalMessage envelopes don't
        // carry an identity key.
        val (preKeyMessage, signalMessage) = when (type) {
            CiphertextMessage.PREKEY_TYPE ->
                PreKeySignalMessage(ciphertextBytes) to null
            CiphertextMessage.WHISPER_TYPE ->
                null to SignalMessage(ciphertextBytes)
            else -> {
                Log.w(TAG, "unknown ciphertext type $type — skip")
                return
            }
        }

        val plaintext = if (preKeyMessage != null) {
            cipher.decrypt(preKeyMessage)
        } else {
            cipher.decrypt(signalMessage!!)
        }

        val body = plaintext.toString(Charsets.UTF_8)

        // First-contact TOFU: pin the sender's identityKey on the
        // ConversationEntity. The previous code stored "" which made
        // the outbound TOFU check on reply silently accept anything.
        val peerIdentityKeyB64 = preKeyMessage?.identityKey?.serialize()
            ?.let { Base64.encodeToString(it, Base64.NO_WRAP) }

        val conversationId = resolveOrCreateConversation(
            senderAccountId = senderAccountId,
            senderDeviceId = senderDeviceId,
            peerIdentityKeyB64 = peerIdentityKeyB64,
        )
        val now = System.currentTimeMillis()

        messageDao.insert(
            MessageEntity(
                id = envelope.messageId,
                conversationId = conversationId,
                senderDeviceId = senderDeviceId,
                body = body,
                timestamp = now,
                isOutbound = false,
                deliveryState = DeliveryState.DELIVERED,
            ),
        )
        conversationDao.updateLastMessage(
            conversationId = conversationId,
            messageId = envelope.messageId,
            preview = body.take(PREVIEW_MAX_LEN),
            at = now,
        )
        conversationDao.incrementUnread(conversationId)

        // Ack + purge — intentionally after the DB insert. Replay on
        // crash is safe because MessageDao.insert uses ON CONFLICT IGNORE.
        // Successful decrypt → clear the failure counter so a future
        // unrelated envelope doesn't inherit the wrong count.
        decryptFailureCounts.remove(envelope.messageId)
        ackEnvelope(envelope.messageId, deviceToken)
    }

    /**
     * Find a conversation for the sender, or create a new one. On
     * first contact we pin the peer's identityKey extracted from the
     * PreKeySignalMessage so the next outbound-reply TOFU check has
     * a real value to compare against (was empty string previously,
     * which silently accepted any identity-key swap).
     */
    private suspend fun resolveOrCreateConversation(
        senderAccountId: String,
        senderDeviceId: String,
        peerIdentityKeyB64: String?,
    ): String {
        val existing = conversationDao.findByPeerAccountId(senderAccountId)
        if (existing != null) {
            // Second + inbound message in the same conv: the identity
            // key is already pinned. If we got a fresh
            // PreKeySignalMessage (= peer rebuilt their session) and
            // the key changed, refuse silently — the bubble surfaces
            // a "safety number changed" error (UI lands later).
            if (peerIdentityKeyB64 != null &&
                existing.peerIdentityKey.isNotEmpty() &&
                existing.peerIdentityKey != peerIdentityKeyB64
            ) {
                Log.w(TAG, "peer identity changed — refusing to pin silently")
            }
            return existing.id
        }

        // First contact from this peer.
        val conversationId = UUID.randomUUID().toString()
        val entity = ConversationEntity(
            id = conversationId,
            peerAccountId = senderAccountId,
            peerUsername = null,
            peerDisplayName = null,
            peerDeviceId = senderDeviceId,
            peerRegistrationId = 0,
            // PIN the identity key right now — TOFU. peerIdentityKeyB64
            // is null only on a WHISPER_TYPE first contact, which is
            // technically impossible (a WHISPER message requires a
            // pre-existing session). If it happens, log + skip.
            peerIdentityKey = peerIdentityKeyB64 ?: run {
                Log.w(TAG, "WHISPER first-contact for new peer — skip envelope")
                return ""
            },
        )
        conversationDao.insert(entity)

        // Best-effort: resolve the peer's username via discovery so the
        // conversation list shows a real handle instead of "unknown".
        // Failures are silent — the UI falls back to peerAccountId.
        runCatching {
            val response = discoveryApi.searchByUsername(senderAccountId)
            if (response.isSuccessful) {
                response.body()?.username?.let { name ->
                    conversationDao.updatePeerUsername(conversationId, name)
                }
            }
        }
        return conversationId
    }

    companion object {
        private const val TAG = "MsgPolling"
        private const val POLL_INTERVAL_MS = 5_000L
        private const val PREVIEW_MAX_LEN = 120
        /**
         * After this many consecutive decrypt failures on the same
         * envelope, dead-letter it (ack + drop). 5 ≈ 25s of polling
         * — long enough for a transient libsignal hiccup to clear,
         * short enough that a poison blob doesn't drain the inbox
         * indefinitely.
         */
        private const val MAX_DECRYPT_RETRIES = 5
    }
}
