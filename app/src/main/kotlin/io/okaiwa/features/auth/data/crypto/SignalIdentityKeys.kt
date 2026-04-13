package io.okaiwa.features.auth.data.crypto

import android.util.Base64
import io.okaiwa.features.auth.data.remote.SignedPreKeyDto
import org.signal.libsignal.protocol.IdentityKeyPair
import org.signal.libsignal.protocol.ecc.ECKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyPair
import org.signal.libsignal.protocol.kem.KEMKeyType
import org.signal.libsignal.protocol.state.KyberPreKeyRecord
import org.signal.libsignal.protocol.state.PreKeyRecord
import org.signal.libsignal.protocol.state.SignedPreKeyRecord
import org.signal.libsignal.protocol.state.impl.InMemorySignalProtocolStore
import org.signal.libsignal.protocol.util.KeyHelper
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Signal Protocol key-bundle generator, backed by libsignal-android.
 *
 * Replaces [MockSignalKeyBundle] — every field the backend stores is now
 * cryptographically valid. Peers discovering this account through the
 * identity service will be able to derive a shared secret because the
 * identityPublicKey is a real Curve25519 point and the signedPreKey
 * signature verifies against it.
 *
 * Material generated on [initialise]:
 *   - 1 IdentityKeyPair (Curve25519) — the long-term identity of the device.
 *   - 1 SignedPreKeyRecord — signed by the identity key, rotates periodically.
 *   - [ONE_TIME_PRE_KEY_COUNT] one-time pre-keys — consumed by peers one per
 *     session initiation. The server warns us to upload more when stock runs low.
 *   - 1 registrationId — 14-bit integer, persisted so the server can route messages.
 *
 * Persistence:
 *   The raw key material sits inside a [Signal-backed in-memory store]
 *   ([InMemorySignalProtocolStore]) whose serialized form
 *   ([IdentityKeyPair.serialize], [SignedPreKeyRecord.serialize], one-time
 *   pre-key serializations, [registrationId]) is persisted into the
 *   encrypted [SignalIdentityStore] (AES-256/GCM via Android Keystore).
 *   Across an app restart [loadOrGenerate] rehydrates the same keys, so
 *   peers who have cached a PreKeyBundle keyed on our identity can still
 *   establish a session.
 *
 * Thread safety: the helper exposes a single instance through Hilt and
 * serializes all writes through `@Synchronized`. The underlying libsignal
 * store is NOT thread-safe — do not share the handle outside this class.
 */
