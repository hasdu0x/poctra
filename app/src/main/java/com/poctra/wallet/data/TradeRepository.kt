package com.poctra.wallet.data

import com.poctra.wallet.core.EvmCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.absoluteValue

private const val NativeTradeCurrency = "0x0000000000000000000000000000000000000000"

enum class TradeAsset(
    val symbol: String,
    val displayName: String,
    val address: String,
    val decimals: Int,
) {
    WOCT("wOCT", "Wrapped OCT", "0x4647e1fE715c9e23959022C2416C71867F5a6E80", 6),
    ETH("ETH", "Ethereum", NativeTradeCurrency, 18),
    USDT("USDT", "Tether USD", "0xdAC17F958D2ee523a2206206994597C13D831ec7", 6);

    companion object {
        const val NativeCurrency = NativeTradeCurrency
    }
}

data class TradeBalances(
    val woctRaw: BigInteger = BigInteger.ZERO,
    val ethWei: BigInteger = BigInteger.ZERO,
    val usdtRaw: BigInteger = BigInteger.ZERO,
) {
    fun formatted(asset: TradeAsset): String = when (asset) {
        TradeAsset.WOCT -> EvmCrypto.formatUnits(woctRaw, asset.decimals)
        TradeAsset.ETH -> EvmCrypto.formatEther(ethWei)
        TradeAsset.USDT -> EvmCrypto.formatUnits(usdtRaw, asset.decimals)
    }

    fun raw(asset: TradeAsset): BigInteger = when (asset) {
        TradeAsset.WOCT -> woctRaw
        TradeAsset.ETH -> ethWei
        TradeAsset.USDT -> usdtRaw
    }
}

data class TradeQuote(
    val amountOutRaw: BigInteger,
    val amountOut: String,
    val minimumOutRaw: BigInteger,
    val minimumOut: String,
    val gasEstimate: BigInteger,
    val gasPriceWei: BigInteger,
    val gasFeeEth: String,
    val lowGasPriceWei: BigInteger,
    val lowGasFeeEth: String,
    val fastGasPriceWei: BigInteger,
    val fastGasFeeEth: String,
    val routeLabel: String,
    val priceImpactBps: Int? = null,
    val priceImpactLabel: String = "Unavailable",
    val poolLiquidityLabel: String = "Unavailable",
    val mevRouteLabel: String = "Unknown",
    val riskLevel: TradeQuoteRisk = TradeQuoteRisk.None,
    val riskWarning: String? = null,
)

enum class TradeQuoteRisk {
    None,
    Notice,
    Warning,
    Critical,
}

enum class TradeChartTimeframe(
    val label: String,
    val apiFrame: String,
    val aggregate: Int,
    val limit: Int,
    val clientBucketSeconds: Long? = null,
) {
    H1("1H", "hour", 1, 32),
    H4("4H", "hour", 4, 32),
    D1("1D", "day", 1, 32),
    W1("1W", "day", 1, 168, 7 * 24 * 60 * 60L),
}

data class TradeCandle(
    val timestamp: Long,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
)

data class TradeSwapResult(
    val success: Boolean,
    val hash: String? = null,
    val approvalHash: String? = null,
    val permitHash: String? = null,
    val error: String? = null,
    val transactions: List<TradeSubmittedTransaction> = emptyList(),
)

enum class TradeSwapStage {
    TokenApproval,
    PermitApproval,
    Swap,
}

data class TradeSubmittedTransaction(
    val stage: TradeSwapStage,
    val transaction: EvmSendResult,
)

class TradeRepository {
    private val crypto = EvmCrypto()
    private var activeEthereumRpcUrl = EthereumRpcUrl
    private var cachedWoctPool: ChartPool? = null
    private var cachedWoctPools: List<ChartPool>? = null
    private var cachedDexScreenerPools: List<DexScreenerPool>? = null

    fun configure(evmRpcUrl: String = EthereumRpcUrl) {
        activeEthereumRpcUrl = evmRpcUrl.trim().ifBlank { EthereumRpcUrl }
    }

    fun deriveAddress(wallet: Wallet): String = crypto.deriveAddress(wallet)

    fun parseAmount(value: String, asset: TradeAsset): BigInteger = EvmCrypto.parseUnits(value, asset.decimals)

    fun formatAmount(value: BigInteger, asset: TradeAsset, scale: Int = 6): String =
        EvmCrypto.formatUnits(value, asset.decimals, scale)

    suspend fun fetchBalances(wallet: Wallet): TradeBalances = withContext(Dispatchers.IO) {
        val owner = deriveAddress(wallet)
        TradeBalances(
            woctRaw = erc20BalanceRaw(TradeAsset.WOCT.address, owner),
            ethWei = evmQuantity("eth_getBalance", JSONArray().put(owner).put("latest")),
            usdtRaw = erc20BalanceRaw(TradeAsset.USDT.address, owner),
        )
    }

