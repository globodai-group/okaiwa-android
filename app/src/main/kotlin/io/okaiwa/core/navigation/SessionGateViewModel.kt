package io.okaiwa.core.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.okaiwa.features.auth.data.session.SessionStore
import io.okaiwa.features.profile.data.repositories.RemoteProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Bridges the [SessionStore] singleton into the AppNavigation's
 * SplashScreen so the app can decide its entry point based on the
 * persisted session state, instead of always starting at Welcome.
 *
 * Routing matrix consumed by [AppNavigation]:
 *   - session == null             → Welcome       (fresh install / signed out)
 *   - session.isVerified is false → Welcome       (in-flight register, never verified)
 *   - profileSetupDone == true    → Main
 *   - else → the server is asked whether the account already has a
 *     username. If it does (reinstall, multi-device) we flip the local
 *     flag and route straight to Main; otherwise we land on
 *     ProfileSetup. A network / auth failure falls back to
 *     ProfileSetup — the user can still Skip from there.
 *
 * The server check is what closes the reinstall bug: without it, a
 * fresh install that hydrated a persisted `profileSetupDone = false`
 * (e.g. after a partial Skip flow) would push the user back through
 * ProfileSetup and hit HTTP 409 "username taken" on re-submit.
 */
@HiltViewModel
class SessionGateViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val profileRepository: RemoteProfileRepository,
) : ViewModel() {

    enum class Route { Welcome, Main, ProfileSetup }

    private val _route = MutableStateFlow<Route?>(null)
    val route: StateFlow<Route?> = _route.asStateFlow()

    init {
        viewModelScope.launch { resolveInitialRoute() }
    }

    private suspend fun resolveInitialRoute() {
        val session = sessionStore.current()
        if (session == null || !session.isVerified) {
            _route.value = Route.Welcome
            return
        }
        if (session.profileSetupDone) {
            _route.value = Route.Main
            return
        }
        // Ask the server — same decision path used by OtpVerificationViewModel.
        when (profileRepository.fetchUsernameForBootstrap()) {
            is RemoteProfileRepository.ProfileBootstrap.Existing -> {
                sessionStore.markProfileSetupDone()
                _route.value = Route.Main
            }
            RemoteProfileRepository.ProfileBootstrap.NewUser -> {
                _route.value = Route.ProfileSetup
            }
            RemoteProfileRepository.ProfileBootstrap.AuthFailure -> {
                // Token was rejected — force a clean re-auth instead
                // of dropping into a half-authenticated ProfileSetup
                // where the upcoming PUT /v1/profile would also 401.
                _route.value = Route.Welcome
            }
            RemoteProfileRepository.ProfileBootstrap.TransientFailure -> {
                // Network / 5xx — keep legacy behaviour so the user
                // isn't trapped on splash. They can Skip if needed.
                _route.value = Route.ProfileSetup
            }
        }
    }
}
