package io.okaiwa.features.wallet.data.repositories

import io.okaiwa.features.wallet.domain.entities.BlockchainNetwork
import io.okaiwa.features.wallet.domain.entities.CryptoTransaction
import io.okaiwa.features.wallet.domain.entities.Wallet
import io.okaiwa.features.wallet.domain.repositories.WalletRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stub implementation of [WalletRepository].
 *
 * Exposes an empty wallet and no transactions. Send operations throw
 * [NotImplementedError] because unlocking a signing key requires the
 * wallet-core FFI integration which lands in a later build.
 */
@Singleton
class StubWalletRepository @Inject constructor() : WalletRepository {

    override fun observeWallet(): Flow<Wallet?> = flowOf(null)

    override fun observeTransactions(network: BlockchainNetwork): Flow<List<CryptoTransaction>> =
        flowOf(emptyList())

    override suspend fun refreshBalances() {
        // no-op in stub
    }

    override suspend fun estimateGas(
        network: BlockchainNetwork,
        toAddress: String,
        amount: String,
        tokenContractAddress: String?,
    ): String = "0"

    override suspend fun sendTransaction(
        network: BlockchainNetwork,
        toAddress: String,
        amount: String,
        tokenContractAddress: String?,
        note: String?,
    ): CryptoTransaction {
        throw NotImplementedError("Wallet signing requires wallet-core FFI integration")
    }

    override suspend fun getAddress(network: BlockchainNetwork): String? = null

    override suspend fun initializeWallet(mnemonic: String?) {
        // no-op in stub
    }

    override suspend fun exportMnemonic(): String {
        throw NotImplementedError("Mnemonic export requires wallet-core FFI integration")
    }
}