    suspend fun quoteExactInput(
        fromAsset: TradeAsset,
        toAsset: TradeAsset,
        amountText: String,
        slippageBps: Int,
    ): TradeQuote = withContext(Dispatchers.IO) {
        val amountIn = parseAmount(amountText, fromAsset)
        require(amountIn > BigInteger.ZERO) { "Enter a valid amount." }
        require(isSupportedPair(fromAsset, toAsset)) { "Trade supports wOCT pairs with ETH or USDT." }
        val pool = poolFor(fromAsset, toAsset)
        val quoted = quoteRaw(pool, amountIn)
        val amountOut = quoted.amountOut
        val gasEstimate = quoted.gasEstimate
        val gasPrice = evmQuantity("eth_gasPrice", JSONArray()).coerceAtLeast(BigInteger.ONE)
        val lowGasPrice = gasPrice
            .multiply(BigInteger.valueOf(90L))
            .divide(BigInteger.valueOf(100L))
            .coerceAtLeast(BigInteger.ONE)
        val fastGasPrice = gasPrice
            .multiply(BigInteger.valueOf(125L))
            .divide(BigInteger.valueOf(100L))
            .add(OneGweiWei)
        val minimumOut = amountOut
            .multiply(BigInteger.valueOf((10_000 - slippageBps.coerceIn(1, 5_000)).toLong()))
            .divide(BigInteger.valueOf(10_000L))
        val priceImpactBps = estimatePriceImpactBps(pool, amountIn, amountOut, fromAsset)
        val liquidity = runCatching { findTradePoolLiquidity(fromAsset, toAsset) }.getOrNull()
        val mevRouteLabel = mevRouteLabel()
        val riskLevel = tradeQuoteRisk(priceImpactBps, liquidity?.reserveUsd)
        TradeQuote(
            amountOutRaw = amountOut,
            amountOut = formatAmount(amountOut, toAsset),
            minimumOutRaw = minimumOut,
            minimumOut = formatAmount(minimumOut, toAsset),
            gasEstimate = gasEstimate,
            gasPriceWei = gasPrice,
            gasFeeEth = EvmCrypto.formatEther(gasEstimate.multiply(gasPrice), 8),
            lowGasPriceWei = lowGasPrice,
            lowGasFeeEth = EvmCrypto.formatEther(gasEstimate.multiply(lowGasPrice), 8),
            fastGasPriceWei = fastGasPrice,
            fastGasFeeEth = EvmCrypto.formatEther(gasEstimate.multiply(fastGasPrice), 8),
            routeLabel = "${fromAsset.symbol}/${toAsset.symbol} v4 0.30%",
            priceImpactBps = priceImpactBps,
            priceImpactLabel = formatPriceImpact(priceImpactBps),
            poolLiquidityLabel = poolLiquidityLabel(liquidity, priceImpactBps),
            mevRouteLabel = mevRouteLabel,
            riskLevel = riskLevel,
            riskWarning = tradeQuoteRiskWarning(priceImpactBps, liquidity?.reserveUsd),
        )
    }

    suspend fun swapExactInput(
        wallet: Wallet,
        fromAsset: TradeAsset,
        toAsset: TradeAsset,
        amountText: String,
        minimumOutRaw: BigInteger,
        gasPriceWeiOverride: BigInteger? = null,
    ): TradeSwapResult = withContext(Dispatchers.IO) {
        runCatching {
            require(isSupportedPair(fromAsset, toAsset)) { "Trade supports wOCT pairs with ETH or USDT." }
            val amountIn = parseAmount(amountText, fromAsset)
            require(amountIn > BigInteger.ZERO) { "Enter a valid amount." }
            val owner = deriveAddress(wallet)
            val balance = fetchBalances(wallet).raw(fromAsset)
            require(balance >= amountIn) { "Insufficient ${fromAsset.symbol} balance." }
            var nonceCursor = evmQuantity("eth_getTransactionCount", JSONArray().put(owner).put("pending"))
            fun nextNonce(): BigInteger {
                val nonce = nonceCursor
                nonceCursor = nonceCursor.add(BigInteger.ONE)
                return nonce
            }

            val transactions = mutableListOf<TradeSubmittedTransaction>()
            if (fromAsset != TradeAsset.ETH) {
                transactions += ensureErc20Approval(
                    wallet = wallet,
                    owner = owner,
                    asset = fromAsset,
                    amount = amountIn,
                    gasPriceWeiOverride = gasPriceWeiOverride,
                    nonceProvider = ::nextNonce,
                )
                val permit = approvePermit2(
                    wallet = wallet,
                    asset = fromAsset,
                    amount = amountIn,
                    gasPriceWeiOverride = gasPriceWeiOverride,
                    nonceOverride = nextNonce(),
                )
                transactions += TradeSubmittedTransaction(TradeSwapStage.PermitApproval, permit)
            }

            val pool = poolFor(fromAsset, toAsset)
            val executeData = universalRouterExecuteCalldata(pool, amountIn, minimumOutRaw)
            val valueWei = if (fromAsset == TradeAsset.ETH) amountIn else BigInteger.ZERO
            val swap = sendEvmTransaction(
                wallet = wallet,
                from = owner,
                toAddress = UniversalRouterAddress,
                dataHex = executeData,
                valueWei = valueWei,
                fallbackGas = SwapGasLimit,
                nonceOverride = nextNonce(),
                gasPriceWeiOverride = gasPriceWeiOverride,
            )
            transactions += TradeSubmittedTransaction(TradeSwapStage.Swap, swap)
            TradeSwapResult(
                success = true,
                hash = swap.hash,
                approvalHash = transactions.firstOrNull { it.stage == TradeSwapStage.TokenApproval }?.transaction?.hash,
                permitHash = transactions.firstOrNull { it.stage == TradeSwapStage.PermitApproval }?.transaction?.hash,
                transactions = transactions,
            )
        }.getOrElse { exception ->
            TradeSwapResult(success = false, error = exception.message ?: "Swap failed.")
        }
    }

