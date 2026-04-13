package io.okaiwa.features.chat.data.local

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.sqlite.db.SupportSQLiteDatabase
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
        DeadLetterCountEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class OkaiwaDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun messageDao(): MessageDao
    abstract fun deadLetterCountDao(): DeadLetterCountDao

    companion object {
        /** Build the singleton instance. DI provides this once at app scope. */
        fun build(context: Context): OkaiwaDatabase {
            // Explicitly load libsqlcipher.so BEFORE handing a
            // SupportOpenHelperFactory to Room. The previous comment
            // claimed SupportOpenHelperFactory triggered the static
            // init in net.zetetic.database.sqlcipher.SQLiteDatabase,
            // but that's wrong — only touching SQLiteDatabase itself
            // does, and Room opens the connection via
            // SQLiteConnection.nativeOpen which is a raw JNI method.
            // Without the explicit load, every first-chat-open throws
            // `UnsatisfiedLinkError: No implementation found for long
            // net.zetetic.database.sqlcipher.SQLiteConnection.nativeOpen`
            // (reported from the field on APK 1.5.57).
            System.loadLibrary("sqlcipher")

            val passphrase = loadOrGeneratePassphrase(context)
            val factory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                OkaiwaDatabase::class.java,
                AppConfig.DATABASE_NAME,
            )
                .openHelperFactory(factory)
                .addMigrations(MIGRATION_1_2)
                // Destructive fallback for unknown versions only.
                // Real upgrades go through the explicit Migration
                // objects above — we'd rather drop unknown schemas
                // than silently ship an inconsistent DB to users.
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
        }

        /**
         * v1 → v2 : add the persisted dead-letter counter table.
         *
         * The in-memory `ConcurrentHashMap` reset on every cold start
         * let a hostile relay loop poison envelopes indefinitely by
         * waiting for the app to be killed (P1 from the polling
         * security review). Persisting inside the already-encrypted
         * SQLCipher DB closes the hole without additional key
         * material.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS dead_letter_counts (
                        messageId TEXT PRIMARY KEY NOT NULL,
                        count INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
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
