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
 * The steps are rendered by [WalletOnboardingFlow]; this view-model
 * holds the draft wallet state (chosen method, generated mnemonic,
 * verification indices, name) and exposes the active step so nav
 * transitions stay inside the composable without polluting the
 * NavHost with six throwaway routes.
 */
enum class WalletOnboardingStep {
    Method,
    SecurityTips,
    SeedPhraseDisplay,
    SeedPhraseVerify,
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
)

@HiltViewModel
class WalletOnboardingViewModel @Inject constructor() : ViewModel() {

    private val _uiState = MutableStateFlow(WalletOnboardingUiState())
    val uiState: StateFlow<WalletOnboardingUiState> = _uiState.asStateFlow()

    fun selectMethod(method: WalletCreationMethod) {
        _uiState.update { it.copy(method = method, step = WalletOnboardingStep.SecurityTips) }
    }

    fun onSecurityTipsAccepted() {
        val mnemonic = MockMnemonicGenerator.generate24()
        // Three evenly-spread positions make verification look like
        // Ledger / Trezor: you're never asked for two adjacent words.
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

    fun onSeedPhraseAcknowledged() {
        _uiState.update { it.copy(step = WalletOnboardingStep.SeedPhraseVerify) }
    }

    fun onVerificationSuccess() {
        _uiState.update { it.copy(step = WalletOnboardingStep.NameWallet) }
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
        val previous = when (current) {
            WalletOnboardingStep.Method -> return false
            WalletOnboardingStep.SecurityTips -> WalletOnboardingStep.Method
            WalletOnboardingStep.SeedPhraseDisplay -> WalletOnboardingStep.SecurityTips
            WalletOnboardingStep.SeedPhraseVerify -> WalletOnboardingStep.SeedPhraseDisplay
            WalletOnboardingStep.NameWallet -> WalletOnboardingStep.SeedPhraseVerify
            WalletOnboardingStep.Ready -> return false
        }
        _uiState.update { it.copy(step = previous) }
        return true
    }
}
