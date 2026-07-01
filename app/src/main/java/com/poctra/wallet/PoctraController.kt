package com.poctra.wallet

import android.app.Activity
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.poctra.wallet.core.WalletCrypto
import com.poctra.wallet.data.AssetRepository
import com.poctra.wallet.data.AddressBookContact
import com.poctra.wallet.data.AddressBookRepository
import com.poctra.wallet.data.BackupExporter
import com.poctra.wallet.data.BalanceSnapshot
import com.poctra.wallet.data.BridgeBalances
import com.poctra.wallet.data.BridgeChainHistoryRecord
import com.poctra.wallet.data.BridgeClaimData
import com.poctra.wallet.data.BridgeDirection
import com.poctra.wallet.data.BridgeEvmGasEstimate
import com.poctra.wallet.data.BridgeRepository
import com.poctra.wallet.data.BridgeStatus
import com.poctra.wallet.data.BridgeStep
import com.poctra.wallet.data.CircleAsset
import com.poctra.wallet.data.DAppSession
import com.poctra.wallet.data.DAppSessionRepository
import com.poctra.wallet.data.EvmAccountSnapshot
import com.poctra.wallet.data.EvmGasEstimate
import com.poctra.wallet.data.EvmNetwork
import com.poctra.wallet.data.EvmNft
import com.poctra.wallet.data.EvmRepository
import com.poctra.wallet.data.EvmSendResult
import com.poctra.wallet.data.EvmToken
import com.poctra.wallet.data.EvmTransaction
import com.poctra.wallet.data.OctraToken
import com.poctra.wallet.data.OctraIdentityProvider
import com.poctra.wallet.data.OctraIdentityProviderRepository
import com.poctra.wallet.data.OctPriceRepository
import com.poctra.wallet.data.RpcRepository
import com.poctra.wallet.data.RpcNetwork
import com.poctra.wallet.data.RpcStatus
import com.poctra.wallet.data.SecurityMethod
import com.poctra.wallet.data.TransactionHistoryItem
import com.poctra.wallet.data.TokenBalance
import com.poctra.wallet.data.TradeAsset
import com.poctra.wallet.data.TradeBalances
import com.poctra.wallet.data.TradeCandle
import com.poctra.wallet.data.TradeChartTimeframe
import com.poctra.wallet.data.TradeQuote
import com.poctra.wallet.data.TradeRepository
import com.poctra.wallet.data.TradeSubmittedTransaction
import com.poctra.wallet.data.TradeSwapStage
import com.poctra.wallet.data.SubmittedTransaction
import com.poctra.wallet.data.TransactionFinality
import com.poctra.wallet.data.Wallet
import com.poctra.wallet.data.WalletVault
import com.poctra.wallet.data.TransactionNetworkState
import com.poctra.wallet.data.TransactionNetworkStatus
import com.poctra.wallet.data.canonicalizeOrigin
import com.poctra.wallet.data.hostLabel
import com.poctra.wallet.security.DeviceSecurity
import com.poctra.wallet.security.SecureWalletStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import org.json.JSONArray
import org.json.JSONObject

enum class AppRoute {
    Loading,
    Welcome,
    ImportWallet,
    SecuritySetup,
    Unlock,
    Dashboard,
    Settings,
    RpcManagement,
    RpcAddNetwork,
    SecuritySettings,
    BackupSettings,
    AppearanceSettings,
    ConnectedDApps,
    IdentityProviderSettings,
    IdentityProviderAdd,
    AddressBook,
    AddressBookAdd,
    AddAsset,
    Send,
    SendAsset,
    Receive,
    Bridge,
    BridgeHistory,
    Evm,
    EvmSend,
    EvmReceive,
    EvmActivity,
    EvmNetwork,
    EvmAddNetwork,
    EvmAddToken,
    EvmAddNft,
    TransactionDetails,
    DAppBrowser,
    About,
}

data class OctraCircleTarget(
    val input: String,
    val label: String,
    val circleId: String,
    val path: String,
    val uri: String,
)

private sealed class OctraCircleOpenResult {
    data class Circle(val target: OctraCircleTarget) : OctraCircleOpenResult()
    data class ExplorerAddress(val title: String, val url: String, val displayUrl: String) : OctraCircleOpenResult()
}

data class BridgeHistoryItem(
    val id: String,
    val walletAddress: String,
    val evmAddress: String,
    val direction: BridgeDirection,
    val amount: String,
    val amountRaw: String,
    val status: String,
    val step: BridgeStep,
    val message: String,
    val octraTxHash: String? = null,
    val evmTxHash: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("walletAddress", walletAddress)
        .put("evmAddress", evmAddress)
        .put("direction", direction.name)
        .put("amount", amount)
        .put("amountRaw", amountRaw)
        .put("status", status)
        .put("step", step.name)
        .put("message", message)
        .put("octraTxHash", octraTxHash)
        .put("evmTxHash", evmTxHash)
        .put("createdAt", createdAt)
        .put("updatedAt", updatedAt)

    companion object {
        fun fromJson(json: JSONObject): BridgeHistoryItem = BridgeHistoryItem(
            id = json.optString("id"),
            walletAddress = json.optString("walletAddress"),
            evmAddress = json.optString("evmAddress"),
            direction = runCatching { BridgeDirection.valueOf(json.optString("direction")) }.getOrDefault(BridgeDirection.OctToWoct),
            amount = json.optString("amount", "0"),
            amountRaw = json.optString("amountRaw", "0"),
            status = json.optString("status", "failed"),
            step = runCatching { BridgeStep.valueOf(json.optString("step")) }.getOrDefault(BridgeStep.Failed),
            message = json.optString("message", ""),
            octraTxHash = json.optString("octraTxHash").ifBlank { null },
            evmTxHash = json.optString("evmTxHash").ifBlank { null },
            createdAt = json.optLong("createdAt", 0L),
            updatedAt = json.optLong("updatedAt", 0L),
        )
    }
}

enum class DashboardTab {
    Overview,
    Assets,
    Explore,
    Trade,
}

enum class TradeStep {
    Idle,
    TokenApproval,
    PermitApproval,
    Swapping,
    Completed,
    Failed,
}

data class PendingDAppRequest(
    val origin: String,
    val title: String,
    val message: String,
    val details: List<DAppRequestDetail> = emptyList(),
    val approveLabel: String = "Approve",
    val rejectLabel: String = "Reject",
)

data class DAppRequestDetail(
    val label: String,
    val value: String,
)

