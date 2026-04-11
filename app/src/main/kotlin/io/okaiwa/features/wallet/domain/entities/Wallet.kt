package io.okaiwa.features.wallet.domain.entities

import kotlinx.serialization.Serializable

/**
 * Non-custodial crypto wallet entity.
 *
 * Represents the user's wallet with addresses across multiple blockchain
 * networks. The private key never leaves the Android Keystore / StrongBox.
 * Only public addresses and balance information are stored in this entity.
 */
@Serializable
data class Wallet(
    val id: String,
    val userId: String,
    val chains: List<ChainWallet>,
    val totalBalanceUsd: Double,
    val createdAt: Long,
) {
    /**
     * Get the wallet for a specific chain.
     */
    fun chainWallet(chain: BlockchainNetwork): ChainWallet? =
        chains.find { it.network == chain }
}

/**
 * Per-chain wallet with address and balance.
 */
@Serializable
data class ChainWallet(
    val network: BlockchainNetwork,
    val address: String,
    val nativeBalance: String,
    val nativeBalanceUsd: Double,
    val tokens: List<TokenBalance> = emptyList(),
) {
    /**
     * Total balance (native + tokens) in USD.
     */
    val totalBalanceUsd: Double
        get() = nativeBalanceUsd + tokens.sumOf { it.balanceUsd }
}

/**
 * ERC-20 / SPL token balance.
 */
@Serializable
data class TokenBalance(
    val contractAddress: String,
    val symbol: String,
    val name: String,
    val decimals: Int,
    val balance: String,
    val balanceUsd: Double,
    val logoUrl: String? = null,
)

/**
 * Supported blockchain networks.
 */
@Serializable
enum class BlockchainNetwork(
    val displayName: String,
    val chainId: Int,
    val nativeSymbol: String,
    val explorerUrl: String,
) {
    Ethereum(
        displayName = "Ethereum",
        chainId = 1,
        nativeSymbol = "ETH",
        explorerUrl = "https://etherscan.io",
    ),
    Polygon(
        displayName = "Polygon",
        chainId = 137,
        nativeSymbol = "MATIC",
        explorerUrl = "https://polygonscan.com",
    ),
    Arbitrum(
        displayName = "Arbitrum One",
        chainId = 42161,
        nativeSymbol = "ETH",
        explorerUrl = "https://arbiscan.io",
    ),
    Base(
        displayName = "Base",
        chainId = 8453,
        nativeSymbol = "ETH",
        explorerUrl = "https://basescan.org",
    ),
}