    suspend fun speedUpTransaction(wallet: Wallet, pending: EvmSendResult): EvmSendResult = withContext(Dispatchers.IO) {
        val from = deriveAddress(wallet)
        val nonce = pending.nonce ?: throw IllegalArgumentException("Pending transaction nonce is unavailable.")
        val toAddress = pending.toAddress ?: throw IllegalArgumentException("Pending transaction target is unavailable.")
        val dataHex = pending.dataHex ?: "0x"
        val currentGas = pending.gasPriceWei ?: evmQuantity("eth_gasPrice", JSONArray())
        val networkGas = evmQuantity("eth_gasPrice", JSONArray())
        val bumpedCurrent = currentGas
            .multiply(BigInteger.valueOf(125L))
            .divide(BigInteger.valueOf(100L))
            .add(OneGweiWei)
        val bumpedNetwork = networkGas
            .multiply(BigInteger.valueOf(125L))
            .divide(BigInteger.valueOf(100L))
        val gasPrice = listOf(bumpedCurrent, bumpedNetwork)
            .maxOrNull()
            ?.coerceAtLeast(BigInteger.ONE)
            ?: bumpedCurrent
        sendEvmTransaction(
            wallet = wallet,
            from = from,
            toAddress = toAddress,
            dataHex = dataHex,
            valueWei = pending.valueWei,
            fallbackGas = pending.gasLimit ?: SwapGasLimit,
            nonceOverride = nonce,
            gasPriceWeiOverride = gasPrice,
        )
    }

    suspend fun transactionStatus(hash: String): TransactionNetworkState = withContext(Dispatchers.IO) {
        runCatching {
            val result = evmRaw("eth_getTransactionReceipt", JSONArray().put(hash))
            if (result == null || result == JSONObject.NULL) return@runCatching TransactionNetworkState(TransactionNetworkStatus.Pending)
            val receipt = result as? JSONObject ?: return@runCatching TransactionNetworkState(TransactionNetworkStatus.Pending)
            when (receipt.optString("status").lowercase()) {
                "0x1" -> TransactionNetworkState(TransactionNetworkStatus.Confirmed)
                "0x0" -> TransactionNetworkState(TransactionNetworkStatus.Rejected, "Swap reverted on-chain.")
                else -> TransactionNetworkState(TransactionNetworkStatus.Pending)
            }
        }.getOrDefault(TransactionNetworkState(TransactionNetworkStatus.Pending))
    }

    fun maxAmount(balance: TradeBalances, asset: TradeAsset): String {
        val raw = when (asset) {
            TradeAsset.ETH -> balance.ethWei.subtract(EthGasReserveWei)
            else -> balance.raw(asset)
        }.let { if (it > BigInteger.ZERO) it else BigInteger.ZERO }
        return formatAmount(raw, asset)
    }

    suspend fun fetchWoctCandles(timeframe: TradeChartTimeframe): List<TradeCandle> = withContext(Dispatchers.IO) {
        val pool = findWoctPool()
        val url = "$GeckoTerminalApi/networks/eth/pools/${pool.address}/ohlcv/${timeframe.apiFrame}" +
            "?aggregate=${timeframe.aggregate}&limit=${timeframe.limit}&currency=usd&token=${pool.tokenSide}"
        val json = getJson(url)
        val list = json.optJSONObject("data")
            ?.optJSONObject("attributes")
            ?.optJSONArray("ohlcv_list")
            ?: JSONArray()
        val candles = buildList {
            for (index in 0 until list.length()) {
                val row = list.optJSONArray(index) ?: continue
                if (row.length() < 5) continue
                add(
                    TradeCandle(
                        timestamp = row.optLong(0),
                        open = row.optDouble(1),
                        high = row.optDouble(2),
                        low = row.optDouble(3),
                        close = row.optDouble(4),
                    ),
                )
            }
        }
            .filter { it.open > 0.0 && it.high > 0.0 && it.low > 0.0 && it.close > 0.0 }
            .sortedBy { it.timestamp }
        aggregateTradeCandles(candles, timeframe)
            .also { require(it.isNotEmpty()) { "wOCT chart data unavailable." } }
    }

    fun isSupportedPair(fromAsset: TradeAsset, toAsset: TradeAsset): Boolean =
        fromAsset != toAsset &&
            (fromAsset == TradeAsset.WOCT || toAsset == TradeAsset.WOCT) &&
            (fromAsset == TradeAsset.ETH || fromAsset == TradeAsset.USDT || toAsset == TradeAsset.ETH || toAsset == TradeAsset.USDT)

    private fun poolFor(fromAsset: TradeAsset, toAsset: TradeAsset): TradePool {
        val currency0 = sortedCurrency0(fromAsset.address, toAsset.address)
        val currency1 = if (currency0.equals(fromAsset.address, ignoreCase = true)) toAsset.address else fromAsset.address
        return TradePool(
            currency0 = currency0,
            currency1 = currency1,
            zeroForOne = currency0.equals(fromAsset.address, ignoreCase = true),
            hooks = poolHooks(fromAsset, toAsset),
            inputCurrency = fromAsset.address,
            outputCurrency = toAsset.address,
        )
    }

    private fun poolHooks(fromAsset: TradeAsset, toAsset: TradeAsset): String {
        val isEthRoute = fromAsset == TradeAsset.ETH || toAsset == TradeAsset.ETH
        return if (isEthRoute) WoctEthV4HookAddress else ZeroAddress
    }

