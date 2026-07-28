package com.poctra.wallet.data

import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.URI

enum class WalletType(val storageValue: String) {
    Generated("generated"),
    ImportedMnemonic("imported-mnemonic"),
    ImportedPrivateKey("imported-private-key"),
    ImportedEthereumPrivateKey("imported-ethereum-private-key");

    companion object {
        fun fromStorage(value: String?): WalletType = entries.firstOrNull {
            it.storageValue == value
        } ?: ImportedPrivateKey
    }
}

enum class SecurityMethod(val storageValue: String, val label: String) {
    Biometric("biometric", "Fingerprint"),
    Pin("pin", "PIN");

    companion object {
        fun fromStorage(value: String?): SecurityMethod? = entries.firstOrNull {
            it.storageValue == value
        }
    }
}

data class Wallet(
    val address: String,
    val privateKeyBase64: String,
    val publicKeyHex: String,
    val mnemonic: String?,
    val type: WalletType,
    val name: String = "",
    val identiconSvg: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("address", address)
        .put("privateKey", privateKeyBase64)
        .put("publicKey", publicKeyHex)
        .put("type", type.storageValue)
        .put("name", name)
        .put("identiconSvg", identiconSvg)
        .apply {
            if (!mnemonic.isNullOrBlank()) put("mnemonic", mnemonic)
        }

    companion object {
        fun fromJson(json: JSONObject): Wallet = Wallet(
            address = json.getString("address"),
            privateKeyBase64 = json.getString("privateKey"),
            publicKeyHex = json.optString("publicKey", ""),
            mnemonic = if (json.has("mnemonic") && !json.isNull("mnemonic")) {
                json.getString("mnemonic")
            } else {
                null
            },
            type = WalletType.fromStorage(json.optString("type")),
            name = json.optString("name"),
            identiconSvg = json.optString("identiconSvg"),
        )
    }
}

data class WalletVault(
    val wallets: List<Wallet>,
    val activeAddress: String,
) {
    val activeWallet: Wallet?
        get() = wallets.firstOrNull { it.address == activeAddress } ?: wallets.firstOrNull()

    fun withActive(address: String): WalletVault {
        require(wallets.any { it.address == address }) { "Wallet is not in this vault." }
        return copy(activeAddress = address)
    }

    fun addWallet(wallet: Wallet): WalletVault {
        val nextWallets = if (wallets.any { it.address == wallet.address }) {
            wallets
        } else {
            wallets + wallet
        }
        return WalletVault(wallets = nextWallets, activeAddress = wallet.address)
    }

    fun removeWallet(address: String): WalletVault {
        require(wallets.size > 1) { "At least one wallet must remain." }
        val nextWallets = wallets.filterNot { it.address == address }
        require(nextWallets.isNotEmpty()) { "At least one wallet must remain." }
        return WalletVault(
            wallets = nextWallets,
            activeAddress = if (activeAddress == address) nextWallets.first().address else activeAddress,
        )
    }

    fun renameWallet(address: String, name: String): WalletVault {
        val clean = name.trim().take(32).ifBlank { "Wallet" }
        require(wallets.any { it.address == address }) { "Wallet is not in this vault." }
        return copy(wallets = wallets.map { wallet ->
            if (wallet.address == address) wallet.copy(name = clean) else wallet
        })
    }

    fun reorderWallet(fromIndex: Int, toIndex: Int): WalletVault {
        require(fromIndex in wallets.indices) { "Wallet position is invalid." }
        require(toIndex in wallets.indices) { "Wallet position is invalid." }
        if (fromIndex == toIndex) return this
        val reordered = wallets.toMutableList()
        val wallet = reordered.removeAt(fromIndex)
        reordered.add(toIndex, wallet)
        return copy(wallets = reordered)
    }

    fun toJson(): JSONObject = JSONObject()
        .put("version", 3)
        .put("activeAddress", activeAddress)
        .put("wallets", JSONArray().apply {
            wallets.forEach { put(it.toJson()) }
        })

    companion object {
        fun single(wallet: Wallet): WalletVault = WalletVault(listOf(wallet), wallet.address)

        fun fromJson(json: JSONObject): WalletVault {
            if (!json.has("wallets")) {
                val wallet = Wallet.fromJson(json)
                return single(wallet)
            }
            val array = json.getJSONArray("wallets")
            val wallets = buildList {
                for (index in 0 until array.length()) {
                    add(Wallet.fromJson(array.getJSONObject(index)))
                }
            }
            require(wallets.isNotEmpty()) { "Wallet backup contains no wallets." }
            val active = json.optString("activeAddress").ifBlank { wallets.first().address }
            return WalletVault(
                wallets = wallets,
                activeAddress = if (wallets.any { it.address == active }) active else wallets.first().address,
            )
        }
    }
}

