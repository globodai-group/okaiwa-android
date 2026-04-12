package io.okaiwa.shared.utils

import android.app.Activity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

/**
 * Screen security — opt-in FLAG_SECURE protection.
 *
 * FLAG_SECURE is NOT applied globally. The default for every screen is
 * "screenshots allowed" — so testers and reviewers can capture regular
 * UX, and so the tooling that relies on screenshots (accessibility
 * scanner, debug HUD, screen recording) keeps working.
 *
 * FLAG_SECURE must be explicitly requested on screens that display
 * sensitive material:
 *   - Secret conversations with screenshot protection enabled
 *   - Seed phrase reveal
 *   - Private key / mnemonic export
 *   - Safety number / QR verification screen
 *   - Biometric unlock screens
 *
 * Usage from a Composable:
 *
 *   @Composable
 *   fun SeedPhraseScreen() {
 *       SecureScreen()   // applies FLAG_SECURE while this screen is visible
 *       // ...
 *   }
 *
 * The effect is reversed when the screen leaves composition, so normal
 * screens regain screenshot capability automatically.
 */
object ScreenSecurity {

    /**
     * Apply FLAG_SECURE to the host activity's window.
     *
     * @param activity The activity to secure.
     */
    fun applyFlagSecure(activity: Activity) {
        activity.window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
    }

    /**
     * Remove FLAG_SECURE from the host activity's window.
     *
     * @param activity The activity to unsecure.
     */
    fun removeFlagSecure(activity: Activity) {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}

/**
 * Compose-friendly scope that toggles FLAG_SECURE while the current
 * screen is in the composition. Call this at the top of any Composable
 * that displays sensitive content.
 */
@Composable
fun SecureScreen() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        activity?.let { ScreenSecurity.applyFlagSecure(it) }
        onDispose {
            activity?.let { ScreenSecurity.removeFlagSecure(it) }
        }
    }
}

private fun android.content.Context.findActivity(): Activity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
