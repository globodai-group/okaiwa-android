package io.okaiwa.features.wallet.data.mnemonic

/**
 * Mock 24-word BIP-39 mnemonic generator.
 *
 * Returns a fresh 24-word seed pulled from a small subset of the
 * official English BIP-39 wordlist. This is deliberately NOT
 * cryptographically strong — the purpose is UX review only.
 *
 * Real implementation: wallet-core FFI call to `HDWallet::new(strength =
 * 256, passphrase = "")` which uses the full 2048-word list + OS
 * CSPRNG. That binding lands once the Rust NDK toolchain is wired up.
 *
 * The 24-word / 256-bit strength choice is documented in
 * docs/adr/003-wallet-seed-strength.md — summary: 128 bits is already
 * unbreakable classically, 24 words gives the quantum-era safety
 * margin that matches the security-first brand positioning.
 */
object MockMnemonicGenerator {

    /** Subset of the BIP-39 English wordlist — enough for a believable mock. */
    private val wordlist = listOf(
        "abandon", "ability", "able", "about", "above", "absent", "absorb", "abstract",
        "absurd", "abuse", "access", "accident", "account", "accuse", "achieve", "acid",
        "acoustic", "acquire", "across", "act", "action", "actor", "actress", "actual",
        "adapt", "add", "addict", "address", "adjust", "admit", "adult", "advance",
        "advice", "aerobic", "affair", "afford", "afraid", "again", "age", "agent",
        "agree", "ahead", "aim", "air", "airport", "aisle", "alarm", "album",
        "alcohol", "alert", "alien", "alley", "allow", "almost", "alone", "alpha",
        "already", "also", "alter", "always", "amateur", "amazing", "among", "amount",
        "amused", "analyst", "anchor", "ancient", "anger", "angle", "angry", "animal",
        "ankle", "announce", "annual", "another", "answer", "antenna", "antique", "anxiety",
        "apart", "apology", "appear", "apple", "approve", "april", "arch", "arctic",
        "area", "arena", "argue", "arm", "armed", "armor", "army", "around",
        "arrange", "arrest", "arrive", "arrow", "art", "artefact", "artist", "artwork",
        "ask", "aspect", "assault", "asset", "assist", "assume", "asthma", "athlete",
        "atom", "attack", "attend", "attitude", "attract", "auction", "audit", "august",
        "aunt", "author", "auto", "autumn", "average", "avocado", "avoid", "awake",
    )

    /** 24 random words — fresh on every call so QA doesn't memorise the seed. */
    fun generate24(): List<String> {
        val pool = wordlist.toMutableList()
        val result = mutableListOf<String>()
        repeat(24) {
            val index = (0 until pool.size).random()
            result += pool.removeAt(index)
        }
        return result
    }
}
