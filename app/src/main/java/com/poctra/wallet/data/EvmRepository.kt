package com.poctra.wallet.data

import android.content.Context
import com.poctra.wallet.security.BackendRequestSecurity
import com.poctra.wallet.security.EvmDAppRpcPolicy
import com.poctra.wallet.core.EvmCalldata
import com.poctra.wallet.core.EvmCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock

class EvmRepository(context: Context) {
    private val requestSecurity = BackendRequestSecurity.get(context)
    private val prefs = context.applicationContext.getSharedPreferences("poctra_evm", Context.MODE_PRIVATE)
    private val crypto = EvmCrypto()
    private val nonceLocks = ConcurrentHashMap<String, ReentrantLock>()

    fun networks(): List<EvmNetwork> {
        val stored = prefs.getString(KeyNetworks, null)
        val parsed = runCatching {
            val array = JSONArray(stored ?: "[]")
            buildList {
                for (index in 0 until array.length()) {
                    add(EvmNetwork.fromJson(array.getJSONObject(index)))
                }
            }
        }.getOrDefault(emptyList())
        val networks = mergeNetworks(defaultNetworks() + parsed)
        if (stored == null || networks != parsed) persistNetworks(networks)
        return networks
    }

    fun activeNetwork(): EvmNetwork {
        val all = networks()
        val activeId = prefs.getString(KeyActiveNetwork, null)
        return all.firstOrNull { it.id == activeId }
            ?: all.firstOrNull { it.id == "eth-mainnet" }
            ?: all.first()
    }

    fun setActiveNetwork(id: String): EvmNetwork {
        val network = networks().firstOrNull { it.id == id } ?: throw IllegalArgumentException("EVM network not found.")
        prefs.edit().putString(KeyActiveNetwork, network.id).apply()
        return network
    }

    fun networkByChainId(chainId: Long): EvmNetwork? =
        networks().firstOrNull { it.chainId == chainId }

    fun saveNetworkRpc(id: String, rpcUrl: String): List<EvmNetwork> {
        require(isValidRpcUrl(rpcUrl)) { "Enter an HTTPS EVM RPC endpoint or an approved local/dev endpoint." }
        val next = networks().map { network ->
            if (network.id == id) network.copy(rpcUrl = rpcUrl.trim().trimEnd('/')) else network
        }
        persistNetworks(next)
        return next
    }

    fun saveNetwork(network: EvmNetwork): List<EvmNetwork> {
        val clean = network.copy(
            name = network.name.trim(),
            rpcUrl = network.rpcUrl.trim().trimEnd('/'),
            symbol = network.symbol.trim().uppercase().take(12),
            explorerUrl = network.explorerUrl.trim().trimEnd('/'),
        )
        require(clean.name.isNotBlank()) { "Network name is required." }
        require(clean.chainId > 0L) { "Enter a valid chain ID." }
        require(clean.symbol.isNotBlank()) { "Network symbol is required." }
        require(isValidRpcUrl(clean.rpcUrl)) { "Enter an HTTPS EVM RPC endpoint or an approved local/dev endpoint." }
        require(clean.explorerUrl.isBlank() || isValidRpcUrl(clean.explorerUrl)) {
            "Explorer URL must be HTTPS or an approved local/dev endpoint."
        }
        val next = networks().filterNot { it.id == clean.id } + clean
        persistNetworks(next)
        return next
    }

    fun deriveAddress(wallet: Wallet): String = crypto.deriveAddress(wallet)

    fun signPersonalMessage(wallet: Wallet, message: ByteArray): String =
        crypto.signPersonalMessage(wallet, message)

    fun signTypedDataHash(wallet: Wallet, signingHash: ByteArray): String =
        crypto.signRecoverableHash(wallet, signingHash)

    suspend fun requestDAppRpc(
        method: String,
        params: JSONArray,
        network: EvmNetwork = activeNetwork(),
    ): Any? = withContext(Dispatchers.IO) {
        if (!EvmDAppRpcPolicy.canForward(method)) {
            throw NoSuchMethodException("Unsupported EVM RPC method: $method")
        }
        require(isValidRpcUrl(network.rpcUrl)) {
            "Set an HTTPS or approved local/dev EVM RPC endpoint for ${network.name}."
        }
        rpcRaw(network.rpcUrl, method, params)
    }

    suspend fun verifyDAppNetwork(network: EvmNetwork) = withContext(Dispatchers.IO) {
        require(isValidRpcUrl(network.rpcUrl)) { "The requested network RPC must use HTTPS." }
        val host = URI(network.rpcUrl).host.orEmpty()
        require(host.isNotBlank()) { "The requested network RPC host is invalid." }
        val addresses = runCatching { InetAddress.getAllByName(host).toList() }
            .getOrElse { throw IllegalArgumentException("The requested network RPC host could not be resolved.") }
        require(addresses.isNotEmpty() && addresses.none(::isPrivateAddress)) {
            "dApps cannot add local or private-network RPC endpoints."
        }
        val reportedChainId = rpcQuantity(network.rpcUrl, "eth_chainId", JSONArray())
        require(reportedChainId == BigInteger.valueOf(network.chainId)) {
            "RPC chain ID ${reportedChainId.toQuantity()} does not match ${BigInteger.valueOf(network.chainId).toQuantity()}."
        }
    }

    suspend fun reviewDAppTransaction(
        wallet: Wallet,
        transaction: EvmDAppTransaction,
        network: EvmNetwork = activeNetwork(),
    ): EvmDAppTransactionReview = withContext(Dispatchers.IO) {
        require(isValidRpcUrl(network.rpcUrl)) {
            "Set an HTTPS or approved local/dev EVM RPC endpoint for ${network.name}."
        }
        val activeAddress = deriveAddress(wallet)
        require(activeAddress.equals(transaction.from, ignoreCase = true)) {
            "The transaction sender does not match the active EVM account."
        }
        require(isValidEvmAddress(transaction.to)) { "Contract creation is not supported in the dApp browser." }
        require(transaction.valueWei >= BigInteger.ZERO) { "Transaction value cannot be negative." }
        val data = transaction.dataHex.hexToBytes()
        require(data.size <= MaxDAppCalldataBytes) { "Transaction data is too large to review safely." }
        val requestedGas = transaction.gasLimit?.takeIf { it > BigInteger.ZERO }
        val gasLimit = estimateGas(
            rpcUrl = network.rpcUrl,
            from = activeAddress,
            to = transaction.to,
            valueWei = transaction.valueWei,
            data = data,
            fallbackGas = requestedGas ?: if (data.isEmpty()) DefaultNativeGasLimit else DefaultTokenGasLimit,
        )
        val gasPrice = transaction.gasPriceWei?.takeIf { it > BigInteger.ZERO }
            ?: rpcQuantity(network.rpcUrl, "eth_gasPrice", JSONArray())
        val fee = gasPrice.multiply(gasLimit)
        val balance = rpcQuantity(network.rpcUrl, "eth_getBalance", JSONArray().put(activeAddress).put("latest"))
        require(balance >= transaction.valueWei.add(fee)) {
            "Insufficient ${network.symbol} for transaction value plus gas."
        }
        EvmDAppTransactionReview(
            transaction = transaction,
            gasLimit = gasLimit,
            gasPriceWei = gasPrice,
            feeWei = fee,
        )
    }

    suspend fun sendDAppTransaction(
        wallet: Wallet,
        review: EvmDAppTransactionReview,
        network: EvmNetwork = activeNetwork(),
    ): EvmSendResult = withContext(Dispatchers.IO) {
        runCatching {
            sendEvmTransaction(
                wallet = wallet,
                network = network,
                toAddress = review.transaction.to,
                valueWei = review.transaction.valueWei,
                data = review.transaction.dataHex.hexToBytes(),
                fallbackGas = review.gasLimit,
                gasPriceWeiOverride = review.gasPriceWei,
                gasLimitOverride = review.gasLimit,
                insufficientGasMessage = "Insufficient ${network.symbol} for transaction value plus gas.",
            )
        }.getOrElse { exception ->
            EvmSendResult(success = false, error = exception.message ?: "EVM dApp transaction failed.")
        }
    }

