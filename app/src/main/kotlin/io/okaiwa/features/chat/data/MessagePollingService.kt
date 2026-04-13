package io.okaiwa.features.chat.data

import android.util.Base64
import android.util.Log
import io.okaiwa.features.auth.data.crypto.SignalIdentityKeys
import io.okaiwa.features.auth.data.session.SessionStore
import io.okaiwa.features.chat.data.local.ConversationDao
import io.okaiwa.features.chat.data.local.ConversationEntity
import io.okaiwa.features.chat.data.local.DeadLetterCountDao
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val deadLetterCountDao: DeadLetterCountDao,
    private val signalIdentityKeys: SignalIdentityKeys,
    private val sessionStore: SessionStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var pollingJob: Job? = null

    /**
     * Serialises the spoof-defense `findByIdentityKeyExcluding`
     * check + `insert` pair in [resolveOrCreateConversation]. Without
     * it, two concurrent inbound envelopes from different accountIds
     * that claim the same identityKey could both pass the collision
     * check before either inserts — letting a hostile relay silently
     * mint duplicate sessions for one peer's identity (TOCTOU, P1
     * from the cross-platform security review).
     *
     * The poll loop itself is single-coroutine `forEach`, but any
     * future concurrent consumer (a second `start()`, a direct
     * `handleEnvelope` call from push-notification land, …) would
     * reopen the window. A process-scoped mutex is cheap insurance.
     */
    private val resolveMutex = Mutex()

    /**
     * In-memory fallback for the persisted dead-letter counter.
     * Populated only when a write to [deadLetterCountDao] fails (I/O
     * error, DB locked) so the counter still eventually trips within
     * the current process. The persisted counter in the SQLCipher
     * `dead_letter_counts` table is the primary source of truth —
     * the in-memory map used to be it, and reset on every cold start,
     * which let a hostile relay loop the same poison envelope
     * indefinitely by waiting for the app to be killed (P1 from the
     * cross-platform polling security review).
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
                            resetFailureCounter(envelope.messageId)
                        }
                        // Anything else: bump the counter, stop acking
                        // until we hit the dead-letter threshold.
                        else -> {
                            val next = bumpFailureCounter(envelope.messageId)
                            if (next >= MAX_DECRYPT_RETRIES) {
                                Log.w(
                                    TAG,
                                    "envelope ${envelope.messageId} dead-lettered after $next attempts " +
                                        "(${failure::class.simpleName}) — ack + drop"
                                )
                                ackEnvelope(envelope.messageId, deviceToken)
                                resetFailureCounter(envelope.messageId)
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
     * Atomic bump + read on the persisted `dead_letter_counts` table.
     * Falls back to the in-memory [decryptFailureCounts] ConcurrentHashMap
     * on any DAO exception so the ceiling still trips within the
     * current process even if the DB is transiently unavailable.
     */
    private suspend fun bumpFailureCounter(messageId: String): Int = runCatching {
        deadLetterCountDao.bumpAndRead(messageId, System.currentTimeMillis())
    }.getOrElse {
        Log.w(TAG, "dead-letter bump failed: ${it::class.simpleName} — using in-memory fallback")
        val next = (decryptFailureCounts[messageId] ?: 0) + 1
        decryptFailureCounts[messageId] = next
        next
    }

    private suspend fun resetFailureCounter(messageId: String) {
        runCatching { deadLetterCountDao.reset(messageId) }
        decryptFailureCounts.remove(messageId)
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
        // Drop+ack on malformed envelopes — the previous `return` path
        // left the envelope on the server forever and let a hostile
        // peer mint unlimited null-sender blobs to flood the inbox
        // (DoS, shared P0 with iOS from the polling security review).
        val senderDeviceId = envelope.senderDeviceId
            ?: run {
                Log.w(TAG, "envelope missing senderDeviceId — ack + drop")
                ackEnvelope(envelope.messageId, deviceToken)
                return
            }
        val senderAccountId = envelope.senderAccountId
            ?: run {
                Log.w(TAG, "envelope missing senderAccountId — ack + drop")
                ackEnvelope(envelope.messageId, deviceToken)
                return
            }

        val ciphertextBytes = Base64.decode(envelope.blob, Base64.NO_WRAP)
        val typeByte = ciphertextBytes.firstOrNull()?.toInt()
            ?: run {
                Log.w(TAG, "empty ciphertext — ack + drop")
                ackEnvelope(envelope.messageId, deviceToken)
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
                Log.w(TAG, "unknown ciphertext type $type — ack + drop")
                ackEnvelope(envelope.messageId, deviceToken)
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
        resetFailureCounter(envelope.messageId)
        ackEnvelope(envelope.messageId, deviceToken)
    }

    /**
     * Find a conversation for the sender, or create a new one. On
     * first contact we pin the peer's identityKey extracted from the
     * PreKeySignalMessage so the next outbound-reply TOFU check has
     * a real value to compare against.
     *
     * Throws [IdentityChangedException] when the envelope carries an
     * identityKey that contradicts the one we already pinned or that
     * is already bound to a different accountId — either is a
     * strong signal of MITM / sender spoofing, and letting the
     * envelope land silently would let a hostile relay forward
     * Alice's ciphertext under Mallory's accountId (Bob would render
     * the message attributed to Mallory while the ratchet is
     * actually Alice's). The outer poll loop treats this as a
     * decrypt failure and the dead-letter counter eventually
     * ack+drops the poison envelope.
     */
    private suspend fun resolveOrCreateConversation(
        senderAccountId: String,
        senderDeviceId: String,
        peerIdentityKeyB64: String?,
    ): String = resolveMutex.withLock {
        val existing = conversationDao.findByPeerAccountId(senderAccountId)
        if (existing != null) {
            // Second + inbound message in the same conv: the identity
            // key is already pinned. If we got a fresh
            // PreKeySignalMessage (peer rebuilt their session) and
            // the key changed, refuse — the bubble surfaces a
            // "safety number changed" error once the UI lands.
            if (peerIdentityKeyB64 != null &&
                existing.peerIdentityKey.isNotEmpty() &&
                existing.peerIdentityKey != peerIdentityKeyB64
            ) {
                throw IdentityChangedException(
                    "peer identity changed for conv ${existing.id} — refusing",
                )
            }
            return existing.id
        }

        // First contact from this peer.
        // WHISPER_TYPE for a brand-new peer is technically impossible
        // (a Whisper message requires a pre-existing session), so the
        // null pin is a poison envelope — dead-letter it.
        val pinned = peerIdentityKeyB64
            ?: throw IdentityChangedException(
                "WHISPER first-contact for unknown peer $senderAccountId",
            )

        // Sender-spoofing defense: if ANOTHER accountId already has
        // this identity key pinned, a hostile relay is forwarding
        // someone else's ciphertext under this accountId. Libsignal
        // would happily decrypt (the ratchet matches the real peer)
        // and we'd render the message attributed to the wrong
        // identity. Refuse and dead-letter.
        val collision = conversationDao.findByIdentityKeyExcluding(
            peerIdentityKey = pinned,
            excludeAccountId = senderAccountId,
        )
        if (collision != null) {
            throw IdentityChangedException(
                "identity already bound to conv ${collision.id} — sender spoofing",
            )
        }

        val conversationId = UUID.randomUUID().toString()
        val entity = ConversationEntity(
            id = conversationId,
            peerAccountId = senderAccountId,
            peerUsername = null,
            peerDisplayName = null,
            peerDeviceId = senderDeviceId,
            peerRegistrationId = 0,
            peerIdentityKey = pinned,
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

/**
 * Signalled by [MessagePollingService] when a peer's identityKey
 * contradicts what we already pinned, or when the same identityKey
 * is already bound to a different accountId (sender spoofing). The
 * outer poll loop treats this the same as any other decrypt failure
 * — the dead-letter counter ack+drops the envelope after
 * MAX_DECRYPT_RETRIES so a hostile relay can't loop the same poison
 * blob indefinitely.
 */
class IdentityChangedException(message: String) : Exception(message)
