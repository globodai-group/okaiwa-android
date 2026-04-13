package io.okaiwa.features.auth.data.crypto

import android.util.Base64
import io.okaiwa.features.auth.data.remote.SignedPreKeyDto
import java.security.SecureRandom

/**
 * Placeholder Signal Protocol key bundle.
 *
 * Generates cryptographically random bytes that pass the server-side
 * Vine validator lengths and regex (base64 encoded, 32 bytes identity
 * key, 32 bytes signed pre-key public, 64 bytes signature) — enough to
 * exercise the full register → verify round-trip against the real
 * backend while the libsignal FFI (`okaiwa-signal-core`) XCFramework /
 * AAR are still being shipped.
 *
 * The values produced here are NOT usable for actual Signal Protocol
 * session establishment — the backend stores them but no peer will be
 * able to derive a shared secret from them. This is acceptable during
 * the auth-plumbing milestone because no peer-to-peer encrypted
 * messaging is wired yet. Swap this type for the real libsignal
 * generator the moment `IdentityKeyPair.generate()` is callable.
 */
object MockSignalKeyBundle {
    data class Bundle(
        val identityPublicKey: String,
        val signedPreKey: SignedPreKeyDto,
        val registrationId: Int,
    )

    fun generate(): Bundle {
        val rng = SecureRandom()

        val identityKey = ByteArray(32).also { rng.nextBytes(it) }
        val signedPreKeyPub = ByteArray(32).also { rng.nextBytes(it) }
        val signature = ByteArray(64).also { rng.nextBytes(it) }

        // registrationId is a Signal Protocol 14-bit positive integer.
        // We generate inside [1, 2^14) and guarantee non-zero so the
        // server-side `vine.number().positive()` check passes.
        val registrationId = 1 + rng.nextInt(0x3fff)

        return Bundle(
            identityPublicKey = Base64.encodeToString(identityKey, Base64.NO_WRAP),
            signedPreKey = SignedPreKeyDto(
                keyId = 1 + rng.nextInt(Int.MAX_VALUE - 1),
                publicKey = Base64.encodeToString(signedPreKeyPub, Base64.NO_WRAP),
                signature = Base64.encodeToString(signature, Base64.NO_WRAP),
            ),
            registrationId = registrationId,
        )
    }
}
