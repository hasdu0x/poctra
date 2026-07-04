package com.poctra.wallet.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class CircleAsset(
    val circleId: String,
    val canonicalPath: String,
    val contentType: String,
    val body: ByteArray,
    val sizeBytes: Long,
)

class RpcRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("poctra_rpc", Context.MODE_PRIVATE)

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

    suspend fun checkTransactionStatus(hash: String): TransactionNetworkState = withContext(Dispatchers.IO) {
        runCatching {
            val json = postRpc(
                url = currentUrl(),
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
            when {
                status == "rejected" -> TransactionNetworkState(
                    TransactionNetworkStatus.Rejected,
                    data.optString("reason").ifBlank { null },
                )
                status == "dropped" -> TransactionNetworkState(
                    TransactionNetworkStatus.Dropped,
                    data.optString("reason").ifBlank { null },
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

    suspend fun fetchTransactionHistory(address: String, limit: Int = 11, url: String = currentUrl()): List<TransactionHistoryItem> =
        withContext(Dispatchers.IO) {
            val confirmed = runCatching {
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
                buildList {
                    for (index in 0 until recent.length()) {
                        val item = recent.optJSONObject(index) ?: continue
                        val hash = item.optString("hash", item.optString("tx_hash"))
                        if (hash.isBlank()) continue
                        val tx = runCatching { fetchTransactionDetails(hash, address, url) }.getOrElse {
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
                        add(tx)
                    }
                }
            }.getOrDefault(emptyList())

            val pending = fetchPendingTransactions(address, url)
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
            val lookedUp = missing.mapNotNull { lookupOctraToken(it, walletAddress, url) }
            fetched + lookedUp
        }

    suspend fun lookupOctraToken(contractAddress: String, walletAddress: String, url: String = currentUrl()): OctraToken? =
        withContext(Dispatchers.IO) {
            if (!contractAddress.startsWith("oct") || contractAddress.length != 47) return@withContext null

            val name = contractCall(contractAddress, "get_name", JSONArray(), walletAddress, url)
            val symbol = contractCall(contractAddress, "get_symbol", JSONArray(), walletAddress, url)
            val decimals = contractCall(contractAddress, "decimals", JSONArray(), walletAddress, url)
            val balance = contractCall(
                contractAddress = contractAddress,
                method = "balance_of",
                params = JSONArray().put(walletAddress),
                walletAddress = walletAddress,
                url = url,
            )
            val supply = contractCall(contractAddress, "get_total_supply", JSONArray(), walletAddress, url)

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
        val timestamp = BigDecimal.valueOf(System.currentTimeMillis())
            .divide(BigDecimal.valueOf(1000))
            .toPlainString()

        val canonical = buildString {
            append("{")
            append("\"from\":").append(JSONObject.quote(wallet.address))
            append(",\"to_\":").append(JSONObject.quote(recipientAddress))
            append(",\"amount\":").append(JSONObject.quote(amountRaw))
            append(",\"nonce\":").append(nonce)
            append(",\"ou\":").append(JSONObject.quote(ou))
            append(",\"timestamp\":").append(timestamp)
            append(",\"op_type\":").append(JSONObject.quote(opType.ifBlank { "standard" }))
            if (!encryptedData.isNullOrBlank()) {
                append(",\"encrypted_data\":").append(JSONObject.quote(encryptedData))
            }
            if (!message.isNullOrBlank()) {
                append(",\"message\":").append(JSONObject.quote(message))
            }
            append("}")
        }

        val privateKey = Base64.decode(wallet.privateKeyBase64, Base64.NO_WRAP)
        val publicKey = wallet.publicKeyHex.hexToBytes()
        val signer = Ed25519Signer()
        signer.init(true, Ed25519PrivateKeyParameters(privateKey, 0))
        val signingBytes = canonical.toByteArray(Charsets.UTF_8)
        signer.update(signingBytes, 0, signingBytes.size)
        val signature = signer.generateSignature()

        return JSONObject()
            .put("from", wallet.address)
            .put("to_", recipientAddress)
            .put("amount", amountRaw)
            .put("nonce", nonce)
            .put("ou", ou)
            .put("timestamp", timestamp.toDouble())
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

    private fun postRpc(url: String, payload: JSONObject): JSONObject {
        val rpcUrl = URL("${url.trim().trimEnd('/')}/rpc")
        val connection = (rpcUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
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
            JSONObject(body)
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
        "stealth" -> 5000L
        "decrypt", "encrypt" -> 3000L
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
    }
}
