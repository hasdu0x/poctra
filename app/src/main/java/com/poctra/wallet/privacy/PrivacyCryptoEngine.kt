package com.poctra.wallet.privacy

import android.util.Base64
import com.poctra.wallet.data.Wallet
import java.io.Closeable
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

    fun isSupported(): Boolean = runCatching { NativePvacBridge.nativeSupported() }.getOrDefault(false)

    suspend fun initialize(wallet: Wallet): PrivacyIdentity = withContext(dispatcher) {
        checkSupported()
        initializeNative(wallet)
        PrivacyIdentity(
            publicKey = NativePvacBridge.nativePublicKey(),
            aesKat = NativePvacBridge.nativeAesKat(),
        )
    }

    suspend fun decryptBalance(wallet: Wallet, encodedCipher: String): Long = withContext(dispatcher) {
        checkSupported()
        initializeNative(wallet)
        val raw = Base64.decode(encodedCipher.removePrefix(CipherPrefix), Base64.DEFAULT)
        NativePvacBridge.nativeDecrypt(raw).toLongOrNull()
            ?: throw IllegalStateException("Encrypted balance returned an invalid amount")
    }

    suspend fun createEncryptPayload(wallet: Wallet, amountRaw: Long): PrivacyEncryptPayload = withContext(dispatcher) {
        require(amountRaw > 0L) { "Privacy amount must be greater than zero." }
        checkSupported()
        initializeNative(wallet)
        val result = NativePvacBridge.nativeEncrypt(amountRaw, randomBytes(), randomBytes())
        require(result.size == 4) { "Native encrypt payload is incomplete" }
        PrivacyEncryptPayload(
            cipher = CipherPrefix + result[0].base64(),
            amountCommitment = result[1].base64(),
            zeroProof = ZeroProofPrefix + result[2].base64(),
            blinding = result[3].base64(),
        )
    }

    suspend fun createClaimPayload(
        wallet: Wallet,
        transfer: PrivacyClaimableTransfer,
    ): PrivacyClaimPayload = withContext(dispatcher) {
        checkSupported()
        initializeNative(wallet)
        val result = NativePvacBridge.nativeClaim(transfer.amountRaw, randomBytes(), transfer.blinding)
        require(result.size == 3) { "Native claim payload is incomplete" }
        PrivacyClaimPayload(
            cipher = CipherPrefix + result[0].base64(),
            commitment = result[1].base64(),
            zeroProof = ZeroProofPrefix + result[2].base64(),
        )
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
        val result = NativePvacBridge.nativeBuildDecrypt(
            amountRaw,
            currentBalanceRaw,
            decodeCipher(currentCipher),
            randomBytes(),
            randomBytes(),
        )
        require(result.size == 5) { "Native Decrypt payload is incomplete" }
        PrivacyDecryptPayload(
            cipher = CipherPrefix + result[0].base64(),
            amountCommitment = result[1].base64(),
            zeroProof = ZeroProofPrefix + result[2].base64(),
            blinding = result[3].base64(),
            rangeProofBalance = RangeProofPrefix + result[4].base64(),
        )
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
        val result = NativePvacBridge.nativeBuildStealth(
            amountRaw,
            currentBalanceRaw,
            decodeCipher(currentCipher),
            randomBytes(),
            blinding,
        )
        require(result.size == 6) { "Native Stealth payload is incomplete" }
        PrivacyStealthProofPayload(
            deltaCipher = CipherPrefix + result[0].base64(),
            commitment = result[1].base64(),
            rangeProofDelta = RangeProofPrefix + result[2].base64(),
            rangeProofBalance = RangeProofPrefix + result[3].base64(),
            amountCommitment = result[4].base64(),
            zeroProof = ZeroProofPrefix + result[5].base64(),
        )
    }

    suspend fun clear() = withContext(dispatcher) {
        NativePvacBridge.nativeClear()
    }

    override fun close() {
        runCatching {
            executor.execute { runCatching { NativePvacBridge.nativeClear() } }
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

    private fun randomBytes(): ByteArray = ByteArray(32).also(random::nextBytes)

    private fun decodeCipher(value: String): ByteArray =
        Base64.decode(value.removePrefix(CipherPrefix), Base64.DEFAULT)

    private fun Wallet.seedBytes(): ByteArray {
        val decoded = Base64.decode(privateKeyBase64, Base64.DEFAULT)
        require(decoded.size >= 32) { "Wallet key is not compatible with Privacy." }
        return decoded.copyOfRange(0, 32)
    }

    private fun ByteArray.base64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    private companion object {
        const val CipherPrefix = "hfhe_v1|"
        const val ZeroProofPrefix = "zkzp_v2|"
        const val RangeProofPrefix = "rp_v1|"
    }
}
