package com.poctra.wallet.ui

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.util.Base64
import android.util.Log
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.SafeBrowsingResponse
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.WebChromeClient.FileChooserParams
import android.webkit.ValueCallback
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.poctra.wallet.dapp.POCTRA_PROVIDER_HOMEPAGE
import com.poctra.wallet.dapp.POCTRA_PROVIDER_ICON
import com.poctra.wallet.dapp.POCTRA_PROVIDER_NAME
import com.poctra.wallet.dapp.POCTRA_PROVIDER_RDNS
import com.poctra.wallet.dapp.POCTRA_PROVIDER_UUID
import com.poctra.wallet.dapp.POCTRA_PROVIDER_VERSION
import com.poctra.wallet.dapp.isRfcO1InpageProviderRequest
import com.poctra.wallet.dapp.requiresNavigationLifecycleProvider
import com.poctra.wallet.dapp.rfcO1InpageProviderPath
import com.poctra.wallet.dapp.replaceProviderIconMetadata
import androidx.compose.ui.zIndex
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.poctra.wallet.AppRoute
import com.poctra.wallet.ApprovalReviewItem
import com.poctra.wallet.BridgeHistoryItem
import com.poctra.wallet.CirclePassphraseRequest
import com.poctra.wallet.ConsumerRiskItem
import com.poctra.wallet.ConsumerRiskLevel
import com.poctra.wallet.DashboardTab
import com.poctra.wallet.DAppBrowserTab
import com.poctra.wallet.DAppInputPreview
import com.poctra.wallet.DAppInputPreviewStatus
import com.poctra.wallet.FundingProvider
import com.poctra.wallet.PendingDAppRequest
import com.poctra.wallet.PortfolioHolding
import com.poctra.wallet.UnifiedActivityItem
import com.poctra.wallet.PoctraController
import com.poctra.wallet.TradeStep
import com.poctra.wallet.core.EvmCrypto
import com.poctra.wallet.data.AddressBookContact
import com.poctra.wallet.data.BridgeDirection
import com.poctra.wallet.data.BridgeEvmGasEstimate
import com.poctra.wallet.data.BridgeRepository
import com.poctra.wallet.data.BridgeStatus
import com.poctra.wallet.data.BridgeStep
import com.poctra.wallet.data.CircleAsset
import com.poctra.wallet.data.DAppSession
import com.poctra.wallet.data.DAppCatalogEntry
import com.poctra.wallet.data.EvmNetwork
import com.poctra.wallet.data.EvmGasEstimate
import com.poctra.wallet.data.EvmNft
import com.poctra.wallet.data.EvmToken
import com.poctra.wallet.data.EvmTransaction
import com.poctra.wallet.data.OctraIdentityProvider
import com.poctra.wallet.data.OctraIdentityName
import com.poctra.wallet.data.RpcStatus
import com.poctra.wallet.data.RpcNetwork
import com.poctra.wallet.data.SecurityMethod
import com.poctra.wallet.data.SubmittedTransaction
import com.poctra.wallet.data.TradeAsset
import com.poctra.wallet.data.TradeCandle
import com.poctra.wallet.data.TradeChartTimeframe
import com.poctra.wallet.data.TradeQuote
import com.poctra.wallet.data.TradeQuoteRisk
import com.poctra.wallet.data.TransactionFinality
import com.poctra.wallet.data.TransactionHistoryItem
import com.poctra.wallet.data.Wallet
import com.poctra.wallet.data.WalletType
import com.poctra.wallet.dapp.browser.PoctraProviderBridge
import com.poctra.wallet.dapp.browser.BrowserResourceHandler
import com.poctra.wallet.dapp.browser.routeBrowserResource
import com.poctra.wallet.dapp.circle.resolveCircleContentType
import com.poctra.wallet.dapp.circle.isTransientCircleFailure
import com.poctra.wallet.dapp.DAppOriginBinding
import com.poctra.wallet.dapp.walletBrowserUserAgent
import com.poctra.wallet.security.DeviceSecurity
import com.poctra.wallet.security.DAppSslPolicy
import com.poctra.wallet.security.DAppUrlPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.net.URLEncoder
import java.util.UUID
import kotlin.math.abs
import com.poctra.wallet.BuildConfig

private const val FullWidthDialogFraction = 0.96f
private const val ScrimMotionMillis = 220
private const val SheetEnterMillis = 320
private const val SheetExitMillis = 220
private const val ImportMotionMillis = 340
private val OverviewHeaderHeight = 48.dp

@Composable
fun PoctraApp(activity: Activity, controller: PoctraController) {
    var launchAnimationComplete by rememberSaveable { mutableStateOf(false) }
    var automaticUnlockEnabled by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(PoctraLaunchDisplayMillis)
        launchAnimationComplete = true
    }
    val launchVisible = !launchAnimationComplete || controller.route == AppRoute.Loading
    LaunchedEffect(launchVisible) {
        if (!launchVisible && !automaticUnlockEnabled) {
            delay(PoctraLaunchExitMillis)
            automaticUnlockEnabled = true
        }
    }

    Surface(color = SurfaceBase, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when (controller.route) {
                AppRoute.Loading -> LoadingScreen()
                AppRoute.Welcome -> WelcomeScreen(controller)
                AppRoute.ImportWallet -> ImportWalletScreen(controller)
                AppRoute.SecuritySetup -> SecuritySetupScreen(activity, controller)
                AppRoute.Unlock -> UnlockScreen(
                    activity = activity,
                    controller = controller,
                    automaticUnlockEnabled = automaticUnlockEnabled,
                )
                AppRoute.Dashboard -> DashboardScreen(controller)
                AppRoute.PrivacyEncrypt -> PrivacyEncryptScreen(controller)
                AppRoute.PrivacyDecrypt -> PrivacyDecryptScreen(controller)
                AppRoute.PrivacyStealth -> PrivacyStealthScreen(controller)
                AppRoute.PrivacyClaim -> PrivacyClaimScreen(controller)
                AppRoute.PrivacySettings -> PrivacySettingsScreen(controller)
                AppRoute.Settings -> SettingsScreen(controller)
                AppRoute.RpcManagement -> RpcManagementScreen(controller)
                AppRoute.RpcAddNetwork -> AddRpcNetworkScreen(controller)
                AppRoute.SecuritySettings -> SecuritySettingsScreen(activity, controller)
                AppRoute.BackupSettings -> BackupSettingsScreen(activity, controller)
                AppRoute.SecurityCenter -> SecurityCenterScreen(controller)
                AppRoute.ActivityCenter -> ActivityCenterScreen(controller)
                AppRoute.PaymentRequest -> ReceiveScreen(controller)
                AppRoute.PortfolioAnalytics -> PortfolioAnalyticsScreen(controller)
                AppRoute.PortfolioAssetHistory -> PortfolioAssetHistoryScreen(controller)
                AppRoute.ApprovalDashboard -> ApprovalDashboardScreen(controller)
                AppRoute.RecoveryPlan -> RecoveryPlanScreen(controller)
                AppRoute.FundingProviders -> SettingsScreen(controller)
                AppRoute.AppearanceSettings -> AppearanceSettingsScreen(controller)
                AppRoute.ConnectedDApps -> ConnectedDAppsScreen(controller)
                AppRoute.IdentityProviderSettings -> IdentityProviderSettingsScreen(controller)
                AppRoute.IdentityProviderAdd -> AddIdentityProviderScreen(controller)
                AppRoute.AddressBook -> AddressBookScreen(controller)
                AppRoute.AddressBookAdd -> AddAddressBookContactScreen(controller)
                AppRoute.AddAsset -> AddAssetScreen(controller)
                AppRoute.Send -> SendScreen(controller)
                AppRoute.SendAsset -> SendAssetScreen(controller)
                AppRoute.Receive -> ReceiveScreen(controller)
                AppRoute.Bridge -> BridgeScreen(controller)
                AppRoute.BridgeHistory -> BridgeHistoryScreen(controller)
                AppRoute.Evm -> EvmWalletScreen(controller)
                AppRoute.EvmSend -> EvmSendScreen(controller)
                AppRoute.EvmReceive -> EvmReceiveScreen(controller)
                AppRoute.EvmActivity -> EvmActivityScreen(controller)
                AppRoute.EvmNetwork -> EvmNetworkSettingsScreen(controller)
                AppRoute.EvmAddNetwork -> EvmAddNetworkScreen(controller)
                AppRoute.EvmAddToken -> EvmAddTokenScreen(controller)
                AppRoute.EvmAddNft -> EvmAddNftScreen(controller)
                AppRoute.TransactionDetails -> TransactionDetailsScreen(controller)
                AppRoute.DAppBrowser -> DAppBrowserScreen(controller)
                AppRoute.About -> AboutScreen(controller)
            }

            controller.error?.let { message ->
                LaunchedEffect(message, controller.noticeIsError) {
                    delay(if (controller.noticeIsError) 4800 else 2800)
                    if (controller.error == message) controller.clearError()
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .padding(horizontal = Space.x8)
                        .padding(top = Space.x7),
                ) {
                    StateBanner(
                        text = message,
                        modifier = Modifier.clickable { controller.clearError() },
                        isError = controller.noticeIsError,
                        compact = true,
                        solid = true,
                    )
                }
            }

            controller.submittedTransaction?.takeIf { it.showDialog }?.let { transaction ->
                TransactionResultDialog(
                    transaction = transaction,
                    polling = controller.transactionStatusPolling,
                    onDismiss = controller::dismissSubmittedTransaction,
                    onViewTransaction = controller::openSubmittedTransactionExplorer,
                )
            }

            if (controller.evmSendDialogVisible) {
                EvmSendReviewDialog(controller)
            }

            PrivacyOverlay(controller)

            controller.pendingDAppRequest?.let { request ->
                DAppApprovalDialog(
                    request = request,
                    identityName = controller.wallet?.address?.let(controller::walletIdentityDomain),
                    walletAddress = controller.wallet?.address,
                    onApprove = controller::approvePendingDAppRequest,
                    onReject = controller::rejectPendingDAppRequest,
                )
            }

            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                AnimatedVisibility(
                    visible = launchVisible,
                    exit = fadeOut(animationSpec = tween(PoctraLaunchExitMillis.toInt())),
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(10_000f),
                ) {
                    PoctraLaunchScreen()
                }
            }
        }
    }
}

@Composable
private fun DAppApprovalDialog(
    request: PendingDAppRequest,
    identityName: String?,
    walletAddress: String?,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    LaunchedEffect(request) {
        if (BuildConfig.DEBUG) Log.d("PoctraDApp", "approval composable visible title=${request.title} origin=${request.origin}")
    }
    val largeRequest = request.details.size > 4 || request.details.sumOf { it.value.length } > 220
    val sheetVisible = true
    val scrimAlpha by animateFloatAsState(
        targetValue = if (sheetVisible) 0.52f else 0f,
        animationSpec = tween(ScrimMotionMillis),
        label = "dappApprovalScrim",
    )
    Dialog(
        onDismissRequest = onReject,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            val sheetMaxHeight = maxHeight * if (largeRequest) 0.92f else 0.74f
            val detailMaxHeight = maxHeight * if (largeRequest) 0.52f else 0.40f
            AnimatedVisibility(
                visible = sheetVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(SheetEnterMillis, easing = FastOutSlowInEasing),
                ) + fadeIn(animationSpec = tween(ScrimMotionMillis)),
                exit = slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(SheetExitMillis, easing = FastOutSlowInEasing),
                ) + fadeOut(animationSpec = tween(SheetExitMillis)),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 280.dp, max = sheetMaxHeight)
                        .clickable(onClick = {})
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(topStart = Radius.md, topEnd = Radius.md),
                    colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = sheetMaxHeight)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Space.x7, vertical = Space.x6),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(BorderLine)
                                .align(Alignment.CenterHorizontally),
                        )
                        Spacer(Modifier.height(Space.x5))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(Radius.md))
                                    .background(SurfaceRaised.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                GlyphIcon(Glyph.Browser, modifier = Modifier.size(20.dp), color = SurfaceRaised)
                            }
                            Spacer(Modifier.width(Space.x5))
                            Column(Modifier.weight(1f)) {
                                Text(request.title, color = TextTertiary, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(Space.x2))
                                Text(request.origin, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.height(Space.x5))
                        Text(
                            request.message,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (request.details.isNotEmpty()) {
                            Spacer(Modifier.height(Space.x5))
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = detailMaxHeight),
                                shape = RoundedCornerShape(Radius.sm),
                                colors = CardDefaults.cardColors(containerColor = SurfaceBase),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = Space.x5, vertical = Space.x3),
                                ) {
                                    request.details.forEachIndexed { index, item ->
                                        DAppRequestDetailRow(
                                            label = item.label,
                                            value = item.value,
                                            identityName = identityName,
                                            walletAddress = walletAddress,
                                        )
                                        if (index != request.details.lastIndex) DetailSeparator()
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(Space.x5))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(SurfaceBase)
                                .padding(horizontal = Space.x5, vertical = Space.x4),
                        ) {
                            Text(
                                "Review every field before approving.",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Spacer(Modifier.height(Space.x5))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
                            PoctraButton(
                                text = request.rejectLabel,
                                onClick = onReject,
                                modifier = Modifier.weight(1f),
                                variant = ButtonVariant.Ghost,
                                fullHeight = true,
                            )
                            PoctraButton(
                                text = request.approveLabel,
                                onClick = onApprove,
                                modifier = Modifier.weight(1f),
                                leadingGlyph = Glyph.Check,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DAppRequestDetailRow(
    label: String,
    value: String,
    identityName: String? = null,
    walletAddress: String? = null,
) {
    val warning = label.equals("Security warning", ignoreCase = true)
    val displaysActiveWallet = label.lowercase() in setOf("wallet", "from", "owner") &&
        !identityName.isNullOrBlank() &&
        !walletAddress.isNullOrBlank() &&
        value.trim().equals(walletAddress.trim(), ignoreCase = true)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = if (warning) Warning else TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(82.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(Space.x4))
        Column(modifier = Modifier.weight(1f)) {
            if (displaysActiveWallet) {
                Text(
                    text = identityName.orEmpty(),
                    color = SurfaceRaised,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
            }
            Text(
                value.ifBlank { "-" },
                color = if (warning) Warning else TextTertiary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun TransactionResultDialog(
    transaction: SubmittedTransaction,
    polling: Boolean,
    onDismiss: () -> Unit,
    onViewTransaction: () -> Unit,
) {
    val isSubmitting = transaction.status == TransactionFinality.Submitting
    val isError = transaction.status == TransactionFinality.Rejected || transaction.status == TransactionFinality.Dropped
    val statusColor = if (isError) ErrorRed else SurfaceRaised
    val title = when (transaction.status) {
        TransactionFinality.Submitting -> "Submitting Transaction"
        TransactionFinality.Accepted -> "Transaction Accepted"
        TransactionFinality.Confirmed -> "Transaction Confirmed"
        TransactionFinality.Rejected -> "Transaction Rejected"
        TransactionFinality.Dropped -> "Transaction Dropped"
    }
    val description = when (transaction.status) {
        TransactionFinality.Submitting -> "Signing and broadcasting the transaction to the network."
        TransactionFinality.Accepted -> "The network accepted this transaction. Waiting for on-chain confirmation."
        TransactionFinality.Confirmed -> "This transaction has been confirmed on-chain."
        TransactionFinality.Rejected -> transaction.reason ?: "The network rejected this transaction."
        TransactionFinality.Dropped -> transaction.reason ?: "This transaction was not confirmed by the network."
    }
    var sheetVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sheetVisible = true }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (sheetVisible) 0.52f else 0f,
        animationSpec = tween(ScrimMotionMillis),
        label = "transactionResultScrim",
    )

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(onClick = {}),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val sheetMinHeight = maxHeight * 0.52f
        val sheetMaxHeight = maxHeight * 0.88f
        AnimatedVisibility(
            visible = sheetVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(SheetEnterMillis, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(ScrimMotionMillis)),
            exit = slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(SheetExitMillis, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(SheetExitMillis)),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = sheetMinHeight, max = sheetMaxHeight)
                    .clickable(onClick = {})
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(topStart = Radius.md, topEnd = Radius.md),
                colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Space.x7, vertical = Space.x6),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(BorderLine),
                    )
                    Spacer(Modifier.height(Space.x6))
                    Text(title, color = TextTertiary, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(Space.x6))
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(Radius.md))
                            .background(statusColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSubmitting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                color = PrimaryContent,
                                strokeWidth = 2.dp,
                            )
                        } else {
                            GlyphIcon(
                                glyph = if (isError) Glyph.Info else Glyph.Check,
                                modifier = Modifier.size(26.dp),
                                color = PrimaryContent,
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.x6))
                    Text(
                        text = transaction.status.name.lowercase(),
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(statusColor.copy(alpha = 0.2f))
                            .padding(horizontal = Space.x4, vertical = Space.x2),
                        color = if (isError) ErrorRed else SurfaceRaised,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(Space.x5))
                    Text(description, color = TextSecondary, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(Space.x7))
                    TransactionProgressStepper(transaction = transaction, polling = polling)
                    transaction.hash?.takeIf { it.isNotBlank() }?.let { hash ->
                        Spacer(Modifier.height(Space.x7))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Transaction hash",
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(Radius.sm))
                                    .clickable(onClick = onViewTransaction)
                                    .padding(horizontal = Space.x4, vertical = Space.x2),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "View",
                                    color = SurfaceRaised,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.width(Space.x2))
                                GlyphIcon(Glyph.External, Modifier.size(14.dp), SurfaceRaised)
                            }
                        }
                        Spacer(Modifier.height(Space.x2))
                        Text(hash, color = TextTertiary, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(Modifier.height(Space.x7))
                    PoctraButton(
                        text = if (isSubmitting || (polling && transaction.status == TransactionFinality.Accepted)) "Hide" else "Done",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        compact = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun EvmSendReviewDialog(controller: PoctraController) {
    val transaction = controller.submittedTransaction
    val started = controller.evmSendDialogStarted || transaction != null || controller.evmSending
    val gasEstimate = controller.evmSendGasEstimate
    val network = controller.activeEvmNetwork
    var selectedGas by remember(gasEstimate) { mutableStateOf(BridgeFeePreset.Medium) }
    var customGasGwei by remember(gasEstimate) {
        mutableStateOf(gasEstimate?.marketGasPriceWei?.let(::weiToGweiLabel).orEmpty())
    }
    val selectedGasGwei = evmSendSelectedGasGwei(selectedGas, gasEstimate, customGasGwei)
    val canConfirm = !controller.evmSending &&
        !controller.evmSendGasLoading &&
        gasEstimate != null &&
        (selectedGas != BridgeFeePreset.Custom || customGasGwei.gweiToWeiOrNull() != null)
    var sheetVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sheetVisible = true }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (sheetVisible) 0.52f else 0f,
        animationSpec = tween(ScrimMotionMillis),
        label = "evmSendReviewScrim",
    )
    Dialog(
        onDismissRequest = {
            if (!started && !controller.evmSending) controller.dismissEvmSendDialog()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(
                    enabled = !started && !controller.evmSending,
                    onClick = controller::dismissEvmSendDialog,
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = sheetVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(SheetEnterMillis, easing = FastOutSlowInEasing),
                ) + fadeIn(animationSpec = tween(ScrimMotionMillis)),
                exit = slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(SheetExitMillis, easing = FastOutSlowInEasing),
                ) + fadeOut(animationSpec = tween(SheetExitMillis)),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * if (started) 0.92f else 0.88f)
                        .clickable(onClick = {})
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(topStart = Radius.md, topEnd = Radius.md),
                    colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Space.x7, vertical = Space.x6),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(42.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(BorderLine)
                                .align(Alignment.CenterHorizontally),
                        )
                        Spacer(Modifier.height(Space.x6))
                        if (started) {
                            EvmSendProgressContent(
                                controller = controller,
                                transaction = transaction,
                                onDismiss = controller::dismissEvmSendDialog,
                            )
                        } else {
                            EvmSendReviewContent(
                                controller = controller,
                                gasEstimate = gasEstimate,
                                selectedGas = selectedGas,
                                customGasGwei = customGasGwei,
                                onSelectedGas = { selectedGas = it },
                                onCustomGasChange = {
                                    customGasGwei = it.filter { char -> char.isDigit() || char == '.' || char == ',' }
                                },
                                onRetryGas = controller::retryPreparedEvmSendGasEstimate,
                            )
                            Spacer(Modifier.height(Space.x6))
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
                                PoctraButton(
                                    text = "Cancel",
                                    onClick = controller::dismissEvmSendDialog,
                                    modifier = Modifier.weight(1f),
                                    variant = ButtonVariant.Ghost,
                                    fullHeight = true,
                                )
                                PoctraButton(
                                    text = "Confirm",
                                    onClick = { controller.submitPreparedEvmSend(selectedGasGwei) },
                                    modifier = Modifier.weight(1f),
                                    enabled = canConfirm,
                                    loading = controller.evmSending || controller.evmSendGasLoading,
                                    leadingGlyph = Glyph.Check,
                                )
                            }
                            if (network == null) {
                                Spacer(Modifier.height(Space.x4))
                                StateBanner("Set an active EVM network before sending.", isError = true, compact = true)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EvmSendReviewContent(
    controller: PoctraController,
    gasEstimate: EvmGasEstimate?,
    selectedGas: BridgeFeePreset,
    customGasGwei: String,
    onSelectedGas: (BridgeFeePreset) -> Unit,
    onCustomGasChange: (String) -> Unit,
    onRetryGas: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(SurfaceRaised.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Send, modifier = Modifier.size(21.dp), color = SurfaceRaised)
        }
        Spacer(Modifier.width(Space.x5))
        Column(Modifier.weight(1f)) {
            Text("Review Send", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
            Text(evmSendAssetLabel(controller), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
    val account = controller.evmAccount
    ReviewProcessFlow(
        source = ReviewFlowEndpoint(
            label = "From",
            address = account?.address.orEmpty(),
            identityName = account?.ensName,
        ),
        destination = ReviewFlowEndpoint(
            label = "To",
            address = controller.evmSendDialogRecipient,
            identityName = controller.evmSendDialogRecipientIdentity,
        ),
        actionLabel = "Send",
        actionGlyph = Glyph.Go,
        modifier = Modifier.padding(top = Space.x5),
    )
    Spacer(Modifier.height(Space.x6))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
    ) {
        Column(Modifier.padding(horizontal = Space.x6, vertical = Space.x3)) {
            DetailLine("Asset", evmSendAssetLabel(controller))
            DetailSeparator()
            DetailLine("Amount", evmSendAmountLabel(controller))
            DetailSeparator()
            DetailLine("Network", controller.evmNetworkLabel())
        }
    }
    Spacer(Modifier.height(Space.x5))
    EvmSendGasPanel(
        networkSymbol = controller.activeEvmNetwork?.symbol ?: "ETH",
        gasEstimate = gasEstimate,
        gasLoading = controller.evmSendGasLoading,
        selectedGas = selectedGas,
        customGasGwei = customGasGwei,
        onSelectedGas = onSelectedGas,
        onCustomGasChange = onCustomGasChange,
        onRetryGas = onRetryGas,
    )
}

@Composable
private fun EvmSendGasPanel(
    networkSymbol: String,
    gasEstimate: EvmGasEstimate?,
    gasLoading: Boolean,
    selectedGas: BridgeFeePreset,
    customGasGwei: String,
    onSelectedGas: (BridgeFeePreset) -> Unit,
    onCustomGasChange: (String) -> Unit,
    onRetryGas: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
    ) {
        Column(Modifier.padding(Space.x5)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Network Fee", color = TextTertiary, fontWeight = FontWeight.SemiBold)
                    Text(
                        gasEstimate?.let { "${it.gasLimit} gas estimated for ${it.label}" } ?: "Estimating gas from active RPC",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    )
                }
                if (gasLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SurfaceRaised, strokeWidth = 2.dp)
                } else {
                    GlyphIcon(Glyph.Info, modifier = Modifier.size(16.dp), color = SurfaceRaised)
                }
            }
            Spacer(Modifier.height(Space.x4))
            if (gasEstimate == null) {
                StateBanner(
                    text = if (gasLoading) "Fetching gas estimate..." else "Gas estimate unavailable. Refresh the EVM account or check RPC.",
                    isError = !gasLoading,
                    compact = true,
                )
                if (!gasLoading) {
                    Spacer(Modifier.height(Space.x4))
                    PoctraButton(
                        text = "Retry Gas",
                        onClick = onRetryGas,
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.Ghost,
                        compact = true,
                        leadingGlyph = Glyph.Refresh,
                    )
                }
            } else {
                BridgeFeePresetRow(
                    selected = selectedGas,
                    labels = mapOf(
                        BridgeFeePreset.Low to weiToEthLabel(gasEstimate.lowFeeWei),
                        BridgeFeePreset.Medium to weiToEthLabel(gasEstimate.marketFeeWei),
                        BridgeFeePreset.Market to weiToEthLabel(gasEstimate.fastFeeWei),
                        BridgeFeePreset.Custom to evmSendCustomGasFeeLabel(gasEstimate, customGasGwei, networkSymbol),
                    ),
                    titles = mapOf(
                        BridgeFeePreset.Medium to "Recommended",
                        BridgeFeePreset.Market to "Fast",
                    ),
                    suffix = networkSymbol,
                    onSelect = onSelectedGas,
                )
                if (selectedGas == BridgeFeePreset.Custom) {
                    Spacer(Modifier.height(Space.x4))
                    PoctraTextField(
                        value = customGasGwei,
                        onValueChange = onCustomGasChange,
                        label = "Custom gas price (gwei)",
                        keyboardType = KeyboardType.Decimal,
                        singleLine = true,
                        supportingText = if (customGasGwei.gweiToWeiOrNull() == null) "Enter a valid gas price." else null,
                    )
                }
            }
        }
    }
}

@Composable
private fun EvmSendProgressContent(
    controller: PoctraController,
    transaction: SubmittedTransaction?,
    onDismiss: () -> Unit,
) {
    val status = transaction?.status
    val failed = status == TransactionFinality.Rejected || status == TransactionFinality.Dropped
    val completed = status == TransactionFinality.Confirmed
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background((if (failed) ErrorRed else SurfaceRaised).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                controller.evmSending || status == TransactionFinality.Submitting || status == TransactionFinality.Accepted ->
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SurfaceRaised, strokeWidth = 2.dp)
                failed -> GlyphIcon(Glyph.Info, modifier = Modifier.size(21.dp), color = ErrorRed)
                else -> GlyphIcon(Glyph.Check, modifier = Modifier.size(21.dp), color = SurfaceRaised)
            }
        }
        Spacer(Modifier.width(Space.x5))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    failed -> "Send Failed"
                    completed -> "Send Complete"
                    else -> "Send Processing"
                },
                color = TextTertiary,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(evmSendAssetLabel(controller), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(Space.x5))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
    ) {
        Column(Modifier.padding(horizontal = Space.x6, vertical = Space.x3)) {
            DetailLine("Recipient", controller.evmSendDialogRecipient)
            DetailSeparator()
            DetailLine("Amount", evmSendAmountLabel(controller))
            DetailSeparator()
            DetailLine("Network", controller.evmNetworkLabel())
            transaction?.hash?.takeIf { it.isNotBlank() }?.let { hash ->
                DetailSeparator()
                DetailLine(
                    "Tx Hash",
                    hash,
                    actionLabel = "View",
                    actionGlyph = Glyph.External,
                    onAction = controller::openEvmSendExplorerTx,
                )
            }
            transaction?.reason?.takeIf { it.isNotBlank() }?.let { reason ->
                DetailSeparator()
                DetailLine("Reason", reason)
            }
        }
    }
    Spacer(Modifier.height(Space.x5))
    if (transaction == null) {
        StateBanner("Preparing transaction...", isError = false, compact = true)
    } else {
        TransactionProgressStepper(transaction = transaction, polling = controller.transactionStatusPolling)
    }
    if (controller.evmSendCanSpeedUp) {
        Spacer(Modifier.height(Space.x5))
        StateBanner(
            text = "Pending too long? Speed Up replaces this EVM transaction with the same nonce and higher gas.",
            isError = false,
            compact = true,
        )
        Spacer(Modifier.height(Space.x4))
        PoctraButton(
            text = "Speed Up",
            onClick = controller::speedUpEvmSendTransaction,
            modifier = Modifier.fillMaxWidth(),
            loading = controller.evmSendSpeedUpBusy,
            enabled = !controller.evmSendSpeedUpBusy,
            leadingGlyph = Glyph.Refresh,
            compact = true,
            variant = ButtonVariant.Ghost,
        )
    }
    Spacer(Modifier.height(Space.x5))
    PoctraButton(
        text = if (controller.evmSending || status == TransactionFinality.Submitting || status == TransactionFinality.Accepted) "Hide" else "Done",
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        enabled = !controller.evmSending,
        leadingGlyph = if (completed) Glyph.Check else Glyph.Send,
        compact = true,
    )
}

private fun evmSendSelectedGasGwei(
    preset: BridgeFeePreset,
    gasEstimate: EvmGasEstimate?,
    customGasGwei: String,
): String? {
    val wei = when (preset) {
        BridgeFeePreset.Low -> gasEstimate?.lowGasPriceWei
        BridgeFeePreset.Medium -> gasEstimate?.marketGasPriceWei
        BridgeFeePreset.Market -> gasEstimate?.fastGasPriceWei
        BridgeFeePreset.Custom -> customGasGwei.gweiToWeiOrNull()
    } ?: return null
    return weiToGweiLabel(wei)
}

private fun evmSendCustomGasFeeLabel(gasEstimate: EvmGasEstimate, customGasGwei: String, symbol: String): String {
    val gasPriceWei = customGasGwei.gweiToWeiOrNull() ?: return "Enter a custom gas price."
    return "~ ${weiToEthLabel(gasPriceWei.multiply(gasEstimate.gasLimit))} $symbol"
}

private fun evmSendAssetLabel(controller: PoctraController): String {
    val nft = controller.evmSelectedSendNft
    val token = controller.evmSelectedSendToken
    val network = controller.activeEvmNetwork
    return when {
        nft != null -> "${nft.name} (${nft.symbol} #${nft.tokenId})"
        token != null -> evmTokenTitle(token)
        else -> controller.evmNativeAssetName().ifBlank { network?.symbol ?: "ETH" }
    }
}

private fun evmSendAmountLabel(controller: PoctraController): String {
    val nft = controller.evmSelectedSendNft
    val token = controller.evmSelectedSendToken
    return if (nft != null) {
        "1 NFT"
    } else {
        "${controller.evmSendDialogAmount} ${token?.symbol ?: controller.activeEvmNetwork?.symbol ?: "ETH"}"
    }
}

private enum class TxStepVisual {
    Pending,
    Active,
    Done,
    Error,
}

private data class TxProgressStep(
    val title: String,
    val description: String,
    val visual: TxStepVisual,
)

@Composable
private fun TransactionProgressStepper(transaction: SubmittedTransaction, polling: Boolean) {
    val rejectedBeforeHash = transaction.status == TransactionFinality.Rejected && transaction.hash.isNullOrBlank()
    val finalFailed = transaction.status == TransactionFinality.Rejected || transaction.status == TransactionFinality.Dropped
    val steps = listOf(
        TxProgressStep(
            title = "Submit",
            description = "Preparing and broadcasting transaction",
            visual = when (transaction.status) {
                TransactionFinality.Submitting -> TxStepVisual.Active
                else -> TxStepVisual.Done
            },
        ),
        TxProgressStep(
            title = if (rejectedBeforeHash) "Rejected" else "Accepted",
            description = if (rejectedBeforeHash) "Network rejected the first result" else "First network result received",
            visual = when {
                transaction.status == TransactionFinality.Submitting -> TxStepVisual.Pending
                rejectedBeforeHash -> TxStepVisual.Error
                else -> TxStepVisual.Done
            },
        ),
        TxProgressStep(
            title = if (finalFailed && !rejectedBeforeHash) "Reverted" else "Confirmed",
            description = if (finalFailed && !rejectedBeforeHash) {
                transaction.reason ?: "Final chain result failed"
            } else {
                "Final blockchain confirmation"
            },
            visual = when {
                transaction.status == TransactionFinality.Confirmed -> TxStepVisual.Done
                finalFailed && !rejectedBeforeHash -> TxStepVisual.Error
                transaction.status == TransactionFinality.Accepted && polling -> TxStepVisual.Active
                else -> TxStepVisual.Pending
            },
        ),
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(SurfaceBase)
            .padding(Space.x5),
    ) {
        steps.forEachIndexed { index, step ->
            TxProgressStepRow(
                step = step,
                showLine = index != steps.lastIndex,
            )
        }
    }
}

@Composable
private fun TxProgressStepRow(step: TxProgressStep, showLine: Boolean) {
    val indicatorScale by animateFloatAsState(
        targetValue = if (step.visual == TxStepVisual.Active) 1.08f else 1f,
        animationSpec = tween(ScrimMotionMillis, easing = FastOutSlowInEasing),
        label = "txProgressIndicatorScale",
    )
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .graphicsLayer {
                        scaleX = indicatorScale
                        scaleY = indicatorScale
                    }
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(
                        when (step.visual) {
                            TxStepVisual.Pending -> SurfaceStrong
                            TxStepVisual.Error -> ErrorRed.copy(alpha = 0.18f)
                            else -> SurfaceRaised.copy(alpha = 0.22f)
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                when (step.visual) {
                    TxStepVisual.Active -> CircularProgressIndicator(
                        modifier = Modifier.size(13.dp),
                        color = SurfaceRaised,
                        strokeWidth = 2.dp,
                    )
                    TxStepVisual.Done -> GlyphIcon(Glyph.Check, modifier = Modifier.size(14.dp), color = SurfaceRaised)
                    TxStepVisual.Error -> GlyphIcon(Glyph.Info, modifier = Modifier.size(14.dp), color = ErrorRed)
                    TxStepVisual.Pending -> Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(TextSecondary.copy(alpha = 0.45f)),
                    )
                }
            }
            if (showLine) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(BorderLine),
                )
            }
        }
        Spacer(Modifier.width(Space.x4))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (showLine) Space.x4 else 0.dp),
        ) {
            Text(step.title, color = TextTertiary, fontWeight = FontWeight.SemiBold)
            Text(step.description, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        Modifier
            .fillMaxSize()
            .background(SurfaceBase),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = SurfaceRaised)
    }
}

@Composable
private fun WelcomeScreen(controller: PoctraController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(Space.x8),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Space.x8))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(com.poctra.wallet.R.drawable.poctra_logo),
                contentDescription = "poctra",
                modifier = Modifier.size(76.dp),
            )
            Spacer(Modifier.height(Space.x8))
            Text("poctra", color = TextTertiary, style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(Space.x5))
            Text(
                "pocket octra",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
        }
        Column(Modifier.fillMaxWidth()) {
            PoctraButton(
                text = "Create Wallet",
                onClick = controller::createWallet,
                modifier = Modifier.fillMaxWidth(),
                loading = controller.busy,
                enabled = !controller.busy,
                leadingGlyph = Glyph.Plus,
            )
            Spacer(Modifier.height(Space.x6))
            PoctraButton(
                text = "Import Wallet",
                onClick = controller::openImportWallet,
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Ghost,
                enabled = !controller.busy,
                leadingGlyph = Glyph.Import,
                fullHeight = true,
            )
        }
    }
}

private enum class ImportMethod {
    Mnemonic,
    PrivateKey,
    EthereumPrivateKey,
}

@Composable
private fun ImportWalletScreen(controller: PoctraController) {
    var method by remember { mutableStateOf<ImportMethod?>(null) }
    var value by remember { mutableStateOf("") }
    var secretVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }
    fun selectMethod(next: ImportMethod) {
        if (method != next) {
            value = ""
            secretVisible = false
            localError = null
        }
        method = next
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeImportWallet, enabled = !controller.busy)
            Spacer(Modifier.width(Space.x6))
            Text("Import Wallet", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(34.dp))
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(SurfaceRaised.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Import, modifier = Modifier.size(34.dp), color = SurfaceRaised)
        }
        Spacer(Modifier.height(Space.x7))
        Text("Recover your wallet", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.x4))
        Text(
            "Choose an import method. poctra validates the derived Octra address before security setup.",
            color = TextSecondary,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(Space.x8))

        ImportMethodPicker(
            selected = method,
            enabled = !controller.busy,
            onSelect = ::selectMethod,
        )
        AnimatedContent(
            targetState = method,
            transitionSpec = {
                (
                    fadeIn(animationSpec = tween(ImportMotionMillis, easing = FastOutSlowInEasing)) +
                        slideInVertically(
                            initialOffsetY = { it / 6 },
                            animationSpec = tween(ImportMotionMillis, easing = FastOutSlowInEasing),
                        )
                    ) togetherWith (
                    fadeOut(animationSpec = tween(ImportMotionMillis / 2, easing = FastOutSlowInEasing)) +
                        slideOutVertically(
                            targetOffsetY = { it / 8 },
                            animationSpec = tween(ImportMotionMillis / 2, easing = FastOutSlowInEasing),
                        )
                    )
            },
            label = "importWalletForm",
        ) { selectedMethod ->
            if (selectedMethod != null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .animateContentSize(animationSpec = tween(ImportMotionMillis, easing = FastOutSlowInEasing)),
                ) {
                    Spacer(Modifier.height(Space.x8))
                    ImportSecretForm(
                        method = selectedMethod,
                        value = value,
                        onValueChange = {
                            value = it
                            localError = null
                        },
                        secretVisible = secretVisible,
                        onToggleSecret = { secretVisible = !secretVisible },
                        error = localError,
                    )
                    Spacer(Modifier.height(Space.x8))
                    PoctraButton(
                        text = "Continue",
                        onClick = {
                            val validation = validateImport(selectedMethod, value)
                            if (validation != null) {
                                localError = validation
                            } else if (selectedMethod == ImportMethod.Mnemonic) {
                                controller.importMnemonic(value)
                            } else if (selectedMethod == ImportMethod.PrivateKey) {
                                controller.importPrivateKey(value)
                            } else {
                                controller.importEthereumPrivateKey(value)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        loading = controller.busy,
                        enabled = !controller.busy,
                        leadingGlyph = Glyph.Check,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportMethodPicker(
    selected: ImportMethod?,
    enabled: Boolean,
    onSelect: (ImportMethod) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(ImportMotionMillis, easing = FastOutSlowInEasing)),
        verticalArrangement = Arrangement.spacedBy(Space.x5),
    ) {
        if (selected == null) {
            ImportMethod.values().forEach { method ->
                ImportMethodCard(
                    method = method,
                    selected = false,
                    compact = false,
                    enabled = enabled,
                    onClick = { onSelect(method) },
                )
            }
        } else {
            ImportMethodCard(
                method = selected,
                selected = true,
                compact = false,
                enabled = enabled,
                onClick = { onSelect(selected) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
                ImportMethod.values()
                    .filter { it != selected }
                    .forEach { method ->
                        ImportMethodCard(
                            method = method,
                            selected = false,
                            compact = true,
                            enabled = enabled,
                            onClick = { onSelect(method) },
                            modifier = Modifier.weight(1f),
                        )
                    }
            }
        }
    }
}

@Composable
private fun ImportMethodCard(
    method: ImportMethod,
    selected: Boolean,
    compact: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scale by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.99f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "importMethodScale",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(Radius.md))
            .background(if (selected) SurfaceRaised.copy(alpha = 0.14f) else SurfaceMuted.copy(alpha = 0.72f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(
                horizontal = if (compact) Space.x4 else Space.x7,
                vertical = if (compact) Space.x4 else Space.x7,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 30.dp else 42.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(if (selected) SurfaceRaised else InfoBlue.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph = method.glyph(),
                modifier = Modifier.size(if (compact) 15.dp else 21.dp),
                color = if (selected) PrimaryContent else InfoBlue,
            )
        }
        Spacer(Modifier.width(if (compact) Space.x3 else Space.x6))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (compact) method.compactTitle() else method.title(),
                color = if (enabled) TextTertiary else TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!compact) {
                Spacer(Modifier.height(Space.x2))
                Text(
                    text = method.subtitle(),
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(Space.x4))
            GlyphIcon(Glyph.Check, modifier = Modifier.size(16.dp), color = SurfaceRaised)
        }
    }
}

@Composable
private fun ImportSecretForm(
    method: ImportMethod,
    value: String,
    onValueChange: (String) -> Unit,
    secretVisible: Boolean,
    onToggleSecret: () -> Unit,
    error: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(Modifier.padding(Space.x7)) {
            Text(method.title(), color = TextTertiary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Space.x2))
            Text(
                method.supportingText(),
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.x6))
            PoctraTextField(
                value = value,
                onValueChange = onValueChange,
                label = method.inputLabel(),
                placeholder = method.placeholder(),
                singleLine = method != ImportMethod.Mnemonic,
                minLines = if (method == ImportMethod.Mnemonic) 5 else 1,
                isError = error != null,
                supportingText = error,
                password = !secretVisible,
                trailingContent = {
                    SensitiveToggleButton(
                        visible = secretVisible,
                        onToggle = onToggleSecret,
                        contentDescription = if (secretVisible) "Hide import key" else "Show import key",
                    )
                },
            )
        }
    }
}

private fun ImportMethod.title(): String = when (this) {
    ImportMethod.Mnemonic -> "Mnemonic phrase"
    ImportMethod.PrivateKey -> "Private Key (Base64)"
    ImportMethod.EthereumPrivateKey -> "Ethereum Private Key"
}

private fun ImportMethod.compactTitle(): String = when (this) {
    ImportMethod.Mnemonic -> "Mnemonic"
    ImportMethod.PrivateKey -> "Base64 Key"
    ImportMethod.EthereumPrivateKey -> "EVM Key"
}

private fun ImportMethod.subtitle(): String = when (this) {
    ImportMethod.Mnemonic -> "Use a 12 or 24 word seed phrase."
    ImportMethod.PrivateKey -> "Use an Octra-compatible Base64 encoded 32-byte key."
    ImportMethod.EthereumPrivateKey -> "Use a 0x hex key and derive an Octra wallet from it."
}

private fun ImportMethod.glyph(): Glyph = when (this) {
    ImportMethod.Mnemonic -> Glyph.FileText
    ImportMethod.PrivateKey -> Glyph.Key
    ImportMethod.EthereumPrivateKey -> Glyph.Evm
}

private fun ImportMethod.inputLabel(): String = when (this) {
    ImportMethod.Mnemonic -> "Seed phrase"
    ImportMethod.PrivateKey -> "Base64 private key"
    ImportMethod.EthereumPrivateKey -> "Ethereum private key"
}

private fun ImportMethod.placeholder(): String = when (this) {
    ImportMethod.Mnemonic -> "twelve or twenty four words"
    ImportMethod.PrivateKey -> "Base64 encoded 32-byte key"
    ImportMethod.EthereumPrivateKey -> "0x..."
}

private fun ImportMethod.supportingText(): String = when (this) {
    ImportMethod.Mnemonic -> "Only seed phrases that derive a valid Octra address can be imported."
    ImportMethod.PrivateKey -> "The decoded 32-byte key must derive a valid Octra address."
    ImportMethod.EthereumPrivateKey -> "The secp256k1 key must also derive a valid Octra address."
}

@Composable
private fun SensitiveToggleButton(
    visible: Boolean,
    onToggle: () -> Unit,
    contentDescription: String,
) {
    Box(
        modifier = Modifier
            .padding(end = Space.x1)
            .size(22.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(SurfaceMuted)
            .clickable(onClick = onToggle)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            glyph = if (visible) Glyph.EyeOff else Glyph.Eye,
            modifier = Modifier.size(12.dp),
            color = TextTertiary,
        )
    }
}

private fun validateImport(method: ImportMethod, value: String): String? {
    return when (method) {
        ImportMethod.Mnemonic -> {
            val words = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
            if (words.size != 12 && words.size != 24) "Seed phrase must be 12 or 24 words." else null
        }
        ImportMethod.PrivateKey -> {
            val bytes = runCatching { Base64.decode(value.trim(), Base64.DEFAULT) }.getOrNull()
            if (bytes?.size != 32) "Private key must be Base64 and decode to 32 bytes." else null
        }
        ImportMethod.EthereumPrivateKey -> {
            val clean = value.trim().removePrefix("0x").removePrefix("0X")
            if (!clean.matches(Regex("^[0-9a-fA-F]{64}$"))) {
                "Ethereum private key must be 64 hex characters, with optional 0x prefix."
            } else {
                null
            }
        }
    }
}

@Composable
private fun SecuritySetupScreen(activity: Activity, controller: PoctraController) {
    var showPinFlow by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Space.x8))
        GlyphIcon(Glyph.Pin, modifier = Modifier.size(64.dp), color = SurfaceRaised)
        Spacer(Modifier.height(Space.x8))
        Text("Secure your wallet", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.x4))
        Text(
            "Choose at least one security method before entering poctra.",
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.x8))

        if (!showPinFlow) {
            if (controller.biometricAvailable) {
                SecurityOptionCard(
                    title = "Use Fingerprint",
                    subtitle = "Encrypt wallet key with Android Keystore and biometric auth.",
                    glyph = Glyph.Fingerprint,
                    enabled = !controller.busy,
                    onClick = { startEnrollBiometric(activity, controller) },
                )
                Spacer(Modifier.height(Space.x6))
            } else {
                StateBanner(
                    text = "Fingerprint is unavailable on this device. Set a 6-digit PIN instead.",
                    isError = false,
                )
                Spacer(Modifier.height(Space.x6))
            }

            SecurityOptionCard(
                title = "Use PIN",
                subtitle = "Set a 6-digit PIN. Wallet data is encrypted with a PIN-derived key.",
                glyph = Glyph.Pin,
                enabled = !controller.busy,
                onClick = { showPinFlow = true },
            )
        } else {
            PinSetupPane(
                busy = controller.busy,
                onCancel = { showPinFlow = false },
                onComplete = controller::savePendingWithPin,
            )
        }
    }
}

@Composable
private fun PinSetupPane(
    busy: Boolean,
    onCancel: () -> Unit,
    onComplete: (String) -> Unit,
) {
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxWidth()) {
        StateBanner(
            text = "PIN is a local fallback. Use fingerprint when available and avoid simple repeated or sequential digits.",
            isError = false,
        )
        Spacer(Modifier.height(Space.x6))
        PinField(
            value = if (confirming) confirmPin else firstPin,
            onValueChange = {
                if (confirming) confirmPin = it else firstPin = it
                localError = null
            },
            label = if (confirming) "Confirm PIN" else "Create PIN",
            error = localError,
        )
        Spacer(Modifier.height(Space.x6))
        PoctraButton(
            text = if (confirming) "Save PIN" else "Continue",
            onClick = {
                if (!confirming) {
                    localError = when {
                        firstPin.length != 6 -> "PIN must be exactly 6 digits."
                        isWeakPin(firstPin) -> "Choose a less predictable PIN."
                        else -> null
                    }
                    if (localError == null) confirming = true
                } else if (firstPin != confirmPin) {
                    localError = "PINs do not match."
                    confirmPin = ""
                } else {
                    onComplete(firstPin)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            loading = busy,
            enabled = !busy,
        )
        Spacer(Modifier.height(Space.x6))
        PoctraButton(
            text = "Choose another method",
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Ghost,
            enabled = !busy,
        )
    }
}

private fun isWeakPin(pin: String): Boolean {
    if (pin.toSet().size <= 2) return true
    val digits = pin.map { it.digitToIntOrNull() ?: return true }
    val ascending = digits.zipWithNext().all { (a, b) -> b == (a + 1) % 10 }
    val descending = digits.zipWithNext().all { (a, b) -> b == (a + 9) % 10 }
    return ascending || descending
}

@Composable
private fun UnlockScreen(
    activity: Activity,
    controller: PoctraController,
    automaticUnlockEnabled: Boolean,
) {
    val method = controller.currentSecurityMethod
    val lifecycleOwner = activity as? LifecycleOwner
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var biometricPromptActive by remember { mutableStateOf(false) }
    var lifecycleStartRequest by remember { mutableIntStateOf(0) }
    val biometricPromptGate = remember { com.poctra.wallet.security.BiometricPromptGate() }
    val automaticPromptGate = remember { com.poctra.wallet.security.BiometricPromptGate() }

    fun startBiometricUnlock(): Boolean {
        if (method != SecurityMethod.Biometric || !controller.biometricAvailable || controller.busy) return false
        if (!biometricPromptGate.tryStart()) return false
        biometricPromptActive = true
        startUnlockBiometric(activity, controller) {
            biometricPromptGate.finish()
            biometricPromptActive = false
        }
        return true
    }

    fun autoStartBiometricUnlock() {
        if (!automaticUnlockEnabled) return
        if (!automaticPromptGate.tryStart()) return
        if (!startBiometricUnlock()) automaticPromptGate.finish()
    }

    LaunchedEffect(
        method,
        controller.biometricAvailable,
        controller.busy,
        automaticUnlockEnabled,
        lifecycleStartRequest,
    ) {
        if (lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true) {
            // Let the unlock content complete layout and draw before showing the system prompt.
            withFrameNanos { }
            withFrameNanos { }
            autoStartBiometricUnlock()
        }
    }

    DisposableEffect(lifecycleOwner, method, controller.biometricAvailable, automaticUnlockEnabled) {
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                when (event) {
                    Lifecycle.Event.ON_START -> lifecycleStartRequest += 1
                    Lifecycle.Event.ON_STOP -> automaticPromptGate.finish()
                    else -> Unit
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(com.poctra.wallet.R.drawable.poctra_logo),
            contentDescription = "poctra",
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(Space.x6))
        Text("Unlock wallet", color = TextSecondary)
        Spacer(Modifier.height(Space.x8))

        when (method) {
            SecurityMethod.Biometric -> {
                GlyphIcon(Glyph.Fingerprint, modifier = Modifier.size(72.dp), color = SurfaceRaised)
                Spacer(Modifier.height(Space.x8))
                Text(
                    "Waiting for fingerprint confirmation",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(Space.x6))
                PoctraButton(
                    text = "Try Fingerprint Again",
                    onClick = { startBiometricUnlock() },
                    modifier = Modifier.fillMaxWidth(),
                    loading = controller.busy,
                    enabled = controller.biometricAvailable && !controller.busy && !biometricPromptActive,
                    leadingGlyph = Glyph.Fingerprint,
                )
                if (!controller.biometricAvailable) {
                    Spacer(Modifier.height(Space.x6))
                    StateBanner("Fingerprint is not available on this device.", isError = true)
                }
            }

            SecurityMethod.Pin -> {
                PinField(
                    value = pin,
                    onValueChange = {
                        pin = it
                        pinError = null
                    },
                    label = "6-digit PIN",
                    error = pinError,
                )
                Spacer(Modifier.height(Space.x6))
                PoctraButton(
                    text = "Unlock",
                    onClick = {
                        if (pin.length != 6) {
                            pinError = "PIN must be exactly 6 digits."
                        } else {
                            controller.unlockWithPin(pin)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    loading = controller.busy,
                    enabled = !controller.busy,
                )
            }

            null -> StateBanner("No secured wallet was found.", isError = true)
        }
    }
}

@Composable
private fun DashboardScreen(controller: PoctraController) {
    val wallet = controller.wallet
    var walletDrawerOpen by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val refreshThresholdPx = with(density) { 84.dp.toPx() }
    var pullDistance by remember { mutableStateOf(0f) }
    val overviewRefreshing = controller.dashboardTab == DashboardTab.Overview &&
        controller.overviewRefreshLoading

    if (wallet == null) {
        LoadingScreen()
        return
    }

    LaunchedEffect(pullDistance, overviewRefreshing, controller.dashboardTab) {
        if (controller.dashboardTab == DashboardTab.Overview &&
            pullDistance >= refreshThresholdPx &&
            !overviewRefreshing
        ) {
            controller.refreshOverview()
            pullDistance = 0f
        }
    }

    val pullRefreshConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (controller.dashboardTab != DashboardTab.Overview || source != NestedScrollSource.Drag) return Offset.Zero
            if (pullDistance <= 0f || available.y >= 0f) return Offset.Zero
            val consumed = minOf(pullDistance, -available.y)
            pullDistance -= consumed
            return Offset(0f, -consumed)
        }

        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
            if (controller.dashboardTab != DashboardTab.Overview || source != NestedScrollSource.Drag) return Offset.Zero
            if (scrollState.value == 0 && available.y > 0f && !overviewRefreshing) {
                pullDistance = (pullDistance + available.y * 0.55f).coerceAtMost(refreshThresholdPx * 1.25f)
                return Offset(0f, available.y)
            }
            return Offset.Zero
        }

        override suspend fun onPreFling(available: Velocity): Velocity {
            if (controller.dashboardTab == DashboardTab.Overview && pullDistance >= refreshThresholdPx && !overviewRefreshing) {
                controller.refreshOverview()
            }
            pullDistance = 0f
            return Velocity.Zero
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .nestedScroll(pullRefreshConnection)
                .verticalScroll(scrollState)
                .padding(horizontal = Space.x8, vertical = Space.x7)
                .padding(bottom = 88.dp),
        ) {
            when (controller.dashboardTab) {
                DashboardTab.Overview -> OverviewContent(
                    controller = controller,
                    address = wallet.address,
                    onOpenWallets = { walletDrawerOpen = true },
                )
                DashboardTab.Assets -> AssetsContent(controller)
                DashboardTab.Privacy -> PrivacyContent(controller)
                DashboardTab.Explore -> ExploreDAppsContent(controller)
                DashboardTab.Trade -> TradeContent(controller)
            }
        }

        if (controller.dashboardTab == DashboardTab.Overview && (pullDistance > 0f || overviewRefreshing)) {
            OverviewPullRefreshIndicator(
                refreshing = overviewRefreshing,
                ready = pullDistance >= refreshThresholdPx,
                progress = (pullDistance / refreshThresholdPx).coerceIn(0f, 1f),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = Space.x4),
            )
        }

        DashboardBottomNav(
            selected = controller.dashboardTab,
            onSelect = controller::selectDashboardTab,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        if (walletDrawerOpen) {
            WalletDrawerOverlay(
                controller = controller,
                onClose = { walletDrawerOpen = false },
            )
        }
    }
}

@Composable
private fun OverviewPullRefreshIndicator(
    refreshing: Boolean,
    ready: Boolean,
    progress: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceStrong)
            .padding(horizontal = Space.x5, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (refreshing) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = SurfaceRaised,
                strokeWidth = 2.dp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(SurfaceRaised.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph = Glyph.Refresh,
                    modifier = Modifier.size((12 + (progress * 4)).dp),
                    color = if (ready) SurfaceRaised else TextSecondary,
                )
            }
        }
        Spacer(Modifier.width(Space.x3))
        Text(
            text = when {
                refreshing -> "Refreshing"
                ready -> "Release to refresh"
                else -> "Pull to refresh"
            },
            color = if (ready || refreshing) TextTertiary else TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun DashboardTopBar(
    controller: PoctraController,
    activeAddress: String,
    onToggleWallets: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AddressDropdownBlock(
            walletName = controller.walletOverviewDisplayName(activeAddress),
            address = activeAddress,
            identiconSvg = controller.walletIdenticonSvg(activeAddress),
            onClick = onToggleWallets,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Space.x4))
        val status = controller.rpcStatus
        OverviewNetworkChip(
            text = status?.network ?: controller.activeRpcNetwork?.networkType ?: "mainnet",
            state = when (status?.state) {
                RpcStatus.State.Connected -> "connected"
                RpcStatus.State.Failed -> "failed"
                else -> "checking"
            },
            onClick = controller::openRpcManagement,
            enabled = !controller.busy,
        )
        Spacer(Modifier.width(Space.x4))
        IconCircleButton(
            glyph = Glyph.Settings,
            contentDescription = "Settings",
            onClick = controller::openSettings,
            enabled = !controller.busy,
            buttonSize = OverviewHeaderHeight,
            iconSize = 20.dp,
        )
    }
}

@Composable
private fun OverviewNetworkChip(
    text: String,
    state: String?,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    val borderColor = when (state) {
        "connected" -> SurfaceRaised
        "failed" -> ErrorRed
        else -> TextSecondary
    }
    Column(
        modifier = Modifier
            .height(OverviewHeaderHeight)
            .clip(RoundedCornerShape(Radius.md))
            .background(if (enabled) borderColor.copy(alpha = 0.10f) else SurfaceMuted)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "Open Octra RPC network settings" }
            .padding(horizontal = Space.x5, vertical = Space.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlyphIcon(
            glyph = Glyph.Wifi,
            modifier = Modifier.size(16.dp),
            color = if (state == "failed") ErrorRed else SurfaceRaised,
        )
        Spacer(Modifier.height(1.dp))
        Text(
            text = networkTypeLabel(text),
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun AddressDropdownBlock(
    walletName: String,
    address: String,
    identiconSvg: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(OverviewHeaderHeight)
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceMuted.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WalletIdenticonView(svg = identiconSvg, size = 30.dp)
        Spacer(Modifier.width(Space.x4))
        Column(Modifier.weight(1f)) {
            Text(
                text = walletName,
                color = SurfaceRaised,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = headerShortAddress(address),
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.x3))
        GlyphIcon(Glyph.ChevronDown, modifier = Modifier.size(16.dp), color = TextSecondary)
    }
}

@Composable
private fun WalletDrawerOverlay(
    controller: PoctraController,
    onClose: () -> Unit,
) {
    var drawerVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { drawerVisible = true }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (drawerVisible) 0.28f else 0f,
        animationSpec = tween(ScrimMotionMillis),
        label = "walletDrawerScrim",
    )
    Box(Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(onClick = onClose),
        )
        AnimatedVisibility(
            visible = drawerVisible,
            modifier = Modifier.align(Alignment.CenterStart),
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(SheetEnterMillis, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(ScrimMotionMillis)),
            exit = slideOutHorizontally(
                targetOffsetX = { -it / 2 },
                animationSpec = tween(SheetExitMillis, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(SheetExitMillis)),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(326.dp)
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(topEnd = Radius.md, bottomEnd = Radius.md),
                colors = CardDefaults.cardColors(containerColor = SurfaceBase),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Space.x7),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(Radius.md))
                                .background(SurfaceRaised.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            GlyphIcon(Glyph.Wallet, modifier = Modifier.size(18.dp), color = SurfaceRaised)
                        }
                        Spacer(Modifier.width(Space.x5))
                        Text("Wallets", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
                    }
                    Spacer(Modifier.height(Space.x7))
                    val wallets = controller.vault?.wallets.orEmpty()
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(Space.x4),
                    ) {
                        itemsIndexed(
                            items = wallets,
                            key = { _, item -> item.address },
                        ) { index, item ->
                            DrawerWalletRow(
                                wallet = item,
                                position = index + 1,
                                displayName = controller.walletDisplayName(item.address),
                                identityName = controller.walletIdentityDomain(item.address),
                                active = item.address == controller.vault?.activeAddress,
                                canRemove = (controller.vault?.wallets?.size ?: 0) > 1,
                                onSelect = {
                                    controller.switchWallet(item.address)
                                    onClose()
                                },
                                onRename = { controller.renameWallet(item.address, it) },
                                onRemove = { controller.removeWallet(item.address) },
                                onMove = { direction ->
                                    val targetIndex = (index + direction).coerceIn(0, wallets.lastIndex)
                                    if (targetIndex != index) {
                                        controller.reorderWallet(fromIndex = index, toIndex = targetIndex)
                                        true
                                    } else {
                                        false
                                    }
                                },
                                onReorderFinished = controller::saveWalletOrder,
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.x6))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
                        PoctraButton(
                            text = "Create",
                            onClick = controller::createWallet,
                            modifier = Modifier.weight(1f),
                            leadingGlyph = Glyph.Plus,
                            loading = controller.busy,
                            enabled = !controller.busy,
                        )
                        PoctraButton(
                            text = "Import",
                            onClick = controller::openImportWallet,
                            modifier = Modifier.weight(1f),
                            variant = ButtonVariant.Ghost,
                            enabled = !controller.busy,
                            leadingGlyph = Glyph.Import,
                            fullHeight = true,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerWalletRow(
    modifier: Modifier = Modifier,
    wallet: com.poctra.wallet.data.Wallet,
    position: Int,
    displayName: String,
    identityName: String?,
    active: Boolean,
    canRemove: Boolean,
    onSelect: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    onMove: (Int) -> Boolean,
    onReorderFinished: () -> Unit,
) {
    var editing by remember(wallet.address) { mutableStateOf(false) }
    var name by remember(wallet.address, displayName) { mutableStateOf(displayName) }
    var dragDistance by remember(wallet.address) { mutableStateOf(0f) }
    var dragging by remember(wallet.address) { mutableStateOf(false) }
    var reorderedDuringDrag by remember(wallet.address) { mutableStateOf(false) }
    val focusRequester = remember(wallet.address) { FocusRequester() }
    val currentMove = rememberUpdatedState(onMove)
    val currentReorderFinished = rememberUpdatedState(onReorderFinished)
    val density = LocalDensity.current
    val dragThreshold = with(density) { 44.dp.toPx() }
    val cardShape = RoundedCornerShape(Radius.md)
    val dragOffset by animateFloatAsState(
        targetValue = if (dragging) dragDistance.coerceIn(-dragThreshold, dragThreshold) * 0.35f else 0f,
        label = "walletDragOffset",
    )
    val dragScale by animateFloatAsState(
        targetValue = if (dragging) 1.018f else 1f,
        label = "walletDragScale",
    )
    val dragShadow by animateDpAsState(
        targetValue = if (dragging) 12.dp else 0.dp,
        label = "walletDragShadow",
    )
    val dragShadowPx = with(density) { dragShadow.toPx() }

    LaunchedEffect(editing) {
        if (editing) focusRequester.requestFocus()
    }

    val dragModifier = if (editing) {
        Modifier
    } else {
        Modifier.pointerInput(wallet.address) {
            detectDragGesturesAfterLongPress(
                onDragStart = {
                    dragging = true
                    dragDistance = 0f
                    reorderedDuringDrag = false
                },
                onDragCancel = {
                    dragging = false
                    dragDistance = 0f
                    if (reorderedDuringDrag) currentReorderFinished.value()
                },
                onDragEnd = {
                    dragging = false
                    dragDistance = 0f
                    if (reorderedDuringDrag) currentReorderFinished.value()
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragDistance += dragAmount.y
                    if (abs(dragDistance) >= dragThreshold) {
                        val moved = currentMove.value(if (dragDistance > 0) 1 else -1)
                        dragDistance = 0f
                        if (moved) reorderedDuringDrag = true
                    }
                },
            )
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .zIndex(if (dragging) 1f else 0f)
            .graphicsLayer {
                translationY = dragOffset
                scaleX = dragScale
                scaleY = dragScale
                shadowElevation = dragShadowPx
                shape = cardShape
                clip = false
            }
            .clip(cardShape)
            .background(
                when {
                    dragging -> SurfaceRaised.copy(alpha = 0.15f)
                    active -> SurfaceRaised.copy(alpha = 0.09f)
                    else -> SurfaceStrong
                }
            )
            .then(if (!editing) Modifier.clickable(onClick = onSelect) else Modifier)
            .then(dragModifier)
            .padding(start = Space.x5, top = Space.x5, end = Space.x5, bottom = Space.x4),
    ) {
        if (editing) {
            PoctraTextField(
                value = name,
                onValueChange = { name = it.take(32) },
                label = "Wallet name",
                modifier = Modifier.focusRequester(focusRequester),
                singleLine = true,
            )
            Spacer(Modifier.height(Space.x5))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
                PoctraButton(
                    text = "Save",
                    onClick = {
                        onRename(name)
                        editing = false
                    },
                    modifier = Modifier.weight(1f),
                    leadingGlyph = Glyph.Check,
                    compact = true,
                )
                PoctraButton(
                    text = "Cancel",
                    onClick = {
                        name = displayName
                        editing = false
                    },
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.Ghost,
                    compact = true,
                )
            }
        } else {
            Box(Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 34.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(if (active) SurfaceRaised.copy(alpha = 0.14f) else SurfaceMuted.copy(alpha = 0.72f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = position.toString(),
                                color = if (active) SurfaceRaised else TextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        Spacer(Modifier.width(Space.x4))
                        Text(
                            displayName,
                            color = TextTertiary,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        Spacer(Modifier.width(Space.x2))
                        WalletDrawerActionIcon(
                            glyph = Glyph.Pencil,
                            contentDescription = "Rename wallet",
                            onClick = {
                                name = displayName
                                editing = true
                            },
                            bare = true,
                        )
                    }
                    Spacer(Modifier.height(Space.x3))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(Space.x3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = shortAddress(wallet.address),
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        identityName?.let {
                            WalletMetaChip(
                                it,
                                background = SurfaceRaised.copy(alpha = 0.14f),
                                color = SurfaceRaised,
                            )
                        }
                    }
                    Spacer(Modifier.height(Space.x5))
                    WalletMetaChip(
                        walletTypeLabel(wallet),
                        background = InfoBlue.copy(alpha = if (active) 0.18f else 0.12f),
                        color = InfoBlue,
                    )
                }
                if (active) {
                    GlyphIcon(
                        Glyph.Check,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = Space.x1, end = Space.x1)
                            .size(15.dp),
                        color = SurfaceRaised,
                    )
                }
                WalletDrawerActionIcon(
                    glyph = Glyph.Drag,
                    contentDescription = "Reorder wallet",
                    onClick = {},
                    active = dragging,
                    clickable = false,
                    bare = true,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
                if (canRemove) {
                    WalletDrawerActionIcon(
                        glyph = Glyph.Trash,
                        contentDescription = "Remove wallet",
                        onClick = onRemove,
                        danger = true,
                        modifier = Modifier.align(Alignment.BottomEnd),
                    )
                }
            }
        }
    }
}

@Composable
private fun WalletDrawerActionIcon(
    glyph: Glyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    active: Boolean = false,
    clickable: Boolean = true,
    bare: Boolean = false,
) {
    val baseModifier = if (bare) {
        modifier
            .size(26.dp)
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { this.contentDescription = contentDescription }
    } else {
        modifier
            .size(26.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(
                when {
                    danger -> ErrorRed.copy(alpha = 0.14f)
                    active -> SurfaceRaised.copy(alpha = 0.18f)
                    else -> SurfaceMuted
                },
            )
            .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
            .semantics { this.contentDescription = contentDescription }
    }
    Box(
        modifier = baseModifier,
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            glyph,
            modifier = Modifier.size(if (bare) 15.dp else 12.dp),
            color = when {
                danger -> ErrorRed
                active -> SurfaceRaised
                else -> TextSecondary
            },
        )
    }
}

@Composable
private fun WalletMetaChip(text: String, background: Color, color: Color) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(background)
            .padding(horizontal = Space.x4, vertical = Space.x2),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun walletTypeLabel(wallet: com.poctra.wallet.data.Wallet): String = when (wallet.type) {
    com.poctra.wallet.data.WalletType.Generated -> "Generated"
    com.poctra.wallet.data.WalletType.ImportedPrivateKey -> "Imported (Key)"
    com.poctra.wallet.data.WalletType.ImportedMnemonic -> "Imported (Mnemonic)"
    com.poctra.wallet.data.WalletType.ImportedEthereumPrivateKey -> "Imported (EVM Key)"
}

fun walletTypeLabel(type: WalletType): String = when (type) {
    WalletType.Generated -> "Generated"
    WalletType.ImportedPrivateKey -> "Imported (Key)"
    WalletType.ImportedMnemonic -> "Imported (Mnemonic)"
    WalletType.ImportedEthereumPrivateKey -> "Imported (EVM Key)"
}

@Composable
private fun OverviewContent(
    controller: PoctraController,
    address: String,
    onOpenWallets: () -> Unit,
) {
    DashboardTopBar(
        controller = controller,
        activeAddress = address,
        onToggleWallets = onOpenWallets,
    )
    Spacer(Modifier.height(Space.x8))
    BalanceHero(controller)
    Spacer(Modifier.height(Space.x8))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x6)) {
        WalletActionButton("Send", Glyph.Send, Modifier.weight(1f), controller.busy, controller::openSend)
        WalletActionButton("Receive", Glyph.Receive, Modifier.weight(1f), controller.busy, controller::openReceive)
        WalletActionButton("Bridge", Glyph.Bridge, Modifier.weight(1f), controller.busy, controller::openBridge)
    }
    Spacer(Modifier.height(32.dp))
    RecentActivity(controller, address)
}

@Composable
private fun BalanceHero(controller: PoctraController) {
    Column(Modifier.fillMaxWidth()) {
        if (controller.tokenBalance.isLoading) {
            CircularProgressIndicator(color = SurfaceRaised, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(Space.x6))
            Text("Syncing balance", color = TextSecondary)
        } else {
            Text(
                text = "Public Balance",
                color = SurfaceRaised,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Space.x4))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    controller.tokenBalance.amount,
                    color = TextTertiary,
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 1,
                )
                Spacer(Modifier.width(Space.x5))
                Text(
                    "OCT",
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = Space.x5),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(Space.x3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(2.dp)
                        .background(SurfaceRaised),
                )
                Spacer(Modifier.width(Space.x4))
                Text(controller.tokenBalance.fiatEstimate, color = TextSecondary)
            }
        }
    }
}

@Composable
private fun RecentActivity(controller: PoctraController, address: String) {
    val syncingActivity = controller.historyLoading || controller.overviewRefreshLoading
    LaunchedEffect(
        controller.recentTransactions,
        controller.activeIdentityProvider?.id,
        controller.activeRpcNetwork?.id,
    ) {
        controller.refreshRecentActivityIdentityNames()
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Recent Activity",
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "All",
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.sm))
                .background(SurfaceRaised.copy(alpha = 0.12f))
                .clickable(onClick = controller::openActivityCenter)
                .padding(horizontal = Space.x4, vertical = Space.x2),
            color = SurfaceRaised,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.width(Space.x4))
        if (syncingActivity) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = SurfaceRaised,
                    strokeWidth = 1.5.dp,
                )
                Spacer(Modifier.width(Space.x2))
                Text("Syncing", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
    Spacer(Modifier.height(Space.x7))
    if (syncingActivity && controller.recentTransactions.isEmpty()) {
        StateBanner("Syncing recent activity...", isError = false)
    } else if (controller.recentTransactions.isEmpty()) {
        RecentActivityEmpty()
    } else {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            controller.recentTransactions.forEach { tx ->
                RecentActivityRow(
                    tx = tx,
                    address = address,
                    identityName = controller.identityNameForAddress(if (tx.isOutgoing(address)) tx.to else tx.from),
                    onClick = { controller.openTransactionDetails(tx) },
                )
            }
        }
        Spacer(Modifier.height(Space.x6))
        if (controller.historyCanLoadMore) {
            InlineListAction(
                text = "Load More",
                onClick = controller::loadMoreHistory,
                loading = controller.historyLoading,
                enabled = !controller.historyLoading,
                glyph = Glyph.ChevronDown,
            )
        } else {
            val url = explorerUrl(
                host = controller.activeRpcNetwork?.explorerUrl.orEmpty(),
                page = "address.html",
                key = "addr",
                value = address,
            )
            InlineListAction(
                text = "View All on Explorer",
                onClick = {
                    if (url == null) controller.showError("Set an explorer URL in RPC Management first.")
                    else controller.openDAppBrowser("Transaction history", url)
                },
                glyph = Glyph.Browser,
            )
        }
    }
}

@Composable
private fun RecentActivityRow(
    tx: TransactionHistoryItem,
    address: String,
    identityName: String?,
    onClick: () -> Unit,
) {
    val outgoing = tx.isOutgoing(address)
    val counterparty = if (outgoing) tx.to else tx.from
    val statusLabel = when {
        tx.status == "pending" -> "Pending"
        outgoing -> "Sent"
        else -> "Received"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.x1, vertical = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(if (outgoing) TextSecondary.copy(alpha = 0.10f) else SurfaceRaised.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph = if (outgoing) Glyph.Send else Glyph.Receive,
                modifier = Modifier.size(14.dp),
                color = if (outgoing) TextSecondary else SurfaceRaised,
            )
        }
        Spacer(Modifier.width(Space.x5))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = statusLabel,
                    color = TextTertiary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.width(Space.x3))
                OpTypeBadge(tx.opType)
            }
            Spacer(Modifier.height(Space.x1))
            Text(
                text = activityCounterpartyLabel(
                    prefix = if (outgoing) "to" else "from",
                    address = counterparty,
                    identityName = identityName,
                ),
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.x5))
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${if (outgoing) "-" else "+"}${formatOctAmount(tx.amount)}",
                color = if (outgoing) TextTertiary else SurfaceRaised,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Spacer(Modifier.height(Space.x1))
            Text(
                text = formatRecentActivityTime(tx.timestamp),
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun RecentActivityEmpty() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No activity yet", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
    }
}

private fun activityCounterpartyLabel(prefix: String, address: String, identityName: String?): String {
    val short = shortAddress(address)
    val name = identityName?.takeIf { it.isNotBlank() }
    return if (name == null) "$prefix $short" else "$prefix $name ($short)"
}

@Composable
private fun OpTypeBadge(text: String) {
    val key = text.ifBlank { "standard" }.lowercase()
    val label = titleCaseLabel(text.ifBlank { "standard" })
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(
                when (key) {
                    "private", "stealth" -> SurfaceRaised.copy(alpha = 0.16f)
                    "encrypt", "decrypt" -> Warning.copy(alpha = 0.16f)
                    "claim" -> Color(0xFF3B567F).copy(alpha = 0.16f)
                    else -> SurfaceMuted
                },
            )
            .padding(horizontal = Space.x4, vertical = Space.x2),
        color = TextTertiary,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
        maxLines = 1,
    )
}

private fun titleCaseLabel(value: String): String {
    return value
        .trim()
        .replace('_', ' ')
        .replace('-', ' ')
        .lowercase()
        .split(Regex("\\s+"))
        .filter { it.isNotBlank() }
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }
        .ifBlank { value }
}

private fun formatRecentActivityTime(timestamp: Double): String {
    if (timestamp <= 0.0) return "-"
    return android.text.format.DateFormat.format("MMM d HH:mm", (timestamp * 1000).toLong()).toString()
}

@Composable
private fun AssetsContent(controller: PoctraController) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Assets", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
            Text("Octra assets and EVM account", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
    Spacer(Modifier.height(Space.x7))
    AssetsSectionHeader(
        title = "EVM Account",
        subtitle = controller.activeEvmNetwork?.name ?: "Derived account",
    )
    Spacer(Modifier.height(Space.x4))
    EvmAccountCard(controller)
    Spacer(Modifier.height(Space.x8))
    AssetsSectionHeader(
        title = "Octra Assets",
        subtitle = "Native OCT and OCS01 tokens",
        actionLabel = "Add",
        actionGlyph = Glyph.Plus,
        onAction = controller::openAddAsset,
        enabled = !controller.busy,
    )
    Spacer(Modifier.height(Space.x4))
    TokenRow(controller.tokenBalance)
    if (controller.assetsLoading) {
        Spacer(Modifier.height(Space.x6))
        StateBanner("Loading OCS01 assets...", isError = false)
    }
    if (controller.octraTokens.isNotEmpty()) {
        Spacer(Modifier.height(Space.x6))
        Column(verticalArrangement = Arrangement.spacedBy(Space.x6)) {
            controller.octraTokens.forEach { token ->
                OctraTokenRow(
                    token = token,
                    onSend = { controller.openSendAsset(token) },
                    onRemove = { controller.removeAsset(token) },
                )
            }
        }
    }
}

@Composable
private fun AssetsSectionHeader(
    title: String,
    subtitle: String,
    actionLabel: String? = null,
    actionGlyph: Glyph? = null,
    onAction: (() -> Unit)? = null,
    enabled: Boolean = true,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TextTertiary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (actionLabel != null && actionGlyph != null && onAction != null) {
            MiniHeaderAction(
                glyph = actionGlyph,
                label = actionLabel,
                onClick = onAction,
                enabled = enabled,
            )
        }
    }
}

@Composable
private fun MiniHeaderAction(
    glyph: Glyph,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean = false,
) {
    Column(
        modifier = Modifier
            .width(50.dp)
            .height(42.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(if (enabled) SurfaceMuted.copy(alpha = 0.78f) else SurfaceMuted.copy(alpha = 0.48f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Space.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = SurfaceRaised, strokeWidth = 2.dp)
        } else {
            GlyphIcon(
                glyph = glyph,
                modifier = Modifier.size(14.dp),
                color = if (enabled) TextTertiary else TextSecondary.copy(alpha = 0.45f),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.45f),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EvmAccountCard(controller: PoctraController) {
    val account = controller.evmAccount
    val network = controller.activeEvmNetwork
    val derivedAddress = controller.currentEvmAddress()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = controller::openEvm),
        shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = SurfaceMuted.copy(alpha = 0.62f)),
    ) {
        Row(
            modifier = Modifier.padding(Space.x7),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(SurfaceRaised.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.Evm, modifier = Modifier.size(24.dp), color = SurfaceRaised)
            }
            Spacer(Modifier.width(Space.x6))
            Column(Modifier.weight(1f)) {
                Text("EVM Account", color = TextTertiary, fontWeight = FontWeight.SemiBold)
                Text(
                    text = derivedAddress?.let(::shortAddress) ?: "EVM address unavailable",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = if (controller.evmLoading) "--" else account?.balance ?: "--",
                    color = TextTertiary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(network?.symbol ?: "ETH", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun TradeContent(controller: PoctraController) {
    var fromAsset by remember { mutableStateOf(TradeAsset.WOCT) }
    var toAsset by remember { mutableStateOf(TradeAsset.ETH) }
    var amount by remember { mutableStateOf("") }
    var slippageBps by remember { mutableStateOf(50) }
    var localError by remember { mutableStateOf<String?>(null) }
    var showReview by remember { mutableStateOf(false) }
    var openedReviewKey by remember { mutableStateOf<String?>(null) }
    var dismissedReviewKey by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(controller.activeEvmNetwork?.id, controller.tradeAssets) {
        val available = controller.tradeAssets
        if (available.isEmpty()) return@LaunchedEffect
        val nextFrom = available.firstOrNull { it.key == fromAsset.key }
            ?: available.firstOrNull(TradeAsset::isWOct)
            ?: available.first()
        val outputs = tradeOutputAssets(nextFrom, available)
        val nextTo = outputs.firstOrNull { it.key == toAsset.key }
            ?: outputs.firstOrNull()
            ?: available.firstOrNull { it.key != nextFrom.key }
            ?: nextFrom
        if (nextFrom.key != fromAsset.key || nextTo.key != toAsset.key) {
            fromAsset = nextFrom
            toAsset = nextTo
            amount = ""
            localError = null
            controller.clearTradeQuote()
        } else {
            fromAsset = nextFrom
            toAsset = nextTo
        }
    }

    LaunchedEffect(fromAsset, toAsset, amount, slippageBps) {
        val clean = amount.replace(",", ".").trim()
        if (controller.clearTradeDialogForNewQuote()) {
            showReview = false
            openedReviewKey = null
            dismissedReviewKey = null
        }
        if (clean.isBlank()) {
            controller.clearTradeQuote()
            return@LaunchedEffect
        }
        delay(450)
        controller.requestTradeQuote(fromAsset, toAsset, clean, slippageBps)
    }

    LaunchedEffect(
        controller.tradeQuote,
        controller.tradeQuoteLoading,
        controller.tradeQuoteError,
        fromAsset,
        toAsset,
        amount,
        slippageBps,
        controller.tradeBusy,
        controller.tradeDialogVisible,
    ) {
        val quote = controller.tradeQuote ?: return@LaunchedEffect
        val clean = amount.replace(",", ".").trim()
        if (clean.isBlank() || controller.tradeQuoteLoading || controller.tradeBusy || controller.tradeDialogVisible) return@LaunchedEffect
        val key = tradeReviewKey(fromAsset, toAsset, clean, slippageBps)
        if (dismissedReviewKey == key || openedReviewKey == key) return@LaunchedEffect
        openedReviewKey = key
        controller.prepareTradeDialog(fromAsset, toAsset, clean, quote)
        showReview = true
    }

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Trade", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
                Text("wOCT ${controller.tradeLastPrice}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            TradeHeaderNetworkPill(controller)
        }
        Spacer(Modifier.height(Space.x7))
        TradeMiniChart(controller)
        Spacer(Modifier.height(Space.x7))
        TradeSwapCard(
            controller = controller,
            fromAsset = fromAsset,
            toAsset = toAsset,
            amount = amount,
            slippageBps = slippageBps,
            localError = localError,
            onFromAssetChange = { selected ->
                fromAsset = selected
                toAsset = normalizeTradeToAsset(selected, toAsset, controller.tradeAssets)
                amount = ""
                localError = null
                controller.clearTradeQuote()
            },
            onToAssetChange = {
                toAsset = it
                localError = null
                controller.clearTradeQuote()
            },
            onAmountChange = {
                amount = it
                localError = null
                controller.clearTradeQuote()
            },
            onMax = {
                amount = controller.tradeMaxAmount(fromAsset)
                localError = null
            },
            onSwapPair = {
                val previousFrom = fromAsset
                fromAsset = toAsset
                toAsset = previousFrom
                amount = ""
                localError = null
                controller.clearTradeQuote()
            },
            onSlippageChange = {
                slippageBps = it
                localError = null
                controller.clearTradeQuote()
            },
            onAddAsset = controller::openEvmAddTokenFromTrade,
            onSubmit = {
                val validation = when {
                    controller.tradeLoading -> "Wait until balances finish syncing."
                    controller.tradeQuoteLoading -> "Wait until the quote finishes."
                    amount.isBlank() -> "Amount is required."
                    amount.replace(",", ".").toDoubleOrNull() == null -> "Enter a valid amount."
                    controller.tradeQuote == null -> controller.tradeQuoteError ?: "Wait for a valid Uniswap v4 quote."
                    else -> null
                }
                if (validation != null) localError = validation
                else {
                    controller.prepareTradeDialog(fromAsset, toAsset, amount, controller.tradeQuote)
                    showReview = true
                }
            },
        )
    }
    val shouldShowTradeDialog = showReview || controller.tradeDialogVisible
    val reviewQuote = controller.tradeQuote
    if (shouldShowTradeDialog && reviewQuote != null) {
        val dialogFromAsset = controller.tradeDialogFromAsset
        val dialogToAsset = controller.tradeDialogToAsset
        val dialogAmount = controller.tradeDialogAmount.ifBlank { amount }
        val dialogStarted = controller.tradeDialogStarted ||
            controller.tradeBusy ||
            controller.tradeStep != TradeStep.Idle
        TradeReviewSheet(
            controller = controller,
            fromAsset = dialogFromAsset,
            toAsset = dialogToAsset,
            amount = dialogAmount,
            slippageBps = slippageBps,
            quote = reviewQuote,
            started = dialogStarted,
            onDismiss = {
                if (!controller.tradeBusy) {
                    dismissedReviewKey = tradeReviewKey(
                        dialogFromAsset,
                        dialogToAsset,
                        dialogAmount.replace(",", ".").trim(),
                        slippageBps,
                    )
                    showReview = false
                    controller.dismissTradeDialog()
                }
            },
            onConfirm = { evmGasPriceGwei ->
                controller.prepareTradeDialog(dialogFromAsset, dialogToAsset, dialogAmount, reviewQuote, started = true)
                controller.submitTrade(
                    fromAsset = dialogFromAsset,
                    toAsset = dialogToAsset,
                    amountText = dialogAmount,
                    slippageBps = slippageBps,
                    evmGasPriceGwei = evmGasPriceGwei,
                )
            },
        )
    }
}

@Composable
private fun TradeHeaderNetworkPill(controller: PoctraController) {
    val network = controller.activeEvmNetwork
    Row(
        modifier = Modifier
            .width(154.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceMuted.copy(alpha = 0.72f))
            .clickable(onClick = controller::openEvmNetworkSettingsFromTrade)
            .padding(horizontal = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.Evm, modifier = Modifier.size(16.dp), color = TextTertiary)
        Spacer(Modifier.width(Space.x3))
        Column(Modifier.weight(1f)) {
            Text(
                network?.name ?: "EVM Network",
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "Change",
                color = SurfaceRaised,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun TradeMiniChart(controller: PoctraController) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(194.dp),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceMuted.copy(alpha = 0.54f)),
    ) {
        Column(
            Modifier.padding(
                start = Space.x6,
                top = Space.x6,
                end = Space.x6,
                bottom = Space.x5,
            ),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TradeTimeframeTabs(
                    selected = controller.tradeChartTimeframe,
                    loading = controller.tradeChartLoading,
                    onSelect = controller::refreshTradeChart,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Space.x4))
                SmallRefreshButton(
                    loading = controller.tradeChartLoading,
                    enabled = !controller.tradeChartRefreshCoolingDown,
                    onClick = { controller.forceRefreshTradeChart() },
                )
            }
            Spacer(Modifier.height(Space.x5))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(SurfaceBase)
                    .padding(start = Space.x3, top = Space.x3, end = 0.dp, bottom = Space.x3),
                contentAlignment = Alignment.Center,
            ) {
                if (controller.tradeCandles.isNotEmpty()) {
                    CandleChart(
                        candles = controller.tradeCandles,
                        modifier = Modifier.fillMaxSize(),
                    )
                    controller.tradeChartError?.let { message ->
                        Text(
                            message,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(Warning.copy(alpha = 0.18f))
                                .padding(horizontal = Space.x3, vertical = Space.x1),
                            color = TextTertiary,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                } else {
                    Text(
                        controller.tradeChartError ?: "Loading candles...",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun SmallRefreshButton(loading: Boolean, onClick: () -> Unit, enabled: Boolean = true) {
    val interactive = enabled && !loading
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (interactive) SurfaceMuted else SurfaceMuted.copy(alpha = 0.55f))
            .clickable(enabled = interactive, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = SurfaceRaised, strokeWidth = 2.dp)
        } else {
            GlyphIcon(
                Glyph.Refresh,
                modifier = Modifier.size(15.dp),
                color = if (interactive) TextTertiary else TextSecondary,
            )
        }
    }
}

@Composable
private fun TradeChartMeta(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(SurfaceMuted)
            .padding(horizontal = Space.x4, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
        )
        Text(
            value,
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TradeTimeframeTabs(
    selected: TradeChartTimeframe,
    loading: Boolean,
    onSelect: (TradeChartTimeframe) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
        TradeChartTimeframe.entries.forEach { timeframe ->
            Text(
                timeframe.label,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(if (timeframe == selected) SurfaceRaised else SurfaceMuted)
                    .clickable(enabled = !loading) { onSelect(timeframe) }
                    .padding(horizontal = Space.x4, vertical = Space.x2),
                color = if (timeframe == selected) PrimaryContent else TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun CandleChart(candles: List<TradeCandle>, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val visible = candles.takeLast(28)
        if (visible.isEmpty()) return@Canvas
        val minLow = visible.minOf { it.low }
        val maxHigh = visible.maxOf { it.high }
        val range = (maxHigh - minLow).takeIf { it > 0.0 } ?: 1.0
        val axisWidth = 50.dp.toPx()
        val plotWidth = (size.width - axisWidth).coerceAtLeast(size.width * 0.68f)
        fun y(price: Double): Float {
            val normalized = ((maxHigh - price) / range).toFloat()
            return normalized * size.height
        }
        val step = plotWidth / visible.size
        val candleWidth = (step * 0.48f).coerceAtLeast(2.dp.toPx())
        val current = visible.last().close
        val currentY = y(current).coerceIn(0f, size.height)

        drawLine(
            color = BorderLine.copy(alpha = 0.65f),
            start = Offset(size.width - axisWidth, 0f),
            end = Offset(size.width - axisWidth, size.height),
            strokeWidth = 1.dp.toPx(),
        )
        drawLine(
            color = SurfaceRaised.copy(alpha = 0.28f),
            start = Offset(0f, currentY),
            end = Offset(plotWidth, currentY),
            strokeWidth = 1.dp.toPx(),
        )

        visible.forEachIndexed { index, candle ->
            val centerX = step * index + step / 2f
            val openY = y(candle.open)
            val closeY = y(candle.close)
            val highY = y(candle.high)
            val lowY = y(candle.low)
            val up = candle.close >= candle.open
            val color = if (up) SurfaceRaised else ErrorRed
            drawLine(
                color = color.copy(alpha = 0.9f),
                start = Offset(centerX, highY),
                end = Offset(centerX, lowY),
                strokeWidth = 1.4.dp.toPx(),
            )
            val top = minOf(openY, closeY)
            val height = kotlin.math.abs(openY - closeY).coerceAtLeast(2.dp.toPx())
            drawRect(
                color = color,
                topLeft = Offset(centerX - candleWidth / 2f, top),
                size = Size(candleWidth, height),
            )
        }

        val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 9.sp.toPx()
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }
        val labelHeight = 17.dp.toPx()
        val labelPadding = 3.dp.toPx()
        val labelRadius = androidx.compose.ui.geometry.CornerRadius(5.dp.toPx(), 5.dp.toPx())

        fun drawPriceLabel(price: Double, priceY: Float, background: Color, foreground: Color) {
            val label = formatChartAxisPrice(price)
            val labelWidth = (labelPaint.measureText(label) + labelPadding * 2)
                .coerceAtMost(axisWidth - 1.dp.toPx())
            val labelX = size.width - labelWidth
            val top = (priceY - labelHeight / 2f).coerceIn(0f, size.height - labelHeight)
            drawRoundRect(
                color = background,
                topLeft = Offset(labelX, top),
                size = Size(labelWidth, labelHeight),
                cornerRadius = labelRadius,
            )
            labelPaint.color = foreground.toArgb()
            val textY = top + labelHeight / 2f - (labelPaint.ascent() + labelPaint.descent()) / 2f
            drawContext.canvas.nativeCanvas.drawText(label, labelX + labelPadding, textY, labelPaint)
        }

        drawPriceLabel(maxHigh, 0f, SurfaceMuted.copy(alpha = 0.82f), TextSecondary)
        drawPriceLabel(minLow, size.height, SurfaceMuted.copy(alpha = 0.82f), TextSecondary)
        drawPriceLabel(current, currentY, SurfaceRaised.copy(alpha = 0.22f), TextTertiary)
    }
}

private fun formatChartAxisPrice(value: Double): String = when {
    abs(value) >= 100.0 -> "$${"%.2f".format(value)}"
    abs(value) >= 1.0 -> "$${"%.3f".format(value)}"
    abs(value) >= 0.01 -> "$${"%.4f".format(value)}"
    else -> "$${"%.6f".format(value)}"
}

@Composable
private fun TradeSwapCard(
    controller: PoctraController,
    fromAsset: TradeAsset,
    toAsset: TradeAsset,
    amount: String,
    slippageBps: Int,
    localError: String?,
    onFromAssetChange: (TradeAsset) -> Unit,
    onToAssetChange: (TradeAsset) -> Unit,
    onAmountChange: (String) -> Unit,
    onMax: () -> Unit,
    onSwapPair: () -> Unit,
    onSlippageChange: (Int) -> Unit,
    onAddAsset: () -> Unit,
    onSubmit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceMuted.copy(alpha = 0.54f)),
    ) {
        Column(Modifier.padding(Space.x6)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Swap", color = TextTertiary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "Balances ${if (controller.tradeLoading) "syncing" else "ready"}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                SmallRefreshButton(
                    loading = controller.tradeLoading,
                    onClick = controller::refreshTrade,
                )
            }
            Spacer(Modifier.height(Space.x5))
            TradeAssetInputRow(
                label = "From",
                asset = fromAsset,
                amount = amount,
                balance = controller.tradeBalances.formatted(fromAsset),
                selectableAssets = controller.tradeAssets,
                onAssetChange = onFromAssetChange,
                onAddAsset = onAddAsset,
                onAmountChange = onAmountChange,
                onMax = onMax,
                enabled = !controller.tradeBusy,
                isError = localError != null,
            )
            Spacer(Modifier.height(Space.x4))
            TradeSwapDirectionButton(onClick = onSwapPair, enabled = !controller.tradeBusy)
            Spacer(Modifier.height(Space.x4))
            TradeAssetOutputRow(
                label = "To",
                asset = toAsset,
                amount = controller.tradeQuote?.amountOut ?: "",
                balance = controller.tradeBalances.formatted(toAsset),
                selectableAssets = tradeOutputAssets(fromAsset, controller.tradeAssets),
                onAssetChange = onToAssetChange,
                onAddAsset = onAddAsset,
                enabled = !controller.tradeBusy,
            )
            if (localError != null) {
                Spacer(Modifier.height(Space.x4))
                StateBanner(localError, isError = true, compact = true)
            }
            Spacer(Modifier.height(Space.x5))
            TradeSlippageRow(slippageBps = slippageBps, onChange = onSlippageChange, enabled = !controller.tradeBusy)
            if (controller.tradeQuote == null || controller.tradeQuoteLoading || controller.tradeQuoteError != null) {
                Spacer(Modifier.height(Space.x5))
                TradeQuotePanel(
                    controller = controller,
                    slippageBps = slippageBps,
                    onRetry = { controller.requestTradeQuote(fromAsset, toAsset, amount, slippageBps) },
                )
            }
            Spacer(Modifier.height(Space.x6))
            PoctraButton(
                text = "Swap",
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !controller.tradeBusy && !controller.tradeQuoteLoading,
                loading = controller.tradeBusy,
                leadingGlyph = Glyph.Swap,
            )
        }
    }
}

@Composable
private fun TradeReviewSheet(
    controller: PoctraController,
    fromAsset: TradeAsset,
    toAsset: TradeAsset,
    amount: String,
    slippageBps: Int,
    quote: TradeQuote,
    started: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String?) -> Unit,
) {
    var selectedGas by remember(quote) { mutableStateOf(BridgeFeePreset.Medium) }
    var customGasGwei by remember(quote) { mutableStateOf(weiToGweiLabel(quote.gasPriceWei)) }
    val selectedGasGwei = tradeSelectedGasGwei(selectedGas, quote, customGasGwei)
    val canConfirm = !controller.tradeBusy &&
        (selectedGas != BridgeFeePreset.Custom || customGasGwei.gweiToWeiOrNull() != null)
    var sheetVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sheetVisible = true }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (sheetVisible) 0.52f else 0f,
        animationSpec = tween(ScrimMotionMillis),
        label = "tradeReviewScrim",
    )
    Dialog(
        onDismissRequest = {
            if (!started && !controller.tradeBusy) onDismiss()
        },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha))
                .clickable(enabled = !started && !controller.tradeBusy, onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = sheetVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(SheetEnterMillis, easing = FastOutSlowInEasing),
                ) + fadeIn(animationSpec = tween(ScrimMotionMillis)),
                exit = slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(SheetExitMillis, easing = FastOutSlowInEasing),
                ) + fadeOut(animationSpec = tween(SheetExitMillis)),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * if (started) 0.92f else 0.88f)
                        .clickable(onClick = {})
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(topStart = Radius.md, topEnd = Radius.md),
                    colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
                ) {
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = Space.x7, vertical = Space.x6),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(42.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(BorderLine)
                                .align(Alignment.CenterHorizontally),
                        )
                        Spacer(Modifier.height(Space.x6))
                        if (started) {
                            TradeFlowProgressContent(
                                controller = controller,
                                fromAsset = fromAsset,
                                toAsset = toAsset,
                                amount = amount,
                                quote = quote,
                                onDismiss = onDismiss,
                            )
                        } else {
                            TradeReviewContent(
                                controller = controller,
                                fromAsset = fromAsset,
                                toAsset = toAsset,
                                amount = amount,
                                slippageBps = slippageBps,
                                quote = quote,
                                selectedGas = selectedGas,
                                customGasGwei = customGasGwei,
                                onSelectedGas = { selectedGas = it },
                                onCustomGasChange = {
                                    customGasGwei = it.filter { char -> char.isDigit() || char == '.' || char == ',' }
                                },
                            )
                            Spacer(Modifier.height(Space.x6))
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
                                PoctraButton(
                                    text = "Cancel",
                                    onClick = onDismiss,
                                    modifier = Modifier.weight(1f),
                                    variant = ButtonVariant.Ghost,
                                    fullHeight = true,
                                )
                                PoctraButton(
                                    text = "Confirm",
                                    onClick = { onConfirm(selectedGasGwei) },
                                    modifier = Modifier.weight(1f),
                                    enabled = canConfirm,
                                    loading = controller.tradeBusy,
                                    leadingGlyph = Glyph.Check,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TradeReviewContent(
    controller: PoctraController,
    fromAsset: TradeAsset,
    toAsset: TradeAsset,
    amount: String,
    slippageBps: Int,
    quote: TradeQuote,
    selectedGas: BridgeFeePreset,
    customGasGwei: String,
    onSelectedGas: (BridgeFeePreset) -> Unit,
    onCustomGasChange: (String) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(SurfaceRaised.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Trade, modifier = Modifier.size(21.dp), color = SurfaceRaised)
        }
        Spacer(Modifier.width(Space.x5))
        Column(Modifier.weight(1f)) {
            Text("Review Swap", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
            Text("${fromAsset.symbol} -> ${toAsset.symbol}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(Space.x5))
    TradeReviewSummaryCard(
        controller = controller,
        fromAsset = fromAsset,
        toAsset = toAsset,
        amount = amount,
        slippageBps = slippageBps,
        quote = quote,
    )
    quote.riskWarning?.let { warning ->
        Spacer(Modifier.height(Space.x5))
        TradeInfoHint(
            text = warning,
            isError = quote.riskLevel.ordinal >= TradeQuoteRisk.Warning.ordinal,
        )
    }
    Spacer(Modifier.height(Space.x5))
    TradeReviewGasPanel(
        quote = quote,
        selectedGas = selectedGas,
        customGasGwei = customGasGwei,
        onSelectedGas = onSelectedGas,
        onCustomGasChange = onCustomGasChange,
    )
    Spacer(Modifier.height(Space.x5))
    StateBanner(
        text = "Quotes can move before confirmation. The swap uses the minimum received above as protection.",
        isError = false,
        compact = true,
    )
}

@Composable
private fun TradeReviewSummaryCard(
    controller: PoctraController,
    fromAsset: TradeAsset,
    toAsset: TradeAsset,
    amount: String,
    slippageBps: Int,
    quote: TradeQuote,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
    ) {
        Column(Modifier.padding(horizontal = Space.x5, vertical = Space.x5)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
                TradeReviewAmountCard(
                    label = "Pay",
                    value = "${amount.replace(",", ".").trim()} ${fromAsset.symbol}",
                    subtitle = fromAsset.displayName,
                    modifier = Modifier.weight(1f),
                )
                TradeReviewAmountCard(
                    label = "Receive",
                    value = "${quote.amountOut} ${toAsset.symbol}",
                    subtitle = "Est. ${toAsset.displayName}",
                    modifier = Modifier.weight(1f),
                    highlighted = true,
                )
            }
            Spacer(Modifier.height(Space.x4))
            Spacer(Modifier.height(Space.x3))
            TradeReviewKeyValue("Minimum", "${quote.minimumOut} ${toAsset.symbol}")
            TradeReviewKeyValue("Impact", quote.priceImpactLabel)
            TradeReviewKeyValue("Liquidity", quote.poolLiquidityLabel)
            TradeReviewKeyValue("Slippage", tradeReviewSlippageLabel(slippageBps))
            TradeReviewKeyValue("MEV", quote.mevRouteLabel)
            TradeReviewKeyValue("Network", tradeReviewNetworkLabel(controller))
            TradeReviewKeyValue("Route", quote.routeLabel)
            TradeReviewKeyValue("Account", controller.evmAccount?.address?.let(::shortAddress) ?: "Current wallet")
            TradeReviewKeyValue("Approval", compactTradeApprovalLabel(fromAsset))
        }
    }
}

@Composable
private fun TradeReviewAmountCard(
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
) {
    val accent = if (highlighted) SurfaceRaised else InfoBlue
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(accent.copy(alpha = if (highlighted) 0.14f else 0.10f))
            .padding(horizontal = Space.x4, vertical = Space.x3),
    ) {
        Text(
            label,
            color = accent,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            color = TextTertiary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            subtitle,
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TradeReviewKeyValue(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            modifier = Modifier.weight(0.34f),
            maxLines = 1,
        )
        Text(
            value.ifBlank { "-" },
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(0.66f),
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun compactTradeApprovalLabel(fromAsset: TradeAsset): String =
    if (fromAsset.isNative) "Not required" else "ERC20 + Permit2 if needed"

@Composable
private fun TradeReviewGasPanel(
    quote: TradeQuote,
    selectedGas: BridgeFeePreset,
    customGasGwei: String,
    onSelectedGas: (BridgeFeePreset) -> Unit,
    onCustomGasChange: (String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
    ) {
        Column(Modifier.padding(Space.x5)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Network Fee", color = TextTertiary, fontWeight = FontWeight.SemiBold)
                    Text(
                        "${quote.gasEstimate} gas estimated for route execution",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    )
                }
                GlyphIcon(Glyph.Info, modifier = Modifier.size(16.dp), color = SurfaceRaised)
            }
            Spacer(Modifier.height(Space.x4))
            BridgeFeePresetRow(
                selected = selectedGas,
                labels = mapOf(
                    BridgeFeePreset.Low to quote.lowGasFeeEth,
                    BridgeFeePreset.Medium to quote.gasFeeEth,
                    BridgeFeePreset.Market to quote.fastGasFeeEth,
                    BridgeFeePreset.Custom to tradeCustomGasFeeLabel(quote, customGasGwei),
                ),
                titles = mapOf(
                    BridgeFeePreset.Medium to "Recommended",
                    BridgeFeePreset.Market to "Fast",
                ),
                suffix = "ETH",
                onSelect = onSelectedGas,
            )
            if (selectedGas == BridgeFeePreset.Custom) {
                Spacer(Modifier.height(Space.x4))
                PoctraTextField(
                    value = customGasGwei,
                    onValueChange = onCustomGasChange,
                    label = "Custom gas price (gwei)",
                    keyboardType = KeyboardType.Decimal,
                    singleLine = true,
                    supportingText = if (customGasGwei.gweiToWeiOrNull() == null) "Enter a valid gas price." else null,
                )
            }
        }
    }
}

@Composable
private fun TradeFlowProgressContent(
    controller: PoctraController,
    fromAsset: TradeAsset,
    toAsset: TradeAsset,
    amount: String,
    quote: TradeQuote,
    onDismiss: () -> Unit,
) {
    val failed = controller.tradeStep == TradeStep.Failed
    val completed = controller.tradeStep == TradeStep.Completed
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background((if (failed) ErrorRed else SurfaceRaised).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                controller.tradeBusy -> CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SurfaceRaised, strokeWidth = 2.dp)
                failed -> GlyphIcon(Glyph.Info, modifier = Modifier.size(21.dp), color = ErrorRed)
                else -> GlyphIcon(Glyph.Check, modifier = Modifier.size(21.dp), color = SurfaceRaised)
            }
        }
        Spacer(Modifier.width(Space.x5))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    failed -> "Swap Failed"
                    completed -> "Swap Complete"
                    else -> "Swap Processing"
                },
                color = TextTertiary,
                style = MaterialTheme.typography.titleLarge,
            )
            Text("${fromAsset.symbol} -> ${toAsset.symbol}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(Space.x5))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
    ) {
        Column(Modifier.padding(horizontal = Space.x6, vertical = Space.x3)) {
            DetailLine("Pay", "${amount.replace(",", ".").trim()} ${tradeAssetLabel(fromAsset)}")
            DetailSeparator()
            DetailLine("Receive estimate", "${controller.tradeDialogReceiveEstimate.ifBlank { quote.amountOut }} ${tradeAssetLabel(toAsset)}")
            DetailSeparator()
            DetailLine("Minimum received", "${controller.tradeDialogMinimumReceived.ifBlank { quote.minimumOut }} ${toAsset.symbol}")
            DetailSeparator()
            DetailLine("Route", quote.routeLabel)
            controller.tradeLastTxHash?.let { hash ->
                DetailSeparator()
                DetailLine("Current Tx", hash)
            }
        }
    }
    Spacer(Modifier.height(Space.x5))
    TradeProgressCard(
        fromAsset = fromAsset,
        currentStep = controller.tradeStep,
        progress = controller.tradeProgress,
        running = controller.tradeBusy,
    )
    if (failed) {
        Spacer(Modifier.height(Space.x5))
        StateBanner(controller.tradeProgress, isError = true, compact = true)
    }
    controller.tradeLastTxHash?.let { hash ->
        Spacer(Modifier.height(Space.x5))
        PoctraButton(
            text = "View Ethereum Tx",
            onClick = { controller.openDAppBrowser("Uniswap Swap", "https://etherscan.io/tx/$hash") },
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Ghost,
            compact = true,
            leadingGlyph = Glyph.Browser,
        )
    }
    if (controller.tradeCanSpeedUp) {
        Spacer(Modifier.height(Space.x5))
        StateBanner(
            text = "Pending too long? Speed Up replaces the current swap transaction with the same nonce and higher gas.",
            isError = false,
            compact = true,
        )
        Spacer(Modifier.height(Space.x4))
        PoctraButton(
            text = "Speed Up",
            onClick = controller::speedUpTradeEvmTransaction,
            modifier = Modifier.fillMaxWidth(),
            loading = controller.tradeSpeedUpBusy,
            enabled = !controller.tradeSpeedUpBusy,
            leadingGlyph = Glyph.Refresh,
            compact = true,
            variant = ButtonVariant.Ghost,
        )
    }
    Spacer(Modifier.height(Space.x5))
    PoctraButton(
        text = if (controller.tradeBusy) "Keep Open" else "Done",
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        enabled = !controller.tradeBusy,
        leadingGlyph = if (completed) Glyph.Check else Glyph.Trade,
        compact = true,
    )
}

@Composable
private fun TradeProgressCard(
    fromAsset: TradeAsset,
    currentStep: TradeStep,
    progress: String,
    running: Boolean,
) {
    val steps = tradeUiSteps(fromAsset)
    val activeIndex = tradeStepIndex(fromAsset, currentStep)
    val failed = currentStep == TradeStep.Failed
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
    ) {
        Column(Modifier.padding(Space.x5), verticalArrangement = Arrangement.spacedBy(Space.x4)) {
            steps.forEachIndexed { index, step ->
                val done = currentStep == TradeStep.Completed || index < activeIndex
                val active = index == activeIndex
                TradeProgressStepRow(
                    step = step,
                    done = done,
                    active = active,
                    failed = failed && active,
                    running = running,
                )
            }
            if (progress.isNotBlank()) {
                DetailSeparator()
                Text(progress, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun TradeProgressStepRow(
    step: TradeUiStep,
    done: Boolean,
    active: Boolean,
    failed: Boolean,
    running: Boolean,
) {
    val indicatorScale by animateFloatAsState(
        targetValue = if (active && running) 1.08f else 1f,
        animationSpec = tween(ScrimMotionMillis, easing = FastOutSlowInEasing),
        label = "tradeProgressIndicatorScale",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = indicatorScale
                    scaleY = indicatorScale
                }
                .clip(RoundedCornerShape(Radius.md))
                .background(
                    when {
                        failed -> ErrorRed.copy(alpha = 0.16f)
                        done -> SurfaceRaised
                        active -> SurfaceRaised.copy(alpha = 0.18f)
                        else -> SurfaceMuted
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                active && running -> CircularProgressIndicator(modifier = Modifier.size(14.dp), color = SurfaceRaised, strokeWidth = 2.dp)
                done -> GlyphIcon(Glyph.Check, modifier = Modifier.size(14.dp), color = PrimaryContent)
                failed -> GlyphIcon(Glyph.Info, modifier = Modifier.size(14.dp), color = ErrorRed)
                else -> Text(" ", color = TextSecondary)
            }
        }
        Spacer(Modifier.width(Space.x5))
        Column(Modifier.weight(1f)) {
            Text(step.title, color = TextTertiary, fontWeight = FontWeight.SemiBold)
            Text(step.subtitle, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private data class TradeUiStep(
    val step: TradeStep,
    val title: String,
    val subtitle: String,
)

private fun tradeUiSteps(fromAsset: TradeAsset): List<TradeUiStep> = buildList {
    if (!fromAsset.isNative) {
        add(TradeUiStep(TradeStep.TokenApproval, "Approve token", "Allow Permit2 to spend ${fromAsset.symbol}."))
        add(TradeUiStep(TradeStep.PermitApproval, "Approve route", "Allow Uniswap router to use this amount."))
    }
    add(TradeUiStep(TradeStep.Swapping, "Swap", "Execute Uniswap v4 route."))
}

private fun tradeStepIndex(fromAsset: TradeAsset, currentStep: TradeStep): Int {
    if (currentStep == TradeStep.Completed) return tradeUiSteps(fromAsset).lastIndex
    return tradeUiSteps(fromAsset).indexOfFirst { it.step == currentStep }
}

private fun tradeReviewNetworkLabel(controller: PoctraController): String {
    val network = controller.activeEvmNetwork ?: return "Ethereum Mainnet required"
    return "${network.name} (${network.symbol})"
}

private fun tradeReviewSlippageLabel(slippageBps: Int): String {
    val whole = slippageBps / 100
    val fraction = (slippageBps % 100).toString().padStart(2, '0')
    return "$whole.$fraction%"
}

private fun tradeReviewApprovalLabel(fromAsset: TradeAsset): String =
    if (fromAsset.isNative) "No ERC20 approval required"
    else "ERC20 approval and Permit2 may be submitted if allowance is low"

@Composable
private fun TradeAssetInputRow(
    label: String,
    asset: TradeAsset,
    amount: String,
    balance: String,
    selectableAssets: List<TradeAsset>,
    onAssetChange: (TradeAsset) -> Unit,
    onAddAsset: () -> Unit,
    onAmountChange: (String) -> Unit,
    onMax: () -> Unit,
    enabled: Boolean,
    isError: Boolean,
) {
    var amountFocused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(
        targetValue = if (amountFocused) 0.988f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "tradeAmountFocusScale",
    )
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = focusScale
                scaleY = focusScale
            },
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) ErrorRed.copy(alpha = 0.08f) else SurfaceBase,
        ),
    ) {
        Column(Modifier.padding(horizontal = Space.x5, vertical = Space.x4)) {
            TradeAssetPanelHeader(
                label = label,
                balanceLabel = "Balance $balance ${asset.symbol}",
                showMax = true,
                maxEnabled = enabled,
                onMax = onMax,
            )
            Spacer(Modifier.height(Space.x3))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.x4),
            ) {
                TradeAmountField(
                    value = amount,
                    onValueChange = { raw -> onAmountChange(raw.filter { it.isDigit() || it == '.' || it == ',' }) },
                    modifier = Modifier.weight(1f),
                    enabled = enabled,
                    onFocusChanged = { amountFocused = it },
                )
                TradeAssetSelector(
                    asset = asset,
                    selectableAssets = selectableAssets,
                    onSelect = onAssetChange,
                    onAddAsset = onAddAsset,
                    enabled = enabled,
                    modifier = Modifier.width(128.dp),
                )
            }
        }
    }
}

@Composable
private fun TradeAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(42.dp)
            .onFocusChanged { onFocusChanged(it.isFocused) },
        enabled = enabled,
        singleLine = true,
        textStyle = MaterialTheme.typography.titleMedium.copy(
            color = TextTertiary,
            fontWeight = FontWeight.SemiBold,
        ),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
        cursorBrush = SolidColor(SurfaceRaised),
        decorationBox = { innerTextField ->
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isBlank()) {
                    Text(
                        "0.0",
                        color = TextSecondary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun TradeAssetPanelHeader(
    label: String,
    balanceLabel: String,
    showMax: Boolean,
    maxEnabled: Boolean,
    onMax: (() -> Unit)?,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(0.42f),
            maxLines = 1,
        )
        Text(
            balanceLabel,
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 11.sp),
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (showMax && onMax != null) {
            Spacer(Modifier.width(Space.x3))
            Text(
                "Max",
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable(enabled = maxEnabled, onClick = onMax)
                    .padding(horizontal = Space.x2, vertical = Space.x1),
                color = if (maxEnabled) SurfaceRaised else TextSecondary,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 11.sp),
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TradeSwapDirectionButton(onClick: () -> Unit, enabled: Boolean) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(if (enabled) SurfaceMuted.copy(alpha = 0.80f) else SurfaceMuted.copy(alpha = 0.48f))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                Glyph.SwapVertical,
                modifier = Modifier.size(15.dp),
                color = if (enabled) TextTertiary else TextSecondary.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun TradeOutputAmount(value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.height(42.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = value.ifBlank { "0.0" },
            color = if (value.isBlank()) TextSecondary else TextTertiary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TradeAssetOutputRow(
    label: String,
    asset: TradeAsset,
    amount: String,
    balance: String,
    selectableAssets: List<TradeAsset>,
    onAssetChange: (TradeAsset) -> Unit,
    onAddAsset: () -> Unit,
    enabled: Boolean,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceMuted.copy(alpha = 0.48f)),
    ) {
        Column(Modifier.padding(horizontal = Space.x5, vertical = Space.x4)) {
            TradeAssetPanelHeader(
                label = label,
                balanceLabel = "Balance $balance ${asset.symbol}",
                showMax = false,
                maxEnabled = false,
                onMax = null,
            )
            Spacer(Modifier.height(Space.x3))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.x4),
            ) {
                TradeOutputAmount(
                    value = amount,
                    modifier = Modifier.weight(1f),
                )
                TradeAssetSelector(
                    asset = asset,
                    selectableAssets = selectableAssets,
                    onSelect = onAssetChange,
                    onAddAsset = onAddAsset,
                    enabled = enabled,
                    modifier = Modifier.width(128.dp),
                )
            }
        }
    }
}

@Composable
private fun TradeAssetSelector(
    asset: TradeAsset,
    selectableAssets: List<TradeAsset>,
    onSelect: (TradeAsset) -> Unit,
    onAddAsset: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(SurfaceMuted.copy(alpha = 0.72f))
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = Space.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TradeAssetIcon(asset)
            Spacer(Modifier.width(Space.x3))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    asset.symbol,
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    tradeAssetSecondaryLabel(asset),
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, lineHeight = 9.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            GlyphIcon(Glyph.ChevronDown, modifier = Modifier.size(12.dp), color = TextSecondary)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            selectableAssets.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                tradeAssetLabel(option),
                                color = TextTertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (!option.isNative) {
                                Text(
                                    shortAddress(option.address),
                                    color = TextSecondary,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                )
                            }
                        }
                    },
                    leadingIcon = { TradeAssetIcon(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        "Add asset",
                        color = SurfaceRaised,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                leadingIcon = { GlyphIcon(Glyph.Plus, modifier = Modifier.size(16.dp), color = SurfaceRaised) },
                onClick = {
                    expanded = false
                    onAddAsset()
                },
            )
        }
    }
}

@Composable
private fun TradeAssetIcon(asset: TradeAsset) {
    Box(
        modifier = Modifier
            .size(24.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (asset.isWOct) SurfaceRaised.copy(alpha = 0.18f) else SurfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        when {
            asset.isWOct -> {
            Image(
                painter = painterResource(com.poctra.wallet.R.drawable.octra_logo),
                contentDescription = "wOCT",
                modifier = Modifier.size(14.dp),
            )
            }
            asset.isNative -> {
            Image(
                painter = painterResource(com.poctra.wallet.R.drawable.ic_eth),
                contentDescription = asset.symbol,
                modifier = Modifier.size(15.dp),
            )
            }
            asset.isUsdt -> {
                Image(
                    painter = painterResource(com.poctra.wallet.R.drawable.ic_usdt),
                    contentDescription = asset.symbol,
                    modifier = Modifier.size(15.dp),
                )
            }
            else -> GlyphIcon(Glyph.Assets, modifier = Modifier.size(14.dp), color = SurfaceRaised)
        }
    }
}

private fun tradeAssetLabel(asset: TradeAsset): String = "${asset.symbol} (${asset.displayName})"

private fun tradeAssetSecondaryLabel(asset: TradeAsset): String = when {
    asset.isNative || asset.isWOct || asset.isUsdt -> asset.displayName
    else -> shortAddress(asset.address)
}

@Composable
private fun TradeSlippageRow(slippageBps: Int, onChange: (Int) -> Unit, enabled: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x4), verticalAlignment = Alignment.CenterVertically) {
        Text("Slippage", color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
        listOf(50 to "0.5%", 100 to "1%", 300 to "3%").forEach { (bps, label) ->
            Text(
                label,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(if (slippageBps == bps) SurfaceRaised else SurfaceMuted)
                    .clickable(enabled = enabled) { onChange(bps) }
                    .padding(horizontal = Space.x4, vertical = Space.x2),
                color = if (slippageBps == bps) PrimaryContent else TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TradeQuotePanel(
    controller: PoctraController,
    slippageBps: Int,
    onRetry: () -> Unit,
) {
    when {
        controller.tradeQuoteLoading -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.x2, vertical = Space.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), color = SurfaceRaised, strokeWidth = 2.dp)
                Spacer(Modifier.width(Space.x4))
                Text("Quoting Uniswap v4", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
        controller.tradeQuoteError != null -> {
            TradeInfoHint(
                text = controller.tradeQuoteError.orEmpty(),
                isError = true,
                onRetry = onRetry,
                retryEnabled = !controller.tradeQuoteLoading,
            )
        }
        controller.tradeQuote != null -> {
            val quote = controller.tradeQuote ?: return
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.md),
                colors = CardDefaults.cardColors(containerColor = SurfaceBase),
            ) {
                Column(Modifier.padding(horizontal = Space.x6, vertical = Space.x4)) {
                    DetailLine("Route", quote.routeLabel)
                    DetailSeparator()
                    DetailLine("Minimum received", quote.minimumOut)
                    DetailSeparator()
                    DetailLine("Price impact", quote.priceImpactLabel)
                    DetailSeparator()
                    DetailLine("Pool liquidity", quote.poolLiquidityLabel)
                    DetailSeparator()
                    DetailLine("Slippage", "${slippageBps.toBigDecimal().divide(BigDecimal(100)).stripTrailingZeros().toPlainString()}%")
                    DetailSeparator()
                    DetailLine("MEV route", quote.mevRouteLabel)
                    DetailSeparator()
                    DetailLine("Estimated gas", controller.tradeGasInfo())
                }
            }
            quote.riskWarning?.let { warning ->
                Spacer(Modifier.height(Space.x4))
                TradeInfoHint(
                    text = warning,
                    isError = quote.riskLevel.ordinal >= TradeQuoteRisk.Warning.ordinal,
                )
            }
        }
        else -> {
            TradeInfoHint("Enter amount to quote Uniswap v4 route.", isError = false)
        }
    }
}

@Composable
private fun TradeInfoHint(
    text: String,
    isError: Boolean,
    onRetry: (() -> Unit)? = null,
    retryEnabled: Boolean = true,
) {
    val color = if (isError) ErrorRed else InfoBlue
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = Space.x4, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(
            glyph = Glyph.Info,
            modifier = Modifier.size(13.dp),
            color = color,
        )
        Spacer(Modifier.width(Space.x4))
        Text(
            text,
            color = if (isError) ErrorRed else TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (onRetry != null) {
            Spacer(Modifier.width(Space.x4))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(if (retryEnabled) SurfaceMuted.copy(alpha = 0.82f) else SurfaceMuted.copy(alpha = 0.45f))
                    .clickable(enabled = retryEnabled, onClick = onRetry)
                    .semantics { contentDescription = "Retry quote" },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    Glyph.Refresh,
                    modifier = Modifier.size(13.dp),
                    color = if (retryEnabled) color else TextSecondary.copy(alpha = 0.45f),
                )
            }
        }
    }
}

private fun tradeOutputAssets(fromAsset: TradeAsset, availableAssets: List<TradeAsset>): List<TradeAsset> {
    return availableAssets.filter { it.key != fromAsset.key }
}

private fun normalizeTradeToAsset(
    fromAsset: TradeAsset,
    currentTo: TradeAsset,
    availableAssets: List<TradeAsset>,
): TradeAsset {
    val outputs = tradeOutputAssets(fromAsset, availableAssets)
    return outputs.firstOrNull { it.key == currentTo.key } ?: outputs.firstOrNull() ?: currentTo
}

private fun tradeReviewKey(fromAsset: TradeAsset, toAsset: TradeAsset, amount: String, slippageBps: Int): String =
    "${fromAsset.key}:${toAsset.key}:${amount.replace(",", ".").trim()}:$slippageBps"

@Composable
private fun ExploreDAppsContent(controller: PoctraController) {
    LaunchedEffect(Unit) { controller.refreshDAppCatalog() }
    val sites = controller.dAppCatalog
    Column(Modifier.fillMaxWidth()) {
        Text("Explore", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.x3))
        Text("Open dApps in the wallet browser", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Space.x7))
        CustomDAppLauncher(controller)
        Spacer(Modifier.height(Space.x8))
        Column(verticalArrangement = Arrangement.spacedBy(Space.x6)) {
            if (controller.dAppCatalogLoading && sites.isEmpty()) {
                ExploreCatalogStatus(message = "Loading dApps...", loading = true)
            } else if (controller.dAppCatalogError != null && sites.isEmpty()) {
                ExploreCatalogStatus(
                    message = controller.dAppCatalogError ?: "Unable to load dApps.",
                    onRetry = { controller.refreshDAppCatalog(force = true) },
                )
            } else if (sites.isEmpty()) {
                ExploreCatalogStatus(message = "No dApps are published yet.")
            } else {
                sites.forEach { site ->
                    DAppSiteRow(site = site, onClick = { controller.openDAppBrowser(site.title, site.dappLink) })
                }
            }
        }
    }
}

@Composable
private fun ExploreCatalogStatus(
    message: String,
    loading: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 1.8.dp,
            )
        } else {
            GlyphIcon(Glyph.Info, modifier = Modifier.size(14.dp), color = TextSecondary)
        }
        Spacer(Modifier.width(Space.x4))
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
        if (onRetry != null) {
            Spacer(Modifier.width(Space.x4))
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(SurfaceMuted)
                    .clickable(onClick = onRetry)
                    .semantics { contentDescription = "Retry dApp catalog" },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.Refresh, modifier = Modifier.size(13.dp), color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun DAppSiteRow(site: DAppCatalogEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceMuted.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(Space.x7),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DAppLogoView(
            logoSvg = site.logoSvg,
            contentDescription = "${site.title} logo",
            size = 42.dp,
        )
        Spacer(Modifier.width(Space.x6))
        Column(Modifier.weight(1f)) {
            Text(site.title, color = TextTertiary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Space.x2))
            Text(site.description, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Space.x1))
            Text(site.dappLink, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CustomDAppLauncher(controller: PoctraController) {
    var url by remember { mutableStateOf("") }
    val showOctProtocol = controller.isOctraCircleInput(url)
    val preview = controller.dAppInputPreview?.takeIf { it.input == url.trim() }
    LaunchedEffect(url, controller.activeRpcNetwork?.id, controller.activeIdentityProvider?.id) {
        if (url.isBlank()) {
            controller.clearDAppInputPreview()
        } else {
            delay(320)
            controller.previewDAppInput(url)
        }
    }
    DisposableEffect(Unit) {
        onDispose { controller.clearDAppInputPreview() }
    }
    val openUrl = {
        if (url.isBlank()) controller.showError("Enter a dApp URL.") else controller.openDAppBrowser("dApp", url)
    }
    Column(Modifier.fillMaxWidth()) {
        PoctraTextField(
            value = url,
            onValueChange = { next ->
                url = next.trim()
                if (next.isBlank()) controller.clearDAppInputPreview()
            },
            label = "dApp URL",
            placeholder = "circle address, octra identity or dApp URL",
            keyboardType = KeyboardType.Uri,
            leadingContent = if (showOctProtocol) {
                { OctProtocolBadge() }
            } else {
                null
            },
            trailingContent = {
                UrlGoButton(
                    onClick = openUrl,
                    enabled = url.isNotBlank(),
                )
            },
        )
        AnimatedVisibility(
            visible = preview != null,
            enter = fadeIn(animationSpec = tween(180)) + slideInVertically(initialOffsetY = { -it / 3 }),
            exit = fadeOut(animationSpec = tween(140)),
        ) {
            preview?.let {
                Column {
                    Spacer(Modifier.height(Space.x4))
                    DAppInputPreviewCard(
                        preview = it,
                        onClick = openUrl,
                    )
                }
            }
        }
    }

}

@Composable
private fun DAppInputPreviewCard(
    preview: DAppInputPreview,
    onClick: () -> Unit,
) {
    val isError = preview.status == DAppInputPreviewStatus.Error
    val isLoading = preview.status == DAppInputPreviewStatus.Loading
    val canOpen = !isError && !isLoading
    val color = when (preview.status) {
        DAppInputPreviewStatus.Circle -> SurfaceRaised
        DAppInputPreviewStatus.Explorer -> InfoBlue
        DAppInputPreviewStatus.Web -> InfoBlue
        DAppInputPreviewStatus.Loading -> TextSecondary
        DAppInputPreviewStatus.Error -> ErrorRed
    }
    val glyph = when (preview.status) {
        DAppInputPreviewStatus.Circle -> Glyph.Explore
        DAppInputPreviewStatus.Explorer -> Glyph.External
        DAppInputPreviewStatus.Web -> Glyph.Browser
        DAppInputPreviewStatus.Loading -> Glyph.Refresh
        DAppInputPreviewStatus.Error -> Glyph.Info
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(color.copy(alpha = if (isError) 0.10f else 0.12f))
            .clickable(enabled = canOpen, onClick = onClick)
            .padding(horizontal = Space.x5, vertical = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), color = color, strokeWidth = 2.dp)
            } else {
                GlyphIcon(glyph, modifier = Modifier.size(16.dp), color = color)
            }
        }
        Spacer(Modifier.width(Space.x4))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    preview.title,
                    color = TextTertiary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (preview.showOctProtocol) {
                    Spacer(Modifier.width(Space.x3))
                    OctProtocolBadge()
                }
            }
            Spacer(Modifier.height(Space.x1))
            Text(preview.subtitle, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(preview.detail, color = color, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (canOpen) {
            Spacer(Modifier.width(Space.x4))
            GlyphIcon(Glyph.Go, modifier = Modifier.size(15.dp), color = color)
        }
    }
}

@Composable
private fun OctProtocolBadge() {
    Box(
        modifier = Modifier
            .padding(start = Space.x2)
            .clip(RoundedCornerShape(Radius.sm))
            .background(SurfaceRaised.copy(alpha = 0.16f))
            .padding(horizontal = Space.x3, vertical = Space.x1),
        contentAlignment = Alignment.Center,
    ) {
        Text("oct://", color = SurfaceRaised, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BrowserToolbarButton(
    glyph: Glyph,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(SurfaceMuted.copy(alpha = 0.80f))
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, modifier = Modifier.size(17.dp), color = TextTertiary)
    }
}

@Composable
private fun BrowserDisplayUrl(displayUrl: String) {
    if (displayUrl.isBlank()) {
        Text(
            "Search or enter an address",
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        return
    }
    val isOctra = displayUrl.startsWith("oct://", ignoreCase = true)
    if (!isOctra) {
        Text(displayUrl, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OctProtocolLeftBadge()
        Spacer(Modifier.width(Space.x2))
        Text(
            displayUrl.drop(6).ifBlank { "circle" },
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun DAppBrowserNewTab(controller: PoctraController) {
    var destination by remember { mutableStateOf("") }
    val shortcuts = controller.dAppCatalog.take(4)
    LaunchedEffect(Unit) { controller.refreshDAppCatalog() }
    val openDestination = {
        val clean = destination.trim()
        if (clean.isBlank()) {
            controller.showError("Enter a Circle address, Octra Identity, or dApp URL.")
        } else {
            controller.openDAppBrowser(browserTabFallbackTitle(clean), clean)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.x7, vertical = Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(Space.x8))
        Image(
            painter = painterResource(com.poctra.wallet.R.drawable.poctra_logo),
            contentDescription = "poctra",
            modifier = Modifier.size(58.dp),
        )
        Spacer(Modifier.height(Space.x5))
        Text("poctra", color = TextTertiary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Open Circle apps and dApps", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(Space.x8))
        PoctraTextField(
            value = destination,
            onValueChange = { destination = it },
            label = "Search or enter address",
            placeholder = "foo.oct, oct:// or https://",
            keyboardType = KeyboardType.Uri,
            singleLine = true,
            leadingContent = {
                GlyphIcon(Glyph.Explore, modifier = Modifier.size(16.dp), color = TextSecondary)
            },
            trailingContent = {
                UrlGoButton(onClick = openDestination, enabled = destination.isNotBlank())
            },
        )
        Spacer(Modifier.height(Space.x5))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.md))
                .background(SurfaceMuted.copy(alpha = 0.62f))
                .padding(horizontal = Space.x5, vertical = Space.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            GlyphIcon(Glyph.Wifi, modifier = Modifier.size(14.dp), color = SurfaceRaised)
            Spacer(Modifier.width(Space.x3))
            Text(
                controller.activeRpcNetwork?.networkType?.replaceFirstChar { it.uppercase() } ?: "Network unavailable",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (shortcuts.isNotEmpty()) {
            Spacer(Modifier.height(Space.x8))
            Text(
                "Shortcuts",
                modifier = Modifier.fillMaxWidth(),
                color = TextTertiary,
                style = MaterialTheme.typography.titleSmall,
            )
            Spacer(Modifier.height(Space.x5))
            shortcuts.chunked(2).forEach { rowSites ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Space.x4),
                ) {
                    rowSites.forEach { site ->
                        DAppNewTabShortcut(
                            site = site,
                            onClick = { controller.openDAppBrowser(site.title, site.dappLink) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowSites.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(Space.x4))
            }
        }
    }
}

@Composable
private fun DAppNewTabShortcut(
    site: DAppCatalogEntry,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(64.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceMuted.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DAppLogoView(
            logoSvg = site.logoSvg,
            contentDescription = "${site.title} logo",
            size = 30.dp,
        )
        Spacer(Modifier.width(Space.x4))
        Text(
            site.title,
            modifier = Modifier.weight(1f),
            color = TextTertiary,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OctProtocolLeftBadge() {
    val shape = RoundedCornerShape(
        topStart = Radius.sm,
        topEnd = 0.dp,
        bottomEnd = 0.dp,
        bottomStart = Radius.sm,
    )
    Box(
        modifier = Modifier
            .clip(shape)
            .background(SurfaceRaised.copy(alpha = 0.16f))
            .padding(horizontal = Space.x3, vertical = 1.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("oct://", color = SurfaceRaised, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

private class DAppBrowserWebViewSession(
    val tabId: String,
    initialUrl: String,
) {
    val bridgeToken: String = UUID.randomUUID().toString()
    val originBinding = DAppOriginBinding(initialUrl)
    var requestedUrl: String = initialUrl
    var loadedRequestUrl: String = ""
    var rendererGeneration by mutableStateOf(0)
    var webView by mutableStateOf<WebView?>(null)
    var pageLoading by mutableStateOf(initialUrl.isNotBlank())
    var interactionLoading by mutableStateOf(false)
    var interactionNonce by mutableStateOf(0)
    var pageError by mutableStateOf<String?>(null)

    fun destroy() {
        val view = webView ?: return
        webView = null
        (view.parent as? ViewGroup)?.removeView(view)
        runCatching { view.removeJavascriptInterface("PoctraAndroid") }
        runCatching { view.stopLoading() }
        runCatching { view.clearFormData() }
        runCatching { view.clearHistory() }
        runCatching { view.clearSslPreferences() }
        runCatching { view.destroy() }
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled")
@Suppress("DEPRECATION")
private fun DAppBrowserScreen(controller: PoctraController) {
    val url = controller.selectedDAppUrl
    val isNewTab = url.isBlank()
    val activeTabId = controller.activeDAppTabId
    val displayUrl = controller.circleDisplayUrl()
    val browserSessions = remember(controller) { mutableMapOf<String, DAppBrowserWebViewSession>() }
    val activeSession = activeTabId
        ?.takeIf { !isNewTab }
        ?.let { tabId -> browserSessions.getOrPut(tabId) { DAppBrowserWebViewSession(tabId, url) } }
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }
    val fileChooserLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        fileChooserCallback?.onReceiveValue(uris.takeIf { it.isNotEmpty() }?.toTypedArray())
        fileChooserCallback = null
    }
    LaunchedEffect(activeTabId, activeSession?.interactionNonce) {
        val session = activeSession ?: return@LaunchedEffect
        val nonce = session.interactionNonce
        if (nonce <= 0) return@LaunchedEffect
        session.interactionLoading = true
        delay(900)
        if (session.interactionNonce == nonce) session.interactionLoading = false
    }

    LaunchedEffect(controller.dAppBrowserTabs.map { it.id }) {
        val openTabIds = controller.dAppBrowserTabs.mapTo(mutableSetOf()) { it.id }
        browserSessions.keys.filterNot { it in openTabIds }.forEach { closedTabId ->
            browserSessions.remove(closedTabId)?.destroy()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            fileChooserCallback?.onReceiveValue(null)
            fileChooserCallback = null
            browserSessions.values.forEach(DAppBrowserWebViewSession::destroy)
            browserSessions.clear()
        }
    }

    DisposableEffect(activeTabId) {
        activeSession?.webView?.onResume()
        onDispose { activeSession?.webView?.onPause() }
    }

    val activeWalletAddress = controller.wallet?.address
    val activeOctraNetworkId = controller.activeRpcNetwork?.id
    LaunchedEffect(activeTabId, activeSession?.webView, activeWalletAddress, activeOctraNetworkId) {
        val view = activeSession?.webView ?: return@LaunchedEffect
        if (activeWalletAddress.isNullOrBlank()) return@LaunchedEffect
        view.evaluateJavascript(
            """
            (function () {
              try {
                if (window.octra && typeof window.octra.request === 'function') {
                  window.octra.request({ method: 'octra_accounts' }).catch(function () {});
                }
                var evm = window.poctraEthereum || (window.ethereum && window.ethereum.isPoctra ? window.ethereum : null);
                if (evm && typeof evm.request === 'function') {
                  evm.request({ method: 'eth_accounts' }).catch(function () {});
                }
              } catch (_) {}
            })();
            """.trimIndent(),
            null,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceStrong)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.x5, vertical = Space.x3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrowserToolbarButton(Glyph.Back, "Back", controller::closeDAppBrowser)
                Spacer(Modifier.width(Space.x4))
                Column(Modifier.weight(1f)) {
                    Text(controller.selectedDAppTitle.ifBlank { "dApp" }, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    BrowserDisplayUrl(displayUrl)
                }
                Spacer(Modifier.width(Space.x3))
                BrowserToolbarButton(Glyph.Plus, "New tab", controller::openDAppBrowserNewTab)
                if (!isNewTab) {
                    Spacer(Modifier.width(Space.x2))
                    BrowserToolbarButton(
                        Glyph.Refresh,
                        "Refresh",
                        onClick = {
                            controller.invalidateActiveCircleAssetCache()
                            activeSession?.pageLoading = true
                            activeSession?.pageError = null
                            activeSession?.webView?.reload()
                        },
                    )
                }
            }
            DAppBrowserTabStrip(controller)
            AnimatedVisibility(
                visible = activeSession?.let { it.pageLoading || it.interactionLoading } == true,
                enter = fadeIn(animationSpec = tween(ScrimMotionMillis)),
                exit = fadeOut(animationSpec = tween(ScrimMotionMillis)),
            ) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(2.dp),
                    color = SurfaceRaised,
                    trackColor = Color.Transparent,
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(SurfaceBase),
        ) {
            if (isNewTab) {
                DAppBrowserNewTab(controller)
            } else if (activeSession != null) key(activeTabId, activeSession.rendererGeneration) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        (activeSession.webView ?: WebView(context).apply {
                            activeSession.webView = this
                            webChromeClient = object : WebChromeClient() {
                                override fun onShowFileChooser(
                                    webView: WebView?,
                                    filePathCallback: ValueCallback<Array<Uri>>?,
                                    fileChooserParams: FileChooserParams?,
                                ): Boolean {
                                    if (filePathCallback == null) return false
                                    fileChooserCallback?.onReceiveValue(null)
                                    fileChooserCallback = filePathCallback
                                    val accepted = fileChooserParams
                                        ?.acceptTypes
                                        ?.map(String::trim)
                                        ?.filter { it.contains('/') && !it.contains(';') }
                                        ?.distinct()
                                        ?.takeIf { it.isNotEmpty() }
                                        ?.toTypedArray()
                                        ?: arrayOf("*/*")
                                    return runCatching {
                                        fileChooserLauncher.launch(accepted)
                                        true
                                    }.getOrElse {
                                        fileChooserCallback?.onReceiveValue(null)
                                        fileChooserCallback = null
                                        false
                                    }
                                }

                                override fun onReceivedTitle(view: WebView?, title: String?) {
                                    super.onReceivedTitle(view, title)
                                    controller.updateDAppBrowserPageTitle(title, activeSession.tabId)
                                }

                                override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                                    if (BuildConfig.DEBUG) {
                                        Log.d(
                                            "PoctraDApp",
                                            "console ${consoleMessage.messageLevel()} ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()} ${consoleMessage.message()}",
                                        )
                                    }
                                    return true
                                }

                                override fun onCreateWindow(
                                    view: WebView,
                                    isDialog: Boolean,
                                    isUserGesture: Boolean,
                                    resultMsg: android.os.Message,
                                ): Boolean {
                                    if (!isUserGesture) return false
                                    val popupView = WebView(view.context)
                                    fun openPopupUrl(popupUrl: String) {
                                        val octraInput = controller.normalizeOctraBrowserInput(popupUrl)
                                        if (octraInput != null) {
                                            controller.openDAppBrowser("oct://", octraInput)
                                        } else {
                                            controller.openDAppBrowserTab(browserTabFallbackTitle(popupUrl), popupUrl)
                                        }
                                        runCatching { popupView.stopLoading() }
                                        runCatching { popupView.destroy() }
                                    }
                                    popupView.webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            popup: WebView,
                                            popupRequest: android.webkit.WebResourceRequest,
                                        ): Boolean {
                                            val popupUrl = popupRequest.url?.toString().orEmpty()
                                            if (popupUrl.isNotBlank()) openPopupUrl(popupUrl)
                                            return true
                                        }

                                        override fun onPageStarted(popup: WebView, popupUrl: String?, favicon: android.graphics.Bitmap?) {
                                            if (!popupUrl.isNullOrBlank()) openPopupUrl(popupUrl)
                                        }
                                    }
                                    val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                                    transport.webView = popupView
                                    resultMsg.sendToTarget()
                                    return true
                                }
                            }
                            webViewClient = createDAppWebViewClient(
                                controller = controller,
                                bridgeToken = activeSession.bridgeToken,
                                onNavigationStarted = activeSession.originBinding::onNavigationStarted,
                                onPageCommitted = activeSession.originBinding::onPageCommitted,
                                onLoadingChanged = { activeSession.pageLoading = it },
                                onError = { activeSession.pageError = it },
                                onRendererGone = { failedView, didCrash ->
                                    if (activeSession.webView === failedView) activeSession.webView = null
                                    runCatching { failedView.removeJavascriptInterface("PoctraAndroid") }
                                    (failedView.parent as? ViewGroup)?.removeView(failedView)
                                    runCatching { failedView.destroy() }
                                    activeSession.loadedRequestUrl = ""
                                    activeSession.rendererGeneration += 1
                                    activeSession.pageLoading = false
                                    activeSession.pageError = if (didCrash) {
                                        "The dApp renderer stopped unexpectedly. Reload to continue."
                                    } else {
                                        "The dApp renderer was reclaimed. Reload to continue."
                                    }
                                },
                            )
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.databaseEnabled = true
                            settings.userAgentString = walletBrowserUserAgent(
                                settings.userAgentString,
                                BuildConfig.VERSION_NAME,
                            )
                            settings.setGeolocationEnabled(false)
                            settings.saveFormData = false
                            settings.javaScriptCanOpenWindowsAutomatically = true
                            settings.setSupportMultipleWindows(true)
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.allowFileAccessFromFileURLs = false
                            settings.allowUniversalAccessFromFileURLs = false
                            settings.mediaPlaybackRequiresUserGesture = true
                            isSaveEnabled = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                settings.safeBrowsingEnabled = true
                            }
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            // Modern wallet selectors commonly use embedded auth/session
                            // frames. TLS, mixed content, and local file access stay strict.
                            CookieManager.getInstance().setAcceptCookie(true)
                            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                            if (BuildConfig.DEBUG) {
                                Log.d("PoctraDApp", "wallet user-agent=${settings.userAgentString}")
                            }
                            addJavascriptInterface(
                                PoctraProviderBridge(
                                    controller = controller,
                                    currentOrigin = { activeSession.originBinding.resolve(this.url) },
                                    expectedOrigin = {
                                        activeSession.originBinding.trustedOrigin().ifBlank { activeSession.requestedUrl }
                                    },
                                    expectedToken = { activeSession.bridgeToken },
                                    onPageInteraction = {
                                        post { activeSession.interactionNonce += 1 }
                                    },
                                    writeClipboard = { value ->
                                        runCatching {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Circle app", value))
                                            true
                                        }.getOrDefault(false)
                                    },
                                    evaluate = { script -> post { evaluateJavascript(script, null) } },
                                ),
                                "PoctraAndroid",
                            )
                            installDocumentStartProvider(this, activeSession.bridgeToken, url)
                            activeSession.requestedUrl = url
                            activeSession.loadedRequestUrl = url
                            loadUrl(url)
                        }).also { webView ->
                            (webView.parent as? ViewGroup)?.removeView(webView)
                            webView.onResume()
                        }
                    },
                    update = { webView ->
                        activeSession.webView = webView
                        activeSession.requestedUrl = url
                        webView.onResume()
                        if (activeSession.loadedRequestUrl != url) {
                            activeSession.loadedRequestUrl = url
                            activeSession.pageLoading = true
                            activeSession.pageError = null
                            webView.loadUrl(url)
                        }
                    },
                )
            }
            if (!isNewTab && activeSession?.pageLoading == true) {
                DAppBrowserLoadingOverlay()
            }
            if (!isNewTab) activeSession?.pageError?.let { message ->
                DAppBrowserErrorOverlay(
                    message = message,
                    onRetry = {
                        controller.invalidateActiveCircleAssetCache()
                        activeSession.pageLoading = true
                        activeSession.pageError = null
                        activeSession.webView?.reload()
                    },
                )
            }
        }
    }

    controller.circlePassphraseRequest?.let { request ->
        CirclePassphraseDialog(
            request = request,
            onDismiss = controller::dismissCirclePassphraseRequest,
            onSubmit = { passphrase ->
                controller.submitCirclePassphrase(passphrase)
                if (controller.circlePassphraseRequest == null) {
                    activeSession?.pageLoading = true
                    activeSession?.pageError = null
                    activeSession?.webView?.reload()
                }
            },
        )
    }
}

private fun createDAppWebViewClient(
    controller: PoctraController,
    bridgeToken: String,
    onNavigationStarted: (String?) -> Unit,
    onPageCommitted: (String?) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    onRendererGone: (WebView, Boolean) -> Unit,
): WebViewClient = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1 -> PoctraDAppWebViewClientApi27(
        controller,
        bridgeToken,
        onNavigationStarted,
        onPageCommitted,
        onLoadingChanged,
        onError,
        onRendererGone,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> PoctraDAppWebViewClientApi26(
        controller,
        bridgeToken,
        onNavigationStarted,
        onPageCommitted,
        onLoadingChanged,
        onError,
        onRendererGone,
    )
    else -> PoctraDAppWebViewClient(
        controller,
        bridgeToken,
        onNavigationStarted,
        onPageCommitted,
        onLoadingChanged,
        onError,
    )
}

private open class PoctraDAppWebViewClient(
    private val controller: PoctraController,
    private val bridgeToken: String,
    private val onNavigationStarted: (String?) -> Unit,
    private val onPageCommitted: (String?) -> Unit,
    private val onLoadingChanged: (Boolean) -> Unit,
    private val onError: (String?) -> Unit,
) : WebViewClient() {
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: android.webkit.WebResourceRequest,
    ): Boolean {
        val nextUrl = request.url?.toString().orEmpty()
        val octraInput = controller.normalizeOctraBrowserInput(nextUrl)
        if (octraInput != null) {
            controller.openDAppBrowser("oct://", octraInput)
            return true
        }
        if (request.isForMainFrame && controller.selectedCircleTarget != null && !controller.isCircleBrowserUrl(nextUrl)) {
            controller.openDAppBrowserTab(browserTabFallbackTitle(nextUrl), nextUrl)
            return true
        }
        if (request.isForMainFrame && runCatching { DAppUrlPolicy.normalizeBrowserUrl(nextUrl) }.isFailure) {
            onLoadingChanged(false)
            onError("This dApp URL is blocked by Phishing Guard.")
            return true
        }
        if (request.isForMainFrame) onNavigationStarted(nextUrl)
        return false
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: android.webkit.WebResourceRequest,
    ): WebResourceResponse? {
        // This native main-frame URL is available before document-start
        // JavaScript executes, including after an HTTP redirect.
        if (request.isForMainFrame) onNavigationStarted(request.url?.toString())
        return dAppWebResourceResponse(view.context, controller, request, bridgeToken)
    }

    override fun onPageStarted(view: WebView, pageUrl: String?, favicon: android.graphics.Bitmap?) {
        super.onPageStarted(view, pageUrl, favicon)
        if (BuildConfig.DEBUG) Log.d("PoctraDApp", "page started url=${pageUrl ?: view.url}")
        onNavigationStarted(pageUrl ?: view.url)
        schedulePoctraProviderInjection(view, bridgeToken, pageUrl ?: view.url)
        onLoadingChanged(true)
        onError(null)
    }

    override fun onPageCommitVisible(view: WebView, pageUrl: String?) {
        super.onPageCommitVisible(view, pageUrl)
        if (BuildConfig.DEBUG) Log.d("PoctraDApp", "page committed url=${pageUrl ?: view.url}")
        onPageCommitted(pageUrl ?: view.url)
        schedulePoctraProviderInjection(view, bridgeToken, pageUrl ?: view.url)
    }

    override fun onPageFinished(view: WebView, pageUrl: String?) {
        super.onPageFinished(view, pageUrl)
        if (BuildConfig.DEBUG) Log.d("PoctraDApp", "page finished url=${pageUrl ?: view.url}")
        onPageCommitted(pageUrl ?: view.url)
        schedulePoctraProviderInjection(view, bridgeToken, pageUrl ?: view.url)
        onLoadingChanged(isCircleLoadingBrowserUrl(pageUrl ?: view.url))
    }

    override fun onReceivedError(
        view: WebView,
        request: android.webkit.WebResourceRequest,
        error: android.webkit.WebResourceError,
    ) {
        super.onReceivedError(view, request, error)
        if (request.isForMainFrame) {
            if (BuildConfig.DEBUG) Log.d("PoctraDApp", "page error url=${request.url} message=${error.description}")
            onLoadingChanged(false)
            onError(error.description?.toString() ?: "Unable to load dApp.")
        }
    }

    override fun onReceivedSslError(
        view: WebView,
        handler: android.webkit.SslErrorHandler,
        error: android.net.http.SslError,
    ) {
        handler.cancel()
        val affectsVisiblePage = DAppSslPolicy.affectsVisiblePage(view.url, error.url)
        if (BuildConfig.DEBUG) {
            Log.d(
                "PoctraDApp",
                "ssl error page=${view.url} failed=${error.url} primary=${error.primaryError} visible=$affectsVisiblePage",
            )
        }
        if (affectsVisiblePage) {
            onLoadingChanged(false)
            onError("The dApp certificate could not be verified.")
        }
    }
}

@TargetApi(Build.VERSION_CODES.O)
private open class PoctraDAppWebViewClientApi26(
    controller: PoctraController,
    bridgeToken: String,
    onNavigationStarted: (String?) -> Unit,
    onPageCommitted: (String?) -> Unit,
    onLoadingChanged: (Boolean) -> Unit,
    onError: (String?) -> Unit,
    private val onRendererGone: (WebView, Boolean) -> Unit,
) : PoctraDAppWebViewClient(
    controller,
    bridgeToken,
    onNavigationStarted,
    onPageCommitted,
    onLoadingChanged,
    onError,
) {
    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        onRendererGone(view, detail.didCrash())
        return true
    }
}

@TargetApi(Build.VERSION_CODES.O_MR1)
private class PoctraDAppWebViewClientApi27(
    controller: PoctraController,
    bridgeToken: String,
    onNavigationStarted: (String?) -> Unit,
    onPageCommitted: (String?) -> Unit,
    private val onLoadingChanged: (Boolean) -> Unit,
    private val onError: (String?) -> Unit,
    onRendererGone: (WebView, Boolean) -> Unit,
) : PoctraDAppWebViewClientApi26(
    controller,
    bridgeToken,
    onNavigationStarted,
    onPageCommitted,
    onLoadingChanged,
    onError,
    onRendererGone,
) {
    override fun onSafeBrowsingHit(
        view: WebView,
        request: android.webkit.WebResourceRequest,
        threatType: Int,
        callback: SafeBrowsingResponse,
    ) {
        callback.backToSafety(true)
        onLoadingChanged(false)
        onError("Safe Browsing blocked this dApp.")
    }
}

@Composable
private fun DAppBrowserTabStrip(controller: PoctraController) {
    val tabs = controller.dAppBrowserTabs
    if (tabs.size <= 1) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = Space.x5, end = Space.x5, bottom = Space.x3),
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
    ) {
        tabs.forEach { tab ->
            DAppBrowserTabChip(
                tab = tab,
                active = tab.id == controller.activeDAppTabId,
                onClick = { controller.selectDAppBrowserTab(tab.id) },
                onClose = { controller.closeDAppBrowserTab(tab.id) },
            )
        }
    }
}

@Composable
private fun DAppBrowserTabChip(
    tab: DAppBrowserTab,
    active: Boolean,
    onClick: () -> Unit,
    onClose: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.sm)
    Row(
        modifier = Modifier
            .width(176.dp)
            .clip(shape)
            .background(if (active) SurfaceRaised.copy(alpha = 0.14f) else SurfaceMuted.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(start = Space.x3, end = Space.x2, top = Space.x2, bottom = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                tab.title.ifBlank { "dApp" },
                color = TextTertiary,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                browserTabSubtitle(tab),
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Close, modifier = Modifier.size(12.dp), color = TextSecondary)
        }
    }
}

private fun browserTabSubtitle(tab: DAppBrowserTab): String =
    (tab.circleTarget?.uri ?: tab.displayUrl ?: tab.url)
        .removePrefix("https://")
        .removePrefix("http://")

private fun browserTabFallbackTitle(url: String): String =
    url.removePrefix("https://")
        .removePrefix("http://")
        .substringBefore("/")
        .ifBlank { "dApp" }

@Composable
private fun CirclePassphraseDialog(
    request: CirclePassphraseRequest,
    onDismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    var passphrase by remember(request.circleId) { mutableStateOf("") }
    var sheetVisible by remember(request.circleId) { mutableStateOf(false) }
    LaunchedEffect(request.circleId) { sheetVisible = true }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (sheetVisible) 0.52f else 0f,
        animationSpec = tween(ScrimMotionMillis),
        label = "circlePassphraseScrim",
    )
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrimAlpha)),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = sheetVisible,
                enter = slideInVertically(
                    initialOffsetY = { it },
                    animationSpec = tween(SheetEnterMillis, easing = FastOutSlowInEasing),
                ) + fadeIn(animationSpec = tween(ScrimMotionMillis)),
                exit = slideOutVertically(
                    targetOffsetY = { it / 2 },
                    animationSpec = tween(SheetExitMillis, easing = FastOutSlowInEasing),
                ) + fadeOut(animationSpec = tween(SheetExitMillis)),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = {})
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(topStart = Radius.md, topEnd = Radius.md),
                    colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Space.x7, vertical = Space.x6),
                    ) {
                        Box(
                            modifier = Modifier
                                .width(38.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(Radius.pill))
                                .background(BorderLine)
                                .align(Alignment.CenterHorizontally),
                        )
                        Spacer(Modifier.height(Space.x5))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(RoundedCornerShape(Radius.md))
                                    .background(SurfaceRaised.copy(alpha = 0.18f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                GlyphIcon(Glyph.Key, modifier = Modifier.size(19.dp), color = SurfaceRaised)
                            }
                            Spacer(Modifier.width(Space.x5))
                            Column(Modifier.weight(1f)) {
                                Text("Sealed Circle", color = TextTertiary, style = MaterialTheme.typography.titleMedium)
                                Spacer(Modifier.height(Space.x2))
                                Text(request.label, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(Modifier.height(Space.x5))
                        Text(
                            "Enter the sealed read passphrase to decrypt this circle locally.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(Space.x5))
                        PoctraTextField(
                            value = passphrase,
                            onValueChange = { passphrase = it },
                            label = "Read Passphrase",
                            placeholder = "Sealed read key",
                            keyboardType = KeyboardType.Password,
                            password = true,
                        )
                        Spacer(Modifier.height(Space.x5))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
                            PoctraButton(
                                text = "Cancel",
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                variant = ButtonVariant.Ghost,
                                fullHeight = true,
                            )
                            PoctraButton(
                                text = "Open",
                                onClick = { onSubmit(passphrase) },
                                enabled = passphrase.isNotBlank(),
                                modifier = Modifier.weight(1f),
                                leadingGlyph = Glyph.Check,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun circleWebResourceResponse(
    controller: PoctraController,
    request: android.webkit.WebResourceRequest,
    bridgeToken: String,
): WebResourceResponse? {
    val requestUri = request.url ?: return null
    val host = requestUri.host.orEmpty()
    val normalizedHost = host.lowercase()
    val isDirectCircleUri = requestUri.scheme.equals("oct", ignoreCase = true) &&
        host.startsWith("oct") && host.length == 47
    val isCircleHost = normalizedHost == PoctraController.CircleBrowserHost ||
        normalizedHost.endsWith(".${PoctraController.CircleBrowserHost}")
    if (!isCircleHost && !isDirectCircleUri) {
        return if (
            controller.selectedCircleTarget != null &&
            !request.isForMainFrame &&
            !controller.activeCircleAllowsGatewayRequests()
        ) {
            emptyCircleResponse()
        } else {
            null
        }
    }

    if (isDirectCircleUri) {
        val assetPath = requestUri.path?.takeIf { it.isNotBlank() && it != "/" } ?: "/index.html"
        return runCatching {
            circleAssetResponse(controller.fetchCircleAssetForBrowser(host, assetPath), bridgeToken, request)
        }.getOrElse { exception ->
            circleResourceErrorResponse(exception, request.isForMainFrame)
        }
    }

    val segments = requestUri.pathSegments.orEmpty()
    if (segments.firstOrNull() == "loading") {
        return circleLoadingHtmlResponse()
    }
    if (segments.firstOrNull() == "error") {
        return circleHtmlResponse(
            title = "Cannot open oct://",
            message = controller.selectedCircleError ?: "Unable to open this oct:// resource.",
        )
    }

    if (segments.take(3) == listOf("api", "circle", "asset")) {
        val circleId = requestUri.getQueryParameter("circle_id")
            ?.trim()
            ?.takeIf { it.startsWith("oct") && it.length == 47 }
            ?: activeTargetCircleId(controller)
            ?: return circleJsonErrorResponse("Circle address is required.", 400, "Bad Request")
        val assetPath = requestUri.getQueryParameter("path")?.takeIf { it.isNotBlank() } ?: "/index.html"
        return runCatching {
            circleAssetJsonResponse(controller.fetchCircleAssetForBrowser(circleId, assetPath), request)
        }.getOrElse { exception ->
            if (isTransientCircleFailure(exception)) {
                circleJsonErrorResponse(exception.message ?: "Circle RPC is temporarily unavailable.", 503, "Service Unavailable")
            } else {
                circleJsonErrorResponse(exception.message ?: "Circle asset was not found.", 404, "Not Found")
            }
        }
    }

    val explicitCircleId = segments.takeIf { it.firstOrNull() == "oct" && it.size >= 2 }?.getOrNull(1)
    val activeTarget = controller.selectedCircleTarget
    val circleId = explicitCircleId ?: activeTarget?.circleId ?: return circleHtmlResponse(
        title = "Circle unavailable",
        message = "No active Circle target is available.",
    )
    val path = if (explicitCircleId != null) {
        val assetSegments = segments.drop(2)
        if (assetSegments.isEmpty()) "/index.html" else "/" + assetSegments.joinToString("/")
    } else {
        requestUri.path?.takeIf { it.isNotBlank() && it != "/" } ?: "/index.html"
    }

    return runCatching {
        circleAssetResponse(controller.fetchCircleAssetForBrowser(circleId, path), bridgeToken, request)
    }.getOrElse { exception ->
        circleResourceErrorResponse(exception, request.isForMainFrame)
    }
}

private fun circleResourceErrorResponse(exception: Throwable, isMainFrame: Boolean): WebResourceResponse {
    val message = exception.message ?: "Unable to load oct:// resource."
    if (isMainFrame) {
        return circleHtmlResponse(
            title = "Circle resource unavailable",
            message = message,
            statusCode = 200,
            reasonPhrase = "OK",
        )
    }
    val transient = isTransientCircleFailure(exception)
    val body = message.toByteArray(Charsets.UTF_8)
    return WebResourceResponse(
        "text/plain",
        "UTF-8",
        if (transient) 503 else 404,
        if (transient) "Service Unavailable" else "Not Found",
        buildMap {
            put("Cache-Control", "no-store")
            put("Content-Length", body.size.toString())
            put("X-Content-Type-Options", "nosniff")
            if (transient) put("Retry-After", "1")
        },
        ByteArrayInputStream(body),
    )
}

private fun dAppWebResourceResponse(
    context: Context,
    controller: PoctraController,
    request: android.webkit.WebResourceRequest,
    bridgeToken: String,
): WebResourceResponse? = when (
    routeBrowserResource(
        url = request.url.toString(),
        method = request.method,
        bridgeToken = bridgeToken,
        circleBrowserHost = PoctraController.CircleBrowserHost,
        hasActiveCircle = controller.selectedCircleTarget != null,
        isMainFrame = request.isForMainFrame,
        activeCircleAllowsGatewayRequests = controller.activeCircleAllowsGatewayRequests(),
    )
) {
    BrowserResourceHandler.OctraRfc -> rfcO1InpageProviderResponse(context, request, bridgeToken)
    BrowserResourceHandler.Circle -> circleWebResourceResponse(controller, request, bridgeToken)
    BrowserResourceHandler.CommonWeb -> null
}

private fun rfcO1InpageProviderResponse(
    context: Context,
    request: android.webkit.WebResourceRequest,
    bridgeToken: String,
): WebResourceResponse? {
    if (!request.method.equals("GET", ignoreCase = true)) return null
    if (!isRfcO1InpageProviderRequest(request.url.toString(), bridgeToken)) return null
    val rawProviderSource = context.assets.open("poctra-rfc-o1-inpage.js").bufferedReader().use { it.readText() }
    val providerSource = replaceProviderIconMetadata(rawProviderSource)
    val body = buildString {
        if (BuildConfig.DEBUG) append("window.__poctraRfcO1Debug = true;\n")
        append(providerSource)
    }.toByteArray(Charsets.UTF_8)
    if (BuildConfig.DEBUG) Log.d("PoctraDApp", "serving extension-compatible RFC-O-1 inpage provider")
    return WebResourceResponse(
        "application/javascript",
        "UTF-8",
        200,
        "OK",
        mapOf(
            "Cache-Control" to "no-store",
            "Content-Length" to body.size.toString(),
            "X-Content-Type-Options" to "nosniff",
        ),
        ByteArrayInputStream(body),
    )
}

private fun activeTargetCircleId(controller: PoctraController): String? =
    controller.selectedCircleTarget?.circleId

private fun circleAssetJsonResponse(
    asset: CircleAsset,
    request: android.webkit.WebResourceRequest,
): WebResourceResponse {
    val contentType = resolveCircleContentType(asset.contentType, asset.canonicalPath)
    val payload = JSONObject()
        .put("circle_id", asset.circleId)
        .put("canonical_path", asset.canonicalPath)
        .put("content_type", contentType.value)
        .put("encoding", "identity")
        .put("size_bytes", asset.sizeBytes.toString())
        .put("body_b64", Base64.encodeToString(asset.body, Base64.NO_WRAP))
        .toString()
        .toByteArray(Charsets.UTF_8)
    val body = if (request.method.equals("HEAD", ignoreCase = true)) ByteArray(0) else payload
    return WebResourceResponse(
        "application/json",
        "UTF-8",
        200,
        "OK",
        mapOf(
            "Cache-Control" to "no-store",
            "Content-Length" to payload.size.toString(),
            "Access-Control-Allow-Origin" to "*",
            "X-Content-Type-Options" to "nosniff",
        ),
        ByteArrayInputStream(body),
    )
}

private fun circleJsonErrorResponse(message: String, statusCode: Int, reason: String): WebResourceResponse {
    val body = JSONObject().put("error", message).toString().toByteArray(Charsets.UTF_8)
    return WebResourceResponse(
        "application/json",
        "UTF-8",
        statusCode,
        reason,
        mapOf("Cache-Control" to "no-store", "Content-Length" to body.size.toString()),
        ByteArrayInputStream(body),
    )
}

private fun circleAssetResponse(
    asset: CircleAsset,
    bridgeToken: String,
    request: android.webkit.WebResourceRequest,
): WebResourceResponse {
    val contentType = resolveCircleContentType(asset.contentType, asset.canonicalPath)
    if (request.isForMainFrame && !contentType.isHtml) {
        return circleStandaloneAssetResponse(asset, contentType)
    }
    val mimeType = contentType.mediaType
    val charset = contentType.charset
    val fullBody = if (contentType.isHtml) {
        injectCircleRuntime(asset, bridgeToken).toByteArray(Charsets.UTF_8)
    } else {
        asset.body
    }
    val range = request.requestHeaders["Range"]
        ?.takeIf { !mimeType.equals("text/html", ignoreCase = true) }
        ?.let { parseCircleByteRange(it, fullBody.size) }
    val responseBody = range?.let { fullBody.copyOfRange(it.first, it.last + 1) } ?: fullBody
    val isHead = request.method.equals("HEAD", ignoreCase = true)
    val statusCode = if (range != null) 206 else 200
    val headers = mutableMapOf(
        "Cache-Control" to "no-store",
        "X-Content-Type-Options" to "nosniff",
        "Access-Control-Allow-Origin" to "*",
        "Accept-Ranges" to "bytes",
        "Content-Length" to responseBody.size.toString(),
    )
    if (range != null) headers["Content-Range"] = "bytes ${range.first}-${range.last}/${fullBody.size}"
    return WebResourceResponse(
        mimeType,
        charset,
        statusCode,
        if (range != null) "Partial Content" else "OK",
        headers,
        ByteArrayInputStream(if (isHead) ByteArray(0) else responseBody),
    )
}

private fun circleStandaloneAssetResponse(
    asset: CircleAsset,
    contentType: com.poctra.wallet.dapp.circle.CircleContentType,
): WebResourceResponse {
    val fileName = asset.canonicalPath.substringAfterLast('/').ifBlank { asset.canonicalPath }
    if (!contentType.isPreviewable) {
        return circleHtmlResponse(
            title = "Preview unavailable",
            message = "$fileName (${contentType.value}, ${asset.sizeBytes} bytes) cannot be previewed safely. Use a Circle download link to save the original file.",
        )
    }
    val bodyContent = if (contentType.isImagePreview) {
        val data = Base64.encodeToString(asset.body, Base64.NO_WRAP)
        "<div class=\"image-preview\"><img src=\"data:${escapeHtml(contentType.value)};base64,$data\" alt=\"${escapeHtml(fileName)}\"></div>"
    } else {
        "<pre>${escapeHtml(asset.body.toString(Charsets.UTF_8))}</pre>"
    }
    val html = """
        <!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>${escapeHtml(fileName)}</title><style>
        :root{color-scheme:dark;background:#080b09;color:#f4f7f5;font-family:monospace}*{box-sizing:border-box}body{margin:0;min-height:100vh;background:#080b09}
        main{padding:20px;max-width:1120px;margin:auto}header{display:flex;gap:10px;justify-content:space-between;align-items:baseline;margin-bottom:18px}small{color:#8e9b93}
        pre{margin:0;white-space:pre-wrap;overflow-wrap:anywhere;line-height:1.55;font-size:13px}.image-preview{min-height:calc(100vh - 80px);display:grid;place-items:center}.image-preview img{max-width:100%;max-height:calc(100vh - 80px);object-fit:contain}
        </style></head><body><main><header><strong>${escapeHtml(fileName)}</strong><small>${escapeHtml(contentType.value)}</small></header>$bodyContent</main></body></html>
    """.trimIndent().toByteArray(Charsets.UTF_8)
    return WebResourceResponse(
        "text/html",
        "UTF-8",
        200,
        "OK",
        mapOf(
            "Cache-Control" to "no-store",
            "Content-Length" to html.size.toString(),
            "X-Content-Type-Options" to "nosniff",
        ),
        ByteArrayInputStream(html),
    )
}

internal fun parseCircleByteRange(raw: String, size: Int): IntRange? {
    if (size <= 0) return null
    val match = Regex("^bytes=(\\d*)-(\\d*)$", RegexOption.IGNORE_CASE).matchEntire(raw.trim()) ?: return null
    val startText = match.groupValues[1]
    val endText = match.groupValues[2]
    if (startText.isBlank() && endText.isBlank()) return null
    val start: Int
    val end: Int
    if (startText.isBlank()) {
        val suffix = endText.toLongOrNull()?.coerceAtMost(size.toLong())?.toInt() ?: return null
        if (suffix <= 0) return null
        start = size - suffix
        end = size - 1
    } else {
        start = startText.toLongOrNull()?.takeIf { it < size }?.toInt() ?: return null
        end = endText.toLongOrNull()?.coerceAtMost((size - 1).toLong())?.toInt() ?: (size - 1)
        if (end < start) return null
    }
    return start..end
}

private fun injectCircleRuntime(asset: CircleAsset, bridgeToken: String): String {
    val html = asset.body.toString(Charsets.UTF_8)
    val context = JSONObject()
        .put("circle_id", asset.circleId)
        .put("path", asset.canonicalPath)
        .put("uri", "oct://${asset.circleId}${asset.canonicalPath}")
        .toString()
    val token = JSONObject.quote(bridgeToken)
    val prelude = """
        <script>
        (function () {
          var context = $context;
          var bridgeToken = $token;
          var rpcUrl = 'poctra://circle-rpc';
          var nextCircleRequestId = 0;
          var pendingCircleRequests = {};
          var pendingCircleDownloads = {};
          var pendingRpcFetches = [];
          var rpcFetchTimer = 0;
          function installCircleTransactionSheetCompatibility() {
            if (!document || !document.documentElement || window.__poctraCircleTransactionSheetCompatibilityInstalled) return;
            window.__poctraCircleTransactionSheetCompatibilityInstalled = true;
            var repairScheduled = false;
            function repairTransactionSheet() {
              var sheet = document.querySelector('.tx-progress-sheet');
              if (!sheet) return;
              var backdrop = sheet.closest('.tx-progress-backdrop') || sheet.parentElement;
              var viewport = window.visualViewport;
              var viewportLeft = viewport ? viewport.offsetLeft : 0;
              var viewportTop = viewport ? viewport.offsetTop : 0;
              var viewportWidth = viewport ? viewport.width : window.innerWidth;
              var viewportHeight = viewport ? viewport.height : window.innerHeight;
              var reportedBottomInset = Math.max(0, Math.round(window.innerHeight - (viewportTop + viewportHeight)));
              var bottomReserve = Math.max(16, reportedBottomInset);
              var maxHeight = Math.max(280, Math.floor(viewportHeight * 0.88));

              if (backdrop) {
                backdrop.style.setProperty('position', 'fixed', 'important');
                backdrop.style.setProperty('inset', '0px', 'important');
                backdrop.style.setProperty('width', Math.round(viewportWidth) + 'px', 'important');
                backdrop.style.setProperty('height', Math.round(viewportHeight) + 'px', 'important');
                backdrop.style.setProperty('min-height', Math.round(viewportHeight) + 'px', 'important');
                backdrop.style.setProperty('max-height', Math.round(viewportHeight) + 'px', 'important');
                backdrop.style.setProperty('display', 'flex', 'important');
                backdrop.style.setProperty('align-items', 'flex-end', 'important');
                backdrop.style.setProperty('justify-content', 'center', 'important');
                backdrop.style.setProperty('padding', '0px', 'important');
                backdrop.style.setProperty('overflow', 'hidden', 'important');
                backdrop.style.setProperty('z-index', '2147482999', 'important');
              }

              sheet.style.setProperty('position', 'fixed', 'important');
              sheet.style.setProperty('left', Math.round(viewportLeft) + 'px', 'important');
              sheet.style.setProperty('right', 'auto', 'important');
              sheet.style.setProperty('top', 'auto', 'important');
              sheet.style.setProperty('bottom', Math.round(bottomReserve) + 'px', 'important');
              sheet.style.setProperty('width', Math.round(viewportWidth) + 'px', 'important');
              sheet.style.setProperty('max-width', Math.round(viewportWidth) + 'px', 'important');
              sheet.style.setProperty('min-width', '0px', 'important');
              sheet.style.setProperty('height', 'auto', 'important');
              sheet.style.setProperty('min-height', '0px', 'important');
              sheet.style.setProperty('max-height', maxHeight + 'px', 'important');
              sheet.style.setProperty('transform', 'none', 'important');
              sheet.style.setProperty('animation', 'none', 'important');
              sheet.style.setProperty('overflow-y', 'auto', 'important');
              sheet.style.setProperty('box-sizing', 'border-box', 'important');
              sheet.style.setProperty('z-index', '2147483000', 'important');
              sheet.setAttribute('data-poctra-circle-sheet-compat', 'true');

              // WebView can report a collapsed intrinsic height for a portal
              // during its first layout. Re-read after neutralizing the sheet
              // transform, then provide a conservative floor only if needed.
              sheet.getBoundingClientRect();
              var measuredHeight = Math.max(sheet.scrollHeight || 0, sheet.offsetHeight || 0);
              if (measuredHeight < 180) {
                var hasResultActions = !!sheet.querySelector('.tx-modal-link, .tx-modal-action');
                var heightFloor = Math.min(maxHeight, Math.floor(viewportHeight * (hasResultActions ? 0.62 : 0.50)));
                sheet.style.setProperty('height', Math.max(300, heightFloor) + 'px', 'important');
              }
              if (${BuildConfig.DEBUG}) {
                var rect = sheet.getBoundingClientRect();
                try {
                  console.log('[PoctraCircle] transaction sheet repaired rect=' +
                    Math.round(rect.left) + ',' + Math.round(rect.top) + ',' +
                    Math.round(rect.width) + 'x' + Math.round(rect.height) +
                    ' scroll=' + sheet.scrollHeight + '/' + sheet.clientHeight);
                } catch (_) {}
              }
            }
            function scheduleRepair() {
              if (repairScheduled) return;
              repairScheduled = true;
              var run = function () {
                repairScheduled = false;
                repairTransactionSheet();
                window.setTimeout(repairTransactionSheet, 80);
                window.setTimeout(repairTransactionSheet, 240);
              };
              if (typeof window.requestAnimationFrame === 'function') window.requestAnimationFrame(run);
              else window.setTimeout(run, 16);
            }
            var observer = new MutationObserver(function (mutations) {
              for (var index = 0; index < mutations.length; index++) {
                if (mutations[index].addedNodes.length || mutations[index].removedNodes.length || mutations[index].type === 'characterData') {
                  scheduleRepair();
                  break;
                }
              }
            });
            observer.observe(document.documentElement, { childList: true, subtree: true, characterData: true });
            document.addEventListener('click', scheduleRepair, true);
            window.addEventListener('resize', scheduleRepair);
            if (window.visualViewport) {
              window.visualViewport.addEventListener('resize', scheduleRepair);
              window.visualViewport.addEventListener('scroll', scheduleRepair);
            }
            scheduleRepair();
          }
          installCircleTransactionSheetCompatibility();
          function rejectNativeBridge(message) {
            return Promise.reject(new Error(message || 'OctraCircle native bridge is not available.'));
          }
          window.__poctraCircleResolve = function (id, result) {
            var entry = pendingCircleRequests[id];
            if (!entry) return;
            delete pendingCircleRequests[id];
            clearTimeout(entry.timeout);
            entry.resolve(result);
          };
          window.__poctraCircleReject = function (id, code, message) {
            var entry = pendingCircleRequests[id];
            if (!entry) return;
            delete pendingCircleRequests[id];
            clearTimeout(entry.timeout);
            var error = new Error(message || 'Circle request failed.');
            error.code = code;
            entry.reject(error);
          };
          window.__poctraCircleDownloadProgress = function (id, completed, total) {
            var entry = pendingCircleDownloads[id];
            if (!entry) return;
            entry.link.textContent = 'Preparing ' + completed + '/' + total;
          };
          window.__poctraCircleDownloadDone = function (id) {
            var entry = pendingCircleDownloads[id];
            if (!entry) return;
            delete pendingCircleDownloads[id];
            entry.link.innerHTML = entry.originalHtml;
            entry.link.removeAttribute('aria-disabled');
          };
          window.__poctraCircleDownloadError = function (id, message) {
            var entry = pendingCircleDownloads[id];
            if (!entry) return;
            delete pendingCircleDownloads[id];
            entry.link.textContent = 'Retry download';
            entry.link.removeAttribute('aria-disabled');
            try { console.error(message || 'Circle download failed.'); } catch (_) {}
          };
          window.__poctraCircleClipboardResolve = function (id) {
            var entry = pendingCircleRequests[id];
            if (!entry) return;
            delete pendingCircleRequests[id];
            clearTimeout(entry.timeout);
            entry.resolve();
          };
          window.__poctraCircleClipboardReject = function (id, message) {
            var entry = pendingCircleRequests[id];
            if (!entry) return;
            delete pendingCircleRequests[id];
            clearTimeout(entry.timeout);
            entry.reject(new Error(message || 'Unable to copy text.'));
          };
          function circleRequest(method, payload) {
            if (!window.PoctraAndroid || !window.PoctraAndroid.circleRequest) {
              return rejectNativeBridge();
            }
            return new Promise(function (resolve, reject) {
              var id = 'circle_' + String(++nextCircleRequestId);
              var timeout = setTimeout(function () {
                delete pendingCircleRequests[id];
                var error = new Error('Circle request timed out. Check the wallet approval dialog and retry.');
                error.code = -32001;
                reject(error);
              }, 300000);
              pendingCircleRequests[id] = { resolve: resolve, reject: reject, timeout: timeout };
              try {
                window.PoctraAndroid.circleRequest(JSON.stringify({
                  id: id,
                  bridgeToken: bridgeToken,
                  method: String(method || ''),
                  payload: payload || {},
                  circleId: context.circle_id,
                  origin: context.uri
                }));
              } catch (error) {
                delete pendingCircleRequests[id];
                clearTimeout(timeout);
                reject(error);
              }
            });
          }
          function bodyText(body) {
            if (body == null) return Promise.resolve('');
            if (typeof body === 'string') return Promise.resolve(body);
            if (body instanceof Blob) return body.text();
            if (body instanceof ArrayBuffer) return Promise.resolve(new TextDecoder().decode(body));
            if (body && typeof body.text === 'function') return body.text();
            return Promise.resolve(String(body));
          }
          function isCircleRpcTarget(input) {
            var value = typeof input === 'string'
              ? input
              : (input && input.url ? String(input.url) : '');
            if (value === rpcUrl ||
              value === rpcUrl + '/rpc' ||
              value.indexOf(rpcUrl + '/') === 0 ||
              value === '/rpc' ||
              value === './rpc' ||
              value === window.RPC_URL ||
              value === window.OCTRA_RPC_URL) return true;
            try {
              var parsed = new URL(value, window.location.href);
              return /(?:^|\/)rpc\/?${'$'}/i.test(parsed.pathname || '');
            } catch (_) {
              return false;
            }
          }
          function jsonRpcResponse(payload) {
            return new Response(JSON.stringify(payload), {
              status: 200,
              headers: { 'content-type': 'application/json' }
            });
          }
          function jsonRpcErrorResponse(payload, error) {
            return jsonRpcResponse({
              jsonrpc: '2.0',
              id: payload && payload.id !== undefined ? payload.id : null,
              error: { code: error.code || -32000, message: error.message || 'Circle RPC failed.' }
            });
          }
          function flushRpcFetches() {
            rpcFetchTimer = 0;
            var entries = pendingRpcFetches.splice(0, pendingRpcFetches.length);
            if (!entries.length) return;
            var batch = entries.map(function (entry) { return entry.payload; });
            circleRequest('rpc', batch).then(function (result) {
              var responses = Array.isArray(result) ? result : [result];
              entries.forEach(function (entry, index) {
                entry.resolve(jsonRpcResponse(responses[index] || {
                  jsonrpc: '2.0',
                  id: entry.payload && entry.payload.id !== undefined ? entry.payload.id : null,
                  error: { code: -32000, message: 'Circle RPC batch response missing.' }
                }));
              });
            }).catch(function (error) {
              entries.forEach(function (entry) {
                entry.resolve(jsonRpcErrorResponse(entry.payload, error));
              });
            });
          }
          function rpcResponse(payload) {
            if (Array.isArray(payload)) {
              return circleRequest('rpc', payload).then(jsonRpcResponse).catch(function (error) {
                return jsonRpcResponse(payload.map(function (item) {
                  return {
                    jsonrpc: '2.0',
                    id: item && item.id !== undefined ? item.id : null,
                    error: { code: error.code || -32000, message: error.message || 'Circle RPC failed.' }
                  };
                }));
              });
            }
            return new Promise(function (resolve) {
              pendingRpcFetches.push({ payload: payload || {}, resolve: resolve });
              if (!rpcFetchTimer) {
                rpcFetchTimer = window.setTimeout(flushRpcFetches, 16);
              }
            }).catch(function (error) {
              return new Response(JSON.stringify({
                jsonrpc: '2.0',
                id: payload && payload.id !== undefined ? payload.id : null,
                error: { code: error.code || -32000, message: error.message || 'Circle RPC failed.' }
              }), {
                status: 200,
                headers: { 'content-type': 'application/json' }
              });
            });
          }
          Object.defineProperty(window, 'OctraCircle', {
            configurable: true,
            value: Object.freeze({
              context: Object.freeze(context),
              navigate: function (uri) { window.location.href = String(uri || ''); },
              request: circleRequest
            })
          });
          try {
            Object.defineProperty(navigator, 'clipboard', {
              configurable: true,
              enumerable: true,
              value: Object.freeze({
                writeText: function (value) {
                  if (!window.PoctraAndroid || !window.PoctraAndroid.circleClipboardWrite) {
                    return Promise.reject(new Error('Circle clipboard bridge is unavailable.'));
                  }
                  var id = 'clipboard_' + String(++nextCircleRequestId);
                  return new Promise(function (resolve, reject) {
                    var timeout = setTimeout(function () {
                      delete pendingCircleRequests[id];
                      reject(new Error('Clipboard write timed out.'));
                    }, 15000);
                    pendingCircleRequests[id] = { resolve: resolve, reject: reject, timeout: timeout };
                    window.PoctraAndroid.circleClipboardWrite(JSON.stringify({
                      id: id,
                      bridgeToken: bridgeToken,
                      text: String(value == null ? '' : value)
                    }));
                  });
                }
              })
            });
          } catch (_) {}
          try {
            Object.defineProperty(window, 'RPC_URL', { configurable: true, value: rpcUrl });
            Object.defineProperty(window, 'OCTRA_RPC_URL', { configurable: true, value: rpcUrl });
          } catch (_) {
            window.RPC_URL = rpcUrl;
            window.OCTRA_RPC_URL = rpcUrl;
          }
          if (window.fetch) {
            var nativeFetch = window.fetch.bind(window);
            window.fetch = function (input, init) {
              if (!isCircleRpcTarget(input)) {
                return nativeFetch(input, init);
              }
              var options = init || {};
              var body = options.body;
              var textPromise = body != null
                ? bodyText(body)
                : (input && typeof input.clone === 'function'
                    ? input.clone().text()
                    : Promise.resolve(''));
              return textPromise.then(function (text) {
                var payload = text ? JSON.parse(text) : {};
                return rpcResponse(payload);
              });
            };
          }
          if (window.XMLHttpRequest) {
            var NativeXMLHttpRequest = window.XMLHttpRequest;
            function CircleXMLHttpRequest() {
              this._native = null;
              this._method = 'GET';
              this._url = '';
              this._headers = {};
              this._responseHeaders = {};
              this.readyState = 0;
              this.status = 0;
              this.statusText = '';
              this.responseType = '';
              this.response = null;
              this.responseText = '';
              this.responseURL = '';
              this.timeout = 0;
              this.withCredentials = false;
              this._listeners = {};
            }
            CircleXMLHttpRequest.UNSENT = 0;
            CircleXMLHttpRequest.OPENED = 1;
            CircleXMLHttpRequest.HEADERS_RECEIVED = 2;
            CircleXMLHttpRequest.LOADING = 3;
            CircleXMLHttpRequest.DONE = 4;
            CircleXMLHttpRequest.prototype.addEventListener = function (name, listener) {
              if (typeof listener !== 'function') return;
              (this._listeners[name] || (this._listeners[name] = [])).push(listener);
            };
            CircleXMLHttpRequest.prototype.removeEventListener = function (name, listener) {
              this._listeners[name] = (this._listeners[name] || []).filter(function (entry) { return entry !== listener; });
            };
            CircleXMLHttpRequest.prototype._emit = function (name) {
              var event = new Event(name);
              var handler = this['on' + name];
              if (typeof handler === 'function') try { handler.call(this, event); } catch (_) {}
              (this._listeners[name] || []).slice().forEach(function (listener) {
                try { listener.call(this, event); } catch (_) {}
              }, this);
            };
            CircleXMLHttpRequest.prototype._syncNative = function () {
              var native = this._native;
              if (!native) return;
              this.readyState = native.readyState;
              this.status = native.status;
              this.statusText = native.statusText;
              this.response = native.response;
              this.responseText = native.responseType && native.responseType !== 'text' ? '' : native.responseText;
              this.responseURL = native.responseURL;
            };
            CircleXMLHttpRequest.prototype.open = function (method, url, async, user, password) {
              this._method = String(method || 'GET').toUpperCase();
              this._url = String(url || '');
              if (!isCircleRpcTarget(this._url)) {
                var native = this._native = new NativeXMLHttpRequest();
                var self = this;
                ['readystatechange', 'load', 'error', 'abort', 'timeout', 'loadend', 'progress'].forEach(function (name) {
                  native.addEventListener(name, function () { self._syncNative(); self._emit(name); });
                });
                native.open(method, url, async !== false, user, password);
                this.readyState = native.readyState;
              } else {
                this.readyState = 1;
                this._emit('readystatechange');
              }
            };
            CircleXMLHttpRequest.prototype.setRequestHeader = function (name, value) {
              if (this._native) this._native.setRequestHeader(name, value);
              else this._headers[String(name)] = String(value);
            };
            CircleXMLHttpRequest.prototype.getResponseHeader = function (name) {
              return this._native ? this._native.getResponseHeader(name) : (this._responseHeaders[String(name).toLowerCase()] || null);
            };
            CircleXMLHttpRequest.prototype.getAllResponseHeaders = function () {
              if (this._native) return this._native.getAllResponseHeaders();
              return Object.keys(this._responseHeaders).map(function (key) { return key + ': ' + this._responseHeaders[key]; }, this).join('\r\n');
            };
            CircleXMLHttpRequest.prototype.overrideMimeType = function (value) {
              if (this._native) this._native.overrideMimeType(value);
            };
            CircleXMLHttpRequest.prototype.abort = function () {
              if (this._native) this._native.abort();
              this.readyState = 0;
              this._emit('abort');
              this._emit('loadend');
            };
            CircleXMLHttpRequest.prototype.send = function (body) {
              if (this._native) {
                this._native.responseType = this.responseType;
                this._native.timeout = this.timeout;
                this._native.withCredentials = this.withCredentials;
                this._native.send(body);
                return;
              }
              var self = this;
              window.fetch(this._url, { method: this._method, headers: this._headers, body: body }).then(function (response) {
                self.status = response.status;
                self.statusText = response.statusText;
                self.responseURL = self._url;
                self._responseHeaders = { 'content-type': response.headers.get('content-type') || 'application/json' };
                self.readyState = 2;
                self._emit('readystatechange');
                return response.text();
              }).then(function (text) {
                self.readyState = 4;
                self.responseText = text;
                if (self.responseType === 'json') {
                  try { self.response = JSON.parse(text); } catch (_) { self.response = null; }
                } else {
                  self.response = text;
                }
                self._emit('readystatechange');
                self._emit('load');
                self._emit('loadend');
              }).catch(function () {
                self.readyState = 4;
                self.status = 0;
                self._emit('readystatechange');
                self._emit('error');
                self._emit('loadend');
              });
            };
            window.XMLHttpRequest = CircleXMLHttpRequest;
          }
          function canonicalDownloadResource(raw) {
            if (!raw || raw === '#') return null;
            try {
              var parsed = new URL(raw, window.location.href);
              if (parsed.protocol === 'oct:') {
                return {
                  circleId: parsed.hostname,
                  path: parsed.pathname && parsed.pathname !== '/' ? parsed.pathname : '/index.html'
                };
              }
              var segments = (parsed.pathname || '').split('/').filter(Boolean).map(function (segment) {
                try { return decodeURIComponent(segment); } catch (_) { return segment; }
              });
              if (segments[0] === 'oct' && segments.length >= 2) {
                return {
                  circleId: segments[1],
                  path: segments.length > 2 ? '/' + segments.slice(2).join('/') : '/index.html'
                };
              }
              var path = parsed.pathname || '';
              var prefix = '/oct/' + context.circle_id;
              if (path === prefix) path = context.path || '/index.html';
              if (path.indexOf(prefix + '/') === 0) path = path.slice(prefix.length);
              return {
                circleId: context.circle_id,
                path: path.charAt(0) === '/' ? path : '/' + path
              };
            } catch (_) {
              return {
                circleId: context.circle_id,
                path: raw.charAt(0) === '/' ? raw : '/' + raw
              };
            }
          }
          function downloadMime(fileName, explicitMime) {
            if (explicitMime) return explicitMime;
            var lower = String(fileName || '').toLowerCase();
            if (lower.endsWith('.apk')) return 'application/vnd.android.package-archive';
            if (lower.endsWith('.pdf')) return 'application/pdf';
            if (lower.endsWith('.zip')) return 'application/zip';
            return 'application/octet-stream';
          }
          function circleDownloadSpec(link) {
            var declared = link.getAttribute('data-circle-download-parts') || '';
            var href = link.getAttribute('href') || '';
            var resources = declared.split(/[\n,]+/).map(function (item) {
              return canonicalDownloadResource(item.trim());
            }).filter(Boolean);
            if (!resources.length) {
              var directResource = canonicalDownloadResource(href);
              var isDownload = link.hasAttribute('download') || /\.(apk|exe|msi|pdf|zip|7z|rar|dmg|deb|rpm|tar|gz|csv|docx?|xlsx?|pptx?)(?:$|[?#])/i.test(href);
              if (!isDownload || !directResource) return null;
              resources = [directResource];
            }
            var fallback = resources[resources.length - 1].path.split('/').pop().replace(/\.part\d+$/i, '');
            var fileName = link.getAttribute('data-circle-download-name') || link.getAttribute('download') || fallback || 'circle-download';
            return {
              resources: resources,
              fileName: fileName,
              mimeType: downloadMime(fileName, link.getAttribute('data-circle-download-type') || link.getAttribute('type') || ''),
              sha256: link.getAttribute('data-circle-download-sha256') || ''
            };
          }
          document.addEventListener('click', function (event) {
            var link = event.target && event.target.closest ? event.target.closest('a[href]') : null;
            if (!link) return;
            var spec = circleDownloadSpec(link);
            if (!spec) return;
            event.preventDefault();
            event.stopImmediatePropagation();
            if (!window.PoctraAndroid || !window.PoctraAndroid.circleDownload) return;
            var id = 'download_' + String(++nextCircleRequestId);
            pendingCircleDownloads[id] = { link: link, originalHtml: link.innerHTML };
            link.textContent = 'Preparing 0/' + spec.resources.length;
            link.setAttribute('aria-disabled', 'true');
            window.PoctraAndroid.circleDownload(JSON.stringify({
              id: id,
              bridgeToken: bridgeToken,
              resources: spec.resources,
              fileName: spec.fileName,
              mimeType: spec.mimeType,
              sha256: spec.sha256
            }));
          }, true);
        })();
        </script>
    """.trimIndent()
    val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
    return if (head != null) {
        html.substring(0, head.range.last + 1) + prelude + html.substring(head.range.last + 1)
    } else {
        prelude + html
    }
}

private fun circleHtmlResponse(
    title: String,
    message: String,
    statusCode: Int = 200,
    reasonPhrase: String = "OK",
): WebResourceResponse {
    val html = """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <style>
            body { margin: 0; min-height: 100vh; display: grid; place-items: center; background: #0f100e; color: #f7f7f2; font: 13px monospace; }
            main { width: min(86vw, 420px); border: 1px solid #30352b; border-radius: 8px; background: #191b18; padding: 18px; }
            h1 { font-size: 16px; margin: 0 0 10px; }
            p { color: #9da199; line-height: 1.5; margin: 0; }
          </style>
        </head>
        <body><main><h1>${escapeHtml(title)}</h1><p>${escapeHtml(message)}</p></main></body>
        </html>
    """.trimIndent()
    return WebResourceResponse(
        "text/html",
        "UTF-8",
        statusCode,
        reasonPhrase,
        mapOf("Cache-Control" to "no-store", "X-Content-Type-Options" to "nosniff"),
        ByteArrayInputStream(html.toByteArray(Charsets.UTF_8)),
    )
}

private fun circleLoadingHtmlResponse(): WebResourceResponse {
    val body = """
        <!doctype html>
        <html><head><meta name="viewport" content="width=device-width, initial-scale=1" />
        <style>html,body{margin:0;min-height:100%;background:#0f100e}</style></head><body></body></html>
    """.trimIndent().toByteArray(Charsets.UTF_8)
    return WebResourceResponse(
        "text/html",
        "UTF-8",
        200,
        "OK",
        mapOf("Cache-Control" to "no-store", "Content-Length" to body.size.toString()),
        ByteArrayInputStream(body),
    )
}

private fun isCircleLoadingBrowserUrl(value: String?): Boolean = runCatching {
    val uri = Uri.parse(value.orEmpty())
    uri.host.equals(PoctraController.CircleBrowserHost, ignoreCase = true) &&
        uri.pathSegments.firstOrNull().equals("loading", ignoreCase = true)
}.getOrDefault(false)

private fun emptyCircleResponse(): WebResourceResponse =
    WebResourceResponse(
        "text/plain",
        "UTF-8",
        204,
        "No Content",
        mapOf("Cache-Control" to "no-store"),
        ByteArrayInputStream(ByteArray(0)),
    )

private fun escapeHtml(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")

@Composable
private fun DAppBrowserLoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBase),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.x8),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            CircularProgressIndicator(color = SurfaceRaised, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            Spacer(Modifier.height(Space.x5))
            Text("Loading", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DAppBrowserErrorOverlay(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBase),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Space.x8),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StateBanner(text = message, isError = true)
            Spacer(Modifier.height(Space.x7))
            PoctraButton(
                text = "Retry",
                onClick = onRetry,
                variant = ButtonVariant.Ghost,
                leadingGlyph = Glyph.Refresh,
            )
        }
    }
}

private fun injectPoctraProvider(webView: WebView, bridgeToken: String, targetUrl: String?) {
    runCatching {
        val script = if (requiresNavigationLifecycleProvider(targetUrl.orEmpty())) {
            androidRfcO1ContentBridgeScript(bridgeToken)
        } else {
            poctraProviderScript(bridgeToken)
        }
        webView.evaluateJavascript(script, null)
    }
}

private fun installDocumentStartProvider(webView: WebView, bridgeToken: String, targetUrl: String) {
    val supported = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
    if (BuildConfig.DEBUG) Log.d("PoctraDApp", "document-start supported=$supported")
    if (!supported) return
    runCatching {
        val script = if (requiresNavigationLifecycleProvider(targetUrl)) {
            androidRfcO1ContentBridgeScript(bridgeToken)
        } else {
            poctraProviderScript(bridgeToken)
        }
        WebViewCompat.addDocumentStartJavaScript(
            webView,
            script,
            // EVM dApps commonly redirect to a different HTTPS subdomain before
            // their application code runs. Android WebView accepts the global
            // origin rule here; the script has a top-frame guard and native
            // origin validation still gates every wallet request.
            setOf("*"),
        )
        if (BuildConfig.DEBUG) Log.d("PoctraDApp", "document-start provider installed")
    }.onFailure { error ->
        if (BuildConfig.DEBUG) Log.d("PoctraDApp", "document-start provider failed=${error.message}")
    }
}

private fun schedulePoctraProviderInjection(webView: WebView, bridgeToken: String, targetUrl: String?) {
    injectPoctraProvider(webView, bridgeToken, targetUrl)
    listOf(80L, 250L, 750L, 1_500L, 3_000L).forEach { delayMs ->
        webView.postDelayed({
            injectPoctraProvider(webView, bridgeToken, webView.url)
        }, delayMs)
    }
}

private fun androidRfcO1ContentBridgeScript(bridgeToken: String): String {
    val inpagePath = rfcO1InpageProviderPath(bridgeToken)
    return """
        (function () {
          var bridgeToken = ${JSONObject.quote(bridgeToken)};
          var targetOrigin = window.location.origin === 'null' ? '*' : window.location.origin;
          if (!window.__poctraAndroidRfcO1RelayInstalled) {
            window.__poctraAndroidRfcO1RelayInstalled = true;
            if (${BuildConfig.DEBUG}) {
              try { console.log('[PoctraRelay] relay installed origin=' + window.location.origin); } catch (_) {}
            }
            window.addEventListener('message', function (event) {
              if (event.source !== window) return;
              if (window.location.origin !== 'null' && event.origin !== window.location.origin) return;
              var data = event.data;
              if (!data || data.source !== 'poctra-inpage' || typeof data.id !== 'string') return;
              if (!data.id || data.id.length > 128 || typeof data.method !== 'string') return;
              if (${BuildConfig.DEBUG}) {
                try { console.log('[PoctraRelay] request id=' + data.id + ' method=' + data.method); } catch (_) {}
              }
              try {
                window.PoctraAndroid.request(JSON.stringify({
                  id: data.id,
                  bridgeToken: bridgeToken,
                  origin: window.location.origin || window.location.href,
                  method: data.method,
                  params: Array.isArray(data.params) ? data.params : []
                }));
              } catch (error) {
                window.postMessage({
                  source: 'poctra-content',
                  id: data.id,
                  error: {
                    code: 4900,
                    message: error && error.message ? error.message : 'Wallet transport disconnected.',
                    data: { reason: 'wallet_disconnected' }
                  }
                }, targetOrigin);
              }
            });
            window.__poctraAndroidRfcO1Resolve = function (id, result) {
              window.postMessage({ source: 'poctra-content', id: id, result: result }, targetOrigin);
            };
            window.__poctraAndroidRfcO1Reject = function (id, code, message) {
              window.postMessage({
                source: 'poctra-content',
                id: id,
                error: { code: code, message: message || 'Wallet request failed.' }
              }, targetOrigin);
            };
          }

          function installInpageProvider() {
            var existing = document.getElementById('poctra-rfc-o1-inpage');
            if (existing) {
              window.dispatchEvent(new Event('octra:requestProvider'));
              return;
            }
            var mount = document.head || document.documentElement;
            if (!mount) {
              window.setTimeout(installInpageProvider, 0);
              return;
            }
            var script = document.createElement('script');
            script.id = 'poctra-rfc-o1-inpage';
            script.src = ${JSONObject.quote(inpagePath)};
            script.async = false;
            script.setAttribute('data-poctra-provider', 'rfc-o-1');
            script.addEventListener('load', function () {
              try { console.log('[PoctraDebug] extension-compatible RFC-O-1 provider loaded'); } catch (_) {}
            }, { once: true });
            script.addEventListener('error', function () {
              script.remove();
              try { console.log('[PoctraDebug] extension-compatible RFC-O-1 provider failed'); } catch (_) {}
            }, { once: true });
            mount.appendChild(script);
          }
          installInpageProvider();
        })();
    """.trimIndent()
}

private fun poctraProviderScript(bridgeToken: String): String = """
    (function () {
      // Wallet providers belong to the top-level dApp document. Do not expose
      // the bridge token or provider to third-party iframes.
      try {
        if (window.top !== window.self) return;
      } catch (_) {
        return;
      }
      var bridgeToken = ${JSONObject.quote(bridgeToken)};
      var poctraDebug = ${BuildConfig.DEBUG};
      window.__poctraBridgeToken = bridgeToken;
      function poctraDebugLog(message) {
        if (!poctraDebug) return;
        try { console.log('[PoctraDebug] ' + message); } catch (_) {}
      }
      if (poctraDebug && !window.__poctraDebugClickInstalled) {
        window.__poctraDebugClickInstalled = true;
        try {
          document.addEventListener('click', function (event) {
            window.setTimeout(function () {
              try {
                var target = event && event.target;
                var targetName = target && target.tagName ? String(target.tagName).toLowerCase() : 'unknown';
                var targetRole = target && target.getAttribute ? (target.getAttribute('role') || '') : '';
                var targetControl = target && target.closest ? target.closest('button, a, [role="button"]') : null;
                var targetLabel = '';
                var targetClass = '';
                try {
                  targetLabel = String(
                    targetControl && (targetControl.getAttribute('aria-label') || targetControl.textContent) || ''
                  ).replace(/\s+/g, ' ').trim().slice(0, 120);
                  targetClass = String(targetControl && targetControl.className || '').replace(/\s+/g, '.').slice(0, 80);
                } catch (_) {}
                var dialogs = document.querySelectorAll(
                  '[role="dialog"], [aria-modal="true"], dialog, [data-state="open"], [data-vaul-drawer]'
                );
                var geometry = [];
                for (var index = 0; index < dialogs.length && index < 8; index++) {
                  var dialog = dialogs[index];
                  var rect = dialog.getBoundingClientRect();
                  var style = window.getComputedStyle(dialog);
                  var firstChild = dialog.firstElementChild;
                  var childRect = firstChild ? firstChild.getBoundingClientRect() : null;
                  var className = typeof dialog.className === 'string' ? dialog.className.replace(/\s+/g, '.').slice(0, 72) : '';
                  geometry.push(
                    String(index) + ':' + String(dialog.tagName || '').toLowerCase() +
                    (dialog.id ? '#' + dialog.id : '') + (className ? '.' + className : '') +
                    ',state=' + (dialog.getAttribute('data-state') || '') +
                    ',role=' + (dialog.getAttribute('role') || '') +
                    ',rect=' + Math.round(rect.left) + ',' + Math.round(rect.top) + ',' +
                    Math.round(rect.width) + 'x' + Math.round(rect.height) + ',' +
                    style.display + ',' + style.visibility + ',' + style.opacity + ',z=' + style.zIndex +
                    ',pos=' + style.position + ',overflow=' + style.overflow + ',transform=' + style.transform +
                    ',children=' + dialog.childElementCount + ',desc=' + dialog.querySelectorAll('*').length +
                    ',scroll=' + dialog.scrollHeight + '/' + dialog.clientHeight +
                    (childRect ? ',first=' + Math.round(childRect.left) + ',' + Math.round(childRect.top) + ',' +
                      Math.round(childRect.width) + 'x' + Math.round(childRect.height) : '')
                  );
                }
                poctraDebugLog(
                  'click target=' + targetName + ' role=' + targetRole +
                  ' control=' + targetClass + ' label=' + targetLabel +
                  ' viewport=' + window.innerWidth + 'x' + window.innerHeight +
                  ' visual=' + (window.visualViewport ?
                    Math.round(window.visualViewport.width) + 'x' + Math.round(window.visualViewport.height) : 'none') +
                  ' dialogs=' + dialogs.length + ' [' + geometry.join(';') + ']'
                );
              } catch (_) {}
            }, 350);
          }, true);
        } catch (_) {}
      }
      function installMobileSheetCompatibility() {
        if (!document || !document.documentElement) {
          window.setTimeout(installMobileSheetCompatibility, 0);
          return;
        }
        var repairedProperties = [
          'position', 'left', 'right', 'top', 'bottom', 'width', 'height', 'min-height',
          'max-height', 'transform', 'overflow', 'z-index', 'box-sizing'
        ];
        var repairedChildProperties = ['left', 'right', 'width', 'min-width', 'max-width', 'transform'];
        var repairedContainerProperties = ['transform', 'perspective', 'filter', 'contain', 'will-change'];
        function removeImportantProperties(element, properties) {
          for (var index = 0; index < properties.length; index++) element.style.removeProperty(properties[index]);
        }
        function restoreClosedSheets() {
          var repaired = document.querySelectorAll('[data-poctra-sheet-compat="true"]');
          for (var index = 0; index < repaired.length; index++) {
            var element = repaired[index];
            var state = element.getAttribute('data-state');
            var computed = window.getComputedStyle(element);
            var closed = !element.isConnected || computed.display === 'none' || computed.visibility === 'hidden' ||
              computed.pointerEvents === 'none' || Number(computed.opacity || '1') <= 0 ||
              element.getAttribute('aria-hidden') === 'true' || (state && state !== 'open');
            if (closed) {
              element.removeAttribute('data-poctra-sheet-compat');
              removeImportantProperties(element, repairedProperties);
            }
          }
          if (!document.querySelector('[data-poctra-sheet-compat="true"]')) {
            var repairedChildren = document.querySelectorAll('[data-poctra-sheet-child-compat="true"]');
            for (var childIndex = 0; childIndex < repairedChildren.length; childIndex++) {
              repairedChildren[childIndex].removeAttribute('data-poctra-sheet-child-compat');
              removeImportantProperties(repairedChildren[childIndex], repairedChildProperties);
            }
            var repairedContainers = document.querySelectorAll('[data-poctra-sheet-container-compat="true"]');
            for (var containerIndex = 0; containerIndex < repairedContainers.length; containerIndex++) {
              repairedContainers[containerIndex].removeAttribute('data-poctra-sheet-container-compat');
              removeImportantProperties(repairedContainers[containerIndex], repairedContainerProperties);
            }
          }
        }
        function normalizeContainingBlocks(element) {
          var ancestor = element.parentElement;
          while (ancestor && ancestor !== document.body && ancestor !== document.documentElement) {
            var computed = window.getComputedStyle(ancestor);
            var establishesContainingBlock = computed.transform !== 'none' || computed.perspective !== 'none' ||
              computed.filter !== 'none' || /paint|layout|strict|content/.test(computed.contain || '') ||
              /transform|perspective|filter/.test(computed.willChange || '');
            if (establishesContainingBlock) {
              ancestor.style.setProperty('transform', 'none', 'important');
              ancestor.style.setProperty('perspective', 'none', 'important');
              ancestor.style.setProperty('filter', 'none', 'important');
              ancestor.style.setProperty('contain', 'none', 'important');
              ancestor.style.setProperty('will-change', 'auto', 'important');
              ancestor.setAttribute('data-poctra-sheet-container-compat', 'true');
            }
            ancestor = ancestor.parentElement;
          }
        }
        function expandCollapsedContent(element) {
          var descendants = element.querySelectorAll('*');
          var expanded = 0;
          for (var index = 0; index < descendants.length; index++) {
            var child = descendants[index];
            var childStyle = window.getComputedStyle(child);
            if (childStyle.display === 'none' || childStyle.visibility === 'hidden') continue;
            var childRect = child.getBoundingClientRect();
            if (childRect.width > 2 || childRect.height < 64) continue;
            child.style.setProperty('left', '0px', 'important');
            child.style.setProperty('right', '0px', 'important');
            child.style.setProperty('width', '100%', 'important');
            child.style.setProperty('min-width', '100%', 'important');
            child.style.setProperty('max-width', '100%', 'important');
            child.style.setProperty('transform', 'none', 'important');
            child.setAttribute('data-poctra-sheet-child-compat', 'true');
            expanded += 1;
          }
          return expanded;
        }
        function repairOpenSheets() {
          restoreClosedSheets();
          var viewport = window.visualViewport;
          var viewportLeft = viewport ? viewport.offsetLeft : 0;
          var viewportTop = viewport ? viewport.offsetTop : 0;
          var viewportWidth = viewport ? viewport.width : window.innerWidth;
          var viewportHeight = viewport ? viewport.height : window.innerHeight;
          var viewportBottom = viewportTop + viewportHeight;
          var candidates = document.querySelectorAll(
            '[role="dialog"], [aria-modal="true"], dialog, [data-state="open"], [data-vaul-drawer]'
          );
          for (var index = 0; index < candidates.length; index++) {
            var element = candidates[index];
            if (!element || element === document.body || element === document.documentElement) continue;
            // Circle pages install a viewport-aware transaction sheet repair
            // in their injected runtime. Let that single owner manage layout;
            // alternating between both repairers causes visible height flicker.
            if (element.getAttribute('data-poctra-circle-sheet-compat') === 'true') continue;
            var state = element.getAttribute('data-state');
            if (state && state !== 'open') continue;
            var computed = window.getComputedStyle(element);
            if (computed.display === 'none' || computed.visibility === 'hidden' || Number(computed.opacity || '1') <= 0) continue;
            var rect = element.getBoundingClientRect();
            if (rect.width < viewportWidth * 0.72 || element.childElementCount <= 0) continue;
            var contentHeight = Math.max(element.scrollHeight || 0, element.clientHeight || 0);
            var outsideViewport = rect.top >= viewportBottom - 2;
            var severelyClipped = rect.height <= 56 && contentHeight >= rect.height + 32;
            var role = element.getAttribute('role') || '';
            var collapsedDialog = rect.height <= 56 && (role === 'dialog' || element.getAttribute('aria-modal') === 'true' || element.tagName === 'DIALOG');
            var isTransactionProgress = element.classList && (
              element.classList.contains('tx-progress-dialog') || element.classList.contains('tx-progress-sheet')
            );
            var isWalletPicker = element.classList && element.classList.contains('wallet-sheet');
            var descendantCount = element.querySelectorAll('*').length;
            var contentFloor = descendantCount > 40
              ? Math.floor(viewportHeight * 0.72)
              : (descendantCount > 15 ? Math.floor(viewportHeight * 0.55) : 80);
            var desiredHeight = Math.min(
              Math.max(
                contentHeight,
                isWalletPicker ? 180 : (collapsedDialog ? Math.floor(viewportHeight * 0.72) : contentFloor)
              ),
              Math.floor(viewportHeight * 0.88)
            );
            var reportedBottomInset = Math.max(0, Math.round(window.innerHeight - viewportBottom));
            var transactionBottomReserve = Math.max(16, reportedBottomInset);
            var transactionProgressClipped = isTransactionProgress && (
              rect.height < desiredHeight - 8 ||
              rect.bottom > viewportBottom - transactionBottomReserve + 2 ||
              rect.top > viewportTop + viewportHeight * 0.72
            );
            if (!outsideViewport && !severelyClipped && !collapsedDialog && !transactionProgressClipped) continue;
            var desiredLeft = outsideViewport || isWalletPicker ? viewportLeft : Math.max(viewportLeft, rect.left);
            var desiredWidth = outsideViewport || isWalletPicker ? viewportWidth : Math.min(rect.width, viewportWidth);
            var desiredBottom = isTransactionProgress
              ? transactionBottomReserve
              : reportedBottomInset + ((collapsedDialog || transactionProgressClipped) && !outsideViewport ? 12 : 0);
            var desiredTop = Math.max(viewportTop, viewportBottom - desiredHeight - 12);
            normalizeContainingBlocks(element);
            element.style.setProperty('position', 'fixed', 'important');
            element.style.setProperty('left', Math.round(desiredLeft) + 'px', 'important');
            element.style.setProperty('right', 'auto', 'important');
            element.style.setProperty('top', isWalletPicker ? Math.round(desiredTop) + 'px' : 'auto', 'important');
            element.style.setProperty('bottom', isWalletPicker ? 'auto' : Math.round(desiredBottom) + 'px', 'important');
            element.style.setProperty('width', Math.round(desiredWidth) + 'px', 'important');
            element.style.setProperty('height', desiredHeight + 'px', 'important');
            element.style.setProperty('min-height', desiredHeight + 'px', 'important');
            element.style.setProperty('max-height', Math.floor(viewportHeight * 0.88) + 'px', 'important');
            element.style.setProperty('transform', 'none', 'important');
            element.style.setProperty('overflow', 'auto', 'important');
            element.style.setProperty('z-index', '2147483000', 'important');
            element.style.setProperty('box-sizing', 'border-box', 'important');
            element.setAttribute('data-poctra-sheet-compat', 'true');
            element.getBoundingClientRect();
            var expandedChildren = expandCollapsedContent(element);
            var repairedRect = element.getBoundingClientRect();
            poctraDebugLog(
              'mobile sheet repaired top=' + Math.round(rect.top) + ' height=' + Math.round(rect.height) +
              ' content=' + contentHeight + ' desired=' + desiredHeight + ' role=' + role +
              ' result=' + Math.round(repairedRect.left) + ',' + Math.round(repairedRect.top) + ',' +
              Math.round(repairedRect.width) + 'x' + Math.round(repairedRect.height) +
              ' expanded=' + expandedChildren
            );
          }
        }
        window.__poctraRepairMobileSheets = repairOpenSheets;
        if (!window.__poctraMobileSheetCompatibilityInstalled) {
          window.__poctraMobileSheetCompatibilityInstalled = true;
          var sheetRepairScheduled = false;
          function scheduleSheetRepair() {
            if (sheetRepairScheduled) return;
            sheetRepairScheduled = true;
            var runSheetRepair = function () {
              sheetRepairScheduled = false;
              repairOpenSheets();
              window.setTimeout(repairOpenSheets, 80);
              window.setTimeout(repairOpenSheets, 240);
            };
            if (typeof window.requestAnimationFrame === 'function') window.requestAnimationFrame(runSheetRepair);
            else window.setTimeout(runSheetRepair, 16);
          }
          document.addEventListener('click', scheduleSheetRepair, true);
          window.addEventListener('resize', scheduleSheetRepair);
          if (window.visualViewport) {
            window.visualViewport.addEventListener('resize', scheduleSheetRepair);
          }
          var sheetObserverRoot = document.documentElement || document.body;
          if (sheetObserverRoot && typeof MutationObserver === 'function') {
            window.__poctraMobileSheetObserver = new MutationObserver(function (mutations) {
              for (var mutationIndex = 0; mutationIndex < mutations.length; mutationIndex++) {
                if (mutations[mutationIndex].addedNodes && mutations[mutationIndex].addedNodes.length) {
                  scheduleSheetRepair();
                  break;
                }
              }
            });
            window.__poctraMobileSheetObserver.observe(sheetObserverRoot, { childList: true, subtree: true });
          }
          scheduleSheetRepair();
        }
      }
      installMobileSheetCompatibility();
      window.__poctraDismissConnectOverlays = function () {
        try { window.dispatchEvent(new Event('focus')); } catch (_) {}
        function dismissOnce() {
          try {
            document.dispatchEvent(new KeyboardEvent('keydown', {
              key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true
            }));
            document.dispatchEvent(new KeyboardEvent('keyup', {
              key: 'Escape', code: 'Escape', keyCode: 27, which: 27, bubbles: true
            }));
          } catch (_) {}
        }
        window.setTimeout(dismissOnce, 80);
        window.setTimeout(dismissOnce, 220);
      };
      function poctraNavigationControl(target) {
        try {
          return target && target.closest
            ? target.closest('a[href], area[href], [role="link"]')
            : null;
        } catch (_) {
          return null;
        }
      }
      function poctraNotifyNavigation() {
        try { window.PoctraAndroid.pageInteraction(window.__poctraBridgeToken || bridgeToken); } catch (_) {}
      }
      window.__poctraNotifyNavigation = poctraNotifyNavigation;
      if (!window.__poctraNavigationProgressInstalled) {
        window.__poctraNavigationProgressInstalled = true;
        document.addEventListener('click', function (event) {
          if (!event || event.defaultPrevented || event.button > 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
          var control = poctraNavigationControl(event.target);
          if (!control || control.getAttribute('aria-disabled') === 'true') return;
          var href = '';
          try { href = control.getAttribute('href') || ''; } catch (_) {}
          if (href === '#' || /^javascript:/i.test(href) || /^mailto:/i.test(href) || /^tel:/i.test(href)) return;
          window.__poctraNotifyNavigation();
        }, false);
        document.addEventListener('submit', function (event) {
          if (!event || event.defaultPrevented) return;
          window.__poctraNotifyNavigation();
        }, false);
        ['pushState', 'replaceState'].forEach(function (method) {
          try {
            var original = history[method];
            if (typeof original !== 'function') return;
            history[method] = function () {
              var before = window.location.href;
              var result = original.apply(this, arguments);
              if (window.location.href !== before) window.__poctraNotifyNavigation();
              return result;
            };
          } catch (_) {}
        });
        window.addEventListener('popstate', function () { window.__poctraNotifyNavigation(); });
        window.addEventListener('hashchange', function () { window.__poctraNotifyNavigation(); });
      }
      function dispatchEventSafely(event) {
        try { window.dispatchEvent(event); } catch (_) {}
      }
      function customEvent(name, detail) {
        try {
          return new CustomEvent(name, { detail: detail });
        } catch (_) {
          try {
            var event = document.createEvent('CustomEvent');
            event.initCustomEvent(name, false, false, detail);
            return event;
          } catch (_) {
            return null;
          }
        }
      }
      function announceProvider(provider, info) {
        var detail = Object.freeze({ provider: provider, info: info });
        var announceEvent = customEvent('octra:announceProvider', detail);
        if (announceEvent) dispatchEventSafely(announceEvent);
        poctraDebugLog(
          'RFC-O-1 announce rdns=' + String(info && info.rdns || '') +
          ' uuid=' + String(info && info.uuid || '') +
          ' icon=' + Boolean(info && info.icon) +
          ' surface=' + Boolean(
            provider && provider.isOctra === true &&
            typeof provider.request === 'function' &&
            typeof provider.on === 'function' &&
            typeof provider.removeListener === 'function'
          )
        );
        var loadedEvent = customEvent('octraLoaded', detail);
        if (loadedEvent) dispatchEventSafely(loadedEvent);
        dispatchEventSafely(new Event('octra#initialized'));
        dispatchEventSafely(new Event('poctra#initialized'));
      }
      var octraProviderUuid = ${JSONObject.quote(POCTRA_PROVIDER_UUID)};
      try {
        if (window.crypto && typeof window.crypto.randomUUID === 'function') {
          octraProviderUuid = window.crypto.randomUUID();
        }
      } catch (_) {}
      var octraProviderInfo = Object.freeze({
        uuid: octraProviderUuid,
        name: ${JSONObject.quote(POCTRA_PROVIDER_NAME)},
        rdns: ${JSONObject.quote(POCTRA_PROVIDER_RDNS)},
        icon: ${JSONObject.quote(POCTRA_PROVIDER_ICON)},
        version: ${JSONObject.quote(POCTRA_PROVIDER_VERSION)},
        homepage: ${JSONObject.quote(POCTRA_PROVIDER_HOMEPAGE)}
      });
      function installEvmProvider() {
        var currentHost = '';
        try { currentHost = String(window.location.hostname || '').toLowerCase(); } catch (_) {}
        if (currentHost === 'uniswap.org' || currentHost.endsWith('.uniswap.org')) {
          poctraDebugLog('EVM provider disabled for unsupported dApp host');
          return null;
        }
        var evmInfo = Object.freeze({
          uuid: '2e3de8f2-fb54-4ca1-bc67-81c82488a09c',
          name: 'poctra',
          icon: "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='96' height='96' viewBox='0 0 96 96'%3E%3Crect width='96' height='96' rx='24' fill='%23070b09'/%3E%3Ctext x='48' y='67' text-anchor='middle' font-family='monospace' font-size='64' font-weight='700' fill='%2310b981'%3Ep%3C/text%3E%3C/svg%3E",
          rdns: 'com.poctra.wallet',
          walletId: 'poctra'
        });
        function announceEvm(provider) {
          var detail = Object.freeze({ info: evmInfo, provider: provider });
          var event = customEvent('eip6963:announceProvider', detail);
          if (event) dispatchEventSafely(event);
          poctraDebugLog('eip6963 announce rdns=' + evmInfo.rdns);
        }
        function exposeEvmProvider(provider) {
          try {
            if (!window.ethereum || typeof window.ethereum.request !== 'function' || window.ethereum.isPoctra) {
              Object.defineProperty(window, 'ethereum', { value: provider, configurable: true, writable: false });
            }
          } catch (_) {
            if (!window.ethereum || typeof window.ethereum.request !== 'function') window.ethereum = provider;
          }
          try {
            var providers = window.ethereum && Array.isArray(window.ethereum.providers)
              ? window.ethereum.providers.slice()
              : [];
            if (providers.indexOf(provider) < 0) providers.push(provider);
            if (window.ethereum === provider) provider.providers = providers;
          } catch (_) {}
        }
        if (window.__poctraEvmProviderInstalled && window.poctraEthereum) {
          exposeEvmProvider(window.poctraEthereum);
          return window.poctraEthereum;
        }

        var evmPending = {};
        var evmNextId = 1;
        var evmListeners = {};
        var evmChainId = null;
        var evmSelectedAddress = null;
        var evmConnected = false;
        var evmAccountsRequestPromise = null;
        var evmDiscoveryObserved = false;
        function evmEmit(event) {
          var args = Array.prototype.slice.call(arguments, 1);
          var list = (evmListeners[event] || []).slice();
          for (var i = 0; i < list.length; i++) {
            try { list[i].apply(evmProvider, args); } catch (_) {}
          }
        }
        function evmError(code, message, data) {
          var error = new Error(message || 'EVM wallet request failed');
          error.name = 'ProviderRpcError';
          error.code = Number(code);
          if (data !== undefined) error.data = data;
          return error;
        }
        function updateEvmAccounts(accounts) {
          var next = Array.isArray(accounts) && accounts.length ? String(accounts[0]) : null;
          var changed = next !== evmSelectedAddress;
          evmSelectedAddress = next;
          if (changed) evmEmit('accountsChanged', next ? [next] : []);
        }
        function updateEvmChain(chainId) {
          if (typeof chainId !== 'string' || !chainId) return;
          var changed = evmChainId !== null && evmChainId !== chainId;
          evmChainId = chainId;
          if (changed) evmEmit('chainChanged', chainId);
          if (!evmConnected) {
            evmConnected = true;
            evmEmit('connect', { chainId: chainId });
          }
        }
        function refreshEvmChain() {
          return evmProvider.request({ method: 'eth_chainId' }).then(function (chainId) {
            updateEvmChain(chainId);
            return chainId;
          }).catch(function () { return null; });
        }

        window.__poctraEvmResolve = function (id, result) {
          var entry = evmPending[id];
          if (!entry) return;
          delete evmPending[id];
          if (entry.method === 'eth_requestAccounts') evmAccountsRequestPromise = null;
          if (entry.method === 'eth_requestAccounts' || entry.method === 'eth_accounts') {
            updateEvmAccounts(result);
          }
          if (entry.method === 'eth_chainId') updateEvmChain(result);
          if (entry.method === 'wallet_revokePermissions' || entry.method === 'eth_disconnect') {
            updateEvmAccounts([]);
          }
          entry.resolve(result);
          if (entry.method === 'wallet_requestPermissions') {
            evmProvider.request({ method: 'eth_accounts' }).then(updateEvmAccounts).catch(function () {});
          }
          if (entry.method === 'wallet_switchEthereumChain' || entry.method === 'wallet_addEthereumChain') {
            refreshEvmChain();
          }
        };
        window.__poctraEvmReject = function (id, code, message) {
          var entry = evmPending[id];
          if (!entry) return;
          delete evmPending[id];
          if (entry.method === 'eth_requestAccounts') evmAccountsRequestPromise = null;
          entry.reject(evmError(code, message));
        };

        var evmProvider = {
          isPoctra: true,
          isInjected: true,
          providerId: 'poctra-evm',
          name: 'poctra',
          rdns: 'com.poctra.wallet',
          get chainId() { return evmChainId; },
          get networkVersion() {
            if (!evmChainId) return null;
            try { return String(parseInt(evmChainId, 16)); } catch (_) { return null; }
          },
          get selectedAddress() { return evmSelectedAddress; },
          isConnected: function () { return evmConnected; },
          request: function (args) {
            if (!args || typeof args.method !== 'string' || !args.method) {
              return Promise.reject(evmError(-32602, 'EVM provider method is required'));
            }
            if (args.method === 'eth_requestAccounts' && evmAccountsRequestPromise) {
              return evmAccountsRequestPromise;
            }
            var requestPromise = new Promise(function (resolve, reject) {
              var id = 'evm-' + String(evmNextId++);
              evmPending[id] = { resolve: resolve, reject: reject, method: args.method };
              try {
                window.PoctraAndroid.evmRequest(JSON.stringify({
                  id: id,
                  bridgeToken: window.__poctraBridgeToken || bridgeToken,
                  origin: window.location.origin || window.location.href,
                  method: args.method,
                  params: args.params === undefined ? [] : args.params
                }));
              } catch (error) {
                delete evmPending[id];
                reject(evmError(4900, error && error.message ? error.message : 'poctra EVM bridge unavailable'));
              }
            });
            if (args.method === 'eth_requestAccounts') {
              evmAccountsRequestPromise = requestPromise;
              requestPromise.then(
                function () { if (evmAccountsRequestPromise === requestPromise) evmAccountsRequestPromise = null; },
                function () { if (evmAccountsRequestPromise === requestPromise) evmAccountsRequestPromise = null; }
              );
            }
            return requestPromise;
          },
          enable: function () {
            return evmProvider.request({ method: 'eth_requestAccounts' });
          },
          on: function (event, listener) {
            if (typeof listener !== 'function') return evmProvider;
            if (!evmListeners[event]) evmListeners[event] = [];
            if (evmListeners[event].indexOf(listener) < 0) evmListeners[event].push(listener);
            return evmProvider;
          },
          addListener: function (event, listener) {
            return evmProvider.on(event, listener);
          },
          removeListener: function (event, listener) {
            var list = evmListeners[event] || [];
            var index = list.indexOf(listener);
            if (index >= 0) list.splice(index, 1);
            return evmProvider;
          },
          removeAllListeners: function (event) {
            if (event) evmListeners[event] = [];
            else evmListeners = {};
            return evmProvider;
          },
          sendAsync: function (payload, callback) {
            var request = payload || {};
            evmProvider.request({ method: request.method, params: request.params || [] })
              .then(function (result) {
                callback(null, { jsonrpc: '2.0', id: request.id, result: result });
              })
              .catch(function (error) {
                callback(error, { jsonrpc: '2.0', id: request.id, error: { code: error.code, message: error.message } });
              });
          },
          send: function (payloadOrMethod, paramsOrCallback) {
            if (typeof payloadOrMethod === 'string') {
              return evmProvider.request({ method: payloadOrMethod, params: Array.isArray(paramsOrCallback) ? paramsOrCallback : [] });
            }
            if (typeof paramsOrCallback === 'function') {
              evmProvider.sendAsync(payloadOrMethod, paramsOrCallback);
              return;
            }
            return evmProvider.request(payloadOrMethod || {});
          }
        };

        window.poctraEthereum = evmProvider;
        window.__poctraEvmProviderInstalled = true;
        exposeEvmProvider(evmProvider);

        function announceEvmProvider(event) {
          if (event && event.type === 'eip6963:requestProvider') evmDiscoveryObserved = true;
          poctraDebugLog(event && event.type === 'eip6963:requestProvider'
            ? 'eip6963 request observed'
            : 'eip6963 replay announcement');
          announceEvm(evmProvider);
        }
        window.addEventListener('eip6963:requestProvider', announceEvmProvider);
        document.addEventListener('DOMContentLoaded', announceEvmProvider);
        window.addEventListener('load', announceEvmProvider);
        window.addEventListener('focus', announceEvmProvider);
        announceEvmProvider();
        dispatchEventSafely(new Event('ethereum#initialized'));
        document.addEventListener('click', function (event) {
          if (!evmDiscoveryObserved || evmSelectedAddress || evmAccountsRequestPromise) return;
          var target = event && event.target;
          var control = null;
          try { control = target && target.closest ? target.closest('button, a, [role="button"]') : null; } catch (_) {}
          if (!control) return;
          var rawLabel = '';
          try { rawLabel = control.getAttribute('aria-label') || control.innerText || control.textContent || ''; } catch (_) {}
          var label = String(rawLabel).toLowerCase().replace(/\s+/g, ' ').trim();
          if (label.length > 80) return;
          var isConnectIntent = label === 'connect' ||
            label === 'connect wallet' ||
            label === 'connect a wallet' ||
            label === 'connect evm wallet' ||
            label === 'browser wallet' ||
            label === 'evm wallets' ||
            label === 'get started';
          if (!isConnectIntent) return;
          poctraDebugLog('trusted wallet-connect intent label=' + label);
          window.setTimeout(function () {
            evmProvider.request({ method: 'eth_requestAccounts' }).then(function () {
              if (window.__poctraDismissConnectOverlays) window.__poctraDismissConnectOverlays();
            }).catch(function () {});
          }, 0);
        }, true);
        // Establish provider-to-chain connectivity without requesting or
        // exposing an account. Connector libraries commonly inspect
        // isConnected() and chainId before presenting an injected wallet.
        window.setTimeout(function () { refreshEvmChain(); }, 0);
        return evmProvider;
      }
      window.__poctraInstallEvmProvider = installEvmProvider;
      function installProviderGlobals(provider, info) {
        try {
          Object.defineProperty(window, 'octra', { value: provider, configurable: true, writable: true });
        } catch (_) {
          window.octra = provider;
        }
        window.poctra = provider;
        window.__poctraProviderInfo = info;
        window.__poctraProviderInstalled = true;
        try {
          var providers = Array.isArray(window.octraProviders) ? window.octraProviders.slice() : [];
          if (providers.indexOf(provider) < 0) providers.push(provider);
          window.octraProviders = providers;
        } catch (_) {}
      }
      if (window.__poctraProviderInstalled && window.octra) {
        var existingInfo = window.__poctraProviderInfo || octraProviderInfo;
        installProviderGlobals(window.octra, existingInfo);
        announceProvider(window.octra, existingInfo);
        installEvmProvider();
        return;
      }

      var nextId = 1;
      var pending = {};
      var listeners = {};
      var connected = false;
      function requestPermissions(params) {
        var request = Array.isArray(params) ? params[0] : params;
        if (request && Array.isArray(request.permissions)) return request.permissions.slice();
        return ['read_address', 'read_balance'];
      }
      var info = octraProviderInfo;

      window.__poctraProviderInfo = info;
      window.__poctraResolve = function (id, result) {
        var entry = pending[id];
        if (!entry) return;
        delete pending[id];
        if ((entry.method === 'octra_requestAccounts' || entry.method === 'octra_accounts') && Array.isArray(result)) {
          connected = result.length > 0;
          emit('accountsChanged', result);
          if (connected) {
            if (entry.method === 'octra_requestAccounts') {
              emit('permissionsChanged', requestPermissions(entry.params));
            }
            provider.request({ method: 'octra_networkInfo' }).then(function (networkInfo) {
              emit('connect', { networkId: networkInfo && networkInfo.id, networkInfo: networkInfo });
            }).catch(function () {
              emit('connect', { networkId: null, accounts: result });
            });
          }
        }
        if (entry.method === 'octra_disconnect') {
          connected = false;
          emit('accountsChanged', []);
          emit('permissionsChanged', []);
          var disconnectError = new Error('Disconnected');
          disconnectError.name = 'OctraProviderError';
          disconnectError.code = 4900;
          emit('disconnect', disconnectError);
        }
        entry.resolve(result);
      };
      window.__poctraReject = function (id, code, message) {
        var entry = pending[id];
        if (!entry) return;
        delete pending[id];
        var error = new Error(message || 'poctra request failed');
        error.name = 'OctraProviderError';
        error.code = code;
        entry.reject(error);
      };

      var provider = {
        isOctra: true,
        isPoctra: true,
        isOctraWallet: true,
        providerId: 'poctra',
        name: 'poctra',
        rdns: 'id.octra.poctra',
        version: '1.0.0',
        get isConnected() { return connected; },
        request: function (args) {
          poctraDebugLog('RFC-O-1 provider request method=' + String(args && args.method || ''));
          return new Promise(function (resolve, reject) {
            if (!args || !args.method) {
              var invalid = new Error('Octra provider method is required');
              invalid.name = 'OctraProviderError';
              invalid.code = 4200;
              reject(invalid);
              return;
            }
            var id = String(nextId++);
            pending[id] = { resolve: resolve, reject: reject, method: args.method, params: args.params };
            try {
              window.PoctraAndroid.request(JSON.stringify({
                id: id,
                bridgeToken: window.__poctraBridgeToken || bridgeToken,
                origin: window.location.origin || window.location.href,
                method: args.method,
                params: args.params === undefined ? [] : args.params
              }));
            } catch (error) {
              delete pending[id];
              reject(error);
            }
          });
        },
        on: function (event, listener) {
          if (!listeners[event]) listeners[event] = [];
          listeners[event].push(listener);
          return provider;
        },
        addListener: function (event, listener) {
          return provider.on(event, listener);
        },
        removeListener: function (event, listener) {
          var list = listeners[event] || [];
          var index = list.indexOf(listener);
          if (index >= 0) list.splice(index, 1);
          return provider;
        },
        removeAllListeners: function (event) {
          if (event) listeners[event] = [];
          else listeners = {};
          return provider;
        },
        connect: function (params) {
          return provider.request({
            method: 'octra_requestAccounts',
            params: params === undefined ? [{ permissions: ['read_address', 'read_balance', 'read_public_key', 'contract_calls'] }] : params
          });
        },
        requestAccounts: function (params) {
          return provider.connect(params);
        },
        enable: function () {
          return provider.connect();
        },
        disconnect: function () {
          return provider.request({ method: 'octra_disconnect' });
        }
      };
      function emit(event) {
        var args = Array.prototype.slice.call(arguments, 1);
        var list = (listeners[event] || []).slice();
        for (var i = 0; i < list.length; i++) {
          try { list[i].apply(provider, args); } catch (_) {}
        }
      }

      installProviderGlobals(provider, info);

      function announce() {
        poctraDebugLog('RFC-O-1 request/replay observed');
        announceProvider(provider, info);
      }
      window.addEventListener('octra:requestProvider', announce);
      document.addEventListener('DOMContentLoaded', announce);
      window.addEventListener('load', announce);
      window.addEventListener('focus', announce);
      announce();
      setTimeout(announce, 0);
      setTimeout(announce, 250);
      setTimeout(announce, 1000);
      setTimeout(announce, 2500);
      setTimeout(announce, 5000);
      setTimeout(announce, 9000);
      setTimeout(announce, 15000);
      installEvmProvider();
    })();
""".trimIndent()

@Composable
private fun DashboardBottomNav(
    selected: DashboardTab,
    onSelect: (DashboardTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = Radius.md, topEnd = Radius.md))
            .background(SurfaceStrong)
            .navigationBarsPadding()
            .padding(start = Space.x5, top = Space.x3, end = Space.x5),
        horizontalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        BottomNavItem(
            label = "Overview",
            glyph = Glyph.Overview,
            selected = selected == DashboardTab.Overview,
            onClick = { onSelect(DashboardTab.Overview) },
            modifier = Modifier.weight(1f),
        )
        BottomNavItem(
            label = "Assets",
            glyph = Glyph.Assets,
            selected = selected == DashboardTab.Assets,
            onClick = { onSelect(DashboardTab.Assets) },
            modifier = Modifier.weight(1f),
        )
        BottomNavItem(
            label = "Trade",
            glyph = Glyph.Trade,
            selected = selected == DashboardTab.Trade,
            onClick = { onSelect(DashboardTab.Trade) },
            modifier = Modifier.weight(1f),
        )
        BottomNavItem(
            label = "Explore",
            glyph = Glyph.Explore,
            selected = selected == DashboardTab.Explore,
            onClick = { onSelect(DashboardTab.Explore) },
            modifier = Modifier.weight(1f),
        )
        BottomNavItem(
            label = "Privacy",
            glyph = Glyph.Privacy,
            selected = selected == DashboardTab.Privacy,
            onClick = { onSelect(DashboardTab.Privacy) },
            modifier = Modifier.weight(1f),
            accentColor = PrivacyPrimary,
            iconicPrivacy = true,
        )
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    glyph: Glyph,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = SurfaceRaised,
    iconicPrivacy: Boolean = false,
) {
    val itemScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.98f,
        animationSpec = tween(ScrimMotionMillis, easing = FastOutSlowInEasing),
        label = "bottomNavItemScale",
    )
    Box(
        modifier = modifier
            .height(50.dp)
            .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            }
            .clip(RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick),
    ) {
        if (iconicPrivacy && !selected) {
            PrivacyNavOrbit(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(width = 52.dp, height = 42.dp),
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = Space.x1, top = Space.x2, end = Space.x1, bottom = Space.x3),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GlyphIcon(
                glyph,
                modifier = Modifier.size(if (iconicPrivacy) 18.dp else 16.dp),
                color = if (selected) accentColor else TextSecondary,
            )
            Spacer(Modifier.height(Space.x1))
            Text(
                label,
                color = if (selected) accentColor else TextSecondary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .size(width = if (iconicPrivacy) 28.dp else 20.dp, height = 3.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(if (selected) accentColor else Color.Transparent),
        )
    }
}

@Composable
private fun PrivacyNavOrbit(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "privacyNavOrbit")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2_400, easing = LinearEasing),
        ),
        label = "privacyNavOrbitProgress",
    )
    Canvas(modifier = modifier) {
        val strokeWidth = 1.25.dp.toPx()
        val inset = strokeWidth / 2f
        val dashLength = 12.dp.toPx()
        val perimeter = (size.width + size.height) * 2f
        val pathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(dashLength, perimeter),
            phase = -progress * (dashLength + perimeter),
        )
        drawRoundRect(
            color = PrivacyPrimary.copy(alpha = 0.12f),
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            cornerRadius = CornerRadius(Radius.md.toPx()),
            style = Stroke(width = strokeWidth),
        )
        drawRoundRect(
            color = PrivacyPrimary.copy(alpha = 0.9f),
            topLeft = Offset(inset, inset),
            size = Size(size.width - strokeWidth, size.height - strokeWidth),
            cornerRadius = CornerRadius(Radius.md.toPx()),
            style = Stroke(width = strokeWidth, pathEffect = pathEffect),
        )
    }
}

@Composable
private fun WalletManagementSection(controller: PoctraController) {
    val vault = controller.vault ?: return
    Text("Wallets", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
    Spacer(Modifier.height(Space.x6))
    Card(
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(Modifier.padding(Space.x7), verticalArrangement = Arrangement.spacedBy(Space.x6)) {
            vault.wallets.forEachIndexed { index, item ->
                WalletRow(
                    title = "Wallet ${index + 1}",
                    address = item.address,
                    active = item.address == vault.activeAddress,
                    canRemove = vault.wallets.size > 1,
                    onSelect = { controller.switchWallet(item.address) },
                    onRemove = { controller.removeWallet(item.address) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x6)) {
                PoctraButton(
                    text = "Create",
                    onClick = controller::createWallet,
                    modifier = Modifier.weight(1f),
                    enabled = !controller.busy,
                    loading = controller.busy,
                    leadingGlyph = Glyph.Plus,
                )
                PoctraButton(
                    text = "Import",
                    onClick = controller::openImportWallet,
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.Ghost,
                    enabled = !controller.busy,
                    leadingGlyph = Glyph.Import,
                )
            }
        }
    }
}

@Composable
private fun WalletRow(
    title: String,
    address: String,
    active: Boolean,
    canRemove: Boolean,
    onSelect: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (active) SurfaceRaised.copy(alpha = 0.18f) else SurfaceMuted.copy(alpha = 0.72f))
            .clickable(onClick = onSelect)
            .padding(Space.x6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (active) "$title - Active" else title,
                color = TextTertiary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(shortAddress(address), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
        if (canRemove) {
            Text(
                "Remove",
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.pill))
                    .clickable(onClick = onRemove)
                    .padding(horizontal = Space.x5, vertical = Space.x3),
                color = ErrorRed,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun WalletActionButton(
    label: String,
    glyph: Glyph,
    modifier: Modifier,
    disabled: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && !disabled) 0.988f else 1f,
        animationSpec = tween(90, easing = FastOutSlowInEasing),
        label = "walletActionPress",
    )
    Box(
        modifier = modifier
            .height(62.dp)
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .clip(RoundedCornerShape(Radius.md))
            .background(if (disabled) SurfaceMuted.copy(alpha = 0.48f) else SurfaceMuted.copy(alpha = 0.82f))
            .clickable(
                enabled = !disabled,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(if (disabled) SurfaceMuted.copy(alpha = 0.48f) else SurfaceMuted.copy(alpha = 0.82f))
                .padding(horizontal = Space.x3),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(if (disabled) SurfaceBase.copy(alpha = 0.45f) else SurfaceRaised.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(
                    glyph,
                    modifier = Modifier.size(15.dp),
                    color = if (disabled) TextSecondary else SurfaceRaised,
                )
            }
            Spacer(Modifier.width(Space.x3))
            Text(
                label,
                color = if (disabled) TextSecondary else TextTertiary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun EmptyPanel(text: String, compact: Boolean = false) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (compact) Space.x6 else Space.x8,
                vertical = if (compact) Space.x3 else Space.x6,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = TextSecondary,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SendScreen(controller: PoctraController) {
    var recipient by remember { mutableStateOf("") }
    var selectedRecipientName by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var messageExpanded by remember { mutableStateOf(false) }
    var feeOu by remember { mutableStateOf(controller.recommendedFeeOu.toString()) }
    var feeEdited by remember { mutableStateOf(false) }
    var recipientError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var feeError by remember { mutableStateOf<String?>(null) }
    var showReview by remember { mutableStateOf(false) }
    val recipientLookup = rememberOctraRecipientLookup(controller, recipient)
    val recipientForReview = recipientLookup.resolution?.address ?: recipient

    LaunchedEffect(Unit) {
        controller.consumeSelectedRecipient()?.let {
            recipient = it
            selectedRecipientName = controller.consumeSelectedRecipientIdentityName()
        }
    }

    LaunchedEffect(controller.recommendedFeeOu) {
        if (!feeEdited) feeOu = controller.recommendedFeeOu.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DetailScreenHeader(
            title = "Send OCT",
            description = "Send OCT to foo.oct or an Octra address.",
            onBack = controller::closeSend,
            backEnabled = !controller.busy,
        )
        Spacer(Modifier.height(Space.x8))
        RecipientLookupField(
            value = recipient,
            onValueChange = {
                recipient = it.trim()
                selectedRecipientName = null
                recipientError = null
            },
            label = "Recipient address",
            placeholder = "foo.oct, app.foo.oct or oct address",
            lookupState = recipientLookup,
            glyph = Glyph.AddressBook,
            onClear = {
                recipient = ""
                selectedRecipientName = null
                recipientError = null
            },
            directPreviewName = selectedRecipientName,
            showDirectOctraAddressPreview = true,
            isError = recipientError != null,
            supportingText = recipientError,
            trailingContent = {
                AddressBookFieldButton(
                    onClick = controller::openAddressBookForSend,
                    enabled = !controller.busy,
                )
            },
        )
        Spacer(Modifier.height(Space.x6))
        PoctraTextField(
            value = amount,
            onValueChange = {
                amount = it.filter { char -> char.isDigit() || char == '.' || char == ',' }
                amountError = null
            },
            label = "Amount OCT",
            singleLine = true,
            keyboardType = KeyboardType.Decimal,
            isError = amountError != null,
            supportingText = amountError,
        )
        Spacer(Modifier.height(Space.x4))
        val currentFeeOu = feeOu.toLongOrNull() ?: controller.recommendedFeeOu
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (controller.tokenBalance.isLoading) {
                    "Available: -- OCT"
                } else {
                    "Available: ${formatOctAmount(controller.availableBalance)} OCT"
                },
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Max",
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable(enabled = !controller.tokenBalance.isLoading && controller.availableBalance > 0.0) {
                        amount = controller.maxSendAmount(currentFeeOu)
                        amountError = null
                    }
                    .padding(horizontal = Space.x4, vertical = Space.x2),
                color = if (!controller.tokenBalance.isLoading && controller.availableBalance > 0.0) SurfaceRaised else TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(Space.x6))
        OctraNetworkFeePanel(
            recommendedFeeOu = controller.recommendedFeeOu,
            feeOu = feeOu,
            customSelected = feeEdited && feeOu.toLongOrNull() != controller.recommendedFeeOu * 2,
            loading = controller.feeLoading,
            error = feeError,
            onAuto = {
                feeEdited = false
                feeOu = controller.recommendedFeeOu.toString()
                feeError = null
            },
            onFast = {
                feeEdited = true
                feeOu = (controller.recommendedFeeOu * 2).toString()
                feeError = null
            },
            onCustom = {
                feeEdited = true
                feeError = null
            },
            onFeeChange = {
                feeOu = it.filter(Char::isDigit).take(12)
                feeEdited = true
                feeError = null
            },
            onRefresh = {
                feeEdited = false
                feeError = null
                controller.refreshRecommendedFee()
            },
            optionVerticalPadding = Space.x4,
        )
        val sendRisks = controller.octraRecipientRiskItems(
            recipientInput = recipient,
            resolvedAddress = recipientLookup.resolution?.address,
            amount = amount,
        )
        if (sendRisks.isNotEmpty()) {
            Spacer(Modifier.height(Space.x6))
            Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
                sendRisks.forEach { SendPreflightRow(it) }
            }
        }
        Spacer(Modifier.height(Space.x6))
        if (!messageExpanded) {
            Row(
                modifier = Modifier
                    .align(Alignment.Start)
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable(enabled = !controller.busy) { messageExpanded = true }
                    .padding(vertical = Space.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(Glyph.Plus, modifier = Modifier.size(13.dp), color = SurfaceRaised)
                Spacer(Modifier.width(Space.x2))
                Text("Add Message", color = SurfaceRaised, style = MaterialTheme.typography.labelMedium)
            }
        } else {
            Spacer(Modifier.height(Space.x6))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable(enabled = !controller.busy) {
                            message = ""
                            messageExpanded = false
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.Close, modifier = Modifier.size(12.dp), color = TextSecondary)
                }
            }
            Spacer(Modifier.height(Space.x1))
            PoctraTextField(
                value = message,
                onValueChange = { message = it.take(160) },
                label = "Message (optional)",
                singleLine = false,
                minLines = 3,
            )
        }
        Spacer(Modifier.height(Space.x8))
        PoctraButton(
            text = "Send",
            onClick = {
                val recipientForSubmit = recipientLookup.resolution?.address ?: recipient
                recipientError = null
                amountError = null
                feeError = null
                when {
                    recipient.isBlank() -> recipientError = "Recipient address is required."
                    recipientLookup.loading -> recipientError = "Wait until recipient lookup finishes."
                    isValidOctraIdentityName(recipient) && recipientLookup.resolution == null -> {
                        recipientError = recipientLookup.error ?: "Resolve recipient first."
                    }
                    !isValidOctraRecipientInput(recipientForSubmit) -> {
                        recipientError = "Enter foo.oct or a valid Octra recipient address."
                    }
                    amount.isBlank() -> amountError = "Amount is required."
                    amount.replace(",", ".").toDoubleOrNull() == null -> amountError = "Enter a valid OCT amount."
                    feeOu.isBlank() -> feeError = "Fee is required."
                    feeOu.toLongOrNull() == null -> feeError = "Enter a valid fee in OU."
                    else -> showReview = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            loading = controller.busy,
            enabled = !controller.busy,
            leadingGlyph = Glyph.Send,
        )
    }
    if (showReview) {
        val reviewFeeOu = feeOu.toLongOrNull() ?: controller.recommendedFeeOu
        OctraSendReviewSheet(
            title = "Review Send",
            assetLabel = "OCT",
            senderIdentityName = controller.wallet?.address?.let(controller::walletIdentityDomain),
            senderAddress = controller.wallet?.address,
            recipientIdentityName = recipientLookup.resolution?.name
                ?: selectedRecipientName
                ?: controller.identityNameForAddress(recipientForReview),
            recipient = recipientForReview,
            amount = "${amount.replace(",", ".")} OCT",
            fee = "$reviewFeeOu OU ~= ${ouToOct(reviewFeeOu)} OCT",
            message = if (messageExpanded) message.takeIf { it.isNotBlank() } else null,
            busy = controller.busy,
            onDismiss = { if (!controller.busy) showReview = false },
            onProceed = {
                showReview = false
                controller.sendOct(
                    recipientForReview,
                    amount,
                    if (messageExpanded) message else "",
                    feeOu,
                )
            },
        )
    }
}

@Composable
private fun OctraNetworkFeePanel(
    recommendedFeeOu: Long,
    feeOu: String,
    customSelected: Boolean,
    loading: Boolean,
    error: String?,
    onAuto: () -> Unit,
    onFast: () -> Unit,
    onCustom: () -> Unit,
    onFeeChange: (String) -> Unit,
    onRefresh: () -> Unit,
    optionVerticalPadding: Dp = Space.x3,
) {
    val current = feeOu.toLongOrNull()
    val selected = when {
        customSelected -> BridgeFeePreset.Custom
        current == recommendedFeeOu * 2 -> BridgeFeePreset.Market
        else -> BridgeFeePreset.Medium
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceMuted.copy(alpha = 0.54f)),
    ) {
        Column(Modifier.padding(Space.x5)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Network Fee", color = TextTertiary, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (loading) "Fetching recommended fee..." else "Recommended by active RPC",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(SurfaceRaised.copy(alpha = 0.10f))
                        .clickable(enabled = !loading, onClick = onRefresh),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loading) CircularProgressIndicator(Modifier.size(14.dp), color = SurfaceRaised, strokeWidth = 2.dp)
                    else GlyphIcon(Glyph.Refresh, Modifier.size(15.dp), SurfaceRaised)
                }
            }
            Spacer(Modifier.height(Space.x5))
            BridgeFeePresetRow(
                selected = selected,
                presets = listOf(BridgeFeePreset.Medium, BridgeFeePreset.Market, BridgeFeePreset.Custom),
                titles = mapOf(
                    BridgeFeePreset.Medium to "Auto",
                    BridgeFeePreset.Market to "Fast",
                    BridgeFeePreset.Custom to "Custom",
                ),
                labels = mapOf(
                    BridgeFeePreset.Medium to ouToOct(recommendedFeeOu),
                    BridgeFeePreset.Market to ouToOct(recommendedFeeOu * 2),
                    BridgeFeePreset.Custom to current?.takeIf { it > 0L }
                        ?.let { "${ouToOct(it)} OCT" }
                        .orEmpty()
                        .ifBlank { "Set fee" },
                ),
                suffix = "OCT",
                verticalPadding = optionVerticalPadding,
                onSelect = {
                    when (it) {
                        BridgeFeePreset.Medium -> onAuto()
                        BridgeFeePreset.Market -> onFast()
                        BridgeFeePreset.Custom -> onCustom()
                        else -> Unit
                    }
                },
            )
            if (customSelected) {
                Spacer(Modifier.height(Space.x4))
                PoctraTextField(
                    value = feeOu,
                    onValueChange = onFeeChange,
                    label = "Custom fee (OU)",
                    keyboardType = KeyboardType.Number,
                    singleLine = true,
                    isError = error != null || current?.let { it <= 0L } != false,
                    supportingText = error ?: if (current?.let { it > 0L } == true) null else "Enter a valid fee.",
                )
            } else if (error != null) {
                Spacer(Modifier.height(Space.x3))
                StateBanner(error, isError = true, compact = true)
            }
        }
    }
}

@Composable
private fun SendPreflightRow(item: ConsumerRiskItem) {
    val color = sendPreflightColor(item.level)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.x1, vertical = Space.x2),
        verticalAlignment = Alignment.Top,
    ) {
        GlyphIcon(
            glyph = if (item.level == ConsumerRiskLevel.Safe) Glyph.Check else Glyph.Info,
            modifier = Modifier
                .offset(y = (-1).dp)
                .size(14.dp),
            color = color,
        )
        Spacer(Modifier.width(Space.x4))
        Column(Modifier.weight(1f)) {
            Text(
                text = item.title,
                color = color,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.x1))
            Text(
                text = item.description,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun sendPreflightColor(level: ConsumerRiskLevel): Color = when (level) {
    ConsumerRiskLevel.Critical -> ErrorRed
    ConsumerRiskLevel.Warning -> Warning
    ConsumerRiskLevel.Notice,
    ConsumerRiskLevel.Safe -> InfoBlue
}

@Composable
private fun SendAssetScreen(controller: PoctraController) {
    val token = controller.selectedOctraToken ?: run {
        LoadingScreen()
        return
    }
    var recipient by remember { mutableStateOf("") }
    var selectedRecipientName by remember { mutableStateOf<String?>(null) }
    var amount by remember { mutableStateOf("") }
    var feeOu by remember { mutableStateOf(controller.recommendedFeeOu.toString()) }
    var feeEdited by remember { mutableStateOf(false) }
    var recipientError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var feeError by remember { mutableStateOf<String?>(null) }
    var showReview by remember { mutableStateOf(false) }
    val recipientLookup = rememberOctraRecipientLookup(controller, recipient)
    val recipientForReview = recipientLookup.resolution?.address ?: recipient

    LaunchedEffect(Unit) {
        controller.consumeSelectedRecipient()?.let {
            recipient = it
            selectedRecipientName = controller.consumeSelectedRecipientIdentityName()
        }
    }

    LaunchedEffect(controller.recommendedFeeOu) {
        if (!feeEdited) feeOu = controller.recommendedFeeOu.toString()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DetailScreenHeader(
            title = "Send ${token.symbol}",
            description = "Transfer ${token.symbol} to foo.oct or an Octra address.",
            onBack = controller::closeSendAsset,
            backEnabled = !controller.busy,
        )
        Spacer(Modifier.height(Space.x8))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.sm),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        ) {
            Row(
                modifier = Modifier.padding(Space.x6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(SurfaceMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(token.symbol.take(2).uppercase(), color = SurfaceRaised, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(Modifier.width(Space.x5))
                Column(Modifier.weight(1f)) {
                    Text(token.name, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(token.address, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(token.formattedBalance, color = TextTertiary, fontWeight = FontWeight.SemiBold)
                    Text(token.symbol, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(Space.x7))
        RecipientLookupField(
            value = recipient,
            onValueChange = {
                recipient = it.trim()
                selectedRecipientName = null
                recipientError = null
            },
            label = "Recipient address",
            placeholder = "foo.oct, app.foo.oct or oct address",
            lookupState = recipientLookup,
            glyph = Glyph.AddressBook,
            onClear = {
                recipient = ""
                selectedRecipientName = null
                recipientError = null
            },
            directPreviewName = selectedRecipientName,
            showDirectOctraAddressPreview = true,
            isError = recipientError != null,
            supportingText = recipientError,
            trailingContent = {
                AddressBookFieldButton(
                    onClick = controller::openAddressBookForAssetSend,
                    enabled = !controller.busy,
                )
            },
        )
        Spacer(Modifier.height(Space.x6))
        PoctraTextField(
            value = amount,
            onValueChange = {
                amount = it.filter { char -> char.isDigit() || char == '.' || char == ',' }
                amountError = null
            },
            label = "Amount ${token.symbol}",
            singleLine = true,
            keyboardType = KeyboardType.Decimal,
            isError = amountError != null,
            supportingText = amountError,
        )
        Spacer(Modifier.height(Space.x4))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Available: ${token.formattedBalance} ${token.symbol}",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Max",
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable(enabled = token.balanceRaw != "0") {
                        amount = controller.maxOctraTokenAmount(token)
                        amountError = null
                    }
                    .padding(horizontal = Space.x4, vertical = Space.x2),
                color = if (token.balanceRaw != "0") SurfaceRaised else TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(Space.x6))
        OctraNetworkFeePanel(
            recommendedFeeOu = controller.recommendedFeeOu,
            feeOu = feeOu,
            customSelected = feeEdited && feeOu.toLongOrNull() != controller.recommendedFeeOu * 2,
            loading = controller.feeLoading,
            error = feeError,
            onAuto = {
                feeEdited = false
                feeOu = controller.recommendedFeeOu.toString()
                feeError = null
            },
            onFast = {
                feeEdited = true
                feeOu = (controller.recommendedFeeOu * 2).toString()
                feeError = null
            },
            onCustom = {
                feeEdited = true
                feeError = null
            },
            onFeeChange = {
                feeOu = it.filter(Char::isDigit).take(12)
                feeEdited = true
                feeError = null
            },
            onRefresh = {
                feeEdited = false
                feeError = null
                controller.refreshRecommendedFee()
            },
        )
        Spacer(Modifier.height(Space.x5))
        StateBanner(
            "Token transfer uses a contract call, so OCT is required for gas.",
            isError = false,
        )
        val assetSendRisks = controller.octraRecipientRiskItems(
            recipientInput = recipient,
            resolvedAddress = recipientLookup.resolution?.address,
            amount = "",
        )
        if (assetSendRisks.isNotEmpty()) {
            Spacer(Modifier.height(Space.x6))
            Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
                assetSendRisks.forEach { SendPreflightRow(it) }
            }
        }
        Spacer(Modifier.height(Space.x8))
        PoctraButton(
            text = "Send ${token.symbol}",
            onClick = {
                val recipientForSubmit = recipientLookup.resolution?.address ?: recipient
                recipientError = null
                amountError = null
                feeError = null
                when {
                    recipient.isBlank() -> recipientError = "Recipient address is required."
                    recipientLookup.loading -> recipientError = "Wait until recipient lookup finishes."
                    isValidOctraIdentityName(recipient) && recipientLookup.resolution == null -> {
                        recipientError = recipientLookup.error ?: "Resolve recipient first."
                    }
                    !isValidOctraRecipientInput(recipientForSubmit) -> recipientError = "Enter foo.oct or a valid Octra address."
                    amount.isBlank() -> amountError = "Amount is required."
                    amount.replace(",", ".").toDoubleOrNull() == null -> amountError = "Enter a valid ${token.symbol} amount."
                    feeOu.isBlank() -> feeError = "Fee is required."
                    feeOu.toLongOrNull() == null -> feeError = "Enter a valid fee in OU."
                    else -> showReview = true
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !controller.busy,
            loading = controller.busy,
            leadingGlyph = Glyph.Send,
        )
    }
    if (showReview) {
        val reviewFeeOu = feeOu.toLongOrNull() ?: controller.recommendedFeeOu
        OctraSendReviewSheet(
            title = "Review ${token.symbol} Send",
            assetLabel = token.symbol,
            senderIdentityName = controller.wallet?.address?.let(controller::walletIdentityDomain),
            senderAddress = controller.wallet?.address,
            recipientIdentityName = recipientLookup.resolution?.name
                ?: selectedRecipientName
                ?: controller.identityNameForAddress(recipientForReview),
            recipient = recipientForReview,
            amount = "${amount.replace(",", ".")} ${token.symbol}",
            fee = "$reviewFeeOu OU ~= ${ouToOct(reviewFeeOu)} OCT",
            message = "Contract: ${token.address}",
            busy = controller.busy,
            onDismiss = { if (!controller.busy) showReview = false },
            onProceed = {
                showReview = false
                controller.sendOctraToken(token, recipientForReview, amount, feeOu)
            },
        )
    }
}

@Composable
private fun OctraSendReviewSheet(
    title: String,
    assetLabel: String,
    senderIdentityName: String?,
    senderAddress: String?,
    recipientIdentityName: String?,
    recipient: String,
    amount: String,
    fee: String,
    message: String?,
    busy: Boolean,
    onDismiss: () -> Unit,
    onProceed: () -> Unit,
) {
    var sheetVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sheetVisible = true }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (sheetVisible) 0.52f else 0f,
        animationSpec = tween(ScrimMotionMillis),
        label = "octraSendReviewScrim",
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(enabled = !busy, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val sheetMinHeight = maxHeight * 0.42f
        val sheetMaxHeight = maxHeight * 0.82f
        AnimatedVisibility(
            visible = sheetVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(SheetEnterMillis, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(ScrimMotionMillis)),
            exit = slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(SheetExitMillis, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(SheetExitMillis)),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = sheetMinHeight, max = sheetMaxHeight)
                    .clickable(onClick = {})
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(topStart = Radius.md, topEnd = Radius.md),
                colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Space.x7, vertical = Space.x6),
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(BorderLine)
                            .align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(Space.x6))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(Radius.md))
                                .background(SurfaceRaised.copy(alpha = 0.18f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            GlyphIcon(Glyph.Send, modifier = Modifier.size(20.dp), color = SurfaceRaised)
                        }
                        Spacer(Modifier.width(Space.x5))
                        Column(Modifier.weight(1f)) {
                            Text(title, color = TextTertiary, style = MaterialTheme.typography.titleLarge)
                            Text(assetLabel, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(Space.x5))
                    ReviewProcessFlow(
                        source = ReviewFlowEndpoint(
                            label = "From",
                            address = senderAddress.orEmpty(),
                            identityName = senderIdentityName,
                        ),
                        destination = ReviewFlowEndpoint(
                            label = "To",
                            address = recipient,
                            identityName = recipientIdentityName,
                        ),
                        actionLabel = "Send",
                        actionGlyph = Glyph.Go,
                    )
                    Spacer(Modifier.height(Space.x6))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(Radius.md),
                        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
                    ) {
                        Column(Modifier.padding(horizontal = Space.x6, vertical = Space.x3)) {
                            DetailLine("Amount", amount)
                            DetailSeparator()
                            DetailLine("Network Fee", fee)
                            message?.takeIf { it.isNotBlank() }?.let {
                                DetailSeparator()
                                DetailLine("Message", it)
                            }
                        }
                    }
                    Spacer(Modifier.height(Space.x6))
                    StateBanner(
                        text = "Review the recipient and amount before signing. This action cannot be reversed after broadcast.",
                        isError = false,
                        compact = true,
                    )
                    Spacer(Modifier.height(Space.x6))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
                        PoctraButton(
                            text = "Cancel",
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                            variant = ButtonVariant.Ghost,
                            enabled = !busy,
                            fullHeight = true,
                        )
                        PoctraButton(
                            text = "Proceed",
                            onClick = onProceed,
                            modifier = Modifier.weight(1f),
                            enabled = !busy,
                            loading = busy,
                            leadingGlyph = Glyph.Check,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceiveScreen(controller: PoctraController) {
    val wallet = controller.wallet
    val clipboard = LocalClipboardManager.current

    if (wallet == null) {
        LoadingScreen()
        return
    }

    val identityName = controller.walletIdentityDomain(wallet.address)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeReceive)
            Spacer(Modifier.width(Space.x6))
            Text("Receive OCT", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(34.dp))
        Image(
            painter = painterResource(com.poctra.wallet.R.drawable.poctra_logo),
            contentDescription = "poctra",
            modifier = Modifier.size(74.dp),
        )
        Spacer(Modifier.height(Space.x8))
        Text("Your wallet address", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        identityName?.let { name ->
            Spacer(Modifier.height(Space.x4))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.md))
                    .background(InfoBlue.copy(alpha = 0.12f))
                    .padding(horizontal = Space.x5, vertical = Space.x3),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Space.x3),
            ) {
                GlyphIcon(Glyph.OctraIdentity, modifier = Modifier.size(15.dp), color = InfoBlue)
                Text(name, color = TextTertiary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text("Octra Identity", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
        }
        Spacer(Modifier.height(Space.x4))
        Text(
            "Share this address to receive OCT on the selected network.",
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.x8))
        QrCodeView(text = wallet.address)
        Spacer(Modifier.height(Space.x7))
        Card(
            modifier = Modifier
                .fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceMuted.copy(alpha = 0.62f)),
        ) {
            Text(
                text = wallet.address,
                color = TextTertiary,
                modifier = Modifier.padding(Space.x7),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(Space.x7))
        PoctraButton(
            text = "Copy Address",
            onClick = {
                clipboard.setText(AnnotatedString(wallet.address))
                controller.showMessage("Address copied.")
            },
            modifier = Modifier.fillMaxWidth(),
            leadingGlyph = Glyph.Copy,
        )
    }
}

@Composable
private fun BridgeScreen(controller: PoctraController) {
    val wallet = controller.wallet
    val direction = controller.bridgeDirection
    var amount by remember(direction) { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(false) }
    var showReview by remember { mutableStateOf(false) }
    var reviewStarted by remember { mutableStateOf(false) }
    val evmAddress = controller.bridgeEvmAddress()
    val networkWarning = controller.bridgeNetworkWarning
    val visibleBridgeStatus = if (networkWarning == null) controller.bridgeStatus else BridgeStatus(error = networkWarning)
    val reviewReceiverAddress = if (direction == BridgeDirection.OctToWoct) evmAddress else wallet?.address.orEmpty()

    if (wallet == null) {
        LoadingScreen()
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x7),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeBridge, enabled = !controller.bridgeBusy || !controller.bridgeDialogVisible)
            Spacer(Modifier.width(Space.x6))
            Text("Bridge", color = TextTertiary, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            BridgeHeaderAction(
                glyph = Glyph.Info,
                label = "Info",
                onClick = { showInfo = true },
                enabled = !controller.bridgeBusy || !controller.bridgeDialogVisible,
            )
            Spacer(Modifier.width(Space.x3))
            BridgeHeaderAction(
                glyph = Glyph.History,
                label = "History",
                onClick = controller::openBridgeHistory,
                enabled = !controller.bridgeBusy || !controller.bridgeDialogVisible,
            )
            Spacer(Modifier.width(Space.x3))
            BridgeHeaderAction(
                glyph = Glyph.Refresh,
                label = "Refresh",
                onClick = controller::refreshBridgeData,
                enabled = !controller.bridgeLoading && !controller.bridgeBusy,
                loading = controller.bridgeLoading,
            )
        }
        Spacer(Modifier.height(Space.x6))
        BridgeHeaderIntro()
        Spacer(Modifier.height(Space.x6))
        BridgeNetworkStrip(controller)
        networkWarning?.let {
            Spacer(Modifier.height(Space.x5))
            StateBanner(text = it, isError = true)
        }
        Spacer(Modifier.height(Space.x5))
        BridgeStatusStrip(visibleBridgeStatus)
        if (controller.bridgeBusy && !controller.bridgeDialogVisible) {
            Spacer(Modifier.height(Space.x5))
            BridgeResumeCard(controller)
        }
        Spacer(Modifier.height(Space.x7))
        DetailSeparator()
        Spacer(Modifier.height(Space.x7))
        BridgeDirectionSelector(
            direction = direction,
            enabled = !controller.bridgeBusy,
            onSelect = {
                controller.selectBridgeDirection(it)
                amount = ""
                localError = null
            },
        )
        Spacer(Modifier.height(Space.x5))
        BridgeAmountInput(
            value = amount,
            onValueChange = {
                amount = it.filter { char -> char.isDigit() || char == '.' || char == ',' }
                localError = null
            },
            label = if (direction == BridgeDirection.OctToWoct) "Amount OCT" else "Amount wOCT",
            symbol = if (direction == BridgeDirection.OctToWoct) "OCT" else "wOCT",
            isError = localError != null,
            supportingText = localError,
        )
        Spacer(Modifier.height(Space.x3))
        BridgeAvailableRow(
            controller = controller,
            direction = direction,
            onMax = {
                amount = controller.bridgeMaxAmount()
                localError = null
            },
        )
        Spacer(Modifier.height(Space.x4))
        BridgeRecipientCard(
            title = if (direction == BridgeDirection.OctToWoct) "Receive wOCT to" else "Receive OCT to",
            address = if (direction == BridgeDirection.OctToWoct) evmAddress else wallet.address,
            glyph = if (direction == BridgeDirection.OctToWoct) Glyph.Evm else Glyph.Wallet,
        )
        Spacer(Modifier.height(Space.x5))
        PoctraButton(
            text = if (direction == BridgeDirection.OctToWoct) "Bridge to wOCT" else "Bridge to OCT",
            onClick = {
                when {
                    networkWarning != null -> localError = networkWarning
                    controller.bridgeLoading -> localError = "Wait until balances finish syncing."
                    controller.bridgeStatus.isPaused == true -> localError = "Bridge is closed."
                    amount.isBlank() -> localError = "Amount is required."
                    amount.replace(",", ".").toDoubleOrNull() == null -> localError = "Enter a valid amount."
                    else -> {
                        reviewStarted = false
                        controller.prepareBridgeDialog(amount, reviewReceiverAddress)
                        showReview = true
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !controller.bridgeBusy && !controller.bridgeLoading && controller.bridgeStatus.isPaused != true,
            loading = controller.bridgeBusy,
            leadingGlyph = Glyph.Bridge,
        )
    }

    if (showInfo) {
        BridgeInfoSheet(
            unlockOperator = controller.bridgeStatus.unlockOperator,
            onDismiss = { showInfo = false },
        )
    }
    val shouldShowBridgeDialog = showReview || controller.bridgeDialogVisible
    if (shouldShowBridgeDialog) {
        val dialogAmount = controller.bridgeDialogAmount.ifBlank { amount }
        val dialogReceiver = controller.bridgeDialogReceiverAddress.ifBlank { reviewReceiverAddress }
        val dialogStarted = reviewStarted ||
            controller.bridgeDialogStarted ||
            controller.bridgeBusy ||
            controller.bridgeStep != BridgeStep.Idle
        LaunchedEffect(direction, dialogAmount, dialogStarted) {
            if (!dialogStarted) controller.refreshBridgeReviewEstimates(dialogAmount)
        }
        BridgeReviewSheet(
            controller = controller,
            direction = direction,
            amount = dialogAmount,
            receiverAddress = dialogReceiver,
            status = controller.bridgeStatus,
            recommendedFeeOu = controller.recommendedFeeOu,
            gasEstimate = controller.bridgeEvmGasEstimate,
            gasLoading = controller.bridgeGasLoading,
            started = dialogStarted,
            onRetryGas = { controller.refreshBridgeReviewEstimates(dialogAmount) },
            onDismiss = {
                showReview = false
                if (!controller.bridgeBusy) reviewStarted = false
                controller.dismissBridgeDialog()
            },
            onConfirm = { octraFeeOu, evmGasPriceGwei ->
                reviewStarted = true
                controller.prepareBridgeDialog(dialogAmount, dialogReceiver, started = true)
                controller.submitBridge(
                    amountText = dialogAmount,
                    octraFeeOu = octraFeeOu,
                    evmGasPriceGwei = evmGasPriceGwei,
                )
            },
        )
    }
}

@Composable
private fun BridgeHeaderAction(
    glyph: Glyph,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean,
    loading: Boolean = false,
) {
    Column(
        modifier = Modifier
            .width(50.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(if (enabled) SurfaceMuted.copy(alpha = 0.78f) else SurfaceMuted.copy(alpha = 0.48f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(vertical = Space.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(15.dp), color = SurfaceRaised, strokeWidth = 2.dp)
        } else {
            GlyphIcon(
                glyph = glyph,
                modifier = Modifier.size(15.dp),
                color = if (enabled) TextTertiary else TextSecondary.copy(alpha = 0.45f),
            )
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.45f),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BridgeResumeCard(controller: PoctraController) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised.copy(alpha = 0.08f)),
    ) {
        Row(
            modifier = Modifier.padding(Space.x5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(SurfaceRaised.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(17.dp), color = SurfaceRaised, strokeWidth = 2.dp)
            }
            Spacer(Modifier.width(Space.x4))
            Column(Modifier.weight(1f)) {
                Text("Bridge in progress", color = TextTertiary, fontWeight = FontWeight.SemiBold)
                Text(
                    controller.bridgeProgress.ifBlank { "Processing bridge flow..." },
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Space.x4))
            PoctraButton(
                text = "Resume",
                onClick = controller::resumeBridgeDialog,
                compact = true,
                leadingGlyph = Glyph.Bridge,
            )
        }
    }
}

@Composable
private fun BridgeStatusStrip(status: BridgeStatus) {
    val stateLabel = when (status.isPaused) {
        true -> "Closed"
        false -> "Open"
        null -> "Syncing"
    }
    val stateColor = when (status.isPaused) {
        true -> ErrorRed
        false -> SurfaceRaised
        null -> TextSecondary
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.x4, vertical = Space.x3),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Space.x2),
        ) {
            Row(
                modifier = Modifier
                    .weight(0.92f)
                    .heightIn(min = 30.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(stateColor.copy(alpha = 0.12f))
                    .padding(horizontal = Space.x3, vertical = Space.x2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(Radius.pill))
                        .background(stateColor),
                )
                Spacer(Modifier.width(Space.x2))
                Text(
                    stateLabel,
                    color = stateColor,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BridgeLimitPill(
                "Lock Left",
                compactBridgeLimit(status.dailyLockRemainingRaw, status.dailyLockRemaining),
                Modifier.weight(1.08f),
            )
            BridgeLimitPill(
                "Unlock Left",
                compactBridgeLimit(status.dailyUnlockRemainingRaw, status.dailyUnlockRemaining),
                Modifier.weight(1.18f),
            )
        }
    }
}

@Composable
private fun BridgeLimitPill(label: String, value: String, modifier: Modifier = Modifier) {
    Row(
        modifier = Modifier
            .then(modifier)
            .heightIn(min = 30.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(SurfaceBase)
            .padding(horizontal = Space.x2, vertical = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            label,
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.width(2.dp))
        Text(
            value,
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

private fun compactBridgeLimit(raw: BigInteger?, fallback: String): String {
    if (raw != null) {
        return BigDecimal(raw)
            .divide(BigDecimal.TEN.pow(BridgeRepository.OctDecimals), 2, RoundingMode.DOWN)
            .stripTrailingZeros()
            .toPlainString()
            .ifBlank { "0" }
    }
    val clean = fallback.trim()
    val decimal = clean.toBigDecimalOrNull() ?: return clean.ifBlank { "--" }
    return decimal.setScale(2, RoundingMode.DOWN).stripTrailingZeros().toPlainString()
}

@Composable
private fun BridgeHistoryScreen(controller: PoctraController) {
    var reviewItemId by remember { mutableStateOf<String?>(null) }
    val reviewItem = reviewItemId?.let { id -> controller.bridgeHistory.firstOrNull { it.id == id } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeBridgeHistory, enabled = !controller.bridgeBusy || !controller.bridgeDialogVisible)
            Spacer(Modifier.width(Space.x6))
            Column(Modifier.weight(1f)) {
                Text("Bridge History", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
                Text("Recover or claim unfinished bridge transactions", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            BridgeHeaderAction(
                glyph = Glyph.Refresh,
                label = "Refresh",
                onClick = controller::refreshBridgeHistory,
                enabled = !controller.bridgeHistoryLoading && (!controller.bridgeBusy || !controller.bridgeDialogVisible),
                loading = controller.bridgeHistoryLoading,
            )
        }
        Spacer(Modifier.height(Space.x8))
        if (controller.bridgeHistory.isEmpty() && controller.bridgeHistoryLoading) {
            LoadingScreen()
        } else if (controller.bridgeHistory.isEmpty()) {
            EmptyPanel("No bridge history yet")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x5)) {
                controller.visibleBridgeHistory.forEach { item ->
                    BridgeHistoryRow(
                        item = item,
                        busy = controller.bridgeBusy,
                        onRecover = {
                            reviewItemId = item.id
                            controller.prepareBridgeHistoryRecoveryDialog(item.id)
                        },
                        onOpenOctra = item.octraTxHash?.let { hash ->
                            { controller.openDAppBrowser("Octra Bridge", "https://octrascan.io/tx.html?hash=$hash") }
                        },
                        onOpenEvm = item.evmTxHash?.let { hash ->
                            { controller.openDAppBrowser("Ethereum Bridge", "https://etherscan.io/tx/$hash") }
                        },
                    )
                }
                if (controller.bridgeHistoryCanLoadMore) {
                    InlineListAction(
                        text = "Load More",
                        onClick = controller::loadMoreBridgeHistory,
                        loading = controller.bridgeHistoryLoading,
                        enabled = !controller.bridgeHistoryLoading,
                    )
                }
            }
        }
    }

    if (reviewItem != null && controller.bridgeDialogVisible) {
        val dialogStarted = controller.bridgeDialogStarted || controller.bridgeBusy || controller.bridgeStep != BridgeStep.Idle
        val dialogAmount = controller.bridgeDialogAmount.ifBlank { reviewItem.amount }
        val dialogReceiver = controller.bridgeDialogReceiverAddress.ifBlank { bridgeHistoryToAddress(reviewItem) }
        LaunchedEffect(reviewItem.id, dialogAmount, dialogStarted) {
            if (!dialogStarted && reviewItem.direction == BridgeDirection.OctToWoct) {
                controller.refreshBridgeReviewEstimates(dialogAmount)
            }
        }
        BridgeReviewSheet(
            controller = controller,
            direction = reviewItem.direction,
            amount = dialogAmount,
            receiverAddress = dialogReceiver,
            status = controller.bridgeStatus,
            recommendedFeeOu = controller.recommendedFeeOu,
            gasEstimate = controller.bridgeEvmGasEstimate,
            gasLoading = controller.bridgeGasLoading,
            started = dialogStarted,
            reviewTitle = if (reviewItem.direction == BridgeDirection.OctToWoct) "Review Claim" else "Review Recovery",
            reviewNote = if (reviewItem.direction == BridgeDirection.OctToWoct) {
                ""
            } else {
                "Recovery checks the existing burn and waits for OCT from the Unlock Operator."
            },
            receiverLabel = if (reviewItem.direction == BridgeDirection.OctToWoct) "ETH Receiver" else "Receiver",
            showBridgeDetails = reviewItem.direction != BridgeDirection.OctToWoct,
            showOctraFee = false,
            showEvmGas = reviewItem.direction == BridgeDirection.OctToWoct,
            onRetryGas = { controller.refreshBridgeReviewEstimates(dialogAmount) },
            onDismiss = {
                if (!controller.bridgeBusy) reviewItemId = null
                controller.dismissBridgeDialog()
            },
            onConfirm = { _, evmGasPriceGwei ->
                controller.recoverBridgeHistory(reviewItem.id, evmGasPriceGwei = evmGasPriceGwei)
            },
        )
    } else if (reviewItemId != null) {
        LaunchedEffect(reviewItemId) {
            reviewItemId = null
        }
    }
}

@Composable
private fun BridgeHistoryRow(
    item: BridgeHistoryItem,
    busy: Boolean,
    onRecover: () -> Unit,
    onOpenOctra: (() -> Unit)?,
    onOpenEvm: (() -> Unit)?,
) {
    val recoverable = bridgeHistoryCanRecover(item)
    val completed = item.status == "completed"
    var expanded by remember(item.id) { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = tween(220, easing = FastOutSlowInEasing))
            .clickable { expanded = !expanded },
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = when (item.status) {
                "failed" -> ErrorRed.copy(alpha = 0.08f)
                "claimable" -> SurfaceRaised.copy(alpha = 0.08f)
                else -> SurfaceStrong
            },
        ),
    ) {
        Column(Modifier.padding(Space.x6)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(SurfaceRaised.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.Bridge, modifier = Modifier.size(19.dp), color = SurfaceRaised)
                }
                Spacer(Modifier.width(Space.x5))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(bridgeHistoryRouteLabel(item.direction), color = TextTertiary, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(Space.x3))
                        BridgeDirectionBadge(item.direction)
                    }
                    Spacer(Modifier.height(Space.x1))
                    Text("${item.amount} ${if (item.direction == BridgeDirection.OctToWoct) "OCT" else "wOCT"}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                BridgeHistoryStatusBadge(item.status)
                Spacer(Modifier.width(Space.x3))
                GlyphIcon(
                    if (expanded) Glyph.ChevronUp else Glyph.ChevronDown,
                    modifier = Modifier.size(15.dp),
                    color = TextSecondary,
                )
            }
            Spacer(Modifier.height(Space.x3))
            Text(
                "${bridgeStepLabel(item.step)}  |  ${formatBridgeHistoryTime(item.updatedAt)}",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (expanded) {
                Spacer(Modifier.height(Space.x5))
                Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                    DetailLine("From", bridgeHistoryFromAddress(item))
                    DetailLine("To", bridgeHistoryToAddress(item))
                    DetailLine("Step", bridgeStepLabel(item.step))
                    DetailLine("Created", formatBridgeHistoryTime(item.createdAt))
                    DetailLine("Updated", formatBridgeHistoryTime(item.updatedAt))
                    if (item.message.isNotBlank()) DetailLine("Message", item.message)
                    item.octraTxHash?.let { hash ->
                        DetailLine("Octra Tx", hash, actionLabel = "Open", onAction = onOpenOctra)
                    }
                    item.evmTxHash?.let { hash ->
                        DetailLine("EVM Tx", hash, actionLabel = "Open", onAction = onOpenEvm)
                    }
                }
                Spacer(Modifier.height(Space.x5))
                if (completed) {
                    BridgeHistoryOutcome(text = "Completed successfully", glyph = Glyph.Check, isError = false)
                } else if (recoverable) {
                    PoctraButton(
                        text = bridgeHistoryActionLabel(item),
                        onClick = onRecover,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !busy,
                        loading = busy,
                        compact = true,
                        variant = ButtonVariant.Primary,
                        leadingGlyph = if (item.status == "claimable") Glyph.Check else Glyph.Refresh,
                    )
                } else {
                    BridgeHistoryOutcome(text = bridgeHistoryPassiveLabel(item), glyph = Glyph.Info, isError = item.status == "failed")
                }
            }
        }
    }
}

@Composable
private fun BridgeHistoryOutcome(text: String, glyph: Glyph, isError: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background((if (isError) ErrorRed else SurfaceRaised).copy(alpha = 0.12f))
            .padding(horizontal = Space.x5, vertical = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(glyph, modifier = Modifier.size(16.dp), color = if (isError) ErrorRed else SurfaceRaised)
        Spacer(Modifier.width(Space.x4))
        Text(text, color = if (isError) ErrorRed else TextTertiary, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun BridgeHistoryStatusBadge(status: String) {
    val normalized = status.lowercase()
    val color = when (normalized) {
        "completed" -> SurfaceRaised
        "failed" -> ErrorRed
        "claimable" -> SurfaceRaised
        "running" -> Warning
        else -> TextSecondary
    }
    Text(
        text = normalized.ifBlank { "unknown" },
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(color.copy(alpha = 0.10f))
            .padding(horizontal = Space.x4, vertical = Space.x2),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

@Composable
private fun BridgeDirectionBadge(direction: BridgeDirection) {
    val color = when (direction) {
        BridgeDirection.OctToWoct -> SurfaceRaised
        BridgeDirection.WoctToOct -> Warning
    }
    val label = when (direction) {
        BridgeDirection.OctToWoct -> "LOCK"
        BridgeDirection.WoctToOct -> "UNLOCK"
    }
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = Space.x3, vertical = Space.x1),
        color = color,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

private fun bridgeHistoryCanRecover(item: BridgeHistoryItem): Boolean {
    if (item.status == "completed" || item.status == "unknown") return false
    return when (item.direction) {
        BridgeDirection.OctToWoct -> item.octraTxHash != null
        BridgeDirection.WoctToOct -> item.evmTxHash != null && item.step != BridgeStep.Approving
    }
}

private fun bridgeHistoryActionLabel(item: BridgeHistoryItem): String = when {
    item.status == "claimable" && item.direction == BridgeDirection.OctToWoct -> "Claim wOCT"
    item.direction == BridgeDirection.OctToWoct -> "Recover / Claim wOCT"
    else -> "Check / Claim OCT"
}

private fun bridgeHistoryPassiveLabel(item: BridgeHistoryItem): String = when (item.status) {
    "failed" -> "Claim data is incomplete. Open the related transaction first."
    "unknown" -> "Status could not be verified. Try refresh again."
    else -> "Waiting for bridge network confirmation."
}

private fun bridgeHistoryFromAddress(item: BridgeHistoryItem): String = when (item.direction) {
    BridgeDirection.OctToWoct -> item.walletAddress
    BridgeDirection.WoctToOct -> item.evmAddress
}

private fun bridgeHistoryToAddress(item: BridgeHistoryItem): String = when (item.direction) {
    BridgeDirection.OctToWoct -> item.evmAddress
    BridgeDirection.WoctToOct -> item.walletAddress
}

private fun bridgeHistoryRouteLabel(direction: BridgeDirection): String = when (direction) {
    BridgeDirection.OctToWoct -> "OCT -> wOCT"
    BridgeDirection.WoctToOct -> "wOCT -> OCT"
}

private fun bridgeStepLabel(step: BridgeStep): String = when (step) {
    BridgeStep.Idle -> "Started"
    BridgeStep.Locking -> "Locking OCT"
    BridgeStep.ConfirmingOctra -> "Confirming Octra lock"
    BridgeStep.WaitingRelayer -> "Waiting Ethereum header"
    BridgeStep.Claiming -> "Claiming wOCT"
    BridgeStep.Approving -> "Approving wOCT"
    BridgeStep.Burning -> "Burning wOCT"
    BridgeStep.WaitingUnlock -> "Waiting OCT unlock"
    BridgeStep.Completed -> "Completed"
    BridgeStep.Failed -> "Failed"
}

private fun formatBridgeHistoryTime(timestamp: Long): String {
    if (timestamp <= 0L) return "-"
    return android.text.format.DateFormat.format("MMM d, yyyy HH:mm", timestamp).toString()
}

@Composable
private fun BridgeHeaderIntro() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.x6, vertical = Space.x6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(SurfaceRaised.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Bridge, modifier = Modifier.size(25.dp), color = SurfaceRaised)
        }
        Spacer(Modifier.height(Space.x3))
        Text(
            "Octra Bridge",
            color = TextTertiary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.x2))
        Text(
            "Move OCT between Octra and Ethereum wOCT.",
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BridgeAmountInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    symbol: String,
    isError: Boolean,
    supportingText: String?,
) {
    var focused by remember { mutableStateOf(false) }
    val focusScale by animateFloatAsState(
        targetValue = if (focused) 0.988f else 1f,
        animationSpec = tween(150, easing = FastOutSlowInEasing),
        label = "bridgeAmountFocusScale",
    )
    Column(Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = focusScale
                    scaleY = focusScale
                },
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = Space.x5, vertical = Space.x3),
            ) {
                Text(label, color = if (isError) ErrorRed else TextSecondary, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(Space.x2))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = value,
                        onValueChange = onValueChange,
                        modifier = Modifier
                            .weight(1f)
                            .onFocusChanged { focused = it.isFocused },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge.copy(color = TextTertiary),
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        cursorBrush = SolidColor(SurfaceRaised),
                        decorationBox = { innerTextField ->
                            Box {
                                if (value.isBlank()) {
                                    Text("0.0", color = TextSecondary, style = MaterialTheme.typography.titleLarge)
                                }
                                innerTextField()
                            }
                        },
                    )
                    Spacer(Modifier.width(Space.x5))
                    Text(
                        symbol,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(SurfaceMuted)
                            .padding(horizontal = Space.x4, vertical = Space.x2),
                        color = TextTertiary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
        supportingText?.let {
            Spacer(Modifier.height(Space.x3))
            Text(it, color = ErrorRed, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun BridgeInfoSheet(
    unlockOperator: String,
    onDismiss: () -> Unit,
) {
    var sheetVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sheetVisible = true }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (sheetVisible) 0.52f else 0f,
        animationSpec = tween(ScrimMotionMillis),
        label = "bridgeInfoScrim",
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(onClick = {}),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = sheetVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(SheetEnterMillis, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(ScrimMotionMillis)),
            exit = slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(SheetExitMillis, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(SheetExitMillis)),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * 0.92f)
                    .clickable(onClick = {})
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(topStart = Radius.md, topEnd = Radius.md),
                colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = Space.x6, vertical = Space.x5),
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(BorderLine)
                            .align(Alignment.CenterHorizontally),
                    )
                    Spacer(Modifier.height(Space.x6))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(Radius.md))
                                .background(SurfaceRaised.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            GlyphIcon(Glyph.Bridge, modifier = Modifier.size(20.dp), color = SurfaceRaised)
                        }
                        Spacer(Modifier.width(Space.x5))
                        Column(Modifier.weight(1f)) {
                            Text("Bridge Flow", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
                            Text("Octra mainnet <-> Ethereum", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Spacer(Modifier.height(Space.x5))
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        BridgeFlowCard(
                            title = "OCT -> wOCT",
                            glyph = Glyph.Send,
                            steps = listOf(
                                "Lock OCT on Octra mainnet.",
                                "Wait header processed on Ethereum lightclient.",
                                "Mint wOCT to your EVM account.",
                            ),
                        )
                        Spacer(Modifier.height(Space.x5))
                        BridgeFlowCard(
                            title = "wOCT -> OCT",
                            glyph = Glyph.Receive,
                            steps = listOf(
                                "Approve bridge contract to spend wOCT.",
                                "Burn wOCT on Ethereum mainnet.",
                                "Wait incoming OCT from the Unlock Operator.",
                            ),
                        )
                        Spacer(Modifier.height(Space.x5))
                        BridgeAddressCard(
                            unlockOperator = unlockOperator,
                        )
                    }
                    Spacer(Modifier.height(Space.x5))
                    PoctraButton(
                        text = "Close",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        compact = true,
                        variant = ButtonVariant.Ghost,
                    )
                }
            }
        }
    }
}

@Composable
private fun BridgeFlowCard(title: String, glyph: Glyph, steps: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
    ) {
        Column(Modifier.padding(Space.x6)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(SurfaceRaised.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(glyph, modifier = Modifier.size(17.dp), color = SurfaceRaised)
                }
                Spacer(Modifier.width(Space.x5))
                Text(title, color = TextTertiary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(Space.x5))
            steps.forEachIndexed { index, step ->
                Text(
                    "${index + 1}. $step",
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (index != steps.lastIndex) Spacer(Modifier.height(Space.x3))
            }
        }
    }
}

@Composable
private fun BridgeAddressCard(unlockOperator: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
    ) {
        Column(Modifier.padding(horizontal = Space.x6, vertical = Space.x4)) {
            DetailLine("Octra Bridge", BridgeRepository.OctraBridgeContract)
            DetailSeparator()
            DetailLine("wOCT Token", BridgeRepository.WoctTokenAddress)
            DetailSeparator()
            DetailLine("EVM Bridge", BridgeRepository.WoctBridgeContract)
            DetailSeparator()
            DetailLine("Unlock Operator", unlockOperator.ifBlank { "Sync bridge status first" })
        }
    }
}

@Composable
private fun BridgeReviewSheet(
    controller: PoctraController,
    direction: BridgeDirection,
    amount: String,
    receiverAddress: String,
    status: BridgeStatus,
    recommendedFeeOu: Long,
    gasEstimate: BridgeEvmGasEstimate?,
    gasLoading: Boolean,
    started: Boolean,
    reviewTitle: String = "Review Bridge",
    reviewNote: String = "Bridge may require multiple on-chain steps. Keep the app open or recover from Bridge History.",
    receiverLabel: String = "Receiver",
    showBridgeDetails: Boolean = true,
    showOctraFee: Boolean = direction == BridgeDirection.OctToWoct,
    showEvmGas: Boolean = true,
    onRetryGas: () -> Unit,
    onDismiss: () -> Unit,
    onConfirm: (Long, String?) -> Unit,
) {
    var selectedOctraFee by remember(recommendedFeeOu) { mutableStateOf(BridgeFeePreset.Medium) }
    var customOctraFeeOu by remember(recommendedFeeOu) { mutableStateOf(recommendedFeeOu.coerceAtLeast(1L).toString()) }
    var selectedGas by remember(gasEstimate) { mutableStateOf(BridgeFeePreset.Medium) }
    var customGasGwei by remember(gasEstimate) {
        mutableStateOf(gasEstimate?.marketGasPriceWei?.let(::weiToGweiLabel).orEmpty())
    }
    val selectedOctraFeeOu = bridgeSelectedOctraFeeOu(selectedOctraFee, recommendedFeeOu, customOctraFeeOu)
    val selectedGasGwei = bridgeSelectedGasGwei(selectedGas, gasEstimate, customGasGwei)
    val canConfirm = status.isPaused != true &&
        (!showEvmGas || !gasLoading) &&
        (!showOctraFee || selectedOctraFee != BridgeFeePreset.Custom || customOctraFeeOu.toLongOrNull()?.let { it > 0L } == true) &&
        (!showEvmGas || selectedGas != BridgeFeePreset.Custom || customGasGwei.gweiToWeiOrNull() != null)
    var sheetVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sheetVisible = true }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (sheetVisible) 0.52f else 0f,
        animationSpec = tween(ScrimMotionMillis),
        label = "bridgeReviewScrim",
    )
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = scrimAlpha))
            .clickable(enabled = !started && !controller.bridgeBusy, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = sheetVisible,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(SheetEnterMillis, easing = FastOutSlowInEasing),
            ) + fadeIn(animationSpec = tween(ScrimMotionMillis)),
            exit = slideOutVertically(
                targetOffsetY = { it / 2 },
                animationSpec = tween(SheetExitMillis, easing = FastOutSlowInEasing),
            ) + fadeOut(animationSpec = tween(SheetExitMillis)),
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight * if (started) 0.92f else 0.88f)
                    .clickable(onClick = {})
                    .navigationBarsPadding(),
                shape = RoundedCornerShape(topStart = Radius.md, topEnd = Radius.md),
                colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = Space.x7, vertical = Space.x6),
                ) {
                    Box(
                        modifier = Modifier
                            .width(42.dp)
                            .height(4.dp)
                            .clip(RoundedCornerShape(Radius.pill))
                            .background(BorderLine)
                            .align(Alignment.CenterHorizontally),
                        )
                    Spacer(Modifier.height(Space.x6))
                    if (started) {
                        BridgeFlowProgressContent(
                            controller = controller,
                            direction = direction,
                            amount = amount,
                            receiverAddress = receiverAddress,
                            claimRecoveryMode = controller.bridgeClaimRecoveryMode,
                            onDismiss = onDismiss,
                        )
                    } else {
                        BridgeReviewContent(
                            controller = controller,
                            title = reviewTitle,
                            direction = direction,
                            amount = amount,
                            receiverAddress = receiverAddress,
                            receiverLabel = receiverLabel,
                            status = status,
                            recommendedFeeOu = recommendedFeeOu,
                            selectedOctraFee = selectedOctraFee,
                            customOctraFeeOu = customOctraFeeOu,
                            onSelectedOctraFee = { selectedOctraFee = it },
                            onCustomOctraFeeChange = { customOctraFeeOu = it.filter(Char::isDigit) },
                            gasEstimate = gasEstimate,
                            gasLoading = gasLoading,
                            selectedGas = selectedGas,
                            customGasGwei = customGasGwei,
                            onSelectedGas = { selectedGas = it },
                            onCustomGasChange = {
                                customGasGwei = it.filter { char -> char.isDigit() || char == '.' || char == ',' }
                            },
                            note = reviewNote,
                            showBridgeDetails = showBridgeDetails,
                            showOctraFee = showOctraFee,
                            showEvmGas = showEvmGas,
                            onRetryGas = onRetryGas,
                        )
                        Spacer(Modifier.height(Space.x6))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
                            PoctraButton(
                                text = "Cancel",
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                variant = ButtonVariant.Ghost,
                                fullHeight = true,
                            )
                            PoctraButton(
                                text = "Confirm",
                                onClick = { onConfirm(selectedOctraFeeOu, selectedGasGwei) },
                                modifier = Modifier.weight(1f),
                                leadingGlyph = Glyph.Check,
                                enabled = canConfirm,
                            )
                        }
                    }
                }
            }
        }
    }
}

private enum class BridgeFeePreset {
    Low,
    Medium,
    Market,
    Custom,
}

@Composable
private fun BridgeReviewContent(
    controller: PoctraController,
    title: String,
    direction: BridgeDirection,
    amount: String,
    receiverAddress: String,
    receiverLabel: String,
    status: BridgeStatus,
    recommendedFeeOu: Long,
    selectedOctraFee: BridgeFeePreset,
    customOctraFeeOu: String,
    onSelectedOctraFee: (BridgeFeePreset) -> Unit,
    onCustomOctraFeeChange: (String) -> Unit,
    gasEstimate: BridgeEvmGasEstimate?,
    gasLoading: Boolean,
    selectedGas: BridgeFeePreset,
    customGasGwei: String,
    onSelectedGas: (BridgeFeePreset) -> Unit,
    onCustomGasChange: (String) -> Unit,
    note: String,
    showBridgeDetails: Boolean,
    showOctraFee: Boolean,
    showEvmGas: Boolean,
    onRetryGas: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(SurfaceRaised.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Bridge, modifier = Modifier.size(21.dp), color = SurfaceRaised)
        }
        Spacer(Modifier.width(Space.x5))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextTertiary, style = MaterialTheme.typography.titleLarge)
            Text(bridgeReviewRouteLabel(direction), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
    val octraWallet = controller.wallet
    val evmAccount = controller.evmAccount
    val source = if (direction == BridgeDirection.OctToWoct) {
        ReviewFlowEndpoint(
            label = "Octra sender",
            address = octraWallet?.address.orEmpty(),
            identityName = octraWallet?.address?.let(controller::walletIdentityDomain),
        )
    } else {
        ReviewFlowEndpoint(
            label = "EVM sender",
            address = evmAccount?.address.orEmpty(),
            identityName = evmAccount?.ensName,
        )
    }
    val destination = if (direction == BridgeDirection.OctToWoct) {
        ReviewFlowEndpoint(
            label = receiverLabel.takeIf { it != "Receiver" } ?: "EVM receiver",
            address = receiverAddress,
            identityName = evmAccount?.ensName?.takeIf {
                evmAccount.address.equals(receiverAddress, ignoreCase = true)
            },
        )
    } else {
        ReviewFlowEndpoint(
            label = receiverLabel.takeIf { it != "Receiver" } ?: "Octra receiver",
            address = receiverAddress,
            identityName = controller.identityNameForAddress(receiverAddress),
        )
    }
    ReviewProcessFlow(
        source = source,
        destination = destination,
        actionLabel = "Bridge",
        actionGlyph = Glyph.Bridge,
        modifier = Modifier.padding(top = Space.x5),
    )
    Spacer(Modifier.height(Space.x6))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
    ) {
        Column(Modifier.padding(horizontal = Space.x6, vertical = Space.x3)) {
            DetailLine("Amount", "$amount ${if (direction == BridgeDirection.OctToWoct) "OCT" else "wOCT"}")
            if (showBridgeDetails) {
                DetailSeparator()
                DetailLine("Estimated bridge", bridgeReviewEstimate(direction))
                DetailSeparator()
                DetailLine("Daily remaining", bridgeReviewRemainingLabel(direction, status))
                DetailSeparator()
                DetailLine("Bridge status", bridgeReviewStatusLabel(status))
            }
        }
    }
    Spacer(Modifier.height(Space.x5))
    BridgeReviewFeePanel(
        direction = direction,
        recommendedFeeOu = recommendedFeeOu,
        selectedOctraFee = selectedOctraFee,
        customOctraFeeOu = customOctraFeeOu,
        onSelectedOctraFee = onSelectedOctraFee,
        onCustomOctraFeeChange = onCustomOctraFeeChange,
        gasEstimate = gasEstimate,
        gasLoading = gasLoading,
        selectedGas = selectedGas,
        customGasGwei = customGasGwei,
        onSelectedGas = onSelectedGas,
        onCustomGasChange = onCustomGasChange,
        showOctraFee = showOctraFee,
        showEvmGas = showEvmGas,
        onRetryGas = onRetryGas,
    )
    if (note.isNotBlank()) {
        Spacer(Modifier.height(Space.x5))
        StateBanner(
            text = note,
            isError = false,
            compact = true,
        )
    }
}

@Composable
private fun BridgeReviewFeePanel(
    direction: BridgeDirection,
    recommendedFeeOu: Long,
    selectedOctraFee: BridgeFeePreset,
    customOctraFeeOu: String,
    onSelectedOctraFee: (BridgeFeePreset) -> Unit,
    onCustomOctraFeeChange: (String) -> Unit,
    gasEstimate: BridgeEvmGasEstimate?,
    gasLoading: Boolean,
    selectedGas: BridgeFeePreset,
    customGasGwei: String,
    onSelectedGas: (BridgeFeePreset) -> Unit,
    onCustomGasChange: (String) -> Unit,
    showOctraFee: Boolean,
    showEvmGas: Boolean,
    onRetryGas: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
    ) {
        Column(Modifier.padding(Space.x5)) {
            Text("Network Fee", color = TextTertiary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Space.x4))
            if (showOctraFee && direction == BridgeDirection.OctToWoct) {
                Text("Octra", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(Space.x3))
                BridgeFeePresetRow(
                    selected = selectedOctraFee,
                    presets = listOf(BridgeFeePreset.Medium, BridgeFeePreset.Market, BridgeFeePreset.Custom),
                    titles = mapOf(
                        BridgeFeePreset.Medium to "Recommended",
                        BridgeFeePreset.Market to "Fast",
                        BridgeFeePreset.Custom to "Custom",
                    ),
                    labels = mapOf(
                        BridgeFeePreset.Medium to ouToOct(recommendedFeeOu),
                        BridgeFeePreset.Market to ouToOct(bridgeFastOctraFee(recommendedFeeOu)),
                        BridgeFeePreset.Custom to "${ouToOct(customOctraFeeOu.toLongOrNull() ?: recommendedFeeOu)} OCT",
                    ),
                    suffix = "OCT",
                    onSelect = onSelectedOctraFee,
                )
                if (selectedOctraFee == BridgeFeePreset.Custom) {
                    Spacer(Modifier.height(Space.x4))
                    PoctraTextField(
                        value = customOctraFeeOu,
                        onValueChange = onCustomOctraFeeChange,
                        label = "Custom fee (OU)",
                        keyboardType = KeyboardType.Number,
                        singleLine = true,
                        supportingText = if (customOctraFeeOu.toLongOrNull()?.let { it > 0L } == true) null else "Enter a valid fee.",
                    )
                }
                Spacer(Modifier.height(Space.x5))
                DetailSeparator()
                Spacer(Modifier.height(Space.x5))
            }
            if (!showEvmGas) {
                StateBanner("No new gas fee is required. Poctra will verify the existing bridge transaction.", isError = false, compact = true)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Ethereum",
                        modifier = Modifier.weight(1f),
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    if (gasLoading) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = SurfaceRaised, strokeWidth = 2.dp)
                }
                Spacer(Modifier.height(Space.x3))
                if (gasEstimate == null) {
                    StateBanner(
                        text = if (gasLoading) "Fetching gas estimate..." else "Gas estimate unavailable. The bridge will use the active RPC gas price.",
                        isError = false,
                        compact = true,
                    )
                    if (!gasLoading) {
                        Spacer(Modifier.height(Space.x4))
                        PoctraButton(
                            text = "Retry Gas",
                            onClick = onRetryGas,
                            modifier = Modifier.fillMaxWidth(),
                            variant = ButtonVariant.Ghost,
                            compact = true,
                            leadingGlyph = Glyph.Refresh,
                        )
                    }
                } else {
                    BridgeFeePresetRow(
                        selected = selectedGas,
                        labels = mapOf(
                            BridgeFeePreset.Low to weiToEthLabel(gasEstimate.lowFeeWei),
                            BridgeFeePreset.Medium to weiToEthLabel(gasEstimate.marketFeeWei),
                            BridgeFeePreset.Market to weiToEthLabel(gasEstimate.fastFeeWei),
                            BridgeFeePreset.Custom to bridgeCustomGasFeeLabel(gasEstimate, customGasGwei),
                        ),
                        suffix = "ETH",
                        onSelect = onSelectedGas,
                    )
                    if (selectedGas == BridgeFeePreset.Custom) {
                        Spacer(Modifier.height(Space.x4))
                        PoctraTextField(
                            value = customGasGwei,
                            onValueChange = onCustomGasChange,
                            label = "Custom gas price (gwei)",
                            keyboardType = KeyboardType.Decimal,
                            singleLine = true,
                            supportingText = if (customGasGwei.gweiToWeiOrNull() == null) "Enter a valid gas price." else null,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BridgeFeePresetRow(
    selected: BridgeFeePreset,
    labels: Map<BridgeFeePreset, String>,
    suffix: String,
    onSelect: (BridgeFeePreset) -> Unit,
    presets: List<BridgeFeePreset> = BridgeFeePreset.values().toList(),
    titles: Map<BridgeFeePreset, String> = emptyMap(),
    verticalPadding: Dp = Space.x3,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
        presets.forEach { preset ->
            val value = labels[preset].orEmpty()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(if (selected == preset) SurfaceRaised.copy(alpha = 0.18f) else SurfaceMuted.copy(alpha = 0.72f))
                    .clickable { onSelect(preset) }
                    .padding(horizontal = Space.x2, vertical = verticalPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(
                        glyph = when (preset) {
                            BridgeFeePreset.Low -> Glyph.Info
                            BridgeFeePreset.Medium -> Glyph.Check
                            BridgeFeePreset.Market -> Glyph.Send
                            BridgeFeePreset.Custom -> Glyph.Settings
                        },
                        modifier = Modifier.size(10.dp),
                        color = if (selected == preset) SurfaceRaised else TextSecondary,
                    )
                    Spacer(Modifier.width(Space.x1))
                    Text(
                        titles[preset] ?: preset.name.lowercase().replaceFirstChar { it.uppercase() },
                        color = if (selected == preset) TextTertiary else TextSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(Space.x1))
                Text(
                    if (preset == BridgeFeePreset.Custom) value else "$value $suffix",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun BridgeFlowProgressContent(
    controller: PoctraController,
    direction: BridgeDirection,
    amount: String,
    receiverAddress: String,
    claimRecoveryMode: Boolean,
    onDismiss: () -> Unit,
) {
    val failed = controller.bridgeStep == BridgeStep.Failed
    val completed = controller.bridgeStep == BridgeStep.Completed
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background((if (failed) ErrorRed else SurfaceRaised).copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            when {
                controller.bridgeBusy -> CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SurfaceRaised, strokeWidth = 2.dp)
                failed -> GlyphIcon(Glyph.Info, modifier = Modifier.size(21.dp), color = ErrorRed)
                else -> GlyphIcon(Glyph.Check, modifier = Modifier.size(21.dp), color = SurfaceRaised)
            }
        }
        Spacer(Modifier.width(Space.x5))
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    failed -> "Bridge Failed"
                    completed -> "Bridge Complete"
                    controller.bridgeStep == BridgeStep.WaitingUnlock && !controller.bridgeBusy -> "Waiting Unlock"
                    else -> "Bridge Processing"
                },
                color = TextTertiary,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(bridgeReviewRouteLabel(direction), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(Space.x5))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
    ) {
        Column(Modifier.padding(horizontal = Space.x6, vertical = Space.x3)) {
            DetailLine("Amount", "$amount ${if (direction == BridgeDirection.OctToWoct) "OCT" else "wOCT"}")
            DetailSeparator()
            DetailLine("Receiver", receiverAddress)
            if (controller.bridgeOctraTxHash != null) {
                DetailSeparator()
                DetailLine("Octra Tx", controller.bridgeOctraTxHash.orEmpty())
            }
            if (controller.bridgeEvmTxHash != null) {
                DetailSeparator()
                DetailLine("EVM Tx", controller.bridgeEvmTxHash.orEmpty())
            }
        }
    }
    Spacer(Modifier.height(Space.x5))
    BridgeProgressCard(
        direction = direction,
        currentStep = controller.bridgeStep,
        progress = controller.bridgeProgress,
        running = controller.bridgeBusy,
        claimRecoveryMode = claimRecoveryMode,
    )
    if (controller.bridgeError != null) {
        Spacer(Modifier.height(Space.x5))
        StateBanner(controller.bridgeError.orEmpty(), isError = true, compact = true)
    }
    Spacer(Modifier.height(Space.x5))
    BridgeExplorerLinks(controller)
    if (controller.bridgeCanSpeedUp) {
        Spacer(Modifier.height(Space.x5))
        StateBanner(
            text = "Pending too long? Speed Up replaces the current EVM transaction with the same nonce and higher gas.",
            isError = false,
            compact = true,
        )
        Spacer(Modifier.height(Space.x4))
        PoctraButton(
            text = "Speed Up",
            onClick = controller::speedUpBridgeEvmTransaction,
            modifier = Modifier.fillMaxWidth(),
            loading = controller.bridgeSpeedUpBusy,
            enabled = !controller.bridgeSpeedUpBusy,
            leadingGlyph = Glyph.Refresh,
            compact = true,
            variant = ButtonVariant.Ghost,
        )
    }
    Spacer(Modifier.height(Space.x5))
    PoctraButton(
        text = if (controller.bridgeBusy) "Minimize" else "Done",
        onClick = onDismiss,
        modifier = Modifier.fillMaxWidth(),
        enabled = true,
        leadingGlyph = if (completed) Glyph.Check else if (controller.bridgeBusy) Glyph.History else Glyph.Bridge,
        compact = true,
    )
}

private fun bridgeReviewRouteLabel(direction: BridgeDirection): String = when (direction) {
    BridgeDirection.OctToWoct -> "OCT -> wOCT"
    BridgeDirection.WoctToOct -> "wOCT -> OCT"
}

private fun bridgeFastOctraFee(recommendedFeeOu: Long): Long =
    recommendedFeeOu.multiplyClamped(2).coerceAtLeast(1L)

private fun bridgeSelectedOctraFeeOu(
    preset: BridgeFeePreset,
    recommendedFeeOu: Long,
    customFeeOu: String,
): Long = when (preset) {
    BridgeFeePreset.Low,
    BridgeFeePreset.Medium -> recommendedFeeOu.coerceAtLeast(1L)
    BridgeFeePreset.Market -> bridgeFastOctraFee(recommendedFeeOu)
    BridgeFeePreset.Custom -> customFeeOu.toLongOrNull()?.takeIf { it > 0L } ?: recommendedFeeOu.coerceAtLeast(1L)
}

private fun bridgeSelectedGasGwei(
    preset: BridgeFeePreset,
    gasEstimate: BridgeEvmGasEstimate?,
    customGasGwei: String,
): String? {
    val wei = when (preset) {
        BridgeFeePreset.Low -> gasEstimate?.lowGasPriceWei
        BridgeFeePreset.Medium -> gasEstimate?.marketGasPriceWei
        BridgeFeePreset.Market -> gasEstimate?.fastGasPriceWei
        BridgeFeePreset.Custom -> customGasGwei.gweiToWeiOrNull()
    } ?: return null
    return weiToGweiLabel(wei)
}

private fun bridgeCustomGasFeeLabel(gasEstimate: BridgeEvmGasEstimate, customGasGwei: String): String {
    val gasPriceWei = customGasGwei.gweiToWeiOrNull() ?: return "Enter a custom gas price."
    return "${weiToEthLabel(gasEstimate.gasLimit.multiply(gasPriceWei))} ETH estimated"
}

private fun tradeSelectedGasGwei(
    preset: BridgeFeePreset,
    quote: TradeQuote,
    customGasGwei: String,
): String? {
    val wei = when (preset) {
        BridgeFeePreset.Low -> quote.lowGasPriceWei
        BridgeFeePreset.Medium -> quote.gasPriceWei
        BridgeFeePreset.Market -> quote.fastGasPriceWei
        BridgeFeePreset.Custom -> customGasGwei.gweiToWeiOrNull()
    } ?: return null
    return weiToGweiLabel(wei)
}

private fun tradeCustomGasFeeLabel(quote: TradeQuote, customGasGwei: String): String {
    val gasPriceWei = customGasGwei.gweiToWeiOrNull() ?: return "Enter a custom gas price."
    return "${weiToEthLabel(quote.gasEstimate.multiply(gasPriceWei))} ETH estimated"
}

private fun weiToGweiLabel(wei: BigInteger): String =
    BigDecimal(wei)
        .divide(BigDecimal("1000000000"), 4, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()

private fun weiToEthLabel(wei: BigInteger): String =
    BigDecimal(wei)
        .divide(BigDecimal("1000000000000000000"), 8, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
        .ifBlank { "0" }

private fun String.gweiToWeiOrNull(): BigInteger? {
    val value = trim()
        .replace(",", ".")
        .takeIf { it.isNotBlank() }
        ?.toBigDecimalOrNull()
        ?.takeIf { it > BigDecimal.ZERO }
        ?: return null
    return value.multiply(BigDecimal("1000000000")).toBigInteger().takeIf { it > BigInteger.ZERO }
}

private fun Long.multiplyClamped(multiplier: Long): Long =
    runCatching { Math.multiplyExact(this, multiplier) }.getOrDefault(Long.MAX_VALUE)

private fun Long.divideClamped(divisor: Long): Long =
    if (divisor == 0L) this else this / divisor

private fun bridgeReviewGasLabel(direction: BridgeDirection, recommendedFeeOu: Long): String = when (direction) {
    BridgeDirection.OctToWoct -> "Octra fee ~ ${ouToOct(recommendedFeeOu)} OCT + Ethereum claim gas"
    BridgeDirection.WoctToOct -> "Ethereum approve + burn gas"
}

private fun bridgeReviewEstimate(direction: BridgeDirection): String = when (direction) {
    BridgeDirection.OctToWoct -> "~3-8 minutes total"
    BridgeDirection.WoctToOct -> "~5-15 minutes total"
}

private fun bridgeReviewRemainingLabel(direction: BridgeDirection, status: BridgeStatus): String = when (direction) {
    BridgeDirection.OctToWoct -> "${status.dailyLockRemaining} OCT lock limit left today"
    BridgeDirection.WoctToOct -> "${status.dailyUnlockRemaining} OCT unlock limit left today"
}

private fun bridgeReviewStatusLabel(status: BridgeStatus): String = when (status.isPaused) {
    true -> "Bridge Closed"
    false -> "Bridge Open"
    null -> status.error ?: "Status syncing"
}

@Composable
private fun BridgeNetworkStrip(controller: PoctraController) {
    val octraNetwork = controller.activeRpcNetwork
    val evmNetwork = controller.activeEvmNetwork
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(
            modifier = Modifier.padding(Space.x6),
            verticalArrangement = Arrangement.spacedBy(Space.x4),
        ) {
            ActiveNetworkLine(
                glyph = Glyph.Wallet,
                label = "Octra",
                value = octraNetwork?.name
                    ?: "No active Octra RPC",
                onChange = controller::openRpcManagementFromBridge,
            )
            DetailSeparator()
            ActiveNetworkLine(
                glyph = Glyph.Evm,
                label = "EVM",
                value = evmNetwork?.name
                    ?: "No active EVM network",
                onChange = controller::openEvmNetworkSettingsFromBridge,
            )
        }
    }
}

@Composable
private fun TradeNetworkInfoCard(controller: PoctraController) {
    val evmNetwork = controller.activeEvmNetwork
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        ActiveNetworkLine(
            glyph = Glyph.Evm,
            label = "Active EVM Network",
            value = evmNetwork?.let(::networkNameWithKind)
                ?: "No active EVM network",
            detail = evmNetwork?.let { "Chain ID ${it.chainId} - ${it.symbol}" } ?: "Set an active EVM network",
            onChange = controller::openEvmNetworkSettingsFromTrade,
            modifier = Modifier.padding(Space.x6),
        )
    }
}

@Composable
private fun ActiveNetworkLine(
    glyph: Glyph,
    label: String,
    value: String,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(SurfaceRaised.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, modifier = Modifier.size(18.dp), color = SurfaceRaised)
        }
        Spacer(Modifier.width(Space.x5))
        Column(Modifier.weight(1f)) {
            Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Spacer(Modifier.height(Space.x1))
            Text(value, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            detail?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(Space.x4))
        Text(
            "Change",
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.sm))
                .background(SurfaceMuted.copy(alpha = 0.72f))
                .clickable(onClick = onChange)
                .padding(horizontal = Space.x4, vertical = Space.x2),
            color = SurfaceRaised,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

private fun networkTypeLabel(networkType: String): String = when (networkType.lowercase()) {
    "mainnet" -> "Mainnet"
    "devnet" -> "Devnet"
    else -> networkType
}

private fun evmNetworkKindLabel(network: EvmNetwork): String =
    if (network.isTestnet) "Testnet" else "Mainnet"

private fun networkNameWithKind(network: RpcNetwork): String =
    networkNameWithKind(network.name, networkTypeLabel(network.networkType))

private fun networkNameWithKind(network: EvmNetwork): String =
    networkNameWithKind(network.name, evmNetworkKindLabel(network))

private fun networkNameWithKind(name: String, kind: String): String {
    val cleanName = name.trim()
    val cleanKind = kind.trim()
    if (cleanName.isBlank() || cleanKind.isBlank()) return cleanName.ifBlank { cleanKind }
    val lowerName = cleanName.lowercase()
    val lowerKind = cleanKind.lowercase()
    val knownTestnetName = listOf("testnet", "sepolia", "holesky", "goerli", "amoy", "mumbai")
        .any { lowerName.contains(it) }
    return if (lowerName.contains(lowerKind) || (lowerKind == "testnet" && knownTestnetName)) {
        cleanName
    } else {
        "$cleanName ($cleanKind)"
    }
}

@Composable
private fun BridgeBalancePanel(controller: PoctraController) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(Modifier.padding(Space.x7)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Bridge Balances", color = TextTertiary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (controller.bridgeLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = SurfaceRaised, strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.height(Space.x5))
            BridgeBalanceLine("Octra Mainnet", controller.bridgeBalances.octAmount, "OCT")
            DetailSeparator()
            BridgeBalanceLine("Ethereum Gas", controller.bridgeBalances.ethAmount, "ETH")
            DetailSeparator()
            BridgeBalanceLine("Ethereum Token", controller.bridgeBalances.woctAmount, "wOCT")
        }
    }
}

@Composable
private fun BridgeBalanceLine(label: String, amount: String, symbol: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text("$amount $symbol", color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun BridgeDirectionSelector(
    direction: BridgeDirection,
    enabled: Boolean,
    onSelect: (BridgeDirection) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
        BridgeDirectionButton(
            title = "OCT to wOCT",
            subtitle = "Octra -> Ethereum",
            selected = direction == BridgeDirection.OctToWoct,
            enabled = enabled,
            onClick = { onSelect(BridgeDirection.OctToWoct) },
            modifier = Modifier.weight(1f),
        )
        BridgeDirectionButton(
            title = "wOCT to OCT",
            subtitle = "Ethereum -> Octra",
            selected = direction == BridgeDirection.WoctToOct,
            enabled = enabled,
            onClick = { onSelect(BridgeDirection.WoctToOct) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun BridgeDirectionButton(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .height(58.dp)
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = if (selected) SurfaceRaised.copy(alpha = 0.18f) else SurfaceStrong),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Space.x4, vertical = Space.x3),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                GlyphIcon(
                    glyph = if (selected) Glyph.Check else Glyph.Bridge,
                    modifier = Modifier.size(16.dp),
                    color = if (selected) SurfaceRaised else TextSecondary,
                )
                Spacer(Modifier.width(Space.x3))
                Text(
                    title,
                    color = TextTertiary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(Space.x3))
            Text(
                subtitle,
                modifier = Modifier.fillMaxWidth(),
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun BridgeAvailableRow(
    controller: PoctraController,
    direction: BridgeDirection,
    onMax: () -> Unit,
) {
    val available = if (direction == BridgeDirection.OctToWoct) {
        "${controller.bridgeBalances.octAmount} OCT"
    } else {
        "${controller.bridgeBalances.woctAmount} wOCT"
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Available: $available",
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "Max",
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.sm))
                .clickable(enabled = !controller.bridgeLoading && !controller.bridgeBusy, onClick = onMax)
                .padding(horizontal = Space.x4, vertical = Space.x2),
            color = if (!controller.bridgeLoading && !controller.bridgeBusy) SurfaceRaised else TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun BridgeRecipientCard(title: String, address: String, glyph: Glyph) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Row(Modifier.padding(horizontal = Space.x5, vertical = Space.x4), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(SurfaceRaised.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(glyph, modifier = Modifier.size(17.dp), color = SurfaceRaised)
            }
            Spacer(Modifier.width(Space.x6))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(Space.x2))
                Text(shortAddress(address), color = TextTertiary, fontWeight = FontWeight.SemiBold)
                Text(address, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun BridgeSummaryCard(direction: BridgeDirection, walletAddress: String, evmAddress: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(Modifier.padding(horizontal = Space.x7, vertical = Space.x4)) {
            DetailLine("Route", if (direction == BridgeDirection.OctToWoct) "OCT -> wOCT" else "wOCT -> OCT")
            DetailSeparator()
            DetailLine("Octra Bridge", shortAddress(BridgeRepository.OctraBridgeContract))
            DetailSeparator()
            DetailLine("wOCT Contract", shortAddress(BridgeRepository.WoctTokenAddress))
            DetailSeparator()
            DetailLine("Recipient", if (direction == BridgeDirection.OctToWoct) shortAddress(evmAddress) else shortAddress(walletAddress))
        }
    }
}

@Composable
private fun BridgeProgressCard(
    direction: BridgeDirection,
    currentStep: BridgeStep,
    progress: String,
    running: Boolean,
    claimRecoveryMode: Boolean = false,
) {
    val steps = if (direction == BridgeDirection.OctToWoct) {
        listOf(
            BridgeUiStep(BridgeStep.Locking, "Lock OCT", "Submit lock_to_eth on Octra"),
            BridgeUiStep(BridgeStep.WaitingRelayer, "Wait Header", "Ethereum lightclient processing"),
            BridgeUiStep(BridgeStep.Claiming, "Mint wOCT", "Claim wrapped OCT on Ethereum"),
        )
    } else {
        listOf(
            BridgeUiStep(BridgeStep.Approving, "Approve", "Allow bridge to spend wOCT"),
            BridgeUiStep(BridgeStep.Burning, "Burn wOCT", "Burn wrapped OCT on Ethereum"),
            BridgeUiStep(BridgeStep.WaitingUnlock, "Wait OCT In", "Incoming OCT from Unlock Operator"),
        )
    }
    val currentIndex = if (currentStep == BridgeStep.Completed) {
        steps.lastIndex + 1
    } else if (claimRecoveryMode && direction == BridgeDirection.OctToWoct && currentStep != BridgeStep.Failed) {
        2
    } else {
        bridgeStepIndex(direction, currentStep)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(Modifier.padding(Space.x7)) {
            Text(progress, color = TextTertiary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Space.x6))
            steps.forEachIndexed { index, step ->
                BridgeProgressStepRow(
                    step = step,
                    done = currentIndex > index,
                    active = currentIndex == index && currentStep != BridgeStep.Failed,
                    failed = currentStep == BridgeStep.Failed && currentIndex == index,
                    running = running,
                )
                if (index != steps.lastIndex) {
                    Box(
                        modifier = Modifier
                            .padding(start = 13.dp)
                            .width(1.dp)
                            .height(Space.x5)
                            .background(BorderLine),
                    )
                }
            }
        }
    }
}

@Composable
private fun BridgeProgressStepRow(
    step: BridgeUiStep,
    done: Boolean,
    active: Boolean,
    failed: Boolean,
    running: Boolean,
) {
    val indicatorScale by animateFloatAsState(
        targetValue = if (active && running) 1.08f else 1f,
        animationSpec = tween(ScrimMotionMillis, easing = FastOutSlowInEasing),
        label = "bridgeProgressIndicatorScale",
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .graphicsLayer {
                    scaleX = indicatorScale
                    scaleY = indicatorScale
                }
                .clip(RoundedCornerShape(Radius.md))
                .background(
                    when {
                        failed -> ErrorRed.copy(alpha = 0.18f)
                        done -> SurfaceRaised
                        active -> SurfaceRaised.copy(alpha = 0.18f)
                        else -> SurfaceMuted
                    },
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                active && running -> CircularProgressIndicator(modifier = Modifier.size(14.dp), color = SurfaceRaised, strokeWidth = 2.dp)
                done -> GlyphIcon(Glyph.Check, modifier = Modifier.size(14.dp), color = PrimaryContent)
                failed -> GlyphIcon(Glyph.Info, modifier = Modifier.size(14.dp), color = ErrorRed)
                else -> Text(" ", color = TextSecondary)
            }
        }
        Spacer(Modifier.width(Space.x5))
        Column(Modifier.weight(1f)) {
            Text(step.title, color = TextTertiary, fontWeight = FontWeight.SemiBold)
            Text(step.subtitle, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun BridgeExplorerLinks(controller: PoctraController) {
    val octraHash = controller.bridgeOctraTxHash
    val evmHash = controller.bridgeEvmTxHash
    if (octraHash == null && evmHash == null) return
    Column(verticalArrangement = Arrangement.spacedBy(Space.x5)) {
        octraHash?.let { hash ->
            PoctraButton(
                text = "View Octra Lock",
                onClick = { controller.openDAppBrowser("Octra Bridge", "https://octrascan.io/tx.html?hash=$hash") },
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Ghost,
                compact = true,
                leadingGlyph = Glyph.Browser,
            )
        }
        evmHash?.let { hash ->
            PoctraButton(
                text = "View Ethereum Tx",
                onClick = { controller.openDAppBrowser("Ethereum Bridge", "https://etherscan.io/tx/$hash") },
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Ghost,
                compact = true,
                leadingGlyph = Glyph.Browser,
            )
        }
    }
}

private data class BridgeUiStep(
    val step: BridgeStep,
    val title: String,
    val subtitle: String,
)

private fun bridgeStepIndex(direction: BridgeDirection, currentStep: BridgeStep): Int = when (direction) {
    BridgeDirection.OctToWoct -> when (currentStep) {
        BridgeStep.Locking,
        BridgeStep.ConfirmingOctra -> 0
        BridgeStep.WaitingRelayer -> 1
        BridgeStep.Claiming -> 2
        else -> -1
    }
    BridgeDirection.WoctToOct -> when (currentStep) {
        BridgeStep.Approving -> 0
        BridgeStep.Burning -> 1
        BridgeStep.WaitingUnlock -> 2
        else -> -1
    }
}

@Composable
private fun EvmWalletScreen(controller: PoctraController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeEvm, enabled = !controller.evmSending)
            Spacer(Modifier.width(Space.x6))
            Text("EVM Account", color = TextTertiary, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            MiniHeaderAction(
                glyph = Glyph.History,
                label = "History",
                onClick = controller::openEvmActivity,
                enabled = !controller.evmSending,
            )
            Spacer(Modifier.width(Space.x3))
            MiniHeaderAction(
                glyph = Glyph.External,
                label = "Explorer",
                onClick = controller::openEvmExplorerAddress,
                enabled = !controller.evmSending,
            )
        }
        Spacer(Modifier.height(Space.x8))
        EvmBalanceHero(controller)
        Spacer(Modifier.height(34.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x6)) {
            WalletActionButton(
                label = "Send",
                glyph = Glyph.Send,
                modifier = Modifier.weight(1f),
                disabled = controller.evmSending,
            ) {
                controller.openEvmSend()
            }
            WalletActionButton(
                label = "Receive",
                glyph = Glyph.Receive,
                modifier = Modifier.weight(1f),
                disabled = controller.evmSending,
            ) {
                controller.openEvmReceive()
            }
            WalletActionButton(
                label = "Network",
                glyph = Glyph.Wifi,
                modifier = Modifier.weight(1f),
                disabled = controller.evmSending,
            ) {
                controller.openEvmNetworkSettings()
            }
        }
        Spacer(Modifier.height(Space.x8))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Assets", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
                Text("Native, ERC20, and NFT", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            MiniHeaderAction(
                glyph = Glyph.Plus,
                label = "ERC20",
                onClick = controller::openEvmAddToken,
                enabled = !controller.evmLookupLoading,
            )
            Spacer(Modifier.width(Space.x3))
            MiniHeaderAction(
                glyph = Glyph.Plus,
                label = "NFT",
                onClick = controller::openEvmAddNft,
                enabled = !controller.evmLookupLoading,
            )
            Spacer(Modifier.width(Space.x3))
            MiniHeaderAction(
                glyph = Glyph.Refresh,
                label = "Sync",
                onClick = controller::refreshEvmDashboard,
                enabled = !controller.evmLoading && !controller.evmAssetsLoading,
                loading = controller.evmLoading || controller.evmAssetsLoading,
            )
        }
        Spacer(Modifier.height(Space.x5))
        EvmNativeAssetRow(controller)
        if (controller.evmAssetsLoading) {
            Spacer(Modifier.height(Space.x6))
            StateBanner("Loading EVM assets...", isError = false)
        }
        controller.evmTokens.forEach { token ->
            Spacer(Modifier.height(Space.x6))
            EvmTokenRow(
                token = token,
                onSend = { controller.openEvmSend(token) },
                onRemove = { controller.removeEvmToken(token) },
            )
        }
        controller.evmNfts.forEach { nft ->
            Spacer(Modifier.height(Space.x6))
            EvmNftRow(
                nft = nft,
                onSend = if (nft.owned) { { controller.openEvmSend(nft = nft) } } else null,
                onRemove = { controller.removeEvmNft(nft) },
            )
        }
    }
}

@Composable
private fun EvmActivityScreen(controller: PoctraController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeEvmActivity)
            Spacer(Modifier.width(Space.x6))
            Column(Modifier.weight(1f)) {
                Text("EVM Activity", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
                Text(
                    controller.evmAccount?.address?.let(::shortAddress) ?: controller.activeEvmNetwork?.name ?: "Recent transactions",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            MiniHeaderAction(
                glyph = Glyph.Refresh,
                label = "Refresh",
                onClick = controller::refreshEvmHistory,
                enabled = !controller.evmHistoryLoading,
                loading = controller.evmHistoryLoading,
            )
        }
        Spacer(Modifier.height(Space.x8))
        EvmActivityList(controller)
    }
}

@Composable
private fun EvmSendScreen(controller: PoctraController) {
    val selectedToken = controller.evmSelectedSendToken
    val selectedNft = controller.evmSelectedSendNft
    val network = controller.activeEvmNetwork
    val account = controller.evmAccount
    val symbol = selectedNft?.symbol ?: selectedToken?.symbol ?: network?.symbol ?: "ETH"
    var recipient by remember(selectedToken?.address, selectedNft?.contractAddress, selectedNft?.tokenId) { mutableStateOf("") }
    var amount by remember(selectedToken?.address, selectedNft?.contractAddress, selectedNft?.tokenId) {
        mutableStateOf(if (selectedNft != null) "1" else "")
    }
    var recipientError by remember { mutableStateOf<String?>(null) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    val recipientLookup = rememberEvmRecipientLookup(controller, recipient)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DetailScreenHeader(
            title = "Send $symbol",
            description = when {
                selectedNft != null -> "Send ${selectedNft.symbol} NFT from your derived EVM account."
                selectedToken != null -> "Send ${selectedToken.symbol} ERC20 from your derived EVM account."
                else -> "Send ${network?.symbol ?: "native EVM coin"} from your derived EVM account."
            },
            onBack = controller::closeEvmSend,
            backEnabled = !controller.evmSending,
        )
        Spacer(Modifier.height(Space.x8))
        TokenLikeRow(
            glyph = when {
                selectedNft != null -> Glyph.Browser
                selectedToken != null -> Glyph.Assets
                else -> Glyph.Evm
            },
            iconRes = selectedToken?.let(::evmTokenIconRes) ?: evmNativeIconRes(network?.symbol),
            title = selectedNft?.name ?: selectedToken?.let(::evmTokenTitle) ?: controller.evmNativeAssetName(),
            subtitle = selectedNft?.let { "${it.symbol} #${it.tokenId}" }
                ?: selectedToken?.address?.let { "Contract ${shortAddress(it)}" }
                ?: account?.address?.let { "Account ${shortAddress(it)}" }
                ?: "Derived EVM account",
            amount = selectedNft?.let { if (it.owned) "Owned" else "Missing" } ?: selectedToken?.balance ?: account?.balance ?: "--",
            symbol = if (selectedNft != null) "NFT" else symbol,
            onRemove = null,
            onSend = null,
        )
        Spacer(Modifier.height(Space.x5))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Network", color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            NetworkBadge(network?.name ?: "EVM", state = if (account?.connected == false) "failed" else "connected")
        }
        Spacer(Modifier.height(Space.x7))
        EvmSendPanel(
            controller = controller,
            selectedToken = selectedToken,
            selectedNft = selectedNft,
            recipient = recipient,
            amount = amount,
            recipientError = recipientError,
            amountError = amountError,
            formError = formError,
            recipientLookup = recipientLookup,
            onRecipientChange = {
                recipient = it
                recipientError = null
                formError = null
            },
            onAmountChange = {
                amount = it
                amountError = null
                formError = null
            },
            onMax = {
                if (selectedNft == null) amount = controller.evmMaxAmount(selectedToken)
                amountError = null
                formError = null
            },
            onSubmit = {
                val recipientForSubmit = recipientLookup.resolution?.address ?: recipient
                recipientError = null
                amountError = null
                formError = null
                when {
                    recipient.isBlank() -> recipientError = "Recipient address is required."
                    recipientLookup.loading -> recipientError = "Wait until recipient lookup finishes."
                    isValidEvmNameInput(recipient) && recipientLookup.resolution == null -> {
                        recipientError = recipientLookup.error ?: "Resolve ENS first."
                    }
                    !isValidEvmRecipientInput(recipientForSubmit) -> recipientError = "Enter a valid EVM address or ENS name."
                    selectedNft == null && amount.isBlank() -> amountError = "Amount is required."
                    selectedNft == null && amount.replace(",", ".").toDoubleOrNull() == null -> amountError = "Enter a valid $symbol amount."
                    selectedNft != null && !selectedNft.owned -> formError = "This NFT is not owned by the active EVM account."
                    account?.connected == false -> formError = account.message ?: "EVM RPC is not connected."
                    else -> controller.prepareEvmSendDialog(
                        recipientAddress = recipientForSubmit,
                        amountText = if (selectedNft != null) "1" else amount,
                        recipientIdentityName = recipientLookup.resolution?.name,
                    )
                }
            },
        )
        val evmSendRisks = controller.evmSendPreflightItems(
            recipientInput = recipient,
            resolvedAddress = recipientLookup.resolution?.address,
            amount = if (selectedNft != null) "0" else amount,
        )
        if (evmSendRisks.isNotEmpty()) {
            Spacer(Modifier.height(Space.x6))
            Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
                evmSendRisks.forEach { SendPreflightRow(it) }
            }
        }
        Spacer(Modifier.height(Space.x6))
        EvmGasInfoCard(controller = controller, selectedToken = selectedToken, selectedNft = selectedNft)
    }
}

@Composable
private fun EvmReceiveScreen(controller: PoctraController) {
    val clipboard = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DetailScreenHeader(
            title = "Receive EVM",
            description = "Use this address for native EVM coins, ERC20 tokens, and NFTs.",
            onBack = controller::closeEvmReceive,
        )
        Spacer(Modifier.height(Space.x8))
        EvmReceivePanel(controller, clipboard)
    }
}

@Composable
private fun EvmNetworkSettingsScreen(controller: PoctraController) {
    val activeNetwork = controller.activeEvmNetwork
    val networks = controller.evmNetworks

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DetailScreenHeader(
            title = "EVM Network",
            description = "Choose the active EVM chain for balances, assets, gas, and transactions.",
            onBack = controller::closeEvmNetworkSettings,
            backEnabled = !controller.busy,
        )
        Spacer(Modifier.height(Space.x8))
        EvmNetworkPanel(
            controller = controller,
            selectedNetwork = activeNetwork ?: networks.firstOrNull(),
            onSelectedNetworkChange = { id ->
                if (id != activeNetwork?.id) controller.selectEvmNetwork(id)
            },
        )
        Spacer(Modifier.height(Space.x6))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
            PoctraButton(
                text = "Edit Network",
                onClick = { activeNetwork?.let(controller::openEvmEditNetwork) },
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.Ghost,
                leadingGlyph = Glyph.Pencil,
                enabled = activeNetwork != null && !controller.busy && !controller.evmSending,
            )
            PoctraButton(
                text = "Add Network",
                onClick = controller::openEvmAddNetwork,
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.Ghost,
                leadingGlyph = Glyph.Plus,
                enabled = !controller.busy && !controller.evmSending,
            )
        }
    }
}

@Composable
private fun EvmAddNetworkScreen(controller: PoctraController) {
    val editing = controller.evmEditingNetwork
    val isEditing = editing != null
    var name by remember(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    var chainId by remember(editing?.id) { mutableStateOf(editing?.chainId?.toString().orEmpty()) }
    var symbol by remember(editing?.id) { mutableStateOf(editing?.symbol.orEmpty()) }
    var rpcUrl by remember(editing?.id) { mutableStateOf(editing?.rpcUrl?.let(controller::displayEvmRpcUrl).orEmpty()) }
    var explorerUrl by remember(editing?.id) { mutableStateOf(editing?.explorerUrl.orEmpty()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DetailScreenHeader(
            title = if (isEditing) "Edit Network" else "Add Custom Network",
            description = if (isEditing) {
                "Update this EVM network endpoint, symbol, and explorer."
            } else {
                "Add an EVM-compatible network with its RPC and explorer endpoint."
            },
            onBack = controller::closeEvmAddNetwork,
            backEnabled = !controller.busy,
        )
        Spacer(Modifier.height(Space.x8))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        ) {
            Column(Modifier.padding(Space.x7)) {
                Text("Network Details", color = TextTertiary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(Space.x6))
                PoctraTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Network Name",
                    placeholder = "BNB Smart Chain",
                    singleLine = true,
                )
                Spacer(Modifier.height(Space.x5))
                PoctraTextField(
                    value = chainId,
                    onValueChange = { value -> chainId = value.filter { it.isDigit() } },
                    label = "Chain ID",
                    placeholder = "56",
                    singleLine = true,
                    keyboardType = KeyboardType.Number,
                )
                Spacer(Modifier.height(Space.x5))
                PoctraTextField(
                    value = symbol,
                    onValueChange = { value -> symbol = value.uppercase().take(12) },
                    label = "Symbol",
                    placeholder = "BNB",
                    singleLine = true,
                )
                Spacer(Modifier.height(Space.x5))
                PoctraTextField(
                    value = rpcUrl,
                    onValueChange = { rpcUrl = it },
                    label = "RPC",
                    placeholder = "https://rpc.example.org",
                    singleLine = true,
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(Modifier.height(Space.x5))
                PoctraTextField(
                    value = explorerUrl,
                    onValueChange = { explorerUrl = it },
                    label = "Explorer",
                    placeholder = "https://explorer.example.org",
                    singleLine = true,
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(Modifier.height(Space.x7))
                PoctraButton(
                    text = if (isEditing) "Save Network" else "Add Network",
                    onClick = {
                        controller.saveEvmNetworkDetails(
                            name = name,
                            chainIdInput = chainId,
                            symbol = symbol,
                            rpcUrl = rpcUrl,
                            explorerUrl = explorerUrl,
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    leadingGlyph = Glyph.Check,
                    enabled = !controller.busy && !controller.evmSending,
                    loading = controller.busy,
                )
            }
        }
    }
}

@Composable
private fun EvmBalanceHero(controller: PoctraController) {
    val network = controller.activeEvmNetwork
    val account = controller.evmAccount
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            account?.ensName?.let { ens ->
                Text(
                    ens,
                    color = TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(SurfaceRaised.copy(alpha = 0.16f))
                        .padding(horizontal = Space.x5, vertical = Space.x3),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(Space.x4))
            }
            Text(
                account?.address?.let(::shortAddress) ?: "0x...",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.md))
                    .background(SurfaceMuted.copy(alpha = 0.68f))
                    .padding(horizontal = Space.x5, vertical = Space.x3),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(Space.x4))
            NetworkBadge(
                network?.name ?: "EVM",
                state = if (account?.connected == false) "failed" else "connected",
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.height(Space.x8))
        if (controller.evmLoading && account == null) {
            CircularProgressIndicator(color = SurfaceRaised, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(Space.x6))
            Text("Syncing EVM account", color = TextSecondary)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    account?.balance ?: "--",
                    color = TextTertiary,
                    style = MaterialTheme.typography.headlineLarge,
                    maxLines = 1,
                )
                Spacer(Modifier.width(Space.x5))
                Text(
                    network?.symbol ?: "ETH",
                    color = TextSecondary,
                    modifier = Modifier.padding(bottom = Space.x5),
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(Space.x4))
            Text(account?.fiatEstimate ?: "~ $-- USD", color = TextSecondary)
            account?.message?.takeIf { account.connected.not() }?.let {
                Spacer(Modifier.height(Space.x4))
                Text(it, color = ErrorRed, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun EvmSendPanel(
    controller: PoctraController,
    selectedToken: EvmToken?,
    selectedNft: EvmNft?,
    recipient: String,
    amount: String,
    recipientError: String?,
    amountError: String?,
    formError: String?,
    recipientLookup: RecipientLookupUiState,
    onRecipientChange: (String) -> Unit,
    onAmountChange: (String) -> Unit,
    onMax: () -> Unit,
    onSubmit: () -> Unit,
) {
    val network = controller.activeEvmNetwork
    val symbol = selectedNft?.symbol ?: selectedToken?.symbol ?: network?.symbol ?: "ETH"
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceMuted.copy(alpha = 0.54f)),
    ) {
        Column(Modifier.padding(Space.x7)) {
            RecipientLookupField(
                value = recipient,
                onValueChange = onRecipientChange,
                label = "Recipient 0x address or ENS",
                placeholder = "foo.eth or 0x address",
                lookupState = recipientLookup,
                glyph = Glyph.Evm,
                onClear = { onRecipientChange("") },
                isError = recipientError != null,
                supportingText = recipientError,
            )
            Spacer(Modifier.height(Space.x6))
            if (selectedNft == null) {
                PoctraTextField(
                    value = amount,
                    onValueChange = { raw -> onAmountChange(raw.filter { it.isDigit() || it == '.' || it == ',' }) },
                    label = "Amount $symbol",
                    singleLine = true,
                    keyboardType = KeyboardType.Decimal,
                    isError = amountError != null,
                    supportingText = amountError,
                )
                Spacer(Modifier.height(Space.x4))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Available ${selectedToken?.balance ?: controller.evmAccount?.balance ?: "--"} $symbol",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Max",
                        modifier = Modifier
                            .clip(RoundedCornerShape(Radius.sm))
                            .clickable(enabled = !controller.evmSending, onClick = onMax)
                            .padding(horizontal = Space.x4, vertical = Space.x2),
                        color = if (!controller.evmSending) SurfaceRaised else TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(Radius.sm),
                    colors = CardDefaults.cardColors(containerColor = SurfaceBase),
                ) {
                    Column(Modifier.padding(horizontal = Space.x5, vertical = Space.x4)) {
                        DetailLine("NFT", selectedNft.name)
                        DetailSeparator()
                        DetailLine("Token ID", selectedNft.tokenId)
                        DetailSeparator()
                        DetailLine("Amount", "1 NFT")
                    }
                }
            }
            if (formError != null) {
                Spacer(Modifier.height(Space.x6))
                StateBanner(formError, isError = true, compact = true)
            }
            Spacer(Modifier.height(Space.x7))
            PoctraButton(
                text = if (selectedNft == null) "Send" else "Send NFT",
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                leadingGlyph = Glyph.Send,
                loading = controller.evmSending,
                enabled = !controller.evmSending,
            )
        }
    }
}

@Composable
private fun EvmGasInfoCard(controller: PoctraController, selectedToken: EvmToken?, selectedNft: EvmNft?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceMuted.copy(alpha = 0.54f)),
    ) {
        Column(Modifier.padding(Space.x7)) {
            DetailLine("Estimated Gas", controller.evmGasInfo(selectedToken, selectedNft))
        }
    }
}

@Composable
private fun EvmReceivePanel(controller: PoctraController, clipboard: androidx.compose.ui.platform.ClipboardManager) {
    val address = controller.evmAccount?.address.orEmpty()
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(Modifier.padding(Space.x7), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Receive EVM", color = TextTertiary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Space.x7))
            if (address.isNotBlank()) QrCodeView(text = address)
            Spacer(Modifier.height(Space.x7))
            Text(address.ifBlank { "--" }, color = TextTertiary, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Space.x6))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
                PoctraButton(
                    text = "Copy",
                    onClick = {
                        if (address.isBlank()) controller.showError("EVM address is not ready.")
                        else {
                            clipboard.setText(AnnotatedString(address))
                            controller.showMessage("EVM address copied.")
                        }
                    },
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.Ghost,
                    leadingGlyph = Glyph.Copy,
                )
                PoctraButton(
                    text = "Explorer",
                    onClick = controller::openEvmExplorerAddress,
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.Ghost,
                    leadingGlyph = Glyph.Browser,
                )
            }
        }
    }
}

@Composable
private fun EvmAddTokenScreen(controller: PoctraController) {
    var tokenAddress by remember { mutableStateOf("") }
    val network = controller.activeEvmNetwork

    LaunchedEffect(Unit) {
        controller.refreshEvmAccount()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DetailScreenHeader(
            title = "Add ERC20",
            description = "Add a token on ${network?.name ?: "the active EVM network"}.",
            onBack = controller::closeEvmAddToken,
            backEnabled = !controller.evmLookupLoading,
        )
        Spacer(Modifier.height(Space.x8))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        ) {
            Column(Modifier.padding(Space.x7)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Token Contract", color = TextTertiary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text(
                        network?.name ?: "EVM Network",
                        color = SurfaceRaised,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.height(Space.x6))
                PoctraTextField(
                    value = tokenAddress,
                    onValueChange = { tokenAddress = it.trim() },
                    label = "Token contract 0x...",
                    singleLine = true,
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(Modifier.height(Space.x5))
                PoctraButton(
                    text = "Lookup Token",
                    onClick = { controller.lookupEvmToken(tokenAddress) },
                    modifier = Modifier.fillMaxWidth(),
                    loading = controller.evmLookupLoading,
                    enabled = tokenAddress.isNotBlank() && !controller.evmLookupLoading,
                    leadingGlyph = Glyph.Refresh,
                )
                controller.evmTokenPreview?.let { token ->
                    Spacer(Modifier.height(Space.x6))
                    TokenLikeRow(
                        glyph = Glyph.Assets,
                        iconRes = evmTokenIconRes(token),
                        title = evmTokenTitle(token),
                        subtitle = shortAddress(token.address),
                        amount = token.balance,
                        symbol = token.symbol,
                        onRemove = null,
                        onSend = null,
                    )
                    Spacer(Modifier.height(Space.x6))
                    PoctraButton(
                        text = "Add Token",
                        onClick = controller::addEvmTokenPreview,
                        modifier = Modifier.fillMaxWidth(),
                        leadingGlyph = Glyph.Check,
                        enabled = !controller.evmLookupLoading,
                    )
                }
            }
        }
    }
}

@Composable
private fun EvmAddNftScreen(controller: PoctraController) {
    var contractAddress by remember { mutableStateOf("") }
    var tokenId by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        controller.refreshEvmAccount()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DetailScreenHeader(
            title = "Add NFT",
            description = "Lookup an NFT contract and token ID before adding it locally.",
            onBack = controller::closeEvmAddNft,
            backEnabled = !controller.evmLookupLoading,
        )
        Spacer(Modifier.height(Space.x8))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        ) {
            Column(Modifier.padding(Space.x7)) {
                Text("NFT Details", color = TextTertiary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(Space.x6))
                PoctraTextField(
                    value = contractAddress,
                    onValueChange = { contractAddress = it.trim() },
                    label = "NFT contract 0x...",
                    singleLine = true,
                    keyboardType = KeyboardType.Uri,
                )
                Spacer(Modifier.height(Space.x5))
                PoctraTextField(
                    value = tokenId,
                    onValueChange = { value -> tokenId = value.filter(Char::isDigit) },
                    label = "Token ID",
                    singleLine = true,
                    keyboardType = KeyboardType.Number,
                )
                Spacer(Modifier.height(Space.x5))
                PoctraButton(
                    text = "Lookup NFT",
                    onClick = { controller.lookupEvmNft(contractAddress, tokenId) },
                    modifier = Modifier.fillMaxWidth(),
                    loading = controller.evmLookupLoading,
                    enabled = contractAddress.isNotBlank() && tokenId.isNotBlank() && !controller.evmLookupLoading,
                    leadingGlyph = Glyph.Refresh,
                )
                controller.evmNftPreview?.let { nft ->
                    Spacer(Modifier.height(Space.x6))
                    EvmNftRow(nft = nft, onRemove = null)
                    Spacer(Modifier.height(Space.x6))
                    PoctraButton(
                        text = "Add NFT",
                        onClick = controller::addEvmNftPreview,
                        modifier = Modifier.fillMaxWidth(),
                        leadingGlyph = Glyph.Check,
                        enabled = !controller.evmLookupLoading,
                    )
                }
            }
        }
    }
}

@Composable
private fun EvmNetworkPanel(
    controller: PoctraController,
    selectedNetwork: EvmNetwork?,
    onSelectedNetworkChange: (String) -> Unit,
) {
    val networks = controller.evmNetworks
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(Modifier.padding(Space.x7)) {
            Text("Active Network", color = TextTertiary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Space.x6))
            EvmNetworkDropdown(
                networks = networks,
                selectedNetwork = selectedNetwork,
                onSelected = onSelectedNetworkChange,
                enabled = !controller.busy && !controller.evmSending,
            )
            selectedNetwork?.let { network ->
                Spacer(Modifier.height(Space.x6))
                DetailLine("Network Name", network.name)
                DetailSeparator()
                DetailLine("Chain ID", network.chainId.toString())
                DetailSeparator()
                DetailLine("Symbol", network.symbol)
                DetailSeparator()
                DetailLine("RPC", controller.displayEvmRpcUrl(network.rpcUrl))
                DetailSeparator()
                DetailLine("Explorer", network.explorerUrl)
            }
        }
    }
}

@Composable
private fun EvmNetworkDropdown(
    networks: List<EvmNetwork>,
    selectedNetwork: EvmNetwork?,
    onSelected: (String) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md))
                .background(SurfaceBase)
                .clickable(enabled = enabled && networks.isNotEmpty()) { expanded = true }
                .padding(horizontal = Space.x6, vertical = Space.x5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(SurfaceRaised.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.Wifi, modifier = Modifier.size(17.dp), color = SurfaceRaised)
            }
            Spacer(Modifier.width(Space.x5))
            Column(Modifier.weight(1f)) {
                Text(
                    selectedNetwork?.name ?: "Select network",
                    color = TextTertiary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    selectedNetwork?.let { "Chain ID ${it.chainId} - ${it.symbol}" } ?: "No EVM network available",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Space.x4))
            GlyphIcon(
                if (expanded) Glyph.ChevronUp else Glyph.ChevronDown,
                modifier = Modifier.size(16.dp),
                color = TextSecondary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceStrong),
        ) {
            networks.forEach { network ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                network.name,
                                color = TextTertiary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                "Chain ID ${network.chainId} - ${network.symbol}",
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(if (network.id == selectedNetwork?.id) SurfaceRaised else SurfaceMuted),
                            contentAlignment = Alignment.Center,
                        ) {
                            GlyphIcon(
                                if (network.id == selectedNetwork?.id) Glyph.Check else Glyph.Evm,
                                modifier = Modifier.size(14.dp),
                                color = if (network.id == selectedNetwork?.id) PrimaryContent else TextTertiary,
                            )
                        }
                    },
                    trailingIcon = {
                        Text(
                            if (network.isTestnet) "test" else "main",
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(SurfaceMuted)
                                .padding(horizontal = Space.x3, vertical = Space.x1),
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    onClick = {
                        onSelected(network.id)
                        expanded = false
                    },
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun EvmNativeAssetRow(controller: PoctraController) {
    val network = controller.activeEvmNetwork
    val account = controller.evmAccount
    TokenLikeRow(
        glyph = Glyph.Evm,
        iconRes = evmNativeIconRes(network?.symbol),
        title = controller.evmNativeAssetName(),
        subtitle = account?.fiatEstimate ?: "~ $-- USD",
        amount = if (controller.evmLoading) "--" else account?.balance ?: "--",
        symbol = network?.symbol ?: "ETH",
        onRemove = null,
        onSend = controller::openEvmSend,
    )
}

@Composable
private fun EvmTokenRow(token: EvmToken, onSend: () -> Unit, onRemove: () -> Unit) {
    val isPinnedDefault = evmTokenIsPinnedDefault(token)
    TokenLikeRow(
        glyph = Glyph.Assets,
        iconRes = evmTokenIconRes(token),
        title = evmTokenTitle(token),
        subtitle = shortAddress(token.address),
        amount = token.balance,
        symbol = token.symbol,
        onRemove = if (isPinnedDefault) null else onRemove,
        onSend = onSend,
    )
}

private fun evmTokenTitle(token: EvmToken): String =
    if (token.name.equals(token.symbol, ignoreCase = true)) {
        token.symbol
    } else {
        "${token.symbol} (${token.name})"
    }

private fun evmTokenIconRes(token: EvmToken): Int? = when {
    token.address.equals(TradeAsset.WOCT.address, ignoreCase = true) ||
        token.symbol.equals("wOCT", ignoreCase = true) -> com.poctra.wallet.R.drawable.octra_logo
    token.address.equals(TradeAsset.USDT.address, ignoreCase = true) ||
        token.symbol.equals("USDT", ignoreCase = true) -> com.poctra.wallet.R.drawable.ic_usdt
    else -> null
}

private fun evmNativeIconRes(symbol: String?): Int? =
    if (symbol.equals("ETH", ignoreCase = true)) com.poctra.wallet.R.drawable.ic_eth else null

private fun evmTokenIsPinnedDefault(token: EvmToken): Boolean {
    if (token.chainId != 1L) return false
    return token.address.equals(TradeAsset.WOCT.address, ignoreCase = true) ||
        token.address.equals(TradeAsset.USDT.address, ignoreCase = true)
}

@Composable
private fun EvmNftRow(nft: EvmNft, onSend: (() -> Unit)? = null, onRemove: (() -> Unit)?) {
    TokenLikeRow(
        glyph = Glyph.Browser,
        title = nft.name,
        subtitle = "${nft.symbol} #${nft.tokenId}${if (nft.owned) "" else " - not owned"}",
        amount = if (nft.owned) "Owned" else "Missing",
        symbol = "NFT",
        onRemove = onRemove,
        onSend = onSend,
    )
}

@Composable
private fun TokenLikeRow(
    glyph: Glyph,
    iconRes: Int? = null,
    title: String,
    subtitle: String,
    amount: String,
    symbol: String,
    onRemove: (() -> Unit)?,
    onSend: (() -> Unit)?,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onSend != null) Modifier.clickable(onClick = onSend) else Modifier),
        shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = SurfaceMuted.copy(alpha = 0.58f)),
    ) {
        Row(Modifier.padding(Space.x7), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(SurfaceRaised.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                if (iconRes != null) {
                    Image(
                        painter = painterResource(iconRes),
                        contentDescription = symbol,
                        modifier = Modifier.size(24.dp),
                    )
                } else {
                    GlyphIcon(glyph, modifier = Modifier.size(22.dp), color = TextTertiary)
                }
            }
            Spacer(Modifier.width(Space.x6))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(amount, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(symbol, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    onRemove?.let {
                        Text(
                            "Remove",
                            modifier = Modifier
                                .clip(RoundedCornerShape(Radius.sm))
                                .clickable(onClick = it)
                                .padding(horizontal = Space.x3, vertical = Space.x1),
                            color = ErrorRed,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EvmAddTokenDialog(
    controller: PoctraController,
    tokenAddress: String,
    onTokenAddressChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!controller.evmLookupLoading) onClose() },
        modifier = Modifier.fillMaxWidth(FullWidthDialogFraction),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = SurfaceStrong,
        title = { Text("Add ERC20", color = TextTertiary, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
            PoctraTextField(
                value = tokenAddress,
                onValueChange = onTokenAddressChange,
                label = "Token contract 0x...",
                singleLine = true,
            )
            Spacer(Modifier.height(Space.x5))
            PoctraButton(
                text = "Lookup",
                onClick = { controller.lookupEvmToken(tokenAddress) },
                modifier = Modifier.fillMaxWidth(),
                loading = controller.evmLookupLoading,
                enabled = !controller.evmLookupLoading,
                leadingGlyph = Glyph.Refresh,
            )
            controller.evmTokenPreview?.let { token ->
                Spacer(Modifier.height(Space.x6))
                TokenLikeRow(
                    glyph = Glyph.Assets,
                    iconRes = evmTokenIconRes(token),
                    title = evmTokenTitle(token),
                    subtitle = shortAddress(token.address),
                    amount = token.balance,
                    symbol = token.symbol,
                    onRemove = null,
                    onSend = null,
                )
            }
            }
        },
        dismissButton = {
            PoctraButton(
                text = "Cancel",
                onClick = onClose,
                variant = ButtonVariant.Ghost,
                compact = true,
                enabled = !controller.evmLookupLoading,
            )
        },
        confirmButton = {
            PoctraButton(
                text = "Add Token",
                onClick = {
                    controller.addEvmTokenPreview()
                    onClose()
                },
                compact = true,
                leadingGlyph = Glyph.Plus,
                enabled = controller.evmTokenPreview != null && !controller.evmLookupLoading,
            )
        },
    )
}

@Composable
private fun EvmAddNftDialog(
    controller: PoctraController,
    contractAddress: String,
    tokenId: String,
    onContractAddressChange: (String) -> Unit,
    onTokenIdChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!controller.evmLookupLoading) onClose() },
        modifier = Modifier.fillMaxWidth(FullWidthDialogFraction),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = SurfaceStrong,
        title = { Text("Add NFT", color = TextTertiary, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 500.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
            PoctraTextField(
                value = contractAddress,
                onValueChange = onContractAddressChange,
                label = "NFT contract 0x...",
                singleLine = true,
            )
            Spacer(Modifier.height(Space.x6))
            PoctraTextField(
                value = tokenId,
                onValueChange = onTokenIdChange,
                label = "Token ID",
                singleLine = true,
                keyboardType = KeyboardType.Number,
            )
            Spacer(Modifier.height(Space.x5))
            PoctraButton(
                text = "Lookup",
                onClick = { controller.lookupEvmNft(contractAddress, tokenId) },
                modifier = Modifier.fillMaxWidth(),
                loading = controller.evmLookupLoading,
                enabled = !controller.evmLookupLoading,
                leadingGlyph = Glyph.Refresh,
            )
            controller.evmNftPreview?.let { nft ->
                Spacer(Modifier.height(Space.x6))
                EvmNftRow(nft = nft, onRemove = null)
            }
            }
        },
        dismissButton = {
            PoctraButton(
                text = "Cancel",
                onClick = onClose,
                variant = ButtonVariant.Ghost,
                compact = true,
                enabled = !controller.evmLookupLoading,
            )
        },
        confirmButton = {
            PoctraButton(
                text = "Add NFT",
                onClick = {
                    controller.addEvmNftPreview()
                    onClose()
                },
                compact = true,
                leadingGlyph = Glyph.Plus,
                enabled = controller.evmNftPreview != null && !controller.evmLookupLoading,
            )
        },
    )
}

@Composable
private fun EvmActivityList(controller: PoctraController) {
    if (controller.evmHistoryLoading && controller.evmTransactions.isEmpty()) {
        StateBanner("Loading EVM activity...", isError = false)
    } else if (controller.evmTransactions.isEmpty()) {
        EmptyPanel("No activity yet")
    } else {
        if (controller.evmHistoryLoading) {
            StateBanner("Refreshing EVM activity...", isError = false)
            Spacer(Modifier.height(Space.x5))
        }
        Column(Modifier.fillMaxWidth()) {
            controller.visibleEvmTransactions.forEachIndexed { index, tx ->
                EvmActivityRow(
                    tx = tx,
                    address = controller.evmAccount?.address.orEmpty(),
                    onClick = { controller.openEvmTransaction(tx) },
                )
                if (index != controller.visibleEvmTransactions.lastIndex) {
                    Spacer(Modifier.height(Space.x2))
                }
            }
        }
        if (controller.evmActivityCanLoadMore) {
            Spacer(Modifier.height(Space.x5))
            InlineListAction(
                text = "Load More",
                onClick = controller::loadMoreEvmActivity,
                loading = controller.evmHistoryLoading,
                enabled = !controller.evmHistoryLoading,
            )
        }
    }
}

@Composable
private fun EvmActivityRow(tx: EvmTransaction, address: String, onClick: () -> Unit) {
    val outgoing = tx.isOutgoing(address)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Space.x6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(if (outgoing) SurfaceMuted else SurfaceRaised.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(if (outgoing) Glyph.Send else Glyph.Receive, modifier = Modifier.size(20.dp), color = TextTertiary)
        }
        Spacer(Modifier.width(Space.x7))
        Column(Modifier.weight(1f)) {
            Text(
                text = if (outgoing) "Sent" else "Received",
                color = TextTertiary,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${if (outgoing) "to" else "from"} ${shortAddress(if (outgoing) tx.to else tx.from)}",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = "${if (outgoing) "-" else "+"}${tx.value}",
                color = if (outgoing) TextTertiary else SurfaceRaised,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(tx.symbol, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun isValidEvmRecipientInput(value: String): Boolean {
    val clean = value.trim()
    return clean.matches(Regex("^0x[a-fA-F0-9]{40}$")) ||
        clean.matches(Regex("^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$"))
}

private fun isValidEvmNameInput(value: String): Boolean {
    val parts = value.trim().split('.').filter { it.isNotBlank() }
    return parts.size >= 2 &&
        parts.last().length >= 2 &&
        value.trim().matches(Regex("^[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+$"))
}

private fun isValidOctraRecipientInput(value: String): Boolean {
    val clean = value.trim()
    return isValidOctraAddress(clean) || isValidOctraIdentityName(clean)
}

internal fun isValidOctraAddress(value: String): Boolean = value.startsWith("oct") && value.length == 47

internal fun isValidOctraIdentityName(value: String): Boolean {
    return OctraIdentityName.isValidDomain(value)
}

internal data class RecipientResolutionPreview(
    val name: String,
    val address: String,
)

internal data class RecipientLookupUiState(
    val resolution: RecipientResolutionPreview? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@Composable
internal fun rememberOctraRecipientLookup(
    controller: PoctraController,
    value: String,
): RecipientLookupUiState {
    var state by remember { mutableStateOf(RecipientLookupUiState()) }
    LaunchedEffect(value, controller.activeIdentityProvider?.id, controller.activeRpcNetwork?.id) {
        val query = value.trim()
        state = RecipientLookupUiState()
        if (isValidOctraAddress(query)) {
            controller.identityNameForAddress(query)?.let { cachedName ->
                state = RecipientLookupUiState(
                    resolution = RecipientResolutionPreview(
                        name = cachedName,
                        address = query,
                    ),
                )
                return@LaunchedEffect
            }
            delay(350)
            val identityName = runCatching { controller.previewOctraIdentityName(query) }.getOrNull()
            if (query == value.trim() && !identityName.isNullOrBlank()) {
                state = RecipientLookupUiState(
                    resolution = RecipientResolutionPreview(
                        name = identityName,
                        address = query,
                    ),
                )
            }
            return@LaunchedEffect
        }
        if (!isValidOctraIdentityName(query)) return@LaunchedEffect
        delay(650)
        state = RecipientLookupUiState(loading = true)
        val result = runCatching { controller.previewOctraIdentityRecipient(query) }
        state = if (query == value.trim()) {
            result.fold(
                onSuccess = { address ->
                    if (address.isNullOrBlank()) {
                        RecipientLookupUiState(error = "$query could not be resolved.")
                    } else {
                        RecipientLookupUiState(
                            resolution = RecipientResolutionPreview(
                                name = query.lowercase(),
                                address = address,
                            ),
                        )
                    }
                },
                onFailure = { error -> RecipientLookupUiState(error = error.message ?: "$query could not be resolved.") },
            )
        } else {
            RecipientLookupUiState()
        }
    }
    return state
}

@Composable
private fun rememberEvmRecipientLookup(
    controller: PoctraController,
    value: String,
): RecipientLookupUiState {
    var state by remember { mutableStateOf(RecipientLookupUiState()) }
    LaunchedEffect(value, controller.activeEvmNetwork?.id) {
        val query = value.trim()
        state = RecipientLookupUiState()
        if (query.matches(Regex("^0x[a-fA-F0-9]{40}$"))) {
            delay(350)
            val identityName = runCatching { controller.previewEvmRecipientIdentity(query) }.getOrNull()
            if (query == value.trim() && !identityName.isNullOrBlank()) {
                state = RecipientLookupUiState(
                    resolution = RecipientResolutionPreview(
                        name = identityName,
                        address = query,
                    ),
                )
            }
            return@LaunchedEffect
        }
        if (!isValidEvmNameInput(query)) return@LaunchedEffect
        delay(650)
        state = RecipientLookupUiState(loading = true)
        val result = runCatching { controller.previewEvmRecipient(query) }
        state = if (query == value.trim()) {
            result.fold(
                onSuccess = { address ->
                    if (address.isNullOrBlank()) {
                        RecipientLookupUiState(error = "$query could not be resolved.")
                    } else {
                        RecipientLookupUiState(
                            resolution = RecipientResolutionPreview(
                                name = query.lowercase(),
                                address = address,
                            ),
                        )
                    }
                },
                onFailure = { error -> RecipientLookupUiState(error = error.message ?: "$query could not be resolved.") },
            )
        } else {
            RecipientLookupUiState()
        }
    }
    return state
}

@Composable
internal fun RecipientLookupField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    placeholder: String,
    lookupState: RecipientLookupUiState,
    glyph: Glyph,
    onClear: () -> Unit,
    directPreviewName: String? = null,
    showDirectOctraAddressPreview: Boolean = false,
    isError: Boolean = false,
    supportingText: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    accentColor: Color = SurfaceRaised,
) {
    val cleanValue = value.trim()
    val directResolution = if (showDirectOctraAddressPreview && isValidOctraAddress(cleanValue)) {
        RecipientResolutionPreview(
            name = directPreviewName?.takeIf { it.isNotBlank() } ?: cleanValue,
            address = cleanValue,
        )
    } else {
        null
    }
    val resolution = lookupState.resolution ?: directResolution
    if (resolution != null) {
        ResolvedRecipientCard(
            resolution = resolution,
            glyph = glyph,
            onClear = onClear,
            accentColor = accentColor,
        )
        return
    }
    PoctraTextField(
        value = value,
        onValueChange = onValueChange,
        label = label,
        placeholder = placeholder,
        singleLine = true,
        isError = isError || lookupState.error != null,
        supportingText = supportingText ?: when {
            lookupState.loading -> "Resolving recipient..."
            lookupState.error != null -> lookupState.error
            else -> null
        },
        trailingContent = trailingContent,
        focusColor = accentColor,
    )
}

@Composable
private fun ResolvedRecipientCard(
    resolution: RecipientResolutionPreview,
    glyph: Glyph,
    onClear: () -> Unit,
    accentColor: Color = SurfaceRaised,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = accentColor.copy(alpha = 0.12f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.x5, vertical = Space.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(accentColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(glyph, modifier = Modifier.size(17.dp), color = accentColor)
            }
            Spacer(Modifier.width(Space.x4))
            Column(Modifier.weight(1f)) {
                Text(
                    resolution.name,
                    color = TextTertiary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    shortAddress(resolution.address),
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Space.x3))
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(SurfaceMuted)
                    .clickable(onClick = onClear)
                    .semantics { contentDescription = "Change recipient" },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.Revoke, modifier = Modifier.size(11.dp), color = TextTertiary)
            }
        }
    }
}

@Composable
private fun EvmNetworkOption(
    network: EvmNetwork,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(if (selected) SurfaceRaised.copy(alpha = 0.16f) else SurfaceStrong)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(Space.x6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(if (selected) SurfaceRaised else SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(if (selected) Glyph.Check else Glyph.Evm, modifier = Modifier.size(18.dp), color = if (selected) PrimaryContent else TextTertiary)
        }
        Spacer(Modifier.width(Space.x5))
        Column(Modifier.weight(1f)) {
            Text(network.name, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Chain ID ${network.chainId} - ${network.symbol}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            if (network.isTestnet) "TEST" else "MAIN",
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.sm))
                .background(SurfaceMuted)
                .padding(horizontal = Space.x4, vertical = Space.x2),
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun TransactionDetailsScreen(controller: PoctraController) {
    val tx = controller.selectedTransaction
    val activeAddress = controller.wallet?.address.orEmpty()
    val clipboard = LocalClipboardManager.current

    if (tx == null) {
        LoadingScreen()
        return
    }

    val outgoing = tx.isOutgoing(activeAddress)
    val fromIdentityName = controller.identityNameForAddress(tx.from)
    val toIdentityName = controller.identityNameForAddress(tx.to)
    val explorerHost = controller.activeRpcNetwork?.explorerUrl.orEmpty()
    val txExplorerUrl = explorerUrl(explorerHost, "tx.html", "hash", tx.hash)
    val fromExplorerUrl = explorerUrl(explorerHost, "address.html", "addr", tx.from)
    val toExplorerUrl = explorerUrl(explorerHost, "address.html", "addr", tx.to)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeTransactionDetails)
            Spacer(Modifier.width(Space.x6))
            Text("Transaction Details", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(Space.x8))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        ) {
            Row(
                modifier = Modifier.padding(Space.x7),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(SurfaceRaised.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(
                        if (outgoing) Glyph.Send else Glyph.Receive,
                        modifier = Modifier.size(23.dp),
                        color = SurfaceRaised,
                    )
                }
                Spacer(Modifier.width(Space.x6))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (outgoing) "Sent OCT" else "Received OCT",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.height(Space.x2))
                    Text(
                        text = "${if (outgoing) "-" else "+"}${formatOctAmount(tx.amount)} OCT",
                        color = TextTertiary,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                NetworkBadge(tx.status)
            }
        }
        Spacer(Modifier.height(Space.x5))
        TransactionDetailCard(title = "Route", glyph = Glyph.Go) {
                DetailLine(
                    label = "From",
                    value = fromIdentityName ?: tx.from,
                    secondaryValue = tx.from.takeIf { fromIdentityName != null },
                    valueColor = if (fromIdentityName != null) SurfaceRaised else TextTertiary,
                    actionLabel = if (fromExplorerUrl != null) "View" else null,
                    onAction = fromExplorerUrl?.let { url -> { controller.openDAppBrowser("Address", url) } },
                )
                DetailSeparator()
                DetailLine(
                    label = "To",
                    value = toIdentityName ?: tx.to,
                    secondaryValue = tx.to.takeIf { toIdentityName != null },
                    valueColor = if (toIdentityName != null) SurfaceRaised else TextTertiary,
                    actionLabel = if (toExplorerUrl != null) "View" else null,
                    onAction = toExplorerUrl?.let { url -> { controller.openDAppBrowser("Address", url) } },
                )
        }
        Spacer(Modifier.height(Space.x5))
        TransactionDetailCard(title = "Transaction", glyph = Glyph.History) {
                DetailLine("Hash", tx.hash)
                DetailSeparator()
                DetailLine("Operation", tx.opType)
                DetailSeparator()
                DetailLine("Timestamp", formatTxTime(tx.timestamp))
                tx.message?.let {
                    DetailSeparator()
                    DetailLine("Message", it)
                }
        }
        Spacer(Modifier.height(Space.x5))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
            PoctraButton(
                text = "Explorer",
                onClick = { txExplorerUrl?.let { controller.openDAppBrowser("Transaction", it) } },
                modifier = Modifier.weight(1f),
                leadingGlyph = Glyph.External,
                enabled = txExplorerUrl != null,
                compact = true,
            )
            PoctraButton(
                text = "Copy Hash",
                onClick = {
                    clipboard.setText(AnnotatedString(tx.hash))
                    controller.showMessage("Transaction hash copied.")
                },
                modifier = Modifier.weight(1f),
                leadingGlyph = Glyph.Copy,
                variant = ButtonVariant.Ghost,
                compact = true,
            )
        }
    }
}

@Composable
private fun TransactionDetailCard(
    title: String,
    glyph: Glyph,
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(Modifier.padding(horizontal = Space.x7, vertical = Space.x5)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(SurfaceRaised.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(glyph, modifier = Modifier.size(15.dp), color = SurfaceRaised)
                }
                Spacer(Modifier.width(Space.x4))
                Text(title, color = TextTertiary, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(Space.x3))
            DetailSeparator()
            content()
        }
    }
}

@Composable
fun DetailLine(
    label: String,
    value: String,
    secondaryValue: String? = null,
    valueColor: Color = TextTertiary,
    actionLabel: String? = null,
    actionGlyph: Glyph? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.x5),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            if (actionLabel != null && onAction != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.md))
                        .clickable(onClick = onAction)
                        .padding(horizontal = Space.x4, vertical = Space.x2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        actionLabel,
                        color = SurfaceRaised,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    actionGlyph?.let { glyph ->
                        Spacer(Modifier.width(Space.x2))
                        GlyphIcon(glyph, Modifier.size(14.dp), SurfaceRaised)
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.x2))
        Text(value.ifBlank { "-" }, color = valueColor, style = MaterialTheme.typography.bodyMedium)
        secondaryValue?.takeIf { it.isNotBlank() }?.let { secondary ->
            Spacer(Modifier.height(Space.x1))
            Text(
                text = secondary,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
        }
    }
}

@Composable
fun DetailSeparator() {
    Spacer(Modifier.height(Space.x3))
}

@Composable
private fun AddAssetScreen(controller: PoctraController) {
    var address by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeAddAsset, enabled = !controller.busy)
            Spacer(Modifier.width(Space.x6))
            Text("Add Asset", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(34.dp))
        Image(
            painter = androidx.compose.ui.res.painterResource(com.poctra.wallet.R.drawable.octra_logo),
            contentDescription = "Octra",
            modifier = Modifier.size(70.dp),
        )
        Spacer(Modifier.height(Space.x8))
        Text("Add OCS01 token", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.x4))
        Text(
            "Paste an Octra contract address to preview token metadata and your balance.",
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.x8))
        PoctraTextField(
            value = address,
            onValueChange = { address = it.trim() },
            label = "oct... contract address",
            singleLine = true,
        )
        Spacer(Modifier.height(Space.x6))
        PoctraButton(
            text = "Lookup Token",
            onClick = { controller.lookupAsset(address) },
            modifier = Modifier.fillMaxWidth(),
            loading = controller.assetLookupLoading,
            enabled = !controller.assetLookupLoading,
            leadingGlyph = Glyph.Refresh,
        )

        controller.assetPreview?.let { token ->
            Spacer(Modifier.height(Space.x8))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.md),
                colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            ) {
                Column(Modifier.padding(Space.x7)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(Radius.md))
                                .background(SurfaceRaised.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(token.symbol.take(2).uppercase(), color = SurfaceRaised, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(Space.x6))
                        Column(Modifier.weight(1f)) {
                            Text(token.symbol, color = TextTertiary, fontWeight = FontWeight.Bold)
                            Text(token.name, color = TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text("${token.decimals} dec", color = TextSecondary)
                    }
                    Spacer(Modifier.height(Space.x7))
                    Text("Your balance", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    Text("${token.formattedBalance} ${token.symbol}", color = TextTertiary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(Space.x7))
                    Text(shortAddress(token.address), color = TextSecondary)
                    Spacer(Modifier.height(Space.x7))
                    PoctraButton(
                        text = "Add to Assets",
                        onClick = controller::addPreviewAsset,
                        modifier = Modifier.fillMaxWidth(),
                        leadingGlyph = Glyph.Plus,
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(controller: PoctraController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DetailScreenHeader(
            title = "Settings",
            description = "Network, security, and wallet preferences.",
            onBack = controller::closeSettings,
        )
        Spacer(Modifier.height(Space.x8))
        Column(verticalArrangement = Arrangement.spacedBy(Space.x5)) {
            SettingsSectionTitle("Wallet")
            SettingsDestinationButton(
                title = "Portfolio",
                subtitle = if (controller.portfolioHoldings.isEmpty()) "Track wallet value" else "${controller.portfolioHoldings.size} holdings",
                glyph = Glyph.Assets,
                onClick = controller::openPortfolioAnalytics,
            )
            SettingsDestinationButton(
                title = "Activity Center",
                subtitle = if (controller.unifiedActivity.isEmpty()) "Review all wallet activity" else "${controller.unifiedActivity.size} recent events",
                glyph = Glyph.History,
                onClick = controller::openActivityCenter,
            )
            SettingsDestinationButton(
                title = "Address Book",
                subtitle = if (controller.addressBookContacts.isEmpty()) "Save reusable recipients" else "${controller.addressBookContacts.size} saved recipients",
                glyph = Glyph.AddressBook,
                onClick = controller::openAddressBook,
            )
            SettingsSectionTitle("Security")
            SettingsDestinationButton(
                title = "Security Center",
                subtitle = securityCenterSubtitle(controller),
                glyph = Glyph.Fingerprint,
                onClick = controller::openSecurityCenter,
            )
            SettingsDestinationButton(
                title = "Update Security",
                subtitle = "Current method: ${controller.currentSecurityMethod?.label ?: "Not set"}",
                glyph = Glyph.Pin,
                onClick = controller::openSecuritySettings,
            )
            SettingsDestinationButton(
                title = "Backup Keys",
                subtitle = "View mnemonic or private key",
                glyph = Glyph.Eye,
                onClick = controller::openBackupSettings,
            )
            SettingsDestinationButton(
                title = "Recovery Plan",
                subtitle = "Backup, lock, and restore readiness",
                glyph = Glyph.Download,
                onClick = controller::openRecoveryPlan,
            )
            SettingsDestinationButton(
                title = "Approvals",
                subtitle = "Review ERC20 spending permissions",
                glyph = Glyph.Revoke,
                onClick = controller::openApprovalDashboard,
            )
            SettingsSectionTitle("Networks")
            SettingsDestinationButton(
                title = "RPC Management",
                subtitle = controller.activeRpcNetwork?.let { "${it.name} - ${it.networkType}" } ?: controller.currentRpcUrl,
                glyph = Glyph.Wifi,
                onClick = controller::openRpcManagement,
            )
            SettingsDestinationButton(
                title = "Octra Identity Provider",
                subtitle = controller.activeIdentityProvider?.let {
                    "AML - ${shortAddress(it.contractAddress)}"
                } ?: "Resolve foo.oct recipients",
                glyph = Glyph.Browser,
                onClick = controller::openIdentityProviderSettings,
            )
            SettingsSectionTitle("dApps")
            SettingsDestinationButton(
                title = "Connected dApps",
                subtitle = if (controller.dApps.isEmpty()) "No connected dApps" else "${controller.dApps.size} connected",
                glyph = Glyph.Explore,
                onClick = controller::openConnectedDApps,
            )
            SettingsSectionTitle("App")
            SettingsDestinationButton(
                title = "Dark Mode",
                subtitle = if (controller.darkMode) "Dark mode enabled" else "Light mode enabled",
                glyph = if (controller.darkMode) Glyph.Moon else Glyph.Sun,
                onClick = controller::toggleDarkMode,
            )
            SettingsDestinationButton(
                title = "About",
                subtitle = "poctra wallet information",
                glyph = Glyph.Info,
                onClick = controller::openAbout,
            )
        }
    }
}

@Composable
private fun LegacySettingsScreen(activity: Activity, controller: PoctraController) {
    var showReauthPin by remember { mutableStateOf(false) }
    var pendingRevoke by remember { mutableStateOf<DAppSession?>(null) }
    var pendingPermissions by remember { mutableStateOf<DAppSession?>(null) }
    var expandedPanel by remember { mutableStateOf<SettingsPanel?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeSettings)
            Spacer(Modifier.width(Space.x6))
            Text("Settings", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(34.dp))
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(SurfaceRaised.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Settings, modifier = Modifier.size(34.dp), color = SurfaceRaised)
        }
        Spacer(Modifier.height(Space.x8))
        Text("Manage wallet", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.x4))
        Text(
            "Update network, security, backups, and app connections.",
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.x8))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        ) {
            Column {
                SettingsMenuButton(
                    title = "RPC Management",
                    subtitle = controller.activeRpcNetwork?.let { "${it.name} - ${it.networkType}" } ?: controller.currentRpcUrl,
                    glyph = Glyph.Refresh,
                    selected = expandedPanel == SettingsPanel.Rpc,
                    onClick = { expandedPanel = if (expandedPanel == SettingsPanel.Rpc) null else SettingsPanel.Rpc },
                )
                if (expandedPanel == SettingsPanel.Rpc) {
                    RpcManagementPanel(controller)
                }
                Separator()
                SettingsMenuButton(
                    title = "Update Security",
                    subtitle = "Current method: ${controller.currentSecurityMethod?.label ?: "Not set"}",
                    glyph = Glyph.Pin,
                    selected = expandedPanel == SettingsPanel.Security,
                    onClick = { expandedPanel = if (expandedPanel == SettingsPanel.Security) null else SettingsPanel.Security },
                )
                if (expandedPanel == SettingsPanel.Security) {
                    SettingsInlinePanel {
                        PoctraButton(
                            text = "Change Security Method",
                            onClick = {
                                when (controller.currentSecurityMethod) {
                                    SecurityMethod.Biometric -> startSecurityUpdateBiometric(activity, controller)
                                    SecurityMethod.Pin -> showReauthPin = true
                                    null -> controller.showError("No current security method found.")
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            variant = ButtonVariant.Ghost,
                            enabled = !controller.busy,
                        )
                    }
                }
                Separator()
                SettingsMenuButton(
                    title = "Backup Keys",
                    subtitle = "View mnemonic or private key",
                    glyph = Glyph.Eye,
                    selected = expandedPanel == SettingsPanel.Backup,
                    onClick = { expandedPanel = if (expandedPanel == SettingsPanel.Backup) null else SettingsPanel.Backup },
                )
                if (expandedPanel == SettingsPanel.Backup) {
                    SettingsInlinePanel {
                        Text(
                            "This reveals sensitive recovery material. Never share it with anyone.",
                            color = TextSecondary,
                        )
                        Spacer(Modifier.height(Space.x6))
                        PoctraButton(
                            text = "Open Backup Keys",
                            onClick = controller::openBackupSettings,
                            modifier = Modifier.fillMaxWidth(),
                            variant = ButtonVariant.Ghost,
                            leadingGlyph = Glyph.Eye,
                        )
                    }
                }
                Separator()
                SettingsMenuButton(
                    title = "Dark Mode",
                    subtitle = if (controller.darkMode) "Enabled" else "Disabled",
                    glyph = Glyph.Check,
                    selected = controller.darkMode,
                    onClick = controller::toggleDarkMode,
                    showChevron = false,
                )
                Separator()
                SettingsMenuButton(
                    title = "Connected dApps",
                    subtitle = if (controller.dApps.isEmpty()) "No connected dApps" else "${controller.dApps.size} connected",
                    glyph = Glyph.Explore,
                    selected = expandedPanel == SettingsPanel.DApps,
                    onClick = { expandedPanel = if (expandedPanel == SettingsPanel.DApps) null else SettingsPanel.DApps },
                )
                if (expandedPanel == SettingsPanel.DApps) {
                    SettingsInlinePanel {
                        if (controller.dApps.isEmpty()) {
                            EmptyPanel("No connected dApps")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(Space.x6)) {
                                controller.dApps.forEach { session ->
                                    DAppRow(
                                        session = session,
                                        onRevoke = { pendingRevoke = session },
                                        onPermissionsClick = { pendingPermissions = session },
                                        riskLabels = controller.dAppRiskItems(session)
                                            .filter { it.level.ordinal >= ConsumerRiskLevel.Warning.ordinal }
                                            .map { it.title },
                                    )
                                }
                            }
                        }
                    }
                }
                Separator()
                SettingsMenuButton(
                    title = "About",
                    subtitle = "poctra wallet information",
                    glyph = Glyph.Info,
                    selected = false,
                    onClick = controller::openAbout,
                    showChevron = false,
                )
            }
        }
    }

    if (showReauthPin) {
        ReauthPinDialog(
            busy = controller.busy,
            onDismiss = { showReauthPin = false },
            onConfirm = {
                showReauthPin = false
                controller.beginSecurityUpdateWithPin(it)
            },
        )
    }

    pendingRevoke?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingRevoke = null },
            modifier = Modifier.fillMaxWidth(FullWidthDialogFraction),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            containerColor = SurfaceStrong,
            title = { Text("Revoke dApp?", color = TextTertiary) },
            text = {
                Text(
                    "This removes ${session.name}'s wallet access. It must connect again to regain permissions.",
                    color = TextSecondary,
                )
            },
            confirmButton = {
                PoctraButton(
                    text = "Revoke",
                    onClick = {
                        controller.revokeDApp(session)
                        pendingRevoke = null
                    },
                    variant = ButtonVariant.Danger,
                )
            },
            dismissButton = {
                TextButton(onClick = { pendingRevoke = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        )
    }

    pendingPermissions?.let { session ->
        DAppPermissionsDialog(
            session = session,
            onDismiss = { pendingPermissions = null },
        )
    }
}

private enum class SettingsPanel {
    Rpc,
    Security,
    Backup,
    DApps,
}

@Composable
private fun SettingsSectionTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.x5, bottom = Space.x1),
        color = TextSecondary,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun SettingsDestinationButton(
    title: String,
    subtitle: String,
    glyph: Glyph,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.988f else 1f,
        animationSpec = tween(130, easing = FastOutSlowInEasing),
        label = "settingsDestinationScale",
    )
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        interactionSource = interactionSource,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.x6, vertical = Space.x5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(SurfaceRaised.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(glyph, modifier = Modifier.size(19.dp), color = SurfaceRaised)
            }
            Spacer(Modifier.width(Space.x5))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextTertiary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(Space.x1))
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Space.x4))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(SurfaceMuted.copy(alpha = 0.76f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.Go, modifier = Modifier.size(14.dp), color = TextSecondary)
            }
        }
    }
}

@Composable
private fun DetailScreenHeader(
    title: String,
    description: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
    headerAction: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircleButton(Glyph.Back, "Back", onBack, enabled = backEnabled)
        Spacer(Modifier.width(Space.x6))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = TextTertiary,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (description.isNotBlank()) {
                Spacer(Modifier.height(Space.x1))
                Text(
                    text = description,
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                    ),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        headerAction?.invoke()
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
fun SettingsDetailScreen(
    title: String,
    description: String,
    glyph: Glyph,
    onBack: () -> Unit,
    headerAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DetailScreenHeader(
            title = title,
            description = description,
            onBack = onBack,
            headerAction = headerAction,
        )
        Spacer(Modifier.height(Space.x8))
        content()
    }
}

@Composable
private fun RpcManagementScreen(controller: PoctraController) {
    SettingsDetailScreen(
        title = "RPC Management",
        description = "Choose an active network or add a custom RPC endpoint.",
        glyph = Glyph.Wifi,
        onBack = controller::closeRpcManagement,
    ) {
        RpcManagementPanel(controller)
    }
}

@Composable
private fun SecuritySettingsScreen(activity: Activity, controller: PoctraController) {
    var showReauthPin by remember { mutableStateOf(false) }
    SettingsDetailScreen(
        title = "Security",
        description = "Change the method used to protect this wallet on this device.",
        glyph = Glyph.Pin,
        onBack = controller::closeSettingsDetail,
    ) {
        Text("Current method", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(Space.x3))
        Text(controller.currentSecurityMethod?.label ?: "Not set", color = TextTertiary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(Space.x7))
        PoctraButton(
            text = "Change Security Method",
            onClick = {
                when (controller.currentSecurityMethod) {
                    SecurityMethod.Biometric -> startSecurityUpdateBiometric(activity, controller)
                    SecurityMethod.Pin -> showReauthPin = true
                    null -> controller.showError("No current security method found.")
                }
            },
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Ghost,
            leadingGlyph = Glyph.Pin,
            enabled = !controller.busy,
        )
    }
    if (showReauthPin) {
        ReauthPinDialog(
            busy = controller.busy,
            onDismiss = { showReauthPin = false },
            onConfirm = {
                showReauthPin = false
                controller.beginSecurityUpdateWithPin(it)
            },
        )
    }
}

@Composable
private fun BackupSettingsScreen(activity: Activity, controller: PoctraController) {
    val wallet = controller.wallet
    val secrets = wallet?.let(::backupSecretsFor).orEmpty()
    val clipboard = LocalClipboardManager.current
    val clipboardScope = rememberCoroutineScope()
    var visibleSecretIds by remember(wallet?.address) { mutableStateOf<Set<String>>(emptySet()) }
    var showReauthPin by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<PendingBackupKeyAction?>(null) }

    fun copySecret(currentSecret: BackupSecret) {
        if (currentSecret.value.isBlank()) {
            controller.showError("${currentSecret.label} is not available for this wallet.")
        } else {
            clipboard.setText(AnnotatedString(currentSecret.value))
            clipboardScope.launch {
                delay(60_000)
                if (clipboard.getText()?.text == currentSecret.value) {
                    clipboard.setText(AnnotatedString(""))
                }
            }
            controller.showMessage("${currentSecret.label} copied. Clipboard clears in 60 seconds.")
        }
    }

    fun completeVerifiedAction(action: BackupKeyAction, currentSecret: BackupSecret) {
        when (action) {
            BackupKeyAction.Reveal -> visibleSecretIds = visibleSecretIds + currentSecret.id
            BackupKeyAction.Copy -> copySecret(currentSecret)
        }
    }

    fun requestSensitiveAccess(action: BackupKeyAction, currentSecret: BackupSecret) {
        if (currentSecret.value.isBlank()) {
            controller.showError("${currentSecret.label} is not available for this wallet.")
            return
        }
        when (controller.currentSecurityMethod) {
            SecurityMethod.Biometric -> startBackupRevealBiometric(activity, controller) {
                completeVerifiedAction(action, currentSecret)
            }
            SecurityMethod.Pin -> {
                pendingAction = PendingBackupKeyAction(action = action, secretId = currentSecret.id)
                showReauthPin = true
            }
            null -> controller.showError("No current security method found.")
        }
    }

    SettingsDetailScreen(
        title = "Backup Keys",
        description = "View the recovery material for the active wallet.",
        glyph = Glyph.Eye,
        onBack = controller::closeSettingsDetail,
    ) {
        if (wallet == null || secrets.isEmpty()) {
            EmptyPanel("Wallet locked")
        } else {
            StateBanner(
                text = "Sensitive information. Anyone with these keys can control this wallet and derived accounts.",
                isError = true,
            )
            secrets.forEach { secret ->
                val visible = secret.id in visibleSecretIds
                Spacer(Modifier.height(Space.x5))
                SensitiveKeyCard(
                    title = secret.label,
                    description = secret.description,
                    value = secret.value,
                    visible = visible,
                    onToggle = {
                        if (visible) {
                            visibleSecretIds = visibleSecretIds - secret.id
                        } else {
                            requestSensitiveAccess(BackupKeyAction.Reveal, secret)
                        }
                    },
                    onCopy = {
                        if (visible) copySecret(secret) else requestSensitiveAccess(BackupKeyAction.Copy, secret)
                    },
                )
            }
            Spacer(Modifier.height(Space.x5))
            BackupWalletSummaryCard(
                name = controller.walletDisplayName(wallet.address),
                address = wallet.address,
                type = walletTypeLabel(wallet),
                evmAddress = controller.evmAccount?.address,
                evmNetwork = controller.activeEvmNetwork?.name,
            )
        }
    }

    if (showReauthPin && wallet != null) {
        ReauthPinDialog(
            busy = controller.busy,
            onDismiss = {
                showReauthPin = false
                pendingAction = null
            },
            onConfirm = { pin ->
                val action = pendingAction
                val currentSecret = action?.let { pending ->
                    secrets.firstOrNull { it.id == pending.secretId }
                }
                showReauthPin = false
                pendingAction = null
                if (action != null && currentSecret != null) {
                    controller.confirmSecurityWithPin(pin) {
                        completeVerifiedAction(action.action, currentSecret)
                    }
                }
            },
        )
    }
}

private enum class BackupKeyAction {
    Reveal,
    Copy,
}

private data class PendingBackupKeyAction(
    val action: BackupKeyAction,
    val secretId: String,
)

private data class BackupSecret(
    val id: String,
    val label: String,
    val value: String,
    val description: String,
)

private fun backupSecretsFor(wallet: Wallet): List<BackupSecret> {
    val mnemonic = wallet.mnemonic.orEmpty()
    return buildList {
        if (
            wallet.type != WalletType.ImportedPrivateKey &&
            wallet.type != WalletType.ImportedEthereumPrivateKey &&
            mnemonic.isNotBlank()
        ) {
            add(
                BackupSecret(
                    id = "mnemonic",
                    label = "Mnemonic Phrase",
                    value = mnemonic,
                    description = "Restore the same poctra wallet from this seed phrase.",
                ),
            )
        }
        add(
            BackupSecret(
                id = "octra-base64",
                label = "Octra Private Key (Base64)",
                value = wallet.privateKeyBase64,
                description = "Restore this Octra account in Octra-compatible wallets.",
            ),
        )
        evmPrivateKeyHexFor(wallet)?.let { evmKey ->
            add(
                BackupSecret(
                    id = "evm-hex",
                    label = "EVM Private Key (Hex)",
                    value = evmKey,
                    description = "Import this derived EVM account into MetaMask or another EVM wallet.",
                ),
            )
        }
    }
}

private fun evmPrivateKeyHexFor(wallet: Wallet): String? =
    runCatching { EvmCrypto().exportPrivateKeyHex(wallet) }.getOrNull()

@Composable
private fun SensitiveKeyCard(
    title: String,
    description: String,
    value: String,
    visible: Boolean,
    onToggle: () -> Unit,
    onCopy: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(Modifier.padding(horizontal = Space.x5, vertical = Space.x4)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = TextTertiary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        description,
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(Space.x4))
                SensitiveSmallIconButton(
                    glyph = if (visible) Glyph.EyeOff else Glyph.Eye,
                    contentDescription = if (visible) "Hide backup key" else "Show backup key",
                    onClick = onToggle,
                )
                Spacer(Modifier.width(Space.x4))
                SensitiveSmallIconButton(
                    glyph = Glyph.Copy,
                    contentDescription = "Copy backup key",
                    onClick = onCopy,
                )
            }
            Spacer(Modifier.height(Space.x4))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(SurfaceBase)
                    .padding(horizontal = Space.x4, vertical = Space.x3),
            ) {
                Text(
                    text = if (visible) value.ifBlank { "-" } else maskedSensitiveValue(value),
                    color = if (visible) TextTertiary else TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    minLines = if (visible && value.contains(" ")) 3 else 1,
                    maxLines = if (visible && value.contains(" ")) 6 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BackupWalletSummaryCard(
    name: String,
    address: String,
    type: String,
    evmAddress: String?,
    evmNetwork: String?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(Modifier.padding(horizontal = Space.x5, vertical = Space.x4)) {
            Text(
                "Wallet Info",
                color = TextTertiary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Space.x3))
            BackupWalletInfoRow("Name", name)
            BackupWalletInfoRow("Address", address)
            BackupWalletInfoRow("Type", type)
            BackupWalletInfoRow("EVM", evmAddress ?: "Not available")
            BackupWalletInfoRow("Chain", evmNetwork ?: "Not selected")
        }
    }
}

@Composable
private fun BackupWalletInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            modifier = Modifier.width(58.dp),
            maxLines = 1,
        )
        Text(
            value.ifBlank { "-" },
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SensitiveSmallIconButton(
    glyph: Glyph,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(SurfaceMuted)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, modifier = Modifier.size(12.dp), color = TextTertiary)
    }
}

private fun maskedSensitiveValue(value: String): String {
    if (value.isBlank()) return "Hidden"
    val words = value.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return if (words.size > 1) {
        words.joinToString(" ") { "****" }
    } else {
        "*".repeat(value.length.coerceIn(12, 48))
    }
}

@Composable
private fun AppearanceSettingsScreen(controller: PoctraController) {
    SettingsDetailScreen(
        title = "Appearance",
        description = "Choose the color mode used throughout poctra.",
        glyph = if (controller.darkMode) Glyph.Moon else Glyph.Sun,
        onBack = controller::closeSettingsDetail,
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = controller::toggleDarkMode),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Space.x7),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(if (controller.darkMode) SurfaceRaised else SurfaceMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.Check, modifier = Modifier.size(22.dp), color = if (controller.darkMode) PrimaryContent else TextTertiary)
                }
                Spacer(Modifier.width(Space.x6))
                Column(Modifier.weight(1f)) {
                    Text("Dark Mode", color = TextTertiary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(Space.x2))
                    Text(if (controller.darkMode) "Enabled" else "Disabled", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    if (controller.darkMode) "On" else "Off",
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(SurfaceRaised)
                        .padding(horizontal = Space.x5, vertical = Space.x3),
                    color = PrimaryContent,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ConnectedDAppsScreen(controller: PoctraController) {
    var pendingRevoke by remember { mutableStateOf<DAppSession?>(null) }
    var pendingPermissions by remember { mutableStateOf<DAppSession?>(null) }
    SettingsDetailScreen(
        title = "Connected dApps",
        description = "Review sites that currently have access to this wallet.",
        glyph = Glyph.Explore,
        onBack = controller::closeSettingsDetail,
    ) {
        if (controller.dApps.isEmpty()) {
            EmptyPanel("No connected dApps")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x6)) {
                controller.dApps.forEach { session ->
                    DAppRow(
                        session = session,
                        onRevoke = { pendingRevoke = session },
                        onPermissionsClick = { pendingPermissions = session },
                        riskLabels = controller.dAppRiskItems(session)
                            .filter { it.level.ordinal >= ConsumerRiskLevel.Warning.ordinal }
                            .map { it.title },
                    )
                }
            }
        }
    }
    pendingRevoke?.let { session ->
        AlertDialog(
            onDismissRequest = { pendingRevoke = null },
            modifier = Modifier.fillMaxWidth(FullWidthDialogFraction),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            containerColor = SurfaceStrong,
            title = { Text("Revoke dApp?", color = TextTertiary) },
            text = { Text("This removes ${session.name}'s wallet access.", color = TextSecondary) },
            confirmButton = {
                PoctraButton(
                    text = "Revoke",
                    onClick = {
                        controller.revokeDApp(session)
                        pendingRevoke = null
                    },
                    variant = ButtonVariant.Danger,
                )
            },
            dismissButton = {
                TextButton(onClick = { pendingRevoke = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        )
    }
    pendingPermissions?.let { session ->
        DAppPermissionsDialog(
            session = session,
            onDismiss = { pendingPermissions = null },
        )
    }
}

@Composable
private fun DAppPermissionsDialog(
    session: DAppSession,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(FullWidthDialogFraction),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = SurfaceStrong,
        title = { Text("${session.name} permissions", color = TextTertiary, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                Text(
                    session.url,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Space.x6))
                if (session.permissions.isEmpty()) {
                    Text("No permissions recorded.", color = TextSecondary)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
                        session.permissions.forEach { permission ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Radius.md))
                                    .background(SurfaceBase)
                                    .padding(horizontal = Space.x5, vertical = Space.x4),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                GlyphIcon(Glyph.Check, modifier = Modifier.size(14.dp), color = SurfaceRaised)
                                Spacer(Modifier.width(Space.x4))
                                Text(
                                    formatDAppPermission(permission),
                                    color = TextTertiary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            PoctraButton(
                text = "Done",
                onClick = onDismiss,
                compact = true,
                leadingGlyph = Glyph.Check,
            )
        },
    )
}

private fun formatDAppPermission(permission: String): String {
    return titleCaseLabel(permission)
}

@Composable
private fun IdentityProviderSettingsScreen(controller: PoctraController) {
    val activeNetwork = controller.activeRpcNetwork?.networkType ?: "devnet"
    val activeId = controller.activeIdentityProvider?.id

    SettingsDetailScreen(
        title = "Octra Identity Provider",
        description = "Choose an AML contract to resolve foo.oct.",
        glyph = Glyph.Browser,
        onBack = controller::closeSettingsDetail,
    ) {
        StateBanner(
            text = "Active Network: ${networkTypeLabel(activeNetwork)}",
            isError = false,
        )
        Spacer(Modifier.height(Space.x6))

        if (controller.identityProviders.isEmpty()) {
            EmptyPanel("No identity provider for ${networkTypeLabel(activeNetwork)}")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
                SettingsSectionTitle("AML contracts")
                controller.identityProviders.forEach { provider ->
                    IdentityProviderRow(
                        provider = provider,
                        active = provider.id == activeId,
                        busy = controller.busy,
                        onActivate = { controller.activateIdentityProvider(provider.id) },
                        onDelete = { controller.deleteIdentityProvider(provider.id) },
                    )
                }
            }
        }

        Spacer(Modifier.height(Space.x6))
        PoctraButton(
            text = "Add Identity Provider",
            onClick = controller::openAddIdentityProvider,
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Ghost,
            leadingGlyph = Glyph.Plus,
            enabled = !controller.busy,
        )
    }
}

@Composable
private fun AddIdentityProviderScreen(controller: PoctraController) {
    val activeNetwork = controller.activeRpcNetwork?.networkType ?: "devnet"
    var name by remember(activeNetwork) { mutableStateOf("Octra Identity") }
    var contractAddress by remember(activeNetwork) { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    SettingsDetailScreen(
        title = "Add Identity Provider",
        description = "Add an AML contract for the active Octra network.",
        glyph = Glyph.Browser,
        onBack = controller::closeAddIdentityProvider,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        ) {
            Column(Modifier.padding(Space.x7)) {
                StateBanner("Network: ${networkTypeLabel(activeNetwork)}", isError = false)
                Spacer(Modifier.height(Space.x6))
                PoctraTextField(
                    value = name,
                    onValueChange = {
                        name = it.take(32)
                        localError = null
                    },
                    label = "Provider name",
                    placeholder = "Octra Identity",
                    singleLine = true,
                )
                Spacer(Modifier.height(Space.x5))
                PoctraTextField(
                    value = contractAddress,
                    onValueChange = {
                        contractAddress = it.trim()
                        localError = null
                    },
                    label = "Contract address",
                    placeholder = "oct...",
                    singleLine = true,
                    isError = localError != null,
                    supportingText = localError,
                )
                Spacer(Modifier.height(Space.x7))
                PoctraButton(
                    text = "Save Provider",
                    onClick = {
                        val validation = when {
                            name.trim().isBlank() -> "Provider name is required."
                            !isValidOctraAddress(contractAddress.trim()) -> "Enter a valid Octra contract address."
                            else -> null
                        }
                        if (validation != null) {
                            localError = validation
                        } else {
                            controller.addIdentityProvider(
                                name = name.trim(),
                                contractAddress = contractAddress.trim(),
                                network = activeNetwork,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    leadingGlyph = Glyph.Check,
                    loading = controller.busy,
                    enabled = !controller.busy,
                )
            }
        }
    }
}

@Composable
private fun AddIdentityProviderDialog(
    controller: PoctraController,
    activeNetwork: String,
    onDismiss: () -> Unit,
) {
    var name by remember(activeNetwork) { mutableStateOf("Octra Identity") }
    var contractAddress by remember(activeNetwork) { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(FullWidthDialogFraction),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = SurfaceStrong,
        title = { Text("Add Identity Provider", color = TextTertiary, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                StateBanner("Network: ${networkTypeLabel(activeNetwork)}", isError = false)
                Spacer(Modifier.height(Space.x6))
            PoctraTextField(
                value = name,
                onValueChange = {
                    name = it.take(32)
                    localError = null
                },
                label = "Provider name",
                placeholder = "Octra Identity",
                singleLine = true,
            )
            Spacer(Modifier.height(Space.x5))
            PoctraTextField(
                value = contractAddress,
                onValueChange = {
                    contractAddress = it.trim()
                    localError = null
                },
                label = "Contract address",
                placeholder = "oct...",
                singleLine = true,
                isError = localError != null,
                supportingText = localError,
            )
            }
        },
        dismissButton = {
            PoctraButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = ButtonVariant.Ghost,
                compact = true,
                enabled = !controller.busy,
            )
        },
        confirmButton = {
            PoctraButton(
                text = "Save",
                onClick = {
                    val validation = when {
                        name.trim().isBlank() -> "Provider name is required."
                        !isValidOctraAddress(contractAddress.trim()) -> "Enter a valid Octra contract address."
                        else -> null
                    }
                    if (validation != null) {
                        localError = validation
                    } else {
                        controller.addIdentityProvider(name.trim(), contractAddress.trim(), activeNetwork)
                        onDismiss()
                    }
                },
                leadingGlyph = Glyph.Check,
                loading = controller.busy,
                enabled = !controller.busy,
                compact = true,
            )
        },
    )
}

@Composable
private fun IdentityProviderRow(
    provider: OctraIdentityProvider,
    active: Boolean,
    busy: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(if (active) SurfaceRaised.copy(alpha = 0.14f) else SurfaceMuted.copy(alpha = 0.72f))
            .padding(Space.x6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(if (active) SurfaceRaised else Color.Transparent)
                .clickable(enabled = !active && !busy, onClick = onActivate),
            contentAlignment = Alignment.Center,
        ) {
            if (active) GlyphIcon(Glyph.Check, modifier = Modifier.size(16.dp), color = PrimaryContent)
        }
        Spacer(Modifier.width(Space.x6))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(provider.name, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(Space.x4))
                OpTypeBadge("AML")
            }
            Spacer(Modifier.height(Space.x2))
            Text(provider.contractAddress, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.x5))
        Text(
            if (active) "Primary" else "Set",
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.sm))
                .clickable(enabled = !active && !busy, onClick = onActivate)
                .padding(horizontal = Space.x4, vertical = Space.x3),
            color = if (active) SurfaceRaised else TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "Delete",
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.sm))
                .clickable(enabled = !busy, onClick = onDelete)
                .padding(horizontal = Space.x4, vertical = Space.x3),
            color = ErrorRed,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
private fun AddressBookScreen(controller: PoctraController) {
    SettingsDetailScreen(
        title = "Address Book",
        description = if (controller.choosingAddressBookRecipient) "Choose a saved recipient for this transfer." else "Store reusable recipients locally on this device.",
        glyph = Glyph.AddressBook,
        onBack = controller::closeAddressBook,
    ) {
        PoctraButton(
            text = "Add Contact",
            onClick = controller::openAddAddressBookContact,
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Ghost,
            leadingGlyph = Glyph.Plus,
        )
        Spacer(Modifier.height(Space.x7))
        if (controller.addressBookContacts.isEmpty()) {
            Text(
                "No saved recipients",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = Space.x4),
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x5)) {
                controller.addressBookContacts.forEach { contact ->
                    AddressBookContactRow(
                        contact = contact,
                        onUse = if (controller.choosingAddressBookRecipient) {
                            { controller.selectAddressBookContact(contact) }
                        } else {
                            null
                        },
                        onDelete = { controller.removeAddressBookContact(contact.id) },
                    )
                }
            }
        }
        val suggestions = controller.recentRecipientSuggestions()
        if (suggestions.isNotEmpty() && !controller.choosingAddressBookRecipient) {
            Spacer(Modifier.height(Space.x8))
            Text("Recent Recipients", color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(Space.x5))
            Column(verticalArrangement = Arrangement.spacedBy(Space.x5)) {
                suggestions.forEach { suggestion ->
                    SuggestedContactRow(
                        contact = suggestion,
                        onSave = { controller.saveRecentRecipient(suggestion) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestedContactRow(contact: AddressBookContact, onSave: () -> Unit) {
    val displayLabel = contact.label.ifBlank { contact.identityName ?: shortAddress(contact.address) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.x5, vertical = Space.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(SurfaceRaised.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.History, modifier = Modifier.size(16.dp), color = SurfaceRaised)
            }
            Spacer(Modifier.width(Space.x4))
            Column(Modifier.weight(1f)) {
                Text(displayLabel, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(shortAddress(contact.address), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Spacer(Modifier.width(Space.x4))
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(SurfaceRaised)
                    .clickable(onClick = onSave)
                    .semantics { contentDescription = "Add recent recipient" },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.Plus, modifier = Modifier.size(16.dp), color = PrimaryContent)
            }
        }
    }
}

@Composable
private fun AddAddressBookContactScreen(controller: PoctraController) {
    var label by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val recipientLookup = rememberOctraRecipientLookup(controller, address)

    SettingsDetailScreen(
        title = "Add Contact",
        description = "Save an Octra address or foo.oct recipient for future transfers.",
        glyph = Glyph.AddressBook,
        onBack = controller::closeAddAddressBookContact,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        ) {
            Column(Modifier.padding(Space.x7)) {
                PoctraTextField(
                    value = label,
                    onValueChange = {
                        label = it
                        localError = null
                    },
                    label = "Contact name",
                    isError = localError != null,
                    supportingText = localError,
                )
                Spacer(Modifier.height(Space.x5))
                RecipientLookupField(
                    value = address,
                    onValueChange = {
                        address = it.trim()
                        localError = null
                    },
                    label = "Octra recipient",
                    placeholder = "foo.oct, app.foo.oct or oct address",
                    lookupState = recipientLookup,
                    glyph = Glyph.AddressBook,
                    onClear = {
                        address = ""
                        localError = null
                    },
                    directPreviewName = null,
                    showDirectOctraAddressPreview = true,
                    isError = localError != null,
                    supportingText = localError,
                )
                Spacer(Modifier.height(Space.x5))
                PoctraTextField(
                    value = note,
                    onValueChange = { note = it.take(160) },
                    label = "Note (optional)",
                    singleLine = false,
                    minLines = 2,
                )
                Spacer(Modifier.height(Space.x7))
                PoctraButton(
                    text = "Save Contact",
                    onClick = {
                        val addressForSave = recipientLookup.resolution?.address ?: address
                        localError = when {
                            label.trim().isBlank() -> "Contact name is required."
                            recipientLookup.loading -> "Wait until recipient lookup finishes."
                            isValidOctraIdentityName(address) && recipientLookup.resolution == null -> recipientLookup.error ?: "Resolve recipient first."
                            !isValidOctraRecipientInput(addressForSave) -> "Enter foo.oct or a valid Octra address."
                            else -> null
                        }
                        if (localError == null) {
                            controller.addAddressBookContact(
                                label = label,
                                address = addressForSave,
                                note = note,
                                identityName = recipientLookup.resolution?.name,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    leadingGlyph = Glyph.Check,
                )
            }
        }
    }
}

@Composable
private fun AddAddressBookContactDialog(
    controller: PoctraController,
    onDismiss: () -> Unit,
) {
    var label by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    val recipientLookup = rememberOctraRecipientLookup(controller, address)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(FullWidthDialogFraction),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = SurfaceStrong,
        title = { Text("Add Contact", color = TextTertiary, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                PoctraTextField(
                    value = label,
                    onValueChange = {
                        label = it
                        localError = null
                    },
                    label = "Contact name",
                    isError = localError != null,
                    supportingText = localError,
                )
                Spacer(Modifier.height(Space.x5))
                RecipientLookupField(
                    value = address,
                    onValueChange = {
                        address = it.trim()
                        localError = null
                    },
                    label = "Octra recipient",
                    placeholder = "foo.oct, app.foo.oct or oct address",
                    lookupState = recipientLookup,
                    glyph = Glyph.AddressBook,
                    onClear = {
                        address = ""
                        localError = null
                    },
                    directPreviewName = null,
                    showDirectOctraAddressPreview = true,
                    isError = localError != null,
                    supportingText = localError,
                )
                Spacer(Modifier.height(Space.x5))
                PoctraTextField(
                    value = note,
                    onValueChange = { note = it.take(160) },
                    label = "Note (optional)",
                    singleLine = false,
                    minLines = 2,
                )
            }
        },
        dismissButton = {
            PoctraButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = ButtonVariant.Ghost,
                compact = true,
            )
        },
        confirmButton = {
            PoctraButton(
                text = "Save",
                onClick = {
                    val addressForSave = recipientLookup.resolution?.address ?: address
                    localError = when {
                        label.trim().isBlank() -> "Contact name is required."
                        recipientLookup.loading -> "Wait until recipient lookup finishes."
                        isValidOctraIdentityName(address) && recipientLookup.resolution == null -> recipientLookup.error ?: "Resolve recipient first."
                        !isValidOctraRecipientInput(addressForSave) -> "Enter foo.oct or a valid Octra address."
                        else -> null
                    }
                    if (localError == null) {
                        controller.addAddressBookContact(
                            label = label,
                            address = addressForSave,
                            note = note,
                            identityName = recipientLookup.resolution?.name,
                        )
                        onDismiss()
                    }
                },
                leadingGlyph = Glyph.Check,
                compact = true,
            )
        },
    )
}

@Composable
private fun AddressBookContactRow(
    contact: AddressBookContact,
    onUse: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val displayLabel = contact.label.ifBlank { contact.identityName ?: shortAddress(contact.address) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onUse != null) Modifier.clickable(onClick = onUse) else Modifier),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.x5, vertical = Space.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(SurfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.AddressBook, modifier = Modifier.size(16.dp), color = TextTertiary)
            }
            Spacer(Modifier.width(Space.x4))
            Column(Modifier.weight(1f)) {
                Text(displayLabel, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                contact.identityName?.let {
                    Text(
                        "$it (octra identity)",
                        color = SurfaceRaised,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(shortAddress(contact.address), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                contact.note?.let {
                    Text(it, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.width(Space.x4))
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(ErrorRed.copy(alpha = 0.14f))
                    .clickable(onClick = onDelete)
                    .semantics { contentDescription = "Delete contact" },
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.Trash, modifier = Modifier.size(13.dp), color = ErrorRed)
            }
        }
    }
}

@Composable
private fun AboutScreen(controller: PoctraController) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeAbout)
            Spacer(Modifier.width(Space.x6))
            Text("About", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        }

        Spacer(Modifier.height(Space.x8))
        Image(
            painter = painterResource(com.poctra.wallet.R.drawable.poctra_logo),
            contentDescription = "poctra",
            modifier = Modifier.size(68.dp),
        )
        Spacer(Modifier.height(Space.x6))
        Text("poctra", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.x2))
        Text("pocket octra", color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.x8))

        AboutProjectCard()
        Spacer(Modifier.height(Space.x5))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
            AboutFeatureCard(
                title = "Self-custody",
                description = "Keys remain encrypted on your device.",
                glyph = Glyph.Key,
                modifier = Modifier.weight(1f),
            )
            AboutFeatureCard(
                title = "Multi-chain",
                description = "Manage Octra and derived EVM accounts.",
                glyph = Glyph.Evm,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(Space.x5))
        AboutFeatureCard(
            title = "Consumer wallet",
            description = "Send, receive, bridge, trade, manage assets, and connect to compatible dApps from one wallet.",
            glyph = Glyph.Wallet,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.x5))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceMuted.copy(alpha = 0.56f)),
        ) {
            Column(Modifier.padding(horizontal = Space.x6)) {
                DetailLine("Version", "1.0.0")
            }
        }
    }
}

@Composable
private fun AboutProjectCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(Modifier.padding(Space.x7)) {
            Text("Built for everyday Octra", color = TextTertiary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Space.x3))
            Text(
                "poctra is a self-custodial consumer wallet for managing Octra assets, EVM accounts, on-chain activity, and dApp connections with clear review and security flows.",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun AboutFeatureCard(
    title: String,
    description: String,
    glyph: Glyph,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceMuted.copy(alpha = 0.56f)),
    ) {
        Column(Modifier.padding(Space.x6)) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(SurfaceRaised.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(glyph, modifier = Modifier.size(16.dp), color = SurfaceRaised)
            }
            Spacer(Modifier.height(Space.x4))
            Text(title, color = TextTertiary, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(Space.x2))
            Text(description, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun RpcManagementPanel(controller: PoctraController) {
    val activeNetwork = controller.activeRpcNetwork
    val networks = controller.rpcNetworks

    SettingsInlinePanel {
        val status = controller.rpcStatus
        val statusText = when (status?.state) {
            RpcStatus.State.Connected -> "Connected${status.latestEpoch?.let { " - epoch $it" } ?: ""}"
            RpcStatus.State.Failed -> status.message ?: "Connection failed"
            else -> "Checking connection"
        }
        StateBanner(text = statusText, isError = status?.state == RpcStatus.State.Failed)
        Spacer(Modifier.height(Space.x6))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        ) {
            Column(Modifier.padding(Space.x7)) {
                Text("Active Network", color = TextTertiary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(Space.x6))
                RpcNetworkDropdown(
                    networks = networks,
                    selectedNetwork = activeNetwork ?: networks.firstOrNull(),
                    onSelected = { id ->
                        if (id != activeNetwork?.id) controller.activateRpcNetwork(id)
                    },
                    enabled = !controller.busy,
                )
                activeNetwork?.let { network ->
                    Spacer(Modifier.height(Space.x6))
                    DetailLine("Network Name", network.name)
                    DetailSeparator()
                    DetailLine("Network Type", networkTypeLabel(network.networkType))
                    DetailSeparator()
                    DetailLine("RPC", network.url)
                    DetailSeparator()
                    DetailLine("Explorer", network.explorerUrl.ifBlank { "Not set" })
                }
            }
        }
        Spacer(Modifier.height(Space.x6))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
            PoctraButton(
                text = "Edit Network",
                onClick = { activeNetwork?.let(controller::openEditRpcNetwork) },
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.Ghost,
                leadingGlyph = Glyph.Pencil,
                enabled = activeNetwork != null && !controller.busy,
            )
            PoctraButton(
                text = "Add Network",
                onClick = controller::openAddRpcNetwork,
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.Ghost,
                leadingGlyph = Glyph.Plus,
                enabled = !controller.busy,
            )
        }
        if (activeNetwork != null && networks.size > 1) {
            Spacer(Modifier.height(Space.x5))
            PoctraButton(
                text = "Delete Network",
                onClick = { controller.deleteRpcNetwork(activeNetwork.id) },
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Ghost,
                leadingGlyph = Glyph.Trash,
                enabled = !controller.busy,
            )
        }
    }
}

@Composable
private fun AddRpcNetworkScreen(controller: PoctraController) {
    val editing = controller.rpcEditingNetwork
    val isEditing = editing != null
    var name by remember(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    var url by remember(editing?.id) { mutableStateOf(editing?.url.orEmpty()) }
    var networkType by remember(editing?.id, controller.activeRpcNetwork?.networkType) {
        mutableStateOf(editing?.networkType ?: controller.activeRpcNetwork?.networkType ?: "devnet")
    }
    var explorerUrl by remember(editing?.id) { mutableStateOf(editing?.explorerUrl.orEmpty()) }
    var localError by remember { mutableStateOf<String?>(null) }

    SettingsDetailScreen(
        title = if (isEditing) "Edit RPC Network" else "Add RPC Network",
        description = if (isEditing) {
            "Update this Octra network endpoint, type, and explorer."
        } else {
            "Add a custom Octra RPC endpoint and explorer URL."
        },
        glyph = Glyph.Wifi,
        onBack = controller::closeAddRpcNetwork,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        ) {
            Column(Modifier.padding(Space.x7)) {
                PoctraTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        localError = null
                    },
                    label = "RPC name",
                    singleLine = true,
                )
                Spacer(Modifier.height(Space.x5))
                PoctraTextField(
                    value = url,
                    onValueChange = {
                        url = it.trim()
                        localError = null
                    },
                    label = "RPC endpoint URL",
                    keyboardType = KeyboardType.Uri,
                    singleLine = true,
                )
                Spacer(Modifier.height(Space.x5))
                Text("Network Type", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(Space.x4))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
                    NetworkTypeButton(
                        text = "Mainnet",
                        selected = networkType == "mainnet",
                        onClick = { networkType = "mainnet" },
                        modifier = Modifier.weight(1f),
                    )
                    NetworkTypeButton(
                        text = "Devnet",
                        selected = networkType == "devnet",
                        onClick = { networkType = "devnet" },
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(Space.x5))
                PoctraTextField(
                    value = explorerUrl,
                    onValueChange = {
                        explorerUrl = it.trim()
                        localError = null
                    },
                    label = "Explorer URL",
                    keyboardType = KeyboardType.Uri,
                    singleLine = true,
                    supportingText = localError,
                    isError = localError != null,
                )
                Spacer(Modifier.height(Space.x7))
                PoctraButton(
                    text = if (isEditing) "Save Network" else "Add Network",
                    onClick = {
                        val validation = when {
                            name.trim().isBlank() -> "RPC name is required."
                            url.trim().isBlank() -> "RPC endpoint URL is required."
                            else -> null
                        }
                        if (validation != null) {
                            localError = validation
                        } else {
                            controller.saveRpcNetworkDetails(
                                name = name,
                                url = url,
                                networkType = networkType,
                                explorerUrl = explorerUrl,
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    loading = controller.busy,
                    enabled = !controller.busy,
                    leadingGlyph = Glyph.Check,
                )
            }
        }
    }
}

@Composable
private fun AddRpcNetworkDialog(
    controller: PoctraController,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var networkType by remember(controller.activeRpcNetwork?.networkType) {
        mutableStateOf(controller.activeRpcNetwork?.networkType ?: "devnet")
    }
    var explorerUrl by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(FullWidthDialogFraction),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = SurfaceStrong,
        title = { Text("Add RPC Network", color = TextTertiary, style = MaterialTheme.typography.titleLarge) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
            PoctraTextField(
                value = name,
                onValueChange = {
                    name = it
                    localError = null
                },
                label = "RPC name",
                singleLine = true,
            )
            Spacer(Modifier.height(Space.x5))
            PoctraTextField(
                value = url,
                onValueChange = {
                    url = it.trim()
                    localError = null
                },
                label = "RPC endpoint URL",
                keyboardType = KeyboardType.Uri,
                singleLine = true,
            )
            Spacer(Modifier.height(Space.x5))
            Text("Network Type", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(Space.x4))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
                NetworkTypeButton(
                    text = "Mainnet",
                    selected = networkType == "mainnet",
                    onClick = { networkType = "mainnet" },
                    modifier = Modifier.weight(1f),
                )
                NetworkTypeButton(
                    text = "Devnet",
                    selected = networkType == "devnet",
                    onClick = { networkType = "devnet" },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(Space.x5))
            PoctraTextField(
                value = explorerUrl,
                onValueChange = {
                    explorerUrl = it.trim()
                    localError = null
                },
                label = "Explorer URL",
                keyboardType = KeyboardType.Uri,
                singleLine = true,
                supportingText = localError,
                isError = localError != null,
            )
            }
        },
        dismissButton = {
            PoctraButton(
                text = "Cancel",
                onClick = onDismiss,
                variant = ButtonVariant.Ghost,
                compact = true,
                enabled = !controller.busy,
            )
        },
        confirmButton = {
            PoctraButton(
                text = "Save",
                onClick = {
                    val validation = when {
                        name.trim().isBlank() -> "RPC name is required."
                        url.trim().isBlank() -> "RPC endpoint URL is required."
                        else -> null
                    }
                    if (validation != null) {
                        localError = validation
                    } else {
                        controller.addRpcNetwork(name.trim(), url.trim(), networkType, explorerUrl.trim())
                        onDismiss()
                    }
                },
                loading = controller.busy,
                enabled = !controller.busy,
                leadingGlyph = Glyph.Check,
                compact = true,
            )
        },
    )
}

@Composable
private fun RpcNetworkDropdown(
    networks: List<RpcNetwork>,
    selectedNetwork: RpcNetwork?,
    onSelected: (String) -> Unit,
    enabled: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(Radius.md))
                .background(SurfaceBase)
                .clickable(enabled = enabled && networks.isNotEmpty()) { expanded = true }
                .padding(horizontal = Space.x6, vertical = Space.x5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(SurfaceRaised.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.Wifi, modifier = Modifier.size(17.dp), color = SurfaceRaised)
            }
            Spacer(Modifier.width(Space.x5))
            Column(Modifier.weight(1f)) {
                Text(
                    selectedNetwork?.name ?: "Select network",
                    color = TextTertiary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    selectedNetwork?.let { networkTypeLabel(it.networkType) } ?: "No Octra network available",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Space.x4))
            GlyphIcon(
                if (expanded) Glyph.ChevronUp else Glyph.ChevronDown,
                modifier = Modifier.size(16.dp),
                color = TextSecondary,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceStrong),
        ) {
            networks.forEach { network ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                network.name,
                                color = TextTertiary,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                networkTypeLabel(network.networkType),
                                color = TextSecondary,
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(
                                    if (network.id == selectedNetwork?.id) SurfaceRaised else SurfaceMuted,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            GlyphIcon(
                                if (network.id == selectedNetwork?.id) Glyph.Check else Glyph.Wifi,
                                modifier = Modifier.size(14.dp),
                                color = if (network.id == selectedNetwork?.id) PrimaryContent else TextTertiary,
                            )
                        }
                    },
                    trailingIcon = { OpTypeBadge(networkTypeLabel(network.networkType)) },
                    onClick = {
                        onSelected(network.id)
                        expanded = false
                    },
                    enabled = enabled,
                )
            }
        }
    }
}

@Composable
private fun NetworkTypeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(if (selected) SurfaceRaised.copy(alpha = 0.16f) else SurfaceMuted.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.x6),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (selected) {
            GlyphIcon(Glyph.Check, modifier = Modifier.size(16.dp), color = TextTertiary)
            Spacer(Modifier.width(Space.x3))
        }
        Text(text, color = TextTertiary, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun SettingsMenuButton(
    title: String,
    subtitle: String,
    glyph: Glyph,
    selected: Boolean,
    onClick: () -> Unit,
    showChevron: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (selected) SurfaceRaised.copy(alpha = 0.12f) else Color.Transparent)
            .padding(Space.x7),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(if (selected) SurfaceRaised else SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, modifier = Modifier.size(22.dp), color = if (selected) PrimaryContent else TextTertiary)
        }
        Spacer(Modifier.width(Space.x6))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextTertiary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Space.x2))
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showChevron) {
            GlyphIcon(
                glyph = if (selected) Glyph.ChevronUp else Glyph.ChevronDown,
                modifier = Modifier.size(18.dp),
                color = TextSecondary,
            )
        } else if (selected) {
            GlyphIcon(Glyph.Check, modifier = Modifier.size(18.dp), color = SurfaceRaised)
        }
    }
}

@Composable
private fun SettingsInlinePanel(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceBase)
            .padding(Space.x7),
    ) {
        content()
    }
}

@Composable
private fun Separator() {
    Spacer(Modifier.height(Space.x3))
}

@Composable
private fun ReauthPinDialog(
    busy: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var pin by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(FullWidthDialogFraction),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        containerColor = SurfaceStrong,
        title = { Text("Re-authenticate", color = TextTertiary) },
        text = {
            PinField(
                value = pin,
                onValueChange = {
                    pin = it
                    localError = null
                },
                label = "Current PIN",
                error = localError,
            )
        },
        confirmButton = {
            PoctraButton(
                text = "Continue",
                onClick = {
                    if (pin.length != 6) localError = "PIN must be exactly 6 digits." else onConfirm(pin)
                },
                loading = busy,
                enabled = !busy,
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !busy) {
                Text("Cancel", color = TextSecondary)
            }
        },
    )
}

private fun startEnrollBiometric(activity: Activity, controller: PoctraController) {
    val cipher = runCatching { controller.createBiometricEnrollCipher() }.getOrElse {
        controller.showError(it.message ?: "Unable to prepare biometric encryption. Use PIN instead.")
        return
    }
    DeviceSecurity.authenticateWithCipher(
        activity = activity,
        title = "Secure poctra",
        subtitle = "Authenticate to encrypt this wallet with Android Keystore.",
        negativeText = "Use PIN",
        cipher = cipher,
        onSuccess = controller::savePendingWithBiometric,
        onError = { controller.showError("$it Use PIN if fingerprint keeps failing.") },
    )
}

private fun explorerUrl(host: String, page: String, key: String, value: String): String? {
    val cleanHost = host.trim().trimEnd('/')
    if (cleanHost.isBlank() || value.isBlank()) return null
    val encoded = URLEncoder.encode(value, Charsets.UTF_8.name())
    return "$cleanHost/$page?$key=$encoded"
}

private fun formatOctAmount(amount: Double): String {
    return "%,.6f".format(amount).trimEnd('0').trimEnd('.').ifBlank { "0" }
}

private fun ouToOct(ou: Long): String {
    return "%,.6f".format(ou / 1_000_000.0).trimEnd('0').trimEnd('.').ifBlank { "0" }
}

private fun formatTxTime(timestamp: Double): String {
    if (timestamp <= 0.0) return "-"
    return android.text.format.DateFormat.format("MMM d, yyyy HH:mm", (timestamp * 1000).toLong()).toString()
}

private fun startUnlockBiometric(
    activity: Activity,
    controller: PoctraController,
    onPromptClosed: (() -> Unit)? = null,
) {
    val cipher = runCatching { controller.createBiometricUnlockCipher() }.getOrElse {
        controller.showError(it.message ?: "Unable to prepare biometric unlock.")
        onPromptClosed?.invoke()
        return
    }
    DeviceSecurity.authenticateWithCipher(
        activity = activity,
        title = "Unlock poctra",
        subtitle = "Authenticate to decrypt the wallet key.",
        negativeText = "Cancel",
        cipher = cipher,
        onSuccess = { authenticatedCipher ->
            controller.unlockWithBiometric(authenticatedCipher) {
                onPromptClosed?.invoke()
            }
        },
        onError = { message ->
            onPromptClosed?.invoke()
            controller.showError(message)
        },
        onCancelled = { onPromptClosed?.invoke() },
    )
}

private fun startBackupRevealBiometric(
    activity: Activity,
    controller: PoctraController,
    onVerified: () -> Unit,
) {
    val cipher = runCatching { controller.createBiometricUnlockCipher() }.getOrElse {
        controller.showError(it.message ?: "Unable to prepare biometric confirmation.")
        return
    }
    DeviceSecurity.authenticateWithCipher(
        activity = activity,
        title = "Confirm Backup Access",
        subtitle = "Authenticate before viewing or copying recovery material.",
        negativeText = "Cancel",
        cipher = cipher,
        onSuccess = { authenticatedCipher ->
            controller.confirmSecurityWithBiometric(authenticatedCipher, onVerified)
        },
        onError = controller::showError,
    )
}

private fun startSecurityUpdateBiometric(activity: Activity, controller: PoctraController) {
    val cipher = runCatching { controller.createBiometricUnlockCipher() }.getOrElse {
        controller.showError(it.message ?: "Unable to prepare biometric re-authentication.")
        return
    }
    DeviceSecurity.authenticateWithCipher(
        activity = activity,
        title = "Confirm current security",
        subtitle = "Authenticate before changing wallet protection.",
        negativeText = "Cancel",
        cipher = cipher,
        onSuccess = controller::beginSecurityUpdateWithBiometric,
        onError = controller::showError,
    )
}
