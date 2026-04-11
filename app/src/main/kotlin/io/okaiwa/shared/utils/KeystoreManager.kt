package io.okaiwa.shared.utils

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Android Keystore manager for cryptographic key operations.
 *
 * All keys are stored in the Android Keystore, which provides
 * hardware-backed security. On devices with StrongBox (Titan M chip
 * or equivalent), keys are stored in the secure element for maximum
 * protection against extraction — even with root access.
 *
 * Keys are bound to the device and cannot be exported.
 */
@Singleton
class KeystoreManager @Inject constructor() {

    companion object {
        private const val KEYSTORE_PROVIDER = "AndroidKeyStore"
        private const val DATABASE_KEY_ALIAS = "okaiwa_db_key"
        private const val AUTH_TOKEN_KEY_ALIAS = "okaiwa_auth_key"
        private const val WALLET_KEY_ALIAS = "okaiwa_wallet_key"
        private const val GCM_TAG_LENGTH = 128
    }

    private val keyStore: KeyStore by lazy {
        KeyStore.getInstance(KEYSTORE_PROVIDER).apply { load(null) }
    }

    /**
     * Whether the device API level supports StrongBox.
     * Actual hardware support is verified at key generation time
     * with a fallback to software-backed keys if StrongBox fails.
     */
    val isStrongBoxSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    /**
     * Generate or retrieve the database encryption key.
     * Used with SQLCipher for local database encryption.
     *
     * @return The [SecretKey] for database encryption.
     */
    fun getOrCreateDatabaseKey(): SecretKey {
        return getExistingKey(DATABASE_KEY_ALIAS) ?: generateAesKey(DATABASE_KEY_ALIAS)
    }

    /**
     * Generate or retrieve the auth token encryption key.
     * Used to encrypt authentication tokens at rest.
     *
     * @return The [SecretKey] for token encryption.
     */
    fun getOrCreateAuthKey(): SecretKey {
        return getExistingKey(AUTH_TOKEN_KEY_ALIAS) ?: generateAesKey(AUTH_TOKEN_KEY_ALIAS)
    }

    /**
     * Generate or retrieve the wallet encryption key.
     * Used to encrypt wallet mnemonic and private key material.
     * Requires user authentication (biometric) for access.
     *
     * @return The [SecretKey] for wallet encryption.
     */
    fun getOrCreateWalletKey(): SecretKey {
        return getExistingKey(WALLET_KEY_ALIAS)
            ?: generateAesKey(WALLET_KEY_ALIAS, requireUserAuth = true)
    }

    /**
     * Encrypt data using AES-GCM with the specified key alias.
     *
     * @param data Plaintext data to encrypt.
     * @param keyAlias Keystore alias for the encryption key.
     * @return Encrypted data with prepended IV (12 bytes IV + ciphertext).
     */
    fun encrypt(data: ByteArray, keyAlias: String): ByteArray {
        val key = getExistingKey(keyAlias)
            ?: throw SecurityException("Key not found: $keyAlias")

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)

        val iv = cipher.iv
        val encrypted = cipher.doFinal(data)

        // Prepend IV to ciphertext
        return iv + encrypted
    }

    /**
     * Decrypt data using AES-GCM with the specified key alias.
     *
     * @param encryptedData IV (12 bytes) + ciphertext.
     * @param keyAlias Keystore alias for the decryption key.
     * @return Decrypted plaintext data.
     */
    fun decrypt(encryptedData: ByteArray, keyAlias: String): ByteArray {
        val key = getExistingKey(keyAlias)
            ?: throw SecurityException("Key not found: $keyAlias")

        val iv = encryptedData.copyOfRange(0, 12)
        val ciphertext = encryptedData.copyOfRange(12, encryptedData.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)

        return cipher.doFinal(ciphertext)
    }

    /**
     * Delete a key from the Keystore.
     *
     * @param alias Key alias to delete.
     */
    fun deleteKey(alias: String) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    /**
     * Check if a key exists in the Keystore.
     */
    fun hasKey(alias: String): Boolean = keyStore.containsAlias(alias)

    private fun getExistingKey(alias: String): SecretKey? {
        return if (keyStore.containsAlias(alias)) {
            keyStore.getKey(alias, null) as? SecretKey
        } else {
            null
        }
    }

    private fun generateAesKey(
        alias: String,
        requireUserAuth: Boolean = false,
    ): SecretKey {
        val builder = KeyGenParameterSpec.Builder(
            alias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)

        if (requireUserAuth) {
            builder.setUserAuthenticationRequired(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                    0, // Require auth for every use
                    KeyProperties.AUTH_BIOMETRIC_STRONG,
                )
            }
        }

        // Use StrongBox if available, with fallback for devices that
        // report API 28+ but lack actual StrongBox hardware.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            builder.setIsStrongBoxBacked(true)
        }

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            KEYSTORE_PROVIDER,
        )

        return try {
            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
        } catch (@Suppress("NewApi") e: android.security.keystore.StrongBoxUnavailableException) {
            // StrongBox not available on this device — fall back to TEE
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                builder.setIsStrongBoxBacked(false)
            }
            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
        }
    }
}