    private fun findWoctPool(): ChartPool {
        cachedWoctPool?.let { return it }
        return loadWoctPools()
            .maxByOrNull { it.reserveUsd }
            ?.also { cachedWoctPool = it }
            ?: throw IllegalStateException("wOCT chart pool unavailable.")
    }

    private fun findTradePoolLiquidity(fromAsset: TradeAsset, toAsset: TradeAsset): PoolLiquidity? =
        findDexScreenerPoolLiquidity(fromAsset, toAsset) ?: findGeckoTerminalPoolLiquidity(fromAsset, toAsset)

    private fun findDexScreenerPoolLiquidity(fromAsset: TradeAsset, toAsset: TradeAsset): PoolLiquidity? {
        val otherAsset = if (fromAsset == TradeAsset.WOCT) toAsset else fromAsset
        val otherToken = dexScreenerTokenAddress(otherAsset).cleanHex().lowercase()
        return loadDexScreenerPools()
            .filter { pool ->
                pool.baseTokenAddress.contains(otherToken, ignoreCase = true) ||
                    pool.quoteTokenAddress.contains(otherToken, ignoreCase = true)
            }
            .maxByOrNull { it.reserveUsd }
            ?.takeIf { it.reserveUsd > BigDecimal.ZERO }
            ?.let { PoolLiquidity(reserveUsd = it.reserveUsd, source = "Dexscreener") }
    }

    private fun findGeckoTerminalPoolLiquidity(fromAsset: TradeAsset, toAsset: TradeAsset): PoolLiquidity? {
        val otherAsset = if (fromAsset == TradeAsset.WOCT) toAsset else fromAsset
        val otherToken = geckoTerminalTokenAddress(otherAsset).cleanHex().lowercase()
        val matched = loadWoctPools()
            .filter { pool ->
                pool.baseTokenId.contains(otherToken, ignoreCase = true) ||
                    pool.quoteTokenId.contains(otherToken, ignoreCase = true)
            }
            .maxByOrNull { it.reserveUsd }
        val reserve = matched?.reserveUsd ?: findWoctPool().reserveUsd
        return reserve
            .takeIf { it > BigDecimal.ZERO }
            ?.let { PoolLiquidity(reserveUsd = it, source = "GeckoTerminal") }
    }

    private fun loadWoctPools(): List<ChartPool> {
        cachedWoctPools?.let { return it }
        val json = getJson("$GeckoTerminalApi/networks/eth/tokens/${TradeAsset.WOCT.address}/pools?page=1")
        val data = json.optJSONArray("data") ?: JSONArray()
        val candidates = buildList {
            for (index in 0 until data.length()) {
                val pool = data.optJSONObject(index) ?: continue
                val id = pool.optString("id").substringAfter("eth_", "")
                if (id.isBlank()) continue
                val attributes = pool.optJSONObject("attributes") ?: JSONObject()
                val reserveUsd = attributes.optString("reserve_in_usd").toBigDecimalOrNull() ?: BigDecimal.ZERO
                val relationships = pool.optJSONObject("relationships") ?: JSONObject()
                val baseId = relationships.optJSONObject("base_token")
                    ?.optJSONObject("data")
                    ?.optString("id")
                    .orEmpty()
                    .lowercase()
                val quoteId = relationships.optJSONObject("quote_token")
                    ?.optJSONObject("data")
                    ?.optString("id")
                    .orEmpty()
                    .lowercase()
                val woctClean = TradeAsset.WOCT.address.cleanHex().lowercase()
                val tokenSide = if (baseId.contains(woctClean)) "base" else "quote"
                add(
                    ChartPool(
                        address = id,
                        tokenSide = tokenSide,
                        reserveUsd = reserveUsd,
                        baseTokenId = baseId,
                        quoteTokenId = quoteId,
                    ),
                )
            }
        }
        require(candidates.isNotEmpty()) { "wOCT pool data unavailable." }
        cachedWoctPools = candidates
        return candidates
    }

    private fun loadDexScreenerPools(): List<DexScreenerPool> {
        cachedDexScreenerPools?.let { return it }
        val json = getJson("$DexScreenerApi/latest/dex/tokens/${TradeAsset.WOCT.address}")
        val pairs = json.optJSONArray("pairs") ?: JSONArray()
        val candidates = buildList {
            for (index in 0 until pairs.length()) {
                val pair = pairs.optJSONObject(index) ?: continue
                if (!pair.optString("chainId").equals("ethereum", ignoreCase = true)) continue
                val liquidity = pair.optJSONObject("liquidity")
                    ?.optString("usd")
                    ?.toBigDecimalOrNull()
                    ?: continue
                val baseToken = pair.optJSONObject("baseToken") ?: JSONObject()
                val quoteToken = pair.optJSONObject("quoteToken") ?: JSONObject()
                add(
                    DexScreenerPool(
                        reserveUsd = liquidity,
                        baseTokenAddress = baseToken.optString("address").lowercase(),
                        quoteTokenAddress = quoteToken.optString("address").lowercase(),
                    ),
                )
            }
        }
        require(candidates.isNotEmpty()) { "wOCT Dexscreener pool data unavailable." }
        cachedDexScreenerPools = candidates
        return candidates
    }

