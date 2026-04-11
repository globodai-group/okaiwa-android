package io.okaiwa.features.wallet.domain.repositories

import io.okaiwa.features.wallet.domain.entities.BlockchainNetwork
import io.okaiwa.features.wallet.domain.entities.CryptoTransaction
import io.okaiwa.features.wallet.domain.entities.Wallet
import kotlinx.coroutines.flow.Flow

/**
 * Wallet repository interface.
 *
 * Defines the contract for wallet operations including balance queries,
 * transaction history, and transaction signing/broadcasting.
 * Private keys are managed exclusively through Android Keystore and
 * never exposed to the repository layer.
 */
interface WalletRepository {

    /**
     * Observe the user's wallet state including all chain balances.
     * Emits updates when balances change or new transactions are confirmed.
     */
    fun observeWallet(): Flow<Wallet?>

    /**
     * Observe transaction history for a specific chain.
     * Returns transactions in reverse chronological order.
     *
     * @param network The blockchain network to query.
     */
    fun observeTransactions(network: BlockchainNetwork): Flow<List<CryptoTransaction>>

    /**
     * Refresh wallet balances from the blockchain.
     * Queries all supported chains in parallel.
     */
    suspend fun refreshBalances()

    /**
     * Estimate gas fees for a transaction.
     *
     * @param network Target blockchain network.
     * @param toAddress Recipient address.
     * @param amount Amount to send (in wei/smallest unit).
     * @param tokenContractAddress ERC-20 contract address, null for native token.
     * @return Estimated fee in the network's native token.
     */
    suspend fun estimateGas(
        network: BlockchainNetwork,
        toAddress: String,
        amount: String,
        tokenContractAddress: String? = null,
    ): String

    /**
     * Send a crypto transaction.
     * Signs the transaction locally using Android Keystore, then broadcasts.
     *
     * @param network Target blockchain network.
     * @param toAddress Recipient address.
     * @param amount Amount to send (in human-readable format).
     * @param tokenContractAddress ERC-20 contract address, null for native token.
     * @param note Optional note attached to the transaction.
     * @return The created [CryptoTransaction] with pending status.
     */
    suspend fun sendTransaction(
        network: BlockchainNetwork,
        toAddress: String,
        amount: String,
        tokenContractAddress: String? = null,
        note: String? = null,
    ): CryptoTransaction

    /**
     * Get the wallet address for a specific chain.
     *
     * @param network The blockchain network.
     * @return The wallet address, or null if not initialized.
     */
    suspend fun getAddress(network: BlockchainNetwork): String?

    /**
     * Initialize the wallet by generating or recovering key pairs.
     * Keys are stored in Android Keystore with StrongBox if available.
     *
     * @param mnemonic Optional BIP-39 mnemonic for recovery. Null to generate new.
     */
    suspend fun initializeWallet(mnemonic: String? = null)

    /**
     * Export the encrypted mnemonic for backup.
     * Requires biometric authentication before export.
     *
     * @return Encrypted mnemonic phrase.
     */
    suspend fun exportMnemonic(): String
}