data class RpcStatus(
    val state: State,
    val url: String,
    val network: String,
    val latestEpoch: Long? = null,
    val message: String? = null,
) {
    enum class State {
        Checking,
        Connected,
        Failed,
    }
}

data class RpcNetwork(
    val id: String,
    val name: String,
    val url: String,
    val networkType: String,
    val explorerUrl: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("url", url)
        .put("networkType", networkType)
        .put("explorerUrl", explorerUrl)

    companion object {
        fun fromJson(json: JSONObject): RpcNetwork = RpcNetwork(
            id = json.optString("id").ifBlank { json.optString("url") },
            name = json.optString("name", "Octra"),
            url = json.optString("url").trim().trimEnd('/'),
            networkType = json.optString("networkType", json.optString("network", "mainnet")),
            explorerUrl = json.optString("explorerUrl", json.optString("explorer", "")),
        )
    }
}

data class BalanceSnapshot(
    val balance: Double,
    val nonce: Long,
)

data class TransactionHistoryItem(
    val hash: String,
    val from: String,
    val to: String,
    val amount: Double,
    val timestamp: Double,
    val status: String,
    val type: String,
    val opType: String = "standard",
    val message: String? = null,
) {
    fun isOutgoing(address: String): Boolean = from.equals(address, ignoreCase = true)
}

data class SendResult(
    val success: Boolean,
    val hash: String? = null,
    val error: String? = null,
    val finality: String? = null,
)

enum class TransactionFinality {
    Submitting,
    Accepted,
    Confirmed,
    Rejected,
    Dropped,
}

enum class TransactionNetworkStatus {
    Pending,
    Confirmed,
    Rejected,
    Dropped,
    NotFound,
}

data class TransactionNetworkState(
    val status: TransactionNetworkStatus,
    val reason: String? = null,
)

data class SubmittedTransaction(
    val hash: String?,
    val recipientAddress: String,
    val amount: Double,
    val status: TransactionFinality,
    val reason: String? = null,
    val showDialog: Boolean = true,
)

data class TokenBalance(
    val symbol: String,
    val name: String,
    val amount: String,
    val fiatEstimate: String,
    val isLoading: Boolean = false,
    val error: String? = null,
)

data class EvmNetwork(
    val id: String,
    val name: String,
    val chainId: Long,
    val rpcUrl: String,
    val symbol: String,
    val explorerUrl: String,
    val isTestnet: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("chainId", chainId)
        .put("rpcUrl", rpcUrl)
        .put("symbol", symbol)
        .put("explorerUrl", explorerUrl)
        .put("isTestnet", isTestnet)

    companion object {
        fun fromJson(json: JSONObject): EvmNetwork = EvmNetwork(
            id = json.optString("id").ifBlank { "evm-${json.optLong("chainId", 1L)}" },
            name = json.optString("name", "EVM Network"),
            chainId = json.optLong("chainId", 1L),
            rpcUrl = json.optString("rpcUrl", json.optString("rpc", "")).trim().trimEnd('/'),
            symbol = json.optString("symbol", "ETH"),
            explorerUrl = json.optString("explorerUrl", json.optString("explorer", "")).trim().trimEnd('/'),
            isTestnet = json.optBoolean("isTestnet", false),
        )
    }
}

