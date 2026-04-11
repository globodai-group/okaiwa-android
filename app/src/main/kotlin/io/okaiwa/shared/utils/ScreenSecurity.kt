package io.okaiwa.shared.utils

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.view.WindowManager

/**
 * Screen security utility.
 *
 * Applies FLAG_SECURE to all activities to prevent screenshots and
 * screen recording. This is a defense-in-depth measure — even if an
 * attacker gains physical access, sensitive content cannot be captured
 * through the Android screenshot/recording APIs.
 *
 * FLAG_SECURE is applied globally via ActivityLifecycleCallbacks,
 * ensuring no activity is accidentally left unprotected.
 * In debug builds, FLAG_SECURE can be conditionally disabled for testing.
 */
object ScreenSecurity {

    /**
     * Whether screen security is currently enabled.
     * Can be toggled in debug builds for testing.
     */
    @Volatile
    var isEnabled: Boolean = true

    /**
     * Register activity lifecycle callbacks to apply FLAG_SECURE
     * to every activity as it is created.
     *
     * Call this in [Application.onCreate].
     */
    fun registerActivityLifecycleCallbacks(application: Application) {
        application.registerActivityLifecycleCallbacks(
            object : Application.ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {
                    applyFlagSecure(activity)
                }

                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            }
        )
    }

    /**
     * Apply FLAG_SECURE to a specific activity's window.
     *
     * @param activity The activity to secure.
     */
    fun applyFlagSecure(activity: Activity) {
        if (isEnabled) {
            activity.window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }
    }

    /**
     * Remove FLAG_SECURE from a specific activity's window.
     * Only used in debug/testing scenarios.
     *
     * @param activity The activity to unsecure.
     */
    fun removeFlagSecure(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
