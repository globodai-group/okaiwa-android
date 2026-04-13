package io.okaiwa.core.i18n

import android.content.Context
import android.util.Log
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.Locale

/**
 * Stores and applies the user's preferred application locale.
 *
 * - On first launch (no stored preference), the system locale drives the
 *   resources. We hand that back to the framework by setting the active
 *   locale list to [LocaleListCompat.getEmptyLocaleList].
 * - Once the user picks Français / English from the in-app language
 *   picker, the tag is persisted into EncryptedSharedPreferences under
 *   `okaiwa.locale` and [AppCompatDelegate.setApplicationLocales] is
 *   called so the UI re-composes against the new configuration without
 *   an activity restart. On Android 13+ this also pushes the preference
 *   into the framework's LocaleManager so the system Settings → App
 *   languages surface stays in sync.
 *
 * The preference is stored in EncryptedSharedPreferences for parity with
 * the rest of the app's persistence (session tokens, Signal identity,
 * profile) rather than for any secrecy reason — the locale itself is not
 * sensitive, but routing everything through the same encrypted store
 * keeps the data-at-rest story uniform.
 *
 * This is a plain singleton (not Hilt-bound) because we need to invoke
 * it from [io.okaiwa.OkaiwaApp.onCreate] BEFORE Hilt's application graph
 * is wired up, and the stored tag has to take effect before the first
 * Activity reads any string resource.
 */
object LocaleManager {

    private const val TAG = "LocaleManager"
    private const val PREFS_FILE = "okaiwa_locale_prefs"
    private const val KEY_LOCALE_TAG = "okaiwa.locale"

    /** Sentinel meaning "no preference stored — follow the system locale." */
    const val SYSTEM_DEFAULT_TAG = ""

    /** Languages exposed in the in-app picker. IETF BCP-47 tags. */
    val SUPPORTED_TAGS: List<String> = listOf("fr", "en")

    /**
     * Pulls the persisted tag (if any) and pushes it to
     * AppCompatDelegate so resources resolve against the chosen locale.
     * Safe to call more than once; a no-op when the stored value matches
     * the current application locales list.
     */
    fun applyPersistedLocale(context: Context) {
        val tag = readPersistedTag(context)
        val target = if (tag.isNullOrEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(tag)
        }

        // Skip the framework round-trip when we already have the right
        // locales active. This avoids a redundant configuration change
        // on cold start.
        if (AppCompatDelegate.getApplicationLocales() != target) {
            AppCompatDelegate.setApplicationLocales(target)
        }
    }

    /**
     * Persists [languageTag] (e.g. `"fr"`, `"en"`, or [SYSTEM_DEFAULT_TAG]
     * to clear the override) and applies it immediately. The UI will
     * recompose against the new resources on the next frame; no activity
     * restart required.
     */
    fun setLocale(context: Context, languageTag: String) {
        writePersistedTag(context, languageTag)
        val target = if (languageTag.isEmpty()) {
            LocaleListCompat.getEmptyLocaleList()
        } else {
            LocaleListCompat.forLanguageTags(languageTag)
        }
        AppCompatDelegate.setApplicationLocales(target)
    }

    /**
     * Current preference tag as stored on disk — NOT what the framework
     * is actively rendering. Returns an empty string when the user is
     * still on the system default.
     */
    fun currentTag(context: Context): String = readPersistedTag(context).orEmpty()

    /**
     * Resolve the current effective [Locale] for UI logic that can't
     * lean on string resources — for instance, date/time formatting.
     */
    fun currentLocale(context: Context): Locale {
        val tag = currentTag(context)
        return if (tag.isNotEmpty()) {
            Locale.forLanguageTag(tag)
        } else {
            Locale.getDefault()
        }
    }

    // ─────────────────────────────────────────────────────────────────
    // Storage — encrypted so the locale sits in the same bucket as the
    // rest of the app's persisted state.
    // ─────────────────────────────────────────────────────────────────

    private fun prefs(context: Context) = runCatching {
        val masterKey = MasterKey.Builder(context.applicationContext)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context.applicationContext,
            PREFS_FILE,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.onFailure {
        // EncryptedSharedPreferences can fail on weird OEM builds whose
        // AndroidKeyStore is broken (seen on some fire-TV forks). We log
        // and fall through to a null prefs handle — locale just won't
        // persist across launches on that device.
        Log.w(TAG, "Falling back to no locale persistence", it)
    }.getOrNull()

    private fun readPersistedTag(context: Context): String? =
        prefs(context)?.getString(KEY_LOCALE_TAG, null)

    private fun writePersistedTag(context: Context, tag: String) {
        prefs(context)?.edit()?.apply {
            if (tag.isEmpty()) remove(KEY_LOCALE_TAG) else putString(KEY_LOCALE_TAG, tag)
            apply()
        }
    }
}