data class EvmAccountSnapshot(
    val address: String,
    val balance: String,
    val fiatEstimate: String,
    val gasPriceGwei: String,
    val connected: Boolean,
    val ensName: String? = null,
    val message: String? = null,
)

data class EvmSendResult(
    val success: Boolean,
    val hash: String? = null,
    val error: String? = null,
    val nonce: BigInteger? = null,
    val gasPriceWei: BigInteger? = null,
    val gasLimit: BigInteger? = null,
    val toAddress: String? = null,
    val dataHex: String? = null,
    val valueWei: BigInteger = BigInteger.ZERO,
)

data class EvmGasEstimate(
    val gasLimit: BigInteger,
    val lowGasPriceWei: BigInteger,
    val marketGasPriceWei: BigInteger,
    val fastGasPriceWei: BigInteger,
    val lowFeeWei: BigInteger,
    val marketFeeWei: BigInteger,
    val fastFeeWei: BigInteger,
    val label: String,
)

data class EvmDAppTransaction(
    val from: String,
    val to: String,
    val valueWei: BigInteger = BigInteger.ZERO,
    val dataHex: String = "0x",
    val gasLimit: BigInteger? = null,
    val gasPriceWei: BigInteger? = null,
)

data class EvmDAppTransactionReview(
    val transaction: EvmDAppTransaction,
    val gasLimit: BigInteger,
    val gasPriceWei: BigInteger,
    val feeWei: BigInteger,
)

data class EvmToken(
    val address: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val balance: String,
    val chainId: Long,
    val isLoading: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("address", address)
        .put("name", name)
        .put("symbol", symbol)
        .put("decimals", decimals)
        .put("balance", balance)
        .put("chainId", chainId)

    companion object {
        fun fromJson(json: JSONObject): EvmToken = EvmToken(
            address = json.optString("address"),
            name = json.optString("name", "Unknown Token"),
            symbol = json.optString("symbol", "TOKEN"),
            decimals = json.optInt("decimals", 18),
            balance = json.optString("balance", "0"),
            chainId = json.optLong("chainId", 1L),
        )
    }
}

data class EvmNft(
    val contractAddress: String,
    val tokenId: String,
    val name: String,
    val symbol: String,
    val tokenUri: String? = null,
    val imageUrl: String? = null,
    val chainId: Long,
    val owned: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("contractAddress", contractAddress)
        .put("tokenId", tokenId)
        .put("name", name)
        .put("symbol", symbol)
        .put("chainId", chainId)
        .put("owned", owned)
        .apply {
            if (!tokenUri.isNullOrBlank()) put("tokenUri", tokenUri)
            if (!imageUrl.isNullOrBlank()) put("imageUrl", imageUrl)
        }

    companion object {
        fun fromJson(json: JSONObject): EvmNft = EvmNft(
            contractAddress = json.optString("contractAddress"),
            tokenId = json.optString("tokenId"),
            name = json.optString("name", "Unknown NFT"),
            symbol = json.optString("symbol", "NFT"),
            tokenUri = json.optString("tokenUri").ifBlank { null },
            imageUrl = json.optString("imageUrl").ifBlank { null },
            chainId = json.optLong("chainId", 1L),
            owned = json.optBoolean("owned", false),
        )
    }
}

data class EvmTransaction(
    val hash: String,
    val from: String,
    val to: String,
    val value: String,
    val symbol: String,
    val timestamp: Long,
    val status: String,
    val type: String,
    val tokenAddress: String? = null,
) {
    fun isOutgoing(address: String): Boolean = from.equals(address, ignoreCase = true)
}

data class OctraToken(
    val address: String,
    val name: String,
    val symbol: String,
    val decimals: Int,
    val balanceRaw: String,
    val totalSupply: String? = null,
) {
    val formattedBalance: String
        get() = formatRawTokenAmount(balanceRaw, decimals)

    companion object {
        fun fromJson(json: JSONObject): OctraToken = OctraToken(
            address = json.optString("address"),
            name = json.optString("name", json.optString("symbol", "Unknown")),
            symbol = json.optString("symbol", "???"),
            decimals = when (val value = json.opt("decimals")) {
                is Number -> value.toInt()
                is String -> value.toIntOrNull() ?: 0
                else -> 0
            },
            balanceRaw = json.optString("balance", "0"),
            totalSupply = json.optString("total_supply").ifBlank { null },
        )
    }
}

