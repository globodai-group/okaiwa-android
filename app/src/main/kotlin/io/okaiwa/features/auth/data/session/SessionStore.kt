package io.okaiwa.features.auth.data.session

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
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
 * Persistence policy by field:
 *   - accountId, accessToken, refreshToken, expiresAt → on disk (encrypted)
 *   - phoneHash → IN-MEMORY ONLY, never written to disk
 *
 * The phone hash is excluded from disk by design: it lets an attacker
 * with EncryptedSharedPreferences read access (rooted device, malware
 * with elevated privileges) confirm the device owner's phone number
 * by hashing every candidate E.164 number and comparing — a small,
 * cheap brute-force against a 12-digit space. Keeping it in RAM means
 * a clean app launch on a known device starts with a null phoneHash
 * and the user is asked for their number again before /v1/auth/verify
 * can be called.
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
        // Atomic write: serialize the whole record to one JSON blob and
        // persist under a single key. The previous multi-key putString
        // pattern was non-atomic — a process kill or Keychain failure
        // mid-`apply()` could leave a half-written record where
        // accessToken was set but deviceToken was not, which made the
        // splash gate route to Welcome while the stale tokens remained
        // on disk indefinitely (security review on android@386d11d).
        // Single-key write inherits SharedPreferences' all-or-nothing
        // guarantee on the underlying file replacement.
        val persisted = PersistedSession(
            schemaVersion = SCHEMA_VERSION,
            accountId = session.accountId,
            accessToken = session.accessToken,
            refreshToken = session.refreshToken,
            expiresAtEpochSeconds = session.expiresAtEpochSeconds,
            deviceId = session.deviceId,
            deviceToken = session.deviceToken,
            profileSetupDone = session.profileSetupDone,
        )
        val ok = prefs.edit()
            .putString(KEY_SESSION_BLOB, json.encodeToString(PersistedSession.serializer(), persisted))
            .commit() // synchronous so a caller observing the result knows the write landed.
        if (!ok) {
            // Persistence failed — DO NOT update the in-memory flow.
            // The user's next interaction will see the old session and
            // we'll surface a "session expired" path naturally on the
            // next API call.
            Log.e(TAG, "Failed to persist session — disk write returned false")
            return
        }
        _sessionFlow.value = session
    }

    /**
     * Shorthand to mark the user's profile setup as complete without
     * having to reconstruct the full session object at every call site.
     * Persists the flag + updates the observer flow.
     */
    fun markProfileSetupDone() {
        val existing = _sessionFlow.value ?: return
        save(existing.copy(profileSetupDone = true))
    }

    fun clear() {
        prefs.edit().clear().commit()
        _sessionFlow.value = null
    }

    private fun loadFromDisk(): Session? {
        val raw = prefs.getString(KEY_SESSION_BLOB, null) ?: return null
        val persisted = runCatching {
            json.decodeFromString(PersistedSession.serializer(), raw)
        }.getOrNull()

        if (persisted == null || persisted.schemaVersion != SCHEMA_VERSION) {
            // Schema mismatch or corrupted blob — wipe and force a
            // clean slate. Better to make the user re-authenticate
            // than to ship them into a half-hydrated state where
            // some fields are missing and produce subtle bugs.
            prefs.edit().clear().commit()
            return null
        }

        // phoneHash is intentionally not restored — see the class kdoc.
        // The session is hydrated without it; verify will fail until
        // the user re-enters the phone, which calls register() again
        // and refreshes the in-memory hash.
        return Session(
            accountId = persisted.accountId,
            phoneHash = "",
            accessToken = persisted.accessToken,
            refreshToken = persisted.refreshToken,
            expiresAtEpochSeconds = persisted.expiresAtEpochSeconds,
            deviceId = persisted.deviceId,
            deviceToken = persisted.deviceToken,
            profileSetupDone = persisted.profileSetupDone,
        )
    }

    companion object {
        private const val TAG = "SessionStore"
        private const val PREFS_FILE = "okaiwa_session"
        private const val KEY_SESSION_BLOB = "session_v1"
        /** Bump when the PersistedSession shape changes incompatibly. */
        private const val SCHEMA_VERSION = 1

        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
        }
    }
}

/**
 * Wire-format struct persisted in EncryptedSharedPreferences. Kept
 * separate from the in-memory [Session] so we can include a schema
 * version tag (for safe future migrations) and explicitly EXCLUDE the
 * phoneHash from disk.
 */
@Serializable
private data class PersistedSession(
    val schemaVersion: Int,
    val accountId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val deviceId: String,
    val deviceToken: String,
    val profileSetupDone: Boolean,
)

/**
 * Minimal on-device session record. The `accountId` is returned by the
 * identity service on register, the tokens by verify/refresh, and
 * `phoneHash` is kept client-side so the verify step can re-submit the
 * same hash that was used at registration without asking the user to
 * re-enter the number.
 *
 * `deviceId` + `deviceToken` are issued by the identity service at
 * verify time and consumed by the relay service exclusively — the
 * identity service has no business reading them once minted.
 */
data class Session(
    val accountId: String,
    val phoneHash: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val deviceId: String = "",
    val deviceToken: String = "",
    /**
     * Whether the post-verify profile setup (username, displayName, …)
     * has been completed. Used by the navigation layer to decide
     * between ProfileSetup and Main on cold start.
     */
    val profileSetupDone: Boolean = false,
) {
    val isFresh: Boolean
        get() = System.currentTimeMillis() / 1000L < expiresAtEpochSeconds

    val isVerified: Boolean
        get() = accessToken.isNotEmpty() && deviceToken.isNotEmpty()
}
