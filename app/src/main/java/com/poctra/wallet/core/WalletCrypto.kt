package com.poctra.wallet.core

import android.content.Context
import android.util.Base64
import com.poctra.wallet.data.Wallet
import com.poctra.wallet.data.WalletType
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import java.math.BigInteger
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class WalletCrypto(context: Context) {
    private val mnemonic = Bip39Mnemonic.fromAssets(context.applicationContext)

    fun generateWallet(): Wallet {
        repeat(MaxGenerationAttempts) {
            val phrase = mnemonic.generate(strengthBits = 128)
            val wallet = walletFromMnemonic(phrase, WalletType.Generated)
            if (wallet.address.length == ExpectedAddressLength) return wallet
        }
        throw IllegalStateException("Failed to generate a valid Octra address.")
    }

    fun importFromMnemonic(value: String): Wallet {
        val words = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        require(words.size == 12 || words.size == 24) {
            "Seed phrase must contain 12 or 24 words."
        }
        require(mnemonic.validate(value)) {
            "Seed phrase checksum is invalid."
        }
        return requireValidOctraWallet(
            wallet = walletFromMnemonic(words.joinToString(" "), WalletType.ImportedMnemonic),
            source = "seed phrase",
        )
    }

    fun importFromPrivateKey(value: String): Wallet {
        val clean = value.trim()
        val seed = try {
            Base64.decode(clean, Base64.DEFAULT)
        } catch (_: Exception) {
            throw IllegalArgumentException("Private key must be valid Base64.")
        }
        require(seed.size == 32) {
            "Private key must decode to 32 bytes."
        }
        return requireValidOctraWallet(
            wallet = walletFromPrivateSeed(seed, mnemonic = null, type = WalletType.ImportedPrivateKey),
            source = "Base64 private key",
        )
    }

    fun importFromEthereumPrivateKeyHex(value: String): Wallet {
        val clean = value.trim().removePrefix("0x").removePrefix("0X")
        require(clean.matches(Regex("^[0-9a-fA-F]{64}$"))) {
            "Ethereum private key must be 64 hex characters, with optional 0x prefix."
        }
        val seed = clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val scalar = BigInteger(1, seed)
        require(scalar > BigInteger.ZERO && scalar < Secp256k1Order) {
            "Ethereum private key must be a valid secp256k1 private key."
        }
        return requireValidOctraWallet(
            wallet = walletFromPrivateSeed(seed, mnemonic = null, type = WalletType.ImportedEthereumPrivateKey),
            source = "Ethereum private key",
        )
    }

    private fun requireValidOctraWallet(wallet: Wallet, source: String): Wallet {
        require(wallet.address.startsWith(AddressPrefix) && wallet.address.length == ExpectedAddressLength) {
            "This $source is valid, but it derives ${wallet.address}, which is not a valid Octra address for poctra. Use another $source or create a new wallet."
        }
        return wallet
    }

    private fun walletFromMnemonic(phrase: String, type: WalletType): Wallet {
        val seed = mnemonic.toSeed(phrase)
        val masterPrivateKey = deriveMasterPrivateKey(seed)
        return walletFromPrivateSeed(masterPrivateKey, mnemonic = phrase, type = type)
    }

    private fun walletFromPrivateSeed(seed: ByteArray, mnemonic: String?, type: WalletType): Wallet {
        val privateKey = Ed25519PrivateKeyParameters(seed, 0)
        val publicKey = privateKey.generatePublicKey().encoded
        val address = createOctraAddress(publicKey)
        return Wallet(
            address = address,
            privateKeyBase64 = Base64.encodeToString(seed, Base64.NO_WRAP),
            publicKeyHex = publicKey.toHex(),
            mnemonic = mnemonic,
            type = type,
        )
    }

    private fun deriveMasterPrivateKey(seed: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA512")
        mac.init(SecretKeySpec("Octra seed".toByteArray(Charsets.UTF_8), "HmacSHA512"))
        return mac.doFinal(seed).copyOfRange(0, 32)
    }

    private fun createOctraAddress(publicKey: ByteArray): String {
        val hash = MessageDigest.getInstance("SHA-256").digest(publicKey)
        return "oct${Base58.encode(hash)}"
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }

    companion object {
        private const val AddressPrefix = "oct"
        private const val ExpectedAddressLength = 47
        private const val MaxGenerationAttempts = 100
        private val Secp256k1Order = BigInteger(
            "FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141",
            16,
        )
    }
}
