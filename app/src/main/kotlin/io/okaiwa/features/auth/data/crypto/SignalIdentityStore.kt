package io.okaiwa.features.auth.data.crypto

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Encrypted persistence for the Signal Protocol key material.
 *
 * Why a separate file from [io.okaiwa.features.auth.data.session.SessionStore]:
 *   - Lifecycle: the Signal keys live across sign-outs (re-registering
 *     while keeping the same device identity is possible and desirable).
 *     Session tokens do not.
 *   - Blast radius: a bug that clears the session tokens is recoverable
 *     by asking for a fresh OTP. A bug that clears the identity keys
 *     invalidates every existing conversation, so the store is isolated.
 *
 * Backing store: [EncryptedSharedPreferences] with an Android Keystore
 * master key (AES-256/GCM, StrongBox-backed on Pixel 3+ / Galaxy S22+).
 * All blobs are base64-encoded before storage — the library encrypts
 * the entire value, so base64 is only for the String API, not security.
 */
@Singleton
class SignalIdentityStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /**
     * Serialised snapshot of every key [SignalIdentityKeys] needs to
     * rebuild the in-memory protocol store after process death.
     *
     * The arrays are base64-encoded at the prefs boundary; in-memory
     * we keep raw bytes so the caller can pass them straight to
     * libsignal's deserialization constructors.
     */
    data class Snapshot(
        val identityKeyPair: ByteArray,
        val registrationId: Int,
        val signedPreKeyId: Int,
        val signedPreKeyRecord: ByteArray,
        val oneTimePreKeyRecords: List<ByteArray>,
    )

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

        EncryptedSharedPreferences.create(
            context,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun read(): Snapshot? {
        val identity = prefs.getString(KEY_IDENTITY, null) ?: return null
        val registrationId = prefs.getInt(KEY_REGISTRATION_ID, 0).takeIf { it > 0 }
            ?: return null
        val signedPreKeyId = prefs.getInt(KEY_SIGNED_PRE_KEY_ID, 0).takeIf { it > 0 }
            ?: return null
        val signedPreKeyRecord = prefs.getString(KEY_SIGNED_PRE_KEY_RECORD, null)
            ?: return null
        val oneTimeCount = prefs.getInt(KEY_ONE_TIME_COUNT, 0)

        val oneTimePreKeys = (0 until oneTimeCount).mapNotNull { index ->
            prefs.getString(oneTimeKey(index), null)?.let {
                Base64.decode(it, Base64.NO_WRAP)
            }
        }

        return Snapshot(
            identityKeyPair = Base64.decode(identity, Base64.NO_WRAP),
            registrationId = registrationId,
            signedPreKeyId = signedPreKeyId,
            signedPreKeyRecord = Base64.decode(signedPreKeyRecord, Base64.NO_WRAP),
            oneTimePreKeyRecords = oneTimePreKeys,
        )
    }

    fun write(snapshot: Snapshot) {
        val editor = prefs.edit()
            .putString(
                KEY_IDENTITY,
                Base64.encodeToString(snapshot.identityKeyPair, Base64.NO_WRAP),
            )
            .putInt(KEY_REGISTRATION_ID, snapshot.registrationId)
            .putInt(KEY_SIGNED_PRE_KEY_ID, snapshot.signedPreKeyId)
            .putString(
                KEY_SIGNED_PRE_KEY_RECORD,
                Base64.encodeToString(snapshot.signedPreKeyRecord, Base64.NO_WRAP),
            )
            .putInt(KEY_ONE_TIME_COUNT, snapshot.oneTimePreKeyRecords.size)

        // Clear any leftover one-time entries from a prior, larger pool
        // before writing the new batch — avoids zombie keys from stale runs.
        prefs.all.keys
            .filter { it.startsWith(ONE_TIME_KEY_PREFIX) }
            .forEach { editor.remove(it) }

        snapshot.oneTimePreKeyRecords.forEachIndexed { index, bytes ->
            editor.putString(
                oneTimeKey(index),
                Base64.encodeToString(bytes, Base64.NO_WRAP),
            )
        }

        editor.apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun oneTimeKey(index: Int): String = "$ONE_TIME_KEY_PREFIX$index"

    companion object {
        private const val PREFS_FILE = "okaiwa_signal_identity"
        private const val KEY_IDENTITY = "identity_key_pair"
        private const val KEY_REGISTRATION_ID = "registration_id"
        private const val KEY_SIGNED_PRE_KEY_ID = "signed_pre_key_id"
        private const val KEY_SIGNED_PRE_KEY_RECORD = "signed_pre_key_record"
        private const val KEY_ONE_TIME_COUNT = "one_time_count"
        private const val ONE_TIME_KEY_PREFIX = "one_time_"
    }
}
