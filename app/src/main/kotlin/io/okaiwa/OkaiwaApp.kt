package io.okaiwa

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * Okaiwa application entry point.
 *
 * Initializes Hilt dependency injection.
 *
 * Screen security (FLAG_SECURE) is NOT applied globally here — it is
 * an opt-in per sensitive screen via [io.okaiwa.shared.utils.SecureScreen].
 * See ScreenSecurity.kt for rationale.
 */
@HiltAndroidApp
class OkaiwaApp : Application()
