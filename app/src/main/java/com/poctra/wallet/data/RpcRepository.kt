package com.poctra.wallet.data

import android.content.Context
import android.util.Base64
import com.poctra.wallet.privacy.PrivacyEncryptedBalance
import com.poctra.wallet.core.Base58
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator
import org.bouncycastle.crypto.params.KeyParameter
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.ByteArrayOutputStream
import java.math.BigDecimal
import java.math.RoundingMode
import java.io.OutputStreamWriter
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.Collections
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class CircleAsset(
    val circleId: String,
    val canonicalPath: String,
    val contentType: String,
    val body: ByteArray,
    val sizeBytes: Long,
)

private data class CircleReadCacheEntry(
    val expiresAtMs: Long,
    val value: Any?,
)

private data class CircleRpcBatchCall(
    val index: Int,
    val request: JSONObject,
    val method: String,
    val params: JSONArray,
    val cacheKey: String,
)

private data class TokenLookupResult(
    val name: String?,
    val symbol: String?,
    val decimals: String?,
    val balance: String?,
    val supply: String?,
)

class RpcRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("poctra_rpc", Context.MODE_PRIVATE)
    private val unsupportedCircleProgramViewUrls = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val circleReadCache = ConcurrentHashMap<String, CircleReadCacheEntry>()
    private val circleReadLocks = ConcurrentHashMap<String, Any>()
    private val registeredPvacKeys = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())

    fun currentUrl(): String = activeNetwork().url

    fun saveUrl(url: String) {
        require(isValidRpcUrl(url)) { "Enter an HTTPS RPC URL or an approved devnet HTTP endpoint." }
        val network = activeNetwork().copy(url = url.trim().trimEnd('/'))
        saveNetwork(network)
        setActiveNetwork(network.id)
        prefs.edit().putString(KeyRpcUrl, network.url).apply()
    }

    fun rpcNetworks(): List<RpcNetwork> {
        val stored = prefs.getString(KeyRpcNetworks, null)
        val parsed = runCatching {
            val array = JSONArray(stored ?: "[]")
            buildList {
                for (index in 0 until array.length()) {
                    val network = RpcNetwork.fromJson(array.getJSONObject(index))
                    if (network.url.isNotBlank()) add(network)
                }
            }
        }.getOrDefault(emptyList())
        val seeded = prefs.getBoolean(KeyDefaultRpcSeeded, false)
        val normalized = parsed.map(::normalizeDefaultNetwork)
        val networks = if (normalized.isEmpty()) {
            mergeNetworks(defaultRpcNetworks() + defaultNetworkFromLegacy().takeIf { legacy ->
                defaultRpcNetworks().none { it.url == legacy.url }
            })
        } else if (!seeded) {
            mergeNetworks(defaultRpcNetworks() + normalized)
        } else {
            normalized
        }

        if (normalized.isEmpty() || !seeded) {
            val legacy = defaultNetworkFromLegacy()
            val activeId = prefs.getString(KeyActiveRpcId, null)
                ?: networks.firstOrNull { it.id == DefaultMainnetId }?.id
                ?: networks.firstOrNull { it.url == legacy.url }?.id
                ?: networks.firstOrNull()?.id
            prefs.edit()
                .putString(KeyActiveRpcId, activeId)
                .putBoolean(KeyDefaultRpcSeeded, true)
                .apply()
            persistNetworks(networks)
        }
        ensureMainnetDefault(networks)
        return networks
    }

    fun activeNetwork(): RpcNetwork {
        val networks = rpcNetworks()
        val activeId = prefs.getString(KeyActiveRpcId, null)
        return networks.firstOrNull { it.id == activeId }
            ?: networks.firstOrNull { it.url == prefs.getString(KeyRpcUrl, null)?.trim()?.trimEnd('/') }
            ?: networks.firstOrNull { it.id == DefaultMainnetId }
            ?: networks.first()
    }

    fun saveNetwork(network: RpcNetwork): List<RpcNetwork> {
        require(network.name.isNotBlank()) { "RPC name is required." }
        require(isValidRpcUrl(network.url)) { "Enter an HTTPS RPC URL or an approved devnet HTTP endpoint." }
        require(network.networkType == "mainnet" || network.networkType == "devnet") { "Network type must be mainnet or devnet." }
        if (network.explorerUrl.isNotBlank()) {
            require(isValidRpcUrl(network.explorerUrl)) { "Explorer URL must be HTTPS or an approved devnet HTTP endpoint." }
        }
        val clean = network.copy(
            id = network.id.ifBlank { "rpc-${System.currentTimeMillis()}" },
            name = network.name.trim().take(32),
            url = network.url.trim().trimEnd('/'),
            networkType = network.networkType,
            explorerUrl = network.explorerUrl.trim().trimEnd('/'),
        )
        val next = rpcNetworks().filterNot { it.id == clean.id || it.url == clean.url } + clean
        persistNetworks(next)
        return next
    }

    fun setActiveNetwork(id: String): RpcNetwork {
        val network = rpcNetworks().firstOrNull { it.id == id } ?: throw IllegalArgumentException("RPC network not found.")
        prefs.edit()
            .putString(KeyActiveRpcId, network.id)
            .putString(KeyRpcUrl, network.url)
            .apply()
        return network
    }

    fun deleteNetwork(id: String): List<RpcNetwork> {
        val current = rpcNetworks()
        require(current.size > 1) { "At least one RPC network must remain." }
        val next = current.filterNot { it.id == id }
        require(next.isNotEmpty()) { "At least one RPC network must remain." }
        persistNetworks(next)
        if (activeNetwork().id == id) setActiveNetwork(next.first().id)
        return next
    }

    fun isValidRpcUrl(url: String): Boolean {
        return try {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            scheme == "https" && !host.isNullOrBlank() ||
                scheme == "http" && isApprovedCleartextHost(host)
        } catch (_: Exception) {
            false
        }
    }

    private fun isApprovedCleartextHost(host: String?): Boolean {
        return host in setOf("165.227.225.79", "localhost", "127.0.0.1", "10.0.2.2")
    }

    suspend fun checkStatus(url: String = currentUrl()): RpcStatus = withContext(Dispatchers.IO) {
        try {
            val json = postRpc(
                url = url,
                payload = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 1)
                    .put("method", "node_stats")
                    .put("params", JSONArray()),
            )
            if (json.has("error") || !json.has("result")) {
                return@withContext RpcStatus(RpcStatus.State.Failed, url, networkFor(url), message = "node_stats returned an error.")
            }
            val epochs = json.getJSONObject("result").optJSONArray("latest_epochs")
            RpcStatus(
                state = RpcStatus.State.Connected,
                url = url,
                network = networkFor(url),
                latestEpoch = epochs?.optLong(0),
            )
        } catch (error: Exception) {
            RpcStatus(RpcStatus.State.Failed, url, networkFor(url), message = error.message ?: "RPC check failed.")
        }
    }

    suspend fun fetchBalance(address: String, url: String = currentUrl()): BalanceSnapshot = withContext(Dispatchers.IO) {
        try {
            val json = postRpc(
                url = url,
                payload = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 1)
                    .put("method", "octra_balance")
                    .put("params", JSONArray().put(address)),
            )
            val result = json.optJSONObject("result") ?: return@withContext BalanceSnapshot(0.0, 0)
            val raw = when (val balanceRaw = result.opt("balance_raw")) {
                is Number -> balanceRaw.toLong()
                is String -> balanceRaw.toLongOrNull() ?: 0L
                else -> 0L
            }
            val balance = if (raw > 0) {
                raw / MicroUnits.toDouble()
            } else {
                result.optDouble("balance", 0.0)
            }
            val nonce = result.optLong("pending_nonce", result.optLong("nonce", 0L))
            BalanceSnapshot(balance, nonce)
        } catch (_: Exception) {
            BalanceSnapshot(0.0, 0)
        }
    }

    suspend fun sendOct(
        wallet: Wallet,
        recipientAddress: String,
        amount: Double,
        message: String?,
        feeOu: Long,
    ): SendResult = withContext(Dispatchers.IO) {
        val recipient = recipientAddress.trim()
        require(recipient.startsWith("oct") && recipient.length == 47) {
            "Enter a valid Octra recipient address."
        }
        require(!recipient.equals(wallet.address, ignoreCase = true)) {
            "Cannot send OCT to the active wallet address."
        }
        require(amount > 0.0) {
            "Amount must be greater than zero."
        }

        val snapshot = fetchBalance(wallet.address)
        val maximumAmount = snapshot.balance - (feeOu.coerceAtLeast(1L) / MicroUnits.toDouble())
        require(amount <= maximumAmount + 0.000000001) {
            "Amount exceeds the available balance after network fee."
        }
        val tx = createSignedTransaction(
            wallet = wallet,
            recipientAddress = recipient,
            amount = amount,
            nonce = snapshot.nonce + 1,
            message = message?.trim()?.ifBlank { null },
            feeOu = feeOu,
        )

        val response = postRpc(
            url = currentUrl(),
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", System.currentTimeMillis())
                .put("method", "octra_submit")
                .put("params", JSONArray().put(tx)),
        )

        val rpcError = response.optJSONObject("error")
        if (rpcError != null) {
            return@withContext SendResult(
                success = false,
                error = rpcError.optString("message", rpcError.optString("reason", "Transaction failed")),
                finality = "rejected",
            )
        }

        val result = response.optJSONObject("result") ?: JSONObject()
        val status = result.optString("status").lowercase()
        val hash = result.optString("tx_hash", result.optString("hash")).ifBlank { null }
        when {
            status == "accepted" || hash != null -> SendResult(success = true, hash = hash, finality = "accepted")
            status == "rejected" -> SendResult(
                success = false,
                error = result.optString("reason", "Transaction rejected"),
                finality = "rejected",
            )
            else -> SendResult(success = false, error = "Unknown transaction response.")
        }
    }

    suspend fun sendContractCall(
        wallet: Wallet,
        contractAddress: String,
        method: String,
        params: JSONArray,
        amountOu: String,
        feeOu: Long,
        rpcUrl: String = currentUrl(),
    ): SendResult = withContext(Dispatchers.IO) {
        val contract = contractAddress.trim()
        require(contract.startsWith("oct") && contract.length == 47) {
            "Enter a valid Octra contract address."
        }
        require(method.isNotBlank()) {
            "Contract method is required."
        }
        val amount = amountOu.trim().ifBlank { "0" }
        require(amount.all(Char::isDigit)) {
            "Contract amount must be in ou."
        }

        val snapshot = fetchBalance(wallet.address, rpcUrl)
        val tx = createSignedTransactionRaw(
            wallet = wallet,
            recipientAddress = contract,
            amountRaw = amount,
            nonce = snapshot.nonce + 1,
            message = params.toString(),
            feeOu = feeOu,
            opType = "call",
            encryptedData = method,
        )

        val response = postRpc(
            url = rpcUrl,
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", System.currentTimeMillis())
                .put("method", "octra_submit")
                .put("params", JSONArray().put(tx)),
        )

        val rpcError = response.optJSONObject("error")
        if (rpcError != null) {
            return@withContext SendResult(
                success = false,
                error = rpcError.optString("message", rpcError.optString("reason", "Contract call failed")),
                finality = "rejected",
            )
        }

        val result = response.optJSONObject("result") ?: JSONObject()
        val status = result.optString("status").lowercase()
        val hash = result.optString("tx_hash", result.optString("hash")).ifBlank { null }
        when {
            status == "accepted" || hash != null -> SendResult(success = true, hash = hash, finality = "accepted")
            status == "rejected" -> SendResult(
                success = false,
                error = result.optString("reason", "Contract call rejected"),
                finality = "rejected",
            )
            else -> SendResult(success = false, error = "Unknown contract call response.")
        }
    }

    suspend fun sendOctraToken(
        wallet: Wallet,
        token: OctraToken,
        recipientAddress: String,
        rawAmount: String,
        feeOu: Long,
    ): SendResult = sendContractCall(
        wallet = wallet,
        contractAddress = token.address,
        method = "transfer",
        params = JSONArray()
            .put(recipientAddress.trim())
            .put(rawAmount.toLongOrNull() ?: rawAmount),
        amountOu = "0",
        feeOu = feeOu,
    )

    suspend fun checkTransactionStatus(
        hash: String,
        url: String = currentUrl(),
    ): TransactionNetworkState = withContext(Dispatchers.IO) {
        runCatching {
            val json = postRpc(
                url = url,
                payload = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 1)
                    .put("method", "octra_transaction")
                    .put("params", JSONArray().put(hash)),
            )
            if (json.has("error")) return@runCatching TransactionNetworkState(TransactionNetworkStatus.NotFound)
            val data = json.optJSONObject("result") ?: return@runCatching TransactionNetworkState(TransactionNetworkStatus.NotFound)
            val status = data.optString("status").lowercase()
            val stageStatus = data.optString("stage_status").lowercase()
            val rejectionReason = data.optString("reason").ifBlank {
                data.optJSONObject("error")?.optString("reason").orEmpty()
            }.ifBlank {
                data.optJSONObject("error")?.optString("message").orEmpty()
            }.ifBlank { null }
            when {
                status == "rejected" -> TransactionNetworkState(
                    TransactionNetworkStatus.Rejected,
                    rejectionReason,
                )
                status == "dropped" -> TransactionNetworkState(
                    TransactionNetworkStatus.Dropped,
                    rejectionReason,
                )
                (data.has("epoch") && !data.isNull("epoch")) ||
                    status == "confirmed" ||
                    stageStatus == "confirmed" -> TransactionNetworkState(TransactionNetworkStatus.Confirmed)
                data.has("stage_status") ||
                    status == "pending" ||
                    status == "accepted" -> TransactionNetworkState(TransactionNetworkStatus.Pending)
                else -> TransactionNetworkState(TransactionNetworkStatus.NotFound)
            }
        }.getOrDefault(TransactionNetworkState(TransactionNetworkStatus.NotFound))
    }

    suspend fun fetchEncryptedBalance(
        wallet: Wallet,
        url: String = currentUrl(),
    ): PrivacyEncryptedBalance? = withContext(Dispatchers.IO) {
        val signature = signMessage(wallet, "octra_encryptedBalance|${wallet.address}")
        val publicKey = Base64.encodeToString(signingMaterial(wallet).second, Base64.NO_WRAP)
        val response = postRpc(
            url = url,
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", System.currentTimeMillis())
                .put("method", "octra_encryptedBalance")
                .put("params", JSONArray().put(wallet.address).put(signature).put(publicKey)),
        )
        if (response.has("error")) return@withContext null
        val result = response.opt("result")
        val cipher = when (result) {
            is JSONObject -> result.optString("cipher")
            is String -> result
            else -> ""
        }.trim()
        normalizeEncryptedBalanceCipher(cipher)?.let(::PrivacyEncryptedBalance)
    }

    suspend fun fetchPrivacyHistory(
        address: String,
        limit: Int = 7,
        url: String = currentUrl(),
    ): List<TransactionHistoryItem> = withContext(Dispatchers.IO) {
        val response = postRpc(
            url = url,
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", System.currentTimeMillis())
                .put("method", "octra_account")
                .put("params", JSONArray().put(address).put(PrivacyHistoryQueryLimit)),
        )
        val recent = response.optJSONObject("result")?.optJSONArray("recent_txs") ?: JSONArray()
        buildList {
            for (index in 0 until recent.length()) {
                val item = recent.optJSONObject(index) ?: continue
                val opType = item.optString("op_type").lowercase()
                if (opType !in PrivacyOperationTypes) continue
                val from = item.optString("from", address)
                val to = item.optString("to", item.optString("to_", address))
                add(
                    TransactionHistoryItem(
                        hash = item.optString("hash", item.optString("tx_hash")),
                        from = from,
                        to = to,
                        amount = parseAmount(item.opt("amount_raw"), item.opt("amount")),
                        timestamp = item.optDouble("timestamp", 0.0),
                        status = item.optString("status", "confirmed"),
                        type = if (from.equals(address, ignoreCase = true)) "sent" else "received",
                        opType = opType,
                    ),
                )
                if (size >= limit) break
            }
        }.sortedByDescending { it.timestamp }
    }

    suspend fun fetchStealthOutputs(url: String = currentUrl()): JSONArray = withContext(Dispatchers.IO) {
        val response = postRpc(
            url = url,
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", System.currentTimeMillis())
                .put("method", "octra_stealthOutputs")
                .put("params", JSONArray().put(0)),
            connectTimeoutMs = 20_000,
            readTimeoutMs = 120_000,
        )
        response.optJSONObject("error")?.let { error ->
            throw IllegalStateException(error.optString("message", "Unable to scan claimable transfers."))
        }
        response.optJSONObject("result")?.optJSONArray("outputs") ?: JSONArray()
    }

    suspend fun fetchOctraPublicKey(
        address: String,
        url: String = currentUrl(),
    ): ByteArray = withContext(Dispatchers.IO) {
        require(address.startsWith("oct") && address.length == 47) { "Recipient address is invalid." }
        val response = postRpc(
            url = url,
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", System.currentTimeMillis())
                .put("method", "octra_publicKey")
                .put("params", JSONArray().put(address)),
        )
        response.optJSONObject("error")?.let { error ->
            throw IllegalStateException(error.optString("message", "Recipient public key is unavailable."))
        }
        val encoded = response.optJSONObject("result")?.optString("public_key").orEmpty()
        require(encoded.isNotBlank()) {
            "Recipient has no public key registered on the active network."
        }
        val publicKey = runCatching { Base64.decode(encoded, Base64.DEFAULT) }
            .getOrElse { throw IllegalStateException("Recipient public key is invalid.") }
        require(publicKey.size == 32) { "Recipient public key has an invalid size." }
        val derivedAddress = "oct" + Base58.encode(
            MessageDigest.getInstance("SHA-256").digest(publicKey),
        ).padStart(44, '1')
        require(derivedAddress == address) {
            "Recipient public key does not match the requested address."
        }
        publicKey
    }

    suspend fun ensurePvacRegistered(
        wallet: Wallet,
        pvacPublicKey: ByteArray,
        aesKat: ByteArray,
        url: String = currentUrl(),
    ) = withContext(Dispatchers.IO) {
        val pvacPublicKeyBase64 = Base64.encodeToString(pvacPublicKey, Base64.NO_WRAP)
        val publicKeyHash = MessageDigest.getInstance("SHA-256").digest(pvacPublicKey).toHex()
        val cacheKey = "${url.trimEnd('/')}|${wallet.address}|$publicKeyHash"
        if (cacheKey in registeredPvacKeys) return@withContext

        val existingResponse = postRpc(
            url = url,
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", System.currentTimeMillis())
                .put("method", "octra_pvacPubkey")
                .put("params", JSONArray().put(wallet.address)),
        )
        val existing = existingResponse.optJSONObject("result")?.optString("pvac_pubkey")
            ?.trim()
            .orEmpty()
        if (existing == pvacPublicKeyBase64) {
            registeredPvacKeys += cacheKey
            return@withContext
        }
        require(existing.isBlank()) {
            "This wallet is registered with a different Privacy key on the active network."
        }

        val registrationMessage = "register_pvac|${wallet.address}|$publicKeyHash"
        val response = postRpc(
            url = url,
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", System.currentTimeMillis())
                .put("method", "octra_registerPvacPubkey")
                .put(
                    "params",
                    JSONArray()
                        .put(wallet.address)
                        .put(pvacPublicKeyBase64)
                        .put(signMessage(wallet, registrationMessage))
                        .put(Base64.encodeToString(signingMaterial(wallet).second, Base64.NO_WRAP))
                        .put(aesKat.toHex()),
                ),
            connectTimeoutMs = 60_000,
            readTimeoutMs = 60_000,
        )
        val errorMessage = response.optJSONObject("error")?.optString("message").orEmpty()
        if (errorMessage.isNotBlank() && !errorMessage.contains("already registered", ignoreCase = true)) {
            throw IllegalStateException(errorMessage)
        }
        registeredPvacKeys += cacheKey
    }

    suspend fun submitPrivacyTransaction(
        wallet: Wallet,
        amountRaw: String,
        feeOu: Long,
        opType: String,
        encryptedData: String,
        recipientAddress: String = wallet.address,
        url: String = currentUrl(),
    ): SendResult = withContext(Dispatchers.IO) {
        require(opType in PrivacyOperationTypes) { "Unsupported privacy operation." }
        require(
            (opType == "stealth" && recipientAddress == "stealth") ||
                (opType != "stealth" && recipientAddress == wallet.address),
        ) { "Privacy transaction recipient is invalid." }
        val snapshot = fetchBalanceStrict(wallet.address, url)
        val transaction = createSignedTransactionRaw(
            wallet = wallet,
            recipientAddress = recipientAddress,
            amountRaw = amountRaw,
            nonce = snapshot.nonce + 1,
            message = null,
            feeOu = feeOu,
            opType = opType,
            encryptedData = encryptedData,
        )
        val response = postRpc(
            url = url,
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", System.currentTimeMillis())
                .put("method", "octra_submit")
                .put("params", JSONArray().put(transaction)),
            connectTimeoutMs = 20_000,
            readTimeoutMs = 45_000,
        )
        response.optJSONObject("error")?.let { rpcError ->
            return@withContext SendResult(
                success = false,
                error = rpcError.optString("message", rpcError.optString("reason", "Privacy transaction failed")),
                finality = "rejected",
            )
        }
        val result = response.optJSONObject("result") ?: JSONObject()
        val status = result.optString("status").lowercase()
        val hash = result.optString("tx_hash", result.optString("hash")).ifBlank { null }
        if (status == "rejected") {
            SendResult(false, error = result.optString("reason", "Privacy transaction rejected"), finality = status)
        } else if (status == "accepted" || hash != null) {
            SendResult(true, hash = hash, finality = "accepted")
        } else {
            SendResult(false, error = "Unknown privacy transaction response.")
        }
    }

    suspend fun fetchTransactionHistory(address: String, limit: Int = 11, url: String = currentUrl()): List<TransactionHistoryItem> =
        withContext(Dispatchers.IO) {
            val confirmedJob = async {
                runCatching {
                    val json = postRpc(
                        url = url,
                        payload = JSONObject()
                            .put("jsonrpc", "2.0")
                            .put("id", 1)
                            .put("method", "octra_account")
                            .put("params", JSONArray().put(address).put(limit)),
                    )
                    val result = json.optJSONObject("result") ?: return@runCatching emptyList()
                    val recent = result.optJSONArray("recent_txs") ?: JSONArray()
                    val items = buildList {
                        for (index in 0 until recent.length()) {
                            recent.optJSONObject(index)?.let { add(it) }
                        }
                    }
                    items.chunked(HistoryDetailsBatchSize).flatMap { batch ->
                        coroutineScope {
                            batch.map { item ->
                                async {
                                    val hash = item.optString("hash", item.optString("tx_hash"))
                                    if (hash.isBlank()) {
                                        null
                                    } else {
                                        runCatching { fetchTransactionDetails(hash, address, url) }.getOrElse {
                                            TransactionHistoryItem(
                                                hash = hash,
                                                from = item.optString("from", "unknown"),
                                                to = item.optString("to", item.optString("to_", "unknown")),
                                                amount = parseAmount(item.opt("amount_raw"), item.opt("amount")),
                                                timestamp = item.optDouble("timestamp", 0.0),
                                                status = "confirmed",
                                                type = if (item.optString("from").equals(address, ignoreCase = true)) "sent" else "received",
                                                opType = item.optString("op_type", "standard"),
                                            )
                                        }
                                    }
                                }
                            }.awaitAll().filterNotNull()
                        }
                    }
                }.getOrDefault(emptyList())
            }

            val pendingJob = async { runCatching { fetchPendingTransactions(address, url) }.getOrDefault(emptyList()) }
            val confirmed = confirmedJob.await()
            val pending = pendingJob.await()
            (confirmed + pending)
                .distinctBy { it.hash }
                .sortedByDescending { it.timestamp }
                .take(limit)
        }

    suspend fun fetchRecommendedFee(opType: String = "standard", url: String = currentUrl()): Long = withContext(Dispatchers.IO) {
        runCatching {
            val json = postRpc(
                url = url,
                payload = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 1)
                    .put("method", "octra_recommendedFee")
                    .put("params", JSONArray().put(opType)),
            )
            val recommended = json.optJSONObject("result")?.opt("recommended")
            when (recommended) {
                is Number -> recommended.toLong()
                is String -> recommended.toLongOrNull()
                else -> null
            }?.takeIf { it > 0L } ?: feeFallback(opType)
        }.getOrDefault(feeFallback(opType))
    }

    suspend fun fetchOctraTokens(walletAddress: String, savedAddresses: List<String>, url: String = currentUrl()): List<OctraToken> =
        withContext(Dispatchers.IO) {
            val fetched = runCatching {
                val json = postRpc(
                    url = url,
                    payload = JSONObject()
                        .put("jsonrpc", "2.0")
                        .put("id", 1)
                        .put("method", "octra_tokensByAddress")
                        .put("params", JSONArray().put(walletAddress)),
                )
                val tokens = json.optJSONObject("result")?.optJSONArray("tokens") ?: JSONArray()
                buildList {
                    for (index in 0 until tokens.length()) {
                        add(OctraToken.fromJson(tokens.getJSONObject(index)))
                    }
                }
            }.getOrDefault(emptyList())

            val known = fetched.map { it.address }.toSet()
            val missing = savedAddresses.filterNot { it in known }
            val lookedUp = missing.chunked(OctraTokenLookupBatchSize).flatMap { batch ->
                coroutineScope {
                    batch.map { address ->
                        async { lookupOctraToken(address, walletAddress, url) }
                    }.awaitAll().filterNotNull()
                }
            }
            fetched + lookedUp
        }

    suspend fun lookupOctraToken(contractAddress: String, walletAddress: String, url: String = currentUrl()): OctraToken? =
        withContext(Dispatchers.IO) {
            if (!contractAddress.startsWith("oct") || contractAddress.length != 47) return@withContext null

            val (name, symbol, decimals, balance, supply) = coroutineScope {
                val nameJob = async { contractCall(contractAddress, "get_name", JSONArray(), walletAddress, url) }
                val symbolJob = async { contractCall(contractAddress, "get_symbol", JSONArray(), walletAddress, url) }
                val decimalsJob = async { contractCall(contractAddress, "decimals", JSONArray(), walletAddress, url) }
                val balanceJob = async {
                    contractCall(
                        contractAddress = contractAddress,
                        method = "balance_of",
                        params = JSONArray().put(walletAddress),
                        walletAddress = walletAddress,
                        url = url,
                    )
                }
                val supplyJob = async { contractCall(contractAddress, "get_total_supply", JSONArray(), walletAddress, url) }
                TokenLookupResult(
                    name = nameJob.await(),
                    symbol = symbolJob.await(),
                    decimals = decimalsJob.await(),
                    balance = balanceJob.await(),
                    supply = supplyJob.await(),
                )
            }

            if (name.isNullOrBlank() && symbol.isNullOrBlank()) return@withContext null

            OctraToken(
                address = contractAddress,
                name = name ?: symbol ?: "Unknown",
                symbol = symbol ?: "???",
                decimals = decimals?.toIntOrNull() ?: 0,
                balanceRaw = balance ?: "0",
                totalSupply = supply,
            )
        }

    suspend fun resolveIdentityName(
        provider: OctraIdentityProvider,
        label: String,
        walletAddress: String,
    ): String? = withContext(Dispatchers.IO) {
        val cleanLabel = label.trim()
            .lowercase()
            .removeSuffix(".oct")
            .removeSuffix(".octra")
            .removeSuffix(".id")
        if (!isValidIdentityPath(cleanLabel)) return@withContext null
        val method = if (cleanLabel.contains('.')) "resolve_name" else "resolve"
        val resolved = contractCall(
            contractAddress = provider.contractAddress,
            method = method,
            params = JSONArray().put(cleanLabel),
            walletAddress = walletAddress,
        ) ?: if (!cleanLabel.contains('.')) {
            null
        } else {
            contractCall(
                contractAddress = provider.contractAddress,
                method = "resolve",
                params = JSONArray().put(cleanLabel),
                walletAddress = walletAddress,
            )
        }
        resolved?.takeIf { it.startsWith("oct") && it.length == 47 }
    }

    suspend fun walletIdentityName(
        provider: OctraIdentityProvider,
        walletAddress: String,
    ): String? = withContext(Dispatchers.IO) {
        if (!walletAddress.startsWith("oct") || walletAddress.length != 47) return@withContext null
        val primary = contractCall(
            contractAddress = provider.contractAddress,
            method = "primary_of",
            params = JSONArray().put(walletAddress),
            walletAddress = walletAddress,
        )?.toIdentityDomain()
        if (primary != null) return@withContext primary

        val total = contractCall(
            contractAddress = provider.contractAddress,
            method = "owner_total",
            params = JSONArray().put(walletAddress),
            walletAddress = walletAddress,
        )?.toIntOrNull() ?: 0
        if (total <= 0) return@withContext null

        contractCall(
            contractAddress = provider.contractAddress,
            method = "owner_key_at",
            params = JSONArray().put(walletAddress).put(0),
            walletAddress = walletAddress,
        )?.toIdentityDomain()
    }

    fun fetchCircleInfo(circleId: String): JSONObject {
        val cleanCircleId = circleId.trim()
        require(cleanCircleId.startsWith("oct") && cleanCircleId.length == 47) { "Enter a valid Octra circle address." }
        val json = postRpc(
            url = currentUrl(),
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", "circle_info")
                .put("params", JSONArray().put(cleanCircleId)),
        )
        json.optJSONObject("error")?.let { error ->
            throw IllegalStateException(error.optString("message").ifBlank { "Circle info lookup failed." })
        }
        return json.optJSONObject("result") ?: throw IllegalStateException("Circle info was not found.")
    }

    fun circleProgramView(address: String, method: String, params: JSONArray): Any? {
        val cleanAddress = address.trim()
        val cleanMethod = method.trim()
        require(cleanAddress.startsWith("oct") && cleanAddress.length == 47) { "Enter a valid Octra address." }
        require(cleanMethod.matches(Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$"))) { "Enter a valid view method." }
        require(params.length() <= MaxCircleRpcParams) { "Circle view params are too large." }

        val rpcUrl = currentUrl()
        return cachedCircleRead(circleProgramViewCacheKey(rpcUrl, cleanAddress, cleanMethod, params)) {
            executeCircleProgramView(rpcUrl, cleanAddress, cleanMethod, params)
        }
    }

    fun circleReadRpc(method: String, params: JSONArray): Any? {
        val cleanMethod = validateCircleReadRpc(method, params)
        val rpcUrl = currentUrl()
        return cachedCircleRead(circleReadRpcCacheKey(rpcUrl, cleanMethod, params)) {
            executeCircleReadRpc(rpcUrl, cleanMethod, params)
        }
    }

    fun circleReadRpcBatch(requests: JSONArray): JSONArray {
        val rpcUrl = currentUrl()
        require(requests.length() <= MaxCircleRpcBatchSize) { "Circle RPC batch is too large." }
        val responses = arrayOfNulls<JSONObject>(requests.length())
        val misses = mutableListOf<CircleRpcBatchCall>()
        val firstMissIndexByKey = mutableMapOf<String, Int>()
        val duplicateMisses = mutableListOf<Pair<Int, Int>>()

        for (index in 0 until requests.length()) {
            val request = requests.optJSONObject(index)
            if (request == null) {
                responses[index] = circleRpcError(JSONObject.NULL, -32600, "Invalid JSON-RPC request.")
                continue
            }
            try {
                val method = validateCircleReadRpc(request.optString("method"), request.optJSONArray("params") ?: JSONArray())
                val params = request.optJSONArray("params") ?: JSONArray()
                val cacheKey = circleReadRpcCacheKey(rpcUrl, method, params)
                val cached = cachedCircleReadValue(cacheKey)
                if (cached != null) {
                    responses[index] = circleRpcSuccess(request, cached.value)
                    continue
                }
                val duplicateIndex = cacheKey?.let { firstMissIndexByKey[it] }
                if (duplicateIndex != null) {
                    duplicateMisses += index to duplicateIndex
                    continue
                }
                if (cacheKey != null) firstMissIndexByKey[cacheKey] = index
                misses += CircleRpcBatchCall(index, request, method, params, cacheKey.orEmpty())
            } catch (exception: Exception) {
                responses[index] = circleRpcError(request, -32602, exception.message ?: "Invalid circle RPC request.")
            }
        }

        if (misses.isNotEmpty()) {
            executeCircleReadBatch(rpcUrl, misses).forEach { (index, response) ->
                responses[index] = response
            }
        }
        duplicateMisses.forEach { (duplicateIndex, sourceIndex) ->
            responses[duplicateIndex] = responses[sourceIndex]?.let { response ->
                if (response.has("error")) {
                    circleRpcError(
                        requests.optJSONObject(duplicateIndex) ?: JSONObject.NULL,
                        response.optJSONObject("error")?.optInt("code", -32000) ?: -32000,
                        response.optJSONObject("error")?.optString("message", "Circle RPC failed.") ?: "Circle RPC failed.",
                    )
                } else {
                    circleRpcSuccess(
                        requests.optJSONObject(duplicateIndex) ?: JSONObject(),
                        response.opt("result").takeUnless { it == null } ?: JSONObject.NULL,
                    )
                }
            }
        }

        return JSONArray().also { output ->
            responses.forEachIndexed { index, response ->
                output.put(response ?: circleRpcError(requests.optJSONObject(index) ?: JSONObject.NULL, -32000, "Circle RPC failed."))
            }
        }
    }

    private fun executeCircleProgramView(
        rpcUrl: String,
        cleanAddress: String,
        cleanMethod: String,
        params: JSONArray,
    ): Any? {
        if (!unsupportedCircleProgramViewUrls.contains(rpcUrl)) {
            val direct = postRpc(
                url = rpcUrl,
                payload = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 1)
                    .put("method", "octra_callProgramView")
                    .put(
                        "params",
                        JSONArray().put(
                            JSONObject()
                                .put("address", cleanAddress)
                                .put("method", cleanMethod)
                                .put("params", params),
                        ),
                    ),
            )
            val directError = direct.optJSONObject("error")
            if (directError == null) return direct.opt("result")
            val directMessage = directError.optString("message")
            if (!isMethodNotFound(directMessage)) {
                throw IllegalStateException(directMessage.ifBlank { "Circle program view failed." })
            }
            unsupportedCircleProgramViewUrls.add(rpcUrl)
        }

        return executeContractCallView(rpcUrl, cleanAddress, cleanMethod, params)
    }

    private fun executeContractCallView(
        rpcUrl: String,
        cleanAddress: String,
        cleanMethod: String,
        params: JSONArray,
    ): Any? {
        val fallback = postRpc(
            url = rpcUrl,
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", "contract_call")
                .put("params", JSONArray().put(cleanAddress).put(cleanMethod).put(params)),
        )
        fallback.optJSONObject("error")?.let { error ->
            throw IllegalStateException(error.optString("message").ifBlank { "Circle program view failed." })
        }
        return fallback.opt("result")
    }

    private fun executeCircleReadRpc(rpcUrl: String, cleanMethod: String, params: JSONArray): Any? {
        if (cleanMethod == "octra_callProgramView") {
            val request = params.optJSONObject(0) ?: throw IllegalArgumentException("octra_callProgramView payload is required.")
            return circleProgramView(
                address = request.optString("address"),
                method = request.optString("method"),
                params = request.optJSONArray("params") ?: JSONArray(),
            )
        }

        val direct = postRpc(
            url = rpcUrl,
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", cleanMethod)
                .put("params", params),
        )
        direct.optJSONObject("error")?.let { error ->
            throw IllegalStateException(error.optString("message").ifBlank { "Circle RPC failed." })
        }
        return direct.opt("result")
    }

    private fun executeCircleReadBatch(rpcUrl: String, calls: List<CircleRpcBatchCall>): Map<Int, JSONObject> {
        val responses = mutableMapOf<Int, JSONObject>()
        val useProgramView = !unsupportedCircleProgramViewUrls.contains(rpcUrl)
        val rawBatch = JSONArray()
        calls.forEachIndexed { batchIndex, call ->
            rawBatch.put(rawCircleRpcPayload(call, batchIndex + 1, useProgramView))
        }

        val rawResponses = postRpcBatch(rpcUrl, rawBatch)
        val byId = jsonRpcResponsesById(rawResponses)
        val fallbackCalls = mutableListOf<CircleRpcBatchCall>()

        calls.forEachIndexed { batchIndex, call ->
            val rawResponse = byId[batchIndex + 1] ?: JSONObject()
                .put("error", JSONObject().put("code", -32000).put("message", "Missing circle RPC response."))
            if (useProgramView && call.method == "octra_callProgramView" && isMethodNotFound(rawResponse.optJSONObject("error")?.optString("message").orEmpty())) {
                fallbackCalls += call
            } else {
                responses[call.index] = normalizeCircleRpcBatchResponse(call, rawResponse)
            }
        }

        if (fallbackCalls.isNotEmpty()) {
            unsupportedCircleProgramViewUrls.add(rpcUrl)
            val fallbackBatch = JSONArray()
            fallbackCalls.forEachIndexed { batchIndex, call ->
                fallbackBatch.put(rawCircleRpcPayload(call, batchIndex + 1, useProgramView = false))
            }
            val fallbackResponses = jsonRpcResponsesById(postRpcBatch(rpcUrl, fallbackBatch))
            fallbackCalls.forEachIndexed { batchIndex, call ->
                val rawResponse = fallbackResponses[batchIndex + 1] ?: JSONObject()
                    .put("error", JSONObject().put("code", -32000).put("message", "Missing circle RPC fallback response."))
                responses[call.index] = normalizeCircleRpcBatchResponse(call, rawResponse)
            }
        }

        return responses
    }

    private fun validateCircleReadRpc(method: String, params: JSONArray): String {
        val cleanMethod = method.trim()
        require(cleanMethod in CircleReadRpcMethods) { "Circle RPC method is not allowed: $cleanMethod" }
        require(params.length() <= MaxCircleRpcParams) { "Circle RPC params are too large." }
        return cleanMethod
    }

    private fun rawCircleRpcPayload(call: CircleRpcBatchCall, id: Int, useProgramView: Boolean): JSONObject {
        if (call.method == "octra_callProgramView") {
            val request = call.params.optJSONObject(0) ?: throw IllegalArgumentException("octra_callProgramView payload is required.")
            val address = request.optString("address")
            val method = request.optString("method")
            val params = request.optJSONArray("params") ?: JSONArray()
            require(address.startsWith("oct") && address.length == 47) { "Enter a valid Octra address." }
            require(method.matches(Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$"))) { "Enter a valid view method." }
            require(params.length() <= MaxCircleRpcParams) { "Circle view params are too large." }
            if (useProgramView) {
                return JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", id)
                    .put("method", "octra_callProgramView")
                    .put(
                        "params",
                        JSONArray().put(
                            JSONObject()
                                .put("address", address)
                                .put("method", method)
                                .put("params", params),
                        ),
                    )
            }
            return JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", id)
                .put("method", "contract_call")
                .put("params", JSONArray().put(address).put(method).put(params))
        }

        return JSONObject()
            .put("jsonrpc", call.request.optString("jsonrpc", "2.0").ifBlank { "2.0" })
            .put("id", id)
            .put("method", call.method)
            .put("params", call.params)
    }

    private fun normalizeCircleRpcBatchResponse(call: CircleRpcBatchCall, rawResponse: JSONObject): JSONObject {
        rawResponse.optJSONObject("error")?.let { error ->
            return circleRpcError(
                call.request,
                error.optInt("code", -32000),
                error.optString("message").ifBlank { "Circle RPC failed." },
            )
        }

        val result = rawResponse.opt("result") ?: JSONObject.NULL
        putCircleReadCache(call.cacheKey.takeIf { it.isNotBlank() }, result)
        return circleRpcSuccess(call.request, result)
    }

    private fun jsonRpcResponsesById(responses: JSONArray): Map<Int, JSONObject> {
        val output = mutableMapOf<Int, JSONObject>()
        for (index in 0 until responses.length()) {
            val response = responses.optJSONObject(index) ?: continue
            output[response.optInt("id", index + 1)] = response
        }
        return output
    }

    private fun circleRpcSuccess(request: Any, result: Any?): JSONObject =
        JSONObject()
            .put("jsonrpc", (request as? JSONObject)?.optString("jsonrpc", "2.0")?.ifBlank { "2.0" } ?: "2.0")
            .put("id", (request as? JSONObject)?.opt("id") ?: JSONObject.NULL)
            .put("result", result ?: JSONObject.NULL)

    private fun circleRpcError(request: Any, code: Int, message: String): JSONObject =
        JSONObject()
            .put("jsonrpc", (request as? JSONObject)?.optString("jsonrpc", "2.0")?.ifBlank { "2.0" } ?: "2.0")
            .put("id", (request as? JSONObject)?.opt("id") ?: JSONObject.NULL)
            .put(
                "error",
                JSONObject()
                    .put("code", code)
                    .put("message", message),
            )

    private fun circleProgramViewCacheKey(rpcUrl: String, address: String, method: String, params: JSONArray): String =
        "$rpcUrl|octra_callProgramView|$address|$method|${params}"

    private fun circleReadRpcCacheKey(rpcUrl: String, method: String, params: JSONArray): String? {
        if (method !in CircleReadCacheMethods) return null
        return "$rpcUrl|$method|${params}"
    }

    private fun cachedCircleReadValue(key: String?): CircleReadCacheEntry? {
        if (key.isNullOrBlank()) return null
        val entry = circleReadCache[key] ?: return null
        if (entry.expiresAtMs <= System.currentTimeMillis()) {
            circleReadCache.remove(key)
            return null
        }
        return entry
    }

    private fun cachedCircleRead(key: String?, loader: () -> Any?): Any? {
        val cached = cachedCircleReadValue(key)
        if (cached != null) return cached.value
        if (key.isNullOrBlank()) return loader()
        val lock = circleReadLocks.getOrPut(key) { Any() }
        return synchronized(lock) {
            val lockedCached = cachedCircleReadValue(key)
            if (lockedCached != null) {
                lockedCached.value
            } else {
                loader().also { value ->
                    putCircleReadCache(key, value)
                }
            }
        }
    }

    private fun putCircleReadCache(key: String?, value: Any?) {
        if (key.isNullOrBlank()) return
        if (circleReadCache.size > MaxCircleReadCacheEntries) circleReadCache.clear()
        circleReadCache[key] = CircleReadCacheEntry(
            expiresAtMs = System.currentTimeMillis() + CircleReadCacheMillis,
            value = value,
        )
    }

    private fun isMethodNotFound(message: String): Boolean =
        message.contains("method not found", ignoreCase = true) ||
            message.contains("-32601", ignoreCase = true)

    fun fetchCircleAsset(circleId: String, path: String): CircleAsset {
        val cleanCircleId = circleId.trim()
        val cleanPath = normalizeCircleAssetPath(path)
        require(cleanCircleId.startsWith("oct") && cleanCircleId.length == 47) { "Enter a valid Octra circle address." }
        val json = postRpc(
            url = currentUrl(),
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", "circle_asset")
                .put("params", JSONArray().put(cleanCircleId).put(cleanPath)),
        )
        json.optJSONObject("error")?.let { error ->
            throw IllegalStateException(error.optString("message").ifBlank { "Circle asset lookup failed." })
        }
        val result = json.optJSONObject("result") ?: throw IllegalStateException("Circle asset was not found.")
        val bodyB64 = result.optString("body_b64")
        require(bodyB64.isNotBlank()) { "Circle asset body is empty." }
        val body = Base64.decode(bodyB64, Base64.DEFAULT)
        return CircleAsset(
            circleId = result.optString("circle_id", cleanCircleId),
            canonicalPath = normalizeCircleAssetPath(result.optString("canonical_path", cleanPath)),
            contentType = result.optString("content_type", "application/octet-stream").ifBlank { "application/octet-stream" },
            body = body,
            sizeBytes = result.optLong("size_bytes", body.size.toLong()),
        )
    }

    fun fetchSealedCircleAsset(circleId: String, path: String, passphrase: String): CircleAsset {
        val cleanCircleId = circleId.trim()
        val cleanPath = normalizeCircleAssetPath(path)
        require(cleanCircleId.startsWith("oct") && cleanCircleId.length == 47) { "Enter a valid Octra circle address." }
        require(passphrase.isNotBlank()) { "Sealed read passphrase is required." }

        val result = fetchCircleCiphertext(cleanCircleId, cleanPath)
        val ciphertextB64 = result.optString("ciphertext_b64")
        val keyId = result.optString("key_id")
        val plaintextHash = result.optString("plaintext_hash")
        require(ciphertextB64.isNotBlank() && keyId.isNotBlank() && plaintextHash.isNotBlank()) {
            "Sealed asset metadata is incomplete."
        }
        val body = decryptSealedCircleBytes(
            circleId = cleanCircleId,
            keyId = keyId,
            passphrase = passphrase,
            ciphertextB64 = ciphertextB64,
            plaintextHash = plaintextHash,
        )
        return CircleAsset(
            circleId = result.optString("circle_id", cleanCircleId),
            canonicalPath = normalizeCircleAssetPath(result.optString("canonical_path", cleanPath)),
            contentType = result.optString("content_type", "application/octet-stream").ifBlank { "application/octet-stream" },
            body = body,
            sizeBytes = body.size.toLong(),
        )
    }

    private fun fetchCircleCiphertext(circleId: String, path: String): JSONObject {
        val resourceKey = circleResourceKey(circleId, path)
        runCatching {
            val json = postRpc(
                url = currentUrl(),
                payload = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 1)
                    .put("method", "circle_asset_ciphertext_by_resource_key")
                    .put("params", JSONArray().put(circleId).put(resourceKey)),
            )
            json.optJSONObject("error")?.let { error ->
                throw IllegalStateException(error.optString("message").ifBlank { "Circle sealed asset lookup failed." })
            }
            json.optJSONObject("result") ?: throw IllegalStateException("Circle sealed asset was not found.")
        }.getOrNull()?.let { return it }

        val json = postRpc(
            url = currentUrl(),
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", "circle_asset_ciphertext")
                .put("params", JSONArray().put(circleId).put(path)),
        )
        json.optJSONObject("error")?.let { error ->
            throw IllegalStateException(error.optString("message").ifBlank { "Circle sealed asset lookup failed." })
        }
        return json.optJSONObject("result") ?: throw IllegalStateException("Circle sealed asset was not found.")
    }

    private fun decryptSealedCircleBytes(
        circleId: String,
        keyId: String,
        passphrase: String,
        ciphertextB64: String,
        plaintextHash: String,
    ): ByteArray {
        val envelope = Base64.decode(ciphertextB64, Base64.DEFAULT)
        val magic = "OCRS1".toByteArray(Charsets.UTF_8)
        require(envelope.size > magic.size + 12 && envelope.copyOfRange(0, magic.size).contentEquals(magic)) {
            "Invalid sealed circle envelope."
        }
        val nonceStart = magic.size
        val nonceEnd = nonceStart + 12
        val nonce = envelope.copyOfRange(nonceStart, nonceEnd)
        val cipherBytes = envelope.copyOfRange(nonceEnd, envelope.size)
        val cipher = Cipher.getInstance(AesGcmMode)
        cipher.init(Cipher.DECRYPT_MODE, deriveCircleReadKey(circleId, keyId, passphrase), GCMParameterSpec(128, nonce))
        val plainFrame = runCatching { cipher.doFinal(cipherBytes) }.getOrElse {
            throw IllegalStateException("Sealed read passphrase did not match this circle.")
        }
        require(plainFrame.size >= 4) { "Invalid sealed circle payload." }
        val plainSize = readU32Be(plainFrame, 0)
        require(plainSize >= 0 && plainSize <= plainFrame.size - 4) { "Invalid sealed circle payload length." }
        val plaintext = plainFrame.copyOfRange(4, 4 + plainSize)
        val actualHash = sha256Hex(plaintext)
        require(actualHash.equals(plaintextHash.trim(), ignoreCase = true)) {
            "Sealed circle plaintext hash mismatch."
        }
        return plaintext
    }

    private fun deriveCircleReadKey(circleId: String, keyId: String, passphrase: String): SecretKeySpec {
        val salt = "octra:circle:sealed_read:v1:$circleId:$keyId".toByteArray(Charsets.UTF_8)
        val platformKey = runCatching {
            val spec = PBEKeySpec(passphrase.toCharArray(), salt, SealedReadIterations, AesKeyBits)
            try {
                SecretKeyFactory.getInstance(PbkdfSha256).generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        }.getOrNull()
        if (platformKey != null) return SecretKeySpec(platformKey, "AES")

        val generator = PKCS5S2ParametersGenerator(SHA256Digest())
        generator.init(passphrase.toByteArray(Charsets.UTF_8), salt, SealedReadIterations)
        val key = (generator.generateDerivedParameters(AesKeyBits) as KeyParameter).key
        return SecretKeySpec(key, "AES")
    }

    private fun circleResourceKey(circleId: String, canonicalPath: String): String {
        val out = ByteArrayOutputStream()
        out.write("octra:circle_resource_key:v1".toByteArray(Charsets.UTF_8))
        out.write(0)
        writeFrame(out, circleId.toByteArray(Charsets.UTF_8))
        writeFrame(out, canonicalPath.toByteArray(Charsets.UTF_8))
        return sha256Hex(out.toByteArray())
    }

    private fun writeFrame(out: ByteArrayOutputStream, bytes: ByteArray) {
        out.write((bytes.size ushr 24) and 0xff)
        out.write((bytes.size ushr 16) and 0xff)
        out.write((bytes.size ushr 8) and 0xff)
        out.write(bytes.size and 0xff)
        out.write(bytes)
    }

    private fun readU32Be(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256")
            .digest(bytes)
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun isValidIdentityPath(value: String): Boolean {
        val parts = value.split('.')
        if (parts.isEmpty() || parts.size > 2) return false
        return parts.all { it.matches(Regex("^[a-z0-9]{3,63}$")) }
    }

    private fun normalizeCircleAssetPath(rawPath: String): String {
        val clean = rawPath.trim()
        if (clean.isBlank()) return "/index.html"
        return if (clean.startsWith("/")) clean else "/$clean"
    }

    private fun String.toIdentityDomain(): String? {
        val clean = trim()
            .lowercase()
            .removeSuffix(".oct")
            .removeSuffix(".octra")
            .removeSuffix(".id")
        if (!isValidIdentityPath(clean)) return null
        return "$clean.oct"
    }

    fun currentNetwork(): String = networkFor(currentUrl())

    private fun createSignedTransaction(
        wallet: Wallet,
        recipientAddress: String,
        amount: Double,
        nonce: Long,
        message: String?,
        feeOu: Long,
    ): JSONObject {
        val amountMu = BigDecimal.valueOf(amount)
            .multiply(BigDecimal.valueOf(MicroUnits))
            .setScale(0, RoundingMode.DOWN)
            .toPlainString()
        return createSignedTransactionRaw(
            wallet = wallet,
            recipientAddress = recipientAddress,
            amountRaw = amountMu,
            nonce = nonce,
            message = message,
            feeOu = feeOu,
            opType = "standard",
            encryptedData = null,
        )
    }

    private fun createSignedTransactionRaw(
        wallet: Wallet,
        recipientAddress: String,
        amountRaw: String,
        nonce: Long,
        message: String?,
        feeOu: Long,
        opType: String,
        encryptedData: String?,
    ): JSONObject {
        val ou = feeOu.coerceAtLeast(1L).toString()
        val timestampMillis = System.currentTimeMillis()
        val timestamp = timestampMillis / 1000.0
        // Octra's verifier canonicalizes epoch seconds as a plain decimal.
        // JSONObject.numberToString(Double) switches values this large to
        // scientific notation, which produces a different signed message.
        val canonicalTimestamp = canonicalOctraTimestamp(timestampMillis)

        val canonical = canonicalOctraTransaction(
            from = wallet.address,
            to = recipientAddress,
            amountRaw = amountRaw,
            nonce = nonce,
            ou = ou,
            timestamp = canonicalTimestamp,
            opType = opType,
            encryptedData = encryptedData,
            message = message,
        )

        val (privateKey, publicKey) = signingMaterial(wallet)
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        val signingBytes = canonical.toByteArray(Charsets.UTF_8)
        signer.update(signingBytes, 0, signingBytes.size)
        val signature = signer.generateSignature()

        return JSONObject()
            .put("from", wallet.address)
            .put("to_", recipientAddress)
            .put("amount", amountRaw)
            .put("nonce", nonce)
            .put("ou", ou)
            .put("timestamp", timestamp)
            .put("op_type", opType.ifBlank { "standard" })
            .apply {
                if (!encryptedData.isNullOrBlank()) put("encrypted_data", encryptedData)
            }
            .apply {
                if (!message.isNullOrBlank()) put("message", message)
            }
            .put("signature", Base64.encodeToString(signature, Base64.NO_WRAP))
            .put("public_key", Base64.encodeToString(publicKey, Base64.NO_WRAP))
    }

    private fun fetchTransactionDetails(hash: String, address: String, url: String = currentUrl()): TransactionHistoryItem {
        val json = postRpc(
            url = url,
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", "octra_transaction")
                .put("params", JSONArray().put(hash)),
        )
        val data = json.optJSONObject("result") ?: throw IllegalStateException("Empty transaction result")
        val from = data.optString("from")
        val to = data.optString("to", data.optString("to_"))
        val txHash = data.optString("tx_hash", hash)
        val status = data.optString("status").lowercase()
        val stageStatus = data.optString("stage_status").lowercase()
        val normalizedStatus = when {
            status == "rejected" || status == "dropped" -> status
            (data.has("epoch") && !data.isNull("epoch")) || status == "confirmed" || stageStatus == "confirmed" -> "confirmed"
            status.isNotBlank() -> status
            stageStatus.isNotBlank() -> stageStatus
            else -> "confirmed"
        }
        return TransactionHistoryItem(
            hash = txHash,
            from = from,
            to = to,
            amount = parseAmount(data.opt("amount_raw"), data.opt("amount")),
            timestamp = data.optDouble("timestamp", 0.0),
            status = normalizedStatus,
            type = if (from.equals(address, ignoreCase = true)) "sent" else "received",
            opType = data.optString("op_type", "standard"),
            message = data.optString("message").ifBlank { null },
        )
    }

    private fun fetchPendingTransactions(address: String, url: String = currentUrl()): List<TransactionHistoryItem> {
        return runCatching {
            val json = postRpc(
                url = url,
                payload = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 1)
                    .put("method", "staging_view")
                    .put("params", JSONArray()),
            )
            val result = json.optJSONObject("result") ?: json
            val transactions = result.optJSONArray("transactions")
                ?: result.optJSONArray("staged_transactions")
                ?: JSONArray()
            buildList {
                for (index in 0 until transactions.length()) {
                    val tx = transactions.optJSONObject(index) ?: continue
                    val from = tx.optString("from")
                    val to = tx.optString("to", tx.optString("to_"))
                    if (!from.equals(address, ignoreCase = true) && !to.equals(address, ignoreCase = true)) continue
                    add(
                        TransactionHistoryItem(
                            hash = tx.optString("hash", "pending-${tx.optLong("nonce", index.toLong())}"),
                            from = from,
                            to = to,
                            amount = parseAmount(null, tx.opt("amount")),
                            timestamp = tx.optDouble("timestamp", 0.0),
                            status = "pending",
                            type = if (from.equals(address, ignoreCase = true)) "sent" else "received",
                            opType = tx.optString("op_type", "standard"),
                            message = tx.optString("message").ifBlank { null },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun fetchBalanceStrict(address: String, url: String): BalanceSnapshot {
        val response = postRpc(
            url = url,
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", System.currentTimeMillis())
                .put("method", "octra_balance")
                .put("params", JSONArray().put(address)),
        )
        response.optJSONObject("error")?.let { error ->
            throw IllegalStateException(error.optString("message", "Unable to fetch wallet nonce."))
        }
        val result = response.optJSONObject("result")
            ?: throw IllegalStateException("Octra balance response is empty.")
        val raw = when (val value = result.opt("balance_raw")) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
        val balance = if (raw > 0L) raw / MicroUnits.toDouble() else result.optDouble("balance", 0.0)
        return BalanceSnapshot(
            balance = balance,
            nonce = result.optLong("pending_nonce", result.optLong("nonce", 0L)),
        )
    }

    private fun postRpc(
        url: String,
        payload: JSONObject,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 10_000,
    ): JSONObject {
        val value = postRpcValue(url, payload, connectTimeoutMs, readTimeoutMs)
        return value as? JSONObject ?: throw IllegalStateException("RPC response was not an object.")
    }

    private fun postRpcBatch(url: String, payload: JSONArray): JSONArray {
        val value = postRpcValue(url, payload)
        return value as? JSONArray ?: throw IllegalStateException("RPC batch response was not an array.")
    }

    private fun postRpcValue(
        url: String,
        payload: Any,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 10_000,
    ): Any {
        val rpcUrl = URL("${url.trim().trimEnd('/')}/rpc")
        val connection = (rpcUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
        }

        return try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(payload.toString())
            }

            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val body = stream.bufferedReader().use { it.readText() }
            if (responseCode !in 200..299) {
                throw IllegalStateException("HTTP $responseCode")
            }
            JSONTokener(body).nextValue()
        } finally {
            connection.disconnect()
        }
    }

    private fun contractCall(
        contractAddress: String,
        method: String,
        params: JSONArray,
        walletAddress: String,
        url: String = currentUrl(),
    ): String? {
        return runCatching {
            val json = postRpc(
                url = url,
                payload = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 1)
                    .put("method", "contract_call")
                    .put(
                        "params",
                        JSONArray()
                            .put(contractAddress)
                            .put(method)
                            .put(params)
                            .put(walletAddress),
                    ),
            )
            json.optJSONObject("result")?.optString("result")?.ifBlank { null }
        }.getOrNull()
    }

    private fun networkFor(url: String): String {
        val clean = url.trim().trimEnd('/')
        rpcNetworks().firstOrNull { it.url == clean }?.let { return it.networkType }
        val lower = clean.lowercase()
        return if (lower.contains("devnet") || lower.contains("165.227.225.79")) "devnet" else "mainnet"
    }

    private fun defaultNetworkFromLegacy(): RpcNetwork {
        val legacyUrl = prefs.getString(KeyRpcUrl, null)?.trim()?.trimEnd('/').orEmpty()
        val url = legacyUrl.ifBlank { DefaultMainnetUrl }
        val type = if (url.lowercase().contains("devnet") || url.contains("165.227.225.79")) "devnet" else "mainnet"
        return when (url) {
            DefaultMainnetUrl -> defaultMainnetNetwork()
            DefaultDevnetUrl -> defaultDevnetNetwork()
            else -> RpcNetwork(
                id = "rpc-legacy-${kotlin.math.abs(url.hashCode())}",
                name = if (type == "devnet") "Custom Devnet" else "Custom Mainnet",
                url = url,
                networkType = type,
                explorerUrl = if (type == "devnet") DefaultDevnetExplorerUrl else DefaultMainnetExplorerUrl,
            )
        }
    }

    private fun defaultRpcNetworks(): List<RpcNetwork> = listOf(defaultMainnetNetwork(), defaultDevnetNetwork())

    private fun defaultMainnetNetwork(): RpcNetwork = RpcNetwork(
        id = "octra-mainnet",
        name = "Octra Mainnet",
        url = DefaultMainnetUrl,
        networkType = "mainnet",
        explorerUrl = DefaultMainnetExplorerUrl,
    )

    private fun defaultDevnetNetwork(): RpcNetwork = RpcNetwork(
        id = "octra-devnet",
        name = "Octra Devnet",
        url = DefaultDevnetUrl,
        networkType = "devnet",
        explorerUrl = DefaultDevnetExplorerUrl,
    )

    private fun normalizeDefaultNetwork(network: RpcNetwork): RpcNetwork = when {
        network.id == "octra-mainnet" || network.url == DefaultMainnetUrl -> defaultMainnetNetwork()
        network.id == "octra-devnet" || network.url == DefaultDevnetUrl -> defaultDevnetNetwork()
        else -> network
    }

    private fun mergeNetworks(networks: List<RpcNetwork?>): List<RpcNetwork> {
        val merged = linkedMapOf<String, RpcNetwork>()
        networks.filterNotNull().forEach { network ->
            val clean = normalizeDefaultNetwork(network)
            merged[clean.id] = clean
        }
        return merged.values.toList()
    }

    private fun persistNetworks(networks: List<RpcNetwork>) {
        val array = JSONArray().apply { networks.forEach { put(it.toJson()) } }
        val activeId = prefs.getString(KeyActiveRpcId, null)
        val active = networks.firstOrNull { it.id == activeId }
            ?: networks.firstOrNull { it.id == DefaultMainnetId }
            ?: networks.firstOrNull()
        prefs.edit()
            .putString(KeyRpcNetworks, array.toString())
            .putString(KeyActiveRpcId, active?.id)
            .putString(KeyRpcUrl, active?.url)
            .apply()
    }

    private fun ensureMainnetDefault(networks: List<RpcNetwork>) {
        if (prefs.getBoolean(KeyMainnetDefaultMigrated, false)) return
        val activeId = prefs.getString(KeyActiveRpcId, null)
        val activeUrl = prefs.getString(KeyRpcUrl, null)?.trim()?.trimEnd('/').orEmpty()
        val shouldUseMainnet = activeId.isNullOrBlank() ||
            activeId == DefaultDevnetId ||
            activeUrl.isBlank() ||
            activeUrl == DefaultDevnetUrl
        val mainnet = networks.firstOrNull { it.id == DefaultMainnetId }
        val editor = prefs.edit().putBoolean(KeyMainnetDefaultMigrated, true)
        if (shouldUseMainnet && mainnet != null) {
            editor
                .putString(KeyActiveRpcId, mainnet.id)
                .putString(KeyRpcUrl, mainnet.url)
        }
        editor.apply()
    }

    private fun feeFallback(opType: String): Long = when (opType) {
        "encrypt", "decrypt" -> 10000L
        "stealth", "claim" -> 5000L
        "deploy", "upgrade" -> 200000L
        else -> 1000L
    }

    private fun parseAmount(raw: Any?, formatted: Any?): Double {
        if (raw != null && raw != JSONObject.NULL) {
            val value = when (raw) {
                is Number -> raw.toDouble()
                is String -> raw.toDoubleOrNull()
                else -> null
            }
            if (value != null) return value / MicroUnits
        }
        if (formatted != null && formatted != JSONObject.NULL) {
            val text = formatted.toString()
            val value = text.toDoubleOrNull() ?: return 0.0
            return if (text.contains(".")) value else value / MicroUnits
        }
        return 0.0
    }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Public key hex is invalid." }
        return ByteArray(length / 2) { index ->
            substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun signMessage(wallet: Wallet, message: String): String {
        val (privateKey, _) = signingMaterial(wallet)
        val signer = Ed25519Signer()
        signer.init(true, privateKey)
        val bytes = message.toByteArray(Charsets.UTF_8)
        signer.update(bytes, 0, bytes.size)
        return Base64.encodeToString(signer.generateSignature(), Base64.NO_WRAP)
    }

    private fun signingMaterial(wallet: Wallet): Pair<Ed25519PrivateKeyParameters, ByteArray> {
        val seed = runCatching { Base64.decode(wallet.privateKeyBase64, Base64.DEFAULT) }
            .getOrElse { throw IllegalArgumentException("Wallet private key is invalid.") }
        require(seed.size >= 32) { "Wallet private key is invalid." }
        val privateKey = Ed25519PrivateKeyParameters(seed, 0)
        val publicKey = privateKey.generatePublicKey().encoded
        val derivedAddress = "oct" + Base58.encode(MessageDigest.getInstance("SHA-256").digest(publicKey))
        require(derivedAddress == wallet.address) {
            "Wallet signing key does not match the active Octra address."
        }
        return privateKey to publicKey
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    companion object {
        const val DefaultMainnetUrl = "https://octra.network"
        const val DefaultDevnetUrl = "http://165.227.225.79:8080"
        const val DefaultMainnetExplorerUrl = "https://octrascan.io"
        const val DefaultDevnetExplorerUrl = "https://devnet.octrascan.io"
        private const val DefaultMainnetId = "octra-mainnet"
        private const val DefaultDevnetId = "octra-devnet"
        private const val KeyRpcUrl = "rpc_url"
        private const val KeyRpcNetworks = "rpc_networks"
        private const val KeyActiveRpcId = "active_rpc_id"
        private const val KeyDefaultRpcSeeded = "default_rpc_seeded"
        private const val KeyMainnetDefaultMigrated = "mainnet_default_migrated"
        private const val MicroUnits = 1_000_000L
        private const val AesGcmMode = "AES/GCM/NoPadding"
        private const val PbkdfSha256 = "PBKDF2WithHmacSHA256"
        private const val SealedReadIterations = 120_000
        private const val AesKeyBits = 256
        private const val MaxCircleRpcParams = 32
        private const val MaxCircleRpcBatchSize = 64
        private const val HistoryDetailsBatchSize = 6
        private const val OctraTokenLookupBatchSize = 4
        private const val PrivacyHistoryQueryLimit = 50
        private val PrivacyOperationTypes = setOf("encrypt", "decrypt", "stealth", "claim")
        private const val CircleReadCacheMillis = 8_000L
        private const val MaxCircleReadCacheEntries = 512
        private val CircleReadRpcMethods = setOf(
            "octra_callProgramView",
            "contract_call",
            "circle_info",
            "circle_asset",
            "node_status",
            "epoch_current",
            "octra_balance",
            "octra_account",
            "octra_transaction",
            "contract_receipt",
            "octra_recommendedFee",
        )
        private val CircleReadCacheMethods = setOf(
            "octra_callProgramView",
            "contract_call",
            "circle_info",
            "circle_asset",
            "node_status",
            "epoch_current",
        )
    }
}

internal fun normalizeEncryptedBalanceCipher(value: String): String? {
    val cipher = value.trim()
    if (cipher.isBlank() || cipher == "0" || cipher.equals("null", ignoreCase = true)) return null
    require(cipher.startsWith("hfhe_v1|")) {
        "Encrypted balance uses an unsupported ciphertext format."
    }
    val payload = cipher.removePrefix("hfhe_v1|")
    require(payload.isNotBlank() && payload.length % 4 != 1 && payload.matches(Regex("[A-Za-z0-9+/]+={0,2}"))) {
        "Encrypted balance contains invalid Base64 data."
    }
    return cipher
}

internal fun canonicalJsonQuote(value: String): String = buildString(value.length + 2) {
    append('"')
    value.forEach { character ->
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
    append('"')
}

internal fun canonicalOctraTransaction(
    from: String,
    to: String,
    amountRaw: String,
    nonce: Long,
    ou: String,
    timestamp: String,
    opType: String,
    encryptedData: String? = null,
    message: String? = null,
): String = buildString {
    append("{\"from\":").append(canonicalJsonQuote(from))
    append(",\"to_\":").append(canonicalJsonQuote(to))
    append(",\"amount\":").append(canonicalJsonQuote(amountRaw))
    append(",\"nonce\":").append(nonce)
    append(",\"ou\":").append(canonicalJsonQuote(ou))
    append(",\"timestamp\":").append(timestamp)
    append(",\"op_type\":").append(canonicalJsonQuote(opType.ifBlank { "standard" }))
    if (!encryptedData.isNullOrBlank()) {
        append(",\"encrypted_data\":").append(canonicalJsonQuote(encryptedData))
    }
    if (!message.isNullOrBlank()) {
        append(",\"message\":").append(canonicalJsonQuote(message))
    }
    append('}')
}

internal fun canonicalOctraTimestamp(epochMillis: Long): String {
    val value = BigDecimal.valueOf(epochMillis)
        .movePointLeft(3)
        .stripTrailingZeros()
        .toPlainString()
    return if ('.' in value) value else "$value.0"
}
