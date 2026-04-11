package io.okaiwa.features.wallet.domain.usecases

import io.okaiwa.core.errors.AppError
import io.okaiwa.features.wallet.domain.entities.BlockchainNetwork
import io.okaiwa.features.wallet.domain.entities.CryptoTransaction
import io.okaiwa.features.wallet.domain.repositories.WalletRepository
import javax.inject.Inject

/**
 * Use case for sending a crypto transaction.
 *
 * Orchestrates the transaction flow:
 * 1. Validate the recipient address format.
 * 2. Validate the amount (positive, within balance).
 * 3. Estimate gas fees.
 * 4. Sign the transaction locally (Android Keystore).
 * 5. Broadcast to the blockchain.
 * 6. Store transaction record locally.
 *
 * Biometric authentication is required before signing (handled at the
 * presentation layer before invoking this use case).
 */
class SendCryptoUseCase @Inject constructor(
    private val walletRepository: WalletRepository,
) {

    /**
     * Send a crypto transaction.
     *
     * @param network Target blockchain network.
     * @param toAddress Recipient wallet address.
     * @param amount Human-readable amount (e.g., "0.5").
     * @param tokenContractAddress ERC-20 contract address, or null for native.
     * @param note Optional note.
     * @return Result containing the broadcast [CryptoTransaction].
     */
    suspend operator fun invoke(
        network: BlockchainNetwork,
        toAddress: String,
        amount: String,
        tokenContractAddress: String? = null,
        note: String? = null,
    ): Result<CryptoTransaction> = runCatching {
        // Validate address format
        if (!isValidAddress(toAddress, network)) {
            throw AppError.Crypto.EncryptionFailed(
                IllegalArgumentException("Invalid ${network.displayName} address: $toAddress")
            )
        }

        // Validate amount
        val amountDouble = amount.toDoubleOrNull()
            ?: throw AppError.Crypto.EncryptionFailed(
                IllegalArgumentException("Invalid amount: $amount")
            )

        if (amountDouble <= 0) {
            throw AppError.Crypto.EncryptionFailed(
                IllegalArgumentException("Amount must be greater than zero")
            )
        }

        // Prevent self-send
        val ownAddress = walletRepository.getAddress(network)
        if (toAddress.equals(ownAddress, ignoreCase = true)) {
            throw AppError.Crypto.EncryptionFailed(
                IllegalArgumentException("Cannot send to your own address")
            )
        }

        // Estimate gas to ensure sufficient balance
        walletRepository.estimateGas(
            network = network,
            toAddress = toAddress,
            amount = amount,
            tokenContractAddress = tokenContractAddress,
        )

        // Execute transaction
        walletRepository.sendTransaction(
            network = network,
            toAddress = toAddress,
            amount = amount,
            tokenContractAddress = tokenContractAddress,
            note = note,
        )
    }

    /**
     * Validate an address for the given blockchain network.
     * Checks EVM address format (0x + 40 hex characters).
     */
    private fun isValidAddress(address: String, network: BlockchainNetwork): Boolean {
        // EVM chains use the same address format
        return address.matches(Regex("^0x[0-9a-fA-F]{40}$"))
    }
}
