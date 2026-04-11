package io.okaiwa.features.auth.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.okaiwa.features.auth.domain.entities.User
import io.okaiwa.features.auth.domain.usecases.RegisterUserUseCase
import io.okaiwa.features.auth.domain.usecases.VerifyOtpUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for authentication screens.
 */
data class AuthUiState(
    val phoneNumber: String = "",
    val otpCode: String = "",
    val sessionToken: String? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    val step: AuthStep = AuthStep.PhoneInput,
    val user: User? = null,
) {
    val isPhoneValid: Boolean
        get() = phoneNumber.matches(Regex("^\\+[1-9]\\d{6,14}$"))

    val isOtpComplete: Boolean
        get() = otpCode.length == 6 && otpCode.all { it.isDigit() }
}

/**
 * Steps in the authentication flow.
 */
enum class AuthStep {
    PhoneInput,
    OtpVerification,
    ProfileSetup,
    Complete,
}

/**
 * ViewModel for the authentication flow.
 *
 * Manages state for phone input, OTP verification, and profile setup.
 * Uses [StateFlow] for reactive UI updates in Compose.
 */
@HiltViewModel
class AuthViewModel @Inject constructor(
    private val registerUserUseCase: RegisterUserUseCase,
    private val verifyOtpUseCase: VerifyOtpUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun onPhoneNumberChanged(phone: String) {
        _uiState.update { it.copy(phoneNumber = phone, error = null) }
    }

    fun onOtpCodeChanged(otp: String) {
        if (otp.length <= 6) {
            _uiState.update { it.copy(otpCode = otp, error = null) }
        }
    }

    fun requestOtp() {
        val phone = _uiState.value.phoneNumber
        if (phone.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            registerUserUseCase(phone)
                .onSuccess { sessionToken ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sessionToken = sessionToken,
                            step = AuthStep.OtpVerification,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Registration failed",
                        )
                    }
                }
        }
    }

    fun verifyOtp() {
        val state = _uiState.value
        val token = state.sessionToken ?: return
        if (!state.isOtpComplete) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            verifyOtpUseCase(token, state.otpCode)
                .onSuccess { user ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            user = user,
                            step = if (user.isProfileComplete) AuthStep.Complete else AuthStep.ProfileSetup,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Verification failed",
                        )
                    }
                }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun goBackToPhoneInput() {
        _uiState.update {
            it.copy(
                step = AuthStep.PhoneInput,
                otpCode = "",
                sessionToken = null,
                error = null,
            )
        }
    }
}