@Singleton
class SignalIdentityKeys @Inject constructor(
    private val identityStore: SignalIdentityStore,
) {

    /** Snapshot of the registration bundle expected by `/v1/auth/register`. */
    data class Bundle(
        val identityPublicKey: String,
        val signedPreKey: SignedPreKeyDto,
        val registrationId: Int,
    )

    /**
     * Snapshot passed into the PreKey upload request — see
     * [io.okaiwa.features.keys.data.remote.KeyApi.uploadPreKeys].
     * The upload is gated on `preKeysUploaded` in [SignalIdentityStore],
     * so subsequent launches don't burn the OPK pool.
     */
    data class UploadBatch(
        /** Max 100 rows. `publicKey` is base64 of the PreKeyRecord's ECPublicKey. */
        val oneTimePreKeys: List<PreKeyPublic>,
        /** Signed pre-key already sent at register time — re-uploaded here
         *  so the relay has a fresh record even for accounts that verified
         *  against an older revision of /v1/auth/register. */
        val signedPreKey: SignedPreKeyDto,
        val kyberPreKey: KyberPreKeyDto,
        val identityPublicKey: String,
        val registrationId: Int,
    )

    data class PreKeyPublic(val keyId: Int, val publicKey: String)

    /** Wire-shape for the Kyber (PQXDH) pre-key — base64 public + signature. */
    data class KyberPreKeyDto(val keyId: Int, val publicKey: String, val signature: String)

    @Volatile
    private var cachedStore: InMemorySignalProtocolStore? = null

    /**
     * Expose the identity store so the chat layer can hand it to
     * [org.signal.libsignal.protocol.SessionBuilder] /
     * [org.signal.libsignal.protocol.SessionCipher]. The store lifecycle
     * matches the app process — libsignal keeps its session/identity
     * state here until we rotate the backing snapshot.
     */
    fun protocolStore(): InMemorySignalProtocolStore = loadOrGenerate()

    /**
     * Either rehydrate the persisted key material or generate a fresh set
     * on first launch. Idempotent: subsequent calls return the same store.
     */
    @Synchronized
    fun loadOrGenerate(): InMemorySignalProtocolStore {
        cachedStore?.let { return it }

        val persisted = identityStore.read()
        val store = if (persisted != null) {
            InMemorySignalProtocolStore(
                IdentityKeyPair(persisted.identityKeyPair),
                persisted.registrationId,
            ).also { rehydrated ->
                // Replay the persisted SignedPreKey + OneTime pre-keys into
                // the fresh in-memory store so SessionCipher can look them
                // up by id when a peer's first message arrives.
                rehydrated.storeSignedPreKey(
                    persisted.signedPreKeyId,
                    SignedPreKeyRecord(persisted.signedPreKeyRecord),
                )
                persisted.oneTimePreKeyRecords.forEach { raw ->
                    val record = PreKeyRecord(raw)
                    rehydrated.storePreKey(record.id, record)
                }
                // Kyber pre-key is the PQXDH leg — hydrated from the same
                // snapshot so peers fetching our bundle after a restart
                // still encapsulate against a key we can decapsulate.
                if (persisted.kyberPreKeyRecord != null && persisted.kyberPreKeyId > 0) {
                    rehydrated.storeKyberPreKey(
                        persisted.kyberPreKeyId,
                        KyberPreKeyRecord(persisted.kyberPreKeyRecord),
                    )
                }
            }.also { rehydrated ->
                // Back-fill: accounts that were registered BEFORE the
                // kyber plumbing landed won't have a kyber record. Mint
                // one lazily and persist so the next PreKey upload has
                // something to send.
                //
                // CRITICAL: when we back-fill, we ALSO reset the
                // `preKeysUploaded` flag to false so the next call to
                // RemoteAuthRepository.uploadPreKeysOnce (or the
                // app-level warmup hook) actually pushes the kyber
                // material to the server. Without this reset, peers
                // fetching the pre-commit user's bundle get
                // `kyberPreKey: null` → SessionBuilder.process throws
                // → no message ever reaches them. The agent's review
                // P0#5 caught this silent failure mode.
                val backFilled = persisted.kyberPreKeyRecord == null
                if (backFilled) {
                    val kyberRecord = generateKyberPreKey(rehydrated.identityKeyPair)
                    rehydrated.storeKyberPreKey(KYBER_PRE_KEY_INITIAL_ID, kyberRecord)
                }
                persistSnapshot(
                    rehydrated,
                    preKeysUploaded = if (backFilled) false else persisted.preKeysUploaded,
                )
            }
        } else {
            generateFreshStore().also { persistSnapshot(it, preKeysUploaded = false) }
        }

        cachedStore = store
        return store
    }

    /**
     * Build the registration bundle the backend expects. Runs
     * [loadOrGenerate] so the bundle is always backed by the same keys
     * that [SignalSession] will sign / decrypt with.
     */
    fun registrationBundle(): Bundle {
        val store = loadOrGenerate()
        val identity = store.identityKeyPair
        val signedPreKeyId = identityStore.read()?.signedPreKeyId
            ?: error("Signed pre-key id missing after loadOrGenerate()")
        val signedPreKey = store.loadSignedPreKey(signedPreKeyId)

        return Bundle(
            identityPublicKey = Base64.encodeToString(
                identity.publicKey.serialize(),
                Base64.NO_WRAP,
            ),
            signedPreKey = SignedPreKeyDto(
                keyId = signedPreKey.id,
                publicKey = Base64.encodeToString(
                    signedPreKey.keyPair.publicKey.serialize(),
                    Base64.NO_WRAP,
                ),
                signature = Base64.encodeToString(
                    signedPreKey.signature,
                    Base64.NO_WRAP,
                ),
            ),
            registrationId = store.localRegistrationId,
        )
    }

    // ────────────────────────────────────────────────────────────────
    // Internal — first-run generation
    // ────────────────────────────────────────────────────────────────

    private fun generateFreshStore(): InMemorySignalProtocolStore {
        // Curve25519 long-term identity key. From this point on any bundle
        // we upload to the server ties back to this keypair.
        val identity = IdentityKeyPair.generate()

        // 14-bit registration id (Signal: extendedRange = false fits the
        // standard protobuf encoding). Non-zero by construction — the
        // server rejects 0.
        val registrationId = KeyHelper.generateRegistrationId(false)

        // Signed pre-key: fresh Curve25519 keypair signed by the identity
        // private key. libsignal exposes ECPrivateKey.calculateSignature
        // (the old Curve.calculateSignature static helper was removed in
        // the Kotlin port of the ecc package around v0.70).
        val signedPreKeyId = SIGNED_PRE_KEY_INITIAL_ID
        val signedPreKeyPair = ECKeyPair.generate()
        val signedPreKeySignature = identity.privateKey.calculateSignature(
            signedPreKeyPair.publicKey.serialize()
        )
        val signedPreKeyRecord = SignedPreKeyRecord(
            signedPreKeyId,
            System.currentTimeMillis(),
            signedPreKeyPair,
            signedPreKeySignature,
        )

        // One-time pre-keys. IDs are 1-based (0 is reserved by some peers
        // as "no pre-key"). 100 covers the first wave of conversations;
        // the server prompts us to refill once stock drops below ~10.
        val oneTimePreKeys: List<PreKeyRecord> = (1..ONE_TIME_PRE_KEY_COUNT).map { id ->
            PreKeyRecord(id, ECKeyPair.generate())
        }

        // Kyber (ML-KEM-1024) pre-key — the PQXDH leg libsignal 0.86+
        // requires on every bundle. Signed by the identity key the same
        // way the classic signed pre-key is.
        val kyberRecord = generateKyberPreKey(identity)

        val store = InMemorySignalProtocolStore(identity, registrationId)
        store.storeSignedPreKey(signedPreKeyId, signedPreKeyRecord)
        oneTimePreKeys.forEach { record -> store.storePreKey(record.id, record) }
        store.storeKyberPreKey(KYBER_PRE_KEY_INITIAL_ID, kyberRecord)
        return store
    }

    private fun generateKyberPreKey(identity: IdentityKeyPair): KyberPreKeyRecord {
        val kyberPair = KEMKeyPair.generate(KEMKeyType.KYBER_1024)
        val signature = identity.privateKey.calculateSignature(
            kyberPair.publicKey.serialize()
        )
        return KyberPreKeyRecord(
            KYBER_PRE_KEY_INITIAL_ID,
            System.currentTimeMillis(),
            kyberPair,
            signature,
        )
    }

    private fun persistSnapshot(store: InMemorySignalProtocolStore, preKeysUploaded: Boolean) {
        val signedPreKey = store.loadSignedPreKey(SIGNED_PRE_KEY_INITIAL_ID)
        val oneTimePreKeys = (1..ONE_TIME_PRE_KEY_COUNT).mapNotNull { id ->
            runCatching { store.loadPreKey(id).serialize() }.getOrNull()
        }
        val kyberRecord = runCatching {
            store.loadKyberPreKey(KYBER_PRE_KEY_INITIAL_ID)
        }.getOrNull()

        identityStore.write(
            SignalIdentityStore.Snapshot(
                identityKeyPair = store.identityKeyPair.serialize(),
                registrationId = store.localRegistrationId,
                signedPreKeyId = signedPreKey.id,
                signedPreKeyRecord = signedPreKey.serialize(),
                oneTimePreKeyRecords = oneTimePreKeys,
                kyberPreKeyRecord = kyberRecord?.serialize(),
                kyberPreKeyId = kyberRecord?.id ?: 0,
                preKeysUploaded = preKeysUploaded,
            )
        )
    }

    /**
     * Build the payload for `POST /v1/keys/prekeys`. Called exactly once
     * after OTP verify — gated by [SignalIdentityStore.Snapshot.preKeysUploaded].
     */
    fun uploadBatch(): UploadBatch {
        val store = loadOrGenerate()
        val snapshot = identityStore.read()
            ?: error("Identity snapshot missing after loadOrGenerate()")
        val signedPreKey = store.loadSignedPreKey(snapshot.signedPreKeyId)
        val kyberRecord = snapshot.kyberPreKeyRecord?.let { KyberPreKeyRecord(it) }
            ?: error("Kyber pre-key record missing — regenerate before upload")
        val oneTimePublics: List<PreKeyPublic> = (1..ONE_TIME_PRE_KEY_COUNT).mapNotNull { id ->
            runCatching {
                val record = store.loadPreKey(id)
                PreKeyPublic(
                    keyId = record.id,
                    publicKey = Base64.encodeToString(
                        record.keyPair.publicKey.serialize(),
                        Base64.NO_WRAP,
                    ),
                )
            }.getOrNull()
        }

        return UploadBatch(
            oneTimePreKeys = oneTimePublics,
            signedPreKey = SignedPreKeyDto(
                keyId = signedPreKey.id,
                publicKey = Base64.encodeToString(
                    signedPreKey.keyPair.publicKey.serialize(),
                    Base64.NO_WRAP,
                ),
                signature = Base64.encodeToString(
                    signedPreKey.signature,
                    Base64.NO_WRAP,
                ),
            ),
            kyberPreKey = KyberPreKeyDto(
                keyId = kyberRecord.id,
                publicKey = Base64.encodeToString(
                    kyberRecord.keyPair.publicKey.serialize(),
                    Base64.NO_WRAP,
                ),
                signature = Base64.encodeToString(
                    kyberRecord.signature,
                    Base64.NO_WRAP,
                ),
            ),
            identityPublicKey = Base64.encodeToString(
                store.identityKeyPair.publicKey.serialize(),
                Base64.NO_WRAP,
            ),
            registrationId = store.localRegistrationId,
        )
    }

    fun markPreKeysUploaded() {
        identityStore.markPreKeysUploaded()
    }

    fun arePreKeysUploaded(): Boolean = identityStore.read()?.preKeysUploaded == true

    /**
     * Wipe every persisted Signal artefact: identity key pair,
     * registration id, signed + one-time + kyber pre-keys, peer
     * sessions, pinned identities, and the preKeysUploaded flag.
     * Also drops the in-memory `InMemorySignalProtocolStore` cache so
     * the next consumer calls [loadOrGenerate] fresh.
     *
     * Called from [RemoteAuthRepository.signOut] to close the
     * cross-account leak P1 flagged on the polling security review:
     * without this, a re-auth under a different phone on the same
     * device would inherit the previous user's identity keys (and
     * therefore keep decrypting inbound envelopes targeted at the
     * previous identity).
     */
    @Synchronized
    fun clear() {
        identityStore.clear()
        cachedStore = null
    }

    companion object {
        /** Size of the one-time pre-key pool generated at first launch. */
        const val ONE_TIME_PRE_KEY_COUNT: Int = 100

        /**
         * Initial signed pre-key id. Signed pre-keys rotate every ~7 days
         * — the rotation logic will bump this counter once the key
         * rotation job lands.
         */
        const val SIGNED_PRE_KEY_INITIAL_ID: Int = 1

        /**
         * Initial Kyber (PQXDH) pre-key id. Kyber keys rotate on a
         * slower cadence (Signal production uses ~one per month) — bump
         * this counter when the rotation job lands.
         */
        const val KYBER_PRE_KEY_INITIAL_ID: Int = 1
    }
}