fun formatRawTokenAmount(raw: String, decimals: Int): String {
    val amount = raw.toBigIntegerOrNull() ?: return "0"
    if (amount == java.math.BigInteger.ZERO) return "0"
    if (decimals <= 0) return amount.toString()
    val divisor = java.math.BigInteger.TEN.pow(decimals)
    val whole = amount.divide(divisor)
    val fraction = amount.mod(divisor).toString().padStart(decimals, '0').trimEnd('0')
    return if (fraction.isBlank()) whole.toString() else "$whole.$fraction"
}

data class DAppSession(
    val name: String,
    val url: String,
    val walletAddress: String,
    val permissions: List<String>,
    val network: String,
    val connectedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("appOrigin", url)
        .put("walletAddress", walletAddress)
        .put("network", network)
        .put("connectedAt", connectedAt)
        .put("permissions", JSONArray(permissions))

    companion object {
        fun fromJson(json: JSONObject): DAppSession {
            val origin = canonicalizeOrigin(json.optString("appOrigin", json.optString("url", "")))
            return DAppSession(
                name = hostLabel(origin),
                url = origin,
                walletAddress = json.optString("walletAddress"),
                permissions = json.optJSONArray("permissions").toStringList(),
                network = json.optString("network", "mainnet"),
                connectedAt = json.optLong("connectedAt", 0L),
            )
        }
    }
}

data class AddressBookContact(
    val id: String,
    val label: String,
    val address: String,
    val note: String? = null,
    val identityName: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("label", label)
        .put("address", address)
        .put("createdAt", createdAt)
        .apply {
            if (!note.isNullOrBlank()) put("note", note)
            if (!identityName.isNullOrBlank()) put("identityName", identityName)
        }

    companion object {
        fun fromJson(json: JSONObject): AddressBookContact = AddressBookContact(
            id = json.optString("id").ifBlank { "contact-${json.optString("address").hashCode()}" },
            label = json.optString("label", "Contact"),
            address = json.optString("address").trim(),
            note = json.optString("note").ifBlank { null },
            identityName = json.optString("identityName", json.optString("name", "")).ifBlank { null },
            createdAt = json.optLong("createdAt", 0L),
        )
    }
}

data class OctraIdentityProvider(
    val id: String,
    val name: String,
    val contractAddress: String,
    val network: String,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("name", name)
        .put("contractAddress", contractAddress)
        .put("network", network)
        .put("type", "contract")

    companion object {
        fun fromJson(json: JSONObject): OctraIdentityProvider = OctraIdentityProvider(
            id = json.optString("id").ifBlank { "identity-${json.optString("contractAddress").hashCode()}" },
            name = json.optString("name", "Octra Identity"),
            contractAddress = json.optString("contractAddress", json.optString("address", "")).trim(),
            network = json.optString("network", "devnet"),
        )
    }
}

fun JSONArray?.toStringList(): List<String> {
    if (this == null) return emptyList()
    return buildList {
        for (index in 0 until length()) {
            val value = optString(index)
            if (value.isNotBlank()) add(value)
        }
    }
}

fun canonicalizeOrigin(value: String): String {
    val trimmed = value.trim().trimEnd('/')
    if (trimmed.isBlank()) return ""
    return try {
        URI(trimmed).let { uri ->
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            if (scheme != null && host != null) {
                val defaultPort = (scheme == "http" && uri.port == 80) || (scheme == "https" && uri.port == 443)
                val port = if (uri.port > 0 && !defaultPort) ":${uri.port}" else ""
                "$scheme://$host$port"
            } else {
                trimmed.lowercase()
            }
        }
    } catch (_: Exception) {
        trimmed.lowercase()
    }
}

fun hostLabel(origin: String): String {
    return try {
        URI(origin).host ?: origin
    } catch (_: Exception) {
        origin.ifBlank { "Unknown dApp" }
    }
}