    fun resolveRecipientAddress(value: String, network: EvmNetwork = activeNetwork()): String {
        val recipient = value.trim()
        if (isValidEvmAddress(recipient)) return recipient
        require(isLikelyEnsName(recipient)) { "Enter a valid EVM address or ENS name." }
        return resolveEnsName(recipient, network)
    }

    fun resolveRecipientIdentity(value: String, network: EvmNetwork = activeNetwork()): String? {
        val recipient = value.trim()
        if (!isValidEvmAddress(recipient)) return null
        return resolveReverseEnsName(recipient, network)
    }

    suspend fun fetchAccount(wallet: Wallet, network: EvmNetwork = activeNetwork()): EvmAccountSnapshot =
        withContext(Dispatchers.IO) {
            val address = deriveAddress(wallet)
            try {
                require(isValidRpcUrl(network.rpcUrl)) { "Set an HTTPS or approved local/dev EVM RPC endpoint for ${network.name}." }
                val balance = rpcQuantity(network.rpcUrl, "eth_getBalance", JSONArray().put(address).put("latest"))
                val gasPrice = rpcQuantity(network.rpcUrl, "eth_gasPrice", JSONArray())
                EvmAccountSnapshot(
                    address = address,
                    balance = EvmCrypto.formatEther(balance),
                    fiatEstimate = nativeFiatEstimate(balance, network.symbol),
                    gasPriceGwei = formatGwei(gasPrice),
                    connected = true,
                    ensName = resolveReverseEnsName(address, network),
                )
            } catch (exception: Exception) {
                EvmAccountSnapshot(
                    address = address,
                    balance = "--",
                    fiatEstimate = "~ $-- USD",
                    gasPriceGwei = "--",
                    connected = false,
                    message = exception.message ?: "EVM RPC unavailable.",
                )
        }
    }

    fun savedTokens(chainId: Long = activeNetwork().chainId): List<EvmToken> {
        return allTokens().filter { it.chainId == chainId }
    }

