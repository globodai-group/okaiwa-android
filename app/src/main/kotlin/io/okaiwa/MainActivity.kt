package io.okaiwa

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import io.okaiwa.core.navigation.AppNavigation
import io.okaiwa.core.theme.OkaiwaTheme

/**
 * Main activity for Okaiwa.
 *
 * Hosts the full Compose navigation graph. Screen security (FLAG_SECURE)
 * is applied selectively on sensitive screens via `SecureScreen()` — it
 * is deliberately NOT applied to the activity window, so testers can
 * take screenshots of normal UX during development.
 *
 * Extends [AppCompatActivity] (not plain ComponentActivity) so the
 * pre-Android 13 compatibility shim behind `AppCompatDelegate
 * .setApplicationLocales(...)` can hook the configuration pipeline and
 * apply the user's chosen locale without restarting the activity. The
 * Compose tree itself doesn't use any AppCompat views — AppCompat is
 * carrying the locale machinery only.
 */
@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            OkaiwaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    AppNavigation()
                }
            }
        }
    }
}
