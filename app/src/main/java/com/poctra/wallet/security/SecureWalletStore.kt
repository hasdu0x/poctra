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
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

class SecureWalletStore(context: Context) {
    data class UnlockedPinVault(
        val vault: WalletVault,
        val sessionKey: ByteArray,
    )

    private data class DerivedPinKey(
        val bytes: ByteArray,
        val version: Int,
    )

    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences(
        PrefsName,
        Context.MODE_PRIVATE,
    )
    private val secureRandom = SecureRandom()

    fun hasWallet(): Boolean = prefs.contains(KeyEncryptedWallet) && currentMethod() != null

    fun currentMethod(): SecurityMethod? = SecurityMethod.fromStorage(prefs.getString(KeyMethod, null))
    
    fun hasPinData(): Boolean = prefs.contains(KeySalt)

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
        val plaintext = vault.toJson().toString().toByteArray(Charsets.UTF_8)
        val encrypted = try {
            authenticatedCipher.doFinal(plaintext)
        } finally {
            plaintext.fill(0)
        }
        val iv = authenticatedCipher.iv
        try {
            prefs.edit()
                .putString(KeyMethod, SecurityMethod.Biometric.storageValue)
                .putString(KeyEncryptedWallet, encrypted.toBase64())
                .putString(KeyIv, iv.toBase64())
                .remove(KeySalt)
                .remove(KeyPinKdfVersion)
                .putString(KeyAddress, vault.activeWallet?.address ?: vault.activeAddress)
                .putString(KeyPublicKey, vault.activeWallet?.publicKeyHex.orEmpty())
                .putString(KeyWalletMetadata, walletMetadataJson(vault))
                .putLong(KeyCreatedAt, System.currentTimeMillis())
                .apply()
        } finally {
            encrypted.fill(0)
            iv.fill(0)
        }
        deletePinBindingKey()
        clearPinFailures()
    }

    fun saveWithPin(wallet: Wallet, pin: String) {
        saveVaultWithPin(WalletVault.single(wallet), pin).fill(0)
    }

    fun saveVaultWithPin(vault: WalletVault, pin: String): ByteArray {
        require(pin.matches(Regex("\\d{6}"))) { "PIN must be 6 digits." }
        val salt = ByteArray(SaltLength).also(secureRandom::nextBytes)
        val derived = derivePinKey(pin, salt, bindToDevice = true, createDeviceKey = true)
        return try {
            saveVaultWithKey(vault, derived.bytes, salt, derived.version)
            deleteBiometricKey()
            clearPinFailures()
            derived.bytes
        } catch (error: Exception) {
            derived.bytes.fill(0)
            throw error
        } finally {
            salt.fill(0)
        }
    }

    fun saveVaultWithPinSession(vault: WalletVault, sessionKey: ByteArray) {
        require(sessionKey.size == KeyLengthBytes) { "PIN session key is invalid." }
        val salt = decodeRequired(KeySalt)
        val version = prefs.getInt(KeyPinKdfVersion, LegacyPinKdfVersion)
        try {
            saveVaultWithKey(vault, sessionKey, salt, version)
        } finally {
            salt.fill(0)
        }
    }

    fun loadWithBiometric(authenticatedCipher: Cipher): Wallet {
        return loadVaultWithBiometric(authenticatedCipher).activeWallet
            ?: throw IllegalStateException("No wallet found in secure vault.")
    }

    fun loadVaultWithBiometric(authenticatedCipher: Cipher): WalletVault {
        val encrypted = decodeRequired(KeyEncryptedWallet)
        return try {
            val decrypted = authenticatedCipher.doFinal(encrypted)
            clearPinFailures()
            parseVaultBytes(decrypted)
        } finally {
            encrypted.fill(0)
        }
    }

    fun loadWithPin(pin: String): Wallet {
        return loadVaultWithPin(pin).activeWallet
            ?: throw IllegalStateException("No wallet found in secure vault.")
    }

    fun loadVaultWithPin(pin: String): WalletVault {
        val unlocked = loadVaultWithPinSession(pin)
        return try {
            unlocked.vault
        } finally {
            unlocked.sessionKey.fill(0)
        }
    }

    fun loadVaultWithPinSession(pin: String): UnlockedPinVault {
        require(pin.matches(Regex("\\d{6}"))) { "PIN must be 6 digits." }
        assertPinAllowed()

        val encrypted = decodeRequired(KeyEncryptedWallet)
        val salt = decodeRequired(KeySalt)
        val iv = decodeRequired(KeyIv)
        val storedVersion = prefs.getInt(KeyPinKdfVersion, LegacyPinKdfVersion)
        var sessionKey: ByteArray? = null
        return try {
            val initial = derivePinKey(
                pin = pin,
                salt = salt,
                bindToDevice = storedVersion >= DeviceBoundPinKdfVersion,
                createDeviceKey = storedVersion < DeviceBoundPinKdfVersion,
            )
            sessionKey = initial.bytes
            val cipher = Cipher.getInstance(AesMode)
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(initial.bytes, "AES"), GCMParameterSpec(128, iv))
            val decrypted = cipher.doFinal(encrypted)
            val loaded = parseVaultBytes(decrypted)

            if (storedVersion == LegacyPinKdfVersion) {
                val upgraded = derivePinKey(pin, salt, bindToDevice = true, createDeviceKey = true)
                if (upgraded.version == DeviceBoundPinKdfVersion) {
                    val migrated = runCatching {
                        saveVaultWithKey(loaded, upgraded.bytes, salt, upgraded.version)
                    }.isSuccess
                    if (migrated) {
                        sessionKey?.fill(0)
                        sessionKey = upgraded.bytes
                    } else {
                        upgraded.bytes.fill(0)
                    }
                } else {
                    upgraded.bytes.fill(0)
                }
            }
            clearPinFailures()
            UnlockedPinVault(loaded, requireNotNull(sessionKey))
        } catch (error: Exception) {
            sessionKey?.fill(0)
            if (error is PinDeviceBindingException) throw error
            recordPinFailure()
            throw IllegalArgumentException("Invalid PIN.", error)
        } finally {
            encrypted.fill(0)
            salt.fill(0)
            iv.fill(0)
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
            .put("pinKdfVersion", prefs.getInt(KeyPinKdfVersion, LegacyPinKdfVersion))
            .put("createdAt", prefs.getLong(KeyCreatedAt, 0L))
            .put("portability", "device-bound")
            .put("restoreNote", "This backup is encrypted with a key bound to this device and cannot be restored on another device or after a factory reset. Use your seed phrase for portable recovery.")

        require(!backup.isNull("encryptedWallet")) { "No encrypted wallet data to back up." }
        return backup.toString(2)
    }

    fun pinLockoutRemainingMs(): Long {
        val lockedUntil = prefs.getLong(KeyPinLockedUntil, 0L)
        return (lockedUntil - System.currentTimeMillis()).coerceAtLeast(0L)
    }

    private fun parseVaultBytes(jsonBytes: ByteArray): WalletVault {
        val parsed = try {
            applyWalletMetadata(WalletVault.fromJson(JSONObject(String(jsonBytes, Charsets.UTF_8))))
        } finally {
            jsonBytes.fill(0)
        }
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
        val identiconByAddress = mutableMapOf<String, String>()
        val walletMetadata = metadata.optJSONArray("wallets")
        if (walletMetadata != null) {
            for (index in 0 until walletMetadata.length()) {
                val item = walletMetadata.optJSONObject(index) ?: continue
                val address = item.optString("address")
                val name = item.optString("name")
                val identiconSvg = item.optString("identiconSvg")
                if (address.isNotBlank() && name.isNotBlank()) nameByAddress[address] = name
                if (address.isNotBlank() && identiconSvg.isNotBlank()) identiconByAddress[address] = identiconSvg
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
            wallet.copy(
                name = nameByAddress[wallet.address] ?: wallet.name,
                identiconSvg = identiconByAddress[wallet.address] ?: wallet.identiconSvg,
            )
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
                            .put("name", wallet.name)
                            .put("identiconSvg", wallet.identiconSvg),
                    )
                }
            })
            .toString()
    }

    private fun saveVaultWithKey(vault: WalletVault, keyBytes: ByteArray, salt: ByteArray, kdfVersion: Int) {
        val iv = ByteArray(IvLength).also(secureRandom::nextBytes)
        val plaintext = vault.toJson().toString().toByteArray(Charsets.UTF_8)
        val encrypted = try {
            val cipher = Cipher.getInstance(AesMode)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(keyBytes, "AES"), GCMParameterSpec(128, iv))
            cipher.doFinal(plaintext)
        } finally {
            plaintext.fill(0)
        }
        prefs.edit()
            .putString(KeyMethod, SecurityMethod.Pin.storageValue)
            .putString(KeyEncryptedWallet, encrypted.toBase64())
            .putString(KeyIv, iv.toBase64())
            .putString(KeySalt, salt.toBase64())
            .putInt(KeyPinKdfVersion, kdfVersion)
            .putString(KeyAddress, vault.activeWallet?.address ?: vault.activeAddress)
            .putString(KeyPublicKey, vault.activeWallet?.publicKeyHex.orEmpty())
            .putString(KeyWalletMetadata, walletMetadataJson(vault))
            .putLong(KeyCreatedAt, System.currentTimeMillis())
            .apply()
        encrypted.fill(0)
        iv.fill(0)
    }

    private fun derivePinKey(
        pin: String,
        salt: ByteArray,
        bindToDevice: Boolean,
        createDeviceKey: Boolean,
    ): DerivedPinKey {
        val stretched = platformPbkdf(pin, salt) ?: bouncyCastlePbkdf(pin, salt)
        if (!bindToDevice) return DerivedPinKey(stretched, LegacyPinKdfVersion)

        val deviceKey = runCatching {
            if (createDeviceKey) getOrCreatePinBindingKey() else loadPinBindingKey()
        }.getOrNull()
        if (deviceKey == null) {
            if (createDeviceKey) return DerivedPinKey(stretched, LegacyPinKdfVersion)
            stretched.fill(0)
            throw PinDeviceBindingException("This PIN vault is bound to a device key that is no longer available.")
        }
        return try {
            val mac = Mac.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256)
            mac.init(deviceKey)
            DerivedPinKey(mac.doFinal(stretched), DeviceBoundPinKdfVersion)
        } finally {
            stretched.fill(0)
        }
    }

    private fun platformPbkdf(pin: String, salt: ByteArray): ByteArray? {
        val password = pin.toCharArray()
        val spec = PBEKeySpec(password, salt, PbkdfIterations, KeyLengthBytes * 8)
        return try {
            SecretKeyFactory.getInstance(PbkdfAlgorithm).generateSecret(spec).encoded
        } catch (_: Exception) {
            null
        } finally {
            spec.clearPassword()
            password.fill('\u0000')
        }
    }

    private fun bouncyCastlePbkdf(pin: String, salt: ByteArray): ByteArray {
        val password = pin.toByteArray(Charsets.UTF_8)
        return try {
            val generator = PKCS5S2ParametersGenerator(SHA256Digest())
            generator.init(password, salt, PbkdfIterations)
            (generator.generateDerivedParameters(KeyLengthBytes * 8) as KeyParameter).key
        } finally {
            password.fill(0)
        }
    }

    private fun getOrCreatePinBindingKey(): SecretKey {
        loadPinBindingKey()?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_HMAC_SHA256, AndroidKeyStore)
        generator.init(
            KeyGenParameterSpec.Builder(PinBindingAlias, KeyProperties.PURPOSE_SIGN)
                .setDigests(KeyProperties.DIGEST_SHA256)
                .build(),
        )
        return generator.generateKey()
    }

    private fun loadPinBindingKey(): SecretKey? =
        KeyStore.getInstance(AndroidKeyStore).apply { load(null) }.getKey(PinBindingAlias, null) as? SecretKey

    private fun deletePinBindingKey() {
        runCatching {
            KeyStore.getInstance(AndroidKeyStore).apply {
                load(null)
                if (containsAlias(PinBindingAlias)) deleteEntry(PinBindingAlias)
            }
        }
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

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            builder.setInvalidatedByBiometricEnrollment(true)
        }

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
        private const val PinBindingAlias = "poctra_wallet_pin_binding_key_v2"
        private const val AesMode = "AES/GCM/NoPadding"
        private const val SaltLength = 32
        private const val IvLength = 12
        private const val KeyLengthBytes = 32
        private const val PbkdfAlgorithm = "PBKDF2WithHmacSHA256"
        private const val PbkdfIterations = 310_000
        private const val MaxPinAttempts = 5
        private const val PinLockoutMs = 5 * 60 * 1000L
        private const val LegacyPinKdfVersion = 1
        private const val DeviceBoundPinKdfVersion = 2

        private const val KeyMethod = "method"
        private const val KeyEncryptedWallet = "encrypted_wallet"
        private const val KeyIv = "iv"
        private const val KeySalt = "salt"
        private const val KeyPinKdfVersion = "pinKdfVersion"
        private const val KeyAddress = "address"
        private const val KeyPublicKey = "public_key"
        private const val KeyCreatedAt = "created_at"
        private const val KeyPinAttempts = "pin_attempts"
        private const val KeyPinLockedUntil = "pin_locked_until"
        private const val KeyWalletMetadata = "wallet_metadata"
    }
}

class PinDeviceBindingException(message: String) : IllegalStateException(message)
