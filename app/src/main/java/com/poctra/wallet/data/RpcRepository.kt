package com.poctra.wallet.data

import android.content.Context
import android.util.Base64
import com.poctra.wallet.dapp.circle.normalizeCircleAssetPath
import com.poctra.wallet.dapp.circle.isTransientCircleFailure
import com.poctra.wallet.dapp.circle.shouldFallbackCircleLookup
import com.poctra.wallet.dapp.circle.withCircleReadRetry
import com.poctra.wallet.privacy.PrivacyEncryptedBalance
import com.poctra.wallet.privacy.PrivacyMigrationNetworkStatus
import com.poctra.wallet.privacy.PrivacyKeyEncoding
import com.poctra.wallet.core.Base58
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

data class CircleDeploySubmission(
    val circleId: String,
    val result: SendResult,
)

data class RecentActivityPage(
    val publicTransactions: List<TransactionHistoryItem>,
    val privacyTransactions: List<TransactionHistoryItem>,
    val sourceCount: Int,
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

private data class RecentActivityCacheEntry(
    val requestedLimit: Int,
    val expiresAtMs: Long,
    val transactions: List<TransactionHistoryItem>,
)

class RpcRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("poctra_rpc", Context.MODE_PRIVATE)
    private val unsupportedCircleProgramViewUrls = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val circleReadCache = ConcurrentHashMap<String, CircleReadCacheEntry>()
    private val circleReadLocks = ConcurrentHashMap<String, Any>()
    private val registeredPvacKeys = PrivacyRegistrationCache()
    private val transactionDetailsCache = ConcurrentHashMap<String, TransactionHistoryItem>()
    private val recentActivityCache = ConcurrentHashMap<String, RecentActivityCacheEntry>()
    private val recentActivityLocks = ConcurrentHashMap<String, Any>()
    private val nonceLocks = ConcurrentHashMap<String, Mutex>()

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
        val normalized = mergeNetworks(parsed.map(::normalizeDefaultNetwork))
        val defaultsChanged = parsed != normalized
        val networks = if (normalized.isEmpty()) {
            mergeNetworks(defaultRpcNetworks() + defaultNetworkFromLegacy().takeIf { legacy ->
                defaultRpcNetworks().none { it.url == legacy.url }
            })
        } else if (!seeded) {
            mergeNetworks(defaultRpcNetworks() + normalized)
        } else {
            normalized
        }

        if (normalized.isEmpty() || !seeded || defaultsChanged) {
            val legacy = defaultNetworkFromLegacy()
            val storedActiveId = prefs.getString(KeyActiveRpcId, null)
            val storedActiveUrl = prefs.getString(KeyRpcUrl, null)?.trim()?.trimEnd('/').orEmpty()
            val activeId = networks.firstOrNull { it.id == storedActiveId }?.id
                ?: networks.firstOrNull { it.url.equals(storedActiveUrl, ignoreCase = true) }?.id
                ?: networks.firstOrNull { it.url == legacy.url }?.id
                ?: networks.firstOrNull { it.id == DefaultMainnetId }?.id
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
        return host in setOf("localhost", "127.0.0.1", "10.0.2.2")
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
        val rpcUrl = currentUrl()
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

        val snapshot = nonceLocks.getOrPut(wallet.address.lowercase()) { Mutex() }.withLock {
            fetchBalance(wallet.address, rpcUrl)
        }
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
        opType: String = "call",
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

        val snapshot = nonceLocks.getOrPut(wallet.address.lowercase()) { Mutex() }.withLock {
            fetchBalance(wallet.address, rpcUrl)
        }
        val tx = createSignedTransactionRaw(
            wallet = wallet,
            recipientAddress = contract,
            amountRaw = amount,
            nonce = snapshot.nonce + 1,
            message = params.toString(),
            feeOu = feeOu,
            opType = opType,
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

    suspend fun sendCircleProgramCall(
        wallet: Wallet,
        circleId: String,
        method: String,
        params: JSONArray,
        amountOu: String,
        feeOu: Long,
    ): SendResult = sendContractCall(
        wallet = wallet,
        contractAddress = circleId,
        method = method,
        params = params,
        amountOu = amountOu,
        feeOu = feeOu,
        opType = "circle_call",
    )

    suspend fun deployPublicCircle(
        wallet: Wallet,
        feeOu: Long,
    ): CircleDeploySubmission = withContext(Dispatchers.IO) {
        val rpcUrl = currentUrl()
        val snapshot = fetchBalance(wallet.address, rpcUrl)
        val nonce = snapshot.nonce + 1
        val payload = publicCircleDeployPayload()
        val circleId = deriveCircleDeployId(wallet.address, nonce, payload)
        val tx = createSignedTransactionRaw(
            wallet = wallet,
            recipientAddress = circleId,
            amountRaw = "0",
            nonce = nonce,
            message = payload,
            feeOu = feeOu,
            opType = "deploy_circle",
            encryptedData = null,
        )
        CircleDeploySubmission(
            circleId = circleId,
            result = submitCircleTransaction(tx, "Circle deployment", rpcUrl),
        )
    }

    suspend fun putPublicCircleAsset(
        wallet: Wallet,
        circleId: String,
        path: String,
        contentType: String,
        bodyBase64: String,
        encoding: String?,
        feeOu: Long,
    ): SendResult = withContext(Dispatchers.IO) {
        val rpcUrl = currentUrl()
        val cleanCircleId = circleId.trim()
        require(cleanCircleId.startsWith("oct") && cleanCircleId.length == 47) { "Enter a valid target Circle address." }
        val cleanPath = validateCircleWritePath(path)
        val cleanContentType = validateCircleContentType(contentType)
        val cleanBody = validateCircleAssetBody(bodyBase64)
        val cleanEncoding = encoding?.trim()?.lowercase()?.ifBlank { null }
        require(cleanEncoding == null || cleanEncoding in setOf("identity", "gzip")) {
            "Circle asset encoding must be identity or gzip."
        }
        val snapshot = fetchBalance(wallet.address, rpcUrl)
        val message = JSONObject()
            .put("path", cleanPath)
            .put("content_type", cleanContentType)
            .apply { cleanEncoding?.let { put("encoding", it) } }
            .toString()
        val tx = createSignedTransactionRaw(
            wallet = wallet,
            recipientAddress = cleanCircleId,
            amountRaw = "0",
            nonce = snapshot.nonce + 1,
            message = message,
            feeOu = feeOu,
            opType = "circle_asset_put",
            encryptedData = cleanBody,
        )
        submitCircleTransaction(tx, "Circle asset upload", rpcUrl)
    }

    private fun submitCircleTransaction(tx: JSONObject, label: String, rpcUrl: String): SendResult {
        val response = postRpc(
            url = rpcUrl,
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", System.currentTimeMillis())
                .put("method", "octra_submit")
                .put("params", JSONArray().put(tx)),
        )
        response.optJSONObject("error")?.let { error ->
            return SendResult(
                success = false,
                error = error.optString("message", error.optString("reason", "$label failed")),
                finality = "rejected",
            )
        }
        val result = response.optJSONObject("result") ?: JSONObject()
        val status = result.optString("status").lowercase()
        val hash = result.optString("tx_hash", result.optString("hash")).ifBlank { null }
        return when {
            status == "accepted" || hash != null -> SendResult(success = true, hash = hash, finality = "accepted")
            status == "rejected" -> SendResult(
                success = false,
                error = result.optString("reason", "$label rejected"),
                finality = "rejected",
            )
            else -> SendResult(success = false, error = "$label returned an unknown response.")
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
        response.optJSONObject("error")?.let { error ->
            throw IllegalStateException(
                error.optString("message", error.optString("reason", "Unable to read encrypted balance.")),
            )
        }
        val result = response.opt("result")
        val cipher = when (result) {
            is JSONObject -> result.optString("cipher")
            is String -> result
            else -> ""
        }.trim()
        normalizeEncryptedBalanceCipher(cipher)?.let(::PrivacyEncryptedBalance)
    }

    suspend fun fetchPvacPublicKey(
        address: String,
        url: String = currentUrl(),
    ): String? = withContext(Dispatchers.IO) {
        val response = postRpc(
            url = url,
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", System.currentTimeMillis())
                .put("method", "octra_pvacPubkey")
                .put("params", JSONArray().put(address)),
            connectTimeoutMs = 20_000,
            readTimeoutMs = 120_000,
        )
        response.optJSONObject("error")?.let { error ->
            val message = error.optString("message", error.optString("reason"))
            if (
                message.contains("not found", ignoreCase = true) ||
                message.contains("not registered", ignoreCase = true)
            ) {
                return@withContext null
            }
            throw IllegalStateException(message.ifBlank { "Unable to read the registered Privacy key." })
        }
        val result = response.opt("result")
        val key = when (result) {
            is JSONObject -> result.optString("pvac_pubkey")
            is String -> result
            else -> ""
        }.trim()
        key.ifBlank { null }
    }

    suspend fun fetchPvacMigrationStatus(
        address: String,
        url: String = currentUrl(),
    ): PrivacyMigrationNetworkStatus = withContext(Dispatchers.IO) {
        val response = postRpc(
            url = url,
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", System.currentTimeMillis())
                .put("method", "octra_pvacMigrationStatus")
                .put("params", JSONArray().put(address)),
            connectTimeoutMs = 10_000,
            readTimeoutMs = 20_000,
        )
        response.optJSONObject("error")?.let { error ->
            throw IllegalStateException(
                error.optString("message", error.optString("reason", "Privacy migration status is unavailable.")),
            )
        }
        val result = response.optJSONObject("result")
            ?: throw IllegalStateException("Privacy migration status is unavailable.")
        val replay = result.optJSONObject("legacy_public_replay") ?: JSONObject()
        val replayReason = replay.optString("reason")
        val resultReason = result.optString("reason")
        val auditClass = replay.optString("audit_class", result.optString("audit_class")).ifBlank {
            if (
                replayReason == "legacy history needs hidden witness migration" ||
                resultReason == "legacy history needs hidden witness migration"
            ) {
                "hidden_witness"
            } else {
                ""
            }
        }
        val publicRaw = listOf("public_net_raw", "public_net")
            .firstNotNullOfOrNull { key ->
                replay.opt(key)
                    ?.takeUnless { it == JSONObject.NULL }
                    ?.toString()
                    ?.toLongOrNull()
            }
        PrivacyMigrationNetworkStatus(
            auditClass = auditClass,
            reason = replayReason.ifBlank { resultReason },
            cipherClass = result.optString("cipher_class"),
            canPublicMigrate = replay.optBoolean("can_public_migrate", false) && publicRaw != null,
            publicNetRaw = publicRaw,
            commitmentNet = replay.optString("commitment_net").ifBlank { null },
        )
    }

    suspend fun fetchPrivacyHistory(
        address: String,
        limit: Int = 8,
        url: String = currentUrl(),
    ): List<TransactionHistoryItem> = fetchRecentActivityPage(address, limit, url).privacyTransactions

    suspend fun searchPrivacyHistory(
        address: String,
        resultLimit: Int = 3,
        url: String = currentUrl(),
    ): List<TransactionHistoryItem> = withContext(Dispatchers.IO) {
        fetchConfirmedAccountTransactions(
            address = address,
            resultLimit = resultLimit.coerceIn(1, PrivacyHistorySearchResultLimit),
            queryLimit = RecentActivitySnapshotLimit,
            maxQueryLimit = PrivacyHistorySearchQueryLimit,
            url = url,
        ) { transaction ->
            transaction.opType.lowercase() in PrivacyOperationTypes
        }
    }

    suspend fun fetchStealthOutputs(url: String = currentUrl()): JSONArray = withContext(Dispatchers.IO) {
        // Fetch from recent epochs only to avoid byte limit (param = from_epoch).
        // Start from headEpoch - StealthOutputEpochWindow, paginate via next_before_id if has_more.
        val headEpoch = runCatching {
            val statsResponse = postRpc(
                url = url,
                payload = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", System.currentTimeMillis())
                    .put("method", "node_stats")
                    .put("params", JSONArray()),
                connectTimeoutMs = 10_000,
                readTimeoutMs = 15_000,
            )
            statsResponse.optJSONObject("result")?.optJSONArray("latest_epochs")?.optLong(0) ?: 0L
        }.getOrDefault(0L)

        val fromEpoch = if (headEpoch > StealthOutputEpochWindow) headEpoch - StealthOutputEpochWindow else 0L
        val allOutputs = JSONArray()
        var beforeId: Long? = null
        var hasMore = true
        var pages = 0

        while (hasMore && pages < StealthOutputMaxPages) {
            val params = JSONArray().put(fromEpoch)
            beforeId?.let { params.put(it) }
            val response = postRpc(
                url = url,
                payload = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", System.currentTimeMillis())
                    .put("method", "octra_stealthOutputs")
                    .put("params", params),
                connectTimeoutMs = 20_000,
                readTimeoutMs = 120_000,
            )
            response.optJSONObject("error")?.let { error ->
                val msg = error.optString("message", "Unable to scan claimable transfers.")
                if (pages == 0) throw IllegalStateException(msg)
                return@withContext allOutputs // partial result OK
            }
            val result = response.optJSONObject("result") ?: break
            val outputs = result.optJSONArray("outputs") ?: JSONArray()
            for (i in 0 until outputs.length()) allOutputs.put(outputs.get(i))
            hasMore = result.optBoolean("has_more", false)
            beforeId = if (result.isNull("next_before_id")) null else result.optLong("next_before_id")
            pages++
        }
        allOutputs
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
        matchesRegisteredKey: suspend (String) -> Boolean = {
            PrivacyKeyEncoding.matches(it, pvacPublicKey)
        },
    ) = withContext(Dispatchers.IO) {
        val pvacPublicKeyBase64 = Base64.encodeToString(pvacPublicKey, Base64.NO_WRAP)
        val publicKeyHash = MessageDigest.getInstance("SHA-256").digest(pvacPublicKey).toHex()
        if (registeredPvacKeys.contains(url, wallet.address, pvacPublicKey)) return@withContext

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
        if (existing.isNotBlank() && matchesRegisteredKey(existing)) {
            registeredPvacKeys.remember(url, wallet.address, pvacPublicKey)
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
            connectTimeoutMs = 30_000,
            readTimeoutMs = 110_000,
        )
        val errorMessage = response.optJSONObject("error")?.optString("message").orEmpty()
        if (errorMessage.isNotBlank() && !errorMessage.contains("already registered", ignoreCase = true)) {
            throw IllegalStateException(errorMessage)
        }
        registeredPvacKeys.remember(url, wallet.address, pvacPublicKey)
    }

    fun rememberVerifiedPvacRegistration(
        address: String,
        pvacPublicKey: ByteArray,
        url: String = currentUrl(),
    ) {
        registeredPvacKeys.remember(url, address, pvacPublicKey)
    }

    fun invalidatePvacRegistration(
        address: String,
        url: String? = null,
    ) {
        registeredPvacKeys.invalidate(address, url)
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

    suspend fun submitPrivacyKeySwitch(
        wallet: Wallet,
        feeOu: Long,
        encryptedData: String,
        newPublicKey: ByteArray,
        url: String = currentUrl(),
    ): SendResult = withContext(Dispatchers.IO) {
        val keyHash = MessageDigest.getInstance("SHA-256")
            .digest(newPublicKey)
            .take(8)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        val snapshot = fetchBalanceStrict(wallet.address, url)
        val transaction = createSignedTransactionRaw(
            wallet = wallet,
            recipientAddress = wallet.address,
            amountRaw = "0",
            nonce = snapshot.nonce + 1,
            message = "encryption key switch | new_key:$keyHash",
            feeOu = feeOu,
            opType = "key_switch",
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
        response.optJSONObject("error")?.let { error ->
            return@withContext SendResult(
                success = false,
                error = error.optString("message", error.optString("reason", "Privacy key switch failed.")),
                finality = "rejected",
            )
        }
        val result = response.optJSONObject("result") ?: JSONObject()
        val status = result.optString("status").lowercase()
        val hash = result.optString("tx_hash", result.optString("hash")).ifBlank { null }
        if (status == "rejected") {
            SendResult(false, error = result.optString("reason", "Privacy key switch was rejected."), finality = status)
        } else if (status == "accepted" || hash != null) {
            registeredPvacKeys.invalidate(wallet.address)
            SendResult(true, hash = hash, finality = "accepted")
        } else {
            SendResult(false, error = "Unknown Privacy key switch response.")
        }
    }

    suspend fun fetchTransactionHistory(address: String, limit: Int = 8, url: String = currentUrl()): List<TransactionHistoryItem> =
        fetchRecentActivityPage(address, limit, url).publicTransactions

    suspend fun fetchRecentActivityPage(
        address: String,
        limit: Int = 8,
        url: String = currentUrl(),
    ): RecentActivityPage = withContext(Dispatchers.IO) {
        val transactions = fetchRecentAccountActivitySnapshot(address, limit, url)
        val (privacy, public) = transactions.partition {
            it.opType.lowercase() in PrivacyOperationTypes
        }
        RecentActivityPage(
            publicTransactions = public,
            privacyTransactions = privacy,
            sourceCount = transactions.size,
        )
    }

    private fun fetchRecentAccountActivitySnapshot(
        address: String,
        limit: Int,
        url: String,
    ): List<TransactionHistoryItem> {
        val requestLimit = limit.coerceIn(1, AccountHistoryQueryLimit)
        val cacheKey = "${url.trimEnd('/')}|${address.lowercase()}"
        fun cachedSnapshot(): List<TransactionHistoryItem>? {
            val cached = recentActivityCache[cacheKey] ?: return null
            return cached
                .takeIf {
                    it.expiresAtMs > System.currentTimeMillis() &&
                        it.requestedLimit >= requestLimit
                }
                ?.transactions
                ?.take(requestLimit)
        }
        cachedSnapshot()?.let { return it }

        val requestLock = recentActivityLocks.getOrPut(cacheKey) { Any() }
        return synchronized(requestLock) {
            cachedSnapshot()?.let { return@synchronized it }
            val response = postRpc(
                url = url,
                payload = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 1)
                    .put("method", "octra_account")
                    .put("params", JSONArray().put(address).put(requestLimit)),
            )
            val recent = response.optJSONObject("result")?.optJSONArray("recent_txs") ?: JSONArray()
            val candidates = buildList {
                for (index in 0 until minOf(recent.length(), requestLimit)) {
                    val item = recent.optJSONObject(index) ?: continue
                    val hash = item.optString("hash", item.optString("tx_hash"))
                    if (hash.isBlank()) continue
                    add(hash to item)
                }
            }.distinctBy { it.first }
            val detailsByHash = fetchTransactionDetailsBatch(
                hashes = candidates.map { it.first },
                address = address,
                url = url,
            )
            val transactions = candidates.map { (hash, item) ->
                detailsByHash[hash] ?: run {
                    val from = item.optString("from", "unknown")
                    val status = item.optString("status").lowercase()
                    val stageStatus = item.optString("stage_status").lowercase()
                    TransactionHistoryItem(
                        hash = hash,
                        from = from,
                        to = item.optString("to", item.optString("to_", "unknown")),
                        amount = parseAmount(item.opt("amount_raw"), item.opt("amount")),
                        timestamp = item.optDouble("timestamp", 0.0),
                        status = when {
                            status == "rejected" || status == "dropped" -> status
                            status.isNotBlank() -> status
                            stageStatus.isNotBlank() -> stageStatus
                            else -> "confirmed"
                        },
                        type = if (from.equals(address, ignoreCase = true)) "sent" else "received",
                        opType = item.optString("op_type", "unknown"),
                        message = item.optString("message").ifBlank { null },
                    )
                }
            }.sortedByDescending { it.timestamp }
            if (recentActivityCache.size >= RecentActivityCacheLimit) {
                recentActivityCache.entries
                    .minByOrNull { it.value.expiresAtMs }
                    ?.key
                    ?.let(recentActivityCache::remove)
            }
            recentActivityCache[cacheKey] = RecentActivityCacheEntry(
                requestedLimit = requestLimit,
                expiresAtMs = System.currentTimeMillis() + RecentActivityCacheMillis,
                transactions = transactions,
            )
            transactions
        }
    }

    private suspend fun fetchConfirmedAccountTransactions(
        address: String,
        resultLimit: Int,
        queryLimit: Int,
        maxQueryLimit: Int = queryLimit,
        url: String,
        include: (TransactionHistoryItem) -> Boolean,
    ): List<TransactionHistoryItem> {
        val matches = mutableListOf<TransactionHistoryItem>()
        val seenHashes = mutableSetOf<String>()
        val initialQueryLimit = queryLimit.coerceAtLeast(HistoryDetailsBatchSize)
        val queryCeiling = maxQueryLimit.coerceAtLeast(initialQueryLimit)
        var requestedQueryLimit = initialQueryLimit
        while (matches.size < resultLimit) {
            val json = postRpc(
                url = url,
                payload = JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", 1)
                    .put("method", "octra_account")
                    .put("params", JSONArray().put(address).put(requestedQueryLimit)),
            )
            val recent = json.optJSONObject("result")?.optJSONArray("recent_txs") ?: JSONArray()
            val candidates = buildList {
                for (index in 0 until recent.length()) {
                    val candidate = recent.optJSONObject(index) ?: continue
                    val hash = candidate.optString("hash", candidate.optString("tx_hash"))
                    if (hash.isNotBlank() && seenHashes.add(hash)) add(candidate)
                }
            }
            for (batch in candidates.chunked(HistoryDetailsBatchSize)) {
                val detailsByHash = fetchTransactionDetailsBatch(
                    hashes = batch.mapNotNull { item ->
                        item.optString("hash", item.optString("tx_hash")).ifBlank { null }
                    },
                    address = address,
                    url = url,
                )
                val details = batch.mapNotNull { item ->
                    val hash = item.optString("hash", item.optString("tx_hash"))
                    if (hash.isBlank()) {
                        null
                    } else {
                        detailsByHash[hash] ?: TransactionHistoryItem(
                            hash = hash,
                            from = item.optString("from", "unknown"),
                            to = item.optString("to", item.optString("to_", "unknown")),
                            amount = parseAmount(item.opt("amount_raw"), item.opt("amount")),
                            timestamp = item.optDouble("timestamp", 0.0),
                            status = "confirmed",
                            type = if (item.optString("from").equals(address, ignoreCase = true)) "sent" else "received",
                            opType = item.optString("op_type", "unknown"),
                        )
                    }
                }
                matches += details.filter(include)
                if (matches.size >= resultLimit) break
            }
            if (
                matches.size >= resultLimit ||
                recent.length() < requestedQueryLimit ||
                requestedQueryLimit >= queryCeiling
            ) {
                break
            }
            requestedQueryLimit = (requestedQueryLimit + initialQueryLimit)
                .coerceAtMost(queryCeiling)
        }
        return matches
            .sortedByDescending { it.timestamp }
            .take(resultLimit)
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

    suspend fun fetchRecommendedFeeStrict(
        opType: String,
        url: String = currentUrl(),
    ): Long = withContext(Dispatchers.IO) {
        val json = postRpc(
            url = url,
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", System.currentTimeMillis())
                .put("method", "octra_recommendedFee")
                .put("params", JSONArray().put(opType)),
        )
        json.optJSONObject("error")?.let { error ->
            throw IllegalStateException(error.optString("message", "Unable to read the recommended fee."))
        }
        val recommended = json.optJSONObject("result")?.opt("recommended")
        when (recommended) {
            is Number -> recommended.toLong()
            is String -> recommended.toLongOrNull()
            else -> null
        }?.takeIf { it > 0L }
            ?: throw IllegalStateException("The network returned an invalid recommended fee.")
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
        val rpcUrl = currentUrl()
        val cleanLabel = label.trim()
            .lowercase()
            .removeSuffix(".oct")
            .removeSuffix(".octra")
            .removeSuffix(".id")
        if (!isValidIdentityPath(cleanLabel)) return@withContext null
        val method = if (cleanLabel.contains('.')) "resolve_name" else "resolve"
        val resolved = identityProviderView(
            provider = provider,
            method = method,
            params = JSONArray().put(cleanLabel),
            walletAddress = walletAddress,
            rpcUrl = rpcUrl,
        ) ?: if (!cleanLabel.contains('.')) {
            null
        } else {
            identityProviderView(
                provider = provider,
                method = "resolve",
                params = JSONArray().put(cleanLabel),
                walletAddress = walletAddress,
                rpcUrl = rpcUrl,
            )
        }
        resolved?.takeIf { it.startsWith("oct") && it.length == 47 }
    }

    suspend fun walletIdentityName(
        provider: OctraIdentityProvider,
        walletAddress: String,
    ): String? = withContext(Dispatchers.IO) {
        val rpcUrl = currentUrl()
        if (!walletAddress.startsWith("oct") || walletAddress.length != 47) return@withContext null
        val primary = identityProviderView(
            provider = provider,
            method = "primary_of",
            params = JSONArray().put(walletAddress),
            walletAddress = walletAddress,
            rpcUrl = rpcUrl,
        )?.toIdentityDomain()
        if (primary != null) return@withContext primary

        val total = identityProviderView(
            provider = provider,
            method = "owner_total",
            params = JSONArray().put(walletAddress),
            walletAddress = walletAddress,
            rpcUrl = rpcUrl,
        )?.toIntOrNull() ?: 0
        if (total <= 0) return@withContext null

        identityProviderView(
            provider = provider,
            method = "owner_key_at",
            params = JSONArray().put(walletAddress).put(0),
            walletAddress = walletAddress,
            rpcUrl = rpcUrl,
        )?.toIdentityDomain()
    }

    fun fetchCircleInfo(circleId: String): JSONObject {
        val cleanCircleId = circleId.trim()
        require(cleanCircleId.startsWith("oct") && cleanCircleId.length == 47) { "Enter a valid Octra circle address." }
        val json = postCircleReadRpc(
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
        val result = json.optJSONObject("result") ?: throw IllegalStateException("Circle info was not found.")
        result.optString("circle_id").trim().takeIf(String::isNotBlank)?.let { returnedCircleId ->
            require(returnedCircleId == cleanCircleId) { "Circle info address mismatch." }
        }
        return result
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

    fun circleProgramInfo(address: String): JSONObject {
        val cleanAddress = address.trim()
        require(cleanAddress.startsWith("oct") && cleanAddress.length == 47) { "Enter a valid Octra circle address." }
        val response = postCircleReadRpc(
            url = currentUrl(),
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", "octra_circleProgramInfo")
                .put("params", JSONArray().put(cleanAddress)),
        )
        val error = response.optJSONObject("error")
        if (error == null) {
            return response.optJSONObject("result") ?: throw IllegalStateException("Circle program info was not found.")
        }
        if (!isMethodNotFound(error.optString("message"))) {
            throw IllegalStateException(error.optString("message").ifBlank { "Circle program info lookup failed." })
        }
        return fetchCircleInfo(cleanAddress)
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
            val direct = postCircleReadRpc(
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

        val circleView = postCircleReadRpc(
            url = rpcUrl,
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", "octra_circleView")
                .put(
                    "params",
                    JSONArray()
                        .put(cleanAddress)
                        .put(cleanMethod)
                        .put(params)
                        .put(JSONObject.NULL)
                        .put(false),
                ),
        )
        val circleViewError = circleView.optJSONObject("error")
        if (circleViewError == null) return circleView.opt("result")
        if (!isMethodNotFound(circleViewError.optString("message"))) {
            throw IllegalStateException(circleViewError.optString("message").ifBlank { "Circle program view failed." })
        }

        return executeContractCallView(rpcUrl, cleanAddress, cleanMethod, params)
    }

    private fun executeContractCallView(
        rpcUrl: String,
        cleanAddress: String,
        cleanMethod: String,
        params: JSONArray,
    ): Any? {
        val fallback = postCircleReadRpc(
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

        val direct = postCircleReadRpc(
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

        val rawResponses = postCircleReadRpcBatch(rpcUrl, rawBatch)
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
            val fallbackResponses = jsonRpcResponsesById(postCircleReadRpcBatch(rpcUrl, fallbackBatch))
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
        when (cleanMethod) {
            "octra_circleProgramInfo" -> {
                val address = params.optString(0)
                require(params.length() == 1 && address.startsWith("oct") && address.length == 47) {
                    "Enter a valid Circle program address."
                }
            }
            "octra_circleView" -> {
                val address = params.optString(0)
                val viewMethod = params.optString(1)
                require(params.length() in 3..5 && address.startsWith("oct") && address.length == 47) {
                    "Enter a valid Circle view address."
                }
                require(viewMethod.matches(Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$"))) { "Enter a valid view method." }
                require(params.optJSONArray(2) != null) { "Circle view params must be an array." }
            }
        }
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
                .put("method", "octra_circleView")
                .put("params", JSONArray().put(address).put(method).put(params).put(JSONObject.NULL).put(false))
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
        val json = postCircleReadRpc(
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
        val returnedCircleId = result.optString("circle_id", cleanCircleId).trim()
        require(returnedCircleId == cleanCircleId) { "Circle asset address mismatch." }
        val canonicalPath = normalizeCircleAssetPath(result.optString("canonical_path", cleanPath))
        require(canonicalPath == cleanPath) { "Circle asset path mismatch." }
        require(result.has("body_b64") && !result.isNull("body_b64")) { "Circle asset body is missing." }
        val bodyB64 = result.optString("body_b64")
        require(bodyB64.length % 4 == 0 && bodyB64.matches(Regex("^[A-Za-z0-9+/]*={0,2}$"))) {
            "Circle asset body is not valid Base64."
        }
        val body = Base64.decode(bodyB64, Base64.DEFAULT)
        require(Base64.encodeToString(body, Base64.NO_WRAP) == bodyB64) { "Circle asset body is not canonical Base64." }
        val declaredSize = result.opt("size_bytes")
            ?.toString()
            ?.takeIf(String::isNotBlank)
            ?.toLongOrNull()
            ?: body.size.toLong()
        require(declaredSize == body.size.toLong()) { "Circle asset size mismatch." }
        return CircleAsset(
            circleId = returnedCircleId,
            canonicalPath = canonicalPath,
            contentType = result.optString("content_type", "application/octet-stream").ifBlank { "application/octet-stream" },
            body = body,
            sizeBytes = body.size.toLong(),
        )
    }

    fun fetchSealedCircleAsset(
        circleId: String,
        path: String,
        passphrase: String,
        wallet: Wallet? = null,
    ): CircleAsset {
        val cleanCircleId = circleId.trim()
        val cleanPath = normalizeCircleAssetPath(path)
        require(cleanCircleId.startsWith("oct") && cleanCircleId.length == 47) { "Enter a valid Octra circle address." }
        require(passphrase.isNotBlank()) { "Sealed read passphrase is required." }

        val result = fetchCircleCiphertext(cleanCircleId, cleanPath, wallet)
        val ciphertextB64 = result.optString("ciphertext_b64")
        val keyId = result.optString("key_id")
        val plaintextHash = result.optString("plaintext_hash")
        require(ciphertextB64.isNotBlank() && keyId.isNotBlank() && plaintextHash.isNotBlank()) {
            "Sealed asset metadata is incomplete."
        }
        require(keyId.length <= 128 && keyId.none(Char::isISOControl)) { "Sealed asset key ID is invalid." }
        require(plaintextHash.matches(Regex("^[0-9a-fA-F]{64}$"))) { "Sealed asset plaintext hash is invalid." }
        require(ciphertextB64.length % 4 == 0 && ciphertextB64.matches(Regex("^[A-Za-z0-9+/]*={0,2}$"))) {
            "Sealed asset ciphertext is not valid Base64."
        }
        val body = decryptSealedCircleBytes(
            circleId = cleanCircleId,
            keyId = keyId,
            passphrase = passphrase,
            ciphertextB64 = ciphertextB64,
            plaintextHash = plaintextHash,
        )
        val returnedCircleId = result.optString("circle_id", cleanCircleId).trim()
        require(returnedCircleId == cleanCircleId) { "Circle asset address mismatch." }
        val canonicalPath = normalizeCircleAssetPath(result.optString("canonical_path", cleanPath))
        require(canonicalPath == cleanPath) { "Circle asset path mismatch." }
        return CircleAsset(
            circleId = returnedCircleId,
            canonicalPath = canonicalPath,
            contentType = result.optString("content_type", "application/octet-stream").ifBlank { "application/octet-stream" },
            body = body,
            sizeBytes = body.size.toLong(),
        )
    }

    private fun fetchCircleCiphertext(circleId: String, path: String, wallet: Wallet?): JSONObject {
        val resourceKey = circleResourceKey(circleId, path)
        if (wallet != null) {
            fetchAuthenticatedCircleCiphertext(
                method = "octra_circleAssetCiphertextByResourceKeyAuth",
                circleId = circleId,
                value = resourceKey,
                operation = "octra_circle_asset_ciphertext_by_resource_key",
                subject = "resource_key|$resourceKey",
                wallet = wallet,
            )?.let { return it }
            fetchAuthenticatedCircleCiphertext(
                method = "octra_circleAssetCiphertextAuth",
                circleId = circleId,
                value = path,
                operation = "octra_circle_asset_ciphertext",
                subject = "path|$path",
                wallet = wallet,
            )?.let { return it }
        }

        try {
            val json = postCircleReadRpc(
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
            return json.optJSONObject("result") ?: throw IllegalStateException("Circle sealed asset was not found.")
        } catch (failure: Exception) {
            if (!shouldFallbackCircleLookup(failure)) throw failure
        }

        val json = postCircleReadRpc(
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

    private fun fetchAuthenticatedCircleCiphertext(
        method: String,
        circleId: String,
        value: String,
        operation: String,
        subject: String,
        wallet: Wallet,
    ): JSONObject? = try {
        val (_, publicKey) = signingMaterial(wallet)
        val signature = signMessage(wallet, "$operation|$circleId|${wallet.address}|$subject")
        val json = postCircleReadRpc(
            url = currentUrl(),
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", method)
                .put(
                    "params",
                    JSONArray()
                        .put(circleId)
                        .put(value)
                        .put(wallet.address)
                        .put(Base64.encodeToString(publicKey, Base64.NO_WRAP))
                        .put(signature),
                ),
        )
        json.optJSONObject("error")?.let { error ->
            throw IllegalStateException(error.optString("message").ifBlank { "Authenticated Circle asset lookup failed." })
        }
        json.optJSONObject("result") ?: throw IllegalStateException("Circle sealed asset was not found.")
    } catch (failure: Exception) {
        if (shouldFallbackCircleLookup(failure)) null else throw failure
    }

    private fun decryptSealedCircleBytes(
        circleId: String,
        keyId: String,
        passphrase: String,
        ciphertextB64: String,
        plaintextHash: String,
    ): ByteArray {
        val envelope = Base64.decode(ciphertextB64, Base64.DEFAULT)
        require(Base64.encodeToString(envelope, Base64.NO_WRAP) == ciphertextB64) {
            "Sealed asset ciphertext is not canonical Base64."
        }
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
        return OctraIdentityName.isValidPath(value)
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

    private fun identityProviderView(
        provider: OctraIdentityProvider,
        method: String,
        params: JSONArray,
        walletAddress: String,
        rpcUrl: String,
    ): String? = contractCall(
        contractAddress = provider.contractAddress,
        method = method,
        params = params,
        walletAddress = walletAddress,
        url = rpcUrl,
    )

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
        val cacheKey = "$url|${address.lowercase()}|${hash.lowercase()}"
        transactionDetailsCache[cacheKey]?.let { return it }
        val json = postRpc(
            url = url,
            payload = JSONObject()
                .put("jsonrpc", "2.0")
                .put("id", 1)
                .put("method", "octra_transaction")
                .put("params", JSONArray().put(hash)),
        )
        val data = json.optJSONObject("result") ?: throw IllegalStateException("Empty transaction result")
        return parseTransactionDetails(data, hash, address).also { transaction ->
            cacheTransactionDetails(cacheKey, transaction)
        }
    }

    private fun fetchTransactionDetailsBatch(
        hashes: List<String>,
        address: String,
        url: String,
    ): Map<String, TransactionHistoryItem> {
        val uniqueHashes = hashes.distinct()
        val details = mutableMapOf<String, TransactionHistoryItem>()
        val missing = mutableListOf<String>()
        uniqueHashes.forEach { hash ->
            val cacheKey = "$url|${address.lowercase()}|${hash.lowercase()}"
            transactionDetailsCache[cacheKey]?.let { details[hash] = it } ?: missing.add(hash)
        }
        if (missing.isEmpty()) return details

        val payload = JSONArray()
        missing.forEachIndexed { index, hash ->
            payload.put(
                JSONObject()
                    .put("jsonrpc", "2.0")
                    .put("id", index + 1)
                    .put("method", "octra_transaction")
                    .put("params", JSONArray().put(hash)),
            )
        }
        val responsesById = runCatching {
            jsonRpcResponsesById(postRpcBatch(url, payload))
        }.getOrNull()
        if (responsesById != null) {
            missing.forEachIndexed { index, hash ->
                val data = responsesById[index + 1]?.optJSONObject("result") ?: return@forEachIndexed
                runCatching { parseTransactionDetails(data, hash, address) }
                    .getOrNull()
                    ?.let { transaction ->
                        details[hash] = transaction
                        cacheTransactionDetails(
                            "$url|${address.lowercase()}|${hash.lowercase()}",
                            transaction,
                        )
                    }
            }
        }

        missing.filterNot(details::containsKey).forEach { hash ->
            runCatching { fetchTransactionDetails(hash, address, url) }
                .getOrNull()
                ?.let { details[hash] = it }
        }
        return details
    }

    private fun parseTransactionDetails(
        data: JSONObject,
        fallbackHash: String,
        address: String,
    ): TransactionHistoryItem {
        val from = data.optString("from")
        val to = data.optString("to", data.optString("to_"))
        val txHash = data.optString("tx_hash", fallbackHash)
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

    private fun cacheTransactionDetails(cacheKey: String, transaction: TransactionHistoryItem) {
        if (transaction.status == "confirmed") {
            if (transactionDetailsCache.size >= TransactionDetailsCacheLimit) {
                transactionDetailsCache.keys.firstOrNull()?.let(transactionDetailsCache::remove)
            }
            transactionDetailsCache[cacheKey] = transaction
        }
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

    private fun postCircleReadRpc(
        url: String,
        payload: JSONObject,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 10_000,
    ): JSONObject = withCircleReadRetry {
        postRpc(url, payload, connectTimeoutMs, readTimeoutMs).also { response ->
            response.optJSONObject("error")?.let { error ->
                val failure = IllegalStateException(error.optString("message").ifBlank { "Circle RPC failed." })
                if (isTransientCircleFailure(failure)) throw failure
            }
        }
    }

    private fun postCircleReadRpcBatch(url: String, payload: JSONArray): JSONArray = withCircleReadRetry {
        postRpcBatch(url, payload).also { responses ->
            for (index in 0 until responses.length()) {
                responses.optJSONObject(index)?.optJSONObject("error")?.let { error ->
                    val failure = IllegalStateException(error.optString("message").ifBlank { "Circle RPC failed." })
                    if (isTransientCircleFailure(failure)) throw failure
                }
            }
        }
    }

    private fun postRpcValue(
        url: String,
        payload: Any,
        connectTimeoutMs: Int = 10_000,
        readTimeoutMs: Int = 10_000,
    ): Any {
        val rpcUrl = URL(octraRpcEndpoint(url))
        val connection = (rpcUrl.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", PoctraUserAgent)
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
            DefaultDevnetUrl, LegacyProxyDevnetUrl, LegacyDefaultDevnetUrl -> defaultDevnetNetwork()
            else -> RpcNetwork(
                id = "rpc-legacy-${kotlin.math.abs(url.hashCode())}",
                name = if (type == "devnet") "Custom Devnet" else "Custom Mainnet",
                url = url,
                networkType = type,
                explorerUrl = if (type == "devnet") DefaultDevnetExplorerUrl else DefaultMainnetExplorerUrl,
            )
        }
    }

    private fun defaultRpcNetworks(): List<RpcNetwork> =
        listOf(defaultMainnetNetwork(), defaultDevnetNetwork())

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
        network.id == LegacyPublicDevnetId ||
            network.url == LegacyProxyDevnetUrl ||
            network.url == LegacyDefaultDevnetUrl -> defaultDevnetNetwork()
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
            activeId == LegacyPublicDevnetId ||
            activeUrl.isBlank() ||
            activeUrl == DefaultDevnetUrl ||
            activeUrl == LegacyProxyDevnetUrl ||
            activeUrl == LegacyDefaultDevnetUrl
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
        "deploy", "deploy_circle", "upgrade" -> 200000L
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
        const val DefaultDevnetUrl = "https://devnet.octrascan.io/rpc"
        private const val LegacyProxyDevnetUrl = "https://rpc.xme.my.id/octra-devnet"
        private const val StealthOutputEpochWindow = 200_000L
        private const val StealthOutputMaxPages = 10
        private const val LegacyDefaultDevnetUrl = "http://165.227.225.79:8080"
        const val DefaultMainnetExplorerUrl = "https://octrascan.io"
        const val DefaultDevnetExplorerUrl = "https://devnet.octrascan.io"
        private const val PoctraUserAgent = "poctra-android"
        private const val DefaultMainnetId = "octra-mainnet"
        private const val DefaultDevnetId = "octra-devnet"
        private const val LegacyPublicDevnetId = "octra-devnet-octrascan"
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
        private const val RecentActivitySnapshotLimit = 8
        private const val PrivacyHistorySearchResultLimit = 3
        private const val PrivacyHistorySearchQueryLimit = 24
        private const val RecentActivityCacheMillis = 30_000L
        private const val RecentActivityCacheLimit = 16
        private const val OctraTokenLookupBatchSize = 4
        private const val AccountHistoryQueryLimit = 100
        private const val TransactionDetailsCacheLimit = 512
        private val PrivacyOperationTypes = setOf("encrypt", "decrypt", "stealth", "claim", "key_switch")
        private const val CircleReadCacheMillis = 8_000L
        private const val MaxCircleReadCacheEntries = 512
        private val CircleReadRpcMethods = setOf(
            "octra_callProgramView",
            "octra_circleProgramInfo",
            "octra_circleView",
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
            "octra_circleProgramInfo",
            "octra_circleView",
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

internal fun octraRpcEndpoint(baseUrl: String): String {
    val clean = baseUrl.trim().trimEnd('/')
    require(clean.isNotBlank()) { "RPC URL is required." }
    return if (clean.endsWith("/rpc")) clean else "$clean/rpc"
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

internal fun publicCircleDeployPayload(): String =
    "{\"runtime\":\"octb\",\"privacy_class\":\"public\",\"browser_mode\":\"gateway_allowed\",\"resource_mode\":\"public_resources\",\"code_b64\":null,\"policy_hash\":null,\"members_root\":null,\"export_policy\":null,\"limits\":{\"max_stable_bytes\":\"33554432\",\"max_assets_bytes\":\"33554432\",\"max_inline_value\":\"65536\",\"max_wasm_bytes\":\"33554432\"}}"

internal fun deriveCircleDeployId(deployer: String, nonce: Long, payload: String): String {
    require(deployer.startsWith("oct") && deployer.length == 47) { "Enter a valid Circle deployer address." }
    require(nonce > 0L) { "Circle deployment nonce must be positive." }
    val payloadHash = domainHash(
        tag = "octra:circle_deploy_payload:v1",
        parts = listOf(payload.toByteArray(Charsets.UTF_8)),
    ).toHexString()
    val nonceBytes = ByteArray(Long.SIZE_BYTES) { index ->
        ((nonce ushr ((Long.SIZE_BYTES - index - 1) * 8)) and 0xff).toByte()
    }
    val seed = domainHash(
        tag = "octra:circle_deploy_id:v1",
        parts = listOf(
            deployer.toByteArray(Charsets.UTF_8),
            nonceBytes,
            payloadHash.toByteArray(Charsets.UTF_8),
        ),
    )
    val encoded = Base58.encode(seed)
    val suffix = when {
        encoded.length >= 44 -> encoded.take(44)
        encoded.isEmpty() -> "1".repeat(44)
        else -> buildString(44) {
            append(encoded)
            var index = 0
            while (length < 44) {
                append(encoded[index % encoded.length])
                index += 1
            }
        }.take(44)
    }
    return "oct$suffix"
}

internal fun validateCircleWritePath(rawPath: String): String {
    val raw = rawPath.trim()
    require(raw.startsWith('/')) { "Circle asset path must start with /." }
    require(raw.length <= 512) { "Circle asset path is too long." }
    require(raw.none { it == '\u0000' || it == '\\' || it == '?' || it == '#' || it.code < 0x20 }) {
        "Circle asset path contains unsupported characters."
    }
    val lower = raw.lowercase()
    require("%2f" !in lower && "%5c" !in lower && "%00" !in lower && "%25" !in lower) {
        "Circle asset path contains an encoded path separator."
    }
    val segments = raw.split('/').filter(String::isNotEmpty)
    require(segments.none { it == "." || it == ".." || "%2e" in it.lowercase() }) {
        "Circle asset path traversal is not allowed."
    }
    return if (segments.isEmpty()) "/" else "/${segments.joinToString("/")}"
}

internal fun validateCircleContentType(rawContentType: String): String {
    val contentType = rawContentType.trim().lowercase()
    require(contentType.length in 3..129 && CircleContentTypeRegex.matches(contentType)) {
        "Enter a valid Circle asset content type."
    }
    return contentType
}

internal fun validateCircleAssetBody(rawBodyBase64: String): String {
    val bodyBase64 = rawBodyBase64.trim()
    require(bodyBase64.length <= MaxCircleAssetBodyBase64Chars) {
        "Circle browser uploads are limited to 1 MiB per asset."
    }
    require(bodyBase64.length % 4 == 0 && (bodyBase64.isEmpty() || CircleBase64Regex.matches(bodyBase64))) {
        "Circle asset body must be canonical Base64."
    }
    val bytes = runCatching { org.bouncycastle.util.encoders.Base64.decode(bodyBase64) }
        .getOrElse { throw IllegalArgumentException("Circle asset body must be valid Base64.") }
    require(String(org.bouncycastle.util.encoders.Base64.encode(bytes), Charsets.US_ASCII) == bodyBase64) {
        "Circle asset body must use canonical Base64 padding."
    }
    require(bytes.size <= MaxCircleAssetBodyBytes) {
        "Circle browser uploads are limited to 1 MiB per asset."
    }
    return bodyBase64
}

internal fun circleAssetFeeOu(base64Length: Int): Long {
    require(base64Length >= 0) { "Circle asset size cannot be negative." }
    val decodedUpperBound = (base64Length.toLong() * 3L + 3L) / 4L
    return when {
        decodedUpperBound <= 4_096L -> 5_000L
        decodedUpperBound <= 16_384L -> 10_000L
        decodedUpperBound <= 32_768L -> 20_000L
        decodedUpperBound <= 131_072L -> 40_000L
        decodedUpperBound <= 524_288L -> 80_000L
        decodedUpperBound <= 2_097_152L -> 160_000L
        decodedUpperBound <= 8_388_608L -> 320_000L
        else -> 640_000L
    }
}

private fun domainHash(tag: String, parts: List<ByteArray>): ByteArray {
    val bytes = ByteArrayOutputStream().apply {
        write(tag.toByteArray(Charsets.UTF_8))
        write(0)
        parts.forEach { part ->
            val size = part.size
            write((size ushr 24) and 0xff)
            write((size ushr 16) and 0xff)
            write((size ushr 8) and 0xff)
            write(size and 0xff)
            write(part)
        }
    }.toByteArray()
    return MessageDigest.getInstance("SHA-256").digest(bytes)
}

private fun ByteArray.toHexString(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

private const val MaxCircleAssetBodyBytes = 1_048_576
private const val MaxCircleAssetBodyBase64Chars = 1_398_104
private val CircleContentTypeRegex = Regex("^[a-z0-9][a-z0-9!#$&^_.+-]{0,63}/[a-z0-9][a-z0-9!#$&^_.+-]{0,63}$")
private val CircleBase64Regex = Regex("^[A-Za-z0-9+/]*={0,2}$")
