package com.poctra.wallet

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
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
import com.poctra.wallet.data.ConsumerSettingsRepository
import com.poctra.wallet.data.DAppSession
import com.poctra.wallet.data.DAppSessionRepository
import com.poctra.wallet.data.DAppCatalogEntry
import com.poctra.wallet.data.DAppCatalogRepository
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
import com.poctra.wallet.data.OctraIdentityName
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
import com.poctra.wallet.data.WalletIdenticon
import com.poctra.wallet.data.WalletType
import com.poctra.wallet.data.WalletVault
import com.poctra.wallet.data.TransactionNetworkState
import com.poctra.wallet.data.TransactionNetworkStatus
import com.poctra.wallet.data.canonicalizeOrigin
import com.poctra.wallet.data.hostLabel
import com.poctra.wallet.security.DAppUrlPolicy
import com.poctra.wallet.security.DeviceSecurity
import com.poctra.wallet.security.SecureWalletStore
import com.poctra.wallet.privacy.PrivacyClaimableTransfer
import com.poctra.wallet.privacy.PrivacyClaimPayload
import com.poctra.wallet.privacy.PrivacyCryptoEngine
import com.poctra.wallet.privacy.PrivacyEncryptedBalance
import com.poctra.wallet.privacy.PrivacyOperationStage
import com.poctra.wallet.privacy.PrivacyOperationState
import com.poctra.wallet.privacy.PrivacyOperationType
import com.poctra.wallet.privacy.PrivacyStealthScanner
import com.poctra.wallet.privacy.PrivacyStealthSender
import com.poctra.wallet.privacy.PrivacySyncStatus
import com.poctra.wallet.privacy.PrivacySyncStep
import com.poctra.wallet.privacy.formatPrivacyAmount
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.net.URLEncoder
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.Collections
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
    PrivacyEncrypt,
    PrivacyDecrypt,
    PrivacyStealth,
    PrivacyClaim,
    Settings,
    RpcManagement,
    RpcAddNetwork,
    SecuritySettings,
    BackupSettings,
    SecurityCenter,
    ActivityCenter,
    PaymentRequest,
    PortfolioAnalytics,
    PortfolioAssetHistory,
    ApprovalDashboard,
    RecoveryPlan,
    FundingProviders,
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

data class CirclePassphraseRequest(
    val circleId: String,
    val label: String,
)

data class DAppBrowserTab(
    val id: String,
    val title: String,
    val url: String,
    val displayUrl: String?,
    val circleTarget: OctraCircleTarget?,
    val circleError: String?,
)

enum class DAppInputPreviewStatus {
    Loading,
    Circle,
    Explorer,
    Web,
    Error,
}

data class DAppInputPreview(
    val input: String,
    val title: String,
    val subtitle: String,
    val detail: String,
    val status: DAppInputPreviewStatus,
    val showOctProtocol: Boolean = false,
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
    Privacy,
    Explore,
    Trade,
}

private fun initialPrivacySyncSteps(): List<PrivacySyncStep> = listOf(
    PrivacySyncStep("initializing.."),
    PrivacySyncStep("loading encrypted balance"),
    PrivacySyncStep("loading privacy history"),
)

enum class TradeStep {
    Idle,
    TokenApproval,
    PermitApproval,
    Swapping,
    Completed,
    Failed,
}