class PoctraController(private val activity: Activity) {
    private val appContext = activity.applicationContext
    private val uiPrefs = appContext.getSharedPreferences("poctra_ui", Context.MODE_PRIVATE)
    private val bridgePrefs = appContext.getSharedPreferences("poctra_bridge", Context.MODE_PRIVATE)
    private val walletCrypto = WalletCrypto(appContext)
    private val secureStore = SecureWalletStore(appContext)
    private val rpcRepository = RpcRepository(appContext)
    private val dAppSessionRepository = DAppSessionRepository(appContext)
    private val addressBookRepository = AddressBookRepository(appContext)
    private val assetRepository = AssetRepository(appContext)
    private val identityProviderRepository = OctraIdentityProviderRepository(appContext)
    private val evmRepository = EvmRepository(appContext)
    private val bridgeRepository = BridgeRepository()
    private val tradeRepository = TradeRepository()
    private val octPriceRepository = OctPriceRepository()
    private val backupExporter = BackupExporter(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var rpcManagementReturnRoute = AppRoute.Settings
    private var evmNetworkReturnRoute = AppRoute.Evm
    private var evmNetworkReturnTab: DashboardTab? = null

    var route by mutableStateOf(AppRoute.Loading)
        private set
    var dashboardTab by mutableStateOf(DashboardTab.Overview)
        private set
    var darkMode by mutableStateOf(initialDarkMode())
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var noticeIsError by mutableStateOf(true)
        private set
    var pendingVault by mutableStateOf<WalletVault?>(null)
        private set
    var vault by mutableStateOf<WalletVault?>(null)
        private set
    var wallet by mutableStateOf<Wallet?>(null)
        private set
    var rpcStatus by mutableStateOf<RpcStatus?>(null)
        private set
    var rpcNetworks by mutableStateOf<List<RpcNetwork>>(emptyList())
        private set
    var activeRpcNetwork by mutableStateOf<RpcNetwork?>(null)
        private set
    var tokenBalance by mutableStateOf(
        TokenBalance(
            symbol = "OCT",
            name = "Octra",
            amount = "--",
            fiatEstimate = "Connect RPC to fetch balance",
            isLoading = true,
        ),
    )
        private set
    var dApps by mutableStateOf<List<DAppSession>>(emptyList())
        private set
    var addressBookContacts by mutableStateOf<List<AddressBookContact>>(emptyList())
        private set
    var availableBalance by mutableStateOf(0.0)
        private set
    var octUsdPrice by mutableStateOf<Double?>(null)
        private set
    var priceLoading by mutableStateOf(false)
        private set
    var octraTokens by mutableStateOf<List<OctraToken>>(emptyList())
        private set
    var assetsLoading by mutableStateOf(false)
        private set
    var recentTransactions by mutableStateOf<List<TransactionHistoryItem>>(emptyList())
        private set
    var historyLoading by mutableStateOf(false)
        private set
    var overviewRefreshLoading by mutableStateOf(false)
        private set
    var historyLimit by mutableStateOf(InitialHistoryLimit)
        private set
    var historyCanLoadMore by mutableStateOf(true)
        private set
    var assetPreview by mutableStateOf<OctraToken?>(null)
        private set
    var selectedOctraToken by mutableStateOf<OctraToken?>(null)
        private set
    var identityProviders by mutableStateOf<List<OctraIdentityProvider>>(emptyList())
        private set
    var activeIdentityProvider by mutableStateOf<OctraIdentityProvider?>(null)
        private set
    var walletIdentityNames by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var identityNamesByAddress by mutableStateOf<Map<String, String>>(emptyMap())
        private set
    var assetLookupLoading by mutableStateOf(false)
        private set
    var selectedTransaction by mutableStateOf<TransactionHistoryItem?>(null)
        private set
    var submittedTransaction by mutableStateOf<SubmittedTransaction?>(null)
        private set
    var transactionStatusPolling by mutableStateOf(false)
        private set
    var recommendedFeeOu by mutableStateOf(1000L)
        private set
    var feeLoading by mutableStateOf(false)
        private set
    var selectedDAppTitle by mutableStateOf("")
        private set
    var selectedDAppUrl by mutableStateOf("")
        private set
    private var selectedDAppDisplayUrl by mutableStateOf<String?>(null)
    var selectedCircleTarget by mutableStateOf<OctraCircleTarget?>(null)
        private set
    var selectedCircleError by mutableStateOf<String?>(null)
        private set
    var evmNetworks by mutableStateOf<List<EvmNetwork>>(emptyList())
        private set
    var activeEvmNetwork by mutableStateOf<EvmNetwork?>(null)
        private set
    var evmEditingNetwork by mutableStateOf<EvmNetwork?>(null)
        private set
    var evmAccount by mutableStateOf<EvmAccountSnapshot?>(null)
        private set
    var evmLoading by mutableStateOf(false)
        private set
    var evmSending by mutableStateOf(false)
        private set
    var evmTokens by mutableStateOf<List<EvmToken>>(emptyList())
        private set
    var evmNfts by mutableStateOf<List<EvmNft>>(emptyList())
        private set
    var evmTransactions by mutableStateOf<List<EvmTransaction>>(emptyList())
        private set
    var evmAssetsLoading by mutableStateOf(false)
        private set
    var evmHistoryLoading by mutableStateOf(false)
        private set
    var evmLookupLoading by mutableStateOf(false)
        private set
    var evmTokenPreview by mutableStateOf<EvmToken?>(null)
        private set
    var evmNftPreview by mutableStateOf<EvmNft?>(null)
        private set
    var evmSelectedSendToken by mutableStateOf<EvmToken?>(null)
        private set
    var evmSelectedSendNft by mutableStateOf<EvmNft?>(null)
        private set
    var evmSendGasEstimate by mutableStateOf<EvmGasEstimate?>(null)
        private set
    var evmSendGasLoading by mutableStateOf(false)
        private set
    var evmSendDialogVisible by mutableStateOf(false)
        private set
    var evmSendDialogStarted by mutableStateOf(false)
        private set
    var evmSendDialogRecipient by mutableStateOf("")
        private set
    var evmSendDialogAmount by mutableStateOf("")
        private set
    var evmSendSpeedUpBusy by mutableStateOf(false)
        private set
    private var evmSendPendingTransaction by mutableStateOf<EvmSendResult?>(null)
    var bridgeDirection by mutableStateOf(BridgeDirection.OctToWoct)
        private set
    var bridgeBalances by mutableStateOf(BridgeBalances())
        private set
    var bridgeStatus by mutableStateOf(BridgeStatus())
        private set
    var bridgeLoading by mutableStateOf(false)
        private set
    var bridgeBusy by mutableStateOf(false)
        private set
    var bridgeStep by mutableStateOf(BridgeStep.Idle)
        private set
    var bridgeProgress by mutableStateOf("Bridge is ready.")
        private set
    var bridgeError by mutableStateOf<String?>(null)
        private set
    var bridgeOctraTxHash by mutableStateOf<String?>(null)
        private set
    var bridgeEvmTxHash by mutableStateOf<String?>(null)
        private set
    var bridgeHistory by mutableStateOf<List<BridgeHistoryItem>>(emptyList())
        private set
    var bridgeDialogVisible by mutableStateOf(false)
        private set
    var bridgeDialogStarted by mutableStateOf(false)
        private set
    var bridgeDialogAmount by mutableStateOf("")
        private set
    var bridgeDialogReceiverAddress by mutableStateOf("")
        private set
    var bridgeClaimRecoveryMode by mutableStateOf(false)
        private set
    var bridgeHistoryLoading by mutableStateOf(false)
        private set
    var bridgeEvmGasEstimate by mutableStateOf<BridgeEvmGasEstimate?>(null)
        private set
    var bridgeGasLoading by mutableStateOf(false)
        private set
    var bridgeSpeedUpBusy by mutableStateOf(false)
        private set
    private var bridgePendingEvmTransaction by mutableStateOf<EvmSendResult?>(null)
    private var activeBridgeHistoryId: String? = null
    var tradeBalances by mutableStateOf(TradeBalances())
        private set
    var tradeQuote by mutableStateOf<TradeQuote?>(null)
        private set
    var tradeQuoteError by mutableStateOf<String?>(null)
        private set
    var tradeQuoteLoading by mutableStateOf(false)
        private set
    var tradeLoading by mutableStateOf(false)
        private set
    var tradeBusy by mutableStateOf(false)
        private set
    var tradeLastTxHash by mutableStateOf<String?>(null)
        private set
    var tradeStep by mutableStateOf(TradeStep.Idle)
        private set
    var tradeProgress by mutableStateOf("Swap is ready.")
        private set
    var tradeDialogVisible by mutableStateOf(false)
        private set
    var tradeDialogStarted by mutableStateOf(false)
        private set
    var tradeDialogFromAsset by mutableStateOf(TradeAsset.WOCT)
        private set
    var tradeDialogToAsset by mutableStateOf(TradeAsset.ETH)
        private set
    var tradeDialogAmount by mutableStateOf("")
        private set
    var tradeDialogReceiveEstimate by mutableStateOf("")
        private set
    var tradeDialogMinimumReceived by mutableStateOf("")
        private set
    var tradeSpeedUpBusy by mutableStateOf(false)
        private set
    var tradeChartTimeframe by mutableStateOf(TradeChartTimeframe.H1)
        private set
    var tradeCandles by mutableStateOf<List<TradeCandle>>(emptyList())
        private set
    var tradeChartLoading by mutableStateOf(false)
        private set
    var tradeChartError by mutableStateOf<String?>(null)
        private set
    var tradeLastPrice by mutableStateOf("--")
        private set
    var pendingDAppRequest by mutableStateOf<PendingDAppRequest?>(null)
        private set

    private var sessionPin: String? = null
    private var unlockReturnRoute: AppRoute = AppRoute.Dashboard
    private var unlockReturnTab: DashboardTab = DashboardTab.Overview
    private var dAppReturnRoute: AppRoute = AppRoute.Dashboard
    private var dAppReturnTab: DashboardTab = DashboardTab.Explore
    private var addressBookReturnRoute: AppRoute = AppRoute.Settings
    private var pendingRecipientAddress: String? = null
    private var pendingRecipientIdentityName: String? = null
    private var pendingDAppAction: (suspend () -> String)? = null
    private var pendingDAppSuccess: ((String) -> Unit)? = null
    private var pendingDAppError: ((Int, String) -> Unit)? = null
    private var lastPriceFetchAt = 0L
    private var historyRequestSerial = 0L
    private var identityLookupSerial = 0L
    private var addressIdentityLookupSerial = 0L
    private var addressIdentityCacheKey: String? = null
    private var addressIdentityMisses: Set<String> = emptySet()
    private var tradeChartRequestSerial = 0L
    private var tradeQuoteRequestSerial = 0L
    private var circleResolveSerial = 0L
    private var tradePendingEvmTransaction by mutableStateOf<EvmSendResult?>(null)
    private val circleResourceModeCache = ConcurrentHashMap<String, String>()
    private val tradeChartCache = mutableMapOf<TradeChartTimeframe, List<TradeCandle>>()

    val biometricAvailable: Boolean
        get() = DeviceSecurity.isBiometricAvailable(activity)

    val currentSecurityMethod: SecurityMethod?
        get() = secureStore.currentMethod()

    val currentRpcUrl: String
        get() = activeRpcNetwork?.url ?: rpcRepository.currentUrl()

    val tradeNetworkSupported: Boolean
        get() = (activeEvmNetwork ?: runCatching { evmRepository.activeNetwork() }.getOrNull())?.chainId == 1L

    val bridgeNetworkWarning: String?
        get() = currentBridgeNetworkWarning()

    val bridgeCanSpeedUp: Boolean
        get() = bridgeBusy &&
            bridgePendingEvmTransaction?.hash != null &&
            bridgePendingEvmTransaction?.nonce != null &&
            bridgePendingEvmTransaction?.gasPriceWei != null &&
            bridgeStep in setOf(BridgeStep.Approving, BridgeStep.Burning, BridgeStep.Claiming)

    val tradeCanSpeedUp: Boolean
        get() = tradeBusy &&
            tradePendingEvmTransaction?.hash != null &&
            tradePendingEvmTransaction?.nonce != null &&
            tradePendingEvmTransaction?.gasPriceWei != null &&
            tradeStep in setOf(TradeStep.TokenApproval, TradeStep.PermitApproval, TradeStep.Swapping)

    val evmSendCanSpeedUp: Boolean
        get() = submittedTransaction?.status == TransactionFinality.Accepted &&
            evmSendPendingTransaction?.hash != null &&
            evmSendPendingTransaction?.nonce != null &&
            evmSendPendingTransaction?.gasPriceWei != null

    val secureWindowRequired: Boolean
        get() = route in setOf(
            AppRoute.ImportWallet,
            AppRoute.SecuritySetup,
            AppRoute.Unlock,
            AppRoute.BackupSettings,
        ) || pendingDAppRequest != null || submittedTransaction?.showDialog == true || bridgeDialogVisible || tradeDialogVisible

    init {
        bootstrap()
    }

    fun bootstrap() {
        rpcNetworks = rpcRepository.rpcNetworks()
        activeRpcNetwork = rpcRepository.activeNetwork()
        evmNetworks = evmRepository.networks()
        activeEvmNetwork = evmRepository.activeNetwork()
        bridgeHistory = loadBridgeHistory()
        route = if (secureStore.hasWallet()) AppRoute.Unlock else AppRoute.Welcome
        rpcStatus = RpcStatus(RpcStatus.State.Checking, currentRpcUrl, activeRpcNetwork?.networkType ?: networkFor(currentRpcUrl))
        refreshRpcStatus()
    }

    fun clearError() {
        error = null
        noticeIsError = true
    }

    fun showMessage(message: String) {
        noticeIsError = false
        error = message
    }

    fun showError(message: String) {
        noticeIsError = true
        error = message
    }

    fun lockForBackground() {
        if (!secureStore.hasWallet()) return
        if (route == AppRoute.Welcome || route == AppRoute.SecuritySetup) return
        val bridgeInProgress = bridgeBusy ||
            (bridgeDialogVisible && bridgeDialogStarted) ||
            bridgeStep in setOf(
                BridgeStep.Locking,
                BridgeStep.ConfirmingOctra,
                BridgeStep.WaitingRelayer,
                BridgeStep.Claiming,
                BridgeStep.Approving,
                BridgeStep.Burning,
                BridgeStep.WaitingUnlock,
            )
        val tradeInProgress = tradeBusy ||
            (tradeDialogVisible && tradeDialogStarted) ||
            tradeStep in setOf(
                TradeStep.TokenApproval,
                TradeStep.PermitApproval,
                TradeStep.Swapping,
            )
        if (bridgeInProgress) {
            bridgeDialogVisible = true
            bridgeDialogStarted = true
        }
        if (tradeInProgress) {
            tradeDialogVisible = true
            tradeDialogStarted = true
        }
        unlockReturnRoute = when {
            bridgeInProgress -> AppRoute.Bridge
            tradeInProgress -> AppRoute.Dashboard
            else -> unlockRestoreRoute(route)
        }
        unlockReturnTab = if (tradeInProgress) DashboardTab.Trade else dashboardTab
        sessionPin = null
        vault = null
        wallet = null
        clearPendingDAppRequest()
        submittedTransaction = null
        if (!bridgeInProgress) bridgeBusy = false
        if (!tradeInProgress) tradeBusy = false
        evmSending = false
        busy = false
        route = AppRoute.Unlock
    }

    fun openImportWallet() {
        route = AppRoute.ImportWallet
    }

    fun closeImportWallet() {
        route = if (vault?.activeWallet != null) AppRoute.Dashboard else AppRoute.Welcome
    }

    fun createWallet() {
        runBusy {
            stageOrPersistWallet(withContext(Dispatchers.Default) { walletCrypto.generateWallet() })
        }
    }

    fun importMnemonic(value: String) {
        runBusy {
            stageOrPersistWallet(withContext(Dispatchers.Default) { walletCrypto.importFromMnemonic(value) })
        }
    }

    fun importPrivateKey(value: String) {
        runBusy {
            stageOrPersistWallet(withContext(Dispatchers.Default) { walletCrypto.importFromPrivateKey(value) })
        }
    }

    fun importEthereumPrivateKey(value: String) {
        runBusy {
            stageOrPersistWallet(withContext(Dispatchers.Default) { walletCrypto.importFromEthereumPrivateKeyHex(value) })
        }
    }

    fun createBiometricEnrollCipher(): Cipher = secureStore.createBiometricEncryptCipher()

    fun createBiometricUnlockCipher(): Cipher = secureStore.createBiometricDecryptCipher()

    fun savePendingWithBiometric(cipher: Cipher) {
        val vaultToSave = pendingVault ?: run {
            showError("No wallet is waiting for security setup.")
            return
        }
        runBusy {
            withContext(Dispatchers.IO) { secureStore.saveVaultWithBiometric(vaultToSave, cipher) }
            sessionPin = null
            completeVaultSetup(vaultToSave)
        }
    }

    fun savePendingWithPin(pin: String) {
        val vaultToSave = pendingVault ?: run {
            showError("No wallet is waiting for security setup.")
            return
        }
        runBusy {
            withContext(Dispatchers.IO) { secureStore.saveVaultWithPin(vaultToSave, pin) }
            sessionPin = pin
            completeVaultSetup(vaultToSave)
        }
    }

    fun unlockWithBiometric(cipher: Cipher) {
        runBusy {
            val loaded = withContext(Dispatchers.IO) { secureStore.loadVaultWithBiometric(cipher) }
            vault = loaded
            wallet = loaded.activeWallet
            sessionPin = null
            restoreAfterUnlock()
        }
    }

    fun unlockWithPin(pin: String) {
        runBusy {
            val loaded = withContext(Dispatchers.IO) { secureStore.loadVaultWithPin(pin) }
            vault = loaded
            wallet = loaded.activeWallet
            sessionPin = pin
            restoreAfterUnlock()
        }
    }

    private fun restoreAfterUnlock() {
        dashboardTab = unlockReturnTab
        route = unlockReturnRoute
        refreshDashboard()
        when (route) {
            AppRoute.Bridge,
            AppRoute.BridgeHistory -> {
                bridgeHistory = loadBridgeHistory()
            }
            AppRoute.Evm,
            AppRoute.EvmSend,
            AppRoute.EvmReceive,
            AppRoute.EvmActivity,
            AppRoute.EvmNetwork -> refreshEvmDashboard()
            else -> Unit
        }
        if (route == AppRoute.Dashboard && dashboardTab == DashboardTab.Trade) {
            refreshTrade()
            refreshTradeChart()
        }
    }

    private fun unlockRestoreRoute(current: AppRoute): AppRoute = when (current) {
        AppRoute.Loading,
        AppRoute.Welcome,
        AppRoute.ImportWallet,
        AppRoute.SecuritySetup,
        AppRoute.Unlock -> AppRoute.Dashboard
        else -> current
    }

    fun beginSecurityUpdateWithPin(pin: String) {
        runBusy {
            pendingVault = withContext(Dispatchers.IO) { secureStore.loadVaultWithPin(pin) }
            sessionPin = pin
            route = AppRoute.SecuritySetup
        }
    }

    fun beginSecurityUpdateWithBiometric(cipher: Cipher) {
        runBusy {
            pendingVault = withContext(Dispatchers.IO) { secureStore.loadVaultWithBiometric(cipher) }
            sessionPin = null
            route = AppRoute.SecuritySetup
        }
    }

    fun confirmSecurityWithBiometric(cipher: Cipher, onVerified: () -> Unit) {
        runBusy {
            val loaded = withContext(Dispatchers.IO) { secureStore.loadVaultWithBiometric(cipher) }
            vault = loaded
            wallet = loaded.activeWallet
            sessionPin = null
            onVerified()
        }
    }

    fun confirmSecurityWithPin(pin: String, onVerified: () -> Unit) {
        runBusy {
            val loaded = withContext(Dispatchers.IO) { secureStore.loadVaultWithPin(pin) }
            vault = loaded
            wallet = loaded.activeWallet
            sessionPin = pin
            onVerified()
        }
    }

    fun openSettings() {
        rpcNetworks = rpcRepository.rpcNetworks()
        activeRpcNetwork = rpcRepository.activeNetwork()
        dApps = connectedDAppSessions()
        addressBookContacts = addressBookRepository.contacts()
        refreshIdentityProviders()
        refreshWalletIdentityNames()
        route = AppRoute.Settings
    }

    fun closeSettings() {
        route = AppRoute.Dashboard
    }

    fun openAbout() {
        route = AppRoute.About
    }

    fun closeAbout() {
        route = AppRoute.Settings
    }

    fun openRpcManagement() {
        openRpcManagement(returnRoute = AppRoute.Settings)
    }

    fun openRpcManagementFromBridge() {
        openRpcManagement(returnRoute = AppRoute.Bridge)
    }

    private fun openRpcManagement(returnRoute: AppRoute) {
        rpcManagementReturnRoute = returnRoute
        rpcNetworks = rpcRepository.rpcNetworks()
        activeRpcNetwork = rpcRepository.activeNetwork()
        route = AppRoute.RpcManagement
    }

    fun closeRpcManagement() {
        route = rpcManagementReturnRoute
    }

    fun openAddRpcNetwork() {
        route = AppRoute.RpcAddNetwork
    }

    fun closeAddRpcNetwork() {
        route = AppRoute.RpcManagement
    }

    fun openSecuritySettings() {
        route = AppRoute.SecuritySettings
    }

    fun openBackupSettings() {
        route = AppRoute.BackupSettings
    }

    fun openAppearanceSettings() {
        route = AppRoute.AppearanceSettings
    }

    fun openConnectedDApps() {
        dApps = connectedDAppSessions()
        route = AppRoute.ConnectedDApps
    }

    fun openIdentityProviderSettings() {
        refreshIdentityProviders()
        route = AppRoute.IdentityProviderSettings
    }

    fun openAddIdentityProvider() {
        route = AppRoute.IdentityProviderAdd
    }

    fun closeAddIdentityProvider() {
        route = AppRoute.IdentityProviderSettings
    }

    fun addIdentityProvider(name: String, contractAddress: String, network: String) {
        runBusy {
            val provider = OctraIdentityProvider(
                id = "identity-${System.currentTimeMillis()}",
                name = name,
                contractAddress = contractAddress,
                network = network,
            )
            identityProviders = identityProviderRepository.saveProvider(provider)
            activeIdentityProvider = identityProviderRepository.activeProvider(currentIdentityNetwork())
            refreshWalletIdentityNames()
            showMessage("Identity provider added.")
            route = AppRoute.IdentityProviderSettings
        }
    }

    fun activateIdentityProvider(id: String) {
        runBusy {
            activeIdentityProvider = identityProviderRepository.setActiveProvider(currentIdentityNetwork(), id)
            identityProviders = identityProviderRepository.providers(currentIdentityNetwork())
            refreshWalletIdentityNames()
            showMessage("Identity provider selected.")
        }
    }

    fun deleteIdentityProvider(id: String) {
        runBusy {
            identityProviders = identityProviderRepository.deleteProvider(currentIdentityNetwork(), id)
            activeIdentityProvider = identityProviderRepository.activeProvider(currentIdentityNetwork())
            refreshWalletIdentityNames()
            showMessage("Identity provider removed.")
        }
    }

    fun closeSettingsDetail() {
        route = AppRoute.Settings
    }

    fun openAddressBook() {
        addressBookReturnRoute = AppRoute.Settings
        addressBookContacts = addressBookRepository.contacts()
        route = AppRoute.AddressBook
    }

    fun openAddressBookForSend() {
        addressBookReturnRoute = AppRoute.Send
        addressBookContacts = addressBookRepository.contacts()
        route = AppRoute.AddressBook
    }

    fun openAddressBookForAssetSend() {
        addressBookReturnRoute = AppRoute.SendAsset
        addressBookContacts = addressBookRepository.contacts()
        route = AppRoute.AddressBook
    }

    fun closeAddressBook() {
        route = addressBookReturnRoute
    }

    fun openAddAddressBookContact() {
        route = AppRoute.AddressBookAdd
    }

    fun closeAddAddressBookContact() {
        route = AppRoute.AddressBook
    }

    val choosingAddressBookRecipient: Boolean
        get() = addressBookReturnRoute == AppRoute.Send || addressBookReturnRoute == AppRoute.SendAsset

    fun addAddressBookContact(label: String, address: String, note: String, identityName: String? = null) {
        val cleanAddress = address.trim()
        if (label.trim().isBlank()) {
            showError("Contact name is required.")
            return
        }
        if (!isOctraRecipientInput(cleanAddress)) {
            showError("Enter foo.oct or a valid Octra address.")
            return
        }
        addressBookContacts = addressBookRepository.save(
            AddressBookContact(
                id = "contact-${System.currentTimeMillis()}",
                label = label,
                address = cleanAddress,
                note = note,
                identityName = identityName,
            ),
        )
        showMessage("Contact saved.")
        route = AppRoute.AddressBook
    }

    fun removeAddressBookContact(id: String) {
        addressBookContacts = addressBookRepository.remove(id)
        showMessage("Contact removed.")
    }

    fun selectAddressBookContact(contact: AddressBookContact) {
        if (!choosingAddressBookRecipient) return
        pendingRecipientAddress = contact.address
        pendingRecipientIdentityName = contact.identityName
        route = addressBookReturnRoute
    }

    fun consumeSelectedRecipient(): String? {
        val selected = pendingRecipientAddress
        pendingRecipientAddress = null
        return selected
    }

    fun consumeSelectedRecipientIdentityName(): String? {
        val selected = pendingRecipientIdentityName
        pendingRecipientIdentityName = null
        return selected
    }

    fun openAddAsset() {
        dashboardTab = DashboardTab.Assets
        assetPreview = null
        route = AppRoute.AddAsset
    }

    fun closeAddAsset() {
        assetPreview = null
        dashboardTab = DashboardTab.Assets
        route = AppRoute.Dashboard
    }

    fun openSendAsset(token: OctraToken) {
        dashboardTab = DashboardTab.Assets
        selectedOctraToken = token
        refreshBalance()
        refreshRecommendedFee("call")
        route = AppRoute.SendAsset
    }

    fun closeSendAsset() {
        selectedOctraToken = null
        dashboardTab = DashboardTab.Assets
        route = AppRoute.Dashboard
    }

    fun openSend() {
        dashboardTab = DashboardTab.Overview
        refreshBalance()
        refreshRecommendedFee()
        route = AppRoute.Send
    }

    fun closeSend() {
        dashboardTab = DashboardTab.Overview
        route = AppRoute.Dashboard
    }

    fun openReceive() {
        dashboardTab = DashboardTab.Overview
        route = AppRoute.Receive
    }

    fun closeReceive() {
        dashboardTab = DashboardTab.Overview
        route = AppRoute.Dashboard
    }

    fun openBridge() {
        dashboardTab = DashboardTab.Overview
        bridgeError = null
        clearFinishedBridgeSession()
        if (bridgeBusy || bridgeDialogStarted) {
            bridgeDialogVisible = true
            bridgeDialogStarted = true
        }
        if (!bridgeDialogVisible && !bridgeBusy) {
            bridgeStep = BridgeStep.Idle
            bridgeProgress = "Bridge is ready."
            bridgeOctraTxHash = null
            bridgeEvmTxHash = null
            bridgePendingEvmTransaction = null
        }
        route = AppRoute.Bridge
        refreshBridgeData()
    }

    fun resumeBridgeDialog() {
        if (!bridgeBusy && (bridgeStep == BridgeStep.Completed || bridgeStep == BridgeStep.Failed)) {
            clearFinishedBridgeSession()
            showError("No active bridge flow to resume.")
            return
        }
        if (!bridgeBusy && !bridgeDialogStarted && bridgeStep == BridgeStep.Idle) {
            showError("No active bridge flow to resume.")
            return
        }
        dashboardTab = DashboardTab.Overview
        route = AppRoute.Bridge
        bridgeDialogVisible = true
        bridgeDialogStarted = true
    }

    fun closeBridge() {
        dashboardTab = DashboardTab.Overview
        route = AppRoute.Dashboard
    }

    fun prepareBridgeDialog(amount: String, receiverAddress: String, started: Boolean = false) {
        clearFinishedBridgeSession()
        bridgeClaimRecoveryMode = false
        bridgeDialogAmount = amount
        bridgeDialogReceiverAddress = receiverAddress
        bridgeDialogStarted = started
        bridgeDialogVisible = true
    }

    fun prepareBridgeHistoryRecoveryDialog(itemId: String) {
        if (bridgeBusy) {
            showError("Wait until the active bridge flow finishes.")
            return
        }
        configureBridgeRepositoryOrWarn()?.let { warning ->
            showError(warning)
            return
        }
        val item = bridgeHistory.firstOrNull { it.id == itemId } ?: return
        val amountRaw = item.amountRaw.toBigIntegerOrNull()
        val amount = amountRaw?.takeIf { it > BigInteger.ZERO }?.let { bridgeRepository.formatAmount(it) }
            ?: item.amount.ifBlank { "0" }
        bridgeDirection = item.direction
        bridgeDialogAmount = amount
        bridgeDialogReceiverAddress = when (item.direction) {
            BridgeDirection.OctToWoct -> item.evmAddress
            BridgeDirection.WoctToOct -> item.walletAddress
        }
        bridgeDialogStarted = false
        bridgeDialogVisible = true
        bridgeClaimRecoveryMode = item.direction == BridgeDirection.OctToWoct
        bridgeStep = BridgeStep.Idle
        bridgeProgress = "Review bridge recovery."
        bridgeError = null
        bridgeEvmGasEstimate = null
        bridgeGasLoading = false
        if (item.direction == BridgeDirection.OctToWoct) {
            refreshBridgeReviewEstimates(amount)
        }
    }

    fun dismissBridgeDialog() {
        bridgeDialogVisible = false
        if (bridgeBusy) {
            bridgeDialogStarted = true
            showMessage("Bridge minimized. Track it from Bridge History.")
            return
        }
        bridgeDialogStarted = false
        if (bridgeStep == BridgeStep.Completed || bridgeStep == BridgeStep.Failed) {
            clearFinishedBridgeSession()
        }
    }

    private fun clearFinishedBridgeSession() {
        if (bridgeBusy) return
        if (bridgeStep != BridgeStep.Completed && bridgeStep != BridgeStep.Failed) return
        bridgeDialogVisible = false
        bridgeDialogStarted = false
        bridgeDialogAmount = ""
        bridgeDialogReceiverAddress = ""
        bridgeClaimRecoveryMode = false
        bridgePendingEvmTransaction = null
        activeBridgeHistoryId = null
        submittedTransaction = null
        bridgeOctraTxHash = null
        bridgeEvmTxHash = null
        bridgeStep = BridgeStep.Idle
        bridgeProgress = "Bridge is ready."
        bridgeError = null
    }

    fun speedUpBridgeEvmTransaction() {
        val activeWallet = wallet ?: return showError("Unlock wallet before speeding up this transaction.")
        val pending = bridgePendingEvmTransaction ?: return showError("No pending EVM bridge transaction to speed up.")
        if (!bridgeCanSpeedUp || bridgeSpeedUpBusy) return
        scope.launch {
            bridgeSpeedUpBusy = true
            try {
                val result = withContext(Dispatchers.IO) {
                    bridgeRepository.speedUpEvmTransaction(activeWallet, pending)
                }
                if (!result.success) throw IllegalStateException(result.error ?: "Speed up transaction failed.")
                val hash = result.hash ?: throw IllegalStateException("Speed up did not return a transaction hash.")
                bridgePendingEvmTransaction = result
                bridgeEvmTxHash = hash
                bridgeProgress = "Speed up submitted. Waiting for Ethereum confirmation..."
                activeBridgeHistoryId?.let { itemId ->
                    setBridgeHistoryProgress(itemId, bridgeStep, bridgeProgress, evmHash = hash)
                }
                updateSubmittedTransactionResult(
                    hash = hash,
                    recipientAddress = result.toAddress ?: pending.toAddress.orEmpty(),
                    amount = submittedTransaction?.amount ?: 0.0,
                    status = TransactionFinality.Accepted,
                    showDialog = false,
                )
                showMessage("Speed up transaction submitted.")
            } catch (exception: Exception) {
                showError(exception.message ?: "Speed up transaction failed.")
            } finally {
                bridgeSpeedUpBusy = false
            }
        }
    }

    fun openBridgeHistory() {
        bridgeHistory = loadBridgeHistory()
        route = AppRoute.BridgeHistory
        refreshBridgeHistory()
    }

    fun closeBridgeHistory() {
        route = AppRoute.Bridge
    }

    private fun currentBridgeNetworkWarning(): String? {
        val octraNetwork = activeRpcNetwork ?: runCatching { rpcRepository.activeNetwork() }.getOrNull()
        val evmNetwork = activeEvmNetwork ?: runCatching { evmRepository.activeNetwork() }.getOrNull()
        if (octraNetwork?.networkType != "mainnet") {
            return "Bridge requires Octra Mainnet. Change Octra network first."
        }
        if (evmNetwork?.chainId != 1L) {
            return "Bridge requires Ethereum Mainnet. Change EVM network first."
        }
        return null
    }

    private fun configureBridgeRepositoryOrWarn(): String? {
        val warning = currentBridgeNetworkWarning()
        if (warning != null) return warning
        val octraNetwork = activeRpcNetwork ?: rpcRepository.activeNetwork()
        val evmNetwork = activeEvmNetwork ?: evmRepository.activeNetwork()
        bridgeRepository.configure(
            octraRpcUrl = octraNetwork.url,
            evmRpcUrl = evmNetwork.rpcUrl,
        )
        return null
    }

    fun refreshBridgeHistory() {
        val activeWallet = wallet ?: return
        if (bridgeHistoryLoading) return
        configureBridgeRepositoryOrWarn()?.let { warning ->
            showError(warning)
            return
        }
        bridgeHistoryLoading = true
        scope.launch {
            try {
                val chainItems = withContext(Dispatchers.IO) {
                    bridgeRepository.fetchOnChainHistory(activeWallet)
                }
                val merged = mergeBridgeHistory(loadBridgeHistory(), chainItems)
                bridgeHistory = merged
                persistBridgeHistory(merged)
                bridgeError = null
            } catch (exception: Exception) {
                showError(exception.message ?: "Bridge history refresh failed.")
            } finally {
                bridgeHistoryLoading = false
            }
        }
    }

    fun selectBridgeDirection(direction: BridgeDirection) {
        clearFinishedBridgeSession()
        bridgeDirection = direction
        bridgeError = null
    }

    fun bridgeEvmAddress(): String = wallet?.let { bridgeRepository.deriveEvmAddress(it) }.orEmpty()

    fun refreshBridgeBalances() {
        val activeWallet = wallet ?: return
        configureBridgeRepositoryOrWarn()?.let { warning ->
            bridgeError = warning
            bridgeStatus = BridgeStatus(error = warning)
            return
        }
        bridgeLoading = true
        scope.launch {
            try {
                val nextBalances = withContext(Dispatchers.IO) {
                    bridgeRepository.fetchBalances(activeWallet)
                }
                val nextStatus = withContext(Dispatchers.IO) {
                    bridgeRepository.fetchStatus(activeWallet)
                }
                bridgeBalances = nextBalances
                bridgeStatus = nextStatus
                bridgeError = null
            } catch (exception: Exception) {
                bridgeError = exception.message ?: "Bridge balances failed to sync."
            } finally {
                bridgeLoading = false
            }
        }
    }

    fun refreshBridgeData() {
        refreshRecommendedFee("call")
        refreshBridgeBalances()
    }

    fun refreshBridgeReviewEstimates(amountText: String) {
        val activeWallet = wallet ?: return
        refreshRecommendedFee("call")
        configureBridgeRepositoryOrWarn()?.let {
            bridgeEvmGasEstimate = null
            bridgeGasLoading = false
            return
        }
        val amountRaw = runCatching { bridgeRepository.parseAmount(amountText) }
            .getOrDefault(BigInteger.ONE)
            .takeIf { it > BigInteger.ZERO }
            ?: BigInteger.ONE
        val direction = bridgeDirection
        bridgeGasLoading = true
        scope.launch {
            try {
                bridgeEvmGasEstimate = withContext(Dispatchers.IO) {
                    bridgeRepository.estimateBridgeEvmGas(activeWallet, direction, amountRaw)
                }
            } catch (_: Exception) {
                bridgeEvmGasEstimate = null
            } finally {
                bridgeGasLoading = false
            }
        }
    }

    private fun refreshBridgeAfterNetworkChangeIfNeeded(returnRoute: AppRoute) {
        if (returnRoute == AppRoute.Bridge || returnRoute == AppRoute.BridgeHistory) {
            refreshBridgeData()
            refreshBridgeHistory()
        }
    }

    fun bridgeMaxAmount(): String {
        val balanceRaw = when (bridgeDirection) {
            BridgeDirection.OctToWoct -> bridgeBalances.octRaw
                .subtract(BigInteger.valueOf(recommendedFeeOu.coerceAtLeast(1L)))
                .subtract(BigInteger.valueOf(BridgeFeeReserveOu))
            BridgeDirection.WoctToOct -> bridgeBalances.woctRaw
        }
        val remainingRaw = when (bridgeDirection) {
            BridgeDirection.OctToWoct -> bridgeStatus.dailyLockRemainingRaw
            BridgeDirection.WoctToOct -> bridgeStatus.dailyUnlockRemainingRaw
        }
        val raw = remainingRaw?.let { if (it < balanceRaw) it else balanceRaw } ?: balanceRaw
        return bridgeRepository.formatAmount(if (raw > BigInteger.ZERO) raw else BigInteger.ZERO)
    }

    private fun loadBridgeHistory(): List<BridgeHistoryItem> {
        val raw = bridgePrefs.getString(KeyBridgeHistory, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = runCatching { BridgeHistoryItem.fromJson(array.getJSONObject(index)) }.getOrNull()
                    if (item != null && item.id.isNotBlank()) add(item)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistBridgeHistory(items: List<BridgeHistoryItem>) {
        val array = JSONArray()
        items.take(MaxBridgeHistoryItems).forEach { array.put(it.toJson()) }
        bridgePrefs.edit().putString(KeyBridgeHistory, array.toString()).apply()
    }

    private fun upsertBridgeHistory(item: BridgeHistoryItem) {
        val updated = item.copy(updatedAt = System.currentTimeMillis())
        val next = (listOf(updated) + bridgeHistory.filterNot { it.id == item.id }).take(MaxBridgeHistoryItems)
        bridgeHistory = next
        persistBridgeHistory(next)
    }

    private fun updateBridgeHistoryItem(
        itemId: String,
        transform: (BridgeHistoryItem) -> BridgeHistoryItem,
    ) {
        val source = if (bridgeHistory.any { it.id == itemId }) bridgeHistory else loadBridgeHistory()
        val current = source.firstOrNull { it.id == itemId } ?: return
        val updated = transform(current).copy(updatedAt = System.currentTimeMillis())
        val next = (listOf(updated) + source.filterNot { it.id == itemId }).take(MaxBridgeHistoryItems)
        bridgeHistory = next
        persistBridgeHistory(next)
    }

    private fun setBridgeHistoryProgress(
        itemId: String,
        step: BridgeStep,
        message: String,
        status: String = "running",
        octraHash: String? = null,
        evmHash: String? = null,
    ) {
        updateBridgeHistoryItem(itemId) {
            it.copy(
                status = status,
                step = step,
                message = message,
                octraTxHash = octraHash ?: it.octraTxHash,
                evmTxHash = evmHash ?: it.evmTxHash,
            )
        }
    }

    private fun mergeBridgeHistory(
        localItems: List<BridgeHistoryItem>,
        chainItems: List<BridgeChainHistoryRecord>,
    ): List<BridgeHistoryItem> {
        val consumedLocalIds = mutableSetOf<String>()
        val mergedChain = chainItems.map { chain ->
            val local = localItems.firstOrNull { local ->
                (chain.octraTxHash != null && local.octraTxHash?.equals(chain.octraTxHash, ignoreCase = true) == true) ||
                    (chain.evmTxHash != null && local.evmTxHash?.equals(chain.evmTxHash, ignoreCase = true) == true)
            }
            if (local != null) consumedLocalIds.add(local.id)
            mergeBridgeHistoryItem(local, chain)
        }
        return (mergedChain + localItems.filterNot { it.id in consumedLocalIds })
            .distinctBy { it.id }
            .sortedByDescending { it.updatedAt }
            .take(MaxBridgeHistoryItems)
    }

    private fun mergeBridgeHistoryItem(
        local: BridgeHistoryItem?,
        chain: BridgeChainHistoryRecord,
    ): BridgeHistoryItem {
        val localTerminal = local?.status == "completed"
        val chainTerminal = chain.status == "completed"
        val keepLocalState = localTerminal && !chainTerminal
        return BridgeHistoryItem(
            id = local?.id ?: chain.id,
            walletAddress = chain.walletAddress.ifBlank { local?.walletAddress.orEmpty() },
            evmAddress = chain.evmAddress.ifBlank { local?.evmAddress.orEmpty() },
            direction = chain.direction,
            amount = chain.amount.ifBlank { local?.amount ?: "0" },
            amountRaw = chain.amountRaw.ifBlank { local?.amountRaw ?: "0" },
            status = if (keepLocalState) local?.status ?: chain.status else chain.status,
            step = if (keepLocalState) local?.step ?: chain.step else chain.step,
            message = if (keepLocalState) local?.message ?: chain.message else chain.message,
            octraTxHash = chain.octraTxHash ?: local?.octraTxHash,
            evmTxHash = local?.evmTxHash ?: chain.evmTxHash,
            createdAt = listOf(local?.createdAt ?: chain.createdAt, chain.createdAt)
                .filter { it > 0L }
                .minOrNull() ?: System.currentTimeMillis(),
            updatedAt = maxOf(local?.updatedAt ?: 0L, chain.updatedAt),
        )
    }

    fun recoverBridgeHistory(itemId: String, evmGasPriceGwei: String? = null) {
        val activeWallet = wallet ?: return
        configureBridgeRepositoryOrWarn()?.let { warning ->
            showError(warning)
            return
        }
        val item = bridgeHistory.firstOrNull { it.id == itemId } ?: return
        val amountRaw = item.amountRaw.toBigIntegerOrNull() ?: run {
            showError("Bridge recovery amount is invalid.")
            return
        }
        val selectedEvmGasPriceWei = evmGasPriceGwei.toGasPriceWeiOrNull()
        activeBridgeHistoryId = item.id
        bridgeDirection = item.direction
        bridgeDialogAmount = bridgeRepository.formatAmount(amountRaw)
        bridgeDialogReceiverAddress = when (item.direction) {
            BridgeDirection.OctToWoct -> item.evmAddress
            BridgeDirection.WoctToOct -> item.walletAddress
        }
        bridgeDialogVisible = true
        bridgeDialogStarted = true
        bridgeClaimRecoveryMode = item.direction == BridgeDirection.OctToWoct
        bridgePendingEvmTransaction = null
        bridgeOctraTxHash = item.octraTxHash
        bridgeEvmTxHash = item.evmTxHash
        scope.launch {
            bridgeBusy = true
            bridgeDirection = item.direction
            bridgeStep = if (item.direction == BridgeDirection.OctToWoct) BridgeStep.Claiming else item.step
            bridgeProgress = if (item.direction == BridgeDirection.OctToWoct) "Preparing wOCT claim..." else "Recovering bridge..."
            bridgeError = null
            clearError()
            updateBridgeHistoryItem(item.id) {
                it.copy(status = "running", step = bridgeStep, message = bridgeProgress)
            }
            try {
                when (item.direction) {
                    BridgeDirection.OctToWoct -> recoverOctToWoct(activeWallet, item, amountRaw, selectedEvmGasPriceWei)
                    BridgeDirection.WoctToOct -> recoverWoctToOct(activeWallet, item, amountRaw)
                }
                if (bridgeStep == BridgeStep.Completed) refreshBridgeHistory()
                refreshBridgeBalances()
                refreshBalance()
                refreshAssets()
                refreshEvmDashboard()
                showMessage("Bridge recovery updated.")
            } catch (exception: Exception) {
                val message = bridgeUserMessage(exception.message ?: "Bridge recovery failed.")
                bridgeStep = BridgeStep.Failed
                bridgeError = message
                updateBridgeHistoryItem(item.id) {
                    it.copy(status = "failed", message = message)
                }
                showError(message)
            } finally {
                bridgeBusy = false
            }
        }
    }

    private suspend fun recoverOctToWoct(
        activeWallet: Wallet,
        item: BridgeHistoryItem,
        amountRaw: BigInteger,
        evmGasPriceWei: BigInteger?,
    ) {
        val octraHash = item.octraTxHash ?: throw IllegalStateException("No Octra lock transaction was saved for recovery.")
        bridgeOctraTxHash = octraHash
        val amount = item.amount.toDoubleOrNull() ?: 0.0

        item.evmTxHash?.let { claimHash ->
            bridgeEvmTxHash = claimHash
            bridgeStep = BridgeStep.Claiming
            bridgeProgress = "Checking existing wOCT claim..."
            setBridgeHistoryProgress(item.id, BridgeStep.Claiming, bridgeProgress, octraHash = octraHash, evmHash = claimHash)
            val receipt = withContext(Dispatchers.IO) { bridgeRepository.waitForEvmReceipt(claimHash) }
            if (receipt.status == TransactionNetworkStatus.Confirmed) {
                updateSubmittedTransaction(claimHash, TransactionFinality.Confirmed)
                bridgeStep = BridgeStep.Completed
                bridgeProgress = "wOCT confirmed on Ethereum."
                setBridgeHistoryProgress(item.id, BridgeStep.Completed, bridgeProgress, status = "completed", octraHash = octraHash, evmHash = claimHash)
                return
            }
            throw IllegalStateException(receipt.reason ?: "Existing wOCT claim is still pending.")
        }

        bridgeStep = BridgeStep.Claiming
        bridgeProgress = "Preparing wOCT claim..."
        setBridgeHistoryProgress(item.id, BridgeStep.Claiming, bridgeProgress, octraHash = octraHash)
        val locked = withContext(Dispatchers.IO) { bridgeRepository.waitForLockedEvent(octraHash) }

        bridgeStep = BridgeStep.Claiming
        bridgeProgress = "Minting wOCT on Ethereum..."
        setBridgeHistoryProgress(item.id, BridgeStep.Claiming, bridgeProgress, octraHash = octraHash)
        val claimData = withContext(Dispatchers.IO) {
            bridgeRepository.waitForClaimData(
                epochId = locked.epoch,
                recipient = locked.ethAddress,
                rawAmount = locked.amountRaw.takeIf { it > BigInteger.ZERO } ?: amountRaw,
                srcNonce = locked.srcNonce,
            )
        }

        bridgeStep = BridgeStep.Claiming
        bridgeProgress = "Minting wOCT on Ethereum..."
        setBridgeHistoryProgress(item.id, BridgeStep.Claiming, bridgeProgress, octraHash = octraHash)
        beginSubmittedTransaction(BridgeRepository.WoctBridgeContract, amount, showDialog = false)
        val claim = claimWoctWithReadinessRetry(
            activeWallet = activeWallet,
            claimData = claimData,
            gasPriceWei = evmGasPriceWei,
            historyId = item.id,
            octraHash = octraHash,
        )
        if (!claim.success) {
            if (isBridgeClaimReadinessError(claim.error)) {
                markBridgeClaimDeferred(item.id, octraHash)
                return
            }
            throw IllegalStateException(claim.error ?: "wOCT claim rejected.")
        }
        val claimHash = claim.hash ?: throw IllegalStateException("wOCT claim did not return a transaction hash.")
        bridgeEvmTxHash = claimHash
        updateSubmittedTransactionResult(
            hash = claimHash,
            recipientAddress = BridgeRepository.WoctBridgeContract,
            amount = amount,
            status = TransactionFinality.Accepted,
            showDialog = false,
        )
        setBridgeHistoryProgress(item.id, BridgeStep.Claiming, "wOCT claim submitted.", octraHash = octraHash, evmHash = claimHash)
        val receipt = withContext(Dispatchers.IO) { bridgeRepository.waitForEvmReceipt(claimHash) }
        if (receipt.status != TransactionNetworkStatus.Confirmed) {
            updateSubmittedTransaction(claimHash, TransactionFinality.Rejected, receipt.reason)
            throw IllegalStateException(receipt.reason ?: "wOCT claim is still pending.")
        }
        updateSubmittedTransaction(claimHash, TransactionFinality.Confirmed)
        bridgeStep = BridgeStep.Completed
        bridgeProgress = "wOCT confirmed on Ethereum."
        setBridgeHistoryProgress(item.id, BridgeStep.Completed, bridgeProgress, status = "completed", octraHash = octraHash, evmHash = claimHash)
    }

    private suspend fun recoverWoctToOct(activeWallet: Wallet, item: BridgeHistoryItem, amountRaw: BigInteger) {
        val burnHash = item.evmTxHash ?: throw IllegalStateException("No burn transaction was saved for recovery. Retry bridge from the form.")
        if (item.step == BridgeStep.Approving) {
            throw IllegalStateException("Only a burn or unlock step can be recovered. Retry bridge from the form after approve failure.")
        }
        bridgeEvmTxHash = burnHash
        bridgeStep = BridgeStep.WaitingUnlock
        bridgeProgress = "Checking burn and waiting for OCT unlock..."
        setBridgeHistoryProgress(item.id, BridgeStep.WaitingUnlock, bridgeProgress, evmHash = burnHash)
        val receipt = withContext(Dispatchers.IO) { bridgeRepository.waitForEvmReceipt(burnHash) }
        if (receipt.status != TransactionNetworkStatus.Confirmed) throw IllegalStateException(receipt.reason ?: "Burn transaction is still pending.")
        updateSubmittedTransaction(burnHash, TransactionFinality.Confirmed)
        val unlocked = withContext(Dispatchers.IO) {
            bridgeRepository.waitForOctraUnlock(
                recipientAddress = activeWallet.address,
                amountRaw = amountRaw,
                startedAtSeconds = (item.createdAt / 1000L).coerceAtLeast(0L),
                burnTxHash = burnHash,
            )
        }
        if (!unlocked) {
            bridgeProgress = "Burn confirmed. OCT unlock is still pending on Octra."
            setBridgeHistoryProgress(item.id, BridgeStep.WaitingUnlock, bridgeProgress, status = "running", evmHash = burnHash)
            return
        }
        bridgeStep = BridgeStep.Completed
        bridgeProgress = "OCT received from Unlock Operator."
        setBridgeHistoryProgress(item.id, BridgeStep.Completed, bridgeProgress, status = "completed", evmHash = burnHash)
    }

    private suspend fun claimWoctWithReadinessRetry(
        activeWallet: Wallet,
        claimData: BridgeClaimData,
        gasPriceWei: BigInteger?,
        historyId: String,
        octraHash: String,
    ): EvmSendResult {
        var lastResult: EvmSendResult? = null
        repeat(BridgeClaimRetryAttempts) { attempt ->
            val result = withContext(Dispatchers.IO) {
                bridgeRepository.claimWoct(activeWallet, claimData, gasPriceWeiOverride = gasPriceWei)
            }
            if (result.success || !isBridgeClaimReadinessError(result.error)) return result
            lastResult = result
            if (attempt < BridgeClaimRetryAttempts - 1) {
                bridgeProgress = "Mint is not ready yet. Retrying wOCT claim (${attempt + 2}/$BridgeClaimRetryAttempts)..."
                setBridgeHistoryProgress(historyId, BridgeStep.Claiming, bridgeProgress, octraHash = octraHash)
                delay(BridgeClaimRetryDelayMs)
            }
        }
        return lastResult ?: EvmSendResult(success = false, error = "wOCT claim is not ready yet.")
    }

    private fun markBridgeClaimDeferred(historyId: String, octraHash: String) {
        bridgeStep = BridgeStep.Claiming
        bridgeProgress = "wOCT mint is not ready yet. Claim from Bridge History in a few minutes."
        bridgeError = null
        bridgePendingEvmTransaction = null
        submittedTransaction = null
        setBridgeHistoryProgress(
            itemId = historyId,
            step = BridgeStep.Claiming,
            message = bridgeProgress,
            status = "claimable",
            octraHash = octraHash,
        )
        showMessage("wOCT claim will be ready shortly. Use Bridge History to claim.")
    }

    private fun isBridgeClaimReadinessError(message: String?): Boolean {
        val normalized = message.orEmpty().lowercase()
        return normalized.contains("execution reverted") ||
            normalized.contains("reverted") ||
            normalized.contains("claim data is not ready") ||
            normalized.contains("claim is not ready") ||
            normalized.contains("evm rpc error")
    }

    fun submitBridge(
        amountText: String,
        octraFeeOu: Long? = null,
        evmGasPriceGwei: String? = null,
    ) {
        val activeWallet = wallet ?: return
        configureBridgeRepositoryOrWarn()?.let { warning ->
            showError(warning)
            return
        }
        val amountRaw = runCatching { bridgeRepository.parseAmount(amountText) }.getOrElse {
            showError("Enter a valid bridge amount.")
            return
        }
        if (amountRaw <= BigInteger.ZERO) {
            showError("Amount must be greater than zero.")
            return
        }
        if (bridgeStatus.isPaused == true) {
            showError("Bridge is closed.")
            return
        }
        val remainingRaw = when (bridgeDirection) {
            BridgeDirection.OctToWoct -> bridgeStatus.dailyLockRemainingRaw
            BridgeDirection.WoctToOct -> bridgeStatus.dailyUnlockRemainingRaw
        }
        if (remainingRaw != null && amountRaw > remainingRaw) {
            showError("Amount exceeds today's bridge limit.")
            return
        }
        val amount = amountText.replace(",", ".").trim().toDoubleOrNull() ?: 0.0
        val direction = bridgeDirection
        val receiverAddress = when (direction) {
            BridgeDirection.OctToWoct -> bridgeRepository.deriveEvmAddress(activeWallet)
            BridgeDirection.WoctToOct -> activeWallet.address
        }
        val selectedOctraFeeOu = octraFeeOu?.takeIf { it > 0L } ?: recommendedFeeOu
        val selectedEvmGasPriceWei = evmGasPriceGwei.toGasPriceWeiOrNull()
        val historyId = "bridge-${System.currentTimeMillis()}"
        activeBridgeHistoryId = historyId
        bridgePendingEvmTransaction = null
        bridgeDialogVisible = true
        bridgeDialogStarted = true
        bridgeDialogAmount = bridgeRepository.formatAmount(amountRaw)
        bridgeDialogReceiverAddress = receiverAddress
        upsertBridgeHistory(
            BridgeHistoryItem(
                id = historyId,
                walletAddress = activeWallet.address,
                evmAddress = bridgeRepository.deriveEvmAddress(activeWallet),
                direction = direction,
                amount = bridgeRepository.formatAmount(amountRaw),
                amountRaw = amountRaw.toString(),
                status = "running",
                step = BridgeStep.Idle,
                message = "Bridge started.",
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
        scope.launch {
            bridgeBusy = true
            bridgeError = null
            bridgeStep = BridgeStep.Idle
            bridgeProgress = "Bridge is starting..."
            bridgeOctraTxHash = null
            bridgeEvmTxHash = null
            clearError()
            try {
                when (direction) {
                    BridgeDirection.OctToWoct -> bridgeOctToWoct(
                        activeWallet = activeWallet,
                        amountRaw = amountRaw,
                        amount = amount,
                        historyId = historyId,
                        feeOu = selectedOctraFeeOu,
                        evmGasPriceWei = selectedEvmGasPriceWei,
                    )
                    BridgeDirection.WoctToOct -> bridgeWoctToOct(
                        activeWallet = activeWallet,
                        amountRaw = amountRaw,
                        amount = amount,
                        historyId = historyId,
                        evmGasPriceWei = selectedEvmGasPriceWei,
                    )
                }
                updateBridgeHistoryItem(historyId) {
                    when {
                        bridgeStep == BridgeStep.Completed -> it.copy(status = "completed", step = BridgeStep.Completed, message = bridgeProgress)
                        it.status == "claimable" -> it.copy(step = bridgeStep, message = bridgeProgress)
                        else -> it.copy(status = "running", step = bridgeStep, message = bridgeProgress)
                    }
                }
                if (bridgeStep == BridgeStep.Completed) refreshBridgeHistory()
                refreshBridgeBalances()
                refreshBalance()
                refreshAssets()
                refreshEvmDashboard()
            } catch (exception: Exception) {
                val message = bridgeUserMessage(exception.message ?: "Bridge transaction failed.")
                updateBridgeHistoryItem(historyId) {
                    it.copy(status = "failed", message = message)
                }
                bridgeStep = BridgeStep.Failed
                bridgeError = message
                rejectSubmittingTransaction(message)
                showError(message)
            } finally {
                bridgeBusy = false
                if (bridgeStep == BridgeStep.Completed || bridgeStep == BridgeStep.Failed) {
                    bridgePendingEvmTransaction = null
                }
            }
        }
    }

    private suspend fun bridgeOctToWoct(
        activeWallet: Wallet,
        amountRaw: BigInteger,
        amount: Double,
        historyId: String,
        feeOu: Long,
        evmGasPriceWei: BigInteger?,
    ) {
        val evmAddress = bridgeRepository.deriveEvmAddress(activeWallet)
        if (amountRaw > bridgeBalances.octRaw) throw IllegalArgumentException("Insufficient OCT on Octra mainnet.")
        bridgeStep = BridgeStep.Locking
        bridgeProgress = "Locking OCT on Octra mainnet..."
        setBridgeHistoryProgress(historyId, BridgeStep.Locking, bridgeProgress)
        beginSubmittedTransaction(BridgeRepository.OctraBridgeContract, amount, showDialog = false)
        val lockResult = withContext(Dispatchers.IO) {
            rpcRepository.sendContractCall(
                wallet = activeWallet,
                contractAddress = BridgeRepository.OctraBridgeContract,
                method = BridgeRepository.OctraLockMethod,
                params = JSONArray().put(evmAddress),
                amountOu = amountRaw.toString(),
                feeOu = feeOu,
                rpcUrl = activeRpcNetwork?.url ?: rpcRepository.currentUrl(),
            )
        }
        if (!lockResult.success) {
            updateSubmittedTransactionResult(
                hash = lockResult.hash,
                recipientAddress = BridgeRepository.OctraBridgeContract,
                amount = amount,
                status = TransactionFinality.Rejected,
                reason = lockResult.error ?: "Bridge lock rejected.",
                showDialog = false,
            )
            throw IllegalStateException(lockResult.error ?: "Bridge lock rejected.")
        }
        val octraHash = lockResult.hash ?: throw IllegalStateException("Octra lock did not return a transaction hash.")
        bridgeOctraTxHash = octraHash
        setBridgeHistoryProgress(historyId, BridgeStep.ConfirmingOctra, "Lock submitted. Waiting for Octra confirmation...", octraHash = octraHash)
        updateSubmittedTransactionResult(
            hash = octraHash,
            recipientAddress = BridgeRepository.OctraBridgeContract,
            amount = amount,
            status = TransactionFinality.Accepted,
            showDialog = false,
        )
        addPendingTransaction(
            hash = octraHash,
            from = activeWallet.address,
            recipient = BridgeRepository.OctraBridgeContract,
            amount = amount,
            message = BridgeRepository.OctraLockMethod,
            opType = "call",
        )

        bridgeStep = BridgeStep.ConfirmingOctra
        bridgeProgress = "Lock submitted. Waiting for Octra confirmation..."
        val locked = withContext(Dispatchers.IO) {
            bridgeRepository.waitForLockedEvent(octraHash)
        }
        updateSubmittedTransaction(octraHash, TransactionFinality.Confirmed)

        bridgeStep = BridgeStep.WaitingRelayer
        bridgeProgress = "Waiting for header on Ethereum lightclient..."
        setBridgeHistoryProgress(historyId, BridgeStep.WaitingRelayer, bridgeProgress, octraHash = octraHash)
        val claimData = withContext(Dispatchers.IO) {
            bridgeRepository.waitForClaimData(
                epochId = locked.epoch,
                recipient = locked.ethAddress,
                rawAmount = locked.amountRaw,
                srcNonce = locked.srcNonce,
            )
        }

        bridgeStep = BridgeStep.Claiming
        bridgeProgress = "Minting wOCT on Ethereum mainnet..."
        setBridgeHistoryProgress(historyId, BridgeStep.Claiming, bridgeProgress, octraHash = octraHash)
        beginSubmittedTransaction(BridgeRepository.WoctBridgeContract, amount, showDialog = false)
        val claim = claimWoctWithReadinessRetry(
            activeWallet = activeWallet,
            claimData = claimData,
            gasPriceWei = evmGasPriceWei,
            historyId = historyId,
            octraHash = octraHash,
        )
        if (!claim.success) {
            if (isBridgeClaimReadinessError(claim.error)) {
                markBridgeClaimDeferred(historyId, octraHash)
                return
            }
            updateSubmittedTransactionResult(
                hash = claim.hash,
                recipientAddress = BridgeRepository.WoctBridgeContract,
                amount = amount,
                status = TransactionFinality.Rejected,
                reason = claim.error ?: "wOCT claim rejected.",
                showDialog = false,
            )
            throw IllegalStateException(claim.error ?: "wOCT claim rejected.")
        }
        val claimHash = claim.hash ?: throw IllegalStateException("wOCT claim did not return a transaction hash.")
        bridgePendingEvmTransaction = claim
        bridgeEvmTxHash = claimHash
        setBridgeHistoryProgress(historyId, BridgeStep.Claiming, "wOCT claim submitted. Waiting for Ethereum confirmation...", octraHash = octraHash, evmHash = claimHash)
        updateSubmittedTransactionResult(
            hash = claimHash,
            recipientAddress = BridgeRepository.WoctBridgeContract,
            amount = amount,
            status = TransactionFinality.Accepted,
            showDialog = false,
        )
        val receipt = waitForBridgeEvmReceipt(claimHash)
        if (receipt.status == TransactionNetworkStatus.Confirmed) {
            bridgePendingEvmTransaction = null
            updateSubmittedTransaction(bridgeEvmTxHash ?: claimHash, TransactionFinality.Confirmed)
            bridgeStep = BridgeStep.Completed
            bridgeProgress = "wOCT confirmed on Ethereum."
            setBridgeHistoryProgress(historyId, BridgeStep.Completed, bridgeProgress, status = "completed", octraHash = octraHash, evmHash = bridgeEvmTxHash ?: claimHash)
        } else {
            updateSubmittedTransaction(bridgeEvmTxHash ?: claimHash, TransactionFinality.Rejected, receipt.reason)
            throw IllegalStateException(receipt.reason ?: "wOCT claim is still pending.")
        }
    }

    private suspend fun bridgeWoctToOct(
        activeWallet: Wallet,
        amountRaw: BigInteger,
        amount: Double,
        historyId: String,
        evmGasPriceWei: BigInteger?,
    ) {
        if (amountRaw > bridgeBalances.woctRaw) throw IllegalArgumentException("Insufficient wOCT on Ethereum mainnet.")
        bridgeStep = BridgeStep.Approving
        bridgeProgress = "Approving wOCT spend..."
        setBridgeHistoryProgress(historyId, BridgeStep.Approving, bridgeProgress)
        beginSubmittedTransaction(BridgeRepository.WoctTokenAddress, amount, showDialog = false)
        val approve = withContext(Dispatchers.IO) {
            bridgeRepository.approveWoct(activeWallet, amountRaw, gasPriceWeiOverride = evmGasPriceWei)
        }
        if (!approve.success) {
            updateSubmittedTransactionResult(
                hash = approve.hash,
                recipientAddress = BridgeRepository.WoctTokenAddress,
                amount = amount,
                status = TransactionFinality.Rejected,
                reason = approve.error ?: "wOCT approve rejected.",
                showDialog = false,
            )
            throw IllegalStateException(approve.error ?: "wOCT approve rejected.")
        }
        val approveHash = approve.hash ?: throw IllegalStateException("wOCT approve did not return a transaction hash.")
        bridgePendingEvmTransaction = approve
        bridgeEvmTxHash = approveHash
        setBridgeHistoryProgress(historyId, BridgeStep.Approving, "Approve submitted. Waiting for Ethereum confirmation...", evmHash = approveHash)
        updateSubmittedTransactionResult(
            hash = approveHash,
            recipientAddress = BridgeRepository.WoctTokenAddress,
            amount = amount,
            status = TransactionFinality.Accepted,
            showDialog = false,
        )
        val approveReceipt = waitForBridgeEvmReceipt(approveHash)
        if (approveReceipt.status != TransactionNetworkStatus.Confirmed) {
            updateSubmittedTransaction(bridgeEvmTxHash ?: approveHash, TransactionFinality.Rejected, approveReceipt.reason)
            throw IllegalStateException(approveReceipt.reason ?: "wOCT approve is still pending.")
        }
        updateSubmittedTransaction(bridgeEvmTxHash ?: approveHash, TransactionFinality.Confirmed)
        bridgePendingEvmTransaction = null

        bridgeStep = BridgeStep.Burning
        bridgeProgress = "Burning wOCT for your Octra wallet..."
        setBridgeHistoryProgress(historyId, BridgeStep.Burning, bridgeProgress, evmHash = bridgeEvmTxHash ?: approveHash)
        beginSubmittedTransaction(BridgeRepository.WoctBridgeContract, amount, showDialog = false)
        val octraBalanceBeforeBurn = withContext(Dispatchers.IO) {
            bridgeRepository.currentOctraBalanceRaw(activeWallet.address)
        }
        val burn = withContext(Dispatchers.IO) {
            bridgeRepository.burnWoctToOctra(
                wallet = activeWallet,
                octraRecipient = activeWallet.address,
                amountRaw = amountRaw,
                gasPriceWeiOverride = evmGasPriceWei,
            )
        }
        if (!burn.success) {
            updateSubmittedTransactionResult(
                hash = burn.hash,
                recipientAddress = BridgeRepository.WoctBridgeContract,
                amount = amount,
                status = TransactionFinality.Rejected,
                reason = burn.error ?: "wOCT burn rejected.",
                showDialog = false,
            )
            throw IllegalStateException(burn.error ?: "wOCT burn rejected.")
        }
        val burnHash = burn.hash ?: throw IllegalStateException("wOCT burn did not return a transaction hash.")
        bridgePendingEvmTransaction = burn
        bridgeEvmTxHash = burnHash
        setBridgeHistoryProgress(historyId, BridgeStep.Burning, "Burn submitted. Waiting for Ethereum confirmation...", evmHash = burnHash)
        updateSubmittedTransactionResult(
            hash = burnHash,
            recipientAddress = BridgeRepository.WoctBridgeContract,
            amount = amount,
            status = TransactionFinality.Accepted,
            showDialog = false,
        )
        val unlockStartedAt = System.currentTimeMillis() / 1000L
        val burnReceipt = waitForBridgeEvmReceipt(burnHash)
        if (burnReceipt.status == TransactionNetworkStatus.Confirmed) {
            bridgePendingEvmTransaction = null
            updateSubmittedTransaction(bridgeEvmTxHash ?: burnHash, TransactionFinality.Confirmed)
            bridgeStep = BridgeStep.WaitingUnlock
            bridgeProgress = "Burn confirmed. Waiting for incoming OCT from the Unlock Operator..."
            setBridgeHistoryProgress(historyId, BridgeStep.WaitingUnlock, bridgeProgress, evmHash = bridgeEvmTxHash ?: burnHash)
            val unlocked = withContext(Dispatchers.IO) {
                bridgeRepository.waitForOctraUnlock(
                    recipientAddress = activeWallet.address,
                    amountRaw = amountRaw,
                    startedAtSeconds = unlockStartedAt,
                    burnTxHash = bridgeEvmTxHash ?: burnHash,
                    previousBalanceRaw = octraBalanceBeforeBurn,
                )
            }
            if (unlocked) {
                bridgeStep = BridgeStep.Completed
                bridgeProgress = "OCT received from Unlock Operator."
                setBridgeHistoryProgress(historyId, BridgeStep.Completed, bridgeProgress, status = "completed", evmHash = bridgeEvmTxHash ?: burnHash)
            } else {
                bridgeProgress = "Burn confirmed. OCT unlock is still pending on Octra."
                setBridgeHistoryProgress(historyId, BridgeStep.WaitingUnlock, bridgeProgress, status = "running", evmHash = bridgeEvmTxHash ?: burnHash)
            }
        } else {
            updateSubmittedTransaction(bridgeEvmTxHash ?: burnHash, TransactionFinality.Rejected, burnReceipt.reason)
            throw IllegalStateException(burnReceipt.reason ?: "wOCT burn is still pending.")
        }
    }

    private suspend fun waitForBridgeEvmReceipt(initialHash: String): TransactionNetworkState {
        val hashes = linkedSetOf(initialHash)
        repeat(BridgeEvmReceiptAttempts) { attempt ->
            bridgeEvmTxHash?.takeIf { it.isNotBlank() }?.let(hashes::add)
            hashes.toList().forEach { hash ->
                val state = withContext(Dispatchers.IO) { bridgeRepository.evmReceiptStatus(hash) }
                when (state.status) {
                    TransactionNetworkStatus.Confirmed,
                    TransactionNetworkStatus.Rejected,
                    TransactionNetworkStatus.Dropped -> {
                        bridgeEvmTxHash = hash
                        return state
                    }
                    TransactionNetworkStatus.Pending,
                    TransactionNetworkStatus.NotFound -> Unit
                }
            }
            if (attempt < BridgeEvmReceiptAttempts - 1) delay(BridgeEvmReceiptIntervalMs)
        }
        return TransactionNetworkState(TransactionNetworkStatus.Pending, "Ethereum transaction is still pending.")
    }

    private fun bridgeUserMessage(message: String): String {
        val normalized = message.lowercase()
        return when {
            normalized.contains("execution reverted") || normalized.contains("evm rpc error") ->
                "wOCT mint is not ready yet. Refresh Bridge History, then tap Claim wOCT again."
            normalized.contains("claim data is not ready") ->
                "Bridge claim data is not ready yet. Refresh Bridge History again in a few minutes."
            else -> message
        }
    }

    private fun configureTradeRepositoryOrWarn(action: String): String? {
        val network = activeEvmNetwork ?: runCatching { evmRepository.activeNetwork() }.getOrNull()
        if (network?.chainId != 1L) {
            return "Switch EVM Network to Ethereum Mainnet before $action."
        }
        tradeRepository.configure(network.rpcUrl)
        return null
    }

    fun refreshTrade() {
        val activeWallet = wallet ?: return
        configureTradeRepositoryOrWarn("syncing trade data")?.let { warning ->
            tradeBalances = TradeBalances()
            tradeQuote = null
            tradeQuoteError = warning
            tradeQuoteLoading = false
            tradeLoading = false
            return
        }
        tradeLoading = true
        scope.launch {
            try {
                tradeBalances = withContext(Dispatchers.IO) {
                    tradeRepository.fetchBalances(activeWallet)
                }
                tradeQuoteError = null
            } catch (exception: Exception) {
                tradeQuoteError = exception.message ?: "Trade balances failed to sync."
            } finally {
                tradeLoading = false
            }
        }
    }

    fun refreshTradeChart(timeframe: TradeChartTimeframe = tradeChartTimeframe) {
        val requestId = ++tradeChartRequestSerial
        tradeChartTimeframe = timeframe
        val cached = tradeChartCache[timeframe]
        if (cached != null) {
            tradeCandles = cached
            tradeLastPrice = cached.lastOrNull()?.close?.let { "$${"%.6f".format(it)}" } ?: "--"
        } else {
            tradeCandles = emptyList()
            tradeLastPrice = "--"
        }
        tradeChartError = null
        if (!tradeNetworkSupported) {
            tradeChartError = "wOCT chart is available on Ethereum Mainnet."
            tradeChartLoading = false
            return
        }
        tradeChartLoading = true
        scope.launch {
            try {
                val candles = withContext(Dispatchers.IO) {
                    tradeRepository.fetchWoctCandles(timeframe)
                }
                if (requestId != tradeChartRequestSerial) return@launch
                tradeChartCache[timeframe] = candles
                tradeCandles = candles
                tradeLastPrice = tradeCandles.lastOrNull()?.close?.let { "$${"%.6f".format(it)}" } ?: "--"
                tradeChartError = null
            } catch (exception: Exception) {
                if (requestId != tradeChartRequestSerial) return@launch
                val fallback = tradeChartCache[timeframe] ?: tradeCandles.takeIf { it.isNotEmpty() }
                if (fallback != null) {
                    tradeCandles = fallback
                    tradeLastPrice = fallback.lastOrNull()?.close?.let { "$${"%.6f".format(it)}" } ?: tradeLastPrice
                } else {
                    tradeCandles = emptyList()
                    tradeLastPrice = "--"
                }
                tradeChartError = exception.message ?: "wOCT chart failed to load."
            } finally {
                if (requestId == tradeChartRequestSerial) tradeChartLoading = false
            }
        }
    }

    fun clearTradeQuote() {
        tradeQuoteRequestSerial++
        tradeQuote = null
        tradeQuoteError = null
        tradeQuoteLoading = false
        clearTradeDialogForNewQuote()
    }

    fun requestTradeQuote(
        fromAsset: TradeAsset,
        toAsset: TradeAsset,
        amountText: String,
        slippageBps: Int,
    ) {
        clearTradeDialogForNewQuote()
        tradeQuote = null
        tradeQuoteError = null
        val cleanAmount = amountText.replace(",", ".").trim()
        if (cleanAmount.isBlank()) return
        configureTradeRepositoryOrWarn("quoting")?.let { warning ->
            tradeQuoteError = warning
            return
        }
        if (!tradeRepository.isSupportedPair(fromAsset, toAsset)) {
            tradeQuoteError = "Trade supports wOCT pairs with ETH or USDT."
            return
        }
        val requestId = ++tradeQuoteRequestSerial
        tradeQuoteLoading = true
        scope.launch {
            try {
                val quote = withContext(Dispatchers.IO) {
                    tradeRepository.quoteExactInput(fromAsset, toAsset, cleanAmount, slippageBps)
                }
                if (requestId != tradeQuoteRequestSerial) return@launch
                tradeQuote = quote
                tradeQuoteError = null
            } catch (exception: Exception) {
                if (requestId != tradeQuoteRequestSerial) return@launch
                tradeQuote = null
                tradeQuoteError = tradeQuoteUserMessage(exception.message ?: "Uniswap v4 quote failed.")
            } finally {
                if (requestId == tradeQuoteRequestSerial) tradeQuoteLoading = false
            }
        }
    }

    fun tradeMaxAmount(asset: TradeAsset): String = tradeRepository.maxAmount(tradeBalances, asset)

    fun tradeGasInfo(): String {
        val quote = tradeQuote ?: return "Gas estimate appears after quote."
        return "~ ${quote.gasEstimate} gas / ${quote.gasFeeEth} ETH"
    }

    private fun tradeQuoteUserMessage(message: String): String {
        val normalized = message.lowercase()
        return when {
            normalized.contains("execution reverted") ||
                normalized.contains("reverted") ||
                normalized.contains("evm rpc error") ->
                "Quote unavailable for this route or amount. Try a different amount, pair, or refresh Trade."
            normalized.contains("empty result") ->
                "Quote returned no result. Try a different amount or refresh Trade."
            else -> message
        }
    }

    fun prepareTradeDialog(
        fromAsset: TradeAsset,
        toAsset: TradeAsset,
        amountText: String,
        quote: TradeQuote?,
        started: Boolean = false,
    ) {
        if (!started && !tradeBusy) {
            resetTradeDialogProgress()
        }
        tradeDialogFromAsset = fromAsset
        tradeDialogToAsset = toAsset
        tradeDialogAmount = amountText.replace(",", ".").trim()
        tradeDialogReceiveEstimate = quote?.amountOut.orEmpty()
        tradeDialogMinimumReceived = quote?.minimumOut.orEmpty()
        tradeDialogStarted = started
        tradeDialogVisible = true
    }

    fun dismissTradeDialog() {
        if (tradeBusy) return
        val shouldClear = tradeStep == TradeStep.Completed || tradeStep == TradeStep.Failed
        tradeDialogVisible = false
        tradeDialogStarted = false
        if (shouldClear) {
            resetTradeDialogProgress()
            tradeDialogAmount = ""
            tradeDialogReceiveEstimate = ""
            tradeDialogMinimumReceived = ""
        }
    }

    fun clearTradeDialogForNewQuote(): Boolean {
        if (tradeBusy || (tradeStep == TradeStep.Idle && !tradeDialogVisible && !tradeDialogStarted)) return false
        tradeDialogVisible = false
        tradeDialogStarted = false
        tradeDialogAmount = ""
        tradeDialogReceiveEstimate = ""
        tradeDialogMinimumReceived = ""
        resetTradeDialogProgress()
        return true
    }

    private fun resetTradeDialogProgress() {
        tradePendingEvmTransaction = null
        tradeSpeedUpBusy = false
        tradeLastTxHash = null
        tradeStep = TradeStep.Idle
        tradeProgress = "Swap is ready."
    }

    fun speedUpTradeEvmTransaction() {
        val activeWallet = wallet ?: return showError("Unlock wallet before speeding up this swap.")
        val pending = tradePendingEvmTransaction ?: return showError("No pending swap transaction to speed up.")
        if (!tradeCanSpeedUp || tradeSpeedUpBusy) return
        scope.launch {
            tradeSpeedUpBusy = true
            try {
                val result = withContext(Dispatchers.IO) {
                    tradeRepository.speedUpTransaction(activeWallet, pending)
                }
                if (!result.success) throw IllegalStateException(result.error ?: "Speed up transaction failed.")
                val hash = result.hash ?: throw IllegalStateException("Speed up did not return a transaction hash.")
                tradePendingEvmTransaction = result
                tradeLastTxHash = hash
                tradeProgress = "Speed up submitted. Waiting for confirmation..."
                updateSubmittedTransactionResult(
                    hash = hash,
                    recipientAddress = "Uniswap v4",
                    amount = tradeDialogAmount.toDoubleOrNull() ?: submittedTransaction?.amount ?: 0.0,
                    status = TransactionFinality.Accepted,
                    showDialog = false,
                )
                showMessage("Swap speed up submitted.")
            } catch (exception: Exception) {
                showError(exception.message ?: "Speed up transaction failed.")
            } finally {
                tradeSpeedUpBusy = false
            }
        }
    }

    fun submitTrade(
        fromAsset: TradeAsset,
        toAsset: TradeAsset,
        amountText: String,
        slippageBps: Int,
        evmGasPriceGwei: String? = null,
    ) {
        val activeWallet = wallet ?: return
        val cleanAmount = amountText.replace(",", ".").trim()
        val amount = cleanAmount.toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            showError("Enter a valid swap amount.")
            return
        }
        configureTradeRepositoryOrWarn("swapping")?.let { warning ->
            showError(warning)
            return
        }
        if (!tradeRepository.isSupportedPair(fromAsset, toAsset)) {
            showError("Trade supports wOCT pairs with ETH or USDT.")
            return
        }
        val quote = tradeQuote
        if (quote == null || quote.amountOutRaw <= BigInteger.ZERO) {
            requestTradeQuote(fromAsset, toAsset, cleanAmount, slippageBps)
            showError("Wait for a valid Uniswap v4 quote before swapping.")
            return
        }
        val selectedEvmGasPriceWei = evmGasPriceGwei.toGasPriceWeiOrNull()
        prepareTradeDialog(fromAsset, toAsset, cleanAmount, quote, started = true)
        scope.launch {
            tradeBusy = true
            clearError()
            tradeStep = if (fromAsset == TradeAsset.ETH) TradeStep.Swapping else TradeStep.TokenApproval
            tradeProgress = "Submitting swap route..."
            tradeLastTxHash = null
            tradePendingEvmTransaction = null
            tradeSpeedUpBusy = false
            beginSubmittedTransaction("Uniswap v4", amount, showDialog = false)
            try {
                val result = withContext(Dispatchers.IO) {
                    tradeRepository.swapExactInput(
                        wallet = activeWallet,
                        fromAsset = fromAsset,
                        toAsset = toAsset,
                        amountText = cleanAmount,
                        minimumOutRaw = quote.minimumOutRaw,
                        gasPriceWeiOverride = selectedEvmGasPriceWei,
                    )
                }
                if (!result.success) {
                    val message = tradeSwapUserMessage(result.error ?: "Swap rejected.")
                    updateSubmittedTransactionResult(
                        hash = result.hash,
                        recipientAddress = "Uniswap v4",
                        amount = amount,
                        status = TransactionFinality.Rejected,
                        reason = message,
                        showDialog = false,
                    )
                    tradeStep = TradeStep.Failed
                    tradeProgress = message
                    refreshTrade()
                    return@launch
                }
                waitForTradeTransactions(result.transactions, amount)
            } catch (exception: Exception) {
                val message = tradeSwapUserMessage(exception.message ?: "Swap failed.")
                tradeStep = TradeStep.Failed
                tradeProgress = message
                rejectSubmittingTransaction(message)
                showError(message)
            } finally {
                if (tradeStep == TradeStep.Completed || tradeStep == TradeStep.Failed) {
                    tradePendingEvmTransaction = null
                }
                tradeBusy = false
            }
        }
    }

    private fun tradeSwapUserMessage(message: String): String {
        val normalized = message.lowercase()
        return when {
            normalized.contains("execution reverted") ||
                normalized.contains("reverted") ->
                "Swap could not be executed for this route. Review the quote, slippage, token approval, and try again."
            normalized.contains("insufficient eth") ->
                "Insufficient ETH for swap gas."
            normalized.contains("evm rpc error") ||
                normalized.contains("replacement transaction underpriced") ->
                "Ethereum RPC rejected the swap transaction. Try Fast gas or refresh Trade."
            else -> message
        }
    }

    private suspend fun waitForTradeTransactions(transactions: List<TradeSubmittedTransaction>, amount: Double) {
        require(transactions.isNotEmpty()) { "Swap did not submit a transaction." }
        transactions.forEach { submitted ->
            val pending = submitted.transaction
            val hash = pending.hash ?: throw IllegalStateException("Swap transaction did not return a hash.")
            tradeStep = submitted.stage.toTradeStep()
            tradePendingEvmTransaction = pending
            tradeLastTxHash = hash
            tradeProgress = submitted.stage.tradeSubmittedMessage()
            updateSubmittedTransactionResult(
                hash = hash,
                recipientAddress = "Uniswap v4",
                amount = amount,
                status = TransactionFinality.Accepted,
                showDialog = false,
            )
            val receipt = waitForTradeEvmReceipt(hash)
            if (receipt.status != TransactionNetworkStatus.Confirmed) {
                updateSubmittedTransaction(tradeLastTxHash ?: hash, TransactionFinality.Rejected, receipt.reason)
                throw IllegalStateException(receipt.reason ?: "${submitted.stage.tradeTitle()} is still pending.")
            }
            updateSubmittedTransaction(tradeLastTxHash ?: hash, TransactionFinality.Confirmed)
            tradePendingEvmTransaction = null
            tradeProgress = submitted.stage.tradeConfirmedMessage()
        }
        tradeStep = TradeStep.Completed
        tradeProgress = "Swap confirmed. Balances are syncing..."
        refreshTrade()
        refreshEvmDashboard()
    }

    private suspend fun waitForTradeEvmReceipt(initialHash: String): TransactionNetworkState {
        val hashes = linkedSetOf(initialHash)
        repeat(BridgeEvmReceiptAttempts) { attempt ->
            tradeLastTxHash?.takeIf { it.isNotBlank() }?.let(hashes::add)
            hashes.toList().forEach { hash ->
                val state = withContext(Dispatchers.IO) { tradeRepository.transactionStatus(hash) }
                when (state.status) {
                    TransactionNetworkStatus.Confirmed,
                    TransactionNetworkStatus.Rejected,
                    TransactionNetworkStatus.Dropped -> {
                        tradeLastTxHash = hash
                        return state
                    }
                    TransactionNetworkStatus.Pending,
                    TransactionNetworkStatus.NotFound -> Unit
                }
            }
            if (attempt < BridgeEvmReceiptAttempts - 1) delay(BridgeEvmReceiptIntervalMs)
        }
        return TransactionNetworkState(TransactionNetworkStatus.Pending, "Swap transaction is still pending.")
    }

    private fun TradeSwapStage.toTradeStep(): TradeStep = when (this) {
        TradeSwapStage.TokenApproval -> TradeStep.TokenApproval
        TradeSwapStage.PermitApproval -> TradeStep.PermitApproval
        TradeSwapStage.Swap -> TradeStep.Swapping
    }

    private fun TradeSwapStage.tradeTitle(): String = when (this) {
        TradeSwapStage.TokenApproval -> "Token approval"
        TradeSwapStage.PermitApproval -> "Permit2 approval"
        TradeSwapStage.Swap -> "Swap"
    }

    private fun TradeSwapStage.tradeSubmittedMessage(): String = when (this) {
        TradeSwapStage.TokenApproval -> "Token approval submitted. Waiting for confirmation..."
        TradeSwapStage.PermitApproval -> "Permit2 approval submitted. Waiting for confirmation..."
        TradeSwapStage.Swap -> "Swap submitted. Waiting for Uniswap confirmation..."
    }

    private fun TradeSwapStage.tradeConfirmedMessage(): String = when (this) {
        TradeSwapStage.TokenApproval -> "Token approval confirmed."
        TradeSwapStage.PermitApproval -> "Permit2 approval confirmed."
        TradeSwapStage.Swap -> "Swap confirmed."
    }

    fun openEvm() {
        dashboardTab = DashboardTab.Assets
        evmNetworks = evmRepository.networks()
        activeEvmNetwork = evmRepository.activeNetwork()
        route = AppRoute.Evm
        refreshEvmDashboard()
    }

    fun closeEvm() {
        dashboardTab = DashboardTab.Assets
        route = AppRoute.Dashboard
    }

    fun openEvmSend(token: EvmToken? = null, nft: EvmNft? = null) {
        evmSelectedSendToken = token
        evmSelectedSendNft = nft
        route = AppRoute.EvmSend
        refreshEvmAccount()
    }

    fun closeEvmSend() {
        if (evmSending) return
        evmSelectedSendToken = null
        evmSelectedSendNft = null
        if (!evmSendDialogStarted) dismissEvmSendDialog()
        route = AppRoute.Evm
    }

    fun openEvmReceive() {
        route = AppRoute.EvmReceive
        refreshEvmAccount()
    }

    fun closeEvmReceive() {
        route = AppRoute.Evm
    }

    fun openEvmActivity() {
        refreshEvmHistory()
        route = AppRoute.EvmActivity
    }

    fun closeEvmActivity() {
        route = AppRoute.Evm
    }

    fun openEvmNetworkSettings() {
        openEvmNetworkSettings(returnRoute = AppRoute.Evm)
    }

    fun openEvmNetworkSettingsFromBridge() {
        openEvmNetworkSettings(returnRoute = AppRoute.Bridge)
    }

    fun openEvmNetworkSettingsFromTrade() {
        openEvmNetworkSettings(returnRoute = AppRoute.Dashboard, returnTab = DashboardTab.Trade)
    }

    private fun openEvmNetworkSettings(returnRoute: AppRoute, returnTab: DashboardTab? = null) {
        evmNetworkReturnRoute = returnRoute
        evmNetworkReturnTab = returnTab
        evmNetworks = evmRepository.networks()
        activeEvmNetwork = evmRepository.activeNetwork()
        route = AppRoute.EvmNetwork
    }

    fun closeEvmNetworkSettings() {
        evmNetworkReturnTab?.let { dashboardTab = it }
        route = evmNetworkReturnRoute
    }

    fun openEvmAddNetwork() {
        evmEditingNetwork = null
        route = AppRoute.EvmAddNetwork
    }

    fun openEvmEditNetwork(network: EvmNetwork) {
        evmEditingNetwork = network
        route = AppRoute.EvmAddNetwork
    }

    fun closeEvmAddNetwork() {
        evmEditingNetwork = null
        route = AppRoute.EvmNetwork
    }

    fun openEvmAddToken() {
        clearEvmAssetPreviews()
        route = AppRoute.EvmAddToken
    }

    fun closeEvmAddToken() {
        clearEvmAssetPreviews()
        route = AppRoute.Evm
    }

    fun openEvmAddNft() {
        clearEvmAssetPreviews()
        route = AppRoute.EvmAddNft
    }

    fun closeEvmAddNft() {
        clearEvmAssetPreviews()
        route = AppRoute.Evm
    }

    fun openTransactionDetails(transaction: TransactionHistoryItem) {
        selectedTransaction = transaction
        dashboardTab = DashboardTab.Overview
        route = AppRoute.TransactionDetails
    }

    fun closeTransactionDetails() {
        selectedTransaction = null
        dashboardTab = DashboardTab.Overview
        route = AppRoute.Dashboard
    }

    fun selectDashboardTab(tab: DashboardTab) {
        dashboardTab = tab
        if (tab == DashboardTab.Assets) refreshEvmDashboard()
        if (tab == DashboardTab.Trade) {
            refreshTrade()
            refreshTradeChart()
        }
    }

    fun openDAppBrowser(title: String, url: String) {
        if (route != AppRoute.DAppBrowser) {
            dAppReturnRoute = route
            dAppReturnTab = dashboardTab
        }
        selectedCircleTarget = null
        selectedCircleError = null
        selectedDAppDisplayUrl = null
        if (isOctraCircleInput(url)) {
            openOctraCircleBrowser(title, url)
            return
        }
        selectedDAppTitle = title
        selectedDAppUrl = if (url.startsWith("http://") || url.startsWith("https://")) url else "https://$url"
        route = AppRoute.DAppBrowser
    }

    fun closeDAppBrowser() {
        circleResolveSerial++
        selectedCircleTarget = null
        selectedCircleError = null
        selectedDAppDisplayUrl = null
        val returningToDashboard = dAppReturnRoute == AppRoute.Dashboard
        dashboardTab = dAppReturnTab
        route = dAppReturnRoute
        if (returningToDashboard) {
            refreshIdentityProviders()
            refreshWalletIdentityNames()
            if (dashboardTab == DashboardTab.Overview) {
                refreshOverview()
            } else {
                refreshDashboard()
            }
        }
    }

    fun isOctraCircleInput(value: String): Boolean = parseCircleInputSpec(value) != null

    fun isCircleBrowserUrl(value: String): Boolean =
        value.startsWith("https://$CircleBrowserHost/", ignoreCase = true)

    fun circleDisplayUrl(): String =
        selectedCircleTarget?.uri ?: selectedCircleError?.let { "oct://error" } ?: selectedDAppDisplayUrl ?: selectedDAppUrl

    fun fetchCircleAssetForBrowser(circleId: String, path: String): CircleAsset {
        val cacheKey = circleCacheKey(circleId)
        val resourceMode = circleResourceModeCache.getOrPut(cacheKey) {
            rpcRepository.fetchCircleInfo(circleId).optString("resource_mode")
        }
        if (resourceMode == "sealed_read") {
            throw IllegalStateException("Sealed circle resources need a read key. Public circle resources can be opened now.")
        }
        return rpcRepository.fetchCircleAsset(circleId, path)
    }

    private suspend fun isCircleAddress(circleId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val cacheKey = circleCacheKey(circleId)
            val resourceMode = circleResourceModeCache.getOrPut(cacheKey) {
                rpcRepository.fetchCircleInfo(circleId).optString("resource_mode")
            }
            resourceMode.isNotBlank()
        }.getOrDefault(false)
    }

    private fun circleCacheKey(circleId: String): String =
        "${activeRpcNetwork?.id.orEmpty()}:$circleId"

    private fun openOctraCircleBrowser(title: String, rawInput: String) {
        selectedDAppTitle = title.takeIf { it.isNotBlank() && it != "dApp" } ?: "oct://"
        selectedDAppUrl = circleLoadingUrl(rawInput)
        selectedDAppDisplayUrl = circleDisplayUri(parseCircleInputSpec(rawInput), rawInput)
        selectedCircleTarget = null
        selectedCircleError = null
        route = AppRoute.DAppBrowser

        val serial = ++circleResolveSerial
        scope.launch {
            runCatching { resolveCircleOpenResult(rawInput) }
                .onSuccess { result ->
                    if (serial != circleResolveSerial) return@onSuccess
                    when (result) {
                        is OctraCircleOpenResult.Circle -> {
                            selectedCircleTarget = result.target
                            selectedCircleError = null
                            selectedDAppTitle = result.target.label
                            selectedDAppUrl = circleBrowserUrl(result.target.circleId, result.target.path)
                        }
                        is OctraCircleOpenResult.ExplorerAddress -> {
                            selectedCircleTarget = null
                            selectedCircleError = null
                            selectedDAppTitle = result.title
                            selectedDAppUrl = result.url
                            selectedDAppDisplayUrl = result.displayUrl
                        }
                    }
                }
                .onFailure { exception ->
                    if (serial != circleResolveSerial) return@onFailure
                    selectedCircleTarget = null
                    selectedCircleError = exception.message ?: "Unable to open oct:// resource."
                    selectedDAppDisplayUrl = circleDisplayUri(parseCircleInputSpec(rawInput), rawInput)
                    selectedDAppTitle = "oct://"
                    selectedDAppUrl = circleErrorUrl(rawInput)
                }
        }
    }

    private suspend fun resolveCircleOpenResult(rawInput: String): OctraCircleOpenResult {
        val spec = parseCircleInputSpec(rawInput) ?: throw IllegalStateException("Enter foo.oct or an Octra circle address.")
        val circleId = if (isOctraAddress(spec.host)) {
            spec.host
        } else {
            val activeWallet = wallet ?: throw IllegalStateException("Unlock wallet before resolving Octra Identity.")
            val provider = activeIdentityProvider
                ?: identityProviderRepository.activeProvider(currentIdentityNetwork())
                ?: throw IllegalStateException("Add an Octra Identity Provider in Settings first.")
            withContext(Dispatchers.IO) {
                rpcRepository.resolveIdentityName(provider, spec.host, activeWallet.address)
            } ?: throw IllegalStateException("${spec.host} could not be resolved.")
        }
        val displayUri = circleDisplayUri(spec, rawInput)
        if (!isCircleAddress(circleId)) {
            val url = explorerAddressUrl(circleId)
                ?: throw IllegalStateException("${spec.identityName ?: circleId} resolved to $circleId, but Explorer URL is not configured.")
            return OctraCircleOpenResult.ExplorerAddress(
                title = spec.identityName ?: shortAddress(circleId),
                url = url,
                displayUrl = displayUri,
            )
        }
        val label = spec.identityName?.let { "$it (oct://)" } ?: shortAddress(circleId)
        return OctraCircleOpenResult.Circle(
            OctraCircleTarget(
                input = rawInput,
                label = label,
                circleId = circleId,
                path = spec.path,
                uri = displayUri,
            ),
        )
    }

    fun walletDisplayName(address: String): String {
        val list = vault?.wallets.orEmpty()
        val index = list.indexOfFirst { it.address == address }
        val saved = list.getOrNull(index)?.name.orEmpty()
        return saved.ifBlank { "Wallet ${if (index >= 0) index + 1 else 1}" }
    }

    fun walletOverviewDisplayName(address: String): String =
        walletIdentityDomain(address) ?: walletDisplayName(address)

    fun walletIdentityDomain(address: String): String? {
        val clean = address.trim()
        val key = clean.lowercase()
        return walletIdentityNames[clean]?.takeIf { it.isNotBlank() }
            ?: walletIdentityNames.entries.firstOrNull { it.key.equals(clean, ignoreCase = true) }?.value?.takeIf { it.isNotBlank() }
            ?: identityNamesByAddress[key]?.takeIf { it.isNotBlank() }
    }

    fun identityNameForAddress(address: String): String? {
        val clean = address.trim()
        val key = clean.lowercase()
        return identityNamesByAddress[key]?.takeIf { it.isNotBlank() }
            ?: walletIdentityNames.entries.firstOrNull { it.key.equals(clean, ignoreCase = true) }?.value
    }

    fun refreshRecentActivityIdentityNames(transactions: List<TransactionHistoryItem> = recentTransactions) {
        val provider = activeIdentityProvider
            ?: identityProviderRepository.activeProvider(currentIdentityNetwork())
            ?: run {
                identityNamesByAddress = emptyMap()
                addressIdentityMisses = emptySet()
                addressIdentityCacheKey = null
                return
            }
        val cacheKey = "${provider.network}:${provider.id}"
        if (addressIdentityCacheKey != cacheKey) {
            identityNamesByAddress = emptyMap()
            addressIdentityMisses = emptySet()
            addressIdentityCacheKey = cacheKey
        }
        val addresses = transactions
            .flatMap { listOf(it.from, it.to) }
            .map { it.trim() }
            .filter(::isOctraAddress)
            .distinctBy { it.lowercase() }
        if (addresses.isEmpty()) return

        val cachedKeys = identityNamesByAddress.keys + addressIdentityMisses
        val pending = addresses.filterNot { it.lowercase() in cachedKeys }
        if (pending.isEmpty()) return

        val serial = ++addressIdentityLookupSerial
        scope.launch {
            val resolved = withContext(Dispatchers.IO) {
                pending.mapNotNull { address ->
                    val name = runCatching { rpcRepository.walletIdentityName(provider, address) }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                    name?.let { address.lowercase() to it }
                }.toMap()
            }
            if (serial == addressIdentityLookupSerial && addressIdentityCacheKey == cacheKey) {
                identityNamesByAddress = identityNamesByAddress + resolved
                addressIdentityMisses = addressIdentityMisses + pending
                    .map { it.lowercase() }
                    .filterNot { it in resolved.keys }
            }
        }
    }

    fun toggleDarkMode() {
        darkMode = !darkMode
        uiPrefs.edit().putBoolean(KeyDarkMode, darkMode).apply()
    }

    fun saveRpcUrl(url: String) {
        if (!rpcRepository.isValidRpcUrl(url)) {
            showError("Enter a valid HTTP or HTTPS RPC URL.")
            return
        }
        runBusy {
            rpcRepository.saveUrl(url)
            activeRpcNetwork = rpcRepository.activeNetwork()
            rpcNetworks = rpcRepository.rpcNetworks()
            rpcStatus = RpcStatus(RpcStatus.State.Checking, url.trim().trimEnd('/'), networkFor(url))
            rpcStatus = rpcRepository.checkStatus()
            refreshBalance()
            refreshAssets()
        }
    }

    fun addRpcNetwork(name: String, url: String, networkType: String, explorerUrl: String) {
        runBusy {
            val network = RpcNetwork(
                id = "rpc-${System.currentTimeMillis()}",
                name = name,
                url = url,
                networkType = networkType,
                explorerUrl = explorerUrl,
            )
            rpcNetworks = rpcRepository.saveNetwork(network)
            activeRpcNetwork = rpcRepository.activeNetwork()
            showMessage("RPC network added.")
            route = AppRoute.RpcManagement
        }
    }

    fun activateRpcNetwork(id: String) {
        runBusy {
            val network = rpcRepository.setActiveNetwork(id)
            activeRpcNetwork = network
            rpcNetworks = rpcRepository.rpcNetworks()
            rpcStatus = RpcStatus(RpcStatus.State.Checking, network.url, network.networkType)
            val status = rpcRepository.checkStatus(network.url)
            if (activeRpcNetwork?.id == network.id) rpcStatus = status
            refreshIdentityProviders()
            showMessage("${network.name} selected.")
            refreshWalletIdentityNames()
            refreshBalance()
            refreshAssets()
            refreshHistory()
            refreshBridgeAfterNetworkChangeIfNeeded(rpcManagementReturnRoute)
        }
    }

    fun deleteRpcNetwork(id: String) {
        runBusy {
            rpcNetworks = rpcRepository.deleteNetwork(id)
            val active = rpcRepository.activeNetwork()
            activeRpcNetwork = active
            rpcStatus = RpcStatus(RpcStatus.State.Checking, active.url, active.networkType)
            val status = rpcRepository.checkStatus(active.url)
            if (activeRpcNetwork?.id == active.id) rpcStatus = status
            refreshIdentityProviders()
            refreshWalletIdentityNames()
            refreshBalance()
            refreshAssets()
            refreshHistory()
            refreshBridgeAfterNetworkChangeIfNeeded(rpcManagementReturnRoute)
            showMessage("RPC network deleted.")
        }
    }

    fun refreshRpcStatus() {
        scope.launch {
            try {
                rpcNetworks = rpcRepository.rpcNetworks()
                val active = rpcRepository.activeNetwork()
                activeRpcNetwork = active
                rpcStatus = RpcStatus(RpcStatus.State.Checking, active.url, active.networkType)
                val status = rpcRepository.checkStatus(active.url)
                if (activeRpcNetwork?.id == active.id) rpcStatus = status
            } catch (exception: Exception) {
                val url = runCatching { currentRpcUrl }.getOrDefault("")
                rpcStatus = RpcStatus(
                    state = RpcStatus.State.Failed,
                    url = url,
                    network = networkFor(url),
                    message = exception.message ?: "RPC check failed.",
                )
            }
        }
    }

    fun refreshDashboard() {
        dApps = connectedDAppSessions()
        refreshRpcStatus()
        refreshIdentityProviders()
        refreshWalletIdentityNames()
        refreshBalance()
        refreshOctPrice()
        refreshAssets()
        refreshHistory()
    }

    fun refreshOverview() {
        val activeWallet = wallet ?: return
        if (overviewRefreshLoading) return
        overviewRefreshLoading = true
        refreshWalletIdentityNames()
        val balanceJob = refreshBalance(activeWallet)
        val historyJob = refreshHistory(activeWallet, reset = true)
        scope.launch {
            try {
                balanceJob.join()
                historyJob.join()
            } finally {
                overviewRefreshLoading = false
            }
        }
    }

    private fun refreshIdentityProviders() {
        val network = currentIdentityNetwork()
        identityProviders = identityProviderRepository.providers(network)
        activeIdentityProvider = identityProviderRepository.activeProvider(network)
    }

    private fun refreshWalletIdentityNames() {
        val currentVault = vault ?: run {
            walletIdentityNames = emptyMap()
            return
        }
        val provider = activeIdentityProvider
            ?: identityProviderRepository.activeProvider(currentIdentityNetwork())
            ?: run {
                walletIdentityNames = emptyMap()
                return
            }
        val cacheKey = "${provider.network}:${provider.id}"
        if (addressIdentityCacheKey != cacheKey) {
            identityNamesByAddress = emptyMap()
            addressIdentityMisses = emptySet()
            addressIdentityCacheKey = cacheKey
        }
        val wallets = currentVault.wallets
        if (wallets.isEmpty()) {
            walletIdentityNames = emptyMap()
            return
        }
        val serial = ++identityLookupSerial
        val addresses = wallets.map { it.address }
        scope.launch {
            val names = withContext(Dispatchers.IO) {
                addresses.mapNotNull { address ->
                    val name = runCatching { rpcRepository.walletIdentityName(provider, address) }
                        .getOrNull()
                        ?.takeIf { it.isNotBlank() }
                    name?.let { address to it }
                }.toMap()
            }
            if (serial == identityLookupSerial && activeIdentityProvider?.id == provider.id) {
                walletIdentityNames = names
                val normalized = names.mapKeys { it.key.lowercase() }
                identityNamesByAddress = identityNamesByAddress + normalized
                addressIdentityMisses = addressIdentityMisses - normalized.keys
            }
        }
    }

    private fun currentIdentityNetwork(): String =
        activeRpcNetwork?.networkType ?: runCatching { rpcRepository.activeNetwork().networkType }.getOrDefault("devnet")

    fun revokeDApp(session: DAppSession) {
        dAppSessionRepository.revoke(session.url, session.walletAddress)
        dApps = connectedDAppSessions()
    }

    fun handleDAppBridgeRequest(
        originRaw: String,
        payload: String,
        onSuccess: (String) -> Unit,
        onError: (Int, String) -> Unit,
    ) {
        scope.launch {
            try {
                val activeWallet = wallet ?: throw IllegalStateException("Wallet is locked.")
                val request = JSONObject(payload)
                val method = request.optString("method")
                val origin = canonicalizeOrigin(originRaw.ifBlank { selectedDAppUrl })
                when (method) {
                    "octra_requestAccounts" -> {
                        val permissions = requestedDAppPermissions(request.opt("params"))
                        queueDAppApproval(
                            origin = origin,
                            title = "Connect dApp",
                            message = "${hostLabel(origin)} wants to connect to ${shortAddress(activeWallet.address)}.",
                            details = listOf(
                                DAppRequestDetail("Wallet", activeWallet.address),
                                DAppRequestDetail("Network", activeRpcNetwork?.name ?: currentRpcUrl),
                                DAppRequestDetail("Permissions", permissions.joinToString(", ")),
                            ),
                            approveLabel = "Connect",
                            onSuccess = onSuccess,
                            onError = onError,
                        ) {
                            authorizeDApp(origin, activeWallet.address, permissions)
                            JSONArray().put(activeWallet.address).toString()
                        }
                    }
                    "octra_sendContractTransaction" -> {
                        val session = dAppSession(origin, activeWallet.address)
                            ?: throw SecurityException("Connect poctra before sending contract calls.")
                        if (!session.permissions.contains("contract_calls")) {
                            throw SecurityException("The dApp is missing contract call permission.")
                        }
                        val call = contractCallRequest(request.opt("params"))
                        val contract = call.getString("address")
                        val callMethod = call.getString("method")
                        val amountOu = call.optString("amount", "0").ifBlank { "0" }
                        val params = call.optJSONArray("params") ?: JSONArray()
                        val providedFee = call.optString("fee").takeIf { it.isNotBlank() }
                        queueDAppApproval(
                            origin = origin,
                            title = "Approve contract call",
                            message = "${hostLabel(origin)} wants to call $callMethod on ${shortAddress(contract)} with $amountOu ou.",
                            details = listOf(
                                DAppRequestDetail("From", activeWallet.address),
                                DAppRequestDetail("Contract", contract),
                                DAppRequestDetail("Method", callMethod),
                                DAppRequestDetail("Amount", "$amountOu ou (${formatOuAmount(amountOu)} OCT)"),
                                DAppRequestDetail("Fee", providedFee?.let { "$it ou" } ?: "Auto"),
                                DAppRequestDetail("Params", if (params.length() == 0) "[]" else params.toString(2)),
                            ),
                            approveLabel = "Approve",
                            onSuccess = onSuccess,
                            onError = onError,
                        ) {
                            val amountOct = amountOu.toDoubleOrNull()?.div(MicroUnits.toDouble()) ?: 0.0
                            beginSubmittedTransaction(contract, amountOct, showDialog = false)
                            val feeOu = providedFee?.toLongOrNull()
                                ?: withContext(Dispatchers.IO) { rpcRepository.fetchRecommendedFee("call") }
                            val result = rpcRepository.sendContractCall(
                                wallet = activeWallet,
                                contractAddress = contract,
                                method = callMethod,
                                params = params,
                                amountOu = amountOu,
                                feeOu = feeOu,
                            )
                            if (!result.success) {
                                updateSubmittedTransactionResult(
                                    hash = result.hash,
                                    recipientAddress = contract,
                                    amount = amountOct,
                                    status = TransactionFinality.Rejected,
                                    reason = result.error ?: "Contract call rejected.",
                                )
                                throw IllegalStateException(result.error ?: "Contract call rejected.")
                            }
                            val hash = result.hash?.takeIf { it.isNotBlank() }
                            updateSubmittedTransactionResult(
                                hash = hash,
                                recipientAddress = contract,
                                amount = amountOct,
                                status = TransactionFinality.Accepted,
                            )
                            if (hash != null) {
                                addPendingTransaction(
                                    hash = hash,
                                    from = activeWallet.address,
                                    recipient = contract,
                                    amount = amountOct,
                                    message = callMethod,
                                    opType = callMethod,
                                )
                                refreshBalance()
                                pollSubmittedTransaction(hash)
                            } else {
                                refreshDashboard()
                            }
                            JSONObject()
                                .put("hash", hash ?: "")
                                .put("accepted", true)
                                .put("status", "pending")
                                .put("explorerUrl", explorerTxUrl(hash))
                                .toString()
                        }
                    }
                    else -> onSuccess(resolveDAppReadRequest(origin, activeWallet, request))
                }
            } catch (exception: Exception) {
                val (code, message) = dAppError(exception)
                onError(code, message)
            }
        }
    }

    fun approvePendingDAppRequest() {
        val action = pendingDAppAction ?: return
        val onSuccess = pendingDAppSuccess
        val onError = pendingDAppError
        clearPendingDAppRequest()
        scope.launch {
            try {
                onSuccess?.invoke(action())
            } catch (exception: Exception) {
                rejectSubmittingTransaction(exception.message ?: "poctra request failed.")
                val (code, message) = dAppError(exception)
                onError?.invoke(code, message)
            }
        }
    }

    fun rejectPendingDAppRequest() {
        val onError = pendingDAppError
        clearPendingDAppRequest()
        onError?.invoke(4001, "User rejected the poctra request.")
    }

    fun switchWallet(address: String) {
        val current = vault ?: return
        val next = current.withActive(address)
        vault = next
        wallet = next.activeWallet
        secureStore.setActiveAddress(address)
        refreshDashboard()
    }

    fun renameWallet(address: String, name: String) {
        val current = vault ?: return
        try {
            val next = current.renameWallet(address, name)
            persistWalletMetadata(next, "Wallet renamed.")
        } catch (exception: Exception) {
            showError(exception.message ?: "Unable to rename wallet.")
        }
    }

    fun removeWallet(address: String) {
        val current = vault ?: return
        try {
            val next = current.removeWallet(address)
            persistVault(next, "Wallet removed.")
        } catch (exception: Exception) {
            showError(exception.message ?: "Unable to remove wallet.")
        }
    }

    fun reorderWallet(fromIndex: Int, toIndex: Int) {
        val current = vault ?: return
        try {
            val next = current.reorderWallet(fromIndex, toIndex)
            vault = next
            wallet = next.activeWallet
        } catch (exception: Exception) {
            showError(exception.message ?: "Unable to reorder wallets.")
        }
    }

    fun saveWalletOrder() {
        val current = vault ?: return
        persistWalletMetadata(current, "Wallet order updated.")
    }

    fun downloadBackup() {
        runBusy {
            val backup = withContext(Dispatchers.IO) { secureStore.exportEncryptedBackup() }
            val location = withContext(Dispatchers.IO) { backupExporter.save(backup) }
            showMessage("Backup saved to $location")
        }
    }

    fun lookupAsset(contractAddress: String) {
        val activeWallet = wallet ?: return
        if (!contractAddress.startsWith("oct") || contractAddress.length != 47) {
            showError("Enter a valid Octra contract address (oct..., 47 chars).")
            return
        }
        scope.launch {
            assetLookupLoading = true
            clearError()
            try {
                assetPreview = rpcRepository.lookupOctraToken(contractAddress, activeWallet.address)
                if (assetPreview == null) showError("Token not found or not a valid OCS01 contract.")
            } catch (exception: Exception) {
                showError(exception.message ?: "Token lookup failed.")
            } finally {
                assetLookupLoading = false
            }
        }
    }

    fun addPreviewAsset() {
        val token = assetPreview ?: return
        assetRepository.saveTokenAddress(rpcRepository.currentNetwork(), token.address)
        assetPreview = null
        dashboardTab = DashboardTab.Assets
        route = AppRoute.Dashboard
        refreshAssets()
        showMessage("${token.symbol} added to assets")
    }

    fun removeAsset(token: OctraToken) {
        assetRepository.removeTokenAddress(rpcRepository.currentNetwork(), token.address)
        refreshAssets()
    }

    fun sendOct(recipientAddress: String, amountText: String, message: String?, feeOuText: String) {
        val activeWallet = wallet ?: return
        val amount = amountText.replace(",", ".").trim().toDoubleOrNull()
        val feeOu = feeOuText.filter(Char::isDigit).toLongOrNull() ?: recommendedFeeOu
        if (amount == null || amount <= 0.0) {
            showError("Enter a valid OCT amount.")
            return
        }
        if (feeOu <= 0L) {
            showError("Enter a valid network fee.")
            return
        }
        runBusy {
            val recipient = resolveOctraRecipient(recipientAddress, activeWallet)
            beginSubmittedTransaction(recipient, amount)
            val result = withContext(Dispatchers.IO) {
                rpcRepository.sendOct(
                    wallet = activeWallet,
                    recipientAddress = recipient,
                    amount = amount,
                    message = message,
                    feeOu = feeOu,
                )
            }
            route = AppRoute.Dashboard
            if (!result.success) {
                updateSubmittedTransactionResult(
                    hash = result.hash,
                    recipientAddress = recipient,
                    amount = amount,
                    status = TransactionFinality.Rejected,
                    reason = result.error ?: "Transaction rejected.",
                )
                refreshDashboard()
                return@runBusy
            }

            val hash = result.hash
            updateSubmittedTransactionResult(
                hash = hash,
                recipientAddress = recipient,
                amount = amount,
                status = TransactionFinality.Accepted,
            )
            if (hash != null) {
                addPendingTransaction(hash, activeWallet.address, recipient, amount, message)
                refreshBalance()
                refreshAssets()
                pollSubmittedTransaction(hash)
            } else {
                refreshDashboard()
            }
        }
    }

    fun sendOctraToken(token: OctraToken, recipientAddress: String, amountText: String, feeOuText: String) {
        val activeWallet = wallet ?: return
        val rawAmount = parseTokenAmount(amountText, token.decimals)
        val feeOu = feeOuText.filter(Char::isDigit).toLongOrNull() ?: recommendedFeeOu
        if (!isOctraRecipientInput(recipientAddress.trim())) {
            showError("Enter foo.oct or a valid Octra recipient address.")
            return
        }
        if (rawAmount == null || rawAmount <= BigInteger.ZERO) {
            showError("Enter a valid ${token.symbol} amount.")
            return
        }
        val balanceRaw = token.balanceRaw.toBigIntegerOrNull() ?: BigInteger.ZERO
        if (rawAmount > balanceRaw) {
            showError("Amount exceeds available ${token.symbol} balance.")
            return
        }
        if (feeOu <= 0L) {
            showError("Enter a valid network fee.")
            return
        }
        runBusy {
            val recipient = resolveOctraRecipient(recipientAddress, activeWallet)
            if (recipient.equals(activeWallet.address, ignoreCase = true)) {
                throw IllegalArgumentException("Cannot send asset to the active wallet address.")
            }
            val amount = amountText.replace(",", ".").trim().toDoubleOrNull() ?: 0.0
            beginSubmittedTransaction(recipient, amount)
            val result = withContext(Dispatchers.IO) {
                rpcRepository.sendOctraToken(
                    wallet = activeWallet,
                    token = token,
                    recipientAddress = recipient,
                    rawAmount = rawAmount.toString(),
                    feeOu = feeOu,
                )
            }
            route = AppRoute.Dashboard
            dashboardTab = DashboardTab.Assets
            if (!result.success) {
                updateSubmittedTransactionResult(
                    hash = result.hash,
                    recipientAddress = recipient,
                    amount = amount,
                    status = TransactionFinality.Rejected,
                    reason = result.error ?: "${token.symbol} transfer rejected.",
                )
                refreshAssets()
                return@runBusy
            }
            val hash = result.hash
            updateSubmittedTransactionResult(
                hash = hash,
                recipientAddress = recipient,
                amount = amount,
                status = TransactionFinality.Accepted,
            )
            selectedOctraToken = null
            if (hash != null) {
                addPendingTransaction(
                    hash = hash,
                    from = activeWallet.address,
                    recipient = token.address,
                    amount = 0.0,
                    message = "transfer",
                    opType = "transfer",
                )
                refreshBalance()
                refreshAssets()
                pollSubmittedTransaction(hash)
            } else {
                refreshDashboard()
            }
        }
    }

    suspend fun previewOctraIdentityRecipient(input: String): String? {
        val activeWallet = wallet ?: return null
        val clean = input.trim()
        if (!isIdentityNameInput(clean.lowercase())) return null
        return resolveOctraRecipient(clean, activeWallet)
    }

    fun maxOctraTokenAmount(token: OctraToken): String = token.formattedBalance

    fun dismissSubmittedTransaction() {
        submittedTransaction = null
    }

    fun refreshRecommendedFee(opType: String = "standard") {
        feeLoading = true
        scope.launch {
            try {
                recommendedFeeOu = withContext(Dispatchers.IO) {
                    rpcRepository.fetchRecommendedFee(opType)
                }
            } catch (_: Exception) {
                recommendedFeeOu = 1000L
            } finally {
                feeLoading = false
            }
        }
    }

    fun maxSendAmount(feeOu: Long): String {
        val maximum = (availableBalance - (feeOu.coerceAtLeast(0L) / MicroUnits.toDouble())).coerceAtLeast(0.0)
        return "%,.6f".format(maximum).trimEnd('0').trimEnd('.').ifBlank { "0" }
    }

    fun loadMoreHistory() {
        if (historyLoading || !historyCanLoadMore || historyLimit >= MaxHistoryLimit) return
        historyLimit = (historyLimit + HistoryPageSize).coerceAtMost(MaxHistoryLimit)
        refreshHistory(reset = false)
    }

    fun selectEvmNetwork(id: String) {
        runBusy {
            val network = evmRepository.setActiveNetwork(id)
            activeEvmNetwork = network
            evmNetworks = evmRepository.networks()
            showMessage("${network.name} selected.")
            refreshEvmDashboard()
            if (evmNetworkReturnTab == DashboardTab.Trade) {
                clearTradeQuote()
                refreshTrade()
                refreshTradeChart()
            }
            refreshBridgeAfterNetworkChangeIfNeeded(evmNetworkReturnRoute)
        }
    }

    fun saveEvmRpcUrl(url: String) {
        val active = activeEvmNetwork ?: evmRepository.activeNetwork()
        runBusy {
            evmNetworks = evmRepository.saveNetworkRpc(active.id, url)
            activeEvmNetwork = evmRepository.setActiveNetwork(active.id)
            showMessage("EVM RPC updated.")
            refreshEvmDashboard()
            refreshBridgeAfterNetworkChangeIfNeeded(evmNetworkReturnRoute)
        }
    }

    fun addCustomEvmNetwork(
        name: String,
        chainIdInput: String,
        symbol: String,
        rpcUrl: String,
        explorerUrl: String,
    ) = saveEvmNetworkDetails(name, chainIdInput, symbol, rpcUrl, explorerUrl)

    fun saveEvmNetworkDetails(
        name: String,
        chainIdInput: String,
        symbol: String,
        rpcUrl: String,
        explorerUrl: String,
    ) {
        val editing = evmEditingNetwork
        val cleanName = name.trim()
        val chainId = chainIdInput.trim().toLongOrNull()
        val cleanSymbol = symbol.trim().uppercase().take(12)
        val cleanRpc = when {
            editing != null && (rpcUrl.trim().isBlank() || isMaskedEvmRpcUrl(rpcUrl)) -> editing.rpcUrl
            else -> rpcUrl.trim().trimEnd('/')
        }
        val cleanExplorer = explorerUrl.trim().trimEnd('/')
        when {
            cleanName.isBlank() -> {
                showError("Network name is required.")
                return
            }
            chainId == null || chainId <= 0L -> {
                showError("Enter a valid chain ID.")
                return
            }
            cleanSymbol.isBlank() -> {
                showError("Network symbol is required.")
                return
            }
            cleanRpc.isBlank() -> {
                showError("RPC endpoint is required.")
                return
            }
            cleanExplorer.isBlank() -> {
                showError("Explorer URL is required.")
                return
            }
        }

        val cleanChainId = chainId ?: return
        val slug = cleanName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "network" }
        val lowerName = cleanName.lowercase()
        val network = EvmNetwork(
            id = editing?.id ?: "custom-$cleanChainId-$slug",
            name = cleanName,
            chainId = cleanChainId,
            rpcUrl = cleanRpc,
            symbol = cleanSymbol,
            explorerUrl = cleanExplorer,
            isTestnet = lowerName.contains("test") ||
                lowerName.contains("dev") ||
                lowerName.contains("sepolia") ||
                lowerName.contains("goerli"),
        )
        runBusy {
            evmNetworks = evmRepository.saveNetwork(network)
            activeEvmNetwork = evmRepository.setActiveNetwork(network.id)
            evmEditingNetwork = null
            showMessage(if (editing == null) "${network.name} added." else "${network.name} updated.")
            refreshEvmDashboard()
            refreshBridgeAfterNetworkChangeIfNeeded(evmNetworkReturnRoute)
            route = AppRoute.EvmNetwork
        }
    }

    fun displayEvmRpcUrl(url: String): String {
        val clean = url.trim()
        val infuraMarker = ".infura.io/v3/"
        val markerIndex = clean.indexOf(infuraMarker, ignoreCase = true)
        if (markerIndex < 0) return clean
        val keyStart = markerIndex + infuraMarker.length
        if (keyStart >= clean.length) return clean
        val nextSlash = clean.indexOf('/', startIndex = keyStart).let { if (it < 0) clean.length else it }
        return clean.replaceRange(keyStart, nextSlash, HiddenEvmRpcKey)
    }

    private fun isMaskedEvmRpcUrl(url: String): Boolean =
        url.contains(HiddenEvmRpcKey, ignoreCase = true)

    fun refreshEvmDashboard() {
        refreshEvmAccount()
        refreshEvmAssets()
        refreshEvmHistory()
    }

    fun refreshEvmAccount() {
        val activeWallet = wallet ?: return
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        evmLoading = true
        scope.launch {
            try {
                evmAccount = withContext(Dispatchers.IO) { evmRepository.fetchAccount(activeWallet, network) }
            } finally {
                evmLoading = false
            }
        }
    }

    fun refreshEvmAssets() {
        val activeWallet = wallet ?: return
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        evmAssetsLoading = true
        scope.launch {
            try {
                val account = evmAccount ?: withContext(Dispatchers.IO) { evmRepository.fetchAccount(activeWallet, network) }.also {
                    evmAccount = it
                }
                evmTokens = withContext(Dispatchers.IO) { evmRepository.refreshTokens(account.address, network) }
                evmNfts = withContext(Dispatchers.IO) { evmRepository.refreshNfts(account.address, network) }
            } finally {
                evmAssetsLoading = false
            }
        }
    }

    fun refreshEvmHistory() {
        val activeWallet = wallet ?: return
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        evmHistoryLoading = true
        scope.launch {
            try {
                val account = evmAccount ?: withContext(Dispatchers.IO) { evmRepository.fetchAccount(activeWallet, network) }.also {
                    evmAccount = it
                }
                evmTransactions = withContext(Dispatchers.IO) {
                    evmRepository.fetchTransactions(
                        address = account.address,
                        network = network,
                        tokenAddresses = evmRepository.savedTokens(network.chainId).map { it.address },
                    )
                }
            } finally {
                evmHistoryLoading = false
            }
        }
    }

    fun lookupEvmToken(address: String) {
        val activeWallet = wallet ?: return
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        evmLookupLoading = true
        scope.launch {
            clearError()
            try {
                val account = evmAccount ?: withContext(Dispatchers.IO) { evmRepository.fetchAccount(activeWallet, network) }.also {
                    evmAccount = it
                }
                evmTokenPreview = withContext(Dispatchers.IO) { evmRepository.lookupErc20Token(address, account.address, network) }
                if (evmTokenPreview == null) showError("ERC20 token not found.")
            } catch (exception: Exception) {
                showError(exception.message ?: "ERC20 lookup failed.")
            } finally {
                evmLookupLoading = false
            }
        }
    }

    fun addEvmTokenPreview() {
        val token = evmTokenPreview ?: return
        evmTokens = evmRepository.saveToken(token)
        evmTokenPreview = null
        showMessage("${token.symbol} added.")
        route = AppRoute.Evm
        refreshEvmAssets()
    }

    fun clearEvmAssetPreviews() {
        evmTokenPreview = null
        evmNftPreview = null
    }

    fun removeEvmToken(token: EvmToken) {
        evmTokens = evmRepository.removeToken(token)
        showMessage("${token.symbol} removed.")
        refreshEvmHistory()
    }

    fun lookupEvmNft(contractAddress: String, tokenId: String) {
        val activeWallet = wallet ?: return
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        evmLookupLoading = true
        scope.launch {
            clearError()
            try {
                val account = evmAccount ?: withContext(Dispatchers.IO) { evmRepository.fetchAccount(activeWallet, network) }.also {
                    evmAccount = it
                }
                evmNftPreview = withContext(Dispatchers.IO) { evmRepository.lookupNft(contractAddress, tokenId, account.address, network) }
                if (evmNftPreview == null) showError("NFT not found.")
            } catch (exception: Exception) {
                showError(exception.message ?: "NFT lookup failed.")
            } finally {
                evmLookupLoading = false
            }
        }
    }

    fun addEvmNftPreview() {
        val nft = evmNftPreview ?: return
        evmNfts = evmRepository.saveNft(nft)
        evmNftPreview = null
        showMessage("${nft.symbol} #${nft.tokenId} added.")
        route = AppRoute.Evm
        refreshEvmAssets()
    }

    fun removeEvmNft(nft: EvmNft) {
        evmNfts = evmRepository.removeNft(nft)
        showMessage("${nft.symbol} #${nft.tokenId} removed.")
    }

    fun prepareEvmSendDialog(recipientAddress: String, amountText: String) {
        if (evmSending) return
        val cleanRecipient = recipientAddress.trim()
        val cleanAmount = if (evmSelectedSendNft != null) "1" else amountText.replace(",", ".").trim()
        resetEvmSendDialogProgress()
        evmSendDialogRecipient = cleanRecipient
        evmSendDialogAmount = cleanAmount
        evmSendDialogStarted = false
        evmSendDialogVisible = true
        estimatePreparedEvmSendGas()
    }

    fun dismissEvmSendDialog() {
        if (evmSending) return
        evmSendDialogVisible = false
        evmSendDialogStarted = false
        if (submittedTransaction?.showDialog == false) submittedTransaction = null
        resetEvmSendDialogProgress()
        evmSendDialogRecipient = ""
        evmSendDialogAmount = ""
    }

    fun submitPreparedEvmSend(evmGasPriceGwei: String? = null) {
        val recipient = evmSendDialogRecipient
        val amount = evmSendDialogAmount
        evmSendDialogStarted = true
        when {
            evmSelectedSendNft != null -> sendEvmNft(evmSelectedSendNft ?: return, recipient, evmGasPriceGwei)
            evmSelectedSendToken != null -> sendEvmToken(evmSelectedSendToken ?: return, recipient, amount, evmGasPriceGwei)
            else -> sendEvmNative(recipient, amount, evmGasPriceGwei)
        }
    }

    fun speedUpEvmSendTransaction() {
        val activeWallet = wallet ?: return showError("Unlock wallet before speeding up this transaction.")
        val pending = evmSendPendingTransaction ?: return showError("No pending EVM transaction to speed up.")
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        if (!evmSendCanSpeedUp || evmSendSpeedUpBusy) return
        scope.launch {
            evmSendSpeedUpBusy = true
            try {
                val result = withContext(Dispatchers.IO) {
                    evmRepository.speedUpTransaction(activeWallet, pending, network)
                }
                if (!result.success) throw IllegalStateException(result.error ?: "Speed up transaction failed.")
                val hash = result.hash ?: throw IllegalStateException("Speed up did not return a transaction hash.")
                evmSendPendingTransaction = result
                updateSubmittedTransactionResult(
                    hash = hash,
                    recipientAddress = evmSendDialogRecipient,
                    amount = evmSendDialogAmount.toDoubleOrNull() ?: 1.0,
                    status = TransactionFinality.Accepted,
                    showDialog = false,
                )
                pollEvmTransaction(hash, network)
                showMessage("EVM transaction speed up submitted.")
            } catch (exception: Exception) {
                showError(exception.message ?: "Speed up transaction failed.")
            } finally {
                evmSendSpeedUpBusy = false
            }
        }
    }

    fun openEvmSendExplorerTx() {
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        val hash = submittedTransaction?.hash ?: evmSendPendingTransaction?.hash
        if (hash.isNullOrBlank()) return showError("Transaction hash is not ready.")
        val url = evmRepository.explorerTxUrl(network, hash)
        if (url == null) showError("Explorer URL is not configured.")
        else openDAppBrowser("EVM Transaction", url)
    }

    private fun estimatePreparedEvmSendGas() {
        val activeWallet = wallet ?: return
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        val recipient = evmSendDialogRecipient
        val amount = evmSendDialogAmount
        evmSendGasLoading = true
        evmSendGasEstimate = null
        scope.launch {
            try {
                evmSendGasEstimate = withContext(Dispatchers.IO) {
                    when {
                        evmSelectedSendNft != null -> evmRepository.estimateNftTransferGas(activeWallet, evmSelectedSendNft ?: return@withContext null, recipient, network)
                        evmSelectedSendToken != null -> evmRepository.estimateErc20TransferGas(activeWallet, evmSelectedSendToken ?: return@withContext null, recipient, amount, network)
                        else -> evmRepository.estimateNativeTransferGas(activeWallet, recipient, amount, network)
                    }
                }
            } catch (exception: Exception) {
                evmSendGasEstimate = null
                showError(exception.message ?: "EVM gas estimate failed.")
            } finally {
                evmSendGasLoading = false
            }
        }
    }

    private fun resetEvmSendDialogProgress() {
        evmSendPendingTransaction = null
        evmSendSpeedUpBusy = false
        evmSendGasEstimate = null
        evmSendGasLoading = false
    }

    fun sendEvmNative(recipientAddress: String, amountText: String, evmGasPriceGwei: String? = null) {
        val activeWallet = wallet ?: return
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        val amount = amountText.replace(",", ".").trim().toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            showError("Enter a valid ${network.symbol} amount.")
            return
        }
        val selectedEvmGasPriceWei = evmGasPriceGwei.toGasPriceWeiOrNull()
        scope.launch {
            evmSending = true
            clearError()
            evmSendDialogStarted = true
            evmSendDialogVisible = true
            evmSendPendingTransaction = null
            beginSubmittedTransaction(recipientAddress, amount, showDialog = false)
            try {
                val result = withContext(Dispatchers.IO) {
                    evmRepository.sendNative(activeWallet, recipientAddress, amountText, network, selectedEvmGasPriceWei)
                }
                route = AppRoute.Evm
                dashboardTab = DashboardTab.Assets
                if (!result.success) {
                    updateSubmittedTransactionResult(
                        hash = result.hash,
                        recipientAddress = recipientAddress,
                        amount = amount,
                        status = TransactionFinality.Rejected,
                        reason = result.error ?: "EVM transaction rejected.",
                        showDialog = false,
                    )
                    refreshEvmDashboard()
                    return@launch
                }
                val hash = result.hash
                evmSendPendingTransaction = result
                updateSubmittedTransactionResult(
                    hash = hash,
                    recipientAddress = recipientAddress,
                    amount = amount,
                    status = TransactionFinality.Accepted,
                    showDialog = false,
                )
                refreshEvmDashboard()
                if (hash != null) pollEvmTransaction(hash, network)
            } catch (exception: Exception) {
                rejectSubmittingTransaction(exception.message ?: "EVM transaction failed.")
                showError(exception.message ?: "EVM transaction failed.")
            } finally {
                evmSending = false
            }
        }
    }

    fun evmMaxAmount(token: EvmToken?): String {
        if (token != null) return token.balance.cleanAmount()
        val balance = evmAccount?.balance?.toBigDecimalOrNull() ?: return "0"
        val gas = evmGasCostAmount(token)
        return (balance - gas).coerceAtLeast(BigDecimal.ZERO).formatAmount()
    }

    fun evmGasInfo(token: EvmToken?, nft: EvmNft? = null): String {
        val network = activeEvmNetwork ?: return "Gas unavailable"
        val gasPrice = evmAccount?.gasPriceGwei?.toBigDecimalOrNull() ?: return "Gas unavailable"
        val limit = when {
            nft != null -> EvmNftGasLimit
            token == null -> EvmNativeGasLimit
            else -> EvmTokenGasLimit
        }
        val gasCost = gasPrice
            .multiply(BigDecimal.valueOf(limit))
            .divide(BigDecimal("1000000000"), 18, RoundingMode.HALF_UP)
        return "~ ${gasCost.formatAmount()} ${network.symbol} ($limit gas @ ${gasPrice.stripTrailingZeros().toPlainString()} Gwei)"
    }

    fun evmNativeAssetName(): String = nativeAssetName(activeEvmNetwork?.symbol ?: "ETH")

    fun evmNetworkLabel(): String {
        val network = activeEvmNetwork ?: return "EVM"
        return "${network.name} / Chain ID ${network.chainId}"
    }

    suspend fun previewEvmRecipient(input: String): String? {
        val clean = input.trim()
        if (!clean.matches(Regex("^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$"))) return null
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        return withContext(Dispatchers.IO) {
            evmRepository.resolveRecipientAddress(clean, network)
        }
    }

    private fun evmGasCostAmount(token: EvmToken?): BigDecimal {
        val gasPrice = evmAccount?.gasPriceGwei?.toBigDecimalOrNull() ?: return BigDecimal.ZERO
        val limit = if (token == null) EvmNativeGasLimit else EvmTokenGasLimit
        return gasPrice
            .multiply(BigDecimal.valueOf(limit))
            .divide(BigDecimal("1000000000"), 18, RoundingMode.HALF_UP)
    }

    fun sendEvmToken(token: EvmToken, recipientAddress: String, amountText: String, evmGasPriceGwei: String? = null) {
        val activeWallet = wallet ?: return
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        val amount = amountText.replace(",", ".").trim().toDoubleOrNull()
        if (amount == null || amount <= 0.0) {
            showError("Enter a valid ${token.symbol} amount.")
            return
        }
        val selectedEvmGasPriceWei = evmGasPriceGwei.toGasPriceWeiOrNull()
        scope.launch {
            evmSending = true
            clearError()
            evmSendDialogStarted = true
            evmSendDialogVisible = true
            evmSendPendingTransaction = null
            beginSubmittedTransaction(recipientAddress, amount, showDialog = false)
            try {
                val result = withContext(Dispatchers.IO) {
                    evmRepository.sendErc20(activeWallet, token, recipientAddress, amountText, network, selectedEvmGasPriceWei)
                }
                route = AppRoute.Evm
                dashboardTab = DashboardTab.Assets
                if (!result.success) {
                    updateSubmittedTransactionResult(
                        hash = result.hash,
                        recipientAddress = recipientAddress,
                        amount = amount,
                        status = TransactionFinality.Rejected,
                        reason = result.error ?: "ERC20 transfer rejected.",
                        showDialog = false,
                    )
                    refreshEvmDashboard()
                    return@launch
                }
                val hash = result.hash
                evmSendPendingTransaction = result
                updateSubmittedTransactionResult(
                    hash = hash,
                    recipientAddress = recipientAddress,
                    amount = amount,
                    status = TransactionFinality.Accepted,
                    showDialog = false,
                )
                refreshEvmDashboard()
                if (hash != null) pollEvmTransaction(hash, network)
            } catch (exception: Exception) {
                rejectSubmittingTransaction(exception.message ?: "ERC20 transfer failed.")
                showError(exception.message ?: "ERC20 transfer failed.")
            } finally {
                evmSending = false
            }
        }
    }

    fun sendEvmNft(nft: EvmNft, recipientAddress: String, evmGasPriceGwei: String? = null) {
        val activeWallet = wallet ?: return
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        val selectedEvmGasPriceWei = evmGasPriceGwei.toGasPriceWeiOrNull()
        scope.launch {
            evmSending = true
            clearError()
            evmSendDialogStarted = true
            evmSendDialogVisible = true
            evmSendPendingTransaction = null
            beginSubmittedTransaction(recipientAddress, 1.0, showDialog = false)
            try {
                val result = withContext(Dispatchers.IO) {
                    evmRepository.sendNft(activeWallet, nft, recipientAddress, network, selectedEvmGasPriceWei)
                }
                route = AppRoute.Evm
                dashboardTab = DashboardTab.Assets
                if (!result.success) {
                    updateSubmittedTransactionResult(
                        hash = result.hash,
                        recipientAddress = recipientAddress,
                        amount = 1.0,
                        status = TransactionFinality.Rejected,
                        reason = result.error ?: "NFT transfer rejected.",
                        showDialog = false,
                    )
                    refreshEvmDashboard()
                    return@launch
                }
                val hash = result.hash
                evmSendPendingTransaction = result
                updateSubmittedTransactionResult(
                    hash = hash,
                    recipientAddress = recipientAddress,
                    amount = 1.0,
                    status = TransactionFinality.Accepted,
                    showDialog = false,
                )
                refreshEvmDashboard()
                if (hash != null) pollEvmTransaction(hash, network)
            } catch (exception: Exception) {
                rejectSubmittingTransaction(exception.message ?: "NFT transfer failed.")
                showError(exception.message ?: "NFT transfer failed.")
            } finally {
                evmSending = false
            }
        }
    }

    fun openEvmTransaction(transaction: EvmTransaction) {
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        val url = evmRepository.explorerTxUrl(network, transaction.hash)
        if (url == null) showError("Explorer URL is not configured.")
        else openDAppBrowser("EVM Transaction", url)
    }

    fun openEvmExplorerAddress() {
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        val address = evmAccount?.address ?: return showError("EVM address is not ready.")
        val url = evmRepository.explorerAddressUrl(network, address)
        if (url == null) showError("Explorer URL is not configured.")
        else openDAppBrowser("EVM Address", url)
    }

    fun dispose() {
        scope.cancel()
    }

    fun handleBack(): Boolean {
        return when (route) {
            AppRoute.DAppBrowser -> {
                closeDAppBrowser()
                true
            }
            AppRoute.About -> {
                closeAbout()
                true
            }
            AppRoute.RpcManagement -> {
                closeRpcManagement()
                true
            }
            AppRoute.SecuritySettings,
            AppRoute.BackupSettings,
            AppRoute.AppearanceSettings,
            AppRoute.ConnectedDApps,
            AppRoute.IdentityProviderSettings -> {
                closeSettingsDetail()
                true
            }
            AppRoute.RpcAddNetwork -> {
                closeAddRpcNetwork()
                true
            }
            AppRoute.IdentityProviderAdd -> {
                closeAddIdentityProvider()
                true
            }
            AppRoute.AddressBook -> {
                closeAddressBook()
                true
            }
            AppRoute.AddressBookAdd -> {
                closeAddAddressBookContact()
                true
            }
            AppRoute.TransactionDetails -> {
                closeTransactionDetails()
                true
            }
            AppRoute.Send -> {
                closeSend()
                true
            }
            AppRoute.SendAsset -> {
                closeSendAsset()
                true
            }
            AppRoute.Receive -> {
                closeReceive()
                true
            }
            AppRoute.Bridge -> {
                if (!bridgeBusy || !bridgeDialogVisible) closeBridge()
                true
            }
            AppRoute.BridgeHistory -> {
                closeBridgeHistory()
                true
            }
            AppRoute.Evm -> {
                closeEvm()
                true
            }
            AppRoute.EvmSend -> {
                closeEvmSend()
                true
            }
            AppRoute.EvmReceive -> {
                closeEvmReceive()
                true
            }
            AppRoute.EvmActivity -> {
                closeEvmActivity()
                true
            }
            AppRoute.EvmNetwork -> {
                closeEvmNetworkSettings()
                true
            }
            AppRoute.EvmAddNetwork -> {
                closeEvmAddNetwork()
                true
            }
            AppRoute.EvmAddToken -> {
                closeEvmAddToken()
                true
            }
            AppRoute.EvmAddNft -> {
                closeEvmAddNft()
                true
            }
            AppRoute.AddAsset -> {
                closeAddAsset()
                true
            }
            AppRoute.Settings -> {
                closeSettings()
                true
            }
            AppRoute.ImportWallet -> {
                closeImportWallet()
                true
            }
            AppRoute.SecuritySetup -> {
                route = if (vault?.activeWallet != null) AppRoute.Dashboard else AppRoute.Welcome
                true
            }
            AppRoute.Dashboard -> {
                if (dashboardTab != DashboardTab.Overview) {
                    dashboardTab = DashboardTab.Overview
                    true
                } else {
                    false
                }
            }
            else -> false
        }
    }

    private fun stageOrPersistWallet(newWallet: Wallet) {
        val current = vault
        val nextIndex = (current?.wallets?.size ?: 0) + 1
        val namedWallet = if (newWallet.name.isBlank()) {
            newWallet.copy(name = "Wallet $nextIndex")
        } else {
            newWallet
        }
        val next = current?.addWallet(namedWallet) ?: WalletVault.single(namedWallet)
        if (current == null) {
            pendingVault = next
            route = AppRoute.SecuritySetup
        } else {
            persistVault(next, "Wallet added.")
        }
    }

    private fun persistVault(next: WalletVault, message: String) {
        when (secureStore.currentMethod()) {
            SecurityMethod.Pin -> {
                val pin = sessionPin ?: run {
                    showError("Unlock with PIN again before saving wallet changes.")
                    return
                }
                runBusy {
                    withContext(Dispatchers.IO) { secureStore.saveVaultWithPin(next, pin) }
                    completeVaultSetup(next)
                    showMessage(message)
                }
            }
            SecurityMethod.Biometric -> {
                val cipher = runCatching { secureStore.createBiometricEncryptCipher() }
                    .getOrElse {
                        showError(it.message ?: "Unable to start biometric confirmation.")
                        return
                    }
                DeviceSecurity.authenticateWithCipher(
                    activity = activity,
                    title = "Confirm Wallet Change",
                    subtitle = "Authenticate to save the encrypted vault.",
                    negativeText = "Cancel",
                    cipher = cipher,
                    onSuccess = { authenticatedCipher ->
                        runBusy {
                            withContext(Dispatchers.IO) { secureStore.saveVaultWithBiometric(next, authenticatedCipher) }
                            completeVaultSetup(next)
                            showMessage(message)
                        }
                    },
                    onError = { showError(it) },
                )
            }
            null -> showError("No current security method found.")
        }
    }

    private fun persistWalletMetadata(next: WalletVault, message: String) {
        vault = next
        wallet = next.activeWallet
        secureStore.saveWalletMetadata(next)
        showMessage(message)
    }

    private fun completeVaultSetup(savedVault: WalletVault) {
        vault = savedVault
        wallet = savedVault.activeWallet
        pendingVault = null
        route = AppRoute.Dashboard
        refreshDashboard()
    }

    private fun refreshBalance() {
        val activeWallet = wallet ?: return
        refreshBalance(activeWallet)
    }

    private fun refreshBalance(activeWallet: Wallet) = scope.launch {
        tokenBalance = tokenBalance.copy(isLoading = true, error = null)
        try {
            val snapshot = withContext(Dispatchers.IO) {
                rpcRepository.fetchBalance(activeWallet.address)
            }
            availableBalance = snapshot.balance
            tokenBalance = snapshot.toTokenBalance()
            updateFiatEstimate()
        } catch (exception: Exception) {
            availableBalance = 0.0
            tokenBalance = tokenBalance.copy(
                amount = "--",
                fiatEstimate = "Balance unavailable",
                isLoading = false,
                error = exception.message ?: "Balance unavailable",
            )
        }
    }

    private fun refreshAssets() {
        val activeWallet = wallet ?: return
        assetsLoading = true
        scope.launch {
            try {
                val network = rpcRepository.currentNetwork()
                val saved = assetRepository.savedTokenAddresses(network)
                octraTokens = withContext(Dispatchers.IO) {
                    rpcRepository.fetchOctraTokens(activeWallet.address, saved)
                }
            } catch (_: Exception) {
                octraTokens = emptyList()
            } finally {
                assetsLoading = false
            }
        }
    }

    private fun refreshOctPrice() {
        val now = System.currentTimeMillis()
        if (priceLoading || now - lastPriceFetchAt < PriceRefreshMs) return
        priceLoading = true
        lastPriceFetchAt = now
        scope.launch {
            try {
                octPriceRepository.fetchUsdPrice()?.let { price ->
                    octUsdPrice = price
                    updateFiatEstimate()
                }
            } finally {
                priceLoading = false
            }
        }
    }

    private fun refreshHistory(reset: Boolean = true) {
        val activeWallet = wallet ?: return
        refreshHistory(activeWallet, reset)
    }

    private fun refreshHistory(activeWallet: Wallet, reset: Boolean = true) = scope.launch {
        if (reset) {
            historyLimit = InitialHistoryLimit
            historyCanLoadMore = true
        }
        val requestLimit = historyLimit
        val requestSerial = ++historyRequestSerial
        historyLoading = true
        kotlinx.coroutines.coroutineScope {
            try {
                val transactions = withContext(Dispatchers.IO) {
                    rpcRepository.fetchTransactionHistory(activeWallet.address, requestLimit)
                }
                if (requestSerial == historyRequestSerial && historyLimit == requestLimit) {
                    recentTransactions = transactions
                    historyCanLoadMore = requestLimit < MaxHistoryLimit && transactions.size >= requestLimit
                    refreshRecentActivityIdentityNames(transactions)
                }
            } catch (_: Exception) {
                if (requestSerial == historyRequestSerial && historyLimit == requestLimit) {
                    recentTransactions = emptyList()
                    historyCanLoadMore = false
                }
            } finally {
                if (requestSerial == historyRequestSerial) historyLoading = false
            }
        }
    }

    private fun addPendingTransaction(
        hash: String,
        from: String,
        recipient: String,
        amount: Double,
        message: String?,
        opType: String = "standard",
    ) {
        val pending = TransactionHistoryItem(
            hash = hash,
            from = from,
            to = recipient,
            amount = amount,
            timestamp = System.currentTimeMillis() / 1000.0,
            status = "pending",
            type = "sent",
            opType = opType,
            message = message?.trim()?.ifBlank { null },
        )
        recentTransactions = (listOf(pending) + recentTransactions.filterNot { it.hash == hash })
            .take(historyLimit)
        refreshRecentActivityIdentityNames(recentTransactions)
    }

    private fun pollSubmittedTransaction(hash: String) {
        transactionStatusPolling = true
        scope.launch {
            try {
                repeat(TransactionConfirmationAttempts) { attempt ->
                    val state = withContext(Dispatchers.IO) { rpcRepository.checkTransactionStatus(hash) }
                    when (state.status) {
                        TransactionNetworkStatus.Confirmed -> {
                            updateSubmittedTransaction(hash, TransactionFinality.Confirmed)
                            refreshDashboard()
                            return@launch
                        }
                        TransactionNetworkStatus.Rejected -> {
                            updateSubmittedTransaction(hash, TransactionFinality.Rejected, state.reason)
                            recentTransactions = recentTransactions.filterNot { it.hash == hash }
                            refreshDashboard()
                            return@launch
                        }
                        TransactionNetworkStatus.Dropped -> {
                            updateSubmittedTransaction(hash, TransactionFinality.Dropped, state.reason)
                            recentTransactions = recentTransactions.filterNot { it.hash == hash }
                            refreshDashboard()
                            return@launch
                        }
                        TransactionNetworkStatus.NotFound -> if (attempt > 2) {
                            updateSubmittedTransaction(hash, TransactionFinality.Dropped, "Transaction was not found on the network.")
                            recentTransactions = recentTransactions.filterNot { it.hash == hash }
                            refreshDashboard()
                            return@launch
                        }
                        TransactionNetworkStatus.Pending -> Unit
                    }
                    if (attempt < TransactionConfirmationAttempts - 1) delay(TransactionPollingIntervalMs)
                }
                refreshDashboard()
            } finally {
                transactionStatusPolling = false
            }
        }
    }

    private fun pollEvmTransaction(hash: String, network: EvmNetwork) {
        transactionStatusPolling = true
        scope.launch {
            try {
                repeat(TransactionConfirmationAttempts) { attempt ->
                    val state = withContext(Dispatchers.IO) { evmRepository.transactionReceiptStatus(hash, network) }
                    when (state.status) {
                        TransactionNetworkStatus.Confirmed -> {
                            updateSubmittedTransaction(hash, TransactionFinality.Confirmed)
                            refreshEvmDashboard()
                            return@launch
                        }
                        TransactionNetworkStatus.Rejected,
                        TransactionNetworkStatus.Dropped -> {
                            updateSubmittedTransaction(hash, TransactionFinality.Rejected, state.reason)
                            refreshEvmDashboard()
                            return@launch
                        }
                        TransactionNetworkStatus.Pending,
                        TransactionNetworkStatus.NotFound -> Unit
                    }
                    if (attempt < TransactionConfirmationAttempts - 1) delay(TransactionPollingIntervalMs)
                }
                refreshEvmDashboard()
            } finally {
                transactionStatusPolling = false
            }
        }
    }

    private fun pollTradeTransaction(hash: String) {
        transactionStatusPolling = true
        scope.launch {
            try {
                repeat(TransactionConfirmationAttempts) { attempt ->
                    val state = withContext(Dispatchers.IO) { tradeRepository.transactionStatus(hash) }
                    when (state.status) {
                        TransactionNetworkStatus.Confirmed -> {
                            updateSubmittedTransaction(hash, TransactionFinality.Confirmed)
                            refreshTrade()
                            refreshEvmDashboard()
                            return@launch
                        }
                        TransactionNetworkStatus.Rejected,
                        TransactionNetworkStatus.Dropped -> {
                            updateSubmittedTransaction(hash, TransactionFinality.Rejected, state.reason)
                            refreshTrade()
                            return@launch
                        }
                        TransactionNetworkStatus.Pending,
                        TransactionNetworkStatus.NotFound -> Unit
                    }
                    if (attempt < TransactionConfirmationAttempts - 1) delay(TransactionPollingIntervalMs)
                }
                refreshTrade()
            } finally {
                transactionStatusPolling = false
            }
        }
    }

    private fun updateSubmittedTransaction(hash: String, status: TransactionFinality, reason: String? = null) {
        val current = submittedTransaction ?: return
        if (current.hash == hash) submittedTransaction = current.copy(status = status, reason = reason)
    }

    private fun beginSubmittedTransaction(recipientAddress: String, amount: Double, showDialog: Boolean = true) {
        submittedTransaction = SubmittedTransaction(
            hash = null,
            recipientAddress = recipientAddress,
            amount = amount,
            status = TransactionFinality.Submitting,
            showDialog = showDialog,
        )
    }

    private fun updateSubmittedTransactionResult(
        hash: String?,
        recipientAddress: String,
        amount: Double,
        status: TransactionFinality,
        reason: String? = null,
        showDialog: Boolean = submittedTransaction?.showDialog ?: true,
    ) {
        submittedTransaction = SubmittedTransaction(
            hash = hash,
            recipientAddress = recipientAddress,
            amount = amount,
            status = status,
            reason = reason,
            showDialog = showDialog,
        )
    }

    private fun rejectSubmittingTransaction(reason: String) {
        val current = submittedTransaction ?: return
        if (current.status == TransactionFinality.Submitting) {
            submittedTransaction = current.copy(status = TransactionFinality.Rejected, reason = reason)
        }
    }

    private fun resolveDAppReadRequest(origin: String, activeWallet: Wallet, request: JSONObject): String {
        return when (val method = request.optString("method")) {
            "octra_accounts" -> {
                if (dAppSession(origin, activeWallet.address) != null) JSONArray().put(activeWallet.address).toString()
                else JSONArray().toString()
            }
            "octra_permissions" -> {
                JSONArray(dAppSession(origin, activeWallet.address)?.permissions ?: emptyList<String>()).toString()
            }
            "octra_disconnect" -> {
                dAppSessionRepository.revoke(origin, activeWallet.address)
                dApps = connectedDAppSessions()
                "null"
            }
            "octra_networkInfo" -> {
                val network = activeRpcNetwork ?: rpcRepository.activeNetwork()
                JSONObject()
                    .put("id", network.id)
                    .put("name", network.name)
                    .put("rpcUrl", network.url)
                    .put("explorerUrl", network.explorerUrl)
                    .put("supportsPrivacy", false)
                    .put("isTestnet", network.networkType == "devnet")
                    .toString()
            }
            "octra_networkId" -> {
                val network = activeRpcNetwork ?: rpcRepository.activeNetwork()
                JSONObject.quote(network.id.ifBlank { "octra:${network.networkType}" })
            }
            "octra_viewPubkey" -> JSONObject.quote("0")
            else -> throw NoSuchMethodException("Unsupported poctra provider method: $method")
        }
    }

    private fun queueDAppApproval(
        origin: String,
        title: String,
        message: String,
        details: List<DAppRequestDetail> = emptyList(),
        approveLabel: String,
        onSuccess: (String) -> Unit,
        onError: (Int, String) -> Unit,
        action: suspend () -> String,
    ) {
        pendingDAppRequest = PendingDAppRequest(
            origin = origin,
            title = title,
            message = message,
            details = details,
            approveLabel = approveLabel,
        )
        pendingDAppAction = action
        pendingDAppSuccess = onSuccess
        pendingDAppError = onError
    }

    private fun clearPendingDAppRequest() {
        pendingDAppRequest = null
        pendingDAppAction = null
        pendingDAppSuccess = null
        pendingDAppError = null
    }

    private fun authorizeDApp(origin: String, walletAddress: String, permissions: List<String>) {
        val session = DAppSession(
            name = hostLabel(origin),
            url = origin,
            walletAddress = walletAddress,
            permissions = permissions,
            network = activeRpcNetwork?.networkType ?: networkFor(currentRpcUrl),
            connectedAt = System.currentTimeMillis(),
        )
        val next = dAppSessionRepository.listSessions()
            .filterNot { canonicalizeOrigin(it.url) == origin && it.walletAddress == walletAddress } + session
        dAppSessionRepository.persist(next)
        dApps = connectedDAppSessions()
    }

    private fun dAppSession(origin: String, walletAddress: String): DAppSession? =
        dAppSessionRepository.listSessions().firstOrNull {
            canonicalizeOrigin(it.url) == origin && it.walletAddress == walletAddress
        }

    private fun connectedDAppSessions(): List<DAppSession> {
        val activeAddress = wallet?.address ?: return emptyList()
        return dAppSessionRepository.listSessions().filter { it.walletAddress == activeAddress }
    }

    private fun requestedDAppPermissions(raw: Any?): List<String> {
        val obj = when (raw) {
            is JSONArray -> raw.optJSONObject(0)
            is JSONObject -> raw
            else -> null
        }
        val array = obj?.optJSONArray("permissions")
        val requested = buildList {
            if (array != null) {
                for (index in 0 until array.length()) {
                    val value = array.optString(index)
                    if (value.isNotBlank()) add(value)
                }
            }
        }
        return requested.ifEmpty {
            listOf("read_address", "read_balance", "read_public_key", "contract_calls")
        }.distinct()
    }

    private fun contractCallRequest(raw: Any?): JSONObject {
        val obj = when (raw) {
            is JSONArray -> raw.optJSONObject(0)
            is JSONObject -> raw
            else -> null
        } ?: throw IllegalArgumentException("Contract call payload is required.")
        require(obj.optString("address").isNotBlank()) { "Contract address is required." }
        require(obj.optString("method").isNotBlank()) { "Contract method is required." }
        return obj
    }

    private fun explorerTxUrl(hash: String?): String? {
        if (hash.isNullOrBlank()) return null
        val explorer = activeRpcNetwork?.explorerUrl.orEmpty().trimEnd('/')
        if (explorer.isBlank()) return null
        return "$explorer/tx.html?hash=$hash"
    }

    private fun explorerAddressUrl(address: String?): String? {
        if (address.isNullOrBlank()) return null
        val explorer = activeRpcNetwork?.explorerUrl.orEmpty().trimEnd('/')
        if (explorer.isBlank()) return null
        return "$explorer/address.html?addr=${encodeUrlPart(address)}"
    }

    private fun dAppError(exception: Exception): Pair<Int, String> = when (exception) {
        is SecurityException -> 4100 to (exception.message ?: "Unauthorized")
        is NoSuchMethodException -> -32601 to (exception.message ?: "Method not found")
        else -> -32000 to (exception.message ?: "poctra request failed")
    }

    private fun shortAddress(address: String): String {
        if (address.length <= 16) return address
        return "${address.take(8)}...${address.takeLast(6)}"
    }

    private fun parseTokenAmount(input: String, decimals: Int): BigInteger? {
        val clean = input.trim().replace(",", ".")
        if (clean.isBlank() || clean == "0") return null
        val parts = clean.split(".")
        if (parts.size > 2) return null
        val whole = parts.getOrNull(0).orEmpty().filter(Char::isDigit).ifBlank { "0" }
        val rawFraction = parts.getOrNull(1).orEmpty().filter(Char::isDigit)
        val safeDecimals = decimals.coerceAtLeast(0)
        val fraction = rawFraction.take(safeDecimals).padEnd(safeDecimals, '0')
        val raw = (whole + fraction).trimStart('0').ifBlank { "0" }
        return raw.toBigIntegerOrNull()?.takeIf { it > BigInteger.ZERO }
    }

    private data class CircleInputSpec(
        val host: String,
        val path: String,
        val identityName: String?,
    )

    private fun parseCircleInputSpec(value: String): CircleInputSpec? {
        val raw = value.trim()
        if (raw.isBlank()) return null
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) return null

        val withoutScheme = if (raw.startsWith("oct://", ignoreCase = true)) raw.drop(6) else raw
        val clean = withoutScheme.trim().substringBefore('?').substringBefore('#')
        if (clean.isBlank()) return null

        val slashIndex = clean.indexOf('/')
        val host = if (slashIndex >= 0) clean.take(slashIndex).trim() else clean.trim()
        val path = if (slashIndex >= 0) normalizeCirclePath(clean.substring(slashIndex)) else "/index.html"
        if (host.isBlank()) return null
        if (isOctraAddress(host)) return CircleInputSpec(host = host, path = path, identityName = null)

        val identity = host.lowercase()
        if (identity.endsWith(".oct") && isIdentityNameInput(identity)) {
            return CircleInputSpec(host = identity, path = path, identityName = identity)
        }
        return null
    }

