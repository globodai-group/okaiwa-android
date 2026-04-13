package io.okaiwa.features.wallet.presentation.onboarding

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import io.okaiwa.features.wallet.data.mnemonic.MockMnemonicGenerator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

/**
 * Linear state machine for the "Créer un wallet" onboarding.
 *
 * The steps are rendered by [WalletOnboardingFlow]. The VM branches
 * the flow based on the chosen [WalletCreationMethod]:
 *
 *   SeedPhrase → Method → Tips → SeedDisplay → SeedVerify → Name → Ready
 *   Passkey    → Method → Tips → PasskeyCreation              → Name → Ready
 *
 * The passkey branch skips seed display + verification because the
 * private key lives inside the Secure Enclave / StrongBox; the user
 * authenticates with biometrics and there is nothing to write down.
 */
enum class WalletOnboardingStep {
    Method,
    SecurityTips,
    SeedPhraseDisplay,
    SeedPhraseVerify,
    PasskeyCreation,
    NameWallet,
    Ready,
}

enum class WalletCreationMethod {
    /** 24-word BIP-39 mnemonic. User owns the seed. */
    SeedPhrase,

    /** Passkey + cloud backup (iCloud Keychain / Google Password Manager). */
    Passkey,
}

data class WalletOnboardingUiState(
    val step: WalletOnboardingStep = WalletOnboardingStep.Method,
    val method: WalletCreationMethod = WalletCreationMethod.SeedPhrase,
    val mnemonic: List<String> = emptyList(),
    /**
     * Indices (0..23) of the three words we'll ask the user to confirm
     * during verification. Generated when the mnemonic is displayed so
     * the verify step always checks the same three positions.
     */
    val verifyIndices: List<Int> = emptyList(),
    val walletName: String = "",
    val savedToPasswordManager: Boolean = false,
    val passkeyRegistered: Boolean = false,
)

@HiltViewModel
class WalletOnboardingViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(WalletOnboardingUiState())
    val uiState: StateFlow<WalletOnboardingUiState> = _uiState.asStateFlow()

    fun selectMethod(method: WalletCreationMethod) {
        _uiState.update { it.copy(method = method, step = WalletOnboardingStep.SecurityTips) }
    }

    fun onSecurityTipsAccepted() {
        when (_uiState.value.method) {
            WalletCreationMethod.SeedPhrase -> {
                val mnemonic = MockMnemonicGenerator.generate24()
                val verifyIndices = listOf(
                    (2..7).random(),
                    (10..15).random(),
                    (18..23).random(),
                )
                _uiState.update {
                    it.copy(
                        mnemonic = mnemonic,
                        verifyIndices = verifyIndices,
                        step = WalletOnboardingStep.SeedPhraseDisplay,
                    )
                }
            }

            WalletCreationMethod.Passkey -> {
                _uiState.update { it.copy(step = WalletOnboardingStep.PasskeyCreation) }
            }
        }
    }

    fun onSeedPhraseAcknowledged() {
        _uiState.update { it.copy(step = WalletOnboardingStep.SeedPhraseVerify) }
    }

    fun onVerificationSuccess() {
        _uiState.update { it.copy(step = WalletOnboardingStep.NameWallet) }
    }

    /** Called by the passkey step after the system biometric prompt. */
    fun onPasskeyCreated() {
        _uiState.update {
            it.copy(passkeyRegistered = true, step = WalletOnboardingStep.NameWallet)
        }
    }

    fun setWalletName(name: String) {
        _uiState.update { it.copy(walletName = name) }
    }

    fun confirmName() {
        _uiState.update { it.copy(step = WalletOnboardingStep.Ready) }
    }

    fun markSavedToPasswordManager() {
        _uiState.update { it.copy(savedToPasswordManager = true) }
    }

    /** Navigate back one step — used by the top-bar back arrow. */
    fun previousStep(): Boolean {
        val current = _uiState.value.step
        val method = _uiState.value.method
        val previous = when (current) {
            WalletOnboardingStep.Method -> return false
            WalletOnboardingStep.SecurityTips -> WalletOnboardingStep.Method
            WalletOnboardingStep.SeedPhraseDisplay -> WalletOnboardingStep.SecurityTips
            WalletOnboardingStep.SeedPhraseVerify -> WalletOnboardingStep.SeedPhraseDisplay
            WalletOnboardingStep.PasskeyCreation -> WalletOnboardingStep.SecurityTips
            WalletOnboardingStep.NameWallet -> when (method) {
                WalletCreationMethod.SeedPhrase -> WalletOnboardingStep.SeedPhraseVerify
                WalletCreationMethod.Passkey -> WalletOnboardingStep.PasskeyCreation
            }
            WalletOnboardingStep.Ready -> return false
        }
        _uiState.update { it.copy(step = previous) }
        return true
    }
}
