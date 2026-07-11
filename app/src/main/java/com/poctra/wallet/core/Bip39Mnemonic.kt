package com.poctra.wallet.core

import android.content.Context
import org.bouncycastle.crypto.digests.SHA512Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter
import org.json.JSONArray
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class Bip39Mnemonic private constructor(private val words: List<String>) {
    private val wordIndex = words.withIndex().associate { it.value to it.index }
    private val secureRandom = SecureRandom()

    fun generate(strengthBits: Int = 128): String {
        require(strengthBits == 128 || strengthBits == 256) {
            "poctra mobile supports 12 or 24 word mnemonics."
        }
        val entropy = ByteArray(strengthBits / 8)
        secureRandom.nextBytes(entropy)
        return entropyToMnemonic(entropy)
    }

    fun validate(mnemonic: String): Boolean {
        return try {
            mnemonicToEntropy(mnemonic)
            true
        } catch (_: Exception) {
            false
        }
    }

    fun toSeed(mnemonic: String, passphrase: String = ""): ByteArray {
        val normalizedMnemonic = normalize(mnemonic)
        val salt = normalize("mnemonic$passphrase").toByteArray(Charsets.UTF_8)
        val platformSeed = runCatching {
            val spec = PBEKeySpec(normalizedMnemonic.toCharArray(), salt, Bip39Iterations, Bip39SeedBits)
            SecretKeyFactory.getInstance(PbkdfSha512).generateSecret(spec).encoded
        }.getOrNull()
        if (platformSeed != null) return platformSeed

        val generator = PKCS5S2ParametersGenerator(SHA512Digest())
        generator.init(normalizedMnemonic.toByteArray(Charsets.UTF_8), salt, Bip39Iterations)
        return (generator.generateDerivedParameters(Bip39SeedBits) as KeyParameter).key
    }

    private fun entropyToMnemonic(entropy: ByteArray): String {
        val checksumBits = entropy.size * 8 / 32
        val bits = entropy.toBitString() + sha256(entropy).toBitString().take(checksumBits)
        return bits.chunked(11).joinToString(" ") { chunk ->
            words[chunk.toInt(2)]
        }
    }

    private fun mnemonicToEntropy(mnemonic: String): ByteArray {
        val cleanWords = normalize(mnemonic).trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        require(cleanWords.size == 12 || cleanWords.size == 24) {
            "Mnemonic must contain 12 or 24 words."
        }

        val bits = cleanWords.joinToString("") { word ->
            val index = wordIndex[word] ?: throw IllegalArgumentException("Unknown mnemonic word: $word")
            index.toString(2).padStart(11, '0')
        }

        val entropyBitsLength = bits.length * 32 / 33
        val checksumBitsLength = bits.length - entropyBitsLength
        val entropyBits = bits.take(entropyBitsLength)
        val checksumBits = bits.takeLast(checksumBitsLength)
        val entropy = entropyBits.toBytes()
        val expectedChecksum = sha256(entropy).toBitString().take(checksumBitsLength)

        require(checksumBits == expectedChecksum) {
            "Mnemonic checksum is invalid."
        }
        return entropy
    }

    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)

    private fun ByteArray.toBitString(): String = joinToString("") { byte ->
        val unsigned = byte.toInt() and 0xff
        unsigned.toString(2).padStart(8, '0')
    }

    private fun String.toBytes(): ByteArray = chunked(8).map { it.toInt(2).toByte() }.toByteArray()

    private fun sha256(bytes: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(bytes)

    companion object {
        private const val Bip39Iterations = 2048
        private const val Bip39SeedBits = 512
        private const val PbkdfSha512 = "PBKDF2WithHmacSHA512"

        fun fromAssets(context: Context): Bip39Mnemonic {
            val json = context.assets.open("bip39_english.json").bufferedReader().use { it.readText() }
            val array = JSONArray(json)
            val words = buildList {
                for (index in 0 until array.length()) {
                    add(array.getString(index))
                }
            }
            require(words.size == 2048) { "BIP39 English wordlist must contain 2048 words." }
            return Bip39Mnemonic(words)
        }
    }
}