    private fun circleDisplayUri(spec: CircleInputSpec?, rawInput: String): String {
        val parsed = spec ?: parseCircleInputSpec(rawInput)
        val fallback = rawInput.trim().let {
            if (it.startsWith("oct://", ignoreCase = true)) it.drop(6) else it
        }
        val host = parsed?.identityName ?: parsed?.host ?: fallback.substringBefore('/').ifBlank { fallback }
        val path = parsed?.path
            ?.takeIf { it.isNotBlank() && it != "/index.html" }
            .orEmpty()
        return "oct://$host$path"
    }

    private fun normalizeCirclePath(rawPath: String): String {
        val clean = rawPath.trim()
        if (clean.isBlank()) return "/index.html"
        return if (clean.startsWith("/")) clean else "/$clean"
    }

    private fun circleBrowserUrl(circleId: String, path: String): String {
        val segments = normalizeCirclePath(path)
            .split('/')
            .filter { it.isNotBlank() }
            .joinToString("/") { encodeUrlPart(it) }
        val suffix = if (segments.isBlank()) "index.html" else segments
        return "https://$CircleBrowserHost/oct/${encodeUrlPart(circleId)}/$suffix"
    }

    private fun circleLoadingUrl(rawInput: String): String =
        "https://$CircleBrowserHost/loading?target=${encodeUrlPart(rawInput)}"

