package com.poctra.wallet.privacy

import android.util.Base64
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.math.ec.rfc7748.X25519

class PrivacyStealthSender {
    private val random = SecureRandom()

    suspend fun createEnvelope(
        recipientAddress: String,
        recipientEd25519PublicKey: ByteArray,
        amountRaw: Long,
    ): PrivacyStealthEnvelope = withContext(Dispatchers.Default) {
        require(amountRaw > 0L) { "Stealth amount must be greater than zero." }
        require(recipientEd25519PublicKey.size == 32) { "Recipient public key is invalid." }
        val recipientViewPublicKey = ed25519PublicToX25519(recipientEd25519PublicKey)
        val ephemeralPrivateKey = ByteArray(32)
        val ephemeralPublicKey = ByteArray(32)
        val agreement = ByteArray(32)
        val blinding = ByteArray(32).also(random::nextBytes)
        X25519.generatePrivateKey(random, ephemeralPrivateKey)
        X25519.generatePublicKey(ephemeralPrivateKey, 0, ephemeralPublicKey, 0)
        check(X25519.calculateAgreement(ephemeralPrivateKey, 0, recipientViewPublicKey, 0, agreement, 0)) {
            "Unable to establish recipient stealth key."
        }
        val shared = sha256(agreement)
        val claimSecret = sha256(shared + ClaimSecretDomain)
        try {
            PrivacyStealthEnvelope(
                ephemeralPublicKey = ephemeralPublicKey,
                stealthTag = sha256(shared + StealthTagDomain).copyOf(16),
                encryptedAmount = encryptAmount(shared, amountRaw, blinding),
                claimPublicKey = sha256(
                    claimSecret + recipientAddress.toByteArray(Charsets.UTF_8) + ClaimBindDomain,
                ),
                blinding = blinding,
            )
        } catch (exception: Exception) {
            blinding.fill(0)
            throw exception
        } finally {
            ephemeralPrivateKey.fill(0)
            agreement.fill(0)
            shared.fill(0)
            claimSecret.fill(0)
        }
    }

    private fun ed25519PublicToX25519(publicKey: ByteArray): ByteArray {
        val encodedY = publicKey.copyOf()
        encodedY[31] = (encodedY[31].toInt() and 0x7f).toByte()
        val y = BigInteger(1, encodedY.reversedArray())
        require(y < FieldPrime) { "Recipient public key is outside the Ed25519 field." }
        val denominator = BigInteger.ONE.subtract(y).mod(FieldPrime)
        require(denominator != BigInteger.ZERO) { "Recipient public key cannot be converted to X25519." }
        val u = BigInteger.ONE
            .add(y)
            .mod(FieldPrime)
            .multiply(denominator.modInverse(FieldPrime))
            .mod(FieldPrime)
        val bigEndian = u.toByteArray().let { raw ->
            when {
                raw.size == 32 -> raw
                raw.size > 32 -> raw.copyOfRange(raw.size - 32, raw.size)
                else -> ByteArray(32 - raw.size) + raw
            }
        }
        return bigEndian.reversedArray()
    }

    private fun encryptAmount(shared: ByteArray, amountRaw: Long, blinding: ByteArray): String {
        val nonce = ByteArray(12).also(random::nextBytes)
        val plaintext = ByteArray(40)
        ByteBuffer.wrap(plaintext, 0, 8).order(ByteOrder.LITTLE_ENDIAN).putLong(amountRaw)
        blinding.copyInto(plaintext, destinationOffset = 8)
        return try {
            val encrypted = Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.ENCRYPT_MODE, SecretKeySpec(shared, "AES"), GCMParameterSpec(128, nonce))
                doFinal(plaintext)
            }
            require(encrypted.size == 56) { "Stealth envelope has an invalid size." }
            Base64.encodeToString(nonce + encrypted, Base64.NO_WRAP)
        } finally {
            plaintext.fill(0)
        }
    }

    private fun sha256(value: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(value)

    private companion object {
        val FieldPrime: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19L))
        val StealthTagDomain = "OCTRA_STEALTH_TAG_V1".toByteArray(Charsets.UTF_8)
        val ClaimSecretDomain = "OCTRA_CLAIM_SECRET_V1".toByteArray(Charsets.UTF_8)
        val ClaimBindDomain = "OCTRA_CLAIM_BIND_V1".toByteArray(Charsets.UTF_8)
    }
}
