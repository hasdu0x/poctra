package com.poctra.wallet.data

import com.poctra.wallet.core.EvmCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL

enum class BridgeDirection {
    OctToWoct,
    WoctToOct,
}

enum class BridgeStep {
    Idle,
    Locking,
    ConfirmingOctra,
    WaitingRelayer,
    Claiming,
    Approving,
    Burning,
    WaitingUnlock,
    Completed,
    Failed,
}

data class BridgeBalances(
    val octRaw: BigInteger = BigInteger.ZERO,
    val ethWei: BigInteger = BigInteger.ZERO,
    val woctRaw: BigInteger = BigInteger.ZERO,
    val octAmount: String = "--",
    val ethAmount: String = "--",
    val woctAmount: String = "--",
)

data class BridgeStatus(
    val isPaused: Boolean? = null,
    val dailyLockRemainingRaw: BigInteger? = null,
    val dailyUnlockRemainingRaw: BigInteger? = null,
    val dailyLockRemaining: String = "--",
    val dailyUnlockRemaining: String = "--",
    val unlockOperator: String = "",
    val error: String? = null,
)

data class BridgeEvmGasEstimate(
    val gasLimit: BigInteger,
    val lowGasPriceWei: BigInteger,
    val marketGasPriceWei: BigInteger,
    val fastGasPriceWei: BigInteger,
    val lowFeeWei: BigInteger,
    val marketFeeWei: BigInteger,
    val fastFeeWei: BigInteger,
    val steps: String,
)

