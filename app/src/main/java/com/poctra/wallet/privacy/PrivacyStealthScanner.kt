package com.poctra.wallet.privacy

import android.util.Base64
import com.poctra.wallet.data.Wallet
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.bouncycastle.math.ec.rfc7748.X25519
import org.json.JSONArray
import org.json.JSONObject

class PrivacyStealthScanner {
    suspend fun scan(wallet: Wallet, outputs: JSONArray): List<PrivacyClaimableTransfer> = withContext(Dispatchers.Default) {
        val viewKey = deriveViewKey(wallet)
        buildList {
            for (index in 0 until outputs.length()) {
                if (index % 16 == 0) coroutineContext.ensureActive()
                val output = outputs.optJSONObject(index) ?: continue
                if (output.isClaimed()) continue
                runCatching { matchOutput(viewKey, output) }.getOrNull()?.let { add(it) }
            }
        }
    }

    suspend fun reconstructMigrationBlinding(
        wallet: Wallet,
        outputs: JSONArray,
        crypto: PrivacyCryptoEngine,
        encryptedBalanceRaw: Long,
        expectedCommitmentBase64: String,
    ): ByteArray = withContext(Dispatchers.Default) {
        val viewKey = deriveViewKey(wallet)
        var accumulator = ByteArray(32)
        try {
            for (index in 0 until outputs.length()) {
                if (index % 8 == 0) coroutineContext.ensureActive()
                val output = outputs.optJSONObject(index) ?: continue
                val sentByWallet = output.optString("sender_addr")
                    .equals(wallet.address, ignoreCase = true)
                val claimed = output.isClaimed()
                if (!sentByWallet && !claimed) continue

                val decrypted = decryptOutput(viewKey, output)
                if (decrypted == null) {
                    if (sentByWallet) {
                        throw IllegalStateException("A sent Stealth output cannot be opened for migration.")
                    }
                    continue
                }
                val (amount, blinding, claimSecret) = decrypted
                try {
                    val storedCommitment = decodeCommitment(output.optString("amount_commitment"))
                    val localCommitment = crypto.pedersenCommit(wallet, amount, blinding)
                    check(MessageDigest.isEqual(storedCommitment, localCommitment)) {
                        "A Stealth output commitment does not match its encrypted amount."
                    }
                    if (sentByWallet) {
                        val next = crypto.subtractBlindings(wallet, accumulator, blinding)
                        accumulator.fill(0)
                        accumulator = next
                    }
                    if (claimed) {
                        val next = crypto.addBlindings(wallet, accumulator, blinding)
                        accumulator.fill(0)
                        accumulator = next
                    }
                } finally {
                    blinding.fill(0)
                    claimSecret.fill(0)
                }
            }
            val expected = decodeCommitment(expectedCommitmentBase64)
            val reconstructed = crypto.pedersenCommit(wallet, encryptedBalanceRaw, accumulator)
            check(MessageDigest.isEqual(expected, reconstructed)) {
                "Local Privacy history does not match the network migration commitment."
            }
            accumulator
        } catch (error: Throwable) {
            accumulator.fill(0)
            throw error
        } finally {
            viewKey.fill(0)
        }
    }

    private fun matchOutput(viewKey: ByteArray, output: JSONObject): PrivacyClaimableTransfer? {
        val decrypted = decryptOutput(viewKey, output) ?: return null
        return try {
            val outputId = output.opt("id")?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
            if (outputId.isBlank()) {
                decrypted.second.fill(0)
                decrypted.third.fill(0)
                return null
            }
            PrivacyClaimableTransfer(
                outputId = outputId,
                amountRaw = decrypted.first,
                epoch = output.optLong("epoch_id", 0L),
                sender = output.optString("sender_addr"),
                txHash = output.optString("tx_hash"),
                claimSecret = decrypted.third,
                blinding = decrypted.second,
            )
        } catch (error: Throwable) {
            decrypted.second.fill(0)
            decrypted.third.fill(0)
            throw error
        }
    }

    private fun decryptOutput(
        viewKey: ByteArray,
        output: JSONObject,
    ): Triple<Long, ByteArray, ByteArray>? {
        val ephemeral = runCatching {
            Base64.decode(output.optString("eph_pub"), Base64.DEFAULT)
        }.getOrNull() ?: return null
        if (ephemeral.size != 32) return null
        val agreement = ByteArray(32)
        if (!X25519.calculateAgreement(viewKey, 0, ephemeral, 0, agreement, 0)) return null
        val shared = sha256(agreement)
        return try {
            val expectedTag = sha256(shared + StealthTagDomain).copyOf(16).toHex()
            val outputTag = output.optString("stealth_tag").removePrefix("0x").lowercase()
            if (expectedTag != outputTag) return null
            val decrypted = decryptAmount(shared, output.optString("enc_amount")) ?: return null
            Triple(decrypted.first, decrypted.second, sha256(shared + ClaimSecretDomain))
        } finally {
            agreement.fill(0)
            shared.fill(0)
        }
    }

    private fun decodeCommitment(encoded: String): ByteArray {
        val value = runCatching { Base64.decode(encoded, Base64.DEFAULT) }
            .getOrElse { throw IllegalStateException("Privacy migration commitment is malformed.") }
        require(value.size == 32) { "Privacy migration commitment is missing." }
        return value
    }

    private fun deriveViewKey(wallet: Wallet): ByteArray {
        val seed = Base64.decode(wallet.privateKeyBase64, Base64.DEFAULT)
        require(seed.size >= 32) { "Wallet key is not compatible with Claim." }
        val hash = MessageDigest.getInstance("SHA-512").digest(seed.copyOfRange(0, 32))
        val viewKey = hash.copyOfRange(0, 32).also {
            it[0] = (it[0].toInt() and 248).toByte()
            it[31] = (it[31].toInt() and 127 or 64).toByte()
        }
        seed.fill(0)
        hash.fill(0)
        return viewKey
    }

    private fun decryptAmount(shared: ByteArray, encoded: String): Pair<Long, ByteArray>? {
        val raw = runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull() ?: return null
        if (raw.size != 68) return null
        val nonce = raw.copyOfRange(0, 12)
        val ciphertextAndTag = raw.copyOfRange(12, raw.size)
        val plain = runCatching {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, SecretKeySpec(shared, "AES"), GCMParameterSpec(128, nonce))
                doFinal(ciphertextAndTag)
            }
        }.getOrNull() ?: return null
        if (plain.size < 40) return null
        val amount = ByteBuffer.wrap(plain, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long
        if (amount <= 0L) return null
        val blinding = plain.copyOfRange(8, 40)
        plain.fill(0)
        return amount to blinding
    }

    private fun JSONObject.isClaimed(): Boolean = when (val value = opt("claimed")) {
        is Boolean -> value
        is Number -> value.toLong() != 0L
        is String -> value != "0" && !value.equals("false", ignoreCase = true)
        else -> false
    }

    private fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private companion object {
        val StealthTagDomain = "OCTRA_STEALTH_TAG_V1".toByteArray(Charsets.UTF_8)
        val ClaimSecretDomain = "OCTRA_CLAIM_SECRET_V1".toByteArray(Charsets.UTF_8)
    }
}
