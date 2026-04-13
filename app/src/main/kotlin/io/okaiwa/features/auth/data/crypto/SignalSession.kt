package io.okaiwa.features.auth.data.crypto

import org.signal.libsignal.protocol.SessionCipher
import org.signal.libsignal.protocol.SignalProtocolAddress
import org.signal.libsignal.protocol.UsePqRatchet
import org.signal.libsignal.protocol.message.CiphertextMessage
import org.signal.libsignal.protocol.message.PreKeySignalMessage
import org.signal.libsignal.protocol.message.SignalMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thin wrapper over libsignal's [SessionCipher] so the chat layer doesn't
 * need to know about protocol stores and pre-key bookkeeping.
 *
 * Two public entry points:
 *   - [encryptForRecipient] — once a session is established (by handing
 *     SessionBuilder a peer's PreKeyBundle, which lands with the key
 *     discovery flow), this produces a CiphertextMessage ready to drop on
 *     the relay WebSocket.
 *   - [decryptFromSender] — accepts the raw bytes coming off the wire,
 *     sniffs the Signal message type byte (PreKey vs regular SignalMessage)
 *     and feeds the right libsignal API to unwrap to plaintext.
 *
 * The SessionCipher is NOT cached — libsignal explicitly documents the
 * class as non-thread-safe, so we build a fresh cipher per call against
 * the shared [SignalIdentityKeys] store. The store itself is cheap to
 * reuse because only the address-scoped lookup allocates.
 *
 * The chat UI will hit this after the relay delivers the first message
 * (inbound path: we receive a PreKeySignalMessage and let libsignal
 * consume our one-time pre-key in the process) or after key discovery
 * (outbound path: we fetched the peer's PreKeyBundle and called
 * `SessionBuilder.process(bundle)` beforehand). Session establishment
 * itself lives in a follow-up file (SignalSessionBuilder) that this
 * commit does not introduce.
 */
@Singleton
class SignalSession @Inject constructor(
    private val identityKeys: SignalIdentityKeys,
) {

    /**
     * Seal [plaintext] for [recipientAddress]. A session for that address
     * must already be established — callers get a [NoSessionException]
     * via libsignal otherwise (surfaced as-is so the chat layer can trigger
     * the key-discovery retry).
     */
    fun encryptForRecipient(
        recipientAddress: SignalProtocolAddress,
        plaintext: ByteArray,
    ): CiphertextMessage {
        val store = identityKeys.loadOrGenerate()
        val cipher = SessionCipher(store, recipientAddress)
        return cipher.encrypt(plaintext)
    }

    /**
     * Open a Signal-framed [ciphertext] from [senderAddress].
     *
     * The first byte of the ciphertext carries the framing version +
     * message type. If the message is a [PreKeySignalMessage] we use the
     * overload that consumes a one-time pre-key from our store; otherwise
     * we treat it as a regular [SignalMessage] on an existing ratchet.
     */
    fun decryptFromSender(
        senderAddress: SignalProtocolAddress,
        ciphertext: ByteArray,
    ): ByteArray {
        val store = identityKeys.loadOrGenerate()
        val cipher = SessionCipher(store, senderAddress)

        // Bit packing comes straight from libsignal's CiphertextMessage
        // (upper 4 bits = version, lower 4 bits = type). We branch on the
        // type because libsignal's SessionCipher.decrypt() is split into
        // two typed methods, one per message variant.
        val typeByte = ciphertext.firstOrNull()?.toInt() ?: error("Empty ciphertext")
        val type = typeByte and 0x0F

        return when (type) {
            // PreKey messages are the first inbound message of a session:
            // libsignal needs to know whether we expect a post-quantum
            // ratchet negotiation. We default to ON because PQXDH is the
            // current Signal Protocol baseline — flipping to YES matches
            // what Signal's official clients use today.
            CiphertextMessage.PREKEY_TYPE -> cipher.decrypt(
                PreKeySignalMessage(ciphertext),
                UsePqRatchet.YES,
            )
            CiphertextMessage.WHISPER_TYPE -> cipher.decrypt(SignalMessage(ciphertext))
            else -> error("Unsupported Signal ciphertext type: $type")
        }
    }
}
