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

    private fun matchOutput(viewKey: ByteArray, output: JSONObject): PrivacyClaimableTransfer? {
        val ephemeral = Base64.decode(output.optString("eph_pub"), Base64.DEFAULT)
        if (ephemeral.size != 32) return null
        val agreement = ByteArray(32)
        if (!X25519.calculateAgreement(viewKey, 0, ephemeral, 0, agreement, 0)) return null
        val shared = sha256(agreement)
        return try {
            val expectedTag = sha256(shared + StealthTagDomain).copyOf(16).toHex()
            val outputTag = output.optString("stealth_tag").removePrefix("0x").lowercase()
            if (expectedTag != outputTag) return null

            val decrypted = decryptAmount(shared, output.optString("enc_amount")) ?: return null
            val outputId = output.opt("id")?.takeUnless { it == JSONObject.NULL }?.toString().orEmpty()
            if (outputId.isBlank()) {
                decrypted.second.fill(0)
                return null
            }
            PrivacyClaimableTransfer(
                outputId = outputId,
                amountRaw = decrypted.first,
                epoch = output.optLong("epoch_id", 0L),
                sender = output.optString("sender_addr"),
                txHash = output.optString("tx_hash"),
                claimSecret = sha256(shared + ClaimSecretDomain),
                blinding = decrypted.second,
            )
        } finally {
            agreement.fill(0)
            shared.fill(0)
        }
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