    fun savedNfts(chainId: Long = activeNetwork().chainId): List<EvmNft> {
        val stored = prefs.getString(KeyNfts, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) {
                    val nft = EvmNft.fromJson(array.getJSONObject(index))
                    if (nft.chainId == chainId) add(nft)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveToken(token: EvmToken): List<EvmToken> {
        val all = allTokens()
        val next = all.filterNot {
            it.chainId == token.chainId && it.address.equals(token.address, ignoreCase = true)
        } + token.copy(address = token.address.trim())
        persistTokens(next)
        return allTokens().filter { it.chainId == token.chainId }
    }

    fun removeToken(token: EvmToken): List<EvmToken> {
        val next = allTokens().filterNot {
            it.chainId == token.chainId && it.address.equals(token.address, ignoreCase = true)
        }
        persistTokens(next)
        return allTokens().filter { it.chainId == token.chainId }
    }

    fun saveNft(nft: EvmNft): List<EvmNft> {
        val all = allNfts()
        val next = all.filterNot {
            it.chainId == nft.chainId &&
                it.contractAddress.equals(nft.contractAddress, ignoreCase = true) &&
                it.tokenId == nft.tokenId
        } + nft.copy(contractAddress = nft.contractAddress.trim())
        persistNfts(next)
        return next.filter { it.chainId == nft.chainId }
    }

    fun removeNft(nft: EvmNft): List<EvmNft> {
        val next = allNfts().filterNot {
            it.chainId == nft.chainId &&
                it.contractAddress.equals(nft.contractAddress, ignoreCase = true) &&
                it.tokenId == nft.tokenId
        }
        persistNfts(next)
        return next.filter { it.chainId == nft.chainId }
    }

    suspend fun refreshTokens(
        ownerAddress: String,
        network: EvmNetwork = activeNetwork(),
        persistBalances: Boolean = true,
    ): List<EvmToken> =
        withContext(Dispatchers.IO) {
            savedTokens(network.chainId).chunked(TokenRefreshBatchSize).flatMap { batch ->
                coroutineScope {
                    batch.map { token ->
                        async {
                            runCatching {
                                val raw = erc20BalanceRaw(token.address, ownerAddress, network)
                                token.copy(balance = EvmCrypto.formatUnits(raw, token.decimals))
                            }.getOrElse { token.copy(balance = "0") }
                        }
                    }.awaitAll()
                }
            }.also { refreshed ->
                if (persistBalances) {
                    val others = allTokens().filterNot { it.chainId == network.chainId }
                    persistTokens(others + refreshed)
                }
            }
        }

    suspend fun refreshNfts(
        ownerAddress: String,
        network: EvmNetwork = activeNetwork(),
        persistOwnership: Boolean = true,
    ): List<EvmNft> =
        withContext(Dispatchers.IO) {
            savedNfts(network.chainId).chunked(NftRefreshBatchSize).flatMap { batch ->
                coroutineScope {
                    batch.map { nft ->
                        async {
                            val owned = runCatching {
                                checkNftOwnership(nft.contractAddress, nft.tokenId, ownerAddress, network)
                            }.getOrDefault(false)
                            nft.copy(owned = owned)
                        }
                    }.awaitAll()
                }
            }.also { refreshed ->
                if (persistOwnership) {
                    val others = allNfts().filterNot { it.chainId == network.chainId }
                    persistNfts(others + refreshed)
                }
            }
        }

    suspend fun lookupErc20Token(
        tokenAddress: String,
        ownerAddress: String,
        network: EvmNetwork = activeNetwork(),
    ): EvmToken? = withContext(Dispatchers.IO) {
        val address = tokenAddress.trim()
        if (!isValidEvmAddress(address)) return@withContext null
        val name = ethCallString(network.rpcUrl, address, "0x06fdde03").ifBlank { "Unknown Token" }
        val symbol = ethCallString(network.rpcUrl, address, "0x95d89b41").ifBlank { "TOKEN" }
        val decimalsHex = rpcString(
            network.rpcUrl,
            "eth_call",
            JSONArray()
                .put(JSONObject().put("to", address).put("data", "0x313ce567"))
                .put("latest"),
        )
        val decimals = decimalsHex.hexQuantityToBigInteger().toInt().coerceIn(0, 36)
        val balance = EvmCrypto.formatUnits(erc20BalanceRaw(address, ownerAddress, network), decimals)
        EvmToken(
            address = address,
            name = name,
            symbol = symbol,
            decimals = decimals,
            balance = balance,
            chainId = network.chainId,
        )
    }

    suspend fun lookupNft(
        contractAddress: String,
        tokenId: String,
        ownerAddress: String,
        network: EvmNetwork = activeNetwork(),
    ): EvmNft? = withContext(Dispatchers.IO) {
        val address = contractAddress.trim()
        if (!isValidEvmAddress(address) || tokenId.trim().toBigIntegerOrNull() == null) return@withContext null
        val name = ethCallString(network.rpcUrl, address, "0x06fdde03").ifBlank { "Unknown NFT" }
        val symbol = ethCallString(network.rpcUrl, address, "0x95d89b41").ifBlank { "NFT" }
        val tokenUri = runCatching {
            ethCallString(network.rpcUrl, address, "0xc87b56dd${tokenId.trim().toBigInteger().toAbiWord()}")
        }.getOrDefault("").ifBlank { null }
        EvmNft(
            contractAddress = address,
            tokenId = tokenId.trim(),
            name = name,
            symbol = symbol,
            tokenUri = tokenUri,
            chainId = network.chainId,
            owned = checkNftOwnership(address, tokenId.trim(), ownerAddress, network),
        )
    }

    suspend fun sendNative(
        wallet: Wallet,
        recipientAddress: String,
        amount: String,
        network: EvmNetwork = activeNetwork(),
        gasPriceWeiOverride: BigInteger? = null,
    ): EvmSendResult = withContext(Dispatchers.IO) {
        runCatching {
            require(isValidRpcUrl(network.rpcUrl)) { "Set an HTTPS or approved local/dev EVM RPC endpoint for ${network.name}." }
            val recipient = resolveRecipientAddress(recipientAddress, network)
            val from = deriveAddress(wallet)
            require(!from.equals(recipient, ignoreCase = true)) { "Cannot send to the active EVM address." }

            val valueWei = EvmCrypto.parseEther(amount)
            require(valueWei > BigInteger.ZERO) { "Amount must be greater than zero." }

            val balance = rpcQuantity(network.rpcUrl, "eth_getBalance", JSONArray().put(from).put("latest"))
            val gasPrice = gasPriceWeiOverride?.takeIf { it > BigInteger.ZERO }
                ?: rpcQuantity(network.rpcUrl, "eth_gasPrice", JSONArray())
            val gasLimit = estimateGas(network.rpcUrl, from, recipient, valueWei, fallbackGas = DefaultNativeGasLimit)
            val gasCost = gasPrice.multiply(gasLimit)
            require(balance >= valueWei.add(gasCost)) {
                "Insufficient ${network.symbol} for amount plus gas."
            }
            sendEvmTransaction(
                wallet = wallet,
                network = network,
                toAddress = recipient,
                valueWei = valueWei,
                data = ByteArray(0),
                fallbackGas = gasLimit,
                gasPriceWeiOverride = gasPrice,
                insufficientGasMessage = "Insufficient ${network.symbol} for amount plus gas.",
            )
        }.getOrElse { exception ->
            EvmSendResult(success = false, error = exception.message ?: "EVM transaction failed.")
        }
    }

    suspend fun estimateNativeTransferGas(
        wallet: Wallet,
        recipientAddress: String,
        amount: String,
        network: EvmNetwork = activeNetwork(),
    ): EvmGasEstimate = withContext(Dispatchers.IO) {
        require(isValidRpcUrl(network.rpcUrl)) { "Set an HTTPS or approved local/dev EVM RPC endpoint for ${network.name}." }
        val recipient = resolveRecipientAddress(recipientAddress, network)
        val from = deriveAddress(wallet)
        val valueWei = EvmCrypto.parseEther(amount)
        require(valueWei > BigInteger.ZERO) { "Amount must be greater than zero." }
        val gasLimit = estimateGas(network.rpcUrl, from, recipient, valueWei, fallbackGas = DefaultNativeGasLimit)
        buildGasEstimate(network, gasLimit, "Native transfer")
    }

    suspend fun sendErc20(
        wallet: Wallet,
        token: EvmToken,
        recipientAddress: String,
        amount: String,
        network: EvmNetwork = activeNetwork(),
        gasPriceWeiOverride: BigInteger? = null,
    ): EvmSendResult = withContext(Dispatchers.IO) {
        runCatching {
            require(isValidRpcUrl(network.rpcUrl)) { "Set an HTTPS or approved local/dev EVM RPC endpoint for ${network.name}." }
            val recipient = resolveRecipientAddress(recipientAddress, network)
            val from = deriveAddress(wallet)
            val amountRaw = EvmCrypto.parseUnits(amount, token.decimals)
            require(amountRaw > BigInteger.ZERO) { "Amount must be greater than zero." }
            val balanceRaw = erc20BalanceRaw(token.address, from, network)
            require(balanceRaw >= amountRaw) { "Insufficient ${token.symbol} balance." }

            val data = erc20TransferData(recipient, amountRaw)
            val gasPrice = gasPriceWeiOverride?.takeIf { it > BigInteger.ZERO }
                ?: rpcQuantity(network.rpcUrl, "eth_gasPrice", JSONArray())
            val gasLimit = estimateGas(network.rpcUrl, from, token.address, BigInteger.ZERO, data, DefaultTokenGasLimit)
            val nativeBalance = rpcQuantity(network.rpcUrl, "eth_getBalance", JSONArray().put(from).put("latest"))
            require(nativeBalance >= gasPrice.multiply(gasLimit)) { "Insufficient ${network.symbol} for token transfer gas." }
            sendEvmTransaction(
                wallet = wallet,
                network = network,
                toAddress = token.address,
                valueWei = BigInteger.ZERO,
                data = data,
                fallbackGas = gasLimit,
                gasPriceWeiOverride = gasPrice,
                insufficientGasMessage = "Insufficient ${network.symbol} for token transfer gas.",
            )
        }.getOrElse { exception ->
            EvmSendResult(success = false, error = exception.message ?: "ERC20 transfer failed.")
        }
    }

    suspend fun estimateErc20TransferGas(
        wallet: Wallet,
        token: EvmToken,
        recipientAddress: String,
        amount: String,
        network: EvmNetwork = activeNetwork(),
    ): EvmGasEstimate = withContext(Dispatchers.IO) {
        require(isValidRpcUrl(network.rpcUrl)) { "Set an HTTPS or approved local/dev EVM RPC endpoint for ${network.name}." }
        val recipient = resolveRecipientAddress(recipientAddress, network)
        val from = deriveAddress(wallet)
        val amountRaw = EvmCrypto.parseUnits(amount, token.decimals)
        require(amountRaw > BigInteger.ZERO) { "Amount must be greater than zero." }
        val data = erc20TransferData(recipient, amountRaw)
        val gasLimit = estimateGas(network.rpcUrl, from, token.address, BigInteger.ZERO, data, DefaultTokenGasLimit)
        buildGasEstimate(network, gasLimit, "${token.symbol} transfer")
    }

    suspend fun sendNft(
        wallet: Wallet,
        nft: EvmNft,
        recipientAddress: String,
        network: EvmNetwork = activeNetwork(),
        gasPriceWeiOverride: BigInteger? = null,
    ): EvmSendResult = withContext(Dispatchers.IO) {
        runCatching {
            require(isValidRpcUrl(network.rpcUrl)) { "Set an HTTPS or approved local/dev EVM RPC endpoint for ${network.name}." }
            val recipient = resolveRecipientAddress(recipientAddress, network)
            val from = deriveAddress(wallet)
            require(!from.equals(recipient, ignoreCase = true)) { "Cannot send to the active EVM address." }
            require(checkNftOwnership(nft.contractAddress, nft.tokenId, from, network)) { "This NFT is not owned by the active EVM account." }
            val data = erc721SafeTransferData(from, recipient, nft.tokenId)
            val gasPrice = gasPriceWeiOverride?.takeIf { it > BigInteger.ZERO }
                ?: rpcQuantity(network.rpcUrl, "eth_gasPrice", JSONArray())
            val gasLimit = estimateGas(network.rpcUrl, from, nft.contractAddress, BigInteger.ZERO, data, DefaultNftGasLimit)
            val nativeBalance = rpcQuantity(network.rpcUrl, "eth_getBalance", JSONArray().put(from).put("latest"))
            require(nativeBalance >= gasPrice.multiply(gasLimit)) { "Insufficient ${network.symbol} for NFT transfer gas." }
            sendEvmTransaction(
                wallet = wallet,
                network = network,
                toAddress = nft.contractAddress,
                valueWei = BigInteger.ZERO,
                data = data,
                fallbackGas = gasLimit,
                gasPriceWeiOverride = gasPrice,
                insufficientGasMessage = "Insufficient ${network.symbol} for NFT transfer gas.",
            )
        }.getOrElse { exception ->
            EvmSendResult(success = false, error = exception.message ?: "NFT transfer failed.")
        }
    }

    suspend fun estimateNftTransferGas(
        wallet: Wallet,
        nft: EvmNft,
        recipientAddress: String,
        network: EvmNetwork = activeNetwork(),
    ): EvmGasEstimate = withContext(Dispatchers.IO) {
        require(isValidRpcUrl(network.rpcUrl)) { "Set an HTTPS or approved local/dev EVM RPC endpoint for ${network.name}." }
        val recipient = resolveRecipientAddress(recipientAddress, network)
        val from = deriveAddress(wallet)
        val data = erc721SafeTransferData(from, recipient, nft.tokenId)
        val gasLimit = estimateGas(network.rpcUrl, from, nft.contractAddress, BigInteger.ZERO, data, DefaultNftGasLimit)
        buildGasEstimate(network, gasLimit, "${nft.symbol} NFT transfer")
    }

    suspend fun erc20Allowance(
        token: EvmToken,
        ownerAddress: String,
        spenderAddress: String,
        network: EvmNetwork = activeNetwork(),
    ): BigInteger = withContext(Dispatchers.IO) {
        require(isValidRpcUrl(network.rpcUrl)) { "Set an HTTPS or approved local/dev EVM RPC endpoint for ${network.name}." }
        require(isValidEvmAddress(ownerAddress)) { "Owner address is invalid." }
        require(isValidEvmAddress(spenderAddress)) { "Spender address is invalid." }
        erc20AllowanceRaw(token.address, ownerAddress, spenderAddress, network)
    }

    suspend fun estimateRevokeAllowanceGas(
        wallet: Wallet,
        token: EvmToken,
        spenderAddress: String,
        network: EvmNetwork = activeNetwork(),
    ): EvmGasEstimate = withContext(Dispatchers.IO) {
        require(isValidRpcUrl(network.rpcUrl)) { "Set an HTTPS or approved local/dev EVM RPC endpoint for ${network.name}." }
        require(isValidEvmAddress(spenderAddress)) { "Spender address is invalid." }
        val from = deriveAddress(wallet)
        val data = erc20ApproveData(spenderAddress, BigInteger.ZERO)
        val gasLimit = estimateGas(network.rpcUrl, from, token.address, BigInteger.ZERO, data, DefaultTokenGasLimit)
        buildGasEstimate(network, gasLimit, "Revoke ${token.symbol}")
    }

    suspend fun revokeErc20Allowance(
        wallet: Wallet,
        token: EvmToken,
        spenderAddress: String,
        network: EvmNetwork = activeNetwork(),
        gasPriceWeiOverride: BigInteger? = null,
    ): EvmSendResult = withContext(Dispatchers.IO) {
        runCatching {
            require(isValidRpcUrl(network.rpcUrl)) { "Set an HTTPS or approved local/dev EVM RPC endpoint for ${network.name}." }
            require(isValidEvmAddress(spenderAddress)) { "Spender address is invalid." }
            val from = deriveAddress(wallet)
            val data = erc20ApproveData(spenderAddress, BigInteger.ZERO)
            val gasPrice = gasPriceWeiOverride?.takeIf { it > BigInteger.ZERO }
                ?: rpcQuantity(network.rpcUrl, "eth_gasPrice", JSONArray())
            val gasLimit = estimateGas(network.rpcUrl, from, token.address, BigInteger.ZERO, data, DefaultTokenGasLimit)
            val nativeBalance = rpcQuantity(network.rpcUrl, "eth_getBalance", JSONArray().put(from).put("latest"))
            require(nativeBalance >= gasPrice.multiply(gasLimit)) { "Insufficient ${network.symbol} for revoke gas." }
            sendEvmTransaction(
                wallet = wallet,
                network = network,
                toAddress = token.address,
                valueWei = BigInteger.ZERO,
                data = data,
                fallbackGas = gasLimit,
                gasPriceWeiOverride = gasPrice,
                insufficientGasMessage = "Insufficient ${network.symbol} for revoke gas.",
            )
        }.getOrElse { exception ->
            EvmSendResult(success = false, error = exception.message ?: "Revoke approval failed.")
        }
    }

    suspend fun speedUpTransaction(
        wallet: Wallet,
        pending: EvmSendResult,
        network: EvmNetwork = activeNetwork(),
    ): EvmSendResult = withContext(Dispatchers.IO) {
        runCatching {
            val nonce = pending.nonce ?: throw IllegalArgumentException("Pending transaction nonce is unavailable.")
            val toAddress = pending.toAddress ?: throw IllegalArgumentException("Pending transaction target is unavailable.")
            val dataHex = pending.dataHex ?: "0x"
            val currentGas = pending.gasPriceWei ?: rpcQuantity(network.rpcUrl, "eth_gasPrice", JSONArray())
            val networkGas = rpcQuantity(network.rpcUrl, "eth_gasPrice", JSONArray())
            val bumpedGasPrice = listOf(
                currentGas.multiply(BigInteger.valueOf(125L)).divide(BigInteger.valueOf(100L)).add(OneGweiWei),
                networkGas.multiply(BigInteger.valueOf(125L)).divide(BigInteger.valueOf(100L)),
            ).maxOrNull()?.coerceAtLeast(BigInteger.ONE) ?: currentGas.add(OneGweiWei)
            sendEvmTransaction(
                wallet = wallet,
                network = network,
                toAddress = toAddress,
                valueWei = pending.valueWei,
                data = dataHex.hexToBytes(),
                fallbackGas = pending.gasLimit ?: DefaultTokenGasLimit,
                gasPriceWeiOverride = bumpedGasPrice,
                nonceOverride = nonce,
                insufficientGasMessage = "Insufficient ${network.symbol} to speed up this transaction.",
            )
        }.getOrElse { exception ->
            EvmSendResult(success = false, error = exception.message ?: "Speed up transaction failed.")
        }
    }

    suspend fun transactionReceiptStatus(hash: String, network: EvmNetwork = activeNetwork()): TransactionNetworkState =
        withContext(Dispatchers.IO) {
            runCatching {
                val result = rpcRaw(network.rpcUrl, "eth_getTransactionReceipt", JSONArray().put(hash))
                if (result == null || result == JSONObject.NULL) return@runCatching TransactionNetworkState(TransactionNetworkStatus.Pending)
                val receipt = result as? JSONObject ?: return@runCatching TransactionNetworkState(TransactionNetworkStatus.Pending)
                when (receipt.optString("status").lowercase()) {
                    "0x1" -> TransactionNetworkState(TransactionNetworkStatus.Confirmed)
                    "0x0" -> TransactionNetworkState(TransactionNetworkStatus.Rejected, "EVM transaction reverted.")
                    else -> TransactionNetworkState(TransactionNetworkStatus.Pending)
                }
            }.getOrDefault(TransactionNetworkState(TransactionNetworkStatus.Pending))
        }

    @Suppress("UNUSED_PARAMETER")
    suspend fun fetchTransactions(
        address: String,
        network: EvmNetwork = activeNetwork(),
        tokenAddresses: List<String> = emptyList(),
    ): List<EvmTransaction> = withContext(Dispatchers.IO) {
        if (network.explorerUrl.isBlank()) return@withContext emptyList()
        val native = runCatching { fetchNativeTransactions(address, network) }.getOrDefault(emptyList())
        val internal = runCatching { fetchInternalTransactions(address, network) }.getOrDefault(emptyList())
        val tokens = runCatching { fetchTokenTransactions(address, network, emptyList()) }.getOrDefault(emptyList())
        runCatching {
            val tokenByHash = tokens.associateBy { it.hash.lowercase() }
            val merged = buildList {
                (native + internal).forEach { tx ->
                    val tokenTx = tokenByHash[tx.hash.lowercase()]
                    if (tokenTx != null && tx.value.toDoubleOrNull() == 0.0) add(tokenTx) else add(tx)
                }
                tokens.forEach { tx ->
                    if ((native + internal).none { it.hash.equals(tx.hash, ignoreCase = true) }) add(tx)
                }
            }
            merged.distinctBy { "${it.hash}-${it.symbol}-${it.tokenAddress.orEmpty()}" }
                .sortedByDescending { it.timestamp }
                .take(20)
        }.getOrDefault(emptyList())
    }

    fun explorerTxUrl(network: EvmNetwork, hash: String): String? {
        if (network.explorerUrl.isBlank()) return null
        return "${network.explorerUrl.trimEnd('/')}/tx/$hash"
    }

    fun explorerAddressUrl(network: EvmNetwork, address: String): String? {
        if (network.explorerUrl.isBlank()) return null
        return "${network.explorerUrl.trimEnd('/')}/address/$address"
    }

    private fun buildGasEstimate(network: EvmNetwork, gasLimit: BigInteger, label: String): EvmGasEstimate {
        val marketGasPrice = rpcQuantity(network.rpcUrl, "eth_gasPrice", JSONArray())
        val lowGasPrice = marketGasPrice.multiply(BigInteger.valueOf(85L)).divide(BigInteger.valueOf(100L)).coerceAtLeast(BigInteger.ONE)
        val fastGasPrice = marketGasPrice.multiply(BigInteger.valueOf(125L)).divide(BigInteger.valueOf(100L)).coerceAtLeast(BigInteger.ONE)
        return EvmGasEstimate(
            gasLimit = gasLimit,
            lowGasPriceWei = lowGasPrice,
            marketGasPriceWei = marketGasPrice,
            fastGasPriceWei = fastGasPrice,
            lowFeeWei = lowGasPrice.multiply(gasLimit),
            marketFeeWei = marketGasPrice.multiply(gasLimit),
            fastFeeWei = fastGasPrice.multiply(gasLimit),
            label = label,
        )
    }

    private fun sendEvmTransaction(
        wallet: Wallet,
        network: EvmNetwork,
        toAddress: String,
        valueWei: BigInteger,
        data: ByteArray,
        fallbackGas: BigInteger,
        gasPriceWeiOverride: BigInteger? = null,
        gasLimitOverride: BigInteger? = null,
        nonceOverride: BigInteger? = null,
        insufficientGasMessage: String,
    ): EvmSendResult {
        val from = deriveAddress(wallet)
        val nonce = if (nonceOverride != null) {
            nonceOverride
        } else {
            val lockKey = "${from.lowercase()}:${network.chainId}"
            val nonceLock = nonceLocks.getOrPut(lockKey) { ReentrantLock() }
            nonceLock.lock()
            try {
                rpcQuantity(network.rpcUrl, "eth_getTransactionCount", JSONArray().put(from).put("pending"))
            } finally {
                nonceLock.unlock()
            }
        }
        val gasPrice = gasPriceWeiOverride?.takeIf { it > BigInteger.ZERO }
            ?: rpcQuantity(network.rpcUrl, "eth_gasPrice", JSONArray())
        val gasLimit = gasLimitOverride?.takeIf { it > BigInteger.ZERO }
            ?: estimateGas(network.rpcUrl, from, toAddress, valueWei, data, fallbackGas)
        val nativeBalance = rpcQuantity(network.rpcUrl, "eth_getBalance", JSONArray().put(from).put("latest"))
        require(nativeBalance >= valueWei.add(gasPrice.multiply(gasLimit))) { insufficientGasMessage }
        val rawTx = crypto.signLegacyTransaction(
            wallet = wallet,
            nonce = nonce,
            gasPriceWei = gasPrice,
            gasLimit = gasLimit,
            toAddress = toAddress,
            valueWei = valueWei,
            data = data,
            chainId = network.chainId,
        )
        val hash = rpcString(network.rpcUrl, "eth_sendRawTransaction", JSONArray().put(rawTx))
        val dataHex = if (data.isEmpty()) "0x" else "0x${data.toHex()}"
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

    private fun erc20TransferData(recipient: String, amountRaw: BigInteger): ByteArray =
        EvmCalldata.erc20Transfer(recipient, amountRaw)

    private fun erc20ApproveData(spender: String, amountRaw: BigInteger): ByteArray =
        EvmCalldata.erc20Approve(spender, amountRaw)

    private fun erc721SafeTransferData(from: String, recipient: String, tokenId: String): ByteArray =
        EvmCalldata.erc721SafeTransferFrom(from, recipient, tokenId.toTokenIdBigInteger())

    private fun estimateGas(
        rpcUrl: String,
        from: String,
        to: String,
        valueWei: BigInteger,
        data: ByteArray = ByteArray(0),
        fallbackGas: BigInteger = if (data.isEmpty()) DefaultNativeGasLimit else DefaultTokenGasLimit,
    ): BigInteger {
        return runCatching {
            val tx = JSONObject()
                .put("from", from)
                .put("to", to)
                .put("value", valueWei.toQuantity())
                .apply {
                    if (data.isNotEmpty()) put("data", "0x${data.toHex()}")
                }
            rpcQuantity(rpcUrl, "eth_estimateGas", JSONArray().put(tx))
                .multiply(BigInteger.valueOf(110))
                .divide(BigInteger.valueOf(100))
                .coerceAtLeast(fallbackGas)
        }.getOrDefault(fallbackGas)
    }

    private fun rpcQuantity(rpcUrl: String, method: String, params: JSONArray): BigInteger {
        val value = rpcString(rpcUrl, method, params)
        return value.hexQuantityToBigInteger()
    }

    private fun rpcString(rpcUrl: String, method: String, params: JSONArray): String {
        val result = rpcRaw(rpcUrl, method, params)
        return result?.toString() ?: throw IllegalStateException("$method returned empty result.")
    }

    private fun rpcRaw(rpcUrl: String, method: String, params: JSONArray): Any? =
        rpcRawOnce(rpcUrl.trim().trimEnd('/'), method, params)

    private fun rpcRawOnce(rpcUrl: String, method: String, params: JSONArray): Any? {
        val payload = JSONObject()
            .put("jsonrpc", "2.0")
            .put("id", System.currentTimeMillis())
            .put("method", method)
            .put("params", params)
        val requestBody = payload.toString()
        val connection = (URL(rpcUrl.trim().trimEnd('/')).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", PoctraUserAgent)
        }
        requestSecurity.applyTo(
            connection = connection,
            url = rpcUrl,
            requestBody = requestBody,
            highValueAction = method == "eth_sendRawTransaction",
        )
        return try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(requestBody)
            }
            val responseCode = connection.responseCode
            val stream = if (responseCode in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream ?: connection.inputStream
            }
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
            if (responseCode !in 200..299) throw IllegalStateException("HTTP $responseCode")
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun erc20BalanceRaw(tokenAddress: String, ownerAddress: String, network: EvmNetwork): BigInteger {
        val data = EvmCalldata.erc20BalanceOf(ownerAddress)
        return rpcString(
            network.rpcUrl,
            "eth_call",
            JSONArray()
                .put(JSONObject().put("to", tokenAddress).put("data", data))
                .put("latest"),
        ).hexQuantityToBigInteger()
    }

    private fun erc20AllowanceRaw(tokenAddress: String, ownerAddress: String, spenderAddress: String, network: EvmNetwork): BigInteger {
        val data = EvmCalldata.erc20Allowance(ownerAddress, spenderAddress)
        return rpcString(
            network.rpcUrl,
            "eth_call",
            JSONArray()
                .put(JSONObject().put("to", tokenAddress).put("data", data))
                .put("latest"),
        ).hexQuantityToBigInteger()
    }

    private fun ethCallString(rpcUrl: String, contractAddress: String, data: String): String {
        return runCatching {
            decodeAbiString(
                rpcString(
                    rpcUrl,
                    "eth_call",
                    JSONArray()
                        .put(JSONObject().put("to", contractAddress).put("data", data))
                        .put("latest"),
                ),
            )
        }.getOrDefault("")
    }

    private fun checkNftOwnership(contractAddress: String, tokenId: String, walletAddress: String, network: EvmNetwork): Boolean {
        return runCatching {
            val data = "0x6352211e${tokenId.toBigInteger().toAbiWord()}"
            val result = rpcString(
                network.rpcUrl,
                "eth_call",
                JSONArray()
                    .put(JSONObject().put("to", contractAddress).put("data", data))
                    .put("latest"),
            )
            "0x${result.cleanHex().takeLast(40)}".equals(walletAddress, ignoreCase = true)
        }.getOrDefault(false)
    }

    private fun resolveEnsName(name: String, network: EvmNetwork): String {
        val rpcUrl = ensRpcUrl(network)
        val node = namehash(name)
        val resolverResult = rpcString(
            rpcUrl,
            "eth_call",
            JSONArray()
                .put(JSONObject().put("to", EnsRegistryAddress).put("data", "0x0178b8bf$node"))
                .put("latest"),
        ).cleanHex()
        val resolver = "0x${resolverResult.takeLast(40)}"
        require(isValidEvmAddress(resolver) && !resolver.equals(ZeroAddress, ignoreCase = true)) {
            "ENS name has no resolver."
        }
        val addressResult = rpcString(
            rpcUrl,
            "eth_call",
            JSONArray()
                .put(JSONObject().put("to", resolver).put("data", "0x3b3b57de$node"))
                .put("latest"),
        ).cleanHex()
        val address = "0x${addressResult.takeLast(40)}"
        require(isValidEvmAddress(address) && !address.equals(ZeroAddress, ignoreCase = true)) {
            "ENS name has no EVM address."
        }
        return EvmCrypto.checksumAddress(address.cleanHex())
    }

    private fun resolveReverseEnsName(address: String, network: EvmNetwork): String? {
        return runCatching {
            val rpcUrl = ensRpcUrl(network)
            val reverseName = "${address.cleanHex().lowercase()}.addr.reverse"
            val node = namehash(reverseName)
            val resolverResult = rpcString(
                rpcUrl,
                "eth_call",
                JSONArray()
                    .put(JSONObject().put("to", EnsRegistryAddress).put("data", "0x0178b8bf$node"))
                    .put("latest"),
            ).cleanHex()
            val resolver = "0x${resolverResult.takeLast(40)}"
            if (!isValidEvmAddress(resolver) || resolver.equals(ZeroAddress, ignoreCase = true)) return@runCatching null
            val name = decodeAbiString(
                rpcString(
                    rpcUrl,
                    "eth_call",
                    JSONArray()
                        .put(JSONObject().put("to", resolver).put("data", "0x691f3431$node"))
                        .put("latest"),
                ),
            ).trim().trimEnd('.').lowercase()
            if (!isLikelyEnsName(name)) return@runCatching null
            val forward = runCatching { resolveEnsName(name, network) }.getOrNull()
            name.takeIf { forward.equals(address, ignoreCase = true) }
        }.getOrNull()
    }

    private fun ensRpcUrl(network: EvmNetwork): String {
        if (network.chainId == 1L && isValidRpcUrl(network.rpcUrl)) return network.rpcUrl
        throw IllegalStateException("ENS lookup requires Ethereum Mainnet. Change EVM network first.")
    }

    private fun namehash(name: String): String {
        var node = ByteArray(32)
        name.trim()
            .lowercase()
            .trimEnd('.')
            .split('.')
            .filter { it.isNotBlank() }
            .asReversed()
            .forEach { label ->
                val labelHash = EvmCrypto.keccak(label.toByteArray(Charsets.UTF_8))
                node = EvmCrypto.keccak(node + labelHash)
            }
        return node.toHex()
    }

    private fun fetchNativeTransactions(address: String, network: EvmNetwork): List<EvmTransaction> {
        val result = fetchExplorerResultArray(network, "txlist", address) ?: return emptyList()
        return buildList {
            for (index in 0 until result.length()) {
                val item = result.optJSONObject(index) ?: continue
                val hash = item.optString("hash")
                if (hash.isBlank()) continue
                val from = item.optString("from")
                val value = item.optString("value").toBigIntegerOrNull() ?: BigInteger.ZERO
                add(
                    EvmTransaction(
                        hash = hash,
                        from = from,
                        to = item.optString("to"),
                        value = EvmCrypto.formatEther(value),
                        symbol = network.symbol,
                        timestamp = item.optLong("timeStamp", 0L) * 1000,
                        status = if (item.optString("isError") == "1") "failed" else "confirmed",
                        type = if (from.equals(address, ignoreCase = true)) "sent" else "received",
                    ),
                )
            }
        }
    }

    private fun fetchInternalTransactions(address: String, network: EvmNetwork): List<EvmTransaction> {
        val result = fetchExplorerResultArray(network, "txlistinternal", address) ?: return emptyList()
        return buildList {
            for (index in 0 until result.length()) {
                val item = result.optJSONObject(index) ?: continue
                val hash = item.optString("hash")
                if (hash.isBlank()) continue
                val from = item.optString("from")
                val value = item.optString("value").toBigIntegerOrNull() ?: BigInteger.ZERO
                if (value == BigInteger.ZERO) continue
                add(
                    EvmTransaction(
                        hash = hash,
                        from = from,
                        to = item.optString("to"),
                        value = EvmCrypto.formatEther(value),
                        symbol = network.symbol,
                        timestamp = item.optLong("timeStamp", 0L) * 1000,
                        status = if (item.optString("isError") == "1") "failed" else "confirmed",
                        type = if (from.equals(address, ignoreCase = true)) "sent" else "received",
                    ),
                )
            }
        }
    }

    private fun fetchTokenTransactions(address: String, network: EvmNetwork, tokenAddresses: List<String>): List<EvmTransaction> {
        val result = fetchExplorerResultArray(network, "tokentx", address) ?: return emptyList()
        val filter = tokenAddresses.map { it.lowercase() }.toSet()
        return buildList {
            for (index in 0 until result.length()) {
                val item = result.optJSONObject(index) ?: continue
                val contract = item.optString("contractAddress")
                if (filter.isNotEmpty() && contract.lowercase() !in filter) continue
                val hash = item.optString("hash")
                if (hash.isBlank()) continue
                val decimals = item.optString("tokenDecimal").toIntOrNull()?.coerceIn(0, 36) ?: 18
                val value = item.optString("value").toBigIntegerOrNull() ?: BigInteger.ZERO
                val from = item.optString("from")
                add(
                    EvmTransaction(
                        hash = hash,
                        from = from,
                        to = item.optString("to"),
                        value = EvmCrypto.formatUnits(value, decimals),
                        symbol = item.optString("tokenSymbol").ifBlank { "TOKEN" },
                        timestamp = item.optLong("timeStamp", 0L) * 1000,
                        status = "confirmed",
                        type = if (from.equals(address, ignoreCase = true)) "sent" else "received",
                        tokenAddress = contract,
                    ),
                )
            }
        }
    }

    private fun fetchExplorerResultArray(network: EvmNetwork, action: String, address: String): JSONArray? {
        var primaryError: Throwable? = null
        if (isPoctraBackendHistorySupported(network.chainId)) {
            runCatching {
                fetchPoctraEtherscanResultArray(network, action, address)
            }.onSuccess { result ->
                return result
            }.onFailure { error ->
                primaryError = error
            }
        }

        val fallbackUrl = explorerApiUrl(network, action, address)
        if (fallbackUrl == null) {
            primaryError?.let { throw it }
            return JSONArray()
        }
        return runCatching {
            etherscanResultArray(getJson(fallbackUrl))
        }.getOrElse { error ->
            throw primaryError ?: error
        }
    }

    private fun fetchPoctraEtherscanResultArray(network: EvmNetwork, action: String, address: String): JSONArray? =
        etherscanResultArray(getJson(poctraEtherscanApiUrl(network, action, address)))

    private fun isPoctraBackendHistorySupported(chainId: Long): Boolean =
        chainId == EthereumMainnetChainId || chainId == EthereumSepoliaChainId

    private fun etherscanResultArray(json: JSONObject): JSONArray? {
        val result = json.opt("result")
        if (result is JSONArray) return result

        val message = json.optString("message")
        val resultText = result?.toString().orEmpty()
        if (message.equals("No transactions found", ignoreCase = true) ||
            resultText.contains("No transactions found", ignoreCase = true)
        ) {
            return JSONArray()
        }
        if (json.optString("status") == "0") {
            throw IllegalStateException(message.ifBlank { resultText.ifBlank { "Etherscan history lookup failed." } })
        }
        return null
    }

    private fun poctraEtherscanApiUrl(network: EvmNetwork, action: String, address: String): String =
        "$PoctraBackendBaseUrl/api/etherscan/account?${etherscanAccountQuery(network.chainId, action, address)}"

    private fun explorerApiUrl(network: EvmNetwork, action: String, address: String): String? {
        val apiHost = explorerApiHost(network.explorerUrl) ?: return null
        return "https://$apiHost/api?${legacyExplorerAccountQuery(action, address).removePrefix("&")}"
    }

    private fun etherscanAccountQuery(chainId: Long, action: String, address: String): String = buildString {
        append("chainId=")
        append(chainId)
        append("&action=")
        append(action)
        append("&address=")
        append(address)
        append("&startblock=0&endblock=99999999&page=1&offset=")
        append(20)
        append("&sort=desc")
    }

    private fun legacyExplorerAccountQuery(action: String, address: String): String = buildString {
        append("&module=account&action=")
        append(action)
        append("&address=")
        append(address)
        append("&startblock=0&endblock=99999999&page=1&offset=")
        append(20)
        append("&sort=desc")
    }

    private fun explorerApiHost(explorerUrl: String): String? {
        val host = runCatching { URI(explorerUrl.trim()).host.orEmpty().removePrefix("www.").lowercase() }
            .getOrDefault("")
        if (host.isBlank()) return null
        return when {
            host == "etherscan.io" -> "api.etherscan.io"
            host.endsWith(".etherscan.io") -> "api-${host.removeSuffix(".etherscan.io")}.etherscan.io"
            host == "bscscan.com" -> "api.bscscan.com"
            host.endsWith(".bscscan.com") -> "api-${host.removeSuffix(".bscscan.com")}.bscscan.com"
            host == "polygonscan.com" -> "api.polygonscan.com"
            host.endsWith(".polygonscan.com") -> "api-${host.removeSuffix(".polygonscan.com")}.polygonscan.com"
            host == "basescan.org" -> "api.basescan.org"
            host.endsWith(".basescan.org") -> "api-${host.removeSuffix(".basescan.org")}.basescan.org"
            host == "arbiscan.io" -> "api.arbiscan.io"
            host.endsWith(".arbiscan.io") -> "api-${host.removeSuffix(".arbiscan.io")}.arbiscan.io"
            host == "optimistic.etherscan.io" -> "api-optimistic.etherscan.io"
            host == "snowtrace.io" -> "api.snowtrace.io"
            host.endsWith(".snowtrace.io") -> "api-${host.removeSuffix(".snowtrace.io")}.snowtrace.io"
            else -> null
        }
    }

    private fun nativeFiatEstimate(balanceWei: BigInteger, symbol: String): String {
        val price = fetchNativeUsdPrice(symbol) ?: return "~ $-- USD"
        val balance = BigDecimal(balanceWei).divide(BigDecimal(EvmCrypto.WeiPerEther), 18, RoundingMode.DOWN)
        return "~ ${formatUsd(balance.multiply(BigDecimal.valueOf(price)))} USD"
    }

    private fun fetchNativeUsdPrice(symbol: String): Double? {
        val coinId = when (symbol.uppercase()) {
            "ETH" -> "ethereum"
            "POL", "MATIC" -> "matic-network"
            "BNB" -> "binancecoin"
            "HYPE" -> "hyperliquid"
            else -> return null
        }
        val now = System.currentTimeMillis()
        if (coinId == prefs.getString(KeyPriceCoin, null) && now - prefs.getLong(KeyPriceFetchedAt, 0L) < PriceCacheMs) {
            return prefs.getFloat(KeyPriceUsd, 0f).takeIf { it > 0f }?.toDouble()
        }
        return runCatching {
            val json = getJson("https://api.coingecko.com/api/v3/simple/price?ids=$coinId&vs_currencies=usd")
            val price = json.optJSONObject(coinId)?.optDouble("usd")?.takeIf { it > 0.0 }
            if (price != null) {
                prefs.edit()
                    .putString(KeyPriceCoin, coinId)
                    .putFloat(KeyPriceUsd, price.toFloat())
                    .putLong(KeyPriceFetchedAt, now)
                    .apply()
            }
            price
        }.getOrNull()
    }

    private fun isValidRpcUrl(url: String): Boolean {
        return try {
            val uri = URI(url.trim())
            val scheme = uri.scheme?.lowercase()
            val host = uri.host?.lowercase()
            scheme == "https" && !host.isNullOrBlank() ||
                scheme == "http" && host in setOf("localhost", "127.0.0.1", "10.0.2.2")
        } catch (_: Exception) {
            false
        }
    }

    private fun isPrivateAddress(address: InetAddress): Boolean =
        address.isAnyLocalAddress ||
            address.isLoopbackAddress ||
            address.isLinkLocalAddress ||
            address.isSiteLocalAddress ||
            address.isMulticastAddress ||
            (address.address.size == 16 && (address.address[0].toInt() and 0xfe) == 0xfc)

    private fun isValidEvmAddress(address: String): Boolean =
        address.matches(Regex("^0x[a-fA-F0-9]{40}$"))

    private fun isLikelyEnsName(value: String): Boolean =
        value.trim().matches(Regex("^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$"))

    private fun persistNetworks(networks: List<EvmNetwork>) {
        val array = JSONArray().apply { networks.forEach { put(it.toJson()) } }
        val activeId = when (prefs.getString(KeyActiveNetwork, null)) {
            LegacyPublicNodeMainnetId -> "eth-mainnet"
            LegacyPublicNodeSepoliaId -> "eth-sepolia"
            else -> prefs.getString(KeyActiveNetwork, null)
        }
        val active = networks.firstOrNull { it.id == activeId }
            ?: networks.firstOrNull { it.id == "eth-mainnet" }
            ?: networks.firstOrNull()
        prefs.edit()
            .putString(KeyNetworks, array.toString())
            .putString(KeyActiveNetwork, active?.id)
            .apply()
    }

    private fun allTokens(): List<EvmToken> {
        val stored = prefs.getString(KeyTokens, null)
        val parsed = runCatching {
            val array = JSONArray(stored ?: "[]")
            buildList {
                for (index in 0 until array.length()) add(EvmToken.fromJson(array.getJSONObject(index)))
            }
        }.getOrDefault(emptyList())
        return mergeTokens(defaultErc20Tokens() + parsed)
            .filter(::isAllowedSavedToken)
    }

    private fun persistTokens(tokens: List<EvmToken>) {
        val merged = mergeTokens(tokens).filter(::isAllowedSavedToken)
        prefs.edit().putString(KeyTokens, JSONArray().apply { merged.forEach { put(it.toJson()) } }.toString()).apply()
    }

    private fun allNfts(): List<EvmNft> {
        val stored = prefs.getString(KeyNfts, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(stored)
            buildList {
                for (index in 0 until array.length()) add(EvmNft.fromJson(array.getJSONObject(index)))
            }
        }.getOrDefault(emptyList())
    }

    private fun persistNfts(nfts: List<EvmNft>) {
        prefs.edit().putString(KeyNfts, JSONArray().apply { nfts.forEach { put(it.toJson()) } }.toString()).apply()
    }

    private fun mergeNetworks(networks: List<EvmNetwork>): List<EvmNetwork> {
        val merged = linkedMapOf<String, EvmNetwork>()
        networks.forEach { network ->
            val normalized = normalizeDefaultNetwork(network)
            merged[normalized.id] = normalized
        }
        return merged.values.toList()
    }

    private fun normalizeDefaultNetwork(network: EvmNetwork): EvmNetwork = when {
        network.id == "eth-mainnet" && network.rpcUrl.contains("mainnet.infura.io/v3/", ignoreCase = true) -> defaultMainnetNetwork()
        network.id == "eth-sepolia" && network.rpcUrl.contains("sepolia.infura.io/v3/", ignoreCase = true) -> defaultSepoliaNetwork()
        network.id == "eth-mainnet" && network.rpcUrl.equals(PublicNodeMainnetRpcUrl, ignoreCase = true) -> defaultMainnetNetwork()
        network.id == "eth-sepolia" && network.rpcUrl.equals(PublicNodeSepoliaRpcUrl, ignoreCase = true) -> defaultSepoliaNetwork()
        network.id == "eth-mainnet" && network.rpcUrl.contains(LegacyRpcBackendHost, ignoreCase = true) -> defaultMainnetNetwork()
        network.id == "eth-sepolia" && network.rpcUrl.contains(LegacyRpcBackendHost, ignoreCase = true) -> defaultSepoliaNetwork()
        network.id == "eth-mainnet" && network.rpcUrl.contains(LegacyPoctraBackendHost, ignoreCase = true) -> defaultMainnetNetwork()
        network.id == "eth-sepolia" && network.rpcUrl.contains(LegacyPoctraBackendHost, ignoreCase = true) -> defaultSepoliaNetwork()
        network.id == "eth-mainnet" && network.rpcUrl.contains(LegacyNetBackendHost, ignoreCase = true) -> defaultMainnetNetwork()
        network.id == "eth-sepolia" && network.rpcUrl.contains(LegacyNetBackendHost, ignoreCase = true) -> defaultSepoliaNetwork()
        network.id == LegacyPublicNodeMainnetId -> defaultMainnetNetwork()
        network.id == LegacyPublicNodeSepoliaId -> defaultSepoliaNetwork()
        network.chainId == EthereumMainnetChainId && network.rpcUrl.contains(LegacyRpcBackendHost, ignoreCase = true) ->
            network.copy(rpcUrl = MainnetBackendRpcUrl)
        network.chainId == EthereumSepoliaChainId && network.rpcUrl.contains(LegacyRpcBackendHost, ignoreCase = true) ->
            network.copy(rpcUrl = SepoliaBackendRpcUrl)
        network.chainId == EthereumMainnetChainId && network.rpcUrl.contains(LegacyPoctraBackendHost, ignoreCase = true) ->
            network.copy(rpcUrl = MainnetBackendRpcUrl)
        network.chainId == EthereumSepoliaChainId && network.rpcUrl.contains(LegacyPoctraBackendHost, ignoreCase = true) ->
            network.copy(rpcUrl = SepoliaBackendRpcUrl)
        network.chainId == EthereumMainnetChainId && network.rpcUrl.contains(LegacyNetBackendHost, ignoreCase = true) ->
            network.copy(rpcUrl = MainnetBackendRpcUrl)
        network.chainId == EthereumSepoliaChainId && network.rpcUrl.contains(LegacyNetBackendHost, ignoreCase = true) ->
            network.copy(rpcUrl = SepoliaBackendRpcUrl)
        network.id == "eth-mainnet" && network.rpcUrl.isBlank() -> defaultMainnetNetwork()
        network.id == "eth-sepolia" && network.rpcUrl.isBlank() -> defaultSepoliaNetwork()
        else -> network
    }

    private fun mergeTokens(tokens: List<EvmToken>): List<EvmToken> {
        val merged = linkedMapOf<String, EvmToken>()
        tokens.forEach { token ->
            merged["${token.chainId}:${token.address.lowercase()}"] = token
        }
        return merged.values.toList()
    }

    private fun defaultErc20Tokens(): List<EvmToken> = listOf(
        EvmToken(
            address = DefaultWOctAddress,
            name = "Wrapped OCT",
            symbol = "wOCT",
            decimals = 6,
            balance = "0",
            chainId = EthereumMainnetChainId,
        ),
        EvmToken(
            address = DefaultUsdtAddress,
            name = "Tether USD",
            symbol = "USDT",
            decimals = 6,
            balance = "0",
            chainId = EthereumMainnetChainId,
        ),
    )

    private fun isAllowedSavedToken(token: EvmToken): Boolean {
        val isWOct = token.address.equals(DefaultWOctAddress, ignoreCase = true) ||
            token.symbol.equals("wOCT", ignoreCase = true)
        return !isWOct || token.chainId == EthereumMainnetChainId
    }

    private fun defaultSepoliaNetwork(): EvmNetwork =
        EvmNetwork(
            id = "eth-sepolia",
            name = "Ethereum Sepolia",
            chainId = 11155111L,
            rpcUrl = SepoliaBackendRpcUrl,
            symbol = "ETH",
            explorerUrl = "https://sepolia.etherscan.io",
            isTestnet = true,
        )

    private fun defaultMainnetNetwork(): EvmNetwork =
        EvmNetwork(
            id = "eth-mainnet",
            name = "Ethereum Mainnet",
            chainId = 1L,
            rpcUrl = MainnetBackendRpcUrl,
            symbol = "ETH",
            explorerUrl = "https://etherscan.io",
            isTestnet = false,
        )

    private fun defaultNetworks(): List<EvmNetwork> =
        listOf(
            defaultMainnetNetwork(),
            defaultSepoliaNetwork(),
            hyperEvmMainnetNetwork(),
            hyperEvmTestnetNetwork(),
        )

    private fun hyperEvmMainnetNetwork(): EvmNetwork =
        EvmNetwork(
            id = "hyperevm-mainnet",
            name = "HyperEVM Mainnet",
            chainId = HyperEvmMainnetChainId,
            rpcUrl = HyperEvmMainnetRpcUrl,
            symbol = "HYPE",
            explorerUrl = "https://hyperevmscan.io",
            isTestnet = false,
        )

    private fun hyperEvmTestnetNetwork(): EvmNetwork =
        EvmNetwork(
            id = "hyperevm-testnet",
            name = "HyperEVM Testnet",
            chainId = HyperEvmTestnetChainId,
            rpcUrl = HyperEvmTestnetRpcUrl,
            symbol = "HYPE",
            explorerUrl = "",
            isTestnet = true,
        )

    private fun String.hexQuantityToBigInteger(): BigInteger {
        val clean = removePrefix("0x").removePrefix("0X")
        if (clean.isBlank()) return BigInteger.ZERO
        return BigInteger(clean, 16)
    }

    private fun BigInteger.toQuantity(): String = if (this == BigInteger.ZERO) "0x0" else "0x${toString(16)}"

    private fun BigInteger.toAbiWord(): String = toString(16).padStart(64, '0')

    private fun String.cleanHex(): String = removePrefix("0x").removePrefix("0X")

    private fun String.toTokenIdBigInteger(): BigInteger {
        val clean = trim()
        require(clean.isNotBlank()) { "NFT token ID is required." }
        return if (clean.startsWith("0x", ignoreCase = true)) {
            BigInteger(clean.cleanHex(), 16)
        } else {
            clean.toBigIntegerOrNull() ?: throw IllegalArgumentException("NFT token ID must be numeric.")
        }
    }

    private fun String.hexToBytes(): ByteArray {
        val clean = cleanHex()
        require(clean.length % 2 == 0) { "Hex value has invalid length." }
        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.toHex(): String = joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }

    private fun decodeAbiString(hex: String): String {
        val clean = hex.cleanHex()
        if (clean.isBlank()) return ""
        return runCatching {
            if (clean.length >= 128) {
                val length = clean.substring(64, 128).toInt(16)
                clean.substring(128, (128 + length * 2).coerceAtMost(clean.length)).hexToBytes()
                    .toString(Charsets.UTF_8)
                    .trimEnd('\u0000')
            } else {
                clean.hexToBytes().toString(Charsets.UTF_8).trimEnd('\u0000')
            }
        }.getOrDefault("")
    }

    private fun formatGwei(wei: BigInteger): String {
        val gwei = wei.toBigDecimal().divide(BigInteger.TEN.pow(9).toBigDecimal())
        return gwei.setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
    }

    private fun formatUsd(value: BigDecimal): String = "$${value.setScale(2, RoundingMode.HALF_UP).toPlainString()}"

    companion object {
        private const val KeyNetworks = "evm_networks"
        private const val KeyActiveNetwork = "active_evm_network"
        private const val KeyTokens = "evm_tokens"
        private const val KeyNfts = "evm_nfts"
        private const val KeyPriceCoin = "evm_price_coin"
        private const val KeyPriceUsd = "evm_price_usd"
        private const val KeyPriceFetchedAt = "evm_price_fetched_at"
        private const val PoctraUserAgent = "poctra-android"
        private const val PoctraBackendBaseUrl = "https://rpc.xme.my.id"
        private const val LegacyRpcBackendHost = "rpc.xme.my.id"
        private const val LegacyPoctraBackendHost = "poctra.xme.my.id"
        private const val LegacyNetBackendHost = "net.xme.my.id"
        private const val LegacyPublicNodeMainnetId = "eth-mainnet-publicnode"
        private const val LegacyPublicNodeSepoliaId = "eth-sepolia-publicnode"
        private const val PriceCacheMs = 60_000L
        private const val TokenRefreshBatchSize = 4
        private const val NftRefreshBatchSize = 3
        private const val EthereumMainnetChainId = 1L
        private const val EthereumSepoliaChainId = 11155111L
        private const val HyperEvmMainnetChainId = 999L
        private const val HyperEvmTestnetChainId = 998L
        private const val MainnetBackendRpcUrl = "$PoctraBackendBaseUrl/eth-mainnet"
        private const val SepoliaBackendRpcUrl = "$PoctraBackendBaseUrl/eth-sepolia"
        private const val PublicNodeMainnetRpcUrl = "https://ethereum-rpc.publicnode.com"
        private const val PublicNodeSepoliaRpcUrl = "https://ethereum-sepolia-rpc.publicnode.com"
        private const val HyperEvmMainnetRpcUrl = "https://rpc.hyperliquid.xyz/evm"
        private const val HyperEvmTestnetRpcUrl = "https://rpc.hyperliquid-testnet.xyz/evm"
        private const val DefaultWOctAddress = "0x4647e1fE715c9e23959022C2416C71867F5a6E80"
        private const val DefaultUsdtAddress = "0xdAC17F958D2ee523a2206206994597C13D831ec7"
        private const val EnsRegistryAddress = "0x00000000000C2E074eC69A0dFb2997BA6C7d2e1e"
        private const val ZeroAddress = "0x0000000000000000000000000000000000000000"
        private val DefaultNativeGasLimit = BigInteger.valueOf(21_000L)
        private val DefaultTokenGasLimit = BigInteger.valueOf(80_000L)
        private val DefaultNftGasLimit = BigInteger.valueOf(120_000L)
        private val OneGweiWei = BigInteger("1000000000")
        private const val MaxDAppCalldataBytes = 131_072
    }
}
