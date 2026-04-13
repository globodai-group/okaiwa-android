package io.okaiwa.features.wallet.data.mnemonic

import wallet.core.jni.HDWallet

/**
 * Real 24-word BIP-39 mnemonic generator, backed by Trust Wallet's
 * wallet-core. Previously shipped under the name `MockMnemonicGenerator`
 * with a 128-word subset and `kotlin.random.Random` — the object name is
 * kept for now to minimise the blast radius on the call sites (see
 * WalletOnboardingViewModel). A follow-up rename will land once every
 * caller goes through the repository layer instead.
 *
 * `HDWallet(strength = 256, passphrase = "")`:
 *   - 256 bits of entropy → 24 words of BIP-39 English wordlist.
 *     The entropy comes from wallet-core's embedded CSPRNG, which on
 *     Android defers to `/dev/urandom` via `getrandom(2)`.
 *   - `passphrase = ""` = no BIP-39 optional passphrase at this stage.
 *     When we wire the "Passphrase avancée" UX the empty string is
 *     replaced with the user's chosen extension.
 *
 * The wallet-core HDWallet holder is destroyed as soon as we extract
 * the word list — we do not keep a reference to the seed. Re-deriving
 * the wallet later is the job of [WalletDeriver], which re-instantiates
 * HDWallet from the mnemonic.
 *
 * Requires the TrustWalletCore JNI loaded beforehand — see
 * [io.okaiwa.OkaiwaApp.onCreate].
 */
object MockMnemonicGenerator {

    /**
     * Fresh 24-word BIP-39 mnemonic. The wallet-core implementation
     * returns the words as a single space-separated string (BIP-39 canon)
     * — we split it for compatibility with the existing call sites that
     * treat the mnemonic as a `List<String>` for the verification grid.
     */
    fun generate24(): List<String> {
        val wallet = HDWallet(ENTROPY_BITS, PASSPHRASE)
        return wallet.mnemonic().trim().split(' ')
    }

    /** BIP-39 256-bit entropy → 24 words. See ADR 003 for the rationale. */
    private const val ENTROPY_BITS: Int = 256

    /** No optional BIP-39 passphrase while the "advanced passphrase" UX is pending. */
    private const val PASSPHRASE: String = ""
}
