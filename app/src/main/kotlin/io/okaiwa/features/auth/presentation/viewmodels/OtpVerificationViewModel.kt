package io.okaiwa.features.auth.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.okaiwa.features.auth.domain.usecases.VerifyOtpUseCase
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
 * letting the Navigation layer pop the OTP route and land on /main.
 */
@HiltViewModel
class OtpVerificationViewModel @Inject constructor(
    private val verifyOtpUseCase: VerifyOtpUseCase,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val verified: Boolean = false,
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
                    _state.value = UiState(verified = true)
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