data class BridgeChainHistoryRecord(
    val id: String,
    val direction: BridgeDirection,
    val walletAddress: String,
    val evmAddress: String,
    val amount: String,
    val amountRaw: String,
    val status: String,
    val step: BridgeStep,
    val message: String,
    val octraTxHash: String? = null,
    val evmTxHash: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

data class BridgeLockedEvent(
    val from: String,
    val amountRaw: BigInteger,
    val ethAddress: String,
    val srcNonce: Long,
    val epoch: Long,
    val txHash: String,
)

data class BridgeClaimData(
    val calldata: String,
    val epochId: Long,
    val leafIndex: Int,
)

class BridgeRepository {
    private val crypto = EvmCrypto()
    private var activeOctraRpcUrl = OctraRpcUrl
    private var activeEthereumRpcUrl = EthereumRpcUrl

    fun configure(octraRpcUrl: String = OctraRpcUrl, evmRpcUrl: String = EthereumRpcUrl) {
        activeOctraRpcUrl = octraRpcUrl.trim().trimEnd('/').ifBlank { OctraRpcUrl }
        activeEthereumRpcUrl = evmRpcUrl.trim().ifBlank { EthereumRpcUrl }
    }

    fun ethereumNetwork(): EvmNetwork = EvmNetwork(
        id = "eth-mainnet",
        name = "Ethereum Mainnet",
        chainId = EthChainId,
        rpcUrl = activeEthereumRpcUrl,
        symbol = "ETH",
        explorerUrl = "https://etherscan.io",
        isTestnet = false,
    )

    fun deriveEvmAddress(wallet: Wallet): String = crypto.deriveAddress(wallet)

    fun parseAmount(value: String): BigInteger = EvmCrypto.parseUnits(value, OctDecimals)

    fun formatAmount(value: BigInteger, scale: Int = 6): String = EvmCrypto.formatUnits(value, OctDecimals, scale)

    fun formatEth(value: BigInteger): String = EvmCrypto.formatEther(value)

    suspend fun fetchBalances(wallet: Wallet): BridgeBalances = withContext(Dispatchers.IO) {
        val evmAddress = deriveEvmAddress(wallet)
        val (octRaw, ethWei, woctRaw) = coroutineScope {
            val oct = async { octraBalanceRaw(wallet.address) }
            val eth = async { evmQuantity(activeEthereumRpcUrl, "eth_getBalance", JSONArray().put(evmAddress).put("latest")) }
            val woct = async { erc20BalanceRaw(evmAddress) }
            Triple(oct.await(), eth.await(), woct.await())
        }
        BridgeBalances(
            octRaw = octRaw,
            ethWei = ethWei,
            woctRaw = woctRaw,
            octAmount = formatAmount(octRaw),
            ethAmount = formatEth(ethWei),
            woctAmount = formatAmount(woctRaw),
        )
    }

    suspend fun fetchStatus(wallet: Wallet): BridgeStatus = withContext(Dispatchers.IO) {
        val reads = coroutineScope {
            listOf(
                "is_paused",
                "daily_lock_remaining",
                "daily_unlock_remaining",
                "get_unlock_operator",
            ).associateWith { method ->
                async { runCatching { octraContractCall(method, JSONArray(), wallet.address) } }
            }.mapValues { (_, deferred) -> deferred.await() }
        }
        val firstError = reads.values.firstOrNull { it.isFailure }?.exceptionOrNull()?.message
            ?: reads.values.firstOrNull { it.isFailure }?.exceptionOrNull()?.let { "Bridge status lookup failed." }

        val paused = reads["is_paused"]?.getOrNull()?.toBridgePaused()
        val lockRaw = reads["daily_lock_remaining"]?.getOrNull()?.toRawAmount()
        val unlockRaw = reads["daily_unlock_remaining"]?.getOrNull()?.toRawAmount()
        val unlockOperator = reads["get_unlock_operator"]?.getOrNull().orEmpty()

        BridgeStatus(
            isPaused = paused,
            dailyLockRemainingRaw = lockRaw,
            dailyUnlockRemainingRaw = unlockRaw,
            dailyLockRemaining = lockRaw?.let(::formatAmount) ?: "--",
            dailyUnlockRemaining = unlockRaw?.let(::formatAmount) ?: "--",
            unlockOperator = unlockOperator,
            error = firstError,
        )
    }

    suspend fun waitForLockedEvent(
        octraTxHash: String,
        timeoutMs: Long = 180_000L,
        intervalMs: Long = 3_000L,
    ): BridgeLockedEvent = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        var txEpoch = 0L
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            val result = runCatching {
                octraRpc("octra_transaction", JSONArray().put(octraTxHash)) as? JSONObject
            }.getOrNull()
            if (result != null) {
                val status = result.optString("status").lowercase()
                val stageStatus = result.optString("stage_status").lowercase()
                txEpoch = result.optLong("epoch", txEpoch)
                when {
                    status == "rejected" -> throw IllegalStateException(result.optString("reason", "Octra lock was rejected."))
                    status == "dropped" -> throw IllegalStateException(result.optString("reason", "Octra lock was dropped."))
                    status == "confirmed" ||
                        stageStatus == "confirmed" ||
                        (result.has("epoch") && !result.isNull("epoch")) -> {
                        val locked = runCatching { readLockedEvent(octraTxHash, txEpoch) }.getOrElse { exception ->
                            val message = exception.message.orEmpty().lowercase()
                            if (message.contains("failed") || message.contains("rejected")) throw exception
                            null
                        }
                        if (locked != null) return@withContext locked
                    }
                }
            }
            delay(intervalMs)
        }
        throw IllegalStateException("Lock transaction is still pending. Retry bridge claim after the transaction confirms.")
    }

    suspend fun waitForClaimData(
        epochId: Long,
        recipient: String,
        rawAmount: BigInteger,
        srcNonce: Long,
        timeoutMs: Long = 300_000L,
        intervalMs: Long = 5_000L,
    ): BridgeClaimData = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            val header = runCatching {
                signerRpc("bridgeHeader", JSONArray().put(epochId)) as? JSONObject
            }.getOrNull()
            if (header?.optLong("message_count", 0L)?.let { it > 0L } == true) {
                val claimData = buildClaimData(epochId, recipient, rawAmount, srcNonce)
                if (claimData != null) return@withContext claimData
            }
            delay(intervalMs)
        }
        throw IllegalStateException("Bridge claim data is not ready yet. The lock is safe; retry claim in a few minutes.")
    }

    suspend fun estimateBridgeEvmGas(
        wallet: Wallet,
        direction: BridgeDirection,
        amountRaw: BigInteger,
    ): BridgeEvmGasEstimate = withContext(Dispatchers.IO) {
        val from = deriveEvmAddress(wallet)
        val safeAmount = amountRaw.takeIf { it > BigInteger.ZERO } ?: BigInteger.ONE
        val (marketGasPrice, gasLimit) = coroutineScope {
            val gasPrice = async { evmQuantity(activeEthereumRpcUrl, "eth_gasPrice", JSONArray()) }
            val limit = async {
                when (direction) {
                    BridgeDirection.OctToWoct -> ClaimGasLimit
                    BridgeDirection.WoctToOct -> coroutineScope {
                        val approveData = "0x095ea7b3${addressWord(WoctBridgeContract)}${safeAmount.toAbiWord()}"
                        val burnData = "0x$BurnToOctraSelector${encodeStringUint(wallet.address, safeAmount)}"
                        val approve = async { estimateGas(from, WoctTokenAddress, approveData, ApproveGasLimit) }
                        val burn = async { estimateGas(from, WoctBridgeContract, burnData, BurnGasLimit) }
                        approve.await().add(burn.await())
                    }
                }
            }
            gasPrice.await() to limit.await()
        }
        val lowGasPrice = marketGasPrice.multiply(BigInteger.valueOf(85)).divide(BigInteger.valueOf(100)).coerceAtLeast(BigInteger.ONE)
        val fastGasPrice = marketGasPrice.multiply(BigInteger.valueOf(125)).divide(BigInteger.valueOf(100)).coerceAtLeast(BigInteger.ONE)
        BridgeEvmGasEstimate(
            gasLimit = gasLimit,
            lowGasPriceWei = lowGasPrice,
            marketGasPriceWei = marketGasPrice,
            fastGasPriceWei = fastGasPrice,
            lowFeeWei = lowGasPrice.multiply(gasLimit),
            marketFeeWei = marketGasPrice.multiply(gasLimit),
            fastFeeWei = fastGasPrice.multiply(gasLimit),
            steps = when (direction) {
                BridgeDirection.OctToWoct -> "Ethereum claim/mint gas"
                BridgeDirection.WoctToOct -> "Ethereum approve + burn gas"
            },
        )
    }

    suspend fun claimWoct(
        wallet: Wallet,
        claimData: BridgeClaimData,
        gasPriceWeiOverride: BigInteger? = null,
    ): EvmSendResult = withContext(Dispatchers.IO) {
        runCatching {
            val from = deriveEvmAddress(wallet)
            evmRaw(
                activeEthereumRpcUrl,
                "eth_call",
                JSONArray()
                    .put(
                        JSONObject()
                            .put("from", from)
                            .put("to", WoctBridgeContract)
                            .put("data", claimData.calldata),
                    )
                    .put("latest"),
            )
            sendEvmContractCall(
                wallet = wallet,
                toAddress = WoctBridgeContract,
                dataHex = claimData.calldata,
                fallbackGas = ClaimGasLimit,
                gasPriceWeiOverride = gasPriceWeiOverride,
            )
        }.getOrElse { exception ->
            EvmSendResult(success = false, error = exception.message ?: "wOCT claim failed.")
        }
    }

    suspend fun approveWoct(
        wallet: Wallet,
        amountRaw: BigInteger,
        gasPriceWeiOverride: BigInteger? = null,
    ): EvmSendResult = withContext(Dispatchers.IO) {
        runCatching {
            require(amountRaw > BigInteger.ZERO) { "Amount must be greater than zero." }
            val calldata = "0x095ea7b3${addressWord(WoctBridgeContract)}${amountRaw.toAbiWord()}"
            sendEvmContractCall(
                wallet = wallet,
                toAddress = WoctTokenAddress,
                dataHex = calldata,
                fallbackGas = ApproveGasLimit,
                gasPriceWeiOverride = gasPriceWeiOverride,
            )
        }.getOrElse { exception ->
            EvmSendResult(success = false, error = exception.message ?: "wOCT approve failed.")
        }
    }

    suspend fun burnWoctToOctra(
        wallet: Wallet,
        octraRecipient: String,
        amountRaw: BigInteger,
        gasPriceWeiOverride: BigInteger? = null,
    ): EvmSendResult =
        withContext(Dispatchers.IO) {
            runCatching {
                val recipient = octraRecipient.trim()
                require(recipient.startsWith("oct") && recipient.length == 47) {
                    "Enter a valid Octra recipient address."
                }
                require(amountRaw > BigInteger.ZERO) { "Amount must be greater than zero." }
                val calldata = "0x$BurnToOctraSelector${encodeStringUint(recipient, amountRaw)}"
                sendEvmContractCall(
                    wallet = wallet,
                    toAddress = WoctBridgeContract,
                    dataHex = calldata,
                    fallbackGas = BurnGasLimit,
                    gasPriceWeiOverride = gasPriceWeiOverride,
                )
            }.getOrElse { exception ->
                EvmSendResult(success = false, error = exception.message ?: "wOCT burn failed.")
            }
        }

    suspend fun speedUpEvmTransaction(wallet: Wallet, pending: EvmSendResult): EvmSendResult = withContext(Dispatchers.IO) {
        runCatching {
            val nonce = pending.nonce ?: throw IllegalStateException("Pending transaction nonce is unavailable.")
            val gasLimit = pending.gasLimit ?: throw IllegalStateException("Pending transaction gas limit is unavailable.")
            val toAddress = pending.toAddress?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("Pending transaction recipient is unavailable.")
            val dataHex = pending.dataHex ?: throw IllegalStateException("Pending transaction data is unavailable.")
            val currentGasPrice = pending.gasPriceWei?.takeIf { it > BigInteger.ZERO }
                ?: evmQuantity(activeEthereumRpcUrl, "eth_gasPrice", JSONArray())
            val networkGasPrice = evmQuantity(activeEthereumRpcUrl, "eth_gasPrice", JSONArray())
            val bumpedGasPrice = listOf(
                currentGasPrice.multiply(BigInteger.valueOf(125)).divide(BigInteger.valueOf(100)).add(OneGweiWei),
                networkGasPrice.multiply(BigInteger.valueOf(125)).divide(BigInteger.valueOf(100)),
            ).maxOrNull()?.coerceAtLeast(BigInteger.ONE) ?: currentGasPrice.add(OneGweiWei)
            sendEvmContractCall(
                wallet = wallet,
                toAddress = toAddress,
                dataHex = dataHex,
                fallbackGas = gasLimit,
                gasPriceWeiOverride = bumpedGasPrice,
                nonceOverride = nonce,
            )
        }.getOrElse { exception ->
            EvmSendResult(success = false, error = exception.message ?: "Speed up transaction failed.")
        }
    }

    suspend fun waitForEvmReceipt(
        hash: String,
        timeoutMs: Long = 300_000L,
        intervalMs: Long = 3_000L,
    ): TransactionNetworkState = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            val receipt = runCatching {
                evmRaw(activeEthereumRpcUrl, "eth_getTransactionReceipt", JSONArray().put(hash)) as? JSONObject
            }.getOrNull()
            if (receipt != null) {
                return@withContext when (receipt.optString("status").lowercase()) {
                    "0x1" -> TransactionNetworkState(TransactionNetworkStatus.Confirmed)
                    "0x0" -> TransactionNetworkState(TransactionNetworkStatus.Rejected, "Ethereum transaction reverted.")
                    else -> TransactionNetworkState(TransactionNetworkStatus.Pending)
                }
            }
            delay(intervalMs)
        }
        TransactionNetworkState(TransactionNetworkStatus.Pending, "Ethereum transaction is still pending.")
    }

    fun evmReceiptStatus(hash: String): TransactionNetworkState {
        val receipt = runCatching {
            evmRaw(activeEthereumRpcUrl, "eth_getTransactionReceipt", JSONArray().put(hash)) as? JSONObject
        }.getOrNull() ?: return TransactionNetworkState(TransactionNetworkStatus.Pending)
        return when (receipt.optString("status").lowercase()) {
            "0x1" -> TransactionNetworkState(TransactionNetworkStatus.Confirmed)
            "0x0" -> TransactionNetworkState(TransactionNetworkStatus.Rejected, "Ethereum transaction reverted.")
            else -> TransactionNetworkState(TransactionNetworkStatus.Pending)
        }
    }

    suspend fun waitForOctraUnlock(
        recipientAddress: String,
        amountRaw: BigInteger,
        startedAtSeconds: Long,
        burnTxHash: String? = null,
        previousBalanceRaw: BigInteger? = null,
        timeoutMs: Long = 300_000L,
        intervalMs: Long = 5_000L,
    ): Boolean = withContext(Dispatchers.IO) {
        val startedAt = System.currentTimeMillis()
        val initialBalance = previousBalanceRaw ?: runCatching { octraBalanceRaw(recipientAddress) }.getOrNull()
        while (System.currentTimeMillis() - startedAt < timeoutMs) {
            if (!burnTxHash.isNullOrBlank() && isBurnTxUnlocked(burnTxHash, recipientAddress) == true) return@withContext true
            if (initialBalance != null && hasBalanceIncreased(recipientAddress, initialBalance)) return@withContext true
            if (hasIncomingUnlock(recipientAddress, amountRaw, startedAtSeconds)) return@withContext true
            delay(intervalMs)
        }
        false
    }

    fun explorerTxUrl(hash: String): String = "https://etherscan.io/tx/$hash"

    fun octraExplorerTxUrl(hash: String): String = "https://octrascan.io/tx.html?hash=$hash"

    suspend fun fetchOnChainHistory(wallet: Wallet): List<BridgeChainHistoryRecord> = withContext(Dispatchers.IO) {
        val evmAddress = deriveEvmAddress(wallet)
        val (octToWoct, woctToOct) = coroutineScope {
            val octToWoctJob = async { fetchOctToWoctHistory(wallet.address, evmAddress) }
            val woctToOctJob = async { fetchWoctToOctHistory(wallet.address, evmAddress) }
            octToWoctJob.await() to woctToOctJob.await()
        }
        (octToWoct + woctToOct)
            .distinctBy { "${it.direction}:${it.octraTxHash.orEmpty()}:${it.evmTxHash.orEmpty()}:${it.id}" }
            .sortedByDescending { it.updatedAt }
    }

    private fun octraBalanceRaw(address: String): BigInteger {
        val result = octraRpc("octra_balance", JSONArray().put(address)) as? JSONObject
            ?: return BigInteger.ZERO
        val raw = when (val value = result.opt("balance_raw")) {
            is Number -> value.toLong().toString()
            is String -> value
            else -> ""
        }
        if (raw.isNotBlank()) return raw.toBigIntegerOrNull() ?: BigInteger.ZERO
        val formatted = result.optString("balance").toBigDecimalOrNull() ?: return BigInteger.ZERO
        return formatted.movePointRight(OctDecimals).toBigInteger()
    }

    fun currentOctraBalanceRaw(address: String): BigInteger = octraBalanceRaw(address)

    private fun hasBalanceIncreased(recipientAddress: String, previousBalanceRaw: BigInteger): Boolean {
        val current = runCatching { octraBalanceRaw(recipientAddress) }.getOrNull() ?: return false
        return current > previousBalanceRaw
    }

    private fun hasIncomingUnlock(recipientAddress: String, amountRaw: BigInteger, startedAtSeconds: Long): Boolean {
        val result = runCatching {
            octraRpc("octra_account", JSONArray().put(recipientAddress).put(50)) as? JSONObject
        }.getOrNull() ?: return false
        val recent = result.transactionArray() ?: return false
        for (index in 0 until recent.length()) {
            val item = recent.optJSONObject(index) ?: continue
            val from = item.optString("from")
            val to = item.optString("to", item.optString("to_"))
            val timestamp = normalizedTimestampSeconds(item)
            val raw = amountRawFrom(item)
            val recentEnough = timestamp == 0L || timestamp >= startedAtSeconds - 120L
            val fromBridge = from.equals(OctraBridgeContract, ignoreCase = true)
            val bridgeMethod = item.optString("encrypted_data", item.optString("method")).equals(OctraUnlockMethod, ignoreCase = true)
            val incomingToWallet = to.equals(recipientAddress, ignoreCase = true)
            if (recentEnough && incomingToWallet && (fromBridge || bridgeMethod || raw > BigInteger.ZERO || raw >= amountRaw)) return true
        }
        return false
    }

    private fun isBurnTxUnlocked(burnTxHash: String, recipientAddress: String): Boolean? {
        val receipt = runCatching {
            evmRaw(activeEthereumRpcUrl, "eth_getTransactionReceipt", JSONArray().put(burnTxHash)) as? JSONObject
        }.getOrNull() ?: return null
        val logs = receipt.optJSONArray("logs") ?: return false
        var sawBurn = false
        for (index in 0 until logs.length()) {
            val log = logs.optJSONObject(index) ?: continue
            if (!log.optString("address").equals(WoctBridgeContract, ignoreCase = true)) continue
            val topics = log.optJSONArray("topics") ?: continue
            if (!topics.optString(0).equals(BurnInitiatedTopic, ignoreCase = true)) continue
            val decoded = decodeBurnLog(log) ?: continue
            if (!decoded.octraRecipient.equals(recipientAddress, ignoreCase = true)) continue
            sawBurn = true
            val unlocked = isBurnUnlocked(decoded.burnId)
            if (unlocked == true) return true
            if (unlocked == null) return null
        }
        return if (sawBurn) false else null
    }

    private fun amountRawFrom(item: JSONObject): BigInteger {
        val raw = when (val value = item.opt("amount_raw")) {
            is Number -> value.toLong().toString()
            is String -> value
            else -> ""
        }
        if (raw.isNotBlank()) return raw.toBigIntegerOrNull() ?: BigInteger.ZERO
        val amount = item.optString("amount").toBigDecimalOrNull() ?: return BigInteger.ZERO
        return amount.movePointRight(OctDecimals).toBigInteger()
    }

    private fun normalizedTimestampSeconds(item: JSONObject): Long {
        val timestamp = item.optDouble("timestamp", 0.0)
            .takeIf { it > 0.0 }
            ?: item.optDouble("time", 0.0).takeIf { it > 0.0 }
            ?: item.optDouble("created_at", 0.0).takeIf { it > 0.0 }
            ?: return 0L
        val raw = timestamp.toLong()
        return if (raw > 10_000_000_000L) raw / 1000L else raw
    }

    private fun JSONObject.transactionArray(): JSONArray? =
        optJSONArray("recent_txs")
            ?: optJSONArray("transactions")
            ?: optJSONArray("txs")
            ?: optJSONObject("result")?.optJSONArray("recent_txs")
            ?: optJSONObject("result")?.optJSONArray("transactions")
            ?: optJSONObject("result")?.optJSONArray("txs")

    private fun JSONObject.firstString(vararg keys: String): String? {
        keys.forEach { key ->
            if (!has(key) || isNull(key)) return@forEach
            val value = optString(key).trim()
            if (value.isNotBlank()) return value
        }
        return null
    }

    private fun JSONObject.firstInt(vararg keys: String): Int {
        keys.forEach { key ->
            if (!has(key) || isNull(key)) return@forEach
            when (val value = opt(key)) {
                is Number -> return value.toInt()
                is String -> value.toIntOrNull()?.let { return it }
            }
        }
        return -1
    }

    private fun fetchOctToWoctHistory(octraAddress: String, evmAddress: String): List<BridgeChainHistoryRecord> {
        val latestEpoch = historyRpcRetry { lightClientLatestEpoch() }
        return fetchLockTransactions(octraAddress).mapNotNull { tx ->
            val hash = tx.optString("hash", tx.optString("tx_hash")).ifBlank { return@mapNotNull null }
            val timestamp = tx.optDouble("timestamp", 0.0).takeIf { it > 0.0 }?.times(1000)?.toLong()
                ?: System.currentTimeMillis()
            val txEpoch = tx.optLong("epoch", 0L)
            val fallbackAmount = amountRawFrom(tx)
            val locked = runCatching { readLockedEvent(hash, txEpoch) }.getOrNull()
            val amountRaw = locked?.amountRaw?.takeIf { it > BigInteger.ZERO } ?: fallbackAmount
            val epoch = locked?.epoch ?: txEpoch
            val recipient = locked?.ethAddress?.ifBlank { evmAddress } ?: evmAddress
            val srcNonce = locked?.srcNonce ?: 0L
            val statusData = if (locked == null) {
                Triple("running", BridgeStep.ConfirmingOctra, "Lock transaction found. Waiting for Octra contract receipt.")
            } else if (epoch > latestEpoch) {
                Triple("running", BridgeStep.WaitingRelayer, "Waiting for Ethereum lightclient header.")
            } else {
                val claimStatus = runCatching {
                    historyRpcRetry {
                        val msgHash = bridgeMessageHash(recipient, amountRaw, srcNonce)
                        isMessageClaimed(msgHash)
                    }
                }.getOrNull()
                when (claimStatus) {
                    true -> Triple("completed", BridgeStep.Completed, "wOCT claim already completed.")
                    false -> Triple("claimable", BridgeStep.Claiming, "wOCT claim is ready.")
                    null -> Triple("unknown", BridgeStep.WaitingRelayer, "Claim status could not be verified.")
                }
            }
            BridgeChainHistoryRecord(
                id = "oct-$hash",
                direction = BridgeDirection.OctToWoct,
                walletAddress = octraAddress,
                evmAddress = recipient,
                amount = formatAmount(amountRaw),
                amountRaw = amountRaw.toString(),
                status = statusData.first,
                step = statusData.second,
                message = statusData.third,
                octraTxHash = hash,
                createdAt = timestamp,
                updatedAt = timestamp,
            )
        }
    }

    private fun fetchLockTransactions(octraAddress: String, limit: Int = 20): List<JSONObject> {
        val result = runCatching {
            historyRpcRetry { octraRpc("octra_transactionsByAddress", JSONArray().put(octraAddress).put(50).put(0)) }
        }.getOrNull() ?: runCatching {
            historyRpcRetry { octraRpc("octra_account", JSONArray().put(octraAddress).put(50)) }
        }.getOrNull() ?: throw IllegalStateException("Octra bridge history could not be fetched.")

        val transactions = when (result) {
            is JSONObject -> result.optJSONArray("transactions")
                ?: result.optJSONArray("recent_txs")
                ?: result.optJSONObject("result")?.optJSONArray("transactions")
                ?: result.optJSONObject("result")?.optJSONArray("recent_txs")
            is JSONArray -> result
            else -> null
        } ?: return emptyList()

        return buildList {
            for (index in 0 until transactions.length()) {
                val tx = transactions.optJSONObject(index) ?: continue
                val to = tx.optString("to", tx.optString("to_"))
                val opType = tx.optString("op_type")
                val method = tx.optString("encrypted_data", tx.optString("method"))
                if (!to.equals(OctraBridgeContract, ignoreCase = true)) continue
                if (opType.isNotBlank() && opType != "call") continue
                if (method == OctraUnlockMethod) continue
                if (method.isNotBlank() && method != OctraLockMethod) continue
                add(tx)
                if (size >= limit) break
            }
        }
    }

    private fun fetchWoctToOctHistory(octraAddress: String, evmAddress: String): List<BridgeChainHistoryRecord> {
        val latestBlock = historyRpcRetry {
            evmQuantity(activeEthereumRpcUrl, "eth_blockNumber", JSONArray()).toLong()
        }
        if (latestBlock <= 0L) throw IllegalStateException("Ethereum bridge history block height is unavailable.")
        val burnerTopic = "0x" + evmAddress.cleanHex().lowercase().padStart(64, '0')
        val logs = mutableListOf<JSONObject>()
        val startBlock = (latestBlock - BurnHistoryLookbackBlocks).coerceAtLeast(0L)
        var toBlock = latestBlock
        var successfulChunks = 0
        var lastChunkError: Throwable? = null
        while (toBlock > startBlock && logs.size < MaxBridgeHistoryLogs) {
            val fromBlock = (toBlock - BurnHistoryChunkBlocks + 1L).coerceAtLeast(startBlock)
            val chunk = runCatching {
                historyRpcRetry {
                    evmRaw(
                        activeEthereumRpcUrl,
                        "eth_getLogs",
                        JSONArray().put(
                            JSONObject()
                                .put("address", WoctBridgeContract)
                                .put(
                                    "topics",
                                    JSONArray()
                                        .put(BurnInitiatedTopic)
                                        .put(JSONObject.NULL)
                                        .put(burnerTopic),
                                )
                                .put("fromBlock", "0x${fromBlock.toString(16)}")
                                .put("toBlock", "0x${toBlock.toString(16)}"),
                        ),
                    ) as? JSONArray ?: JSONArray()
                }
            }.onFailure { lastChunkError = it }.getOrNull()
            if (chunk != null) {
                successfulChunks++
                for (index in 0 until chunk.length()) {
                    chunk.optJSONObject(index)?.let(logs::add)
                }
            }
            toBlock = fromBlock - 1L
        }
        if (successfulChunks == 0 && lastChunkError != null) {
            throw IllegalStateException(lastChunkError?.message ?: "Ethereum bridge history could not be fetched.")
        }

        return logs
            .asReversed()
            .take(MaxBridgeHistoryLogs)
            .mapNotNull { log ->
                val decoded = decodeBurnLog(log) ?: return@mapNotNull null
                if (!decoded.octraRecipient.equals(octraAddress, ignoreCase = true)) return@mapNotNull null
                val unlocked = runCatching { historyRpcRetry { isBurnUnlocked(decoded.burnId) } }.getOrNull()
                val statusData = when (unlocked) {
                    true -> Triple("completed", BridgeStep.Completed, "OCT unlocked by Unlock Operator.")
                    false -> Triple("running", BridgeStep.WaitingUnlock, "Burn confirmed. Waiting for Unlock Operator.")
                    null -> Triple("unknown", BridgeStep.WaitingUnlock, "Unlock status could not be verified.")
                }
                BridgeChainHistoryRecord(
                    id = "evm-${decoded.txHash}",
                    direction = BridgeDirection.WoctToOct,
                    walletAddress = octraAddress,
                    evmAddress = evmAddress,
                    amount = formatAmount(decoded.amountRaw),
                    amountRaw = decoded.amountRaw.toString(),
                    status = statusData.first,
                    step = statusData.second,
                    message = statusData.third,
                    evmTxHash = decoded.txHash,
                    createdAt = decoded.timestamp,
                    updatedAt = decoded.timestamp,
                )
            }
    }

    private data class BurnLog(
        val txHash: String,
        val burnId: String,
        val octraRecipient: String,
        val amountRaw: BigInteger,
        val timestamp: Long,
    )

    private fun decodeBurnLog(log: JSONObject): BurnLog? {
        val topics = log.optJSONArray("topics") ?: return null
        val burnId = topics.optString(1).ifBlank { return null }
        val txHash = log.optString("transactionHash").ifBlank { return null }
        val data = log.optString("data").cleanHex()
        if (data.length < 64 * 4) return null
        val stringOffset = data.wordAt(0).toIntOrNull(16) ?: return null
        val amountRaw = data.wordAt(1).toBigIntegerOrNull(16) ?: BigInteger.ZERO
        val offset = stringOffset * 2
        if (offset + 64 > data.length) return null
        val stringBytes = data.wordAt(offset / 64).toIntOrNull(16) ?: return null
        val stringStart = offset + 64
        val stringEnd = (stringStart + stringBytes * 2).coerceAtMost(data.length)
        val recipient = runCatching {
            data.substring(stringStart, stringEnd).hexToBytes().toString(Charsets.UTF_8)
        }.getOrDefault("")
        val timestamp = blockTimestamp(log.optString("blockNumber"))
        return BurnLog(
            txHash = txHash,
            burnId = burnId,
            octraRecipient = recipient,
            amountRaw = amountRaw,
            timestamp = timestamp,
        )
    }

    private fun blockTimestamp(blockHex: String): Long {
        val block = runCatching {
            evmRaw(activeEthereumRpcUrl, "eth_getBlockByNumber", JSONArray().put(blockHex).put(false)) as? JSONObject
        }.getOrNull()
        val seconds = block?.optString("timestamp")?.hexQuantityToBigInteger()?.toLong()
        return seconds?.times(1000L) ?: System.currentTimeMillis()
    }

    private fun readLockedEvent(txHash: String, txEpoch: Long): BridgeLockedEvent {
        val receipt = octraRpc("contract_receipt", JSONArray().put(txHash)) as? JSONObject
            ?: throw IllegalStateException("Could not fetch Octra contract receipt.")
        if (!receipt.optBoolean("success", false)) {
            throw IllegalStateException(receipt.optString("error", "lock_to_eth failed on-chain."))
        }
        val method = receipt.optString("method", receipt.optString("encrypted_data"))
        if (method.isNotBlank() && method != OctraLockMethod) {
            throw IllegalStateException("Contract receipt is not a lock_to_eth bridge call.")
        }
        val events = receipt.optJSONArray("events") ?: JSONArray()
        for (index in 0 until events.length()) {
            val item = events.optJSONObject(index) ?: continue
            if (item.optString("event") != "Locked") continue
            val values = item.optJSONArray("values") ?: continue
            if (values.length() < 4) continue
            return BridgeLockedEvent(
                from = values.optString(0),
                amountRaw = values.optString(1).toBigIntegerOrNull() ?: BigInteger.ZERO,
                ethAddress = values.optString(2),
                srcNonce = values.optString(3).toLongOrNull() ?: 0L,
                epoch = receipt.optLong("epoch", txEpoch),
                txHash = txHash,
            )
        }
        throw IllegalStateException("Locked event was not found in the contract receipt.")
    }

    private fun buildClaimData(
        epochId: Long,
        recipient: String,
        rawAmount: BigInteger,
        srcNonce: Long,
    ): BridgeClaimData? {
        val result = signerRpc("bridgeMessagesByEpoch", JSONArray().put(epochId))
        val messages = when (result) {
            is JSONObject -> result.optJSONArray("messages")
                ?: result.optJSONObject("result")?.optJSONArray("messages")
                ?: result.optJSONArray("result")
            is JSONArray -> result
            else -> null
        } ?: return null

        val candidates = buildList {
            for (index in 0 until messages.length()) {
                val message = messages.optJSONObject(index) ?: continue
                val messageRecipient = message.firstString("recipient", "eth_address", "ethAddress", "to")
                if (!messageRecipient.equals(recipient, ignoreCase = true)) continue
                val messageAmount = message.firstString("amount_raw", "amountRaw", "amount")?.toBigIntegerOrNull()
                if (messageAmount != null && messageAmount != rawAmount) continue
                val messageNonce = message.firstString("src_nonce", "srcNonce", "source_nonce", "nonce")?.toLongOrNull()
                val leafIndex = message.firstInt("leaf_index", "leafIndex", "index")
                if (leafIndex < 0) continue
                val score = when {
                    messageNonce == srcNonce -> 3
                    messageNonce == null && messageAmount == rawAmount -> 2
                    messageNonce == null -> 1
                    else -> 0
                }
                if (score > 0) add(leafIndex to score)
            }
        }
        val leafIndex = candidates.maxWithOrNull(compareBy<Pair<Int, Int>> { it.second }.thenBy { -it.first })?.first
            ?: return null
        val calldataResult = signerRpc("bridgeClaimCalldata", JSONArray().put(epochId).put(leafIndex))
        val calldata = when (calldataResult) {
            is JSONObject -> calldataResult.optString("calldata")
                .ifBlank { calldataResult.optJSONObject("result")?.optString("calldata").orEmpty() }
            else -> ""
        }.takeIf { it.startsWith("0x") && it.length > 10 } ?: return null
        return BridgeClaimData(calldata = calldata, epochId = epochId, leafIndex = leafIndex)
    }

    private fun sendEvmContractCall(
        wallet: Wallet,
        toAddress: String,
        dataHex: String,
        fallbackGas: BigInteger,
        gasPriceWeiOverride: BigInteger? = null,
        nonceOverride: BigInteger? = null,
    ): EvmSendResult {
        val from = deriveEvmAddress(wallet)
        val data = dataHex.hexToBytes()
        val nonce = nonceOverride ?: evmQuantity(activeEthereumRpcUrl, "eth_getTransactionCount", JSONArray().put(from).put("pending"))
        val gasPrice = gasPriceWeiOverride?.takeIf { it > BigInteger.ZERO }
            ?: evmQuantity(activeEthereumRpcUrl, "eth_gasPrice", JSONArray())
        val gasLimit = estimateGas(from, toAddress, dataHex, fallbackGas)
        val gasCost = gasPrice.multiply(gasLimit)
        val nativeBalance = evmQuantity(activeEthereumRpcUrl, "eth_getBalance", JSONArray().put(from).put("latest"))
        require(nativeBalance >= gasCost) { "Insufficient ETH for bridge gas." }
        val rawTx = crypto.signLegacyTransaction(
            wallet = wallet,
            nonce = nonce,
            gasPriceWei = gasPrice,
            gasLimit = gasLimit,
            toAddress = toAddress,
            valueWei = BigInteger.ZERO,
            data = data,
            chainId = EthChainId,
        )
        return EvmSendResult(
            success = true,
            hash = evmString(activeEthereumRpcUrl, "eth_sendRawTransaction", JSONArray().put(rawTx)),
            nonce = nonce,
            gasPriceWei = gasPrice,
            gasLimit = gasLimit,
            toAddress = toAddress,
            dataHex = dataHex,
            valueWei = BigInteger.ZERO,
        )
    }

    private fun estimateGas(from: String, to: String, dataHex: String, fallbackGas: BigInteger): BigInteger {
        val estimated = runCatching {
            val tx = JSONObject()
                .put("from", from)
                .put("to", to)
                .put("value", "0x0")
                .put("data", dataHex)
            evmQuantity(activeEthereumRpcUrl, "eth_estimateGas", JSONArray().put(tx))
                .multiply(BigInteger.valueOf(115))
                .divide(BigInteger.valueOf(100))
        }.getOrDefault(fallbackGas)
        return if (estimated > fallbackGas) estimated else fallbackGas
    }

    private fun erc20BalanceRaw(ownerAddress: String): BigInteger {
        val data = "0x70a08231${addressWord(ownerAddress)}"
        return evmString(
            activeEthereumRpcUrl,
            "eth_call",
            JSONArray()
                .put(JSONObject().put("to", WoctTokenAddress).put("data", data))
                .put("latest"),
        ).hexQuantityToBigInteger()
    }

    private fun octraRpc(method: String, params: JSONArray): Any? =
        jsonRpc("${activeOctraRpcUrl.trimEnd('/')}/rpc", method, params)

    private fun octraContractCall(method: String, params: JSONArray, walletAddress: String): String? {
        val result = octraRpc(
            "contract_call",
            JSONArray()
                .put(OctraBridgeContract)
                .put(method)
                .put(params)
                .put(walletAddress),
        )
        return contractResultString(result)
    }

    private fun octraStorage(key: String): String? {
        val result = octraRpc("octra_contractStorage", JSONArray().put(OctraBridgeContract).put(key))
        return when (result) {
            is JSONObject -> result.optString("value").ifBlank { null }
            else -> null
        }
    }

    private fun isBurnUnlocked(burnId: String): Boolean? {
        return runCatching { octraStorage("processed_unlocks:$burnId")?.trim() == "1" }.getOrNull()
    }

    private fun lightClientLatestEpoch(): Long {
        return evmString(
            activeEthereumRpcUrl,
            "eth_call",
            JSONArray()
                .put(JSONObject().put("to", LightClientAddress).put("data", LightClientLatestEpochSelector))
                .put("latest"),
        ).hexQuantityToBigInteger().toLong()
    }

    private fun bridgeMessageHash(ethAddress: String, amountRaw: BigInteger, srcNonce: Long): String {
        val calldata = HashBridgeMessageSelector +
            BigInteger.ONE.toAbiWord() +
            BigInteger.ZERO.toAbiWord() +
            BigInteger.valueOf(OctraChainId).toAbiWord() +
            BigInteger.valueOf(EthChainId).toAbiWord() +
            BridgeSrcBridgeId.cleanHex().padStart(64, '0') +
            BridgeDstBridgeId.cleanHex().padStart(64, '0') +
            BridgeTokenId.cleanHex().padStart(64, '0') +
            addressWord(ethAddress) +
            amountRaw.toAbiWord() +
            BigInteger.valueOf(srcNonce).toAbiWord()
        return evmString(
            activeEthereumRpcUrl,
            "eth_call",
            JSONArray()
                .put(JSONObject().put("to", WoctBridgeContract).put("data", calldata))
                .put("latest"),
        )
    }

    private fun isMessageClaimed(msgHash: String): Boolean {
        val result = evmString(
            activeEthereumRpcUrl,
            "eth_call",
            JSONArray()
                .put(JSONObject().put("to", WoctBridgeContract).put("data", ProcessedMessagesSelector + msgHash.cleanHex().padStart(64, '0')))
                .put("latest"),
        )
        return result.hexQuantityToBigInteger() != BigInteger.ZERO
    }

    private fun contractResultString(value: Any?): String? {
        return when (value) {
            null,
            JSONObject.NULL -> null
            is JSONObject -> {
                when {
                    value.has("result") -> contractResultString(value.opt("result"))
                    value.has("value") -> contractResultString(value.opt("value"))
                    else -> value.toString()
                }
            }
            is JSONArray -> if (value.length() > 0) contractResultString(value.opt(0)) else null
            is Number,
            is Boolean -> value.toString()
            is String -> value
            else -> value.toString()
        }?.trim()?.trim('"')?.ifBlank { null }
    }

    private fun signerRpc(method: String, params: JSONArray): Any? =
        jsonRpc("${RelayerRpcUrl.trimEnd('/')}/", method, params)

    private fun <T> historyRpcRetry(block: () -> T): T {
        var lastError: Throwable? = null
        repeat(BridgeHistoryRpcAttempts) { attempt ->
            try {
                return block()
            } catch (error: Throwable) {
                lastError = error
                if (attempt < BridgeHistoryRpcAttempts - 1) {
                    Thread.sleep(BridgeHistoryRetryDelayMs * (attempt + 1L))
                }
            }
        }
        throw lastError ?: IllegalStateException("Bridge history RPC failed.")
    }

    private fun evmQuantity(rpcUrl: String, method: String, params: JSONArray): BigInteger =
        evmString(rpcUrl, method, params).hexQuantityToBigInteger()

    private fun evmString(rpcUrl: String, method: String, params: JSONArray): String {
        val result = evmRaw(rpcUrl, method, params)
        return result?.toString() ?: throw IllegalStateException("$method returned empty result.")
    }

    private fun evmRaw(rpcUrl: String, method: String, params: JSONArray): Any? =
        jsonRpc(rpcUrl, method, params)

    private fun jsonRpc(url: String, method: String, params: JSONArray): Any? {
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", System.currentTimeMillis())
            .put("method", method)
            .put("params", params)
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 12_000
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
            if (responseCode !in 200..299) throw IllegalStateException("HTTP $responseCode")
            val json = JSONObject(body)
            json.optJSONObject("error")?.let { error ->
                throw IllegalStateException(error.optString("message", "Bridge RPC error."))
            }
            if (json.has("result")) json.get("result") else null
        } finally {
            connection.disconnect()
        }
    }

    private fun encodeStringUint(text: String, amount: BigInteger): String {
        val bytes = text.toByteArray(Charsets.UTF_8)
        val encodedText = bytes.toHex().padEnd(((bytes.size + 31) / 32) * 64, '0')
        return BigInteger.valueOf(64L).toAbiWord() +
            amount.toAbiWord() +
            BigInteger.valueOf(bytes.size.toLong()).toAbiWord() +
            encodedText
    }

    private fun addressWord(address: String): String = address.cleanHex().lowercase().padStart(64, '0')

    private fun BigInteger.toAbiWord(): String = toString(16).padStart(64, '0')

    private fun String.hexQuantityToBigInteger(): BigInteger {
        val clean = cleanHex()
        if (clean.isBlank()) return BigInteger.ZERO
        return BigInteger(clean, 16)
    }

    private fun String.cleanHex(): String = removePrefix("0x").removePrefix("0X")

    private fun String.hexToBytes(): ByteArray {
        val clean = cleanHex()
        require(clean.length % 2 == 0) { "Hex value has invalid length." }
        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun String.wordAt(index: Int): String {
        val start = index * 64
        if (start >= length) return ""
        return substring(start, (start + 64).coerceAtMost(length))
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun String.toBridgePaused(): Boolean? {
        return when (trim().lowercase()) {
            "1", "true", "paused", "closed" -> true
            "0", "false", "open", "active" -> false
            else -> toBigIntegerOrNull()?.let { it != BigInteger.ZERO }
        }
    }

    private fun String.toRawAmount(): BigInteger? {
        val clean = trim().removePrefix("+")
        return clean.toBigIntegerOrNull() ?: clean.toBigDecimalOrNull()?.toBigInteger()
    }

    companion object {
        const val OctraRpcUrl = "https://octra.network"
        const val OctraBridgeContract = "oct5MrNfjiXFNRDLwsodn8Zm9hDKNGAYt3eQDCQ52bSpCHq"
        const val OctraLockMethod = "lock_to_eth"
        const val OctraUnlockMethod = "unlock_trusted"
        const val WoctBridgeContract = "0xE7eD69b852fd2a1406080B26A37e8E04e7dA4caE"
        const val WoctTokenAddress = "0x4647e1fE715c9e23959022C2416C71867F5a6E80"
        const val EthereumRpcUrl = "https://rpc.xme.my.id/eth-mainnet"
        const val RelayerRpcUrl = "https://relayer-002838819188.octra.network"
        private const val PoctraUserAgent = "poctra-android"
        const val OctDecimals = 6
        const val EthChainId = 1L
        private const val OctraChainId = 7777L
        private const val LightClientAddress = "0xc01ca57dc7f7c4b6f1b6b87b85d79e5ddf0df55d"
        private const val LightClientLatestEpochSelector = "0x9cb118bf"
        private const val HashBridgeMessageSelector = "0x93cf0d23"
        private const val ProcessedMessagesSelector = "0x88ba16ab"
        private const val BridgeSrcBridgeId = "0x381ab73c25fb8d4ec4c03e15dd630fab75b410afd90a9276ab81df81c38d2a8b"
        private const val BridgeDstBridgeId = "0xab33480ea300316d03f76278f05f08f011d41d60f5d49c6ff6d8489fbd60c794"
        private const val BridgeTokenId = "0x412ec1126381d672a9f42b8612e4bc9ee64f5b6467b991e61110203549cdd6de"
        private const val BurnInitiatedTopic = "0xfaa9b3e2244d80066df764926dce8b97fa7077d982b97f52195a39950fc100b4"
        private const val BurnHistoryChunkBlocks = 9_000L
        private const val BurnHistoryLookbackBlocks = 200_000L
        private const val MaxBridgeHistoryLogs = 20
        private const val BridgeHistoryRpcAttempts = 3
        private const val BridgeHistoryRetryDelayMs = 350L
        private const val BurnToOctraSelector = "e3e3aed0"
        private val OneGweiWei = BigInteger("1000000000")
        private val ApproveGasLimit = BigInteger.valueOf(196_608L)
        private val BurnGasLimit = BigInteger.valueOf(196_608L)
        private val ClaimGasLimit = BigInteger.valueOf(393_216L)
    }
}
