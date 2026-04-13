package io.okaiwa.core.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.okaiwa.features.auth.data.session.Session
import io.okaiwa.features.auth.data.session.SessionStore
import javax.inject.Inject

/**
 * Bridges the [SessionStore] singleton into the AppNavigation's
 * SplashScreen so the app can decide its entry point based on the
 * persisted session state, instead of always starting at Welcome.
 *
 * Routing matrix consumed by [AppNavigation]:
 *   - session == null            → Welcome  (fresh install / signed out)
 *   - session.isVerified is false → Welcome (in-flight register, never verified)
 *   - !session.profileSetupDone   → ProfileSetup (verified but skipped profile)
 *   - else                        → Main
 */
@HiltViewModel
class SessionGateViewModel @Inject constructor(
    sessionStore: SessionStore,
) : ViewModel() {

    /** Snapshot of the session at the moment the splash composable lands. */
    val session: Session? = sessionStore.current()
}
