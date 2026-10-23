package com.poctra.wallet.privacy

import android.util.Base64
import com.poctra.wallet.data.Wallet
import java.io.Closeable
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext

class PrivacyCryptoEngine : Closeable {
    private val executor = Executors.newSingleThreadExecutor { work ->
        Thread(work, "poctra-privacy-crypto").apply { isDaemon = true }
    }
    private val dispatcher: CoroutineDispatcher = executor.asCoroutineDispatcher()
    private val random = SecureRandom()
    private var cachedIdentityKey: String? = null
    private var cachedIdentity: PrivacyIdentity? = null

    fun isSupported(): Boolean = runCatching { NativePvacBridge.nativeSupported() }.getOrDefault(false)

    suspend fun initialize(wallet: Wallet): PrivacyIdentity = withContext(dispatcher) {
        checkSupported()
        val identityKey = wallet.identityCacheKey()
        cachedIdentity?.takeIf { cachedIdentityKey == identityKey }?.let {
            return@withContext it
        }
        clearCachedIdentity()
        initializeNative(wallet)
        PrivacyIdentity(
            publicKey = NativePvacBridge.nativePublicKey(),
            aesKat = NativePvacBridge.nativeAesKat(),
        ).also {
            cachedIdentityKey = identityKey
            cachedIdentity = it
        }
    }

    suspend fun decryptBalance(wallet: Wallet, encodedCipher: String): Long = withContext(dispatcher) {
        checkSupported()
        initializeNative(wallet)
        val cipher = decodeCipher(encodedCipher)
        try {
            NativePvacBridge.nativeDecrypt(cipher).toLongOrNull()
                ?: throw IllegalStateException("Encrypted balance returned an invalid amount")
        } finally {
            cipher.fill(0)
        }
    }

    suspend fun decryptBalanceWithRegisteredKey(
        wallet: Wallet,
        encodedCipher: String,
        registeredPublicKeyBase64: String,
        legacyProfile: Boolean,
    ): Long = withContext(dispatcher) {
        checkSupported()
        initializeNative(wallet)
        val cipher = decodeCipher(encodedCipher)
        val publicKey = decodeBase64(registeredPublicKeyBase64, "Registered Privacy key")
        try {
            NativePvacBridge.nativeDecryptWithPublicKey(cipher, publicKey, legacyProfile)
                .toLongOrNull()
                ?: throw IllegalStateException("Encrypted balance returned an invalid amount")
        } finally {
            cipher.fill(0)
            publicKey.fill(0)
        }
    }

    suspend fun inspectCipher(
        wallet: Wallet,
        encodedCipher: String,
    ): PrivacyCipherDiagnostics = withContext(dispatcher) {
        checkSupported()
        initializeNative(wallet)
        val cipher = decodeCipher(encodedCipher)
        try {
            val values = NativePvacBridge.nativeInspectCipher(cipher)
            require(values.size == 3) { "Privacy cipher diagnostics are incomplete" }
            PrivacyCipherDiagnostics(
                keyBound = values[0] == 1L,
                canonicalRandomnessCommitment = values[1] == 1L,
                baseLayerCount = values[2],
            )
        } finally {
            cipher.fill(0)
        }
    }

    suspend fun keyCompatibility(
        wallet: Wallet,
        registeredPublicKeyBase64: String,
    ): PrivacyKeyCompatibility = withContext(dispatcher) {
        checkSupported()
        initializeNative(wallet)
        val publicKey = decodeBase64(registeredPublicKeyBase64, "Registered Privacy key")
        try {
            val flags = NativePvacBridge.nativeKeyCompatibility(publicKey)
            PrivacyKeyCompatibility(
                extendsLocalKey = flags and 1 != 0,
                legacyV1Profile = flags and 2 != 0,
                proofProfile = flags and 4 != 0,
                secretProfile = flags and 8 != 0,
            )
        } finally {
            publicKey.fill(0)
        }
    }

    suspend fun publicKeyMatches(
        wallet: Wallet,
        expectedPublicKey: ByteArray,
        registeredPublicKeyBase64: String,
    ): Boolean = withContext(dispatcher) {
        if (PrivacyKeyEncoding.matches(registeredPublicKeyBase64, expectedPublicKey)) {
            return@withContext true
        }
        checkSupported()
        initializeNative(wallet)
        val registeredPublicKey = decodeBase64(
            registeredPublicKeyBase64,
            "Registered Privacy key",
        )
        try {
            val flags = NativePvacBridge.nativeKeyCompatibility(registeredPublicKey)
            PrivacyKeyEncoding.matchesCompatibilityFlags(flags)
        } finally {
            registeredPublicKey.fill(0)
        }
    }

