package io.okaiwa.features.wallet.presentation.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.okaiwa.features.wallet.domain.entities.BlockchainNetwork
import io.okaiwa.features.wallet.domain.entities.CryptoTransaction
import io.okaiwa.features.wallet.domain.entities.Wallet
import io.okaiwa.features.wallet.domain.repositories.WalletRepository
import io.okaiwa.features.wallet.domain.usecases.SendCryptoUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for the wallet screens.
 */
data class WalletUiState(
    val wallet: Wallet? = null,
    val selectedNetwork: BlockchainNetwork = BlockchainNetwork.Ethereum,
    val transactions: List<CryptoTransaction> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val isSending: Boolean = false,
    val error: String? = null,
    val sendSuccess: CryptoTransaction? = null,
) {
    val selectedChainWallet
        get() = wallet?.chainWallet(selectedNetwork)
}

/**
 * ViewModel for wallet screens.
 *
 * Manages wallet state, balance refreshing, network selection,
 * and transaction sending. Exposes StateFlow for Compose UI.
 */
@HiltViewModel
class WalletViewModel @Inject constructor(
    private val walletRepository: WalletRepository,
    private val sendCryptoUseCase: SendCryptoUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(WalletUiState())
    val uiState: StateFlow<WalletUiState> = _uiState.asStateFlow()

    init {
        observeWallet()
        observeTransactions()
    }

    private fun observeWallet() {
        viewModelScope.launch {
            walletRepository.observeWallet()
                .catch { error ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = error.message ?: "Failed to load wallet",
                        )
                    }
                }
                .collect { wallet ->
                    _uiState.update {
                        it.copy(
                            wallet = wallet,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    private fun observeTransactions() {
        viewModelScope.launch {
            walletRepository.observeTransactions(_uiState.value.selectedNetwork)
                .catch { /* silently handle — transactions are secondary */ }
                .collect { transactions ->
                    _uiState.update { it.copy(transactions = transactions) }
                }
        }
    }

    fun selectNetwork(network: BlockchainNetwork) {
        _uiState.update { it.copy(selectedNetwork = network) }
        observeTransactions()
    }

    fun refreshBalances() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            runCatching { walletRepository.refreshBalances() }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(error = error.message ?: "Refresh failed")
                    }
                }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun sendCrypto(
        toAddress: String,
        amount: String,
        tokenContractAddress: String? = null,
        note: String? = null,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, error = null, sendSuccess = null) }

            sendCryptoUseCase(
                network = _uiState.value.selectedNetwork,
                toAddress = toAddress,
                amount = amount,
                tokenContractAddress = tokenContractAddress,
                note = note,
            ).onSuccess { transaction ->
                _uiState.update {
                    it.copy(
                        isSending = false,
                        sendSuccess = transaction,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSending = false,
                        error = error.message ?: "Transaction failed",
                    )
                }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    fun clearSendSuccess() {
        _uiState.update { it.copy(sendSuccess = null) }
    }
}
