package io.okaiwa.features.auth.data.crypto

import java.security.MessageDigest
import java.util.Locale

/**
 * SHA-256 of an E.164 phone number, hex-encoded (64 lowercase chars).
 *
 * The identity service NEVER sees the plaintext — it receives only this
 * hash. The same deterministic transformation is applied on every
 * Okaiwa client (iOS + Android) so that discovery by phone-hash batch
 * matches what another user's client has computed. No salt: discovery
 * requires both peers to produce the same digest from the same number.
 *
 * Input is normalized to strip spaces, hyphens and parentheses before
 * hashing, so `+33 6 12 34 56 78` and `+33612345678` collide — the UI
 * frequently shows the grouped form, but the identity layer must be
 * indifferent to cosmetic differences.
 */
object PhoneHasher {
    fun hashE164(rawE164: String): String {
        val normalized = normalize(rawE164)
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
        return digest.toHex()
    }

    private fun normalize(raw: String): String {
        val builder = StringBuilder(raw.length)
        for (c in raw) {
            if (c.isDigit() || c == '+') {
                builder.append(c)
            }
        }
        return builder.toString()
    }

    private fun ByteArray.toHex(): String = buildString(size * 2) {
        for (byte in this@toHex) {
            val v = byte.toInt() and 0xff
            append(Character.forDigit(v ushr 4, 16))
            append(Character.forDigit(v and 0x0f, 16))
        }
    }.lowercase(Locale.ROOT)
}
