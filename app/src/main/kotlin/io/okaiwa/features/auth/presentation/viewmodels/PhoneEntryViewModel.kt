package io.okaiwa.features.auth.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.okaiwa.features.auth.domain.usecases.RegisterUserUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Per-screen VM behind [io.okaiwa.features.auth.presentation.screens.PhoneNumberScreen].
 *
 * Drives the "Continuer" button: validates the E.164 number via
 * [RegisterUserUseCase], kicks off `POST /v1/auth/register` through the
 * repository, and exposes loading/error + a `registered` latch so the
 * Navigation layer can push the OTP route once the backend acknowledges
 * the account.
 *
 * The per-screen ViewModel pattern is deliberate: the PhoneEntry and
 * OtpVerification screens share no UI state — only the SessionStore,
 * which is already a @Singleton. Coupling them through a shared VM
 * would only trade that simplicity for NavGraph-scoping boilerplate.
 */
@HiltViewModel
class PhoneEntryViewModel @Inject constructor(
    private val registerUserUseCase: RegisterUserUseCase,
) : ViewModel() {

    data class UiState(
        val isLoading: Boolean = false,
        val error: String? = null,
        val registeredPhoneE164: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun submitPhone(phoneE164: String) {
        if (_state.value.isLoading) return
        _state.value = UiState(isLoading = true)

        viewModelScope.launch {
            registerUserUseCase(phoneE164)
                .onSuccess {
                    _state.value = UiState(registeredPhoneE164 = phoneE164)
                }
                .onFailure { t ->
                    _state.value = UiState(error = t.message ?: "Échec de l'inscription")
                }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    /** Called after the Navigation layer has consumed the registered event. */
    fun onNavigated() {
        _state.value = UiState()
    }
}
