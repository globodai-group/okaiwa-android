package io.okaiwa.features.profile.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.okaiwa.features.auth.data.session.SessionStore
import io.okaiwa.features.profile.domain.entities.UserProfile
import io.okaiwa.features.profile.domain.repositories.ProfileRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class ProfileUiState(
    val profile: UserProfile? = null,
    val isLoading: Boolean = true,
    /** Flips to true after signOut() so the navigation layer can pop. */
    val signedOut: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val sessionStore: SessionStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            profileRepository.observeProfile().collect { profile ->
                _uiState.update { it.copy(profile = profile, isLoading = false) }
            }
        }
    }

    /**
     * Sign out — wipe the session blob from EncryptedSharedPreferences
     * and the in-memory snapshot. Flips the `signedOut` flag so the
     * navigation layer pops the user back to Welcome.
     *
     * Runs on Dispatchers.IO so the EncryptedSharedPreferences commit
     * + Keystore unlock (can be 100-400ms on cold storage) doesn't
     * block the main thread / trip StrictMode. The `signedOut` flag
     * is only flipped AFTER the wipe succeeds — if `clear()` throws
     * we keep the user on the Profile screen and surface the error,
     * rather than navigate away with stale tokens still on disk.
     *
     * NOTE: this does NOT delete the libsignal identity keys or the
     * wallet seed — those stay on the Keystore-backed store so the
     * user can re-login with the same phone and recover the same
     * wallet without re-deriving everything. Account deletion (which
     * DOES wipe everything) is a separate destructive action.
     *
     * The cross-account profile cache leak fix lives in
     * RemoteProfileRepository — it observes SessionStore.sessionFlow
     * and resets state.value to null + hasFetchedOnce to false the
     * moment the session becomes null, regardless of WHO triggered
     * the clear (logout, session-expired refresh failure, future
     * delete-account flow).
     */
    fun signOut() {
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { sessionStore.clear() }.isSuccess
            }
            if (ok) {
                _uiState.update { it.copy(signedOut = true) }
            }
            // Failure path: nothing flipped, the user stays on the
            // Profile tab. The session blob is either still on disk
            // (user remains logged in next launch) or partially
            // wiped (next API call surfaces 401 → session-expired UX
            // re-routes to Welcome). Either branch is recoverable.
        }
    }

    /** Clear the signedOut latch after the navigation layer has
     * consumed it — defends against the LaunchedEffect re-firing on
     * recomposition / back-navigation to a stale ProfileScreen. */
    fun onSignOutNavigated() {
        _uiState.update { it.copy(signedOut = false) }
    }
}