    private fun quoteRaw(pool: TradePool, amountIn: BigInteger): RawTradeQuote {
        val calldata = quoteExactInputSingleCalldata(pool, amountIn)
        val result = evmString(
            "eth_call",
            JSONArray()
                .put(JSONObject().put("to", V4QuoterAddress).put("data", calldata))
                .put("latest"),
        ).cleanHex()
        require(result.length >= 128) { "Quote returned empty result." }
        return RawTradeQuote(
            amountOut = BigInteger(result.substring(0, 64), 16),
            gasEstimate = BigInteger(result.substring(64, 128), 16),
        )
    }

    private fun estimatePriceImpactBps(
        pool: TradePool,
        amountIn: BigInteger,
        amountOut: BigInteger,
        fromAsset: TradeAsset,
    ): Int? {
        if (amountIn <= BigInteger.ONE || amountOut <= BigInteger.ZERO) return 0
        val sampleAmount = referenceQuoteAmount(amountIn, fromAsset)
        if (sampleAmount <= BigInteger.ZERO || sampleAmount >= amountIn) return 0
        val sampleOut = runCatching { quoteRaw(pool, sampleAmount).amountOut }.getOrNull()
            ?.takeIf { it > BigInteger.ZERO }
            ?: return null
        val expectedOut = sampleOut.multiply(amountIn).divide(sampleAmount)
        if (expectedOut <= BigInteger.ZERO || amountOut >= expectedOut) return 0
        return expectedOut
            .subtract(amountOut)
            .multiply(BigInteger.valueOf(10_000L))
            .divide(expectedOut)
            .toInt()
            .coerceIn(0, 10_000)
    }

    private fun referenceQuoteAmount(amountIn: BigInteger, asset: TradeAsset): BigInteger {
        val oneWhole = BigInteger.TEN.pow(asset.decimals)
        val oneHundredth = oneWhole.divide(BigInteger.valueOf(100L)).coerceAtLeast(BigInteger.ONE)
        val onePercentOfTrade = amountIn.divide(BigInteger.valueOf(100L)).coerceAtLeast(BigInteger.ONE)
        val sample = if (oneHundredth < onePercentOfTrade) oneHundredth else onePercentOfTrade
        return if (sample < amountIn) sample else amountIn
    }

    private fun dexScreenerTokenAddress(asset: TradeAsset): String = asset.address

    private fun geckoTerminalTokenAddress(asset: TradeAsset): String = when (asset) {
        TradeAsset.ETH -> WrappedEthAddress
        else -> asset.address
    }

