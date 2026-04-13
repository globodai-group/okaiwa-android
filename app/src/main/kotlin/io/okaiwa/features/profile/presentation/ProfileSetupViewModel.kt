package io.okaiwa.features.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.okaiwa.features.auth.data.session.SessionStore
import io.okaiwa.features.profile.data.remote.ProfileApi
import io.okaiwa.features.profile.data.remote.UpdateProfileRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Backs the post-OTP "Pick your username" screen. Validates the
 * username against the same regex the server enforces
 * (`[a-zA-Z0-9_]{3,32}`), POSTs `PUT /v1/profile` with the chosen
 * fields, and on success marks the SessionStore so the splash will
 * route directly to Main on subsequent launches.
 *
 * The Skip path leaves the profile blank server-side but still flips
 * the session flag so the user isn't trapped on this screen — they
 * can fill it in later from the Profile tab.
 */
@HiltViewModel
class ProfileSetupViewModel @Inject constructor(
    private val profileApi: ProfileApi,
    private val sessionStore: SessionStore,
) : ViewModel() {

    data class UiState(
        val username: String = "",
        val displayName: String = "",
        val bio: String = "",
        val isSubmitting: Boolean = false,
        val error: String? = null,
        val done: Boolean = false,
    ) {
        val isUsernameValid: Boolean
            get() = username.matches(Regex("^[a-zA-Z0-9_]{3,32}$"))

        val isSubmitEnabled: Boolean
            get() = !isSubmitting && isUsernameValid
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onUsernameChanged(v: String) { _state.value = _state.value.copy(username = v.take(32), error = null) }
    fun onDisplayNameChanged(v: String) { _state.value = _state.value.copy(displayName = v.take(64), error = null) }
    fun onBioChanged(v: String) { _state.value = _state.value.copy(bio = v.take(300), error = null) }

    fun submit() {
        val s = _state.value
        if (!s.isSubmitEnabled) return
        val accountId = sessionStore.current()?.accountId
        if (accountId.isNullOrEmpty()) {
            _state.value = s.copy(error = "Session expirée — reconnectez-vous.")
            return
        }

        _state.value = s.copy(isSubmitting = true, error = null)
        viewModelScope.launch {
            val result = runCatching {
                profileApi.updateProfile(
                    accountId = accountId,
                    body = UpdateProfileRequest(
                        username = s.username,
                        displayName = s.displayName.takeIf { it.isNotBlank() },
                        bio = s.bio.takeIf { it.isNotBlank() },
                    ),
                )
            }
            result
                .onSuccess { response ->
                    _state.value = when {
                        response.code() == 409 ->
                            _state.value.copy(isSubmitting = false, error = "Ce nom d'utilisateur est déjà pris.")
                        response.isSuccessful -> {
                            sessionStore.markProfileSetupDone()
                            _state.value.copy(isSubmitting = false, done = true)
                        }
                        else ->
                            _state.value.copy(isSubmitting = false, error = "Erreur ${response.code()}")
                    }
                }
                .onFailure { t ->
                    _state.value = _state.value.copy(isSubmitting = false, error = t.message ?: "Erreur réseau")
                }
        }
    }

    /**
     * Skip — no PUT call. The user lands on Main without a username;
     * the Profile tab CTA can prompt them to complete it later.
     */
    fun skip() {
        sessionStore.markProfileSetupDone()
        _state.value = _state.value.copy(done = true)
    }
}
