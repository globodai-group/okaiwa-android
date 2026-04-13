package io.okaiwa.features.wallet.data.mnemonic

import wallet.core.jni.CoinType
import wallet.core.jni.HDWallet

/**
 * Derives per-chain addresses from a BIP-39 mnemonic using Trust Wallet's
 * wallet-core, which ships the canonical derivation paths and address
 * encodings for every [CoinType] it supports.
 *
 * Each [CoinType] maps to a fixed BIP-44 path:
 *   - ETH → m/44'/60'/0'/0/0  (hex + EIP-55 checksum)
 *   - BTC → m/84'/0'/0'/0/0   (native segwit, bech32)
 *   - SOL → m/44'/501'/0'/0'  (Ed25519, base58)
 *
 * The derivation is deterministic: the same mnemonic + passphrase pair
 * always produces the same addresses, so we never need to persist
 * the addresses themselves — regenerate on demand.
 *
 * Security: the [HDWallet] holder carries the decrypted seed in native
 * memory for the lifetime of [derive]. Callers MUST scope invocations
 * narrowly (one call per onboarding step, not a long-held field) so the
 * JVM GC can release the holder promptly. wallet-core's native
 * destructor zeroes the seed on release.
 */
object WalletDeriver {

    /** Addresses for the chains Okaiwa surfaces today. */
    data class Addresses(
        val ethereum: String,
        val bitcoin: String,
        val solana: String,
    )

    /**
     * Derive one address per supported chain.
     *
     * @param mnemonic 24-word BIP-39 phrase, space-joined. Caller is
     * responsible for normalising whitespace and lowercasing (the user
     * usually re-types the recovery phrase and occasional extra spaces
     * would otherwise cause silent "wrong wallet" failures).
     * @param passphrase Optional BIP-39 passphrase. Defaults to the empty
     * string to match [MockMnemonicGenerator.generate24].
     */
    fun derive(mnemonic: String, passphrase: String = ""): Addresses {
        val wallet = HDWallet(mnemonic, passphrase)
        return Addresses(
            ethereum = wallet.getAddressForCoin(CoinType.ETHEREUM),
            bitcoin = wallet.getAddressForCoin(CoinType.BITCOIN),
            solana = wallet.getAddressForCoin(CoinType.SOLANA),
        )
    }
}
