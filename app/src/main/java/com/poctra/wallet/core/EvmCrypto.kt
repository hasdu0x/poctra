package com.poctra.wallet.core

import android.util.Base64
import com.poctra.wallet.data.Wallet
import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.crypto.digests.KeccakDigest
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.ec.CustomNamedCurves
import org.bouncycastle.crypto.params.ECDomainParameters
import org.bouncycastle.crypto.params.ECPrivateKeyParameters
import org.bouncycastle.crypto.signers.ECDSASigner
import org.bouncycastle.crypto.signers.HMacDSAKCalculator
import org.bouncycastle.math.ec.ECAlgorithms
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.math.ec.FixedPointCombMultiplier
import java.math.BigInteger
import java.util.Locale

class EvmCrypto {
    fun deriveAddress(wallet: Wallet): String {
        val privateKey = wallet.evmPrivateKey()
        val publicKey = publicPoint(privateKey).getEncoded(false).drop(1).toByteArray()
        val address = keccak(publicKey).takeLast(20).toByteArray().toHex()
        return checksumAddress(address)
    }

    fun exportPrivateKeyHex(wallet: Wallet): String =
        "0x${wallet.evmPrivateKey().toFixedBytes(32).toHex()}"

    fun signPersonalMessage(wallet: Wallet, message: ByteArray): String {
        val prefix = "\u0019Ethereum Signed Message:\n${message.size}".toByteArray(Charsets.UTF_8)
        return signRecoverableHash(wallet, keccak(prefix + message))
    }

    fun signRecoverableHash(wallet: Wallet, hash: ByteArray): String {
        require(hash.size == 32) { "EVM signing hash must be 32 bytes." }
        val signature = signHash(hash, wallet.evmPrivateKey())
        val bytes = signature.r.toFixedBytes(32) +
            signature.s.toFixedBytes(32) +
            byteArrayOf((signature.recoveryId + 27).toByte())
        return "0x${bytes.toHex()}"
    }

    fun signLegacyTransaction(
        wallet: Wallet,
        nonce: BigInteger,
        gasPriceWei: BigInteger,
        gasLimit: BigInteger,
        toAddress: String,
        valueWei: BigInteger,
        data: ByteArray = ByteArray(0),
        chainId: Long,
    ): String {
        val privateKey = wallet.evmPrivateKey()
        val to = toAddress.cleanHex().hexToBytes()
        require(to.size == 20) { "Enter a valid EVM recipient address." }

        val unsigned = listOf(
            nonce,
            gasPriceWei,
            gasLimit,
            to,
            valueWei,
            data,
            BigInteger.valueOf(chainId),
            BigInteger.ZERO,
            BigInteger.ZERO,
        )
        val signingHash = keccak(Rlp.encodeList(unsigned.map(Rlp::encode)))
        val signature = signHash(signingHash, privateKey)
        val v = BigInteger.valueOf(chainId * 2 + 35 + signature.recoveryId)
        val signed = listOf(
            nonce,
            gasPriceWei,
            gasLimit,
            to,
            valueWei,
            data,
            v,
            signature.r,
            signature.s,
        )
        return "0x${Rlp.encodeList(signed.map(Rlp::encode)).toHex()}"
    }

    private fun signHash(hash: ByteArray, privateKey: BigInteger): EvmSignature {
        val signer = ECDSASigner(HMacDSAKCalculator(SHA256Digest()))
        signer.init(true, ECPrivateKeyParameters(privateKey, Domain))
        val components = signer.generateSignature(hash)
        val r = components[0]
        var s = components[1]
        if (s > HalfCurveOrder) s = CurveOrder.subtract(s)
        val recoveryId = recoverId(hash, r, s, privateKey)
        return EvmSignature(r, s, recoveryId)
    }

    private fun recoverId(hash: ByteArray, r: BigInteger, s: BigInteger, privateKey: BigInteger): Int {
        val publicKey = publicPoint(privateKey).normalize()
        for (id in 0..1) {
            val recovered = recoverPublicKey(id, r, s, hash)?.normalize()
            if (recovered != null && recovered == publicKey) return id
        }
        throw IllegalStateException("Unable to recover EVM signature id.")
    }

    private fun recoverPublicKey(recId: Int, r: BigInteger, s: BigInteger, hash: ByteArray): ECPoint? {
        val x = r.add(BigInteger.valueOf(recId / 2L).multiply(CurveOrder))
        val prime = Curve.curve.field.characteristic
        if (x >= prime) return null
        val compressed = ByteArray(33)
        compressed[0] = if (recId and 1 == 1) 0x03 else 0x02
        val xBytes = x.toFixedBytes(32)
        System.arraycopy(xBytes, 0, compressed, 1, xBytes.size)
        val rPoint = runCatching { Curve.curve.decodePoint(compressed) }.getOrNull() ?: return null
        if (!rPoint.multiply(CurveOrder).isInfinity) return null

        val e = BigInteger(1, hash)
        val rInverse = r.modInverse(CurveOrder)
        val eInverse = BigInteger.ZERO.subtract(e).mod(CurveOrder)
        val q = ECAlgorithms.sumOfTwoMultiplies(Curve.g, eInverse.multiply(rInverse).mod(CurveOrder), rPoint, s.multiply(rInverse).mod(CurveOrder))
        return q
    }

    private fun publicPoint(privateKey: BigInteger): ECPoint =
        FixedPointCombMultiplier().multiply(Curve.g, privateKey).normalize()