    private fun formatPriceImpact(priceImpactBps: Int?): String = when {
        priceImpactBps == null -> "Unavailable"
        priceImpactBps == 0 -> "<0.01%"
        else -> {
            val percent = BigDecimal.valueOf(priceImpactBps.toLong())
                .divide(BigDecimal.valueOf(100L), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros()
                .toPlainString()
            "$percent%"
        }
    }

    private fun poolLiquidityLabel(liquidity: PoolLiquidity?, priceImpactBps: Int?): String {
        val depthLabel = when {
            priceImpactBps == null -> "Depth unknown"
            priceImpactBps >= 500 -> "Very thin depth"
            priceImpactBps >= 300 -> "Thin depth"
            priceImpactBps >= 100 -> "Moderate depth"
            else -> "Healthy depth"
        }
        val reserveLabel = liquidity?.reserveUsd
            ?.takeIf { it > BigDecimal.ZERO }
            ?.let { formatUsdCompact(it) }
        return if (reserveLabel == null) depthLabel else "$reserveLabel ${liquidity.source} / $depthLabel"
    }

    private fun tradeQuoteRisk(priceImpactBps: Int?, reserveUsd: BigDecimal?): TradeQuoteRisk = when {
        priceImpactBps != null && priceImpactBps >= 500 -> TradeQuoteRisk.Critical
        priceImpactBps != null && priceImpactBps >= 300 -> TradeQuoteRisk.Warning
        reserveUsd != null && reserveUsd > BigDecimal.ZERO && reserveUsd < BigDecimal("50000") -> TradeQuoteRisk.Warning
        priceImpactBps != null && priceImpactBps >= 100 -> TradeQuoteRisk.Notice
        reserveUsd != null && reserveUsd > BigDecimal.ZERO && reserveUsd < BigDecimal("250000") -> TradeQuoteRisk.Notice
        else -> TradeQuoteRisk.None
    }

    private fun tradeQuoteRiskWarning(priceImpactBps: Int?, reserveUsd: BigDecimal?): String? = when {
        priceImpactBps != null && priceImpactBps >= 500 ->
            "Very high price impact. Consider lowering the amount or waiting for deeper liquidity."
        priceImpactBps != null && priceImpactBps >= 300 ->
            "High price impact. Review the minimum received before confirming."
        reserveUsd != null && reserveUsd > BigDecimal.ZERO && reserveUsd < BigDecimal("50000") ->
            "Pool liquidity looks thin. The final received amount may move quickly."
        priceImpactBps != null && priceImpactBps >= 100 ->
            "Price impact is above 1%. Confirm only if the received amount is acceptable."
        else -> null
    }

    private fun mevRouteLabel(): String {
        val rpc = activeEthereumRpcUrl.lowercase()
        return when {
            rpc.contains("rpc.xme.my.id") || rpc.contains("infura.io/v3") -> "Protected (Infura MEV)"
            rpc.contains("publicnode") || rpc.contains("llamarpc") || rpc.contains("ankr") -> "Public mempool"
            else -> "Unknown"
        }
    }

    private fun formatUsdCompact(value: BigDecimal): String {
        val absValue = value.abs()
        val million = BigDecimal("1000000")
        val thousand = BigDecimal("1000")
        val (divisor, suffix) = when {
            absValue >= million -> million to "M"
            absValue >= thousand -> thousand to "K"
            else -> BigDecimal.ONE to ""
        }
        val formatted = value
            .divide(divisor, if (divisor == BigDecimal.ONE) 0 else 2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        return "\$$formatted$suffix"
    }

    private fun quoteExactInputSingleCalldata(pool: TradePool, amountIn: BigInteger): String {
        val tupleHead = listOf(
            addressWord(pool.currency0),
            addressWord(pool.currency1),
            BigInteger.valueOf(PoolFee.toLong()).toAbiWord(),
            signedInt24Word(PoolTickSpacing),
            addressWord(pool.hooks),
            boolWord(pool.zeroForOne),
            amountIn.toAbiWord(),
            BigInteger.valueOf(256L).toAbiWord(),
        ).joinToString("")
        val hookData = BigInteger.ZERO.toAbiWord()
        return "0x${selector("quoteExactInputSingle(((address,address,uint24,int24,address),bool,uint128,bytes))")}" +
            BigInteger.valueOf(32L).toAbiWord() +
            tupleHead +
            hookData
    }

    private fun universalRouterExecuteCalldata(pool: TradePool, amountIn: BigInteger, minimumOut: BigInteger): String {
        val commands = byteArrayOf(V4SwapCommand.toByte())
        val actions = byteArrayOf(ActionSwapExactInSingle, ActionSettleAll, ActionTakeAll)
        val swapParams = encodeExactInputSingleParams(pool, amountIn, minimumOut)
        val settleParams = abiEncodeStatic(listOf(addressWord(pool.inputCurrency), MaxUint256Word))
        val takeParams = abiEncodeStatic(listOf(addressWord(pool.outputCurrency), BigInteger.ZERO.toAbiWord()))
        val routerInput = abiEncodeBytesAndBytesArray(actions, listOf(swapParams, settleParams, takeParams))
        val inputs = abiEncodeBytesArray(listOf(routerInput.hexToBytes()))
        val commandsEncoded = abiEncodeBytes(commands)
        val inputsOffset = BigInteger.valueOf((96 + commandsEncoded.length / 2).toLong()).toAbiWord()
        val deadline = BigInteger.valueOf(System.currentTimeMillis() / 1000L + 1_200L).toAbiWord()
        return "0x${selector("execute(bytes,bytes[],uint256)")}" +
            BigInteger.valueOf(96L).toAbiWord() +
            inputsOffset +
            deadline +
            commandsEncoded +
            inputs
    }

    private fun encodeExactInputSingleParams(pool: TradePool, amountIn: BigInteger, minimumOut: BigInteger): String {
        val head = listOf(
            addressWord(pool.currency0),
            addressWord(pool.currency1),
            BigInteger.valueOf(PoolFee.toLong()).toAbiWord(),
            signedInt24Word(PoolTickSpacing),
            addressWord(pool.hooks),
            boolWord(pool.zeroForOne),
            amountIn.toAbiWord(),
            minimumOut.toAbiWord(),
            BigInteger.ZERO.toAbiWord(),
            BigInteger.valueOf(320L).toAbiWord(),
        ).joinToString("")
        return BigInteger.valueOf(32L).toAbiWord() + head + BigInteger.ZERO.toAbiWord()
    }

    private fun ensureErc20Approval(
        wallet: Wallet,
        owner: String,
        asset: TradeAsset,
        amount: BigInteger,
        gasPriceWeiOverride: BigInteger?,
        nonceProvider: () -> BigInteger,
    ): List<TradeSubmittedTransaction> {
        val current = erc20AllowanceRaw(asset.address, owner, Permit2Address)
        if (current >= amount) return emptyList()
        val approvals = mutableListOf<TradeSubmittedTransaction>()
        if (asset == TradeAsset.USDT && current > BigInteger.ZERO) {
            val reset = sendEvmTransaction(
                wallet = wallet,
                from = owner,
                toAddress = asset.address,
                dataHex = "0x095ea7b3${addressWord(Permit2Address)}${BigInteger.ZERO.toAbiWord()}",
                valueWei = BigInteger.ZERO,
                fallbackGas = ApproveGasLimit,
                nonceOverride = nonceProvider(),
                gasPriceWeiOverride = gasPriceWeiOverride,
            )
            approvals += TradeSubmittedTransaction(TradeSwapStage.TokenApproval, reset)
        }
        val approval = sendEvmTransaction(
            wallet = wallet,
            from = owner,
            toAddress = asset.address,
            dataHex = "0x095ea7b3${addressWord(Permit2Address)}${MaxUint256Word}",
            valueWei = BigInteger.ZERO,
            fallbackGas = ApproveGasLimit,
            nonceOverride = nonceProvider(),
            gasPriceWeiOverride = gasPriceWeiOverride,
        )
        approvals += TradeSubmittedTransaction(TradeSwapStage.TokenApproval, approval)
        return approvals
    }

    private fun approvePermit2(
        wallet: Wallet,
        asset: TradeAsset,
        amount: BigInteger,
        gasPriceWeiOverride: BigInteger?,
        nonceOverride: BigInteger,
    ): EvmSendResult {
        val expiration = BigInteger.valueOf(System.currentTimeMillis() / 1000L + 2_592_000L)
        val calldata = "0x${selector("approve(address,address,uint160,uint48)")}" +
            addressWord(asset.address) +
            addressWord(UniversalRouterAddress) +
            amount.toAbiWord() +
            expiration.toAbiWord()
        return sendEvmTransaction(
            wallet = wallet,
            from = deriveAddress(wallet),
            toAddress = Permit2Address,
            dataHex = calldata,
            valueWei = BigInteger.ZERO,
            fallbackGas = ApproveGasLimit,
            nonceOverride = nonceOverride,
            gasPriceWeiOverride = gasPriceWeiOverride,
        )
    }

    private fun sendEvmTransaction(
        wallet: Wallet,
        from: String,
        toAddress: String,
        dataHex: String,
        valueWei: BigInteger,
        fallbackGas: BigInteger,
        nonceOverride: BigInteger? = null,
        gasPriceWeiOverride: BigInteger? = null,
    ): EvmSendResult {
        val nonce = nonceOverride ?: evmQuantity("eth_getTransactionCount", JSONArray().put(from).put("pending"))
        val gasPrice = gasPriceWeiOverride ?: evmQuantity("eth_gasPrice", JSONArray())
        val gasLimit = estimateGas(from, toAddress, dataHex, valueWei, fallbackGas)
        val nativeBalance = evmQuantity("eth_getBalance", JSONArray().put(from).put("latest"))
        require(nativeBalance >= valueWei.add(gasPrice.multiply(gasLimit))) { "Insufficient ETH for amount plus gas." }
        val rawTx = crypto.signLegacyTransaction(
            wallet = wallet,
            nonce = nonce,
            gasPriceWei = gasPrice,
            gasLimit = gasLimit,
            toAddress = toAddress,
            valueWei = valueWei,
            data = dataHex.hexToBytes(),
            chainId = 1L,
        )
        val hash = evmString("eth_sendRawTransaction", JSONArray().put(rawTx))
        return EvmSendResult(
            success = true,
            hash = hash,
            nonce = nonce,
            gasPriceWei = gasPrice,
            gasLimit = gasLimit,
            toAddress = toAddress,
            dataHex = dataHex,
            valueWei = valueWei,
        )
    }

    private fun estimateGas(
        from: String,
        toAddress: String,
        dataHex: String,
        valueWei: BigInteger,
        fallbackGas: BigInteger,
    ): BigInteger {
        return runCatching {
            val tx = JSONObject()
                .put("from", from)
                .put("to", toAddress)
                .put("value", valueWei.toQuantity())
                .put("data", dataHex)
            evmQuantity("eth_estimateGas", JSONArray().put(tx))
                .multiply(BigInteger.valueOf(120))
                .divide(BigInteger.valueOf(100))
                .coerceAtLeast(fallbackGas)
        }.getOrDefault(fallbackGas)
    }

    private fun erc20BalanceRaw(tokenAddress: String, ownerAddress: String): BigInteger {
        val data = "0x70a08231${addressWord(ownerAddress)}"
        return evmString(
            "eth_call",
            JSONArray()
                .put(JSONObject().put("to", tokenAddress).put("data", data))
                .put("latest"),
        ).hexQuantityToBigInteger()
    }

    private fun erc20AllowanceRaw(tokenAddress: String, ownerAddress: String, spenderAddress: String): BigInteger {
        val data = "0xdd62ed3e${addressWord(ownerAddress)}${addressWord(spenderAddress)}"
        return evmString(
            "eth_call",
            JSONArray()
                .put(JSONObject().put("to", tokenAddress).put("data", data))
                .put("latest"),
        ).hexQuantityToBigInteger()
    }

    private fun evmQuantity(method: String, params: JSONArray): BigInteger =
        evmString(method, params).hexQuantityToBigInteger()

    private fun evmString(method: String, params: JSONArray): String {
        val result = evmRaw(method, params)
        return result?.toString() ?: throw IllegalStateException("$method returned empty result.")
    }

    private fun evmRaw(method: String, params: JSONArray): Any? {
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", System.currentTimeMillis())
            .put("method", method)
            .put("params", params)
        val connection = (URL(activeEthereumRpcUrl).openConnection() as HttpURLConnection).apply {
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
            val stream = if (responseCode in 200..299) connection.inputStream else connection.errorStream ?: connection.inputStream
            val body = stream.bufferedReader().use { it.readText() }
            if (responseCode !in 200..299) throw IllegalStateException("EVM RPC HTTP $responseCode")
            val json = JSONObject(body)
            json.optJSONObject("error")?.let { error ->
                throw IllegalStateException(error.optString("message", "EVM RPC error."))
            }
            if (json.has("result")) json.get("result") else null
        } finally {
            connection.disconnect()
        }
    }

    private fun getJson(url: String): JSONObject {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 12_000
            readTimeout = 12_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", PoctraUserAgent)
        }
        return try {
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
            val body = stream.bufferedReader().use { it.readText() }
            if (responseCode !in 200..299) throw IllegalStateException("Chart API HTTP $responseCode")
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun abiEncodeBytesAndBytesArray(bytesValue: ByteArray, array: List<String>): String =
        BigInteger.valueOf(64L).toAbiWord() +
            BigInteger.valueOf((64 + abiEncodeBytes(bytesValue).length / 2).toLong()).toAbiWord() +
            abiEncodeBytes(bytesValue) +
            abiEncodeBytesArray(array.map { it.hexToBytes() })

    private fun abiEncodeBytesArray(items: List<ByteArray>): String {
        val headsSize = items.size * 32
        val encodedItems = items.map(::abiEncodeBytes)
        var cursor = headsSize
        val offsets = encodedItems.map { encoded ->
            val offset = BigInteger.valueOf(cursor.toLong()).toAbiWord()
            cursor += encoded.length / 2
            offset
        }
        return BigInteger.valueOf(items.size.toLong()).toAbiWord() +
            offsets.joinToString("") +
            encodedItems.joinToString("")
    }

    private fun abiEncodeBytes(bytes: ByteArray): String {
        val hex = bytes.toHex()
        val paddedLength = ((bytes.size + 31) / 32) * 64
        return BigInteger.valueOf(bytes.size.toLong()).toAbiWord() + hex.padEnd(paddedLength, '0')
    }

    private fun abiEncodeStatic(words: List<String>): String = words.joinToString("")

    private fun selector(signature: String): String =
        EvmCrypto.keccak(signature.toByteArray(Charsets.UTF_8)).take(4).toByteArray().toHex()

    private fun sortedCurrency0(a: String, b: String): String {
        val aValue = BigInteger(a.cleanHex().ifBlank { "0" }, 16)
        val bValue = BigInteger(b.cleanHex().ifBlank { "0" }, 16)
        return if (aValue <= bValue) a else b
    }

    private fun signedInt24Word(value: Int): String {
        val normalized = if (value >= 0) {
            BigInteger.valueOf(value.toLong())
        } else {
            BigInteger.ONE.shiftLeft(256).subtract(BigInteger.valueOf(value.absoluteValue.toLong()))
        }
        return normalized.toAbiWord()
    }

    private fun boolWord(value: Boolean): String = if (value) BigInteger.ONE.toAbiWord() else BigInteger.ZERO.toAbiWord()

    private fun addressWord(address: String): String = address.cleanHex().lowercase().padStart(64, '0')

    private fun BigInteger.toAbiWord(): String = toString(16).padStart(64, '0')

    private fun BigInteger.toQuantity(): String = if (this == BigInteger.ZERO) "0x0" else "0x${toString(16)}"

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

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    companion object {
        private const val EthereumRpcUrl = "https://rpc.xme.my.id/eth-mainnet"
        private const val GeckoTerminalApi = "https://api.geckoterminal.com/api/v2"
        private const val DexScreenerApi = "https://api.dexscreener.com"
        private const val PoctraUserAgent = "poctra-android"
        private const val UniversalRouterAddress = "0x66a9893cC07D91D95644AEDD05D03f95e1dBA8Af"
        private const val Permit2Address = "0x000000000022D473030F116dDEE9F6B43aC78BA3"
        private const val V4QuoterAddress = "0x52f0e24d1c21c8a0cb1e5a5dd6198556bd9e1203"
        private const val ZeroAddress = "0x0000000000000000000000000000000000000000"
        private const val WrappedEthAddress = "0xC02aaa39b223FE8D0A0e5C4F27ead9083C756Cc2"
        private const val WoctEthV4HookAddress = "0x890681CfF5AD2069F020027f41f5f68F6a292000"
        private const val PoolFee = 3000
        private const val PoolTickSpacing = 60
        private const val V4SwapCommand = 0x10
        private const val ActionSwapExactInSingle: Byte = 0x06
        private const val ActionSettleAll: Byte = 0x0c
        private const val ActionTakeAll: Byte = 0x0f
        private val EthGasReserveWei = BigInteger("5000000000000000")
        private val OneGweiWei = BigInteger("1000000000")
        private val ApproveGasLimit = BigInteger.valueOf(80_000L)
        private val SwapGasLimit = BigInteger.valueOf(450_000L)
        private val MaxUint256Word = "f".repeat(64)
    }
}

internal fun aggregateTradeCandles(
    candles: List<TradeCandle>,
    timeframe: TradeChartTimeframe,
): List<TradeCandle> {
    val bucketSeconds = timeframe.clientBucketSeconds ?: return candles
    return candles
        .sortedBy { it.timestamp }
        .groupBy { it.timestamp / bucketSeconds }
        .values
        .mapNotNull { bucket ->
            val ordered = bucket.sortedBy { it.timestamp }
            val first = ordered.firstOrNull() ?: return@mapNotNull null
            val last = ordered.last()
            TradeCandle(
                timestamp = first.timestamp,
                open = first.open,
                high = ordered.maxOf { it.high },
                low = ordered.minOf { it.low },
                close = last.close,
            )
        }
}

private data class TradePool(
    val currency0: String,
    val currency1: String,
    val zeroForOne: Boolean,
    val hooks: String,
    val inputCurrency: String,
    val outputCurrency: String,
)

private data class RawTradeQuote(
    val amountOut: BigInteger,
    val gasEstimate: BigInteger,
)

private data class PoolLiquidity(
    val reserveUsd: BigDecimal,
    val source: String,
)

private data class DexScreenerPool(
    val reserveUsd: BigDecimal,
    val baseTokenAddress: String,
    val quoteTokenAddress: String,
)

private data class ChartPool(
    val address: String,
    val tokenSide: String,
    val reserveUsd: BigDecimal,
    val baseTokenId: String,
    val quoteTokenId: String,
)
