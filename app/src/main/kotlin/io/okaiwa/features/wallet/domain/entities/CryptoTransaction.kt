package io.okaiwa.features.wallet.domain.entities

import kotlinx.serialization.Serializable

/**
 * Crypto transaction entity.
 *
 * Represents a blockchain transaction (send, receive, or swap).
 * Transactions are signed locally using keys stored in the Android Keystore
 * and broadcast to the blockchain via the wallet-core service.
 */
@Serializable
data class CryptoTransaction(
    val id: String,
    val walletId: String,
    val network: BlockchainNetwork,
    val type: TransactionType,
    val status: TransactionStatus,
    val fromAddress: String,
    val toAddress: String,
    val amount: String,
    val tokenSymbol: String,
    val tokenContractAddress: String? = null,
    val gasUsed: String? = null,
    val gasPrice: String? = null,
    val txHash: String? = null,
    val blockNumber: Long? = null,
    val amountUsd: Double? = null,
    val feeUsd: Double? = null,
    val note: String? = null,
    val conversationId: String? = null,
    val createdAt: Long,
    val confirmedAt: Long? = null,
) {
    /**
     * Explorer URL for this transaction.
     */
    val explorerUrl: String?
        get() = txHash?.let { "${network.explorerUrl}/tx/$it" }

    /**
     * Whether the transaction is still pending confirmation.
     */
    val isPending: Boolean
        get() = status == TransactionStatus.Pending || status == TransactionStatus.Broadcasting

    /**
     * Short display hash (first 6 + last 4 characters).
     */
    val shortHash: String?
        get() = txHash?.let {
            if (it.length > 10) "${it.take(6)}...${it.takeLast(4)}" else it
        }
}

@Serializable
enum class TransactionType {
    Send,
    Receive,
    Swap,
    ContractInteraction,
}

@Serializable
enum class TransactionStatus {
    /** Transaction is being constructed and signed locally. */
    Preparing,
    /** Transaction has been signed and is being broadcast. */
    Broadcasting,
    /** Transaction is in the mempool, awaiting confirmation. */
    Pending,
    /** Transaction has been confirmed on-chain. */
    Confirmed,
    /** Transaction failed (reverted, out of gas, etc.). */
    Failed,
    /** Transaction was cancelled (replaced with higher gas). */
    Cancelled,
}