    suspend fun createEncryptPayload(wallet: Wallet, amountRaw: Long): PrivacyEncryptPayload = withContext(dispatcher) {
        require(amountRaw > 0L) { "Privacy amount must be greater than zero." }
        checkSupported()
        initializeNative(wallet)
        val seed = randomBytes()
        val blinding = randomBytes()
        try {
            val result = NativePvacBridge.nativeEncrypt(amountRaw, seed, blinding)
            require(result.size == 4) { "Native encrypt payload is incomplete" }
            PrivacyEncryptPayload(
                cipher = CipherPrefix + result[0].base64(),
                amountCommitment = result[1].base64(),
                zeroProof = ZeroProofPrefix + result[2].base64(),
                blinding = result[3].base64(),
            ).also { result.wipe() }
        } finally {
            seed.fill(0)
            blinding.fill(0)
        }
    }

    suspend fun createClaimPayload(
        wallet: Wallet,
        transfer: PrivacyClaimableTransfer,
    ): PrivacyClaimPayload = withContext(dispatcher) {
        checkSupported()
        initializeNative(wallet)
        val seed = randomBytes()
        try {
            val result = NativePvacBridge.nativeClaim(transfer.amountRaw, seed, transfer.blinding)
            require(result.size == 3) { "Native claim payload is incomplete" }
            PrivacyClaimPayload(
                cipher = CipherPrefix + result[0].base64(),
                commitment = result[1].base64(),
                zeroProof = ZeroProofPrefix + result[2].base64(),
            ).also { result.wipe() }
        } finally {
            seed.fill(0)
        }
    }

    suspend fun createDecryptPayload(
        wallet: Wallet,
        currentCipher: String,
        currentBalanceRaw: Long,
        amountRaw: Long,
    ): PrivacyDecryptPayload = withContext(dispatcher) {
        require(amountRaw > 0L && amountRaw <= currentBalanceRaw) { "Insufficient encrypted balance." }
        checkSupported()
        initializeNative(wallet)
        val cipher = decodeCipher(currentCipher)
        val seed = randomBytes()
        val blinding = randomBytes()
        val rangeBlinding = randomBytes()
        try {
            val result = NativePvacBridge.nativeBuildDecrypt(
                amountRaw,
                currentBalanceRaw,
                cipher,
                seed,
                blinding,
                rangeBlinding,
            )
            require(result.size == 5) { "Native Decrypt payload is incomplete" }
            PrivacyDecryptPayload(
                cipher = CipherPrefix + result[0].base64(),
                amountCommitment = result[1].base64(),
                zeroProof = ZeroProofPrefix + result[2].base64(),
                blinding = result[3].base64(),
                rangeProofBalance = RangeProofPrefix + result[4].base64(),
            ).also { result.wipe() }
        } finally {
            cipher.fill(0)
            seed.fill(0)
            blinding.fill(0)
            rangeBlinding.fill(0)
        }
    }

    suspend fun createStealthProofPayload(
        wallet: Wallet,
        currentCipher: String,
        currentBalanceRaw: Long,
        amountRaw: Long,
        blinding: ByteArray,
    ): PrivacyStealthProofPayload = withContext(dispatcher) {
        require(amountRaw > 0L && amountRaw <= currentBalanceRaw) { "Insufficient encrypted balance." }
        checkSupported()
        initializeNative(wallet)
        val cipher = decodeCipher(currentCipher)
        val seed = randomBytes()
        val deltaRangeBlinding = randomBytes()
        val balanceRangeBlinding = randomBytes()
        try {
            val result = NativePvacBridge.nativeBuildStealth(
                amountRaw,
                currentBalanceRaw,
                cipher,
                seed,
                blinding,
                deltaRangeBlinding,
                balanceRangeBlinding,
            )
            require(result.size == 6) { "Native Stealth payload is incomplete" }
            PrivacyStealthProofPayload(
                deltaCipher = CipherPrefix + result[0].base64(),
                commitment = result[1].base64(),
                rangeProofDelta = RangeProofPrefix + result[2].base64(),
                rangeProofBalance = RangeProofPrefix + result[3].base64(),
                amountCommitment = result[4].base64(),
                zeroProof = ZeroProofPrefix + result[5].base64(),
            ).also { result.wipe() }
        } finally {
            cipher.fill(0)
            seed.fill(0)
            deltaRangeBlinding.fill(0)
            balanceRangeBlinding.fill(0)
        }
    }

