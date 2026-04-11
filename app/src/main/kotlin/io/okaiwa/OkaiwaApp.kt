package io.okaiwa

import android.app.Application
import dagger.hilt.android.HiltAndroidApp
import io.okaiwa.shared.utils.ScreenSecurity

/**
 * Okaiwa application entry point.
 *
 * Initializes Hilt dependency injection and applies global security policies
 * such as screenshot protection across all activities.
 */
@HiltAndroidApp
class OkaiwaApp : Application() {

    override fun onCreate() {
        super.onCreate()
        initScreenSecurity()
    }

    private fun initScreenSecurity() {
        ScreenSecurity.registerActivityLifecycleCallbacks(this)
    }
}