    private fun circleErrorUrl(rawInput: String): String =
        "https://$CircleBrowserHost/error?target=${encodeUrlPart(rawInput)}"

    private fun encodeUrlPart(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    private suspend fun resolveOctraRecipient(input: String, activeWallet: Wallet): String {
        val clean = input.trim()
        if (isOctraAddress(clean)) return clean
        val name = clean.lowercase()
        require(isIdentityNameInput(name)) { "Enter foo.oct or a valid Octra address." }
        val provider = activeIdentityProvider
            ?: identityProviderRepository.activeProvider(currentIdentityNetwork())
            ?: throw IllegalStateException("Add an Octra Identity Provider in Settings first.")
        return withContext(Dispatchers.IO) {
            rpcRepository.resolveIdentityName(provider, name, activeWallet.address)
        } ?: throw IllegalStateException("$name could not be resolved.")
    }

    private fun isOctraRecipientInput(value: String): Boolean =
        isOctraAddress(value.trim()) || isIdentityNameInput(value.trim().lowercase())

    private fun isOctraAddress(value: String): Boolean = value.startsWith("oct") && value.length == 47

    private fun isIdentityNameInput(value: String): Boolean {
        val path = value
            .removeSuffix(".oct")
            .removeSuffix(".octra")
            .removeSuffix(".id")
        val parts = path.split('.')
        return value.contains('.') &&
            parts.isNotEmpty() &&
            parts.size <= 2 &&
            parts.all { it.matches(Regex("^[a-z0-9]{3,63}$")) }
    }

    private fun BalanceSnapshot.toTokenBalance(): TokenBalance = TokenBalance(
        symbol = "OCT",
        name = "Octra",
        amount = "%,.6f".format(balance).trimEnd('0').trimEnd('.').ifBlank { "0" },
        fiatEstimate = "~ $-- USD",
        isLoading = false,
    )

    private fun updateFiatEstimate() {
        val price = octUsdPrice ?: return
        tokenBalance = tokenBalance.copy(
            fiatEstimate = "~ ${formatUsd(availableBalance * price)} USD",
        )
    }

    private fun formatUsd(value: Double): String = when {
        value >= 1_000_000 -> "$${"%.2f".format(value / 1_000_000)}M"
        value >= 1_000 -> "$${"%.2f".format(value / 1_000)}K"
        else -> "$${"%.2f".format(value)}"
    }

    private fun runBusy(block: suspend () -> Unit) {
        scope.launch {
            busy = true
            clearError()
            try {
                block()
            } catch (exception: Exception) {
                rejectSubmittingTransaction(exception.message ?: "Transaction failed.")
                showError(exception.message ?: "Something went wrong.")
            } finally {
                busy = false
            }
        }
    }

    private fun String?.toGasPriceWeiOrNull(): BigInteger? {
        val value = this
            ?.trim()
            ?.replace(",", ".")
            ?.takeIf { it.isNotBlank() }
            ?.toBigDecimalOrNull()
            ?.takeIf { it > BigDecimal.ZERO }
            ?: return null
        return value
            .multiply(BigDecimal("1000000000"))
            .toBigInteger()
            .takeIf { it > BigInteger.ZERO }
    }

    private fun networkFor(url: String): String {
        val lower = url.lowercase()
        return if (lower.contains("devnet") || lower.contains("165.227.225.79")) "devnet" else "mainnet"
    }

    private fun initialDarkMode(): Boolean {
        if (!uiPrefs.getBoolean(KeyDarkDefaultMigrated, false)) {
            uiPrefs.edit()
                .putBoolean(KeyDarkMode, true)
                .putBoolean(KeyDarkDefaultMigrated, true)
                .apply()
            return true
        }
        return uiPrefs.getBoolean(KeyDarkMode, true)
    }

    companion object {
        private const val KeyDarkMode = "dark_mode"
        private const val KeyDarkDefaultMigrated = "dark_default_migrated"
        private const val InitialHistoryLimit = 10
        private const val HistoryPageSize = 10
        private const val MaxHistoryLimit = 50
        private const val KeyBridgeHistory = "bridge_history"
        private const val MaxBridgeHistoryItems = 25
        private const val MicroUnits = 1_000_000L
        private const val BridgeFeeReserveOu = 10_000L
        private const val PriceRefreshMs = 60_000L
        private const val TransactionConfirmationAttempts = 15
        private const val TransactionPollingIntervalMs = 2_000L
        private const val BridgeEvmReceiptAttempts = 100
        private const val BridgeEvmReceiptIntervalMs = 3_000L
        private const val BridgeClaimRetryAttempts = 8
        private const val BridgeClaimRetryDelayMs = 8_000L
        private const val EvmNativeGasLimit = 21_000L
        private const val EvmTokenGasLimit = 80_000L
        private const val EvmNftGasLimit = 120_000L
        private const val HiddenEvmRpcKey = "<hidden>"
        const val CircleBrowserHost = "octra-circle.local"
    }
}

private fun nativeAssetName(symbol: String): String = when (symbol.uppercase()) {
    "ETH" -> "Ethereum"
    "BNB" -> "BNB"
    "POL" -> "Polygon"
    "MATIC" -> "Polygon"
    else -> symbol.uppercase()
}

private fun formatOuAmount(raw: String): String {
    val value = raw.toBigDecimalOrNull() ?: return "0"
    return value
        .divide(BigDecimal("1000000"), 6, RoundingMode.DOWN)
        .stripTrailingZeros()
        .toPlainString()
}

private fun String.cleanAmount(): String = toBigDecimalOrNull()?.formatAmount() ?: this

private fun BigDecimal.formatAmount(): String =
    setScale(8, RoundingMode.DOWN).stripTrailingZeros().toPlainString().ifBlank { "0" }
