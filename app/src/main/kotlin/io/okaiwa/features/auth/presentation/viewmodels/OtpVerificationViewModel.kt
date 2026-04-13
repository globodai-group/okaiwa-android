package io.okaiwa.features.auth.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.okaiwa.features.auth.data.session.SessionStore
import io.okaiwa.features.auth.domain.usecases.VerifyOtpUseCase
import io.okaiwa.features.profile.data.repositories.RemoteProfileRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Per-screen VM behind [io.okaiwa.features.auth.presentation.screens.OtpVerificationScreen].
 *
 * Submits the 6-digit code to `POST /v1/auth/verify` via the repository,
 * which pulls the phoneHash from the SessionStore stashed at register
 * time. On success the repository persists the opaque access + refresh
 * tokens into EncryptedSharedPreferences and this VM flips `verified`,
 * letting the Navigation layer pop the OTP route.
 *
 * Where the user lands NEXT depends on whether the server already
 * holds a profile for this account. We fetch /v1/profile/me right
 * after verify and decide:
 *   - profile.username present → server already knows this user
 *     (repeat-install / multi-device login), skip ProfileSetup
 *     and land on Main directly. The local profileSetupDone flag
 *     is flipped to match.
 *   - profile.username absent → first-time setup, route to
 *     ProfileSetup as before.
 *
 * Without this branch every reinstall (which wipes the local
 * EncryptedSharedPreferences) forced the user to retype their
 * profile and immediately failed with HTTP 409 "username taken"
 * because the server still held the previous registration.
 */
@HiltViewModel
class OtpVerificationViewModel @Inject constructor(
    private val verifyOtpUseCase: VerifyOtpUseCase,
    private val sessionStore: SessionStore,
    private val profileRepository: RemoteProfileRepository,
) : ViewModel() {

    enum class NextStep { Main, ProfileSetup }

    data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val verified: Boolean = false,
        // Null until the post-verify bootstrap decides; the nav layer
        // should treat `verified && nextStep != null` as the trigger.
        val nextStep: NextStep? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun submit(code: String) {
        if (_state.value.isLoading || _state.value.verified) return
        _state.value = UiState(isLoading = true)

        viewModelScope.launch {
            // The first argument to the use case is the session-request
            // token — we no longer carry one per-screen because the
            // repository reads the in-flight phoneHash from the
            // SessionStore. Passing the code twice is by design: the
            // validator keeps it compatible with the stub impl.
            verifyOtpUseCase(sessionToken = code, otpCode = code)
                .onSuccess {
                    // Server is the source of truth: if the account
                    // already has a username, we MUST NOT push the
                    // user back through ProfileSetup — they'd retype
                    // the same handle and hit a 409. Pre-existing
                    // accounts go straight to Main. Typed result is
                    // load-bearing: a raw `null` on transient failure
                    // would have routed to ProfileSetup and triggered
                    // the very 409 this bootstrap exists to prevent.
                    when (val outcome = profileRepository.fetchUsernameForBootstrap()) {
                        is RemoteProfileRepository.ProfileBootstrap.Existing -> {
                            sessionStore.markProfileSetupDone()
                            _state.value = UiState(
                                verified = true,
                                nextStep = NextStep.Main,
                            )
                        }
                        RemoteProfileRepository.ProfileBootstrap.NewUser -> {
                            _state.value = UiState(
                                verified = true,
                                nextStep = NextStep.ProfileSetup,
                            )
                        }
                        RemoteProfileRepository.ProfileBootstrap.AuthFailure -> {
                            _state.value = UiState(
                                error = "Session expirée, veuillez recommencer",
                            )
                        }
                        RemoteProfileRepository.ProfileBootstrap.TransientFailure -> {
                            _state.value = UiState(
                                error = "Connexion au serveur impossible, réessayez",
                            )
                        }
                    }
                }
                .onFailure { t ->
                    _state.value = UiState(error = t.message ?: "Code incorrect")
                }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