    suspend fun createMigrationPayload(
        wallet: Wallet,
        mode: PrivacyKeyUpgradeMode,
        amountRaw: Long,
        currentCipher: String?,
        registeredPublicKeyBase64: String?,
        migrationBlinding: ByteArray? = null,
    ): PrivacyMigrationPayload = withContext(dispatcher) {
        require(amountRaw >= 0L) { "Privacy migration amount is invalid." }
        checkSupported()
        initializeNative(wallet)
        val nativeMode = when (mode) {
            PrivacyKeyUpgradeMode.KeyBoundMigration -> 1
            PrivacyKeyUpgradeMode.LegacyPublicMigration -> 2
            PrivacyKeyUpgradeMode.LegacyCommitmentMigration -> 3
            PrivacyKeyUpgradeMode.LegacyZeroReset -> 4
            else -> throw IllegalArgumentException("Privacy key mode does not require balance migration.")
        }
        val cipher = currentCipher?.let(::decodeCipher)
        val oldPublicKey = registeredPublicKeyBase64
            ?.takeIf(String::isNotBlank)
            ?.let { decodeBase64(it, "Registered Privacy key") }
        val blinding = migrationBlinding?.copyOf() ?: randomBytes()
        val seed = randomBytes()
        try {
            val result = NativePvacBridge.nativeBuildMigration(
                nativeMode,
                amountRaw,
                cipher,
                oldPublicKey,
                seed,
                blinding,
            )
            require(result.size == 5) { "Native Privacy migration payload is incomplete" }
            PrivacyMigrationPayload(
                newCipher = CipherPrefix + result[0].base64(),
                newZeroProof = ZeroProofPrefix + result[1].base64(),
                amountCommitment = result[2].base64(),
                oldZeroProof = result[3].takeIf(ByteArray::isNotEmpty)?.let {
                    ZeroProofPrefix + it.base64()
                },
                amountBlinding = result[4].takeIf(ByteArray::isNotEmpty)?.base64(),
            ).also { result.wipe() }
        } finally {
            cipher?.fill(0)
            oldPublicKey?.fill(0)
            seed.fill(0)
            blinding.fill(0)
        }
    }

    suspend fun pedersenCommit(
        wallet: Wallet,
        amountRaw: Long,
        blinding: ByteArray,
    ): ByteArray = withContext(dispatcher) {
        checkSupported()
        initializeNative(wallet)
        NativePvacBridge.nativePedersenCommit(amountRaw, blinding)
    }

    suspend fun addBlindings(wallet: Wallet, left: ByteArray, right: ByteArray): ByteArray =
        withContext(dispatcher) {
            checkSupported()
            initializeNative(wallet)
            NativePvacBridge.nativeBlindingAdd(left, right)
        }

    suspend fun subtractBlindings(wallet: Wallet, left: ByteArray, right: ByteArray): ByteArray =
        withContext(dispatcher) {
            checkSupported()
            initializeNative(wallet)
            NativePvacBridge.nativeBlindingSubtract(left, right)
        }

    suspend fun clear() = withContext(dispatcher) {
        clearCachedIdentity()
        NativePvacBridge.nativeClear()
    }

    override fun close() {
        runCatching {
            executor.execute {
                runCatching {
                    clearCachedIdentity()
                    NativePvacBridge.nativeClear()
                }
            }
        }
        executor.shutdown()
    }

    private fun checkSupported() {
        check(isSupported()) { "Privacy requires a 64-bit device with hardware AES support." }
    }

    private fun initializeNative(wallet: Wallet) {
        val seed = wallet.seedBytes()
        try {
            NativePvacBridge.nativeInitialize(seed)
        } finally {
            seed.fill(0)
        }
    }

    private fun clearCachedIdentity() {
        cachedIdentity?.publicKey?.fill(0)
        cachedIdentity?.aesKat?.fill(0)
        cachedIdentity = null
        cachedIdentityKey = null
    }

    private fun Wallet.identityCacheKey(): String {
        val seed = seedBytes()
        return try {
            val fingerprint = MessageDigest.getInstance("SHA-256")
                .digest(seed)
                .take(8)
                .joinToString("") { "%02x".format(it.toInt() and 0xff) }
            "${address.lowercase()}|$fingerprint"
        } finally {
            seed.fill(0)
        }
    }

    private fun randomBytes(): ByteArray = ByteArray(32).also(random::nextBytes)

    private fun decodeCipher(value: String): ByteArray =
        decodeBase64(value.removePrefix(CipherPrefix), "Encrypted balance")

    private fun decodeBase64(value: String, label: String): ByteArray =
        runCatching { Base64.decode(value, Base64.DEFAULT) }
            .getOrElse { throw IllegalArgumentException("$label is malformed.") }
            .also { require(it.isNotEmpty()) { "$label is empty." } }

    private fun Wallet.seedBytes(): ByteArray {
        val decoded = Base64.decode(privateKeyBase64, Base64.DEFAULT)
        require(decoded.size >= 32) { "Wallet key is not compatible with Privacy." }
        return decoded.copyOfRange(0, 32)
    }

    private fun ByteArray.base64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private fun Array<ByteArray>.wipe() {
        forEach { it.fill(0) }
    }

    private companion object {
        const val CipherPrefix = "hfhe_v1|"
        const val ZeroProofPrefix = "zkzp_v2|"
        const val RangeProofPrefix = "rp_v1|"
    }
}
