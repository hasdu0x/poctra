package com.poctra.wallet.security

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.poctra.wallet.data.SecurityMethod
import com.poctra.wallet.data.Wallet
import com.poctra.wallet.data.WalletVault
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class SecureWalletStore(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PrefsName,
        Context.MODE_PRIVATE,
    )
    private val secureRandom = SecureRandom()

    fun hasWallet(): Boolean = prefs.contains(KeyEncryptedWallet) && currentMethod() != null

    fun currentMethod(): SecurityMethod? = SecurityMethod.fromStorage(prefs.getString(KeyMethod, null))

    fun addressPreview(): String? = prefs.getString(KeyAddress, null)

    fun createBiometricEncryptCipher(): Cipher {
        val cipher = Cipher.getInstance(AesMode)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateBiometricKey())
        return cipher
    }

    fun createBiometricDecryptCipher(): Cipher {
        val iv = decodeRequired(KeyIv)
        val cipher = Cipher.getInstance(AesMode)
        cipher.init(Cipher.DECRYPT_MODE, loadBiometricKey(), GCMParameterSpec(128, iv))
        return cipher
    }

    fun setActiveAddress(address: String) {
        prefs.edit().putString(KeyAddress, address).apply()
    }

    fun saveWalletMetadata(vault: WalletVault) {
        prefs.edit()
            .putString(KeyWalletMetadata, walletMetadataJson(vault))
            .putString(KeyAddress, vault.activeWallet?.address ?: vault.activeAddress)
            .apply()
    }

    fun saveWithBiometric(wallet: Wallet, authenticatedCipher: Cipher) {
        saveVaultWithBiometric(WalletVault.single(wallet), authenticatedCipher)
    }

    fun saveVaultWithBiometric(vault: WalletVault, authenticatedCipher: Cipher) {
        val encrypted = authenticatedCipher.doFinal(vault.toJson().toString().toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(KeyMethod, SecurityMethod.Biometric.storageValue)
            .putString(KeyEncryptedWallet, encrypted.toBase64())
            .putString(KeyIv, authenticatedCipher.iv.toBase64())
            .remove(KeySalt)
            .putString(KeyAddress, vault.activeWallet?.address ?: vault.activeAddress)
            .putString(KeyPublicKey, vault.activeWallet?.publicKeyHex.orEmpty())
            .putString(KeyWalletMetadata, walletMetadataJson(vault))
            .putLong(KeyCreatedAt, System.currentTimeMillis())
            .apply()
        clearPinFailures()
    }

    fun saveWithPin(wallet: Wallet, pin: String) {
        saveVaultWithPin(WalletVault.single(wallet), pin)
    }

    fun saveVaultWithPin(vault: WalletVault, pin: String) {
        require(pin.matches(Regex("\\d{6}"))) { "PIN must be 6 digits." }
        deleteBiometricKey()

        val salt = ByteArray(SaltLength).also(secureRandom::nextBytes)
        val iv = ByteArray(IvLength).also(secureRandom::nextBytes)
        val cipher = Cipher.getInstance(AesMode)
        cipher.init(Cipher.ENCRYPT_MODE, derivePinKey(pin, salt), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(vault.toJson().toString().toByteArray(Charsets.UTF_8))

        prefs.edit()
            .putString(KeyMethod, SecurityMethod.Pin.storageValue)
            .putString(KeyEncryptedWallet, encrypted.toBase64())
            .putString(KeyIv, iv.toBase64())
            .putString(KeySalt, salt.toBase64())
            .putString(KeyAddress, vault.activeWallet?.address ?: vault.activeAddress)
            .putString(KeyPublicKey, vault.activeWallet?.publicKeyHex.orEmpty())
            .putString(KeyWalletMetadata, walletMetadataJson(vault))
            .putLong(KeyCreatedAt, System.currentTimeMillis())
            .apply()
        clearPinFailures()
    }

    fun loadWithBiometric(authenticatedCipher: Cipher): Wallet {
        return loadVaultWithBiometric(authenticatedCipher).activeWallet
            ?: throw IllegalStateException("No wallet found in secure vault.")
    }

    fun loadVaultWithBiometric(authenticatedCipher: Cipher): WalletVault {
        val encrypted = decodeRequired(KeyEncryptedWallet)
        val decrypted = authenticatedCipher.doFinal(encrypted)
        clearPinFailures()
        return parseVault(String(decrypted, Charsets.UTF_8))
    }

    fun loadWithPin(pin: String): Wallet {
        return loadVaultWithPin(pin).activeWallet
            ?: throw IllegalStateException("No wallet found in secure vault.")
    }

    fun loadVaultWithPin(pin: String): WalletVault {
        require(pin.matches(Regex("\\d{6}"))) { "PIN must be 6 digits." }
        assertPinAllowed()

        val encrypted = decodeRequired(KeyEncryptedWallet)
        val salt = decodeRequired(KeySalt)
        val iv = decodeRequired(KeyIv)
        return try {
            val cipher = Cipher.getInstance(AesMode)
            cipher.init(Cipher.DECRYPT_MODE, derivePinKey(pin, salt), GCMParameterSpec(128, iv))
            val decrypted = cipher.doFinal(encrypted)
            clearPinFailures()
            parseVault(String(decrypted, Charsets.UTF_8))
        } catch (error: Exception) {
            recordPinFailure()
            throw IllegalArgumentException("Invalid PIN.", error)
        }
    }

    fun exportEncryptedBackup(): String {
        val backup = JSONObject()
            .put("version", "poctra-mobile-1")
            .put("exportedAt", System.currentTimeMillis())
            .put("method", prefs.getString(KeyMethod, null))
            .put("activeAddress", prefs.getString(KeyAddress, null))
            .put("encryptedWallet", prefs.getString(KeyEncryptedWallet, null))
            .put("iv", prefs.getString(KeyIv, null))
            .put("salt", prefs.getString(KeySalt, null))
            .put("createdAt", prefs.getLong(KeyCreatedAt, 0L))

        require(!backup.isNull("encryptedWallet")) { "No encrypted wallet data to back up." }
        return backup.toString(2)
    }

    fun pinLockoutRemainingMs(): Long {
        val lockedUntil = prefs.getLong(KeyPinLockedUntil, 0L)
        return (lockedUntil - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun parseVault(jsonText: String): WalletVault {
        val parsed = applyWalletMetadata(WalletVault.fromJson(JSONObject(jsonText)))
        val preferredActive = prefs.getString(KeyAddress, null)
        return if (!preferredActive.isNullOrBlank() && parsed.wallets.any { it.address == preferredActive }) {
            parsed.copy(activeAddress = preferredActive)
        } else {
            parsed
        }
    }

    private fun applyWalletMetadata(vault: WalletVault): WalletVault {
        val raw = prefs.getString(KeyWalletMetadata, null) ?: return vault
        val metadata = runCatching { JSONObject(raw) }.getOrNull() ?: return vault
        val nameByAddress = mutableMapOf<String, String>()
        val walletMetadata = metadata.optJSONArray("wallets")
        if (walletMetadata != null) {
            for (index in 0 until walletMetadata.length()) {
                val item = walletMetadata.optJSONObject(index) ?: continue
                val address = item.optString("address")
                val name = item.optString("name")
                if (address.isNotBlank() && name.isNotBlank()) nameByAddress[address] = name
            }
        }
        val order = buildList {
            val array = metadata.optJSONArray("order") ?: return@buildList
            for (index in 0 until array.length()) {
                val address = array.optString(index)
                if (address.isNotBlank()) add(address)
            }
        }
        val renamed = vault.wallets.map { wallet ->
            nameByAddress[wallet.address]?.let { wallet.copy(name = it) } ?: wallet
        }
        val reordered = if (order.isEmpty()) {
            renamed
        } else {
            val byAddress = renamed.associateBy { it.address }
            order.mapNotNull { byAddress[it] } + renamed.filterNot { it.address in order }
        }
        return vault.copy(wallets = reordered)
    }

    private fun walletMetadataJson(vault: WalletVault): String {
        return JSONObject()
            .put("version", 1)
            .put("activeAddress", vault.activeAddress)
            .put("order", JSONArray().apply {
                vault.wallets.forEach { put(it.address) }
            })
            .put("wallets", JSONArray().apply {
                vault.wallets.forEach { wallet ->
                    put(
                        JSONObject()
                            .put("address", wallet.address)
                            .put("name", wallet.name),
                    )
                }
            })
            .toString()
    }

    private fun derivePinKey(pin: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(pin.toCharArray(), salt, PbkdfIterations, KeyLengthBytes * 8)
        val key = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec)
            .encoded
        return SecretKeySpec(key, "AES")
    }

    private fun getOrCreateBiometricKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        keyStore.getKey(BiometricAlias, null)?.let { return it as SecretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, AndroidKeyStore)
        val builder = KeyGenParameterSpec.Builder(
            BiometricAlias,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)
            .setInvalidatedByBiometricEnrollment(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        generator.init(builder.build())
        return generator.generateKey()
    }

    private fun loadBiometricKey(): SecretKey {
        val keyStore = KeyStore.getInstance(AndroidKeyStore).apply { load(null) }
        return keyStore.getKey(BiometricAlias, null) as? SecretKey
            ?: throw IllegalStateException("Biometric wallet key is unavailable.")
    }

    private fun deleteBiometricKey() {
        runCatching {
            KeyStore.getInstance(AndroidKeyStore).apply {
                load(null)
                if (containsAlias(BiometricAlias)) deleteEntry(BiometricAlias)
            }
        }
    }

    private fun decodeRequired(key: String): ByteArray {
        val value = prefs.getString(key, null) ?: throw IllegalStateException("Missing secure wallet data.")
        return Base64.decode(value, Base64.DEFAULT)
    }

    private fun assertPinAllowed() {
        val remaining = pinLockoutRemainingMs()
        if (remaining > 0) {
            throw IllegalStateException("Too many PIN attempts. Try again in ${(remaining / 1000) + 1} seconds.")
        }
    }

    private fun recordPinFailure() {
        val attempts = prefs.getInt(KeyPinAttempts, 0) + 1
        val editor = prefs.edit().putInt(KeyPinAttempts, attempts)
        if (attempts >= MaxPinAttempts) {
            editor.putLong(KeyPinLockedUntil, System.currentTimeMillis() + PinLockoutMs)
        }
        editor.apply()
    }

    private fun clearPinFailures() {
        prefs.edit()
            .remove(KeyPinAttempts)
            .remove(KeyPinLockedUntil)
            .apply()
    }

    private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    companion object {
        private const val PrefsName = "poctra_secure_wallet"
        private const val AndroidKeyStore = "AndroidKeyStore"
        private const val BiometricAlias = "poctra_wallet_biometric_key"
        private const val AesMode = "AES/GCM/NoPadding"
        private const val SaltLength = 32
        private const val IvLength = 12
        private const val KeyLengthBytes = 32
        private const val PbkdfIterations = 310_000
        private const val MaxPinAttempts = 5
        private const val PinLockoutMs = 5 * 60 * 1000L

        private const val KeyMethod = "method"
        private const val KeyEncryptedWallet = "encrypted_wallet"
        private const val KeyIv = "iv"
        private const val KeySalt = "salt"
        private const val KeyAddress = "address"
        private const val KeyPublicKey = "public_key"
        private const val KeyCreatedAt = "created_at"
        private const val KeyPinAttempts = "pin_attempts"
        private const val KeyPinLockedUntil = "pin_locked_until"
        private const val KeyWalletMetadata = "wallet_metadata"
    }
}
