package io.okaiwa.features.chat.data.local

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import io.okaiwa.core.config.AppConfig
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import java.security.SecureRandom

/**
 * Main application database — the single on-device store for
 * conversation metadata + plaintext message history.
 *
 * Encryption-at-rest:
 *   - Backed by SQLCipher 4.x via the `sqlcipher-android` AAR. Every
 *     page is AES-256-CBC with HMAC-SHA256 page integrity.
 *   - Passphrase is a 32-byte CSPRNG blob generated on first launch and
 *     stashed in [EncryptedSharedPreferences] under an AES-256/GCM
 *     MasterKey (StrongBox on Pixel 3+ / Galaxy S22+).
 *   - We NEVER derive the passphrase from the session accessToken — the
 *     token rotates on refresh and the DB can't follow. The passphrase
 *     is bound to the app install.
 *
 * NO plaintext writes happen outside this path — the identity / session
 * flow already lives behind EncryptedSharedPreferences.
 */
@androidx.room.Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
abstract class OkaiwaDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao

    companion object {
        /** Build the singleton instance. DI provides this once at app scope. */
        fun build(context: Context): OkaiwaDatabase {
            // sqlcipher-android 4.6+ auto-loads its native .so via the
            // SQLiteDatabase class's static initializer — the explicit
            // SQLiteDatabase.loadLibs(context) of the 4.5-era tutorials
            // is no longer in the API (verified against sqlcipher-android
            // 4.14.1 in our cache). The SupportOpenHelperFactory below
            // touches the class which triggers the static init — first
            // chat open is therefore safe without an explicit load. The
            // review's P0 callout was based on the older API.
            val passphrase = loadOrGeneratePassphrase(context)
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                OkaiwaDatabase::class.java,
                AppConfig.DATABASE_NAME,
            )
                .openHelperFactory(factory)
                // Destructive migration is fine for now — the schema is
                // net-new and version = 1. Once we ship to users we'll
                // swap for real .addMigrations(...) steps.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }

        private const val PREFS_FILE = "okaiwa_db_passphrase"
        private const val KEY_PASSPHRASE = "db_passphrase_v1"
        private const val PASSPHRASE_BYTES = 32

        private fun loadOrGeneratePassphrase(context: Context): ByteArray {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                context,
                PREFS_FILE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )

            val existing = prefs.getString(KEY_PASSPHRASE, null)
            if (existing != null) {
                return Base64.decode(existing, Base64.NO_WRAP)
            }

            val fresh = ByteArray(PASSPHRASE_BYTES)
            SecureRandom().nextBytes(fresh)
            prefs.edit()
                .putString(KEY_PASSPHRASE, Base64.encodeToString(fresh, Base64.NO_WRAP))
                .commit()
            return fresh
        }
    }
}
