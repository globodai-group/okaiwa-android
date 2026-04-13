package io.okaiwa.features.auth.data.session

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persistent home for the identity service session tokens.
 *
 * Backed by EncryptedSharedPreferences so the access + refresh tokens
 * never touch disk unencrypted. The master key is generated once by
 * the Android Keystore (AES-256/GCM, hardware-backed on StrongBox
 * devices) and held under the alias `_androidx_security_master_key_`.
 *
 * A single-source in-memory StateFlow (`sessionFlow`) mirrors the
 * persisted pair so Compose screens can observe sign-in/out without
 * polling SharedPreferences.
 */
@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
) {
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

    private val _sessionFlow = MutableStateFlow<Session?>(loadFromDisk())
    val sessionFlow: StateFlow<Session?> = _sessionFlow.asStateFlow()

    fun current(): Session? = _sessionFlow.value

    fun save(session: Session) {
        prefs.edit()
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putLong(KEY_EXPIRES_AT, session.expiresAtEpochSeconds)
            .putString(KEY_ACCOUNT_ID, session.accountId)
            .putString(KEY_PHONE_HASH, session.phoneHash)
            .apply()
        _sessionFlow.value = session
    }

    fun clear() {
        prefs.edit().clear().apply()
        _sessionFlow.value = null
    }

    private fun loadFromDisk(): Session? {
        val access = prefs.getString(KEY_ACCESS, null) ?: return null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return null
        val accountId = prefs.getString(KEY_ACCOUNT_ID, null) ?: return null
        val phoneHash = prefs.getString(KEY_PHONE_HASH, null) ?: return null
        val expiresAt = prefs.getLong(KEY_EXPIRES_AT, 0L)
        return Session(
            accountId = accountId,
            phoneHash = phoneHash,
            accessToken = access,
            refreshToken = refresh,
            expiresAtEpochSeconds = expiresAt,
        )
    }

    companion object {
        private const val PREFS_FILE = "okaiwa_session"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EXPIRES_AT = "expires_at_epoch_seconds"
        private const val KEY_ACCOUNT_ID = "account_id"
        private const val KEY_PHONE_HASH = "phone_hash"
    }
}

/**
 * Minimal on-device session record. The `accountId` is returned by the
 * identity service on register, the tokens by verify/refresh, and
 * `phoneHash` is kept client-side so the verify step can re-submit the
 * same hash that was used at registration without asking the user to
 * re-enter the number.
 */
data class Session(
    val accountId: String,
    val phoneHash: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
) {
    val isFresh: Boolean
        get() = System.currentTimeMillis() / 1000L < expiresAtEpochSeconds
}