private data class TradeChartCacheEntry(
    val candles: List<TradeCandle>,
    val fetchedAtMs: Long,
)

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
    private val tradePrefs = appContext.getSharedPreferences("poctra_trade", Context.MODE_PRIVATE)
    private val walletCrypto = WalletCrypto(appContext)
    private val secureStore = SecureWalletStore(appContext)
    private val rpcRepository = RpcRepository(appContext)
    private val dAppSessionRepository = DAppSessionRepository(appContext)
    private val dAppCatalogRepository = DAppCatalogRepository(appContext)
    private val addressBookRepository = AddressBookRepository(appContext)
    private val consumerSettingsRepository = ConsumerSettingsRepository(appContext)
    private val assetRepository = AssetRepository(appContext)
    private val identityProviderRepository = OctraIdentityProviderRepository(appContext)
    private val evmRepository = EvmRepository(appContext)
    private val bridgeRepository = BridgeRepository()
    private val tradeRepository = TradeRepository()
    private val octPriceRepository = OctPriceRepository()
    private val backupExporter = BackupExporter(appContext)
    private val privacyCrypto = PrivacyCryptoEngine()
    private val privacyScanner = PrivacyStealthScanner()
    private val privacyStealthSender = PrivacyStealthSender()
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
    var dAppCatalog by mutableStateOf<List<DAppCatalogEntry>>(dAppCatalogRepository.cachedEntries())
        private set
    var dAppCatalogLoading by mutableStateOf(false)
        private set
    var dAppCatalogError by mutableStateOf<String?>(null)
        private set
    var addressBookContacts by mutableStateOf<List<AddressBookContact>>(emptyList())
        private set
    var securityRisks by mutableStateOf<List<ConsumerRiskItem>>(emptyList())
        private set
    var unifiedActivity by mutableStateOf<List<UnifiedActivityItem>>(emptyList())
        private set
    var activityCenterLimit by mutableStateOf(InitialActivityLimit)
        private set
    var paymentRequestDraft by mutableStateOf<PaymentRequestDraft?>(null)
        private set
    var portfolioHoldings by mutableStateOf<List<PortfolioHolding>>(emptyList())
        private set
    var selectedPortfolioHolding by mutableStateOf<PortfolioHolding?>(null)
        private set
    var portfolioAssetActivity by mutableStateOf<List<UnifiedActivityItem>>(emptyList())
        private set
    var portfolioRefreshLoading by mutableStateOf(false)
        private set
    var activityCenterRefreshLoading by mutableStateOf(false)
        private set
    var approvalReviews by mutableStateOf<List<ApprovalReviewItem>>(emptyList())
        private set
    var approvalReviewLoading by mutableStateOf(false)
        private set
    var domainGuardRules by mutableStateOf<List<DomainGuardRule>>(emptyList())
        private set
    var fundingProviders by mutableStateOf(consumerSettingsRepository.fundingProviders())
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
    var privacySupported by mutableStateOf(true)
        private set
    var privacySyncing by mutableStateOf(false)
        private set
    var privacySyncSteps by mutableStateOf(initialPrivacySyncSteps())
        private set
    var privacyEncryptedBalance by mutableStateOf<PrivacyEncryptedBalance?>(null)
        private set
    var privacyBalanceRaw by mutableStateOf<Long?>(null)
        private set
    var privacyBalanceVisible by mutableStateOf(false)
        private set
    var privacyBalanceDecrypting by mutableStateOf(false)
        private set
    var privacyHistory by mutableStateOf<List<TransactionHistoryItem>>(emptyList())
        private set
    var privacyRecommendedFeeOu by mutableStateOf(10000L)
        private set
    var privacyFeeLoading by mutableStateOf(false)
        private set
    var privacyClaimableTransfers by mutableStateOf<List<PrivacyClaimableTransfer>>(emptyList())
        private set
    var privacyOperation by mutableStateOf<PrivacyOperationState?>(null)
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
    var circlePassphraseRequest by mutableStateOf<CirclePassphraseRequest?>(null)
        private set
    var dAppBrowserTabs by mutableStateOf<List<DAppBrowserTab>>(emptyList())
        private set
    var activeDAppTabId by mutableStateOf<String?>(null)
        private set
    var dAppInputPreview by mutableStateOf<DAppInputPreview?>(null)
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
    var evmHistoryVisibleLimit by mutableStateOf(InitialActivityLimit)
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
    var bridgeHistoryLimit by mutableStateOf(InitialActivityLimit)
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
    var tradeChartRefreshCoolingDown by mutableStateOf(false)
        private set
    var pendingDAppRequest by mutableStateOf<PendingDAppRequest?>(null)
        private set

    private var sessionPin: String? = null
    private var unlockReturnRoute: AppRoute = AppRoute.Dashboard
    private var unlockReturnTab: DashboardTab = DashboardTab.Overview
    private var dAppReturnRoute: AppRoute = AppRoute.Dashboard
    private var dAppReturnTab: DashboardTab = DashboardTab.Explore
    private var addressBookReturnRoute: AppRoute = AppRoute.Settings
    private var activityCenterReturnRoute: AppRoute = AppRoute.Settings
    private val settingsDetailBackStack = mutableListOf<AppRoute>()
    private var pendingRecipientAddress: String? = null
    private var pendingRecipientIdentityName: String? = null
    private var pendingDAppAction: (suspend () -> String)? = null
    private var pendingDAppSuccess: ((String) -> Unit)? = null
    private var pendingDAppError: ((Int, String) -> Unit)? = null
    private var busyOperationCount = 0
    private var lastPriceFetchAt = 0L
    private var rpcStatusRequestSerial = 0L
    private var balanceRequestSerial = 0L
    private var assetsRequestSerial = 0L
    private var historyRequestSerial = 0L
    private var feeRequestSerial = 0L
    private var evmAccountRequestSerial = 0L
    private var evmAssetsRequestSerial = 0L
    private var evmHistoryRequestSerial = 0L
    private var bridgeBalanceRequestSerial = 0L
    private var bridgeReviewRequestSerial = 0L
    private var bridgeHistoryRequestSerial = 0L
    private var tradeBalanceRequestSerial = 0L
    private var transactionPollingCount = 0
    private var evmSendPollSerial = 0L
    private var identityLookupSerial = 0L
    private var addressIdentityLookupSerial = 0L
    private var addressIdentityCacheKey: String? = null
    private var addressIdentityMisses: Set<String> = emptySet()
    private var tradeChartRequestSerial = 0L
    private var tradeChartInFlightKey: String? = null
    private var tradeChartCooldownSerial = 0L
    private var tradeQuoteRequestSerial = 0L
    private var circleResolveSerial = 0L
    private var dAppInputPreviewSerial = 0L
    private var dAppCatalogRequestSerial = 0L
    private var dAppCatalogFetchedAt = 0L
    private var approvalReviewRequestSerial = 0L
    private var portfolioRefreshSerial = 0L
    private var privacySyncSerial = 0L
    private var privacyOperationSerial = 0L
    private var privacyFeeSerial = 0L
    private var privacyLoadedKey: String? = null
    private var privacyLoadedAt = 0L
    private var privacyDecryptedCipher: String? = null
    private var privacyClaimCacheKey: String? = null
    private var privacyClaimCacheAt = 0L
    private var tradePendingEvmTransaction by mutableStateOf<EvmSendResult?>(null)
    private val circleResourceModeCache = ConcurrentHashMap<String, String>()
    private val circlePassphrases = ConcurrentHashMap<String, String>()
    private val activeCircleDownloads = Collections.newSetFromMap(ConcurrentHashMap<String, Boolean>())
    private val tradeChartCache = mutableMapOf<String, TradeChartCacheEntry>()

    val biometricAvailable: Boolean
        get() = DeviceSecurity.isBiometricAvailable(activity)

    val currentSecurityMethod: SecurityMethod?
        get() = secureStore.currentMethod()

    val currentRpcUrl: String
        get() = activeRpcNetwork?.url ?: rpcRepository.currentUrl()

    fun octraIdentityDAppUrl(): String =
        if ((activeRpcNetwork?.networkType ?: networkFor(currentRpcUrl)) == "devnet") {
            "https://devnet.octra.id"
        } else {
            "https://octra.id"
        }

    val visibleUnifiedActivity: List<UnifiedActivityItem>
        get() = unifiedActivity.take(activityCenterLimit)

    val activityCenterCanLoadMore: Boolean
        get() = unifiedActivity.size > activityCenterLimit

    val visibleBridgeHistory: List<BridgeHistoryItem>
        get() = bridgeHistory.take(bridgeHistoryLimit)

    val bridgeHistoryCanLoadMore: Boolean
        get() = bridgeHistory.size > bridgeHistoryLimit

    val visibleEvmTransactions: List<EvmTransaction>
        get() = evmTransactions.take(evmHistoryVisibleLimit)

    val evmActivityCanLoadMore: Boolean
        get() = evmTransactions.size > evmHistoryVisibleLimit

    private fun isActiveWallet(address: String): Boolean =
        wallet?.address?.equals(address, ignoreCase = true) == true

    private fun isActiveRpcSnapshot(walletAddress: String, rpcUrl: String): Boolean =
        isActiveWallet(walletAddress) && currentRpcUrl.equals(rpcUrl, ignoreCase = true)

    private fun isActiveEvmSnapshot(walletAddress: String, networkId: String): Boolean =
        isActiveWallet(walletAddress) &&
            (activeEvmNetwork ?: runCatching { evmRepository.activeNetwork() }.getOrNull())?.id == networkId

    private fun isActiveEvmNetworkSnapshot(networkId: String?): Boolean =
        (activeEvmNetwork ?: runCatching { evmRepository.activeNetwork() }.getOrNull())?.id == networkId

    private fun isActiveBridgeSnapshot(walletAddress: String, octraNetworkId: String, evmNetworkId: String): Boolean =
        isActiveWallet(walletAddress) &&
            (activeRpcNetwork ?: runCatching { rpcRepository.activeNetwork() }.getOrNull())?.id == octraNetworkId &&
            (activeEvmNetwork ?: runCatching { evmRepository.activeNetwork() }.getOrNull())?.id == evmNetworkId

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

    val privacyBalanceText: String
        get() = privacyBalanceRaw?.let(::formatPrivacyAmount) ?: "--"

    val privacyOperationActive: Boolean
        get() = privacyOperation?.let { !it.isTerminal && it.stage != PrivacyOperationStage.Review } == true

    val privacyInitializing: Boolean
        get() {
            val activeWallet = wallet ?: return false
            return privacySyncing && privacyLoadedKey != "${activeWallet.address}|$currentRpcUrl"
        }

    val secureWindowRequired: Boolean
        get() = route in setOf(
            AppRoute.ImportWallet,
            AppRoute.SecuritySetup,
            AppRoute.Unlock,
            AppRoute.BackupSettings,
        ) || pendingDAppRequest != null ||
            submittedTransaction?.showDialog == true ||
            bridgeDialogVisible ||
            tradeDialogVisible ||
            privacyOperation?.dialogVisible == true ||
            (dashboardTab == DashboardTab.Privacy && privacyBalanceVisible)

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
        val privacyInProgress = privacyOperationActive
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
        unlockReturnTab = when {
            tradeInProgress -> DashboardTab.Trade
            privacyInProgress -> DashboardTab.Privacy
            else -> dashboardTab
        }
        privacyBalanceVisible = false
        privacyBalanceRaw = null
        privacyDecryptedCipher = null
        circlePassphrases.clear()
        circlePassphraseRequest = null
        if (!privacyInProgress) {
            clearPrivacyClaimSecrets()
            if (privacyOperation?.type == PrivacyOperationType.Claim) privacyOperation = null
            scope.launch { runCatching { privacyCrypto.clear() } }
        }
        sessionPin = null
        vault = null
        wallet = null
        clearPendingDAppRequest()
        submittedTransaction = null
        if (!bridgeInProgress) bridgeBusy = false
        if (!tradeInProgress) tradeBusy = false
        evmSending = false
        evmSendPollSerial++
        transactionPollingCount = 0
        transactionStatusPolling = false
        busyOperationCount = 0
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
            activateVault(loaded)
            sessionPin = null
            restoreAfterUnlock()
        }
    }

    fun unlockWithPin(pin: String) {
        runBusy {
            val loaded = withContext(Dispatchers.IO) { secureStore.loadVaultWithPin(pin) }
            activateVault(loaded)
            sessionPin = pin
            restoreAfterUnlock()
        }
    }

    private fun restoreAfterUnlock() {
        dashboardTab = unlockReturnTab
        route = unlockReturnRoute
        when (route) {
            AppRoute.Bridge,
            AppRoute.BridgeHistory -> {
                bridgeHistory = loadBridgeHistory()
            }
            AppRoute.Dashboard -> when (dashboardTab) {
                DashboardTab.Overview -> refreshDashboard()
                DashboardTab.Assets -> {
                    dApps = connectedDAppSessions()
                    refreshRpcStatus()
                    refreshIdentityProviders()
                    refreshWalletIdentityNames()
                    refreshBalance()
                    refreshOctPrice()
                    refreshAssets()
                    refreshEvmDashboard()
                }
                DashboardTab.Privacy -> refreshPrivacy(force = false)
                DashboardTab.Trade -> {
                    dApps = connectedDAppSessions()
                    refreshTrade()
                    refreshTradeChart()
                }
                DashboardTab.Explore -> {
                    dApps = connectedDAppSessions()
                }
            }
            AppRoute.PrivacyEncrypt,
            AppRoute.PrivacyDecrypt,
            AppRoute.PrivacyStealth -> refreshPrivacy(force = false)
            AppRoute.PrivacyClaim -> {
                refreshPrivacy(force = false)
                scanPrivacyClaims(showDialog = false)
            }
            AppRoute.Evm,
            AppRoute.EvmSend,
            AppRoute.EvmReceive,
            AppRoute.EvmActivity,
            AppRoute.EvmNetwork -> refreshEvmDashboard()
            else -> Unit
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
            activateVault(loaded)
            sessionPin = null
            onVerified()
        }
    }

    fun confirmSecurityWithPin(pin: String, onVerified: () -> Unit) {
        runBusy {
            val loaded = withContext(Dispatchers.IO) { secureStore.loadVaultWithPin(pin) }
            activateVault(loaded)
            sessionPin = pin
            onVerified()
        }
    }

    fun openSettings() {
        settingsDetailBackStack.clear()
        rpcNetworks = rpcRepository.rpcNetworks()
        activeRpcNetwork = rpcRepository.activeNetwork()
        dApps = connectedDAppSessions()
        addressBookContacts = addressBookRepository.contacts()
        refreshIdentityProviders()
        refreshWalletIdentityNames()
        refreshConsumerInsights()
        refreshPortfolioHoldings()
        refreshAssets()
        refreshEvmDashboard()
        domainGuardRules = consumerSettingsRepository.domainGuardRules()
        fundingProviders = consumerSettingsRepository.fundingProviders()
        route = AppRoute.Settings
    }

    fun closeSettings() {
        settingsDetailBackStack.clear()
        route = AppRoute.Dashboard
    }

    fun openAbout() {
        route = AppRoute.About
    }

    fun closeAbout() {
        route = AppRoute.Settings
    }

    fun openRpcManagement() {
        openRpcManagement(returnRoute = if (route == AppRoute.Settings) AppRoute.Settings else route)
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

    private fun openSettingsDetail(target: AppRoute) {
        if (route != target) settingsDetailBackStack += route
        route = target
    }

    fun openAddRpcNetwork() {
        route = AppRoute.RpcAddNetwork
    }

    fun closeAddRpcNetwork() {
        route = AppRoute.RpcManagement
    }

    fun openSecuritySettings() {
        openSettingsDetail(AppRoute.SecuritySettings)
    }

    fun openBackupSettings() {
        openSettingsDetail(AppRoute.BackupSettings)
    }

    fun openSecurityCenter() {
        dApps = connectedDAppSessions()
        addressBookContacts = addressBookRepository.contacts()
        refreshConsumerInsights()
        openSettingsDetail(AppRoute.SecurityCenter)
    }

    fun openActivityCenter() {
        if (route != AppRoute.ActivityCenter) activityCenterReturnRoute = route
        activityCenterLimit = InitialActivityLimit
        refreshUnifiedActivity()
        route = AppRoute.ActivityCenter
        refreshActivityCenter()
    }

    fun loadMoreActivityCenter() {
        activityCenterLimit = (activityCenterLimit + ActivityPageSize).coerceAtMost(MaxUnifiedActivityItems)
    }

    fun refreshActivityCenter() {
        if (activityCenterRefreshLoading) return
        activityCenterRefreshLoading = true
        refreshDashboard()
        refreshEvmHistory()
        if (currentBridgeNetworkWarning() == null) {
            refreshBridgeHistory(showUnsupportedWarning = false)
        }
        dApps = connectedDAppSessions()
        scope.launch {
            try {
                val deadline = System.currentTimeMillis() + ActivityCenterSyncTimeoutMs
                while (activityCenterDataLoading() && System.currentTimeMillis() < deadline) {
                    delay(150)
                }
                refreshUnifiedActivity()
            } finally {
                activityCenterRefreshLoading = false
            }
        }
    }

    private fun activityCenterDataLoading(): Boolean =
        historyLoading ||
            evmHistoryLoading ||
            (currentBridgeNetworkWarning() == null && bridgeHistoryLoading)

    fun closeActivityCenter() {
        route = when (activityCenterReturnRoute) {
            AppRoute.Dashboard -> AppRoute.Dashboard
            else -> AppRoute.Settings
        }
    }

    fun openPaymentRequest() {
        val activeWallet = wallet ?: return showError("Unlock wallet first.")
        val label = walletIdentityNames[activeWallet.address]
            ?: activeWallet.name.ifBlank { "poctra" }
        paymentRequestDraft = PaymentRequestDraft(
            address = activeWallet.address,
            label = label,
        )
        route = AppRoute.PaymentRequest
    }

    fun updatePaymentRequest(amount: String, asset: String, memo: String) {
        paymentRequestDraft = paymentRequestDraft?.copy(
            amount = amount.filter { it.isDigit() || it == '.' || it == ',' }.take(24),
            asset = asset.trim().uppercase().take(12).ifBlank { "OCT" },
            memo = memo.take(48),
        )
    }

    fun closePaymentRequest() {
        paymentRequestDraft = null
        route = AppRoute.Receive
    }

    fun openPortfolioAnalytics() {
        syncPortfolioHoldingsAfterDataChange()
        openSettingsDetail(AppRoute.PortfolioAnalytics)
        refreshPortfolioAnalytics()
    }

    fun refreshPortfolioAnalytics() {
        if (portfolioRefreshLoading) return
        val requestSerial = ++portfolioRefreshSerial
        portfolioRefreshLoading = true
        refreshDashboard()
        refreshEvmDashboard()
        scope.launch {
            try {
                val deadline = System.currentTimeMillis() + 15_000L
                while (requestSerial == portfolioRefreshSerial && portfolioDataLoading() && System.currentTimeMillis() < deadline) {
                    delay(150)
                }
                if (requestSerial != portfolioRefreshSerial) return@launch
                syncPortfolioHoldingsAfterDataChange()
            } finally {
                if (requestSerial == portfolioRefreshSerial) portfolioRefreshLoading = false
            }
        }
    }

    fun openPortfolioAssetHistory(holding: PortfolioHolding) {
        selectedPortfolioHolding = holding
        portfolioAssetActivity = buildPortfolioAssetActivity(holding)
        openSettingsDetail(AppRoute.PortfolioAssetHistory)
    }

    fun closePortfolioAssetHistory() {
        selectedPortfolioHolding = null
        portfolioAssetActivity = emptyList()
        closeSettingsDetail()
    }

    fun openPortfolioActivityExplorer(item: UnifiedActivityItem) {
        val url = item.explorerUrl ?: return showError("Explorer link is not available for this activity.")
        openDAppBrowser(item.title, url)
    }

    fun openApprovalDashboard() {
        refreshApprovalReviews()
        openSettingsDetail(AppRoute.ApprovalDashboard)
    }

    fun openRecoveryPlan() {
        openSettingsDetail(AppRoute.RecoveryPlan)
    }

    fun openFundingProviders() {
        fundingProviders = consumerSettingsRepository.fundingProviders()
        openSettingsDetail(AppRoute.FundingProviders)
    }

    fun openAppearanceSettings() {
        openSettingsDetail(AppRoute.AppearanceSettings)
    }

    fun openConnectedDApps() {
        dApps = connectedDAppSessions()
        refreshConsumerInsights()
        openSettingsDetail(AppRoute.ConnectedDApps)
    }

    fun openIdentityProviderSettings() {
        refreshIdentityProviders()
        openSettingsDetail(AppRoute.IdentityProviderSettings)
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
        route = if (settingsDetailBackStack.isEmpty()) {
            AppRoute.Settings
        } else {
            settingsDetailBackStack.removeAt(settingsDetailBackStack.lastIndex)
        }
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

    fun recentRecipientSuggestions(): List<AddressBookContact> {
        val active = wallet?.address ?: return emptyList()
        val saved = addressBookContacts.map { it.address.lowercase() }.toSet()
        return recentTransactions
            .mapNotNull { tx ->
                val candidate = when {
                    tx.from.equals(active, ignoreCase = true) -> tx.to
                    tx.to.equals(active, ignoreCase = true) -> tx.from
                    else -> null
                }?.trim().orEmpty()
                if (!isOctraAddress(candidate)) return@mapNotNull null
                if (candidate.equals(active, ignoreCase = true) || candidate.lowercase() in saved) return@mapNotNull null
                AddressBookContact(
                    id = "suggestion-${candidate.hashCode()}",
                    label = identityNamesByAddress[candidate.lowercase()] ?: "Recent ${shortAddress(candidate)}",
                    address = candidate,
                    note = "Recent activity",
                    identityName = identityNamesByAddress[candidate.lowercase()],
                    createdAt = tx.timestamp.toLong(),
                )
            }
            .distinctBy { it.address.lowercase() }
            .take(5)
    }

    fun saveRecentRecipient(contact: AddressBookContact) {
        addressBookContacts = addressBookRepository.save(
            contact.copy(
                id = "contact-${System.currentTimeMillis()}",
                label = contact.label.ifBlank { "Recent ${shortAddress(contact.address)}" },
            ),
        )
        showMessage("Recent recipient saved.")
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
        refreshWalletIdentityNames()
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
        bridgeHistoryLimit = InitialActivityLimit
        bridgeHistory = loadBridgeHistory()
        route = AppRoute.BridgeHistory
        if (bridgeHistory.isEmpty()) refreshBridgeHistory()
    }

    fun loadMoreBridgeHistory() {
        bridgeHistoryLimit = (bridgeHistoryLimit + ActivityPageSize).coerceAtMost(MaxBridgeHistoryItems)
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
        refreshBridgeHistory(showUnsupportedWarning = true)
    }

    private fun refreshBridgeHistory(showUnsupportedWarning: Boolean) {
        val activeWallet = wallet ?: return
        if (bridgeHistoryLoading) return
        val requestSerial = ++bridgeHistoryRequestSerial
        configureBridgeRepositoryOrWarn()?.let { warning ->
            if (showUnsupportedWarning) showError(warning)
            bridgeError = warning
            return
        }
        val octraNetwork = activeRpcNetwork ?: rpcRepository.activeNetwork()
        val evmNetwork = activeEvmNetwork ?: evmRepository.activeNetwork()
        val walletAddress = activeWallet.address
        bridgeHistoryLoading = true
        scope.launch {
            try {
                val chainItems = withContext(Dispatchers.IO) {
                    bridgeRepository.fetchOnChainHistory(activeWallet)
                }
                if (requestSerial != bridgeHistoryRequestSerial || !isActiveBridgeSnapshot(walletAddress, octraNetwork.id, evmNetwork.id)) return@launch
                val merged = mergeBridgeHistory(loadBridgeHistory(), chainItems)
                bridgeHistory = merged
                persistBridgeHistory(merged)
                bridgeError = null
            } catch (exception: Exception) {
                if (requestSerial != bridgeHistoryRequestSerial) return@launch
                showError(exception.message ?: "Bridge history refresh failed.")
            } finally {
                if (requestSerial == bridgeHistoryRequestSerial) bridgeHistoryLoading = false
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
        val requestSerial = ++bridgeBalanceRequestSerial
        configureBridgeRepositoryOrWarn()?.let { warning ->
            bridgeError = warning
            bridgeStatus = BridgeStatus(error = warning)
            bridgeLoading = false
            return
        }
        val octraNetwork = activeRpcNetwork ?: rpcRepository.activeNetwork()
        val evmNetwork = activeEvmNetwork ?: evmRepository.activeNetwork()
        val walletAddress = activeWallet.address
        bridgeLoading = true
        scope.launch {
            try {
                val (nextBalances, nextStatus) = coroutineScope {
                    val balances = async(Dispatchers.IO) {
                        bridgeRepository.fetchBalances(activeWallet)
                    }
                    val status = async(Dispatchers.IO) {
                        bridgeRepository.fetchStatus(activeWallet)
                    }
                    balances.await() to status.await()
                }
                if (requestSerial != bridgeBalanceRequestSerial || !isActiveBridgeSnapshot(walletAddress, octraNetwork.id, evmNetwork.id)) return@launch
                bridgeBalances = nextBalances
                bridgeStatus = nextStatus
                bridgeError = null
            } catch (exception: Exception) {
                if (requestSerial != bridgeBalanceRequestSerial) return@launch
                bridgeError = exception.message ?: "Bridge balances failed to sync."
            } finally {
                if (requestSerial == bridgeBalanceRequestSerial) bridgeLoading = false
            }
        }
    }

    fun refreshBridgeData() {
        refreshRecommendedFee("call")
        refreshBridgeBalances()
    }

    fun refreshBridgeReviewEstimates(amountText: String) {
        val activeWallet = wallet ?: return
        val requestSerial = ++bridgeReviewRequestSerial
        refreshRecommendedFee("call")
        configureBridgeRepositoryOrWarn()?.let {
            bridgeEvmGasEstimate = null
            bridgeGasLoading = false
            return
        }
        val octraNetwork = activeRpcNetwork ?: rpcRepository.activeNetwork()
        val evmNetwork = activeEvmNetwork ?: evmRepository.activeNetwork()
        val walletAddress = activeWallet.address
        val amountRaw = runCatching { bridgeRepository.parseAmount(amountText) }
            .getOrDefault(BigInteger.ONE)
            .takeIf { it > BigInteger.ZERO }
            ?: BigInteger.ONE
        val direction = bridgeDirection
        bridgeGasLoading = true
        scope.launch {
            try {
                val estimate = withContext(Dispatchers.IO) {
                    bridgeRepository.estimateBridgeEvmGas(activeWallet, direction, amountRaw)
                }
                if (requestSerial != bridgeReviewRequestSerial || !isActiveBridgeSnapshot(walletAddress, octraNetwork.id, evmNetwork.id)) return@launch
                bridgeEvmGasEstimate = estimate
            } catch (_: Exception) {
                if (requestSerial != bridgeReviewRequestSerial) return@launch
                bridgeEvmGasEstimate = null
            } finally {
                if (requestSerial == bridgeReviewRequestSerial) bridgeGasLoading = false
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
            }.sortedBridgeHistory()
        }.getOrDefault(emptyList())
    }

    private fun persistBridgeHistory(items: List<BridgeHistoryItem>) {
        val array = JSONArray()
        items.sortedBridgeHistory().forEach { array.put(it.toJson()) }
        bridgePrefs.edit().putString(KeyBridgeHistory, array.toString()).apply()
    }

    private fun upsertBridgeHistory(item: BridgeHistoryItem) {
        val updated = item.copy(updatedAt = System.currentTimeMillis())
        val next = (listOf(updated) + bridgeHistory.filterNot { it.id == item.id }).sortedBridgeHistory()
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
        val next = (listOf(updated) + source.filterNot { it.id == itemId }).sortedBridgeHistory()
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
        val mergedChain = chainItems.mapNotNull { chain ->
            val local = localItems.firstOrNull { local ->
                (chain.octraTxHash != null && local.octraTxHash?.equals(chain.octraTxHash, ignoreCase = true) == true) ||
                    (chain.evmTxHash != null && local.evmTxHash?.equals(chain.evmTxHash, ignoreCase = true) == true)
            }
            if (chain.status == "unknown") {
                if (local != null) consumedLocalIds.add(local.id)
                return@mapNotNull local?.takeIf { it.status != "unknown" }
            }
            if (local != null) consumedLocalIds.add(local.id)
            mergeBridgeHistoryItem(local, chain)
        }
        return (mergedChain + localItems.filterNot { it.id in consumedLocalIds })
            .distinctBy { it.id }
            .sortedBridgeHistory()
    }

    private fun List<BridgeHistoryItem>.sortedBridgeHistory(): List<BridgeHistoryItem> =
        sortedWith(
            compareByDescending<BridgeHistoryItem> { maxOf(it.updatedAt, it.createdAt) }
                .thenByDescending { it.createdAt }
                .thenByDescending { it.id },
        ).take(MaxBridgeHistoryItems)

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
        val requestSerial = ++tradeBalanceRequestSerial
        configureTradeRepositoryOrWarn("syncing trade data")?.let { warning ->
            tradeBalances = TradeBalances()
            tradeQuote = null
            tradeQuoteError = warning
            tradeQuoteLoading = false
            tradeLoading = false
            return
        }
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        val walletAddress = activeWallet.address
        tradeLoading = true
        scope.launch {
            try {
                val balances = withContext(Dispatchers.IO) {
                    tradeRepository.fetchBalances(activeWallet)
                }
                if (requestSerial != tradeBalanceRequestSerial || !isActiveEvmSnapshot(walletAddress, network.id)) return@launch
                tradeBalances = balances
                tradeQuoteError = null
            } catch (exception: Exception) {
                if (requestSerial != tradeBalanceRequestSerial) return@launch
                tradeQuoteError = exception.message ?: "Trade balances failed to sync."
            } finally {
                if (requestSerial == tradeBalanceRequestSerial) tradeLoading = false
            }
        }
    }

    fun refreshTradeChart(timeframe: TradeChartTimeframe = tradeChartTimeframe) {
        refreshTradeChartInternal(timeframe = timeframe, forceNetwork = false, userInitiated = false)
    }

    fun forceRefreshTradeChart() {
        refreshTradeChartInternal(timeframe = tradeChartTimeframe, forceNetwork = true, userInitiated = true)
    }

    private fun refreshTradeChartInternal(
        timeframe: TradeChartTimeframe,
        forceNetwork: Boolean,
        userInitiated: Boolean,
    ) {
        val network = activeEvmNetwork ?: runCatching { evmRepository.activeNetwork() }.getOrNull()
        val requestNetworkId = network?.id
        tradeChartTimeframe = timeframe
        if (network?.chainId != 1L) {
            tradeCandles = emptyList()
            tradeLastPrice = "--"
            tradeChartError = "wOCT chart is available on Ethereum Mainnet."
            tradeChartLoading = false
            return
        }

        val cacheKey = tradeChartCacheKey(requestNetworkId, timeframe)
        val cached = tradeChartCacheEntry(requestNetworkId, timeframe)
        cached?.let { applyTradeChartCache(it) } ?: run {
            tradeCandles = emptyList()
            tradeLastPrice = "--"
        }
        tradeChartError = null

        val now = System.currentTimeMillis()
        val cacheIsFresh = cached != null && now - cached.fetchedAtMs < timeframe.tradeChartCacheTtlMs()
        if (!forceNetwork && cacheIsFresh) {
            tradeChartLoading = false
            return
        }

        tradeChartInFlightKey?.let { inFlight ->
            if (inFlight == cacheKey) {
                tradeChartLoading = cached == null
                return
            }
            tradeChartRequestSerial++
        }

        if (tradeChartRefreshCoolingDown) {
            tradeChartError = if (cached != null) {
                "Using cached chart. Refresh is cooling down."
            } else {
                "Chart refresh is cooling down. Try again shortly."
            }
            tradeChartLoading = false
            return
        }

        val requestId = ++tradeChartRequestSerial
        tradeChartInFlightKey = cacheKey
        if (userInitiated) startTradeChartCooldown(TradeChartManualCooldownMs)
        tradeChartLoading = true
        scope.launch {
            try {
                val candles = withContext(Dispatchers.IO) {
                    tradeRepository.fetchWoctCandles(timeframe)
                }
                if (requestId != tradeChartRequestSerial || !isActiveEvmNetworkSnapshot(requestNetworkId)) return@launch
                val entry = TradeChartCacheEntry(candles = candles, fetchedAtMs = System.currentTimeMillis())
                tradeChartCache[cacheKey] = entry
                persistTradeChartCache(requestNetworkId, timeframe, entry)
                applyTradeChartCache(entry)
                tradeChartError = null
            } catch (exception: Exception) {
                if (requestId != tradeChartRequestSerial || !isActiveEvmNetworkSnapshot(requestNetworkId)) return@launch
                val fallback = tradeChartCacheEntry(requestNetworkId, timeframe)
                    ?: tradeCandles.takeIf { it.isNotEmpty() }?.let {
                        TradeChartCacheEntry(it, fetchedAtMs = 0L)
                    }
                if (fallback != null) {
                    applyTradeChartCache(fallback)
                } else {
                    tradeCandles = emptyList()
                    tradeLastPrice = "--"
                }
                if (isTradeChartRateLimited(exception)) startTradeChartCooldown(TradeChartRateLimitCooldownMs)
                tradeChartError = tradeChartErrorMessage(exception, fallback != null)
            } finally {
                if (tradeChartInFlightKey == cacheKey) tradeChartInFlightKey = null
                if (requestId == tradeChartRequestSerial && isActiveEvmNetworkSnapshot(requestNetworkId)) tradeChartLoading = false
            }
        }
    }

    private fun tradeChartCacheEntry(
        networkId: String?,
        timeframe: TradeChartTimeframe,
    ): TradeChartCacheEntry? {
        val key = tradeChartCacheKey(networkId, timeframe)
        return tradeChartCache[key] ?: loadPersistedTradeChartCache(networkId, timeframe)?.also {
            tradeChartCache[key] = it
        }
    }

    private fun applyTradeChartCache(entry: TradeChartCacheEntry) {
        tradeCandles = entry.candles
        tradeLastPrice = entry.candles.lastOrNull()?.close?.let { "$${"%.6f".format(it)}" } ?: "--"
    }

    private fun persistTradeChartCache(
        networkId: String?,
        timeframe: TradeChartTimeframe,
        entry: TradeChartCacheEntry,
    ) {
        val candles = JSONArray()
        entry.candles.forEach { candle ->
            candles.put(
                JSONArray()
                    .put(candle.timestamp)
                    .put(candle.open)
                    .put(candle.high)
                    .put(candle.low)
                    .put(candle.close),
            )
        }
        val payload = JSONObject()
            .put("fetchedAtMs", entry.fetchedAtMs)
            .put("candles", candles)
        tradePrefs.edit()
            .putString(tradeChartCacheKey(networkId, timeframe), payload.toString())
            .apply()
    }

    private fun loadPersistedTradeChartCache(
        networkId: String?,
        timeframe: TradeChartTimeframe,
    ): TradeChartCacheEntry? {
        val raw = tradePrefs.getString(tradeChartCacheKey(networkId, timeframe), null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            val candles = json.optJSONArray("candles") ?: JSONArray()
            val parsed = buildList {
                for (index in 0 until candles.length()) {
                    val row = candles.optJSONArray(index) ?: continue
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
            TradeChartCacheEntry(
                candles = parsed.filter { it.open > 0.0 && it.high > 0.0 && it.low > 0.0 && it.close > 0.0 },
                fetchedAtMs = json.optLong("fetchedAtMs", 0L),
            ).takeIf { it.candles.isNotEmpty() }
        }.getOrNull()
    }

    private fun tradeChartCacheKey(networkId: String?, timeframe: TradeChartTimeframe): String =
        "$KeyTradeChartCachePrefix${networkId.orEmpty().ifBlank { "ethereum" }}_${timeframe.name}"

    private fun TradeChartTimeframe.tradeChartCacheTtlMs(): Long = when (this) {
        TradeChartTimeframe.H1 -> 2 * 60_000L
        TradeChartTimeframe.H4 -> 10 * 60_000L
        TradeChartTimeframe.D1 -> 60 * 60_000L
        TradeChartTimeframe.W1 -> 6 * 60 * 60_000L
    }

    private fun startTradeChartCooldown(durationMs: Long) {
        val serial = ++tradeChartCooldownSerial
        tradeChartRefreshCoolingDown = true
        scope.launch {
            delay(durationMs)
            if (serial == tradeChartCooldownSerial) tradeChartRefreshCoolingDown = false
        }
    }

    private fun isTradeChartRateLimited(exception: Exception): Boolean =
        exception.message.orEmpty().lowercase().contains("429")

    private fun tradeChartErrorMessage(exception: Exception, usingCache: Boolean): String {
        val rateLimited = isTradeChartRateLimited(exception)
        return when {
            rateLimited && usingCache -> "Chart provider is rate limited. Using cached chart."
            rateLimited -> "Chart provider is rate limited. Try again shortly."
            usingCache -> "Live chart refresh failed. Using cached chart."
            else -> exception.message ?: "wOCT chart failed to load."
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
        evmHistoryVisibleLimit = InitialActivityLimit
        refreshEvmHistory()
        route = AppRoute.EvmActivity
    }

    fun loadMoreEvmActivity() {
        evmHistoryVisibleLimit = (evmHistoryVisibleLimit + ActivityPageSize).coerceAtMost(MaxEvmActivityItems)
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

    fun refreshPrivacy(force: Boolean = true) {
        val activeWallet = wallet ?: return
        val requestRpcUrl = currentRpcUrl
        val snapshotKey = "${activeWallet.address}|$requestRpcUrl"
        val now = System.currentTimeMillis()
        if (!force && privacyLoadedKey == snapshotKey && now - privacyLoadedAt < PrivacyCacheMillis) return
        if (privacyLoadedKey != null && privacyLoadedKey != snapshotKey) {
            privacyEncryptedBalance = null
            privacyBalanceRaw = null
            privacyBalanceVisible = false
            privacyDecryptedCipher = null
        }
        if (force) {
            privacyBalanceVisible = false
        }

        val serial = ++privacySyncSerial
        privacySyncing = true
        privacySyncSteps = initialPrivacySyncSteps().mapIndexed { index, step ->
            if (index == 0) step.copy(status = PrivacySyncStatus.Running) else step
        }
        scope.launch {
            try {
                coroutineScope {
                    privacySupported = privacyCrypto.isSupported()
                    updatePrivacySyncStep(
                        index = 0,
                        status = if (privacySupported) PrivacySyncStatus.Complete else PrivacySyncStatus.Failed,
                        detail = if (privacySupported) "Privacy engine ready" else "Hardware AES is unavailable on this device",
                    )
                    delay(120)
                    val balanceDeferred = async {
                        runCatching { rpcRepository.fetchEncryptedBalance(activeWallet, requestRpcUrl) }
                    }
                    val historyDeferred = async {
                        runCatching {
                            rpcRepository.fetchPrivacyHistory(
                                address = activeWallet.address,
                                limit = InitialPrivacyHistoryLimit,
                                url = requestRpcUrl,
                            )
                        }
                    }

                    updatePrivacySyncStep(1, PrivacySyncStatus.Running)
                    val balanceResult = balanceDeferred.await()
                    if (serial == privacySyncSerial && isActiveRpcSnapshot(activeWallet.address, requestRpcUrl)) {
                        balanceResult.onSuccess { balance ->
                            privacyEncryptedBalance = balance
                            if (balance == null) {
                                privacyBalanceRaw = 0L
                                privacyBalanceVisible = true
                                privacyDecryptedCipher = null
                            } else if (privacyDecryptedCipher != balance.cipher) {
                                privacyBalanceRaw = null
                                privacyBalanceVisible = false
                                privacyDecryptedCipher = null
                            }
                            updatePrivacySyncStep(
                                1,
                                PrivacySyncStatus.Complete,
                                if (balance == null) "No encrypted funds" else "Encrypted balance loaded",
                            )
                        }.onFailure { failure ->
                            updatePrivacySyncStep(1, PrivacySyncStatus.Failed, failure.message ?: "Balance unavailable")
                        }
                    }

                    updatePrivacySyncStep(2, PrivacySyncStatus.Running)
                    val historyResult = historyDeferred.await()
                    if (serial == privacySyncSerial && isActiveRpcSnapshot(activeWallet.address, requestRpcUrl)) {
                        historyResult.onSuccess { history ->
                            privacyHistory = history
                            updatePrivacySyncStep(
                                2,
                                PrivacySyncStatus.Complete,
                                if (history.isEmpty()) "No privacy activity" else "${history.size} activities loaded",
                            )
                        }.onFailure { failure ->
                            updatePrivacySyncStep(2, PrivacySyncStatus.Failed, failure.message ?: "History unavailable")
                        }
                    }
                }
                if (serial == privacySyncSerial && isActiveRpcSnapshot(activeWallet.address, requestRpcUrl)) {
                    privacyLoadedKey = snapshotKey
                    privacyLoadedAt = System.currentTimeMillis()
                }
            } finally {
                if (serial == privacySyncSerial) privacySyncing = false
            }
        }
    }

    fun togglePrivacyBalance() {
        if (privacyBalanceVisible) {
            privacyBalanceVisible = false
            return
        }
        val activeWallet = wallet ?: return showError("Unlock wallet first.")
        val cipher = privacyEncryptedBalance?.cipher
        if (cipher.isNullOrBlank()) {
            privacyBalanceRaw = 0L
            privacyBalanceVisible = true
            privacyDecryptedCipher = null
            return
        }
        if (privacyDecryptedCipher == cipher && privacyBalanceRaw != null) {
            privacyBalanceVisible = true
            return
        }
        if (!privacySupported) return showError("Privacy is not supported by this device.")
        if (privacyOperationActive || privacyBalanceDecrypting) {
            return showError("Wait for the active Privacy process to finish or resume it first.")
        }
        privacyBalanceDecrypting = true
        scope.launch {
            try {
                privacyBalanceRaw = privacyCrypto.decryptBalance(activeWallet, cipher)
                privacyDecryptedCipher = cipher
                privacyBalanceVisible = true
            } catch (exception: Exception) {
                showError(exception.message ?: "Unable to decrypt encrypted balance.")
            } finally {
                runCatching { privacyCrypto.clear() }
                privacyBalanceDecrypting = false
            }
        }
    }

    fun openPrivacyEncrypt() {
        openPrivacyAction(AppRoute.PrivacyEncrypt, PrivacyOperationType.Encrypt)
    }

    fun openPrivacyDecrypt() {
        openPrivacyAction(AppRoute.PrivacyDecrypt, PrivacyOperationType.Decrypt)
    }

    fun openPrivacyStealth() {
        openPrivacyAction(AppRoute.PrivacyStealth, PrivacyOperationType.Stealth)
    }

    fun openPrivacyClaim() {
        if (!openPrivacyAction(AppRoute.PrivacyClaim, PrivacyOperationType.Claim)) return
        scanPrivacyClaims(showDialog = false)
    }

    private fun openPrivacyAction(target: AppRoute, type: PrivacyOperationType): Boolean {
        if (!privacySupported) {
            showError("Privacy requires a 64-bit device with hardware AES support.")
            return false
        }
        if (privacyOperationActive || privacyBalanceDecrypting) {
            showError("A Privacy process is already running. Resume or finish it first.")
            return false
        }
        if (type in setOf(PrivacyOperationType.Decrypt, PrivacyOperationType.Stealth) && privacyEncryptedBalance?.cipher.isNullOrBlank()) {
            showError("No encrypted balance is available on the active network.")
            return false
        }
        privacyOperation?.let { current ->
            if (current.stage == PrivacyOperationStage.Review) clearPrivacyClaimSecrets()
            if (current.isTerminal || current.stage == PrivacyOperationStage.Review) privacyOperation = null
        }
        dashboardTab = DashboardTab.Privacy
        route = target
        refreshOctPrice()
        refreshPrivacyFee(type)
        return true
    }

    fun refreshPrivacyFee(type: PrivacyOperationType) {
        privacyFeeLoading = true
        val requestRpcUrl = currentRpcUrl
        val feeSerial = ++privacyFeeSerial
        scope.launch {
            try {
                val recommended = rpcRepository.fetchRecommendedFee(type.name.lowercase(), requestRpcUrl)
                if (feeSerial == privacyFeeSerial) privacyRecommendedFeeOu = recommended
            } finally {
                if (feeSerial == privacyFeeSerial) privacyFeeLoading = false
            }
        }
    }

    fun privacyMaxAmount(type: PrivacyOperationType, feeOu: Long): String {
        val maximumRaw = when (type) {
            PrivacyOperationType.Encrypt -> {
                val publicRaw = BigDecimal.valueOf(availableBalance)
                    .movePointRight(6)
                    .setScale(0, RoundingMode.DOWN)
                    .toLong()
                (publicRaw - feeOu.coerceAtLeast(0L)).coerceAtLeast(0L)
            }
            PrivacyOperationType.Decrypt,
            PrivacyOperationType.Stealth -> privacyBalanceRaw?.coerceAtLeast(0L) ?: return ""
            PrivacyOperationType.Claim -> return ""
        }
        return formatPrivacyAmount(maximumRaw)
    }

    fun privacyFeeAffordable(feeOu: Long): Boolean = hasPublicFeeBalance(feeOu)

    fun closePrivacyAction() {
        if (privacyOperationActive) {
            minimizePrivacyOperation()
            dashboardTab = DashboardTab.Privacy
            route = AppRoute.Dashboard
            return
        }
        if (route == AppRoute.PrivacyClaim) {
            clearPrivacyClaimSecrets()
            if (privacyOperation?.type == PrivacyOperationType.Claim) privacyOperation = null
        }
        privacyFeeSerial++
        privacyFeeLoading = false
        dashboardTab = DashboardTab.Privacy
        route = AppRoute.Dashboard
    }

    fun startPrivacyEncrypt(amountText: String, feeText: String) {
        val activeWallet = wallet ?: return showError("Unlock wallet first.")
        if (privacyOperationActive) return showError("A Privacy process is already running.")
        val amountRaw = parsePrivacyAmount(amountText)
            ?: return showError("Enter an OCT amount with no more than 6 decimal places.")
        val feeOu = feeText.filter(Char::isDigit).toLongOrNull()?.takeIf { it > 0L }
            ?: return showError("Enter a valid network fee.")
        val availableRaw = BigDecimal.valueOf(availableBalance)
            .movePointRight(6)
            .setScale(0, RoundingMode.DOWN)
            .toLong()
        if (feeOu >= availableRaw || amountRaw > availableRaw - feeOu) {
            return showError("Amount and network fee exceed the available OCT balance.")
        }

        closePrivacyAction()
        val requestRpcUrl = currentRpcUrl
        val operationId = ++privacyOperationSerial
        privacyOperation = PrivacyOperationState(
            id = operationId,
            type = PrivacyOperationType.Encrypt,
            stage = PrivacyOperationStage.Preparing,
            amountOct = formatPrivacyAmount(amountRaw),
            message = "Preparing local encryption...",
        )
        scope.launch {
            try {
                val identity = privacyCrypto.initialize(activeWallet)
                ensurePrivacyOperationCurrent(operationId)
                updatePrivacyOperation(operationId, PrivacyOperationStage.Registering, "Checking Privacy key registration...")
                rpcRepository.ensurePvacRegistered(activeWallet, identity.publicKey, identity.aesKat, requestRpcUrl)
                ensurePrivacyOperationCurrent(operationId)
                updatePrivacyOperation(operationId, PrivacyOperationStage.Preparing, "Generating encrypted amount and proof locally...")
                val payload = privacyCrypto.createEncryptPayload(activeWallet, amountRaw)
                ensurePrivacyOperationCurrent(operationId)
                val encryptedData = JSONObject()
                    .put("cipher", payload.cipher)
                    .put("amount_commitment", payload.amountCommitment)
                    .put("zero_proof", payload.zeroProof)
                    .put("blinding", payload.blinding)
                    .toWebcliJson()
                updatePrivacyOperation(operationId, PrivacyOperationStage.Submitting, "Submitting Encrypt transaction...")
                val result = rpcRepository.submitPrivacyTransaction(
                    wallet = activeWallet,
                    amountRaw = amountRaw.toString(),
                    feeOu = feeOu,
                    opType = "encrypt",
                    encryptedData = encryptedData,
                    url = requestRpcUrl,
                )
                ensurePrivacyOperationCurrent(operationId)
                if (!result.success || result.hash.isNullOrBlank()) {
                    throw IllegalStateException(result.error ?: "Encrypt transaction was rejected.")
                }
                updatePrivacyOperation(
                    operationId,
                    PrivacyOperationStage.Accepted,
                    "Encrypt accepted. Waiting for blockchain confirmation...",
                    result.hash,
                )
                addPrivacyPending(result.hash, activeWallet, amountRaw, "encrypt")
                pollPrivacyTransaction(operationId, result.hash, requestRpcUrl)
            } catch (exception: Exception) {
                failPrivacyOperation(operationId, exception.message ?: "Encrypt failed.")
            } finally {
                runCatching { privacyCrypto.clear() }
            }
        }
    }

    fun startPrivacyDecrypt(amountText: String, feeText: String) {
        val activeWallet = wallet ?: return showError("Unlock wallet first.")
        if (privacyOperationActive) return showError("A Privacy process is already running.")
        val cipher = privacyEncryptedBalance?.cipher
            ?: return showError("No encrypted balance is available on the active network.")
        val amountRaw = parsePrivacyAmount(amountText)
            ?: return showError("Enter an OCT amount with no more than 6 decimal places.")
        val feeOu = feeText.filter(Char::isDigit).toLongOrNull()?.takeIf { it > 0L }
            ?: return showError("Enter a valid network fee.")
        if (!hasPublicFeeBalance(feeOu)) return showError("Public OCT balance is insufficient for the network fee.")

        closePrivacyAction()
        val requestRpcUrl = currentRpcUrl
        val operationId = ++privacyOperationSerial
        privacyOperation = PrivacyOperationState(
            id = operationId,
            type = PrivacyOperationType.Decrypt,
            stage = PrivacyOperationStage.Preparing,
            amountOct = formatPrivacyAmount(amountRaw),
            message = "Reading encrypted balance locally...",
        )
        scope.launch {
            try {
                val identity = privacyCrypto.initialize(activeWallet)
                ensurePrivacyOperationCurrent(operationId)
                updatePrivacyOperation(operationId, PrivacyOperationStage.Registering, "Checking Privacy key registration...")
                rpcRepository.ensurePvacRegistered(activeWallet, identity.publicKey, identity.aesKat, requestRpcUrl)
                ensurePrivacyOperationCurrent(operationId)
                val currentBalance = privacyCrypto.decryptBalance(activeWallet, cipher)
                require(amountRaw <= currentBalance) {
                    "Insufficient encrypted balance. Available ${formatPrivacyAmount(currentBalance)} OCT."
                }
                privacyBalanceRaw = currentBalance
                privacyDecryptedCipher = cipher
                privacyBalanceVisible = true
                updatePrivacyOperation(
                    operationId,
                    PrivacyOperationStage.Preparing,
                    "Generating aggregated balance proof. This may take several minutes...",
                )
                val payload = privacyCrypto.createDecryptPayload(
                    wallet = activeWallet,
                    currentCipher = cipher,
                    currentBalanceRaw = currentBalance,
                    amountRaw = amountRaw,
                )
                ensurePrivacyOperationCurrent(operationId)
                val encryptedData = JSONObject()
                    .put("cipher", payload.cipher)
                    .put("amount_commitment", payload.amountCommitment)
                    .put("zero_proof", payload.zeroProof)
                    .put("blinding", payload.blinding)
                    .put("range_proof_balance", payload.rangeProofBalance)
                    .toWebcliJson()
                updatePrivacyOperation(operationId, PrivacyOperationStage.Submitting, "Submitting Decrypt transaction...")
                val result = rpcRepository.submitPrivacyTransaction(
                    wallet = activeWallet,
                    amountRaw = amountRaw.toString(),
                    feeOu = feeOu,
                    opType = "decrypt",
                    encryptedData = encryptedData,
                    url = requestRpcUrl,
                )
                ensurePrivacyOperationCurrent(operationId)
                if (!result.success || result.hash.isNullOrBlank()) {
                    throw IllegalStateException(result.error ?: "Decrypt transaction was rejected.")
                }
                updatePrivacyOperation(
                    operationId,
                    PrivacyOperationStage.Accepted,
                    "Decrypt accepted. Waiting for blockchain confirmation...",
                    result.hash,
                )
                addPrivacyPending(result.hash, activeWallet, amountRaw, "decrypt")
                pollPrivacyTransaction(operationId, result.hash, requestRpcUrl)
            } catch (exception: Exception) {
                failPrivacyOperation(operationId, exception.message ?: "Decrypt failed.")
            } finally {
                runCatching { privacyCrypto.clear() }
            }
        }
    }

    fun startPrivacyStealth(recipientInput: String, amountText: String, feeText: String) {
        val activeWallet = wallet ?: return showError("Unlock wallet first.")
        if (privacyOperationActive) return showError("A Privacy process is already running.")
        if (!isOctraRecipientInput(recipientInput)) {
            return showError("Enter foo.oct or a valid Octra recipient address.")
        }
        val cipher = privacyEncryptedBalance?.cipher
            ?: return showError("No encrypted balance is available on the active network.")
        val amountRaw = parsePrivacyAmount(amountText)
            ?: return showError("Enter an OCT amount with no more than 6 decimal places.")
        val feeOu = feeText.filter(Char::isDigit).toLongOrNull()?.takeIf { it > 0L }
            ?: return showError("Enter a valid network fee.")
        if (!hasPublicFeeBalance(feeOu)) return showError("Public OCT balance is insufficient for the network fee.")

        closePrivacyAction()
        val requestRpcUrl = currentRpcUrl
        val operationId = ++privacyOperationSerial
        privacyOperation = PrivacyOperationState(
            id = operationId,
            type = PrivacyOperationType.Stealth,
            stage = PrivacyOperationStage.Preparing,
            amountOct = formatPrivacyAmount(amountRaw),
            recipient = recipientInput.trim(),
            message = "Resolving recipient on the active Octra network...",
        )
        scope.launch {
            var envelope: com.poctra.wallet.privacy.PrivacyStealthEnvelope? = null
            try {
                val recipientAddress = resolveOctraRecipient(recipientInput, activeWallet)
                require(!recipientAddress.equals(activeWallet.address, ignoreCase = true)) {
                    "Use another wallet as the Stealth recipient."
                }
                privacyOperation = privacyOperation?.takeIf { it.id == operationId }?.copy(recipient = recipientAddress)
                    ?: throw IllegalStateException("Privacy process was cancelled.")
                val recipientPublicKey = rpcRepository.fetchOctraPublicKey(recipientAddress, requestRpcUrl)
                ensurePrivacyOperationCurrent(operationId)
                val identity = privacyCrypto.initialize(activeWallet)
                updatePrivacyOperation(operationId, PrivacyOperationStage.Registering, "Checking Privacy key registration...")
                rpcRepository.ensurePvacRegistered(activeWallet, identity.publicKey, identity.aesKat, requestRpcUrl)
                ensurePrivacyOperationCurrent(operationId)
                val currentBalance = privacyCrypto.decryptBalance(activeWallet, cipher)
                require(amountRaw <= currentBalance) {
                    "Insufficient encrypted balance. Available ${formatPrivacyAmount(currentBalance)} OCT."
                }
                privacyBalanceRaw = currentBalance
                privacyDecryptedCipher = cipher
                privacyBalanceVisible = true
                envelope = privacyStealthSender.createEnvelope(recipientAddress, recipientPublicKey, amountRaw)
                updatePrivacyOperation(
                    operationId,
                    PrivacyOperationStage.Preparing,
                    "Generating two Stealth range proofs. This may take several minutes...",
                )
                val proof = privacyCrypto.createStealthProofPayload(
                    wallet = activeWallet,
                    currentCipher = cipher,
                    currentBalanceRaw = currentBalance,
                    amountRaw = amountRaw,
                    blinding = envelope.blinding,
                )
                ensurePrivacyOperationCurrent(operationId)
                val encryptedData = JSONObject()
                    .put("version", 5)
                    .put("delta_cipher", proof.deltaCipher)
                    .put("commitment", proof.commitment)
                    .put("range_proof_delta", proof.rangeProofDelta)
                    .put("range_proof_balance", proof.rangeProofBalance)
                    .put("eph_pub", android.util.Base64.encodeToString(envelope.ephemeralPublicKey, android.util.Base64.NO_WRAP))
                    .put("stealth_tag", envelope.stealthTag.toHex())
                    .put("enc_amount", envelope.encryptedAmount)
                    .put("claim_pub", envelope.claimPublicKey.toHex())
                    .put("amount_commitment", proof.amountCommitment)
                    .put("send_zero_proof", proof.zeroProof)
                    .toWebcliJson()
                updatePrivacyOperation(operationId, PrivacyOperationStage.Submitting, "Submitting Stealth transaction...")
                val result = rpcRepository.submitPrivacyTransaction(
                    wallet = activeWallet,
                    amountRaw = "0",
                    feeOu = feeOu,
                    opType = "stealth",
                    encryptedData = encryptedData,
                    recipientAddress = "stealth",
                    url = requestRpcUrl,
                )
                ensurePrivacyOperationCurrent(operationId)
                if (!result.success || result.hash.isNullOrBlank()) {
                    throw IllegalStateException(result.error ?: "Stealth transaction was rejected.")
                }
                updatePrivacyOperation(
                    operationId,
                    PrivacyOperationStage.Accepted,
                    "Stealth accepted. Waiting for blockchain confirmation...",
                    result.hash,
                )
                addPrivacyPending(result.hash, activeWallet, amountRaw, "stealth", "stealth")
                pollPrivacyTransaction(operationId, result.hash, requestRpcUrl)
            } catch (exception: Exception) {
                failPrivacyOperation(operationId, exception.message ?: "Stealth failed.")
            } finally {
                envelope?.blinding?.fill(0)
                runCatching { privacyCrypto.clear() }
            }
        }
    }

    fun scanPrivacyClaims(
        showDialog: Boolean = false,
        force: Boolean = false,
    ) {
        val activeWallet = wallet ?: return showError("Unlock wallet first.")
        if (!privacySupported) return showError("Privacy requires a 64-bit device with hardware AES support.")
        if (privacyOperationActive || privacyBalanceDecrypting) {
            return showError("A Privacy process is already running. Resume or finish it first.")
        }
        if (!privacyFeeLoading) refreshPrivacyFee(PrivacyOperationType.Claim)
        val requestRpcUrl = currentRpcUrl
        val cacheKey = "${activeWallet.address}|$requestRpcUrl"
        val operationId = ++privacyOperationSerial
        privacyOperation = PrivacyOperationState(
            id = operationId,
            type = PrivacyOperationType.Claim,
            stage = PrivacyOperationStage.Scanning,
            message = "Scanning unclaimed stealth outputs...",
            dialogVisible = showDialog,
        )
        scope.launch {
            try {
                coroutineScope {
                    val transfers = if (
                        !force &&
                        privacyClaimCacheKey == cacheKey &&
                        System.currentTimeMillis() - privacyClaimCacheAt < PrivacyClaimCacheMillis
                    ) {
                        privacyClaimableTransfers
                    } else {
                        val outputs = rpcRepository.fetchStealthOutputs(requestRpcUrl)
                        privacyScanner.scan(activeWallet, outputs).also {
                            privacyClaimCacheKey = cacheKey
                            privacyClaimCacheAt = System.currentTimeMillis()
                        }
                    }
                    ensurePrivacyOperationCurrent(operationId)
                    if (transfers !== privacyClaimableTransfers) clearPrivacyClaimSecrets()
                    privacyClaimableTransfers = transfers
                    if (transfers.isEmpty()) {
                        updatePrivacyOperation(
                            operationId,
                            PrivacyOperationStage.Empty,
                            "No claimable private transfers were found.",
                        )
                    } else {
                        updatePrivacyOperation(
                            operationId,
                            PrivacyOperationStage.Review,
                            "${transfers.size} claimable transfer${if (transfers.size == 1) "" else "s"} found.",
                        )
                    }
                }
            } catch (exception: Exception) {
                failPrivacyOperation(operationId, exception.message ?: "Claim scan failed.")
            }
        }
    }

    fun startPrivacyClaim(transfer: PrivacyClaimableTransfer, feeText: String) {
        val activeWallet = wallet ?: return showError("Unlock wallet first.")
        val current = privacyOperation
        if (current?.type != PrivacyOperationType.Claim || current.stage != PrivacyOperationStage.Review) {
            return showError("Start a fresh Claim scan first.")
        }
        val feeOu = feeText.filter(Char::isDigit).toLongOrNull()?.takeIf { it > 0L }
            ?: return showError("Enter a valid network fee.")
        if (!hasPublicFeeBalance(feeOu)) return showError("Public OCT balance is insufficient for the network fee.")
        val operationId = ++privacyOperationSerial
        val requestRpcUrl = currentRpcUrl
        privacyOperation = current.copy(
            id = operationId,
            stage = PrivacyOperationStage.Preparing,
            amountOct = transfer.amountOct,
            claimOutputIds = listOf(transfer.outputId),
            message = "Preparing Claim proof locally...",
            dialogVisible = true,
        )
        scope.launch {
            try {
                val identity = privacyCrypto.initialize(activeWallet)
                ensurePrivacyOperationCurrent(operationId)
                updatePrivacyOperation(operationId, PrivacyOperationStage.Registering, "Checking Privacy key registration...")
                rpcRepository.ensurePvacRegistered(activeWallet, identity.publicKey, identity.aesKat, requestRpcUrl)
                ensurePrivacyOperationCurrent(operationId)
                updatePrivacyOperation(operationId, PrivacyOperationStage.Preparing, "Encrypting claimed amount and building proof...")
                val payload = privacyCrypto.createClaimPayload(activeWallet, transfer)
                ensurePrivacyOperationCurrent(operationId)
                val encryptedData = privacyClaimPayloadJson(transfer, payload)
                updatePrivacyOperation(operationId, PrivacyOperationStage.Submitting, "Submitting Claim transaction...")
                val result = rpcRepository.submitPrivacyTransaction(
                    wallet = activeWallet,
                    amountRaw = "0",
                    feeOu = feeOu,
                    opType = "claim",
                    encryptedData = encryptedData,
                    url = requestRpcUrl,
                )
                ensurePrivacyOperationCurrent(operationId)
                if (!result.success || result.hash.isNullOrBlank()) {
                    throw IllegalStateException(result.error ?: "Claim transaction was rejected.")
                }
                updatePrivacyOperation(
                    operationId,
                    PrivacyOperationStage.Accepted,
                    "Claim accepted. Waiting for blockchain confirmation...",
                    result.hash,
                )
                addPrivacyPending(result.hash, activeWallet, transfer.amountRaw, "claim")
                pollPrivacyTransaction(operationId, result.hash, requestRpcUrl)
            } catch (exception: Exception) {
                failPrivacyOperation(operationId, exception.message ?: "Claim failed.")
            } finally {
                runCatching { privacyCrypto.clear() }
            }
        }
    }

    fun startPrivacyBulkClaim(feeText: String) {
        val activeWallet = wallet ?: return showError("Unlock wallet first.")
        val transfers = privacyClaimableTransfers.toList()
        val current = privacyOperation
        if (current?.type != PrivacyOperationType.Claim || current.stage != PrivacyOperationStage.Review) {
            return showError("Start a fresh Claim scan first.")
        }
        if (transfers.isEmpty()) return showError("No claimable private transfers were found.")
        val feeOu = feeText.filter(Char::isDigit).toLongOrNull()?.takeIf { it > 0L }
            ?: return showError("Enter a valid network fee.")
        val totalFee = runCatching { Math.multiplyExact(feeOu, transfers.size.toLong()) }.getOrNull()
            ?: return showError("Bulk Claim fee is too large.")
        if (!hasPublicFeeBalance(totalFee)) {
            return showError("Public OCT balance is insufficient for ${transfers.size} Claim fees.")
        }

        val operationId = ++privacyOperationSerial
        val requestRpcUrl = currentRpcUrl
        val totalAmountRaw = runCatching {
            transfers.fold(0L) { total, transfer -> Math.addExact(total, transfer.amountRaw) }
        }.getOrElse { return showError("Bulk Claim amount is too large.") }
        privacyOperation = current.copy(
            id = operationId,
            stage = PrivacyOperationStage.Preparing,
            amountOct = formatPrivacyAmount(totalAmountRaw),
            claimOutputIds = transfers.map { it.outputId },
            message = "Preparing ${transfers.size} private claims...",
            dialogVisible = true,
        )
        scope.launch {
            try {
                val identity = privacyCrypto.initialize(activeWallet)
                ensurePrivacyOperationCurrent(operationId)
                updatePrivacyOperation(operationId, PrivacyOperationStage.Registering, "Checking Privacy key registration...")
                rpcRepository.ensurePvacRegistered(activeWallet, identity.publicKey, identity.aesKat, requestRpcUrl)

                transfers.forEachIndexed { index, transfer ->
                    ensurePrivacyOperationCurrent(operationId)
                    val position = index + 1
                    updatePrivacyOperation(
                        operationId,
                        PrivacyOperationStage.Preparing,
                        "Building Claim $position of ${transfers.size} locally...",
                    )
                    val payload = privacyCrypto.createClaimPayload(activeWallet, transfer)
                    val encryptedData = privacyClaimPayloadJson(transfer, payload)
                    updatePrivacyOperation(
                        operationId,
                        PrivacyOperationStage.Submitting,
                        "Submitting Claim $position of ${transfers.size}...",
                    )
                    val result = rpcRepository.submitPrivacyTransaction(
                        wallet = activeWallet,
                        amountRaw = "0",
                        feeOu = feeOu,
                        opType = "claim",
                        encryptedData = encryptedData,
                        url = requestRpcUrl,
                    )
                    ensurePrivacyOperationCurrent(operationId)
                    if (!result.success || result.hash.isNullOrBlank()) {
                        throw IllegalStateException(result.error ?: "Claim $position was rejected.")
                    }
                    updatePrivacyOperation(
                        operationId,
                        PrivacyOperationStage.Accepted,
                        "Claim $position accepted. Waiting for confirmation...",
                        result.hash,
                    )
                    addPrivacyPending(result.hash, activeWallet, transfer.amountRaw, "claim")
                    awaitPrivacyConfirmation(operationId, result.hash, requestRpcUrl)
                    consumePrivacyClaimOutputs(setOf(transfer.outputId))
                }

                updatePrivacyOperation(
                    operationId,
                    PrivacyOperationStage.Confirmed,
                    "${transfers.size} private claims confirmed on-chain.",
                )
                refreshAfterPrivacyConfirmation()
            } catch (exception: Exception) {
                failPrivacyOperation(operationId, exception.message ?: "Bulk Claim failed.")
            } finally {
                runCatching { privacyCrypto.clear() }
            }
        }
    }

    fun minimizePrivacyOperation() {
        privacyOperation = privacyOperation?.copy(dialogVisible = false)
    }

    fun resumePrivacyOperation() {
        privacyOperation = privacyOperation?.copy(dialogVisible = true)
    }

    fun finishPrivacyOperation() {
        val current = privacyOperation ?: return
        if (!current.isTerminal && current.stage != PrivacyOperationStage.Review) {
            return showError("This Privacy process is still running. Minimize it instead.")
        }
        if (current.stage == PrivacyOperationStage.Review) clearPrivacyClaimSecrets()
        privacyOperation = null
    }

    private fun pollPrivacyTransaction(operationId: Long, hash: String, rpcUrl: String) {
        scope.launch {
            try {
                awaitPrivacyConfirmation(operationId, hash, rpcUrl)
                val claimedOutputs = privacyOperation
                    ?.takeIf { it.id == operationId && it.type == PrivacyOperationType.Claim }
                    ?.claimOutputIds
                    .orEmpty()
                    .toSet()
                if (claimedOutputs.isNotEmpty()) consumePrivacyClaimOutputs(claimedOutputs)
                updatePrivacyOperation(
                    operationId,
                    PrivacyOperationStage.Confirmed,
                    "Privacy transaction confirmed on-chain.",
                    hash,
                )
                refreshAfterPrivacyConfirmation()
            } catch (exception: Exception) {
                failPrivacyOperation(operationId, exception.message ?: "Privacy transaction was not confirmed.")
            }
        }
    }

    private suspend fun awaitPrivacyConfirmation(operationId: Long, hash: String, rpcUrl: String) {
        repeat(PrivacyConfirmationAttempts) {
            delay(TransactionPollingIntervalMs)
            ensurePrivacyOperationCurrent(operationId)
            val state = rpcRepository.checkTransactionStatus(hash, rpcUrl)
            when (state.status) {
                TransactionNetworkStatus.Confirmed -> return
                TransactionNetworkStatus.Rejected,
                TransactionNetworkStatus.Dropped -> throw IllegalStateException(
                    state.reason ?: "Privacy transaction was not confirmed.",
                )
                else -> Unit
            }
        }
        throw IllegalStateException(
            "Confirmation timed out. The transaction may still be pending; check its hash on the explorer.",
        )
    }

    private fun refreshAfterPrivacyConfirmation() {
        privacyClaimCacheKey = null
        privacyClaimCacheAt = 0L
        privacyLoadedAt = 0L
        refreshBalance()
        refreshHistory()
        refreshPrivacy(force = true)
    }

    private fun updatePrivacySyncStep(index: Int, status: PrivacySyncStatus, detail: String? = null) {
        privacySyncSteps = privacySyncSteps.mapIndexed { stepIndex, step ->
            if (stepIndex == index) step.copy(status = status, detail = detail) else step
        }
    }

    private fun updatePrivacyOperation(
        operationId: Long,
        stage: PrivacyOperationStage,
        message: String,
        hash: String? = privacyOperation?.txHash,
    ) {
        val current = privacyOperation ?: return
        if (current.id != operationId) return
        privacyOperation = current.copy(
            stage = stage,
            message = message,
            txHash = hash,
            progressIndex = if (stage == PrivacyOperationStage.Failed) {
                current.progressIndex
            } else {
                privacyProgressIndex(stage)
            },
        )
    }

    private fun failPrivacyOperation(operationId: Long, message: String) {
        updatePrivacyOperation(operationId, PrivacyOperationStage.Failed, message)
    }

    private fun ensurePrivacyOperationCurrent(operationId: Long) {
        check(privacyOperation?.id == operationId && privacyOperationSerial == operationId) {
            "Privacy process was cancelled."
        }
    }

    private fun addPrivacyPending(
        hash: String,
        activeWallet: Wallet,
        amountRaw: Long,
        opType: String,
        recipient: String = activeWallet.address,
    ) {
        val pending = TransactionHistoryItem(
            hash = hash,
            from = activeWallet.address,
            to = recipient,
            amount = amountRaw / MicroUnits.toDouble(),
            timestamp = System.currentTimeMillis() / 1000.0,
            status = "pending",
            type = "sent",
            opType = opType,
        )
        privacyHistory = (listOf(pending) + privacyHistory.filterNot { it.hash == hash })
            .take(InitialPrivacyHistoryLimit)
        addPendingTransaction(hash, activeWallet.address, recipient, pending.amount, null, opType)
    }

    private fun parsePrivacyAmount(value: String): Long? = runCatching {
        val decimal = value.trim().replace(',', '.').toBigDecimal()
        require(decimal > BigDecimal.ZERO)
        require(decimal.stripTrailingZeros().scale() <= 6)
        decimal.movePointRight(6).longValueExact()
    }.getOrNull()

    private fun privacyProgressIndex(stage: PrivacyOperationStage): Int = when (stage) {
        PrivacyOperationStage.Scanning -> 0
        PrivacyOperationStage.Review -> 1
        PrivacyOperationStage.Preparing -> 1
        PrivacyOperationStage.Registering -> 0
        PrivacyOperationStage.Submitting -> 2
        PrivacyOperationStage.Accepted,
        PrivacyOperationStage.Confirmed -> 3
        PrivacyOperationStage.Empty -> 1
        PrivacyOperationStage.Failed -> privacyOperation?.progressIndex ?: 0
    }

    private fun clearPrivacyClaimSecrets() {
        privacyClaimableTransfers.forEach { transfer ->
            transfer.blinding.fill(0)
            transfer.claimSecret.fill(0)
        }
        privacyClaimableTransfers = emptyList()
    }

    private fun consumePrivacyClaimOutputs(outputIds: Set<String>) {
        if (outputIds.isEmpty()) return
        val (consumed, remaining) = privacyClaimableTransfers.partition { it.outputId in outputIds }
        consumed.forEach { transfer ->
            transfer.blinding.fill(0)
            transfer.claimSecret.fill(0)
        }
        privacyClaimableTransfers = remaining
        privacyClaimCacheKey = null
        privacyClaimCacheAt = 0L
    }

    private fun privacyClaimPayloadJson(
        transfer: PrivacyClaimableTransfer,
        payload: PrivacyClaimPayload,
    ): String = JSONObject()
        .put("version", 5)
        .put("output_id", transfer.outputId.toLongOrNull() ?: transfer.outputId)
        .put("claim_cipher", payload.cipher)
        .put("commitment", payload.commitment)
        .put("claim_secret", transfer.claimSecret.toHex())
        .put("zero_proof", payload.zeroProof)
        .toWebcliJson()

    private fun JSONObject.toWebcliJson(): String = toString().replace("\\/", "/")

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it.toInt() and 0xff) }

    private fun hasPublicFeeBalance(feeOu: Long): Boolean {
        val availableRaw = BigDecimal.valueOf(availableBalance)
            .movePointRight(6)
            .setScale(0, RoundingMode.DOWN)
            .toLong()
        return feeOu > 0L && feeOu < availableRaw
    }

    private fun resetPrivacyState() {
        privacySyncSerial++
        privacyOperationSerial++
        privacyFeeSerial++
        privacyLoadedKey = null
        privacyLoadedAt = 0L
        privacyClaimCacheKey = null
        privacyClaimCacheAt = 0L
        privacySyncing = false
        privacySyncSteps = initialPrivacySyncSteps()
        privacyEncryptedBalance = null
        privacyBalanceRaw = null
        privacyBalanceVisible = false
        privacyDecryptedCipher = null
        privacyBalanceDecrypting = false
        privacyHistory = emptyList()
        clearPrivacyClaimSecrets()
        privacyOperation = null
        scope.launch { runCatching { privacyCrypto.clear() } }
    }

    fun selectDashboardTab(tab: DashboardTab) {
        dashboardTab = tab
        if (tab == DashboardTab.Assets) refreshEvmDashboard()
        if (tab == DashboardTab.Explore) refreshDAppCatalog()
        if (tab == DashboardTab.Privacy) {
            refreshPrivacy(force = false)
            refreshOctPrice()
        }
        if (tab == DashboardTab.Trade) {
            refreshTrade()
            refreshTradeChart()
        }
    }

    fun refreshDAppCatalog(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (dAppCatalogLoading) return
        if (!force && dAppCatalogFetchedAt > 0L && now - dAppCatalogFetchedAt < DAppCatalogRefreshMs) return
        val requestSerial = ++dAppCatalogRequestSerial
        dAppCatalogLoading = true
        dAppCatalogError = null
        scope.launch {
            runCatching { dAppCatalogRepository.fetchEntries() }
                .onSuccess { entries ->
                    if (requestSerial != dAppCatalogRequestSerial) return@onSuccess
                    dAppCatalog = entries
                    dAppCatalogFetchedAt = System.currentTimeMillis()
                }
                .onFailure { error ->
                    if (requestSerial != dAppCatalogRequestSerial) return@onFailure
                    dAppCatalogError = error.message ?: "Unable to load dApps."
                }
            if (requestSerial == dAppCatalogRequestSerial) dAppCatalogLoading = false
        }
    }

    fun previewDAppInput(rawInput: String) {
        val cleanInput = rawInput.trim()
        val serial = ++dAppInputPreviewSerial
        if (cleanInput.isBlank()) {
            dAppInputPreview = null
            return
        }

        val octraSpec = parseCircleInputSpec(cleanInput)
        if (cleanInput.startsWith("oct://", ignoreCase = true) && octraSpec == null) {
            dAppInputPreview = DAppInputPreview(
                input = cleanInput,
                title = "Invalid oct:// input",
                subtitle = "Use oct://foo.oct or oct://octx...",
                detail = "Circle input could not be parsed.",
                status = DAppInputPreviewStatus.Error,
                showOctProtocol = true,
            )
            return
        }

        if (octraSpec != null) {
            dAppInputPreview = DAppInputPreview(
                input = cleanInput,
                title = if (octraSpec.identityName != null) "Resolving ${octraSpec.identityName}" else "Checking Octra address",
                subtitle = activeRpcNetwork?.name ?: currentIdentityNetwork(),
                detail = "Fetching from active Octra RPC...",
                status = DAppInputPreviewStatus.Loading,
                showOctProtocol = octraSpec.explicitScheme,
            )
            scope.launch {
                val next = runCatching { resolveDAppInputPreview(octraSpec, cleanInput) }.getOrElse { error ->
                    DAppInputPreview(
                        input = cleanInput,
                        title = "Unable to resolve",
                        subtitle = octraSpec.identityName ?: octraSpec.host,
                        detail = error.message ?: "Preview lookup failed.",
                        status = DAppInputPreviewStatus.Error,
                        showOctProtocol = octraSpec.explicitScheme,
                    )
                }
                if (serial == dAppInputPreviewSerial) dAppInputPreview = next
            }
            return
        }

        val browserUrl = runCatching { DAppUrlPolicy.normalizeBrowserUrl(cleanInput) }.getOrElse { error ->
            dAppInputPreview = DAppInputPreview(
                input = cleanInput,
                title = "Invalid dApp URL",
                subtitle = "Enter dApp URL, Octra Identity, or circle address.",
                detail = error.message ?: "URL could not be parsed.",
                status = DAppInputPreviewStatus.Error,
            )
            return
        }
        val rule = consumerSettingsRepository.domainGuardRuleForUrl(browserUrl)
        dAppInputPreview = if (rule != null && !rule.trusted) {
            DAppInputPreview(
                input = cleanInput,
                title = "Blocked by Phishing Guard",
                subtitle = rule.host,
                detail = "Remove the block in Settings if this is expected.",
                status = DAppInputPreviewStatus.Error,
            )
        } else {
            val origin = canonicalizeOrigin(browserUrl)
            DAppInputPreview(
                input = cleanInput,
                title = hostLabel(origin),
                subtitle = browserUrl,
                detail = if (rule?.trusted == true) "Trusted dApp URL" else "Ready to open in wallet browser",
                status = DAppInputPreviewStatus.Web,
            )
        }
    }

    fun clearDAppInputPreview() {
        dAppInputPreviewSerial++
        dAppInputPreview = null
    }

    fun openDAppBrowser(title: String, url: String) {
        openDAppBrowser(title, url, openInNewTab = false)
    }

    fun openDAppBrowserTab(title: String, url: String) {
        openDAppBrowser(title, url, openInNewTab = true)
    }

    private fun openDAppBrowser(title: String, url: String, openInNewTab: Boolean) {
        val cleanUrl = url.trim()
        val octraInput = normalizeOctraBrowserInput(cleanUrl)
        if (cleanUrl.startsWith("oct://", ignoreCase = true) && octraInput == null) {
            showError("Enter a valid oct:// identity or Octra address.")
            return
        }
        if (octraInput != null) {
            prepareDAppBrowserTab(openInNewTab)
            openOctraCircleBrowser(title, octraInput)
            return
        }
        val browserUrl = runCatching { DAppUrlPolicy.normalizeBrowserUrl(cleanUrl) }.getOrElse { error ->
            showError(error.message ?: "Enter a valid dApp URL.")
            return
        }
        val rule = consumerSettingsRepository.domainGuardRuleForUrl(browserUrl)
        if (rule != null && !rule.trusted) {
            showError("Blocked by Phishing Guard: ${rule.host}")
            return
        }
        prepareDAppBrowserTab(openInNewTab)
        if (!openInNewTab) circleResolveSerial++
        selectedDAppTitle = title
        selectedDAppUrl = browserUrl
        selectedDAppDisplayUrl = null
        selectedCircleTarget = null
        selectedCircleError = null
        route = AppRoute.DAppBrowser
        syncActiveDAppBrowserTab()
    }

    private fun openOctraAddressExplorer(title: String, address: String, displayLabel: String) {
        val url = explorerAddressUrl(address) ?: run {
            showError("Explorer URL is not configured for the active Octra network.")
            return
        }
        prepareDAppBrowserTab(openInNewTab = false)
        circleResolveSerial++
        selectedDAppTitle = title
        selectedDAppUrl = url
        selectedDAppDisplayUrl = displayLabel
        selectedCircleTarget = null
        selectedCircleError = null
        route = AppRoute.DAppBrowser
        syncActiveDAppBrowserTab()
    }

    fun selectDAppBrowserTab(tabId: String) {
        if (tabId == activeDAppTabId) return
        syncActiveDAppBrowserTab()
        val tab = dAppBrowserTabs.firstOrNull { it.id == tabId } ?: return
        activateDAppBrowserTab(tab)
    }

    fun closeDAppBrowserTab(tabId: String) {
        val tabs = dAppBrowserTabs
        val index = tabs.indexOfFirst { it.id == tabId }
        if (index < 0) return
        if (tabs.size <= 1) {
            closeDAppBrowser()
            return
        }
        val nextTabs = tabs.filterNot { it.id == tabId }
        dAppBrowserTabs = nextTabs
        if (activeDAppTabId == tabId) {
            activateDAppBrowserTab(nextTabs[index.coerceAtMost(nextTabs.lastIndex)])
        }
    }

    fun closeDAppBrowser() {
        circleResolveSerial++
        selectedCircleTarget = null
        selectedCircleError = null
        circlePassphraseRequest = null
        circlePassphrases.clear()
        selectedDAppDisplayUrl = null
        dAppBrowserTabs = emptyList()
        activeDAppTabId = null
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

    private fun prepareDAppBrowserTab(openInNewTab: Boolean) {
        if (route != AppRoute.DAppBrowser) {
            dAppReturnRoute = route
            dAppReturnTab = dashboardTab
        } else {
            syncActiveDAppBrowserTab()
        }
        if (openInNewTab || activeDAppTabId == null || dAppBrowserTabs.none { it.id == activeDAppTabId }) {
            activeDAppTabId = "tab-${System.nanoTime()}"
        }
    }

    private fun activateDAppBrowserTab(tab: DAppBrowserTab) {
        activeDAppTabId = tab.id
        selectedDAppTitle = tab.title
        selectedDAppUrl = tab.url
        selectedDAppDisplayUrl = tab.displayUrl
        selectedCircleTarget = tab.circleTarget
        selectedCircleError = tab.circleError
        circlePassphraseRequest = null
        route = AppRoute.DAppBrowser
    }

    private fun syncActiveDAppBrowserTab() {
        val tabId = activeDAppTabId ?: return
        if (selectedDAppUrl.isBlank() && selectedDAppTitle.isBlank()) return
        val tab = DAppBrowserTab(
            id = tabId,
            title = selectedDAppTitle,
            url = selectedDAppUrl,
            displayUrl = selectedDAppDisplayUrl,
            circleTarget = selectedCircleTarget,
            circleError = selectedCircleError,
        )
        dAppBrowserTabs = if (dAppBrowserTabs.any { it.id == tabId }) {
            dAppBrowserTabs.map { existing -> if (existing.id == tabId) tab else existing }
        } else {
            dAppBrowserTabs + tab
        }
    }

    private fun updateDAppBrowserTab(tabId: String?, tab: DAppBrowserTab) {
        if (tabId == null) return
        dAppBrowserTabs = if (dAppBrowserTabs.any { it.id == tabId }) {
            dAppBrowserTabs.map { existing -> if (existing.id == tabId) tab else existing }
        } else {
            dAppBrowserTabs + tab
        }
    }

    fun normalizeOctraBrowserInput(value: String): String? {
        val raw = value.trim()
        if (raw.isBlank()) return null
        if (parseCircleInputSpec(raw) != null) return raw

        val normalized = OctraIdentityName.fromWebUrl(raw) ?: return null
        return normalized.takeIf { parseCircleInputSpec(it) != null }
    }

    fun isOctraCircleInput(value: String): Boolean = normalizeOctraBrowserInput(value) != null

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
            val passphrase = circlePassphrases[cacheKey]
            if (passphrase.isNullOrBlank()) {
                requestCirclePassphrase(circleId)
                throw IllegalStateException("Enter the sealed read passphrase to open this circle.")
            }
            val activeWallet = wallet ?: throw IllegalStateException("Unlock poctra to open this sealed circle.")
            return runCatching { rpcRepository.fetchSealedCircleAsset(circleId, path, passphrase, activeWallet) }
                .onSuccess { maybeUpdateCircleBrowserTitle(circleId, path, it) }
                .getOrElse { error ->
                    circlePassphrases.remove(cacheKey)
                    requestCirclePassphrase(circleId)
                    throw error
                }
        }
        return rpcRepository.fetchCircleAsset(circleId, path)
            .also { maybeUpdateCircleBrowserTitle(circleId, path, it) }
    }

    fun downloadCircleAssets(
        paths: List<String>,
        fileName: String,
        mimeType: String,
        expectedSha256: String?,
        onProgress: (Int, Int) -> Unit,
        onSuccess: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val target = selectedCircleTarget
        if (target == null) {
            onError("No active Circle app.")
            return
        }
        val cleanPaths = runCatching { validateCircleDownloadPaths(paths) }.getOrElse {
            onError(it.message ?: "Invalid Circle download paths.")
            return
        }
        val safeName = sanitizeCircleDownloadName(fileName, cleanPaths.last())
        val safeMime = mimeType.trim().lowercase().takeIf {
            it.matches(Regex("^[a-z0-9][a-z0-9.+-]*/[a-z0-9][a-z0-9.+-]*$"))
        } ?: "application/octet-stream"
        val cleanSha256 = expectedSha256
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.matches(Regex("^[0-9a-f]{64}$")) }
        val downloadKey = "${circleCacheKey(target.circleId)}:$safeName"
        if (!activeCircleDownloads.add(downloadKey)) {
            onError("This file is already downloading.")
            return
        }

        showMessage("Downloading $safeName in background...")
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    writeCircleDownload(
                        circleId = target.circleId,
                        paths = cleanPaths,
                        fileName = safeName,
                        mimeType = safeMime,
                        expectedSha256 = cleanSha256,
                        onProgress = onProgress,
                    )
                }
            }.onSuccess { uri ->
                activeCircleDownloads.remove(downloadKey)
                showMessage("$safeName downloaded.")
                onSuccess()
                openCircleDownload(uri, safeMime, safeName)
            }.onFailure { exception ->
                activeCircleDownloads.remove(downloadKey)
                val message = exception.message ?: "Circle download failed."
                showError(message)
                onError(message)
            }
        }
    }

    private fun validateCircleDownloadPaths(paths: List<String>): List<String> {
        require(paths.isNotEmpty()) { "Download has no resources." }
        require(paths.size <= MaxCircleDownloadParts) { "Download contains too many parts." }
        return paths.map { rawPath ->
            val clean = rawPath.trim().substringBefore('?').substringBefore('#')
            require(clean.startsWith('/') && clean.length <= MaxCircleDownloadPathLength) {
                "Invalid Circle download path."
            }
            require(clean.split('/').none { it == ".." }) { "Unsafe Circle download path." }
            clean
        }.distinct().also {
            require(it.size == paths.size) { "Download contains duplicate parts." }
        }
    }

    private fun sanitizeCircleDownloadName(fileName: String, fallbackPath: String): String {
        val fallback = fallbackPath.substringAfterLast('/').substringBefore(".part").ifBlank { "circle-download" }
        val clean = fileName
            .trim()
            .ifBlank { fallback }
            .replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim('.', ' ')
            .take(MaxCircleDownloadNameLength)
        return clean.ifBlank { "circle-download" }
    }

    private fun writeCircleDownload(
        circleId: String,
        paths: List<String>,
        fileName: String,
        mimeType: String,
        expectedSha256: String?,
        onProgress: (Int, Int) -> Unit,
    ): Uri {
        val digest = MessageDigest.getInstance("SHA-256")
        var totalBytes = 0L
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, mimeType)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/poctra")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            val resolver = appContext.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IllegalStateException("Unable to create the download file.")
            try {
                resolver.openOutputStream(uri, "w")?.use { output ->
                    paths.forEachIndexed { index, path ->
                        val bytes = fetchCircleAssetForBrowser(circleId, path).body
                        totalBytes += bytes.size
                        require(totalBytes <= MaxCircleDownloadBytes) { "Circle download is larger than 256 MB." }
                        digest.update(bytes)
                        output.write(bytes)
                        onProgress(index + 1, paths.size)
                    }
                } ?: throw IllegalStateException("Unable to write the download file.")
                verifyCircleDownloadHash(digest, expectedSha256)
                resolver.update(uri, ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) }, null, null)
                uri
            } catch (exception: Exception) {
                resolver.delete(uri, null, null)
                throw exception
            }
        } else {
            val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                ?: File(appContext.cacheDir, "circle-downloads")
            require(directory.exists() || directory.mkdirs()) { "Unable to create the download directory." }
            val destination = File(directory, fileName)
            val temporary = File(directory, "$fileName.download")
            try {
                temporary.outputStream().buffered().use { output ->
                    paths.forEachIndexed { index, path ->
                        val bytes = fetchCircleAssetForBrowser(circleId, path).body
                        totalBytes += bytes.size
                        require(totalBytes <= MaxCircleDownloadBytes) { "Circle download is larger than 256 MB." }
                        digest.update(bytes)
                        output.write(bytes)
                        onProgress(index + 1, paths.size)
                    }
                }
                verifyCircleDownloadHash(digest, expectedSha256)
                if (destination.exists()) destination.delete()
                require(temporary.renameTo(destination)) { "Unable to finalize the download file." }
                FileProvider.getUriForFile(appContext, "${appContext.packageName}.fileprovider", destination)
            } catch (exception: Exception) {
                temporary.delete()
                throw exception
            }
        }
    }

    private fun verifyCircleDownloadHash(digest: MessageDigest, expectedSha256: String?) {
        if (expectedSha256 == null) return
        val actual = digest.digest().joinToString("") { "%02x".format(it) }
        require(actual == expectedSha256) { "Downloaded file integrity check failed." }
    }

    private fun openCircleDownload(uri: Uri, mimeType: String, fileName: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            activity.startActivity(Intent.createChooser(intent, "Open $fileName"))
        } catch (_: ActivityNotFoundException) {
            showMessage("$fileName saved to Downloads/poctra.")
        }
    }

    fun submitCirclePassphrase(passphrase: String) {
        val request = circlePassphraseRequest ?: return
        if (passphrase.isBlank()) {
            showError("Enter the sealed read passphrase.")
            return
        }
        circlePassphrases[circleCacheKey(request.circleId)] = passphrase
        circlePassphraseRequest = null
    }

    fun dismissCirclePassphraseRequest() {
        circlePassphraseRequest = null
    }

    private fun requestCirclePassphrase(circleId: String) {
        val label = selectedCircleTarget?.uri ?: "oct://$circleId"
        scope.launch {
            if (circlePassphrases[circleCacheKey(circleId)].isNullOrBlank()) {
                circlePassphraseRequest = CirclePassphraseRequest(circleId, label)
            }
        }
    }

    private suspend fun isCircleAddress(circleId: String): Boolean =
        !circleResourceMode(circleId).isNullOrBlank()

    private suspend fun circleResourceMode(circleId: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val cacheKey = circleCacheKey(circleId)
            circleResourceModeCache.getOrPut(cacheKey) {
                rpcRepository.fetchCircleInfo(circleId).optString("resource_mode")
            }
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun circleCacheKey(circleId: String): String =
        "${activeRpcNetwork?.id.orEmpty()}:$circleId"

    private fun resetCircleNetworkState() {
        circleResolveSerial++
        circleResourceModeCache.clear()
        circlePassphrases.clear()
        circlePassphraseRequest = null
        selectedCircleTarget = null
        selectedCircleError = null
    }

    private fun openOctraCircleBrowser(title: String, rawInput: String) {
        val spec = parseCircleInputSpec(rawInput)
        val tabId = activeDAppTabId
        selectedDAppTitle = when {
            title.isNotBlank() && title != "dApp" -> title
            spec?.explicitScheme == true -> "oct://"
            spec?.identityName != null -> spec.identityName
            else -> "oct://"
        }
        selectedDAppUrl = circleLoadingUrl(rawInput)
        selectedDAppDisplayUrl = if (spec?.explicitScheme == true) circleDisplayUri(spec, rawInput) else rawInput.trim()
        val initialDisplayUrl = selectedDAppDisplayUrl
        selectedCircleTarget = null
        selectedCircleError = null
        route = AppRoute.DAppBrowser
        syncActiveDAppBrowserTab()

        val serial = ++circleResolveSerial
        scope.launch {
            runCatching { resolveCircleOpenResult(rawInput) }
                .onSuccess { result ->
                    if (serial != circleResolveSerial) return@onSuccess
                    when (result) {
                        is OctraCircleOpenResult.Circle -> {
                            val nextTitle = result.target.label
                            val nextUrl = circleBrowserUrl(result.target.circleId, result.target.path)
                            updateDAppBrowserTab(
                                tabId,
                                DAppBrowserTab(
                                    id = tabId.orEmpty(),
                                    title = nextTitle,
                                    url = nextUrl,
                                    displayUrl = initialDisplayUrl,
                                    circleTarget = result.target,
                                    circleError = null,
                                ),
                            )
                            if (activeDAppTabId != tabId) return@onSuccess
                            selectedCircleTarget = result.target
                            selectedCircleError = null
                            selectedDAppTitle = nextTitle
                            selectedDAppUrl = nextUrl
                        }
                        is OctraCircleOpenResult.ExplorerAddress -> {
                            updateDAppBrowserTab(
                                tabId,
                                DAppBrowserTab(
                                    id = tabId.orEmpty(),
                                    title = result.title,
                                    url = result.url,
                                    displayUrl = result.displayUrl,
                                    circleTarget = null,
                                    circleError = null,
                                ),
                            )
                            if (activeDAppTabId != tabId) return@onSuccess
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
                    val failureSpec = parseCircleInputSpec(rawInput)
                    val nextError = exception.message ?: "Unable to open oct:// resource."
                    val nextDisplayUrl = if (failureSpec?.explicitScheme == true) {
                        circleDisplayUri(failureSpec, rawInput)
                    } else {
                        rawInput.trim()
                    }
                    val nextTitle = when {
                        failureSpec?.explicitScheme == true -> "oct://"
                        failureSpec?.identityName != null -> failureSpec.identityName
                        else -> "Octra"
                    }
                    val nextUrl = circleErrorUrl(rawInput)
                    updateDAppBrowserTab(
                        tabId,
                        DAppBrowserTab(
                            id = tabId.orEmpty(),
                            title = nextTitle,
                            url = nextUrl,
                            displayUrl = nextDisplayUrl,
                            circleTarget = null,
                            circleError = nextError,
                        ),
                    )
                    if (activeDAppTabId != tabId) return@onFailure
                    selectedCircleTarget = null
                    selectedCircleError = nextError
                    selectedDAppDisplayUrl = nextDisplayUrl
                    selectedDAppTitle = nextTitle
                    selectedDAppUrl = nextUrl
                }
        }
    }

    private suspend fun resolveCircleOpenResult(rawInput: String): OctraCircleOpenResult {
        val spec = parseCircleInputSpec(rawInput) ?: throw IllegalStateException("Enter foo.oct or an Octra circle address.")
        val circleId = resolveOctraInputAddress(spec)
        val displayUri = circleDisplayUri(spec, rawInput)
        val resourceMode = circleResourceMode(circleId)
        if (resourceMode.isNullOrBlank()) {
            val url = explorerAddressUrl(circleId)
                ?: throw IllegalStateException("${spec.identityName ?: circleId} resolved to $circleId, but Explorer URL is not configured.")
            return OctraCircleOpenResult.ExplorerAddress(
                title = spec.identityName ?: shortAddress(circleId),
                url = url,
                displayUrl = spec.identityName ?: circleId,
            )
        }
        val label = previewCircleIndexTitle(circleId, resourceMode)
            ?: spec.identityName?.let { "$it (oct://)" }
            ?: shortAddress(circleId)
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

    private suspend fun resolveDAppInputPreview(spec: CircleInputSpec, rawInput: String): DAppInputPreview {
        val address = resolveOctraInputAddress(spec)
        val resourceMode = circleResourceMode(address)
        val displayName = spec.identityName ?: shortAddress(address)
        val resolvedLabel = if (spec.identityName != null) {
            "${spec.identityName} -> ${shortAddress(address)}"
        } else {
            shortAddress(address)
        }
        if (resourceMode.isNullOrBlank()) {
            val explorerUrl = explorerAddressUrl(address)
                ?: throw IllegalStateException("Explorer URL is not configured for the active Octra network.")
            return DAppInputPreview(
                input = rawInput,
                title = "Open in Explorer",
                subtitle = resolvedLabel,
                detail = hostLabel(canonicalizeOrigin(explorerUrl)),
                status = DAppInputPreviewStatus.Explorer,
                showOctProtocol = false,
            )
        }

        val indexTitle = previewCircleIndexTitle(address, resourceMode)
        return DAppInputPreview(
            input = rawInput,
            title = indexTitle ?: displayName,
            subtitle = resolvedLabel,
            detail = if (resourceMode == "sealed_read") {
                "Sealed circle. Passphrase required when opened."
            } else {
                "Public circle app on ${activeRpcNetwork?.name ?: currentIdentityNetwork()}"
            },
            status = DAppInputPreviewStatus.Circle,
            showOctProtocol = true,
        )
    }

    private suspend fun resolveOctraInputAddress(spec: CircleInputSpec): String {
        if (isOctraAddress(spec.host)) return spec.host
        val activeWallet = wallet ?: throw IllegalStateException("Unlock wallet before resolving Octra Identity.")
        val provider = activeIdentityProvider
            ?: identityProviderRepository.activeProvider(currentIdentityNetwork())
            ?: throw IllegalStateException("Add an Octra Identity Provider in Settings first.")
        return withContext(Dispatchers.IO) {
            rpcRepository.resolveIdentityName(provider, spec.host, activeWallet.address)
        } ?: throw IllegalStateException("${spec.host} could not be resolved.")
    }

    private suspend fun previewCircleIndexTitle(circleId: String, resourceMode: String): String? {
        if (resourceMode == "sealed_read") return null
        return withContext(Dispatchers.IO) {
            runCatching {
                extractCircleHtmlTitle(rpcRepository.fetchCircleAsset(circleId, "/index.html"))
            }.getOrNull()
        }
    }

    private fun maybeUpdateCircleBrowserTitle(circleId: String, path: String, asset: CircleAsset) {
        if (!isCircleIndexPath(path)) return
        val title = extractCircleHtmlTitle(asset) ?: return
        scope.launch {
            val target = selectedCircleTarget ?: return@launch
            if (target.circleId.equals(circleId, ignoreCase = true)) {
                selectedDAppTitle = title
                syncActiveDAppBrowserTab()
            }
        }
    }

    fun walletDisplayName(address: String): String {
        val list = vault?.wallets.orEmpty()
        val index = list.indexOfFirst { it.address == address }
        val saved = list.getOrNull(index)?.name.orEmpty()
        return saved.ifBlank { "Wallet ${if (index >= 0) index + 1 else 1}" }
    }

    fun walletOverviewDisplayName(address: String): String =
        walletIdentityDomain(address) ?: walletDisplayName(address)

    fun walletIdenticonSvg(address: String): String {
        val label = walletOverviewDisplayName(address)
        return vault?.wallets
            .orEmpty()
            .firstOrNull { it.address.equals(address, ignoreCase = true) }
            ?.identiconSvg
            ?.takeIf { it.isNotBlank() && WalletIdenticon.matchesLabel(it, address, label) }
            ?: WalletIdenticon.previewSvg(address, label)
    }

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
                pending.chunked(IdentityLookupBatchSize).flatMap { batch ->
                    coroutineScope {
                        batch.map { address ->
                            async {
                                val name = runCatching { rpcRepository.walletIdentityName(provider, address) }
                                    .getOrNull()
                                    ?.takeIf { it.isNotBlank() }
                                name?.let { address.lowercase() to it }
                            }
                        }.awaitAll().filterNotNull()
                    }
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
        if (privacyOperationActive) {
            showError("Finish the active Privacy process before changing Octra RPC.")
            return
        }
        if (!rpcRepository.isValidRpcUrl(url)) {
            showError("Enter a valid HTTP or HTTPS RPC URL.")
            return
        }
        runBusy {
            rpcRepository.saveUrl(url)
            activeRpcNetwork = rpcRepository.activeNetwork()
            rpcNetworks = rpcRepository.rpcNetworks()
            rpcStatus = RpcStatus(RpcStatus.State.Checking, url.trim().trimEnd('/'), networkFor(url))
            resetCircleNetworkState()
            resetPrivacyState()
            rpcStatus = rpcRepository.checkStatus()
            refreshIdentityProviders()
            refreshWalletIdentityNames()
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
        if (privacyOperationActive) {
            showError("Finish the active Privacy process before changing Octra network.")
            return
        }
        runBusy {
            val network = rpcRepository.setActiveNetwork(id)
            activeRpcNetwork = network
            rpcNetworks = rpcRepository.rpcNetworks()
            rpcStatus = RpcStatus(RpcStatus.State.Checking, network.url, network.networkType)
            resetCircleNetworkState()
            resetPrivacyState()
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
        if (privacyOperationActive) {
            showError("Finish the active Privacy process before changing Octra network.")
            return
        }
        runBusy {
            rpcNetworks = rpcRepository.deleteNetwork(id)
            val active = rpcRepository.activeNetwork()
            activeRpcNetwork = active
            rpcStatus = RpcStatus(RpcStatus.State.Checking, active.url, active.networkType)
            resetCircleNetworkState()
            resetPrivacyState()
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
        val requestSerial = ++rpcStatusRequestSerial
        scope.launch {
            try {
                rpcNetworks = rpcRepository.rpcNetworks()
                val active = rpcRepository.activeNetwork()
                if (requestSerial != rpcStatusRequestSerial) return@launch
                activeRpcNetwork = active
                rpcStatus = RpcStatus(RpcStatus.State.Checking, active.url, active.networkType)
                val status = rpcRepository.checkStatus(active.url)
                if (requestSerial == rpcStatusRequestSerial && activeRpcNetwork?.id == active.id) rpcStatus = status
            } catch (exception: Exception) {
                if (requestSerial != rpcStatusRequestSerial) return@launch
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

    private fun refreshConsumerInsights() {
        securityRisks = buildSecurityRisks()
        refreshUnifiedActivity()
    }

    private fun refreshPortfolioHoldings() {
        val holdings = mutableListOf<PortfolioHolding>()
        val octSubtitle = activeRpcNetwork?.let { "${it.name} - ${it.networkType}" } ?: "Octra"
        holdings += PortfolioHolding(
            title = "OCT",
            subtitle = octSubtitle,
            balance = tokenBalance.amount,
            fiatEstimate = tokenBalance.fiatEstimate,
            source = "Octra",
            riskLevel = if (rpcStatus?.state == RpcStatus.State.Failed) ConsumerRiskLevel.Warning else ConsumerRiskLevel.Safe,
            assetId = "octra:native",
            symbol = "OCT",
            network = activeRpcNetwork?.networkType ?: "Octra",
            valueUsd = (octUsdPrice ?: 0.0) * availableBalance,
        )
        octraTokens.forEach { token ->
            holdings += PortfolioHolding(
                title = token.symbol,
                subtitle = token.name,
                balance = token.formattedBalance,
                fiatEstimate = "OCS01 asset",
                source = "Octra",
                riskLevel = ConsumerRiskLevel.Notice,
                assetId = "octra:${token.address.lowercase()}",
                symbol = token.symbol,
                network = activeRpcNetwork?.networkType ?: "Octra",
                assetAddress = token.address,
            )
        }
        val evmNetwork = activeEvmNetwork ?: runCatching { evmRepository.activeNetwork() }.getOrNull()
        val account = evmAccount
        if (evmNetwork != null && account != null) {
            holdings += PortfolioHolding(
                title = evmNetwork.symbol,
                subtitle = evmNetwork.name,
                balance = account.balance,
                fiatEstimate = account.fiatEstimate,
                source = "EVM",
                riskLevel = if (account.connected) ConsumerRiskLevel.Safe else ConsumerRiskLevel.Warning,
                assetId = "evm:native:${evmNetwork.chainId}",
                symbol = evmNetwork.symbol,
                network = evmNetwork.name,
                valueUsd = parseUsdEstimate(account.fiatEstimate),
            )
        }
        evmTokens.forEach { token ->
            val fiatEstimate = tokenFiatEstimate(token)
            holdings += PortfolioHolding(
                title = token.symbol,
                subtitle = token.name,
                balance = token.balance,
                fiatEstimate = fiatEstimate,
                source = evmNetwork?.name ?: "EVM",
                riskLevel = ConsumerRiskLevel.Safe,
                assetId = "evm:${token.chainId}:${token.address.lowercase()}",
                symbol = token.symbol,
                network = evmNetwork?.name ?: "EVM",
                assetAddress = token.address,
                valueUsd = parseUsdEstimate(fiatEstimate),
            )
        }
        if (evmNfts.isNotEmpty()) {
            holdings += PortfolioHolding(
                title = "NFTs",
                subtitle = evmNetwork?.name ?: "EVM collectibles",
                balance = evmNfts.count { it.owned }.takeIf { it > 0 }?.toString() ?: evmNfts.size.toString(),
                fiatEstimate = "Collectibles",
                source = "EVM",
                riskLevel = ConsumerRiskLevel.Notice,
                assetId = "evm:nft:${evmNetwork?.chainId ?: 0L}",
                symbol = "NFT",
                network = evmNetwork?.name ?: "EVM",
            )
        }
        val merged = mergePortfolioHoldingsWithCache(holdings)
        portfolioHoldings = merged
        if (merged.isNotEmpty()) persistPortfolioHoldings(merged)
    }

    private fun mergePortfolioHoldingsWithCache(current: List<PortfolioHolding>): List<PortfolioHolding> {
        val cached = loadPortfolioHoldings()
        if (cached.isEmpty()) return current
        val currentIds = current.map { it.assetId }.toSet()
        val currentNetwork = activeEvmNetwork?.name
        val fallback = cached.filter { holding ->
            holding.assetId !in currentIds &&
                when {
                    holding.assetId.startsWith("evm:") -> currentNetwork == null || holding.network == currentNetwork
                    holding.assetId.startsWith("octra:") -> holding.assetId != "octra:native"
                    else -> false
                }
        }
        return (current + fallback).distinctBy { it.assetId }
    }

    private fun persistPortfolioHoldings(holdings: List<PortfolioHolding>) {
        val array = JSONArray()
        holdings.forEach { holding ->
            array.put(
                JSONObject()
                    .put("title", holding.title)
                    .put("subtitle", holding.subtitle)
                    .put("balance", holding.balance)
                    .put("fiatEstimate", holding.fiatEstimate)
                    .put("source", holding.source)
                    .put("riskLevel", holding.riskLevel.name)
                    .put("assetId", holding.assetId)
                    .put("symbol", holding.symbol)
                    .put("network", holding.network)
                    .put("assetAddress", holding.assetAddress)
                    .put("valueUsd", holding.valueUsd),
            )
        }
        uiPrefs.edit().putString(KeyPortfolioHoldings, array.toString()).apply()
    }

    private fun loadPortfolioHoldings(): List<PortfolioHolding> {
        val raw = uiPrefs.getString(KeyPortfolioHoldings, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        PortfolioHolding(
                            title = item.optString("title"),
                            subtitle = item.optString("subtitle"),
                            balance = item.optString("balance"),
                            fiatEstimate = item.optString("fiatEstimate"),
                            source = item.optString("source"),
                            riskLevel = runCatching {
                                ConsumerRiskLevel.valueOf(item.optString("riskLevel"))
                            }.getOrDefault(ConsumerRiskLevel.Notice),
                            assetId = item.optString("assetId"),
                            symbol = item.optString("symbol"),
                            network = item.optString("network"),
                            assetAddress = item.optString("assetAddress").ifBlank { null },
                            valueUsd = item.optDouble("valueUsd", 0.0),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun buildPortfolioAssetActivity(holding: PortfolioHolding): List<UnifiedActivityItem> {
        val activeWallet = wallet
        val octraActivity = when {
            holding.assetId == "octra:native" -> recentTransactions.map { tx ->
                val outgoing = activeWallet?.let { tx.isOutgoing(it.address) } ?: false
                UnifiedActivityItem(
                    id = "octra:${tx.hash}",
                    title = if (outgoing) "Sent OCT" else "Received OCT",
                    subtitle = "${tx.opType.lowercase()} - ${tx.hash.take(10)}...",
                    amount = "${if (outgoing) "-" else "+"}${formatOctraAmount(tx.amount)} OCT",
                    status = tx.status.ifBlank { "confirmed" },
                    timestamp = normalizeActivityTimestamp(tx.timestamp.toLong()),
                    glyph = if (outgoing) "send" else "receive",
                    source = "Octra",
                    explorerUrl = explorerTxUrl(tx.hash),
                    assetSymbol = "OCT",
                )
            }
            holding.assetId.startsWith("octra:") -> recentTransactions
                .filter { tx ->
                    tx.opType.contains("token", ignoreCase = true) ||
                        tx.opType.contains("call", ignoreCase = true) ||
                        tx.message?.contains(holding.symbol, ignoreCase = true) == true ||
                        tx.message?.contains(holding.assetAddress.orEmpty(), ignoreCase = true) == true
                }
                .map { tx ->
                    UnifiedActivityItem(
                        id = "octra:${holding.assetId}:${tx.hash}",
                        title = "${holding.symbol} contract activity",
                        subtitle = "${tx.opType.lowercase()} - ${tx.hash.take(10)}...",
                        amount = "${formatOctraAmount(tx.amount)} OCT gas",
                        status = tx.status.ifBlank { "confirmed" },
                        timestamp = normalizeActivityTimestamp(tx.timestamp.toLong()),
                        glyph = "assets",
                        source = "Octra",
                        explorerUrl = explorerTxUrl(tx.hash),
                        assetSymbol = holding.symbol,
                    )
                }
            else -> emptyList()
        }

        val network = activeEvmNetwork ?: runCatching { evmRepository.activeNetwork() }.getOrNull()
        val evmAddress = evmAccount?.address
        val evmActivity = if (holding.assetId.startsWith("evm:") && network != null) {
            evmTransactions
                .filter { tx ->
                    when {
                        holding.assetId.startsWith("evm:native:") ->
                            tx.tokenAddress.isNullOrBlank() && tx.symbol.equals(holding.symbol, ignoreCase = true)
                        holding.assetId.startsWith("evm:nft:") ->
                            tx.type.contains("nft", ignoreCase = true) || tx.symbol.equals("NFT", ignoreCase = true)
                        else ->
                            tx.tokenAddress?.equals(holding.assetAddress.orEmpty(), ignoreCase = true) == true ||
                                tx.symbol.equals(holding.symbol, ignoreCase = true)
                    }
                }
                .map { tx ->
                    val outgoing = evmAddress?.let { tx.isOutgoing(it) } ?: false
                    UnifiedActivityItem(
                        id = "evm:${holding.assetId}:${tx.hash}:${tx.symbol}",
                        title = if (outgoing) "Sent ${tx.symbol}" else "Received ${tx.symbol}",
                        subtitle = "${tx.type.lowercase()} - ${tx.hash.take(10)}...",
                        amount = "${if (outgoing) "-" else "+"}${tx.value} ${tx.symbol}",
                        status = tx.status.ifBlank { "confirmed" },
                        timestamp = normalizeActivityTimestamp(tx.timestamp),
                        glyph = if (outgoing) "send" else "receive",
                        source = network.name,
                        explorerUrl = evmRepository.explorerTxUrl(network, tx.hash),
                        assetSymbol = tx.symbol,
                    )
                }
        } else {
            emptyList()
        }

        return (octraActivity + evmActivity)
            .distinctBy { it.id }
            .sortedByDescending { it.timestamp }
            .take(50)
    }

    private fun portfolioDataLoading(): Boolean =
        tokenBalance.isLoading || assetsLoading || evmLoading || evmAssetsLoading || priceLoading

    private fun syncPortfolioHoldingsAfterDataChange() {
        val selectedAssetId = selectedPortfolioHolding?.assetId
        refreshPortfolioHoldings()
        if (selectedAssetId != null) {
            val nextHolding = portfolioHoldings.firstOrNull { it.assetId == selectedAssetId } ?: selectedPortfolioHolding
            selectedPortfolioHolding = nextHolding
            portfolioAssetActivity = nextHolding?.let(::buildPortfolioAssetActivity).orEmpty()
        }
    }

    fun refreshApprovalReviews() {
        val activeWallet = wallet ?: return showError("Unlock wallet first.")
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        val owner = evmAccount?.address ?: evmRepository.deriveAddress(activeWallet)
        val tokens = evmTokens.filter { it.chainId == network.chainId }
        val requestSerial = ++approvalReviewRequestSerial
        approvalReviewLoading = true
        scope.launch {
            try {
                val items = withContext(Dispatchers.IO) {
                    buildList {
                        tokens.forEach { token ->
                            approvalSpenders().forEach spenderLoop@{ spender ->
                                val allowance = runCatching {
                                    evmRepository.erc20Allowance(token, owner, spender.address, network)
                                }.getOrElse { error ->
                                    add(
                                        ApprovalReviewItem(
                                            id = "${token.address}:${spender.address}",
                                            tokenSymbol = token.symbol,
                                            tokenName = token.name,
                                            tokenAddress = token.address,
                                            spenderName = spender.name,
                                            spenderAddress = spender.address,
                                            allowance = "Unavailable",
                                            networkName = network.name,
                                            level = ConsumerRiskLevel.Notice,
                                            error = error.message ?: "Allowance lookup failed.",
                                        ),
                                    )
                                    null
                                } ?: return@spenderLoop
                                if (allowance > BigInteger.ZERO) {
                                    add(
                                        ApprovalReviewItem(
                                            id = "${token.address}:${spender.address}",
                                            tokenSymbol = token.symbol,
                                            tokenName = token.name,
                                            tokenAddress = token.address,
                                            spenderName = spender.name,
                                            spenderAddress = spender.address,
                                            allowance = "${formatTokenUnits(allowance, token.decimals)} ${token.symbol}",
                                            networkName = network.name,
                                            level = if (allowance > ApprovalLargeAllowanceRaw) ConsumerRiskLevel.Warning else ConsumerRiskLevel.Notice,
                                        ),
                                    )
                                }
                            }
                        }
                    }
                }
                if (requestSerial == approvalReviewRequestSerial) {
                    approvalReviews = items.sortedByDescending { it.level.ordinal }
                }
            } finally {
                if (requestSerial == approvalReviewRequestSerial) approvalReviewLoading = false
            }
        }
    }

    fun revokeApproval(review: ApprovalReviewItem) {
        val activeWallet = wallet ?: return showError("Unlock wallet first.")
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        val token = evmTokens.firstOrNull {
            it.chainId == network.chainId && it.address.equals(review.tokenAddress, ignoreCase = true)
        } ?: return showError("Token is no longer available.")
        beginSubmittedTransaction(review.spenderAddress, 0.0)
        runBusy {
            val result = withContext(Dispatchers.IO) {
                evmRepository.revokeErc20Allowance(activeWallet, token, review.spenderAddress, network)
            }
            if (!result.success) {
                updateSubmittedTransactionResult(
                    hash = result.hash,
                    recipientAddress = review.spenderAddress,
                    amount = 0.0,
                    status = TransactionFinality.Rejected,
                    reason = result.error ?: "Revoke rejected.",
                )
                return@runBusy
            }
            val hash = result.hash ?: throw IllegalStateException("Revoke did not return a transaction hash.")
            evmSendPendingTransaction = result
            updateSubmittedTransactionResult(hash, review.spenderAddress, 0.0, TransactionFinality.Accepted)
            pollEvmTransaction(hash, network)
            refreshApprovalReviews()
        }
    }

    fun addDomainGuardRule(input: String, trusted: Boolean) {
        val next = runCatching { consumerSettingsRepository.saveDomainRule(input, trusted) }.getOrElse {
            showError(it.message ?: "Enter a valid domain.")
            return
        }
        domainGuardRules = next
        val host = runCatching { DAppUrlPolicy.normalizeDomainHost(input) }.getOrDefault("Domain")
        showMessage(if (trusted) "$host trusted." else "$host blocked.")
    }

    fun removeDomainGuardRule(host: String) {
        domainGuardRules = consumerSettingsRepository.removeDomainRule(host)
        showMessage("Domain rule removed.")
    }

    fun saveFundingProvider(id: String, url: String, enabled: Boolean) {
        fundingProviders = runCatching {
            consumerSettingsRepository.saveFundingProvider(id, url, enabled)
        }.getOrElse { error ->
            showError(error.message ?: "Enter a valid HTTPS provider URL.")
            return
        }
        showMessage("Funding provider updated.")
    }

    fun openFundingProvider(provider: FundingProvider) {
        if (!provider.enabled || provider.url.isBlank()) {
            showError("Configure and enable this provider first.")
            return
        }
        openDAppBrowser(provider.name, provider.url)
    }

    private fun refreshUnifiedActivity() {
        val activeWallet = wallet
        val octraItems = recentTransactions.map { tx ->
            val outgoing = activeWallet?.let { tx.isOutgoing(it.address) } ?: false
            UnifiedActivityItem(
                id = "octra:${tx.hash}",
                title = if (outgoing) "Sent OCT" else "Received OCT",
                subtitle = "${tx.opType.lowercase()} - ${tx.hash.take(10)}...",
                amount = "${if (outgoing) "-" else "+"}${formatOctraAmount(tx.amount)} OCT",
                status = tx.status.ifBlank { "confirmed" },
                timestamp = normalizeActivityTimestamp(tx.timestamp.toLong()),
                glyph = if (outgoing) "send" else "receive",
                source = "Octra",
                explorerUrl = explorerTxUrl(tx.hash),
                assetSymbol = "OCT",
            )
        }
        val evmItems = evmTransactions.map { tx ->
            val outgoing = evmAccount?.let { tx.isOutgoing(it.address) } ?: false
            UnifiedActivityItem(
                id = "evm:${tx.hash}",
                title = if (outgoing) "Sent ${tx.symbol}" else "Received ${tx.symbol}",
                subtitle = "${tx.type.lowercase()} - ${tx.hash.take(10)}...",
                amount = "${if (outgoing) "-" else "+"}${tx.value} ${tx.symbol}",
                status = tx.status.ifBlank { "confirmed" },
                timestamp = normalizeActivityTimestamp(tx.timestamp),
                glyph = if (outgoing) "send" else "receive",
                source = activeEvmNetwork?.name ?: "EVM",
                explorerUrl = activeEvmNetwork?.let { evmRepository.explorerTxUrl(it, tx.hash) },
                assetSymbol = tx.symbol,
            )
        }
        val bridgeItems = if (currentBridgeNetworkWarning() == null) {
            bridgeHistory.map { item ->
                UnifiedActivityItem(
                    id = "bridge:${item.id}",
                    title = if (item.direction == BridgeDirection.OctToWoct) "Bridge OCT to wOCT" else "Bridge wOCT to OCT",
                    subtitle = item.message.ifBlank { item.direction.name },
                    amount = "${item.amount} ${if (item.direction == BridgeDirection.OctToWoct) "OCT" else "wOCT"}",
                    status = item.status,
                    timestamp = normalizeActivityTimestamp(maxOf(item.updatedAt, item.createdAt)),
                    glyph = "bridge",
                    source = "Bridge",
                    explorerUrl = bridgeHistoryExplorerUrl(item),
                    assetSymbol = if (item.direction == BridgeDirection.OctToWoct) "OCT" else "wOCT",
                )
            }
        } else {
            emptyList()
        }
        val dAppItems = dApps.map { session ->
            UnifiedActivityItem(
                id = "dapp:${session.url}:${session.connectedAt}",
                title = "Connected dApp",
                subtitle = session.name,
                amount = "${session.permissions.size} permissions",
                status = "connected",
                timestamp = normalizeActivityTimestamp(session.connectedAt),
                glyph = "dapp",
                source = session.network,
            )
        }
        unifiedActivity = (octraItems + evmItems + bridgeItems + dAppItems)
            .sortedByDescending { it.timestamp }
            .take(80)
    }

    private fun bridgeHistoryExplorerUrl(item: BridgeHistoryItem): String? =
        item.evmTxHash?.let { bridgeRepository.explorerTxUrl(it) }
            ?: item.octraTxHash?.let { bridgeRepository.octraExplorerTxUrl(it) }

    private fun buildSecurityRisks(): List<ConsumerRiskItem> {
        val risks = mutableListOf<ConsumerRiskItem>()
        val method = currentSecurityMethod
        risks += if (method == SecurityMethod.Biometric) {
            ConsumerRiskItem("Biometric lock enabled", "Sensitive actions require Android biometric authentication.", ConsumerRiskLevel.Safe)
        } else if (method == SecurityMethod.Pin) {
            ConsumerRiskItem("PIN fallback active", "Use a strong PIN and avoid sharing your unlocked device.", ConsumerRiskLevel.Notice)
        } else {
            ConsumerRiskItem("Wallet security not configured", "Set biometric or PIN protection before storing value.", ConsumerRiskLevel.Critical)
        }

        val activeWallet = wallet
        val backupLabel = activeWallet?.type?.let {
            when (it) {
                WalletType.Generated -> "Generated wallet"
                WalletType.ImportedMnemonic -> "Imported mnemonic"
                WalletType.ImportedPrivateKey -> "Imported Octra key"
                WalletType.ImportedEthereumPrivateKey -> "Imported Ethereum key"
            }
        } ?: "No wallet"
        risks += ConsumerRiskItem(
            title = "Backup keys available",
            description = "$backupLabel recovery material is stored locally. Reveal it only in Settings > Backup Keys.",
            level = ConsumerRiskLevel.Notice,
        )

        val status = rpcStatus
        risks += when (status?.state) {
            RpcStatus.State.Connected -> ConsumerRiskItem("Octra RPC connected", status.url, ConsumerRiskLevel.Safe)
            RpcStatus.State.Failed -> ConsumerRiskItem("Octra RPC warning", status.message ?: "Active RPC is unavailable.", ConsumerRiskLevel.Warning)
            RpcStatus.State.Checking -> ConsumerRiskItem("Network check in progress", "poctra is checking the active RPC.", ConsumerRiskLevel.Notice)
            null -> ConsumerRiskItem("Network not checked", "Refresh the wallet to verify RPC status.", ConsumerRiskLevel.Notice)
        }

        val dangerousDApps = dApps.filter { session ->
            session.permissions.any { it == "contract_calls" } || DAppUrlPolicy.isSuspiciousOrigin(session.url)
        }
        risks += when {
            dApps.isEmpty() -> ConsumerRiskItem("No connected dApps", "No site currently has wallet permissions.", ConsumerRiskLevel.Safe)
            dangerousDApps.isEmpty() -> ConsumerRiskItem("Connected dApps", "${dApps.size} site(s) can read this wallet address.", ConsumerRiskLevel.Notice)
            else -> ConsumerRiskItem("Review dApp permissions", "${dangerousDApps.size} connected site(s) have contract or suspicious permissions.", ConsumerRiskLevel.Warning)
        }

        if (activeIdentityProvider == null) {
            risks += ConsumerRiskItem("Octra Identity unavailable", "Add an identity provider to resolve foo.oct recipients.", ConsumerRiskLevel.Notice)
        } else {
            risks += ConsumerRiskItem("Octra Identity ready", activeIdentityProvider?.contractAddress.orEmpty(), ConsumerRiskLevel.Safe)
        }

        if (addressBookContacts.isEmpty()) {
            risks += ConsumerRiskItem("No trusted contacts", "Save frequent recipients to reduce address mistakes.", ConsumerRiskLevel.Notice)
        }

        return risks.sortedByDescending { it.level.ordinal }
    }

    fun octraRecipientRiskItems(
        recipientInput: String,
        resolvedAddress: String?,
        amount: String,
    ): List<ConsumerRiskItem> {
        val cleanRecipient = recipientInput.trim()
        if (cleanRecipient.isBlank()) return emptyList()
        val address = resolvedAddress?.ifBlank { null } ?: cleanRecipient
        val amountValue = amount.replace(",", ".").toDoubleOrNull() ?: 0.0
        val knownContact = addressBookContacts.any {
            it.address.equals(address, ignoreCase = true) ||
                it.identityName?.equals(cleanRecipient, ignoreCase = true) == true
        }
        val items = mutableListOf<ConsumerRiskItem>()
        if (!knownContact && wallet?.address?.equals(address, ignoreCase = true) != true) {
            items += ConsumerRiskItem("New recipient", "This address is not in your Address Book.", ConsumerRiskLevel.Notice)
        }
        if (isIdentityNameInput(cleanRecipient.lowercase())) {
            items += ConsumerRiskItem("Identity resolved", "$cleanRecipient resolves before sending.", ConsumerRiskLevel.Safe)
        }
        if (amountValue > availableBalance && amountValue > 0.0) {
            items += ConsumerRiskItem("Amount exceeds balance", "Max available balance is ${formatOctraAmount(availableBalance)} OCT before fee.", ConsumerRiskLevel.Warning)
        }
        if (activeRpcNetwork?.networkType == "devnet") {
            items += ConsumerRiskItem("Devnet active", "This transfer uses the active Octra devnet RPC.", ConsumerRiskLevel.Notice)
        }
        return items
    }

    fun evmRecipientRiskItems(recipientInput: String, amount: String): List<ConsumerRiskItem> {
        val cleanRecipient = recipientInput.trim()
        if (cleanRecipient.isBlank()) return emptyList()
        val amountValue = amount.replace(",", ".").toBigDecimalOrNull() ?: BigDecimal.ZERO
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        val items = mutableListOf<ConsumerRiskItem>()
        if (cleanRecipient.endsWith(".eth", ignoreCase = true)) {
            items += ConsumerRiskItem("ENS lookup", "$cleanRecipient will resolve before sending.", ConsumerRiskLevel.Safe)
        } else if (!cleanRecipient.startsWith("0x", ignoreCase = true) || cleanRecipient.length != 42) {
            items += ConsumerRiskItem("Recipient format", "Use a valid 0x address or ENS name.", ConsumerRiskLevel.Warning)
        }
        val nativeBalance = evmAccount?.balance?.replace(",", ".")?.toBigDecimalOrNull() ?: BigDecimal.ZERO
        if (evmSelectedSendToken == null && evmSelectedSendNft == null && amountValue > nativeBalance && amountValue > BigDecimal.ZERO) {
            items += ConsumerRiskItem("Amount exceeds balance", "Available balance is ${evmAccount?.balance ?: "0"} ${network.symbol}.", ConsumerRiskLevel.Warning)
        }
        if (network.isTestnet) {
            items += ConsumerRiskItem("Testnet active", "This transaction uses ${network.name}.", ConsumerRiskLevel.Notice)
        }
        return items
    }

    fun evmSendPreflightItems(
        recipientInput: String,
        resolvedAddress: String?,
        amount: String,
    ): List<ConsumerRiskItem> {
        val cleanRecipient = recipientInput.trim()
        if (cleanRecipient.isBlank()) return emptyList()
        val amountValue = amount.replace(",", ".").toBigDecimalOrNull() ?: BigDecimal.ZERO
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        val account = evmAccount
        val items = mutableListOf<ConsumerRiskItem>()

        items += evmRecipientRiskItems(cleanRecipient, amount)

        if (resolvedAddress?.startsWith("0x", ignoreCase = true) == true) {
            items += ConsumerRiskItem("Recipient resolved", "${shortAddress(resolvedAddress)} on ${network.name}.", ConsumerRiskLevel.Safe)
        }

        items += if (account?.connected == true) {
            ConsumerRiskItem("RPC ready", "${network.name} gas price ${account.gasPriceGwei} Gwei.", ConsumerRiskLevel.Safe)
        } else {
            ConsumerRiskItem("RPC not ready", account?.message ?: "Refresh EVM Account before sending.", ConsumerRiskLevel.Warning)
        }

        val gasReady = evmSendGasEstimate
        items += when {
            evmSendGasLoading -> ConsumerRiskItem("Gas check running", "poctra is estimating gas for this transfer.", ConsumerRiskLevel.Notice)
            gasReady != null -> ConsumerRiskItem("Gas estimate ready", "${gasReady.label}: ${gasReady.gasLimit} gas.", ConsumerRiskLevel.Safe)
            else -> ConsumerRiskItem("Gas not reviewed", "Open the review sheet to estimate gas before signing.", ConsumerRiskLevel.Notice)
        }

        when {
            evmSelectedSendNft != null -> {
                val nft = evmSelectedSendNft
                if (nft?.owned == true) {
                    items += ConsumerRiskItem("NFT ownership checked", "${nft.symbol} #${nft.tokenId} is marked as owned.", ConsumerRiskLevel.Safe)
                } else {
                    items += ConsumerRiskItem("NFT not owned", "This NFT is not available in the active account.", ConsumerRiskLevel.Warning)
                }
            }
            evmSelectedSendToken != null -> {
                val token = evmSelectedSendToken
                val tokenBalance = token?.balance?.replace(",", ".")?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                if (amountValue > BigDecimal.ZERO && amountValue <= tokenBalance) {
                    items += ConsumerRiskItem("Token balance ready", "Available ${token?.balance ?: "0"} ${token?.symbol ?: "TOKEN"}.", ConsumerRiskLevel.Safe)
                } else if (amountValue > tokenBalance) {
                    items += ConsumerRiskItem("Token balance low", "Available ${token?.balance ?: "0"} ${token?.symbol ?: "TOKEN"}.", ConsumerRiskLevel.Warning)
                }
            }
            else -> {
                val nativeBalance = account?.balance?.replace(",", ".")?.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val gasReserve = evmGasCostAmount(null)
                if (amountValue > BigDecimal.ZERO && amountValue.add(gasReserve) <= nativeBalance) {
                    items += ConsumerRiskItem("Balance includes gas", "Amount plus estimated gas fits current ${network.symbol} balance.", ConsumerRiskLevel.Safe)
                }
            }
        }

        return items.distinctBy { "${it.level}:${it.title}:${it.description}" }
            .sortedByDescending { it.level.ordinal }
    }

    fun dAppRiskItems(session: DAppSession): List<ConsumerRiskItem> {
        val items = mutableListOf<ConsumerRiskItem>()
        if (DAppUrlPolicy.isSuspiciousOrigin(session.url)) {
            items += ConsumerRiskItem("Suspicious origin", "Non-HTTPS, IP, or punycode origins deserve extra caution.", ConsumerRiskLevel.Warning)
        }
        if ("contract_calls" in session.permissions) {
            items += ConsumerRiskItem("Contract calls allowed", "This dApp can request contract transactions.", ConsumerRiskLevel.Warning)
        }
        if (items.isEmpty()) {
            items += ConsumerRiskItem("Read-only connection", "This dApp can read wallet metadata until revoked.", ConsumerRiskLevel.Notice)
        }
        return items
    }

    private fun tokenFiatEstimate(token: EvmToken): String {
        val balance = token.balance.replace(",", ".").toBigDecimalOrNull()
        return when {
            token.symbol.equals("USDT", ignoreCase = true) && balance != null -> "~ ${formatUsd(balance.toDouble())} USD"
            token.symbol.equals("wOCT", ignoreCase = true) && balance != null && octUsdPrice != null -> "~ ${formatUsd(balance.toDouble() * (octUsdPrice ?: 0.0))} USD"
            else -> "Token balance"
        }
    }

    private fun parseUsdEstimate(value: String): Double {
        val match = Regex("""([0-9][0-9,]*\.?[0-9]*)""").find(value) ?: return 0.0
        return match.value.replace(",", "").toDoubleOrNull() ?: 0.0
    }

    private data class ApprovalSpender(val name: String, val address: String)

    private fun approvalSpenders(): List<ApprovalSpender> = listOf(
        ApprovalSpender("Uniswap Permit2", Permit2Address),
        ApprovalSpender("Uniswap Router", UniversalRouterAddress),
        ApprovalSpender("Octra wOCT Bridge", BridgeRepository.WoctBridgeContract),
    ).distinctBy { it.address.lowercase() }

    private fun formatTokenUnits(raw: BigInteger, decimals: Int): String {
        if (raw == BigInteger.ZERO) return "0"
        val divisor = BigInteger.TEN.pow(decimals.coerceIn(0, 36)).toBigDecimal()
        return raw.toBigDecimal()
            .divide(divisor, 8, RoundingMode.DOWN)
            .stripTrailingZeros()
            .toPlainString()
    }

    private fun normalizeActivityTimestamp(value: Long): Long =
        if (value in 1 until 10_000_000_000L) value * 1000L else value

    private fun formatOctraAmount(value: Double): String =
        BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

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
                val appLabel = dAppSessionLabelForOrigin(origin)
                when (method) {
                    "octra_requestAccounts" -> {
                        val permissions = requestedDAppPermissions(request.opt("params"))
                        queueDAppApproval(
                            origin = origin,
                            title = "Connect dApp",
                            message = "$appLabel wants to connect to ${shortAddress(activeWallet.address)}.",
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
                        val needsContractPermission = !session.permissions.contains("contract_calls")
                        val call = contractCallRequest(request.opt("params"))
                        val contract = call.getString("address")
                        val callMethod = call.getString("method")
                        val amountOu = call.optString("amount", "0").ifBlank { "0" }
                        val params = call.optJSONArray("params") ?: JSONArray()
                        val providedFee = call.optString("fee").takeIf { it.isNotBlank() }
                        val permissionDetails = if (needsContractPermission) {
                            listOf(DAppRequestDetail("Permission", "Grant contract_calls for this dApp."))
                        } else {
                            emptyList()
                        }
                        queueDAppApproval(
                            origin = origin,
                            title = "Approve contract call",
                            message = "$appLabel wants to call $callMethod on ${shortAddress(contract)} with $amountOu ou.",
                            details = listOf(
                                DAppRequestDetail("From", activeWallet.address),
                                DAppRequestDetail("Contract", contract),
                                DAppRequestDetail("Method", callMethod),
                                DAppRequestDetail("Amount", "$amountOu ou (${formatOuAmount(amountOu)} OCT)"),
                                DAppRequestDetail("Fee", providedFee?.let { "$it ou" } ?: "Auto"),
                                DAppRequestDetail("Params", if (params.length() == 0) "[]" else params.toString(2)),
                            ) + permissionDetails,
                            approveLabel = "Approve",
                            onSuccess = onSuccess,
                            onError = onError,
                        ) {
                            if (needsContractPermission) {
                                authorizeDApp(origin, activeWallet.address, (session.permissions + "contract_calls").distinct())
                            }
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

    fun handleCircleBridgeRequest(
        payload: String,
        onSuccess: (String) -> Unit,
        onError: (Int, String) -> Unit,
    ) {
        scope.launch {
            try {
                require(payload.length <= MaxCircleBridgePayloadBytes) { "Circle request payload is too large." }
                val request = JSONObject(payload)
                val method = request.optString("method")
                val requestPayload = request.opt("payload") ?: JSONObject()
                val requestedCircleId = request.optString("circleId").ifBlank {
                    (requestPayload as? JSONObject)?.optString("circle_id").orEmpty()
                }
                selectedCircleTarget?.circleId?.let { activeCircleId ->
                    if (requestedCircleId.isNotBlank() && !requestedCircleId.equals(activeCircleId, ignoreCase = true)) {
                        throw SecurityException("Circle target override denied.")
                    }
                }
                val result = withContext(Dispatchers.IO) {
                    resolveCircleBridgeRequest(method, requestPayload)
                }
                onSuccess(result)
            } catch (exception: Exception) {
                val (code, message) = dAppError(exception)
                onError(code, message)
            }
        }
    }

    private fun resolveCircleBridgeRequest(method: String, payload: Any): String {
        val activeCircle = selectedCircleTarget
        val payloadObject = payload as? JSONObject ?: JSONObject()
        return when (method) {
            "circle.context" -> JSONObject()
                .put("circle_id", activeCircle?.circleId ?: payloadObject.optString("circle_id"))
                .put("path", activeCircle?.path ?: payloadObject.optString("path", "/index.html"))
                .put("uri", activeCircle?.uri ?: payloadObject.optString("uri"))
                .put("network", activeRpcNetwork?.networkType ?: networkFor(currentRpcUrl))
                .put("rpc", currentRpcUrl)
                .toString()
            "program.info" -> {
                val circleId = circleBridgeTarget(payloadObject)
                rpcRepository.fetchCircleInfo(circleId).toString()
            }
            "program.view" -> {
                val viewMethod = payloadObject.optString("method")
                val params = payloadObject.optJSONArray("params") ?: JSONArray()
                val address = payloadObject.optString("address").ifBlank { payloadObject.optString("circle_id") }.ifBlank {
                    activeCircle?.circleId.orEmpty()
                }
                jsonLiteral(rpcRepository.circleProgramView(address, viewMethod, params))
            }
            "rpc" -> circleRpcResponse(payload)
            "program.call" -> throw SecurityException("Circle program writes are not supported yet.")
            else -> throw NoSuchMethodException("Unsupported circle method: $method")
        }
    }

    private fun circleBridgeTarget(payload: JSONObject): String {
        val target = payload.optString("circle_id")
            .ifBlank { payload.optString("address") }
            .ifBlank { selectedCircleTarget?.circleId.orEmpty() }
        require(target.startsWith("oct") && target.length == 47) { "Enter a valid Octra circle address." }
        return target
    }

    private fun circleRpcResponse(payload: Any): String {
        if (payload is JSONArray) {
            return rpcRepository.circleReadRpcBatch(payload).toString()
        }
        val payloadObject = payload as? JSONObject ?: JSONObject()
        return circleRpcObjectResponse(payloadObject).toString()
    }

    private fun circleRpcObjectResponse(payload: JSONObject): JSONObject {
        val id = payload.opt("id") ?: JSONObject.NULL
        return try {
            val method = payload.optString("method")
            val params = payload.optJSONArray("params") ?: JSONArray()
            val result = rpcRepository.circleReadRpc(method, params)
            JSONObject()
                .put("jsonrpc", payload.optString("jsonrpc", "2.0").ifBlank { "2.0" })
                .put("id", id)
                .put("result", result ?: JSONObject.NULL)
        } catch (exception: Exception) {
            JSONObject()
                .put("jsonrpc", payload.optString("jsonrpc", "2.0").ifBlank { "2.0" })
                .put("id", id)
                .put(
                    "error",
                    JSONObject()
                        .put("code", dAppError(exception).first)
                        .put("message", exception.message ?: "Circle RPC failed."),
                )
        }
    }

    private fun jsonLiteral(value: Any?): String = when (value) {
        null,
        JSONObject.NULL -> "null"
        is JSONObject,
        is JSONArray -> value.toString()
        is Number,
        is Boolean -> value.toString()
        else -> JSONObject.quote(value.toString())
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
        if (privacyOperationActive) {
            showError("Finish the active Privacy process before switching wallets.")
            return
        }
        val current = vault ?: return
        val next = current.withActive(address)
        vault = next
        wallet = next.activeWallet
        secureStore.setActiveAddress(address)
        resetPrivacyState()
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
        val requestRpcUrl = currentRpcUrl
        val walletAddress = activeWallet.address
        if (!contractAddress.startsWith("oct") || contractAddress.length != 47) {
            showError("Enter a valid Octra contract address (oct..., 47 chars).")
            return
        }
        scope.launch {
            assetLookupLoading = true
            clearError()
            try {
                val preview = rpcRepository.lookupOctraToken(contractAddress, walletAddress, requestRpcUrl)
                if (!isActiveRpcSnapshot(walletAddress, requestRpcUrl)) return@launch
                assetPreview = preview
                if (assetPreview == null) showError("Token not found or not a valid OCS01 contract.")
            } catch (exception: Exception) {
                if (!isActiveRpcSnapshot(walletAddress, requestRpcUrl)) return@launch
                showError(exception.message ?: "Token lookup failed.")
            } finally {
                assetLookupLoading = false
            }
        }
    }

    fun addPreviewAsset() {
        val token = assetPreview ?: return
        assetRepository.saveTokenAddress(activeRpcNetwork?.networkType ?: networkFor(currentRpcUrl), token.address)
        assetPreview = null
        dashboardTab = DashboardTab.Assets
        route = AppRoute.Dashboard
        refreshAssets()
        showMessage("${token.symbol} added to assets")
    }

    fun removeAsset(token: OctraToken) {
        assetRepository.removeTokenAddress(activeRpcNetwork?.networkType ?: networkFor(currentRpcUrl), token.address)
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
        val requestSerial = ++feeRequestSerial
        val requestRpcUrl = currentRpcUrl
        val requestWalletAddress = wallet?.address.orEmpty()
        feeLoading = true
        scope.launch {
            try {
                val fee = withContext(Dispatchers.IO) {
                    rpcRepository.fetchRecommendedFee(opType, requestRpcUrl)
                }
                if (requestSerial != feeRequestSerial) return@launch
                if (requestWalletAddress.isNotBlank() && !isActiveRpcSnapshot(requestWalletAddress, requestRpcUrl)) return@launch
                recommendedFeeOu = fee
            } catch (_: Exception) {
                if (requestSerial != feeRequestSerial) return@launch
                recommendedFeeOu = 1000L
            } finally {
                if (requestSerial == feeRequestSerial) feeLoading = false
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
        refreshEvmDashboardSnapshot()
    }

    private fun refreshEvmDashboardSnapshot() {
        val activeWallet = wallet ?: return
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        val accountSerial = ++evmAccountRequestSerial
        val assetsSerial = ++evmAssetsRequestSerial
        val historySerial = ++evmHistoryRequestSerial
        val walletAddress = activeWallet.address
        evmLoading = true
        evmAssetsLoading = true
        evmHistoryLoading = true
        scope.launch {
            try {
                val account = withContext(Dispatchers.IO) {
                    evmRepository.fetchAccount(activeWallet, network)
                }
                if (!isActiveEvmSnapshot(walletAddress, network.id)) return@launch
                if (accountSerial == evmAccountRequestSerial) {
                    evmAccount = account
                    evmLoading = false
                    syncPortfolioHoldingsAfterDataChange()
                }

                val savedTokenAddresses = withContext(Dispatchers.IO) {
                    evmRepository.savedTokens(network.chainId).map { it.address }
                }
                val (tokensResult, nftsResult, transactionsResult) = coroutineScope {
                    val tokens = async(Dispatchers.IO) {
                        runCatching { evmRepository.refreshTokens(account.address, network, persistBalances = false) }
                    }
                    val nfts = async(Dispatchers.IO) {
                        runCatching { evmRepository.refreshNfts(account.address, network, persistOwnership = false) }
                    }
                    val transactions = async(Dispatchers.IO) {
                        runCatching {
                            evmRepository.fetchTransactions(
                                address = account.address,
                                network = network,
                                tokenAddresses = savedTokenAddresses,
                            )
                        }
                    }
                    Triple(tokens.await(), nfts.await(), transactions.await())
                }

                if (!isActiveEvmSnapshot(walletAddress, network.id)) return@launch
                if (assetsSerial == evmAssetsRequestSerial) {
                    tokensResult.onSuccess { evmTokens = it }
                    nftsResult.onSuccess { evmNfts = it }
                    evmAssetsLoading = false
                }
                if (historySerial == evmHistoryRequestSerial) {
                    transactionsResult.onSuccess { evmTransactions = it }
                    evmHistoryLoading = false
                }
                syncPortfolioHoldingsAfterDataChange()
            } catch (_: Exception) {
                if (isActiveEvmSnapshot(walletAddress, network.id)) {
                    syncPortfolioHoldingsAfterDataChange()
                }
            } finally {
                if (accountSerial == evmAccountRequestSerial) evmLoading = false
                if (assetsSerial == evmAssetsRequestSerial) evmAssetsLoading = false
                if (historySerial == evmHistoryRequestSerial) evmHistoryLoading = false
            }
        }
    }

    fun refreshEvmAccount() {
        val activeWallet = wallet ?: return
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        val requestSerial = ++evmAccountRequestSerial
        val walletAddress = activeWallet.address
        evmLoading = true
        scope.launch {
            try {
                val account = withContext(Dispatchers.IO) { evmRepository.fetchAccount(activeWallet, network) }
                if (requestSerial != evmAccountRequestSerial || !isActiveEvmSnapshot(walletAddress, network.id)) return@launch
                evmAccount = account
                syncPortfolioHoldingsAfterDataChange()
            } catch (_: Exception) {
                if (requestSerial == evmAccountRequestSerial && isActiveEvmSnapshot(walletAddress, network.id)) {
                    syncPortfolioHoldingsAfterDataChange()
                }
            } finally {
                if (requestSerial == evmAccountRequestSerial) evmLoading = false
            }
        }
    }

    fun refreshEvmAssets() {
        val activeWallet = wallet ?: return
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        val requestSerial = ++evmAssetsRequestSerial
        val walletAddress = activeWallet.address
        val expectedAddress = evmRepository.deriveAddress(activeWallet)
        evmAssetsLoading = true
        scope.launch {
            try {
                val account = evmAccount?.takeIf { it.address.equals(expectedAddress, ignoreCase = true) }
                    ?: withContext(Dispatchers.IO) { evmRepository.fetchAccount(activeWallet, network) }
                if (requestSerial != evmAssetsRequestSerial || !isActiveEvmSnapshot(walletAddress, network.id)) return@launch
                if (evmAccount?.address?.equals(account.address, ignoreCase = true) != true) {
                    evmAccount = account
                }
                val tokens = withContext(Dispatchers.IO) {
                    evmRepository.refreshTokens(account.address, network, persistBalances = false)
                }
                val nfts = withContext(Dispatchers.IO) {
                    evmRepository.refreshNfts(account.address, network, persistOwnership = false)
                }
                if (requestSerial != evmAssetsRequestSerial || !isActiveEvmSnapshot(walletAddress, network.id)) return@launch
                evmTokens = tokens
                evmNfts = nfts
                syncPortfolioHoldingsAfterDataChange()
            } catch (_: Exception) {
                if (requestSerial == evmAssetsRequestSerial && isActiveEvmSnapshot(walletAddress, network.id)) {
                    syncPortfolioHoldingsAfterDataChange()
                }
            } finally {
                if (requestSerial == evmAssetsRequestSerial) evmAssetsLoading = false
            }
        }
    }

    fun refreshEvmHistory() {
        val activeWallet = wallet ?: return
        val network = activeEvmNetwork ?: evmRepository.activeNetwork()
        val requestSerial = ++evmHistoryRequestSerial
        val walletAddress = activeWallet.address
        val expectedAddress = evmRepository.deriveAddress(activeWallet)
        evmHistoryLoading = true
        scope.launch {
            try {
                val account = evmAccount?.takeIf { it.address.equals(expectedAddress, ignoreCase = true) }
                    ?: withContext(Dispatchers.IO) { evmRepository.fetchAccount(activeWallet, network) }
                if (requestSerial != evmHistoryRequestSerial || !isActiveEvmSnapshot(walletAddress, network.id)) return@launch
                if (evmAccount?.address?.equals(account.address, ignoreCase = true) != true) {
                    evmAccount = account
                }
                val transactions = withContext(Dispatchers.IO) {
                    evmRepository.fetchTransactions(
                        address = account.address,
                        network = network,
                        tokenAddresses = evmRepository.savedTokens(network.chainId).map { it.address },
                    )
                }
                if (requestSerial != evmHistoryRequestSerial || !isActiveEvmSnapshot(walletAddress, network.id)) return@launch
                evmTransactions = transactions
                if (selectedPortfolioHolding != null) syncPortfolioHoldingsAfterDataChange()
            } catch (_: Exception) {
                if (requestSerial == evmHistoryRequestSerial && isActiveEvmSnapshot(walletAddress, network.id)) {
                    if (selectedPortfolioHolding != null) syncPortfolioHoldingsAfterDataChange()
                }
            } finally {
                if (requestSerial == evmHistoryRequestSerial) evmHistoryLoading = false
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
        syncPortfolioHoldingsAfterDataChange()
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
        syncPortfolioHoldingsAfterDataChange()
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
        syncPortfolioHoldingsAfterDataChange()
        evmNftPreview = null
        showMessage("${nft.symbol} #${nft.tokenId} added.")
        route = AppRoute.Evm
        refreshEvmAssets()
    }

    fun removeEvmNft(nft: EvmNft) {
        evmNfts = evmRepository.removeNft(nft)
        syncPortfolioHoldingsAfterDataChange()
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

    fun openSubmittedTransactionExplorer() {
        val transaction = submittedTransaction ?: return showError("Transaction hash is not ready.")
        val hash = transaction.hash
        if (hash.isNullOrBlank()) return showError("Transaction hash is not ready.")
        val evmNetwork = activeEvmNetwork ?: evmRepository.activeNetwork()
        val evmUrl = evmSendPendingTransaction
            ?.takeIf { it.hash == hash }
            ?.let { evmRepository.explorerTxUrl(evmNetwork, hash) }
        val url = evmUrl ?: explorerTxUrl(hash)
        if (url == null) showError("Explorer URL is not configured.")
        else openDAppBrowser("Transaction", url)
    }

    fun retryPreparedEvmSendGasEstimate() {
        if (evmSendGasLoading) return
        if (evmSendDialogRecipient.isBlank() || (evmSelectedSendNft == null && evmSendDialogAmount.isBlank())) {
            showError("Enter recipient and amount before estimating gas.")
            return
        }
        clearError()
        estimatePreparedEvmSendGas()
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
        evmSendPollSerial++
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
        privacyCrypto.close()
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
            AppRoute.SecurityCenter,
            AppRoute.PortfolioAnalytics,
            AppRoute.ApprovalDashboard,
            AppRoute.RecoveryPlan,
            AppRoute.FundingProviders,
            AppRoute.AppearanceSettings,
            AppRoute.ConnectedDApps,
            AppRoute.IdentityProviderSettings -> {
                closeSettingsDetail()
                true
            }
            AppRoute.PortfolioAssetHistory -> {
                closePortfolioAssetHistory()
                true
            }
            AppRoute.ActivityCenter -> {
                closeActivityCenter()
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
            AppRoute.PrivacyEncrypt,
            AppRoute.PrivacyDecrypt,
            AppRoute.PrivacyStealth,
            AppRoute.PrivacyClaim -> {
                closePrivacyAction()
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
            AppRoute.PaymentRequest -> {
                closePaymentRequest()
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
        }.withIdenticon()
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
        val ensured = ensureWalletIdenticons(next)
        vault = ensured
        wallet = ensured.activeWallet
        secureStore.saveWalletMetadata(ensured)
        showMessage(message)
    }

    private fun completeVaultSetup(savedVault: WalletVault) {
        activateVault(savedVault)
        pendingVault = null
        route = AppRoute.Dashboard
        refreshDashboard()
    }

    private fun activateVault(next: WalletVault): WalletVault {
        val ensured = ensureWalletIdenticons(next)
        vault = ensured
        wallet = ensured.activeWallet
        if (ensured != next) secureStore.saveWalletMetadata(ensured)
        return ensured
    }

    private fun ensureWalletIdenticons(next: WalletVault): WalletVault {
        val wallets = next.wallets.map { it.withIdenticon() }
        return if (wallets == next.wallets) next else next.copy(wallets = wallets)
    }

    private fun Wallet.withIdenticon(): Wallet {
        val label = name.ifBlank { "Wallet" }
        return if (identiconSvg.isNotBlank() && WalletIdenticon.matchesLabel(identiconSvg, address, label)) {
            this
        } else {
            copy(identiconSvg = WalletIdenticon.createSvg(address, label))
        }
    }

    private fun refreshBalance() {
        val activeWallet = wallet ?: return
        refreshBalance(activeWallet)
    }

    private fun refreshBalance(activeWallet: Wallet) = scope.launch {
        val requestSerial = ++balanceRequestSerial
        val requestRpcUrl = currentRpcUrl
        val walletAddress = activeWallet.address
        tokenBalance = tokenBalance.copy(isLoading = true, error = null)
        try {
            val snapshot = withContext(Dispatchers.IO) {
                rpcRepository.fetchBalance(walletAddress, requestRpcUrl)
            }
            if (requestSerial != balanceRequestSerial || !isActiveRpcSnapshot(walletAddress, requestRpcUrl)) return@launch
            availableBalance = snapshot.balance
            tokenBalance = snapshot.toTokenBalance()
            updateFiatEstimate()
            syncPortfolioHoldingsAfterDataChange()
        } catch (exception: Exception) {
            if (requestSerial != balanceRequestSerial || !isActiveRpcSnapshot(walletAddress, requestRpcUrl)) return@launch
            tokenBalance = tokenBalance.copy(
                fiatEstimate = tokenBalance.fiatEstimate.takeIf { it.isNotBlank() && it != "~ $-- USD" } ?: "Balance unavailable",
                isLoading = false,
                error = exception.message ?: "Balance unavailable",
            )
            syncPortfolioHoldingsAfterDataChange()
        }
    }

    private fun refreshAssets() {
        val activeWallet = wallet ?: return
        val requestSerial = ++assetsRequestSerial
        val requestRpcUrl = currentRpcUrl
        val requestNetwork = activeRpcNetwork?.networkType ?: networkFor(requestRpcUrl)
        val walletAddress = activeWallet.address
        assetsLoading = true
        scope.launch {
            try {
                val saved = assetRepository.savedTokenAddresses(requestNetwork)
                val tokens = withContext(Dispatchers.IO) {
                    rpcRepository.fetchOctraTokens(walletAddress, saved, requestRpcUrl)
                }
                if (requestSerial != assetsRequestSerial || !isActiveRpcSnapshot(walletAddress, requestRpcUrl)) return@launch
                octraTokens = tokens
                syncPortfolioHoldingsAfterDataChange()
            } catch (_: Exception) {
                if (requestSerial != assetsRequestSerial || !isActiveRpcSnapshot(walletAddress, requestRpcUrl)) return@launch
                syncPortfolioHoldingsAfterDataChange()
            } finally {
                if (requestSerial == assetsRequestSerial) assetsLoading = false
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
                    syncPortfolioHoldingsAfterDataChange()
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
        val requestRpcUrl = currentRpcUrl
        val walletAddress = activeWallet.address
        historyLoading = true
        kotlinx.coroutines.coroutineScope {
            try {
                val transactions = withContext(Dispatchers.IO) {
                    rpcRepository.fetchTransactionHistory(walletAddress, requestLimit, requestRpcUrl)
                }
                if (requestSerial == historyRequestSerial && historyLimit == requestLimit && isActiveRpcSnapshot(walletAddress, requestRpcUrl)) {
                    recentTransactions = transactions
                    historyCanLoadMore = requestLimit < MaxHistoryLimit && transactions.size >= requestLimit
                    refreshRecentActivityIdentityNames(transactions)
                    if (selectedPortfolioHolding != null) syncPortfolioHoldingsAfterDataChange()
                }
            } catch (_: Exception) {
                if (requestSerial == historyRequestSerial && historyLimit == requestLimit && isActiveRpcSnapshot(walletAddress, requestRpcUrl)) {
                    if (recentTransactions.isEmpty()) {
                        historyCanLoadMore = false
                    }
                    if (selectedPortfolioHolding != null) syncPortfolioHoldingsAfterDataChange()
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
        beginTransactionPolling()
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
                endTransactionPolling()
            }
        }
    }

    private fun pollEvmTransaction(hash: String, network: EvmNetwork) {
        val pollSerial = ++evmSendPollSerial
        beginTransactionPolling()
        scope.launch {
            try {
                repeat(EvmSendReceiptAttempts) { attempt ->
                    if (pollSerial != evmSendPollSerial) return@launch
                    val state = withContext(Dispatchers.IO) { evmRepository.transactionReceiptStatus(hash, network) }
                    when (state.status) {
                        TransactionNetworkStatus.Confirmed -> {
                            if (pollSerial != evmSendPollSerial) return@launch
                            updateSubmittedTransaction(hash, TransactionFinality.Confirmed)
                            evmSendPendingTransaction = null
                            refreshEvmDashboard()
                            if (route == AppRoute.ApprovalDashboard) refreshApprovalReviews()
                            return@launch
                        }
                        TransactionNetworkStatus.Rejected,
                        TransactionNetworkStatus.Dropped -> {
                            if (pollSerial != evmSendPollSerial) return@launch
                            updateSubmittedTransaction(hash, TransactionFinality.Rejected, state.reason)
                            refreshEvmDashboard()
                            return@launch
                        }
                        TransactionNetworkStatus.Pending,
                        TransactionNetworkStatus.NotFound -> Unit
                    }
                    if (attempt < EvmSendReceiptAttempts - 1) delay(EvmSendReceiptIntervalMs)
                }
                if (pollSerial == evmSendPollSerial) refreshEvmDashboard()
            } finally {
                endTransactionPolling()
            }
        }
    }

    private fun pollTradeTransaction(hash: String) {
        beginTransactionPolling()
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
                endTransactionPolling()
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
                revokeDAppByOrigin(origin, activeWallet.address)
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
        if (pendingDAppRequest != null) {
            onError(-32002, "Another poctra request is waiting for approval.")
            return
        }
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
        val sessionUrl = dAppSessionUrlForOrigin(origin)
        val sessionOrigin = canonicalizeOrigin(sessionUrl)
        val originKeys = dAppSessionOriginKeys(origin) + sessionOrigin
        val session = DAppSession(
            name = hostLabel(sessionUrl),
            url = sessionUrl,
            walletAddress = walletAddress,
            permissions = permissions,
            network = activeRpcNetwork?.networkType ?: networkFor(currentRpcUrl),
            connectedAt = System.currentTimeMillis(),
        )
        val next = dAppSessionRepository.listSessions()
            .filterNot { it.walletAddress == walletAddress && canonicalizeOrigin(it.url) in originKeys } + session
        dAppSessionRepository.persist(next)
        dApps = connectedDAppSessions()
    }

    private fun dAppSession(origin: String, walletAddress: String): DAppSession? =
        dAppSessionRepository.listSessions().firstOrNull { session ->
            session.walletAddress == walletAddress && canonicalizeOrigin(session.url) in dAppSessionOriginKeys(origin)
        }

    private fun revokeDAppByOrigin(origin: String, walletAddress: String) {
        val originKeys = dAppSessionOriginKeys(origin)
        val next = dAppSessionRepository.listSessions().filterNot { session ->
            session.walletAddress == walletAddress && canonicalizeOrigin(session.url) in originKeys
        }
        dAppSessionRepository.persist(next)
    }

    private fun dAppSessionOriginKeys(origin: String): Set<String> = buildSet {
        val cleanOrigin = canonicalizeOrigin(origin)
        if (cleanOrigin.isNotBlank()) add(cleanOrigin)
        dAppSessionUrlForOrigin(cleanOrigin)
            .let(::canonicalizeOrigin)
            .takeIf { it.isNotBlank() }
            ?.let(::add)
    }

    private fun dAppSessionUrlForOrigin(origin: String): String =
        circleSessionUrlForOrigin(origin) ?: canonicalizeOrigin(origin)

    private fun dAppSessionLabelForOrigin(origin: String): String =
        hostLabel(dAppSessionUrlForOrigin(origin))

    private fun circleSessionUrlForOrigin(origin: String): String? {
        val cleanOrigin = canonicalizeOrigin(origin)
        val activeTarget = selectedCircleTarget
        if (activeTarget != null && cleanOrigin == canonicalizeOrigin(selectedDAppUrl)) {
            return circleSessionUrl(activeTarget)
        }
        val activeTab = dAppBrowserTabs.firstOrNull { it.id == activeDAppTabId }
        val tabTarget = activeTab?.circleTarget
        if (tabTarget != null && cleanOrigin == canonicalizeOrigin(activeTab.url)) {
            return circleSessionUrl(tabTarget)
        }
        return null
    }

    private fun circleSessionUrl(target: OctraCircleTarget): String =
        target.uri.trim().ifBlank { "oct://${target.circleId}" }

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
        val allowed = setOf("read_address", "read_balance", "read_public_key", "contract_calls")
        val sanitized = requested
            .map { it.trim() }
            .filter { it in allowed }
            .distinct()
        return sanitized.ifEmpty {
            listOf("read_address", "read_balance", "read_public_key")
        }
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
        is NoSuchMethodException -> 4200 to (exception.message ?: "Unsupported method")
        else -> -32000 to (exception.message ?: "poctra request failed")
    }

    private fun shortAddress(address: String): String {
        if (address.length <= 19) return address
        return "${address.take(8)}...${address.takeLast(8)}"
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
        val explicitScheme: Boolean,
    )

    private fun parseCircleInputSpec(value: String): CircleInputSpec? {
        val raw = value.trim()
        if (raw.isBlank()) return null
        if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) return null

        val explicitScheme = raw.startsWith("oct://", ignoreCase = true)
        val withoutScheme = if (explicitScheme) raw.drop(6) else raw
        val clean = withoutScheme.trim().substringBefore('?').substringBefore('#')
        if (clean.isBlank()) return null

        val slashIndex = clean.indexOf('/')
        val host = if (slashIndex >= 0) clean.take(slashIndex).trim() else clean.trim()
        val path = if (slashIndex >= 0) normalizeCirclePath(clean.substring(slashIndex)) else "/index.html"
        if (host.isBlank()) return null
        if (isOctraAddress(host)) return CircleInputSpec(host = host, path = path, identityName = null, explicitScheme = explicitScheme)

        val identity = host.lowercase()
        if (identity.endsWith(".oct") && isIdentityNameInput(identity)) {
            return CircleInputSpec(host = identity, path = path, identityName = identity, explicitScheme = explicitScheme)
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

    private fun isCircleIndexPath(path: String): Boolean {
        val normalized = normalizeCirclePath(path)
        return normalized == "/" || normalized.equals("/index.html", ignoreCase = true)
    }

    private fun extractCircleHtmlTitle(asset: CircleAsset): String? {
        if (!asset.contentType.contains("html", ignoreCase = true)) return null
        val html = runCatching { asset.body.toString(Charsets.UTF_8) }.getOrNull() ?: return null
        val rawTitle = Regex("<title[^>]*>(.*?)</title>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .find(html)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            .orEmpty()
        return decodeBasicHtmlEntities(rawTitle).take(48).takeIf { it.isNotBlank() }
    }

    private fun decodeBasicHtmlEntities(value: String): String =
        value
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&apos;", "'")

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
        return OctraIdentityName.isValidDomain(value)
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
            beginBusyOperation()
            clearError()
            try {
                block()
            } catch (exception: Exception) {
                rejectSubmittingTransaction(exception.message ?: "Transaction failed.")
                showError(exception.message ?: "Something went wrong.")
            } finally {
                endBusyOperation()
            }
        }
    }

    private fun beginBusyOperation() {
        busyOperationCount += 1
        busy = true
    }

    private fun endBusyOperation() {
        busyOperationCount = (busyOperationCount - 1).coerceAtLeast(0)
        busy = busyOperationCount > 0
    }

    private fun beginTransactionPolling() {
        transactionPollingCount += 1
        transactionStatusPolling = true
    }

    private fun endTransactionPolling() {
        transactionPollingCount = (transactionPollingCount - 1).coerceAtLeast(0)
        transactionStatusPolling = transactionPollingCount > 0
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
        private const val KeyTradeChartCachePrefix = "trade_chart_cache_"
        private const val InitialHistoryLimit = 7
        private const val HistoryPageSize = 7
        private const val InitialActivityLimit = 7
        private const val ActivityPageSize = 7
        private const val MaxHistoryLimit = 50
        private const val MaxUnifiedActivityItems = 80
        private const val MaxEvmActivityItems = 20
        private const val ActivityCenterSyncTimeoutMs = 8_000L
        private const val InitialPrivacyHistoryLimit = 7
        private const val PrivacyCacheMillis = 30_000L
        private const val PrivacyClaimCacheMillis = 5 * 60_000L
        private const val PrivacyConfirmationAttempts = 45
        private const val IdentityLookupBatchSize = 4
        private const val MaxCircleBridgePayloadBytes = 64_000
        private const val MaxCircleDownloadParts = 32
        private const val MaxCircleDownloadPathLength = 512
        private const val MaxCircleDownloadNameLength = 128
        private const val MaxCircleDownloadBytes = 256L * 1024L * 1024L
        private const val KeyBridgeHistory = "bridge_history"
        private const val MaxBridgeHistoryItems = 25
        private const val KeyPortfolioHoldings = "portfolio_holdings"
        private const val MicroUnits = 1_000_000L
        private const val BridgeFeeReserveOu = 10_000L
        private const val PriceRefreshMs = 60_000L
        private const val DAppCatalogRefreshMs = 15 * 60_000L
        private const val TransactionConfirmationAttempts = 15
        private const val TransactionPollingIntervalMs = 2_000L
        private const val EvmSendReceiptAttempts = 80
        private const val EvmSendReceiptIntervalMs = 3_000L
        private const val BridgeEvmReceiptAttempts = 100
        private const val BridgeEvmReceiptIntervalMs = 3_000L
        private const val BridgeClaimRetryAttempts = 8
        private const val BridgeClaimRetryDelayMs = 8_000L
        private const val TradeChartManualCooldownMs = 20_000L
        private const val TradeChartRateLimitCooldownMs = 120_000L
        private const val EvmNativeGasLimit = 21_000L
        private const val EvmTokenGasLimit = 80_000L
        private const val EvmNftGasLimit = 120_000L
        private const val HiddenEvmRpcKey = "<hidden>"
        private const val Permit2Address = "0x000000000022D473030F116dDEE9F6B43aC78BA3"
        private const val UniversalRouterAddress = "0x66a9893cC07D91D95644AEDD05D03f95e1dBA8Af"
        private val ApprovalLargeAllowanceRaw = BigInteger("1000000000000000000000000")
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