    private fun Wallet.evmPrivateKey(): BigInteger {
        val bytes = Base64.decode(privateKeyBase64, Base64.NO_WRAP)
        val seed = when {
            bytes.size == 32 -> bytes
            bytes.size >= 64 -> bytes.copyOfRange(0, 32)
            else -> throw IllegalArgumentException("Octra private key is not a valid EVM seed.")
        }
        val key = BigInteger(1, seed)
        require(key > BigInteger.ZERO && key < CurveOrder) { "Octra key cannot be used as a valid EVM key." }
        return key
    }

    companion object {
        private val Curve: X9ECParameters = CustomNamedCurves.getByName("secp256k1")
        private val Domain = ECDomainParameters(Curve.curve, Curve.g, Curve.n, Curve.h)
        private val CurveOrder: BigInteger = Curve.n
        private val HalfCurveOrder: BigInteger = CurveOrder.shiftRight(1)

        fun keccak(bytes: ByteArray): ByteArray {
            val digest = KeccakDigest(256)
            digest.update(bytes, 0, bytes.size)
            val output = ByteArray(32)
            digest.doFinal(output, 0)
            return output
        }

        fun String.cleanHex(): String = removePrefix("0x").removePrefix("0X")

        fun String.hexToBytes(): ByteArray {
            val clean = cleanHex()
            require(clean.length % 2 == 0) { "Hex value has invalid length." }
            return ByteArray(clean.length / 2) { index ->
                clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            }
        }

        fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

        fun parseEther(value: String): BigInteger {
            val clean = value.trim().replace(",", ".")
            require(clean.matches(Regex("^\\d+(\\.\\d{0,18})?$"))) { "Enter a valid EVM amount." }
            val parts = clean.split(".")
            val whole = parts[0].ifBlank { "0" }.toBigInteger()
            val fraction = parts.getOrNull(1).orEmpty().padEnd(18, '0').take(18).ifBlank { "0" }.toBigInteger()
            return whole.multiply(WeiPerEther).add(fraction)
        }

        fun formatEther(wei: BigInteger, scale: Int = 6): String {
            return formatUnits(wei, 18, scale)
        }

        fun parseUnits(value: String, decimals: Int): BigInteger {
            val clean = value.trim().replace(",", ".")
            require(clean.matches(Regex("^\\d+(\\.\\d{0,${decimals.coerceAtLeast(0)}})?$"))) { "Enter a valid amount." }
            val parts = clean.split(".")
            val whole = parts[0].ifBlank { "0" }.toBigInteger()
            val unit = BigInteger.TEN.pow(decimals.coerceAtLeast(0))
            val fraction = parts.getOrNull(1)
                .orEmpty()
                .padEnd(decimals.coerceAtLeast(0), '0')
                .take(decimals.coerceAtLeast(0))
                .ifBlank { "0" }
                .toBigInteger()
            return whole.multiply(unit).add(fraction)
        }

        fun formatUnits(value: BigInteger, decimals: Int, scale: Int = 6): String {
            val unit = BigInteger.TEN.pow(decimals.coerceAtLeast(0))
            if (unit == BigInteger.ONE) return value.toString()
            val whole = value.divide(unit)
            val fraction = value.mod(unit).toString().padStart(decimals.coerceAtLeast(0), '0')
            val visible = fraction.take(scale.coerceAtMost(decimals.coerceAtLeast(0))).trimEnd('0')
            return if (visible.isBlank()) whole.toString() else "$whole.$visible"
        }

        fun checksumAddress(addressNoPrefix: String): String {
            val lower = addressNoPrefix.lowercase(Locale.US)
            val hash = keccak(lower.toByteArray(Charsets.UTF_8)).toHex()
            val checked = buildString {
                lower.forEachIndexed { index, char ->
                    append(
                        if (char.isDigit()) char
                        else if (hash[index].digitToInt(16) >= 8) char.uppercaseChar()
                        else char
                    )
                }
            }
            return "0x$checked"
        }

        val WeiPerEther: BigInteger = BigInteger.TEN.pow(18)
    }
}

private data class EvmSignature(
    val r: BigInteger,
    val s: BigInteger,
    val recoveryId: Int,
)

private object Rlp {
    fun encode(value: Any): ByteArray = when (value) {
        is BigInteger -> encodeBytes(value.toMinimalBytes())
        is ByteArray -> encodeBytes(value)
        else -> throw IllegalArgumentException("Unsupported RLP value.")
    }

    fun encodeList(items: List<ByteArray>): ByteArray {
        val payload = items.fold(ByteArray(0)) { acc, item -> acc + item }
        return encodeLength(payload.size, 0xc0) + payload
    }

    private fun encodeBytes(bytes: ByteArray): ByteArray {
        if (bytes.size == 1 && (bytes[0].toInt() and 0xff) < 0x80) return bytes
        return encodeLength(bytes.size, 0x80) + bytes
    }

    private fun encodeLength(length: Int, offset: Int): ByteArray {
        if (length < 56) return byteArrayOf((length + offset).toByte())
        val binaryLength = BigInteger.valueOf(length.toLong()).toMinimalBytes()
        return byteArrayOf((offset + 55 + binaryLength.size).toByte()) + binaryLength
    }
}

private fun BigInteger.toMinimalBytes(): ByteArray {
    if (this == BigInteger.ZERO) return ByteArray(0)
    val bytes = toByteArray()
    return if (bytes.firstOrNull() == 0.toByte()) bytes.drop(1).toByteArray() else bytes
}

private fun BigInteger.toFixedBytes(size: Int): ByteArray {
    val minimal = toMinimalBytes()
    require(minimal.size <= size) { "Number does not fit in $size bytes." }
    return ByteArray(size - minimal.size) + minimal
}
