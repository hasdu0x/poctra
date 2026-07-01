package com.poctra.wallet.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebChromeClient
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.poctra.wallet.AppRoute
import com.poctra.wallet.BridgeHistoryItem
import com.poctra.wallet.DashboardTab
import com.poctra.wallet.PendingDAppRequest
import com.poctra.wallet.PoctraController
import com.poctra.wallet.TradeStep
import com.poctra.wallet.data.AddressBookContact
import com.poctra.wallet.data.BridgeDirection
import com.poctra.wallet.data.BridgeEvmGasEstimate
import com.poctra.wallet.data.BridgeRepository
import com.poctra.wallet.data.BridgeStatus
import com.poctra.wallet.data.BridgeStep
import com.poctra.wallet.data.CircleAsset
import com.poctra.wallet.data.DAppSession
import com.poctra.wallet.data.EvmNetwork
import com.poctra.wallet.data.EvmGasEstimate
import com.poctra.wallet.data.EvmNft
import com.poctra.wallet.data.EvmToken
import com.poctra.wallet.data.EvmTransaction
import com.poctra.wallet.data.OctraIdentityProvider
import com.poctra.wallet.data.RpcStatus
import com.poctra.wallet.data.RpcNetwork
import com.poctra.wallet.data.SecurityMethod
import com.poctra.wallet.data.SubmittedTransaction
import com.poctra.wallet.data.TradeAsset
import com.poctra.wallet.data.TradeCandle
import com.poctra.wallet.data.TradeChartTimeframe
import com.poctra.wallet.data.TradeQuote
import com.poctra.wallet.data.TransactionFinality
import com.poctra.wallet.data.TransactionHistoryItem
import com.poctra.wallet.data.Wallet
import com.poctra.wallet.data.WalletType
import com.poctra.wallet.data.canonicalizeOrigin
import com.poctra.wallet.security.DeviceSecurity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.Charset
import java.util.UUID
import kotlin.math.abs

private const val FullWidthDialogFraction = 0.96f
private const val ScrimMotionMillis = 180
private const val SheetEnterMillis = 280
private const val SheetExitMillis = 180

@Composable
fun PoctraApp(activity: Activity, controller: PoctraController) {
    Surface(color = SurfaceBase, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize()) {
            when (controller.route) {
                AppRoute.Loading -> LoadingScreen()
                AppRoute.Welcome -> WelcomeScreen(controller)
                AppRoute.ImportWallet -> ImportWalletScreen(controller)
                AppRoute.SecuritySetup -> SecuritySetupScreen(activity, controller)
                AppRoute.Unlock -> UnlockScreen(activity, controller)
                AppRoute.Dashboard -> DashboardScreen(controller)
                AppRoute.Settings -> SettingsScreen(controller)
                AppRoute.RpcManagement -> RpcManagementScreen(controller)
                AppRoute.RpcAddNetwork -> AddRpcNetworkScreen(controller)
                AppRoute.SecuritySettings -> SecuritySettingsScreen(activity, controller)
                AppRoute.BackupSettings -> BackupSettingsScreen(activity, controller)
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
                    )
                }
            }

            controller.submittedTransaction?.takeIf { it.showDialog }?.let { transaction ->
                TransactionResultDialog(
                    transaction = transaction,
                    polling = controller.transactionStatusPolling,
                    onDismiss = controller::dismissSubmittedTransaction,
                )
            }

            if (controller.evmSendDialogVisible) {
                EvmSendReviewDialog(controller)
            }

            controller.pendingDAppRequest?.let { request ->
                DAppApprovalDialog(
                    request = request,
                    onApprove = controller::approvePendingDAppRequest,
                    onReject = controller::rejectPendingDAppRequest,
                )
            }
        }
    }
}

@Composable
private fun DAppApprovalDialog(
    request: PendingDAppRequest,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val largeRequest = request.details.size > 4 || request.details.sumOf { it.value.length } > 220
    var sheetVisible by remember(request) { mutableStateOf(false) }
    LaunchedEffect(request) { sheetVisible = true }
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
                        .heightIn(max = sheetMaxHeight)
                        .clickable(onClick = {})
                        .navigationBarsPadding(),
                    shape = RoundedCornerShape(topStart = Radius.md, topEnd = Radius.md),
                    colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
                    border = BorderStroke(1.dp, BorderLine),
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
                                GlyphIcon(Glyph.Browser, modifier = Modifier.size(20.dp), color = TextTertiary)
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
                                border = BorderStroke(1.dp, BorderLine),
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .verticalScroll(rememberScrollState())
                                        .padding(horizontal = Space.x5, vertical = Space.x3),
                                ) {
                                    request.details.forEachIndexed { index, item ->
                                        DAppRequestDetailRow(item.label, item.value)
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
                                .border(1.dp, BorderLine, RoundedCornerShape(Radius.sm))
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
private fun DAppRequestDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.x2),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.width(82.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.width(Space.x4))
        Text(
            value.ifBlank { "-" },
            color = TextTertiary,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TransactionResultDialog(
    transaction: SubmittedTransaction,
    polling: Boolean,
    onDismiss: () -> Unit,
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
                border = BorderStroke(1.dp, BorderLine),
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
                    transaction.hash?.let { hash ->
                        Spacer(Modifier.height(Space.x7))
                        Text("Transaction hash", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
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
                    border = BorderStroke(1.dp, BorderLine),
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
                            )
                            Spacer(Modifier.height(Space.x6))
                            Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
                                PoctraButton(
                                    text = "Cancel",
                                    onClick = controller::dismissEvmSendDialog,
                                    modifier = Modifier.weight(1f),
                                    variant = ButtonVariant.Ghost,
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
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(SurfaceRaised.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Send, modifier = Modifier.size(21.dp), color = TextTertiary)
        }
        Spacer(Modifier.width(Space.x5))
        Column(Modifier.weight(1f)) {
            Text("Review Send", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
            Text(evmSendAssetLabel(controller), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(Space.x6))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
        border = BorderStroke(1.dp, BorderLine),
    ) {
        Column(Modifier.padding(horizontal = Space.x6, vertical = Space.x3)) {
            DetailLine("From", controller.evmAccount?.address ?: "Current EVM account")
            DetailSeparator()
            DetailLine("To", controller.evmSendDialogRecipient)
            DetailSeparator()
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
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
        border = BorderStroke(1.dp, BorderLine),
    ) {
        Column(Modifier.padding(Space.x5)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Network fee", color = TextTertiary, fontWeight = FontWeight.SemiBold)
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
            } else {
                BridgeFeePresetRow(
                    selected = selectedGas,
                    labels = mapOf(
                        BridgeFeePreset.Low to weiToEthLabel(gasEstimate.lowFeeWei),
                        BridgeFeePreset.Medium to weiToEthLabel(gasEstimate.marketFeeWei),
                        BridgeFeePreset.Market to weiToEthLabel(gasEstimate.fastFeeWei),
                        BridgeFeePreset.Custom to "Custom",
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
                        supportingText = evmSendCustomGasFeeLabel(gasEstimate, customGasGwei, networkSymbol),
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
        border = BorderStroke(1.dp, BorderLine),
    ) {
        Column(Modifier.padding(horizontal = Space.x6, vertical = Space.x3)) {
            DetailLine("Recipient", controller.evmSendDialogRecipient)
            DetailSeparator()
            DetailLine("Amount", evmSendAmountLabel(controller))
            DetailSeparator()
            DetailLine("Network", controller.evmNetworkLabel())
            transaction?.hash?.let { hash ->
                DetailSeparator()
                DetailLine("Tx Hash", hash)
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
    transaction?.hash?.takeIf { completed || failed }?.let {
        Spacer(Modifier.height(Space.x5))
        PoctraButton(
            text = "View on Explorer",
            onClick = controller::openEvmSendExplorerTx,
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Ghost,
            compact = true,
            leadingGlyph = Glyph.External,
        )
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
            .border(1.dp, BorderLine, RoundedCornerShape(Radius.sm))
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
    val color = when (step.visual) {
        TxStepVisual.Done -> SurfaceRaised
        TxStepVisual.Active -> SurfaceRaised
        TxStepVisual.Error -> ErrorRed
        TxStepVisual.Pending -> TextSecondary
    }
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
                    )
                    .border(1.dp, color, RoundedCornerShape(Radius.pill)),
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
    var method by remember { mutableStateOf(ImportMethod.Mnemonic) }
    var value by remember { mutableStateOf("") }
    var secretVisible by remember { mutableStateOf(false) }
    var localError by remember { mutableStateOf<String?>(null) }

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
        GlyphIcon(Glyph.Import, modifier = Modifier.size(64.dp), color = SurfaceRaised)
        Spacer(Modifier.height(Space.x8))
        Text("Recover your wallet", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.x4))
        Text(
            "Paste a seed phrase, Octra Base64 key, or Ethereum hex key. poctra will validate the derived Octra address before security setup.",
            color = TextSecondary,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.x8))

        SecurityOptionCard(
            title = "Mnemonic phrase",
            subtitle = "Use a 12 or 24 word seed phrase.",
            glyph = Glyph.FileText,
            enabled = !controller.busy,
            onClick = {
                method = ImportMethod.Mnemonic
                value = ""
                secretVisible = false
                localError = null
            },
            modifier = Modifier.border(
                1.dp,
                if (method == ImportMethod.Mnemonic) SurfaceRaised else Color.Transparent,
                RoundedCornerShape(Radius.md),
            ),
        )
        Spacer(Modifier.height(Space.x6))
        SecurityOptionCard(
            title = "Private Key (Base64)",
            subtitle = "Use an Octra-compatible Base64 encoded 32-byte key.",
            glyph = Glyph.Key,
            enabled = !controller.busy,
            onClick = {
                method = ImportMethod.PrivateKey
                value = ""
                secretVisible = false
                localError = null
            },
            modifier = Modifier.border(
                1.dp,
                if (method == ImportMethod.PrivateKey) SurfaceRaised else Color.Transparent,
                RoundedCornerShape(Radius.md),
            ),
        )
        Spacer(Modifier.height(Space.x6))
        SecurityOptionCard(
            title = "Ethereum Private Key",
            subtitle = "Use a 0x hex key and derive an Octra wallet from it.",
            glyph = Glyph.Evm,
            enabled = !controller.busy,
            onClick = {
                method = ImportMethod.EthereumPrivateKey
                value = ""
                secretVisible = false
                localError = null
            },
            modifier = Modifier.border(
                1.dp,
                if (method == ImportMethod.EthereumPrivateKey) SurfaceRaised else Color.Transparent,
                RoundedCornerShape(Radius.md),
            ),
        )
        Spacer(Modifier.height(Space.x8))

        PoctraTextField(
            value = value,
            onValueChange = {
                value = it
                localError = null
            },
            label = when (method) {
                ImportMethod.Mnemonic -> "Seed phrase"
                ImportMethod.PrivateKey -> "Base64 private key"
                ImportMethod.EthereumPrivateKey -> "Ethereum private key"
            },
            placeholder = when (method) {
                ImportMethod.Mnemonic -> "twelve or twenty four words"
                ImportMethod.PrivateKey -> "Base64 encoded 32-byte key"
                ImportMethod.EthereumPrivateKey -> "0x..."
            },
            singleLine = method != ImportMethod.Mnemonic,
            minLines = if (method == ImportMethod.Mnemonic) 5 else 1,
            isError = localError != null,
            supportingText = localError ?: when (method) {
                ImportMethod.Mnemonic -> "Only seed phrases that derive a valid Octra address can be imported."
                ImportMethod.PrivateKey -> "The decoded 32-byte key must derive a valid Octra address."
                ImportMethod.EthereumPrivateKey -> "The secp256k1 key must also derive a valid Octra address."
            },
            password = !secretVisible,
            trailingContent = {
                SensitiveToggleButton(
                    visible = secretVisible,
                    onToggle = { secretVisible = !secretVisible },
                    contentDescription = if (secretVisible) "Hide import key" else "Show import key",
                )
            },
        )
        Spacer(Modifier.height(Space.x8))
        PoctraButton(
            text = "Continue",
            onClick = {
                val validation = validateImport(method, value)
                if (validation != null) {
                    localError = validation
                } else if (method == ImportMethod.Mnemonic) {
                    controller.importMnemonic(value)
                } else if (method == ImportMethod.PrivateKey) {
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
                    if (firstPin.length != 6) localError = "PIN must be exactly 6 digits." else confirming = true
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

@Composable
private fun UnlockScreen(activity: Activity, controller: PoctraController) {
    val method = controller.currentSecurityMethod
    val lifecycleOwner = activity as? LifecycleOwner
    var pin by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf<String?>(null) }
    var biometricPromptActive by remember { mutableStateOf(false) }

    fun autoStartBiometricUnlock() {
        if (method != SecurityMethod.Biometric || !controller.biometricAvailable || controller.busy || biometricPromptActive) return
        biometricPromptActive = true
        startUnlockBiometric(activity, controller) {
            biometricPromptActive = false
        }
    }

    LaunchedEffect(method, controller.biometricAvailable, controller.busy) {
        if (lifecycleOwner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == true) {
            autoStartBiometricUnlock()
        }
    }

    DisposableEffect(lifecycleOwner, method, controller.biometricAvailable) {
        if (lifecycleOwner == null) {
            onDispose { }
        } else {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START) {
                    autoStartBiometricUnlock()
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
                    onClick = { startUnlockBiometric(activity, controller) },
                    modifier = Modifier.fillMaxWidth(),
                    loading = controller.busy,
                    enabled = controller.biometricAvailable && !controller.busy,
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
                .padding(bottom = 106.dp),
        ) {
            when (controller.dashboardTab) {
                DashboardTab.Overview -> OverviewContent(
                    controller = controller,
                    address = wallet.address,
                    onOpenWallets = { walletDrawerOpen = true },
                )
                DashboardTab.Assets -> AssetsContent(controller)
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
            .border(1.dp, if (ready || refreshing) SurfaceRaised else BorderLine, RoundedCornerShape(Radius.md))
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
            onClick = onToggleWallets,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Space.x5))
        val status = controller.rpcStatus
        NetworkBadge(
            text = status?.network ?: controller.activeRpcNetwork?.networkType ?: "mainnet",
            state = when (status?.state) {
                RpcStatus.State.Connected -> "connected"
                RpcStatus.State.Failed -> "failed"
                else -> "checking"
            },
        )
        Spacer(Modifier.width(Space.x5))
        IconCircleButton(
            glyph = Glyph.Settings,
            contentDescription = "Settings",
            onClick = controller::openSettings,
            enabled = !controller.busy,
        )
    }
}

@Composable
private fun AddressDropdownBlock(
    walletName: String,
    address: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceStrong)
            .border(1.dp, BorderLine, RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.x6, vertical = Space.x5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = walletName,
                color = TextTertiary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.x2))
            Text(
                text = headerShortAddress(address),
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.x4))
        GlyphIcon(Glyph.ChevronDown, modifier = Modifier.size(18.dp), color = TextSecondary)
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
                .width(318.dp)
                .statusBarsPadding()
                .navigationBarsPadding(),
            shape = RoundedCornerShape(topEnd = Radius.md, bottomEnd = Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            border = BorderStroke(1.dp, BorderLine),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Space.x7),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlyphIcon(Glyph.Wallet, modifier = Modifier.size(24.dp), color = TextTertiary)
                    Spacer(Modifier.width(Space.x5))
                    Text("Wallets", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
                }
                Spacer(Modifier.height(Space.x7))
                val wallets = controller.vault?.wallets.orEmpty()
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(Space.x5),
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
                    dragging -> SurfaceRaised.copy(alpha = 0.20f)
                    active -> SurfaceRaised.copy(alpha = 0.16f)
                    else -> SurfaceBase
                }
            )
            .border(
                1.dp,
                when {
                    dragging -> SurfaceRaised
                    active -> SurfaceRaised
                    else -> BorderLine
                },
                cardShape,
            )
            .then(if (!editing) Modifier.clickable(onClick = onSelect) else Modifier)
            .then(dragModifier)
            .padding(Space.x6),
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
            Column(Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(Radius.sm))
                                    .background(if (active) SurfaceRaised else SurfaceMuted),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = position.toString(),
                                    color = if (active) PrimaryContent else TextTertiary,
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
                        }
                        Spacer(Modifier.height(Space.x3))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                            WalletMetaChip(
                                shortAddress(wallet.address),
                                background = SurfaceMuted,
                                color = TextSecondary,
                            )
                            identityName?.let {
                                WalletMetaChip(
                                    it,
                                    background = SurfaceRaised.copy(alpha = 0.14f),
                                    color = SurfaceRaised,
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(Space.x4))
                    Column(horizontalAlignment = Alignment.End) {
                        if (active) {
                            GlyphIcon(Glyph.Check, modifier = Modifier.size(16.dp), color = SurfaceRaised)
                            Spacer(Modifier.height(Space.x4))
                        }
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(RoundedCornerShape(Radius.sm))
                                .background(if (dragging) SurfaceRaised.copy(alpha = 0.18f) else Color.Transparent),
                            contentAlignment = Alignment.Center,
                        ) {
                            GlyphIcon(
                                Glyph.Drag,
                                modifier = Modifier.size(16.dp),
                                color = if (dragging) SurfaceRaised else TextSecondary,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(Space.x4))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    WalletMetaChip(
                        walletTypeLabel(wallet),
                        background = if (active) SurfaceRaised.copy(alpha = 0.14f) else SurfaceMuted,
                        color = if (active) SurfaceRaised else TextSecondary,
                    )
                    Spacer(Modifier.weight(1f))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                        WalletDrawerActionIcon(
                            glyph = Glyph.Pencil,
                            contentDescription = "Rename wallet",
                            onClick = {
                                name = displayName
                                editing = true
                            },
                        )
                        if (canRemove) {
                            WalletDrawerActionIcon(
                                glyph = Glyph.Trash,
                                contentDescription = "Remove wallet",
                                onClick = onRemove,
                                danger = true,
                            )
                        }
                    }
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
    danger: Boolean = false,
) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (danger) ErrorRed.copy(alpha = 0.14f) else SurfaceMuted)
            .border(1.dp, if (danger) ErrorRed.copy(alpha = 0.45f) else BorderLine, RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, modifier = Modifier.size(12.dp), color = if (danger) ErrorRed else TextSecondary)
    }
}

@Composable
private fun WalletMetaChip(text: String, background: Color, color: Color) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(background)
            .border(1.dp, BorderLine, RoundedCornerShape(Radius.sm))
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
    Spacer(Modifier.height(34.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x6)) {
        WalletActionButton("Send", Glyph.Send, Modifier.weight(1f), controller.busy, controller::openSend)
        WalletActionButton("Receive", Glyph.Receive, Modifier.weight(1f), controller.busy, controller::openReceive)
        WalletActionButton("Bridge", Glyph.Bridge, Modifier.weight(1f), controller.busy, controller::openBridge)
    }
    Spacer(Modifier.height(34.dp))
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
            Spacer(Modifier.height(Space.x4))
            Text(controller.tokenBalance.fiatEstimate, color = TextSecondary)
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
        Text("Recent Activity", color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
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
        EmptyPanel("No activity yet")
    } else {
        Column(Modifier.fillMaxWidth()) {
            controller.recentTransactions.forEachIndexed { index, tx ->
                RecentActivityRow(
                    tx = tx,
                    address = address,
                    identityName = controller.identityNameForAddress(if (tx.isOutgoing(address)) tx.to else tx.from),
                    onClick = { controller.openTransactionDetails(tx) },
                )
                if (index != controller.recentTransactions.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BorderLine),
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.x6))
        if (controller.historyCanLoadMore) {
            PoctraButton(
                text = "Load More",
                onClick = controller::loadMoreHistory,
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Ghost,
                leadingGlyph = Glyph.Refresh,
                loading = controller.historyLoading,
                enabled = !controller.historyLoading,
            )
        } else {
            val url = explorerUrl(
                host = controller.activeRpcNetwork?.explorerUrl.orEmpty(),
                page = "address.html",
                key = "addr",
                value = address,
            )
            PoctraButton(
                text = "View All on Explorer",
                onClick = {
                    if (url == null) controller.showError("Set an explorer URL in RPC Management first.")
                    else controller.openDAppBrowser("Transaction history", url)
                },
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Ghost,
                leadingGlyph = Glyph.Browser,
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
            GlyphIcon(
                glyph = if (outgoing) Glyph.Send else Glyph.Receive,
                modifier = Modifier.size(20.dp),
                color = TextTertiary,
            )
        }
        Spacer(Modifier.width(Space.x7))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        tx.status == "pending" -> "Pending"
                        outgoing -> "Sent"
                        else -> "Received"
                    },
                    color = TextTertiary,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.width(Space.x4))
                OpTypeBadge(tx.opType)
            }
            Text(
                text = activityCounterpartyLabel(
                    prefix = if (outgoing) "to" else "from",
                    address = counterparty,
                    identityName = identityName,
                ),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "${if (outgoing) "-" else "+"}${formatOctAmount(tx.amount)}",
            color = if (outgoing) TextTertiary else SurfaceRaised,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

private fun activityCounterpartyLabel(prefix: String, address: String, identityName: String?): String {
    val short = shortAddress(address)
    val name = identityName?.takeIf { it.isNotBlank() }
    return if (name == null) "$prefix $short" else "$prefix $name ($short)"
}

@Composable
private fun OpTypeBadge(text: String) {
    val label = text.ifBlank { "standard" }.lowercase()
    Text(
        text = label,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(
                when (label) {
                    "private", "stealth" -> SurfaceRaised.copy(alpha = 0.16f)
                    "encrypt", "decrypt" -> Warning.copy(alpha = 0.16f)
                    "claim" -> Color(0xFF3B567F).copy(alpha = 0.16f)
                    else -> SurfaceMuted
                },
            )
            .border(1.dp, BorderLine, RoundedCornerShape(Radius.sm))
            .padding(horizontal = Space.x3, vertical = 0.dp),
        color = TextTertiary,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
        maxLines = 1,
    )
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
            .background(if (enabled) SurfaceStrong else SurfaceMuted)
            .border(1.dp, BorderLine, RoundedCornerShape(Radius.md))
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = controller::openEvm),
        shape = RoundedCornerShape(Radius.sm),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        border = BorderStroke(1.dp, BorderLine),
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
                GlyphIcon(Glyph.Evm, modifier = Modifier.size(24.dp), color = TextTertiary)
            }
            Spacer(Modifier.width(Space.x6))
            Column(Modifier.weight(1f)) {
                Text("EVM Account", color = TextTertiary, fontWeight = FontWeight.SemiBold)
                Text(
                    text = account?.address?.let(::shortAddress) ?: "Derived from active wallet",
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

    LaunchedEffect(controller.activeEvmNetwork?.id) {
        controller.clearTradeQuote()
        controller.refreshTrade()
        controller.refreshTradeChart()
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
                toAsset = normalizeTradeToAsset(selected, toAsset)
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
            .background(SurfaceStrong)
            .border(1.dp, BorderLine, RoundedCornerShape(Radius.md))
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
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        border = BorderStroke(1.dp, BorderLine),
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
                    onClick = { controller.refreshTradeChart() },
                )
            }
            Spacer(Modifier.height(Space.x5))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(136.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(SurfaceBase)
                    .border(1.dp, BorderLine, RoundedCornerShape(Radius.sm))
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
                                .border(1.dp, Warning.copy(alpha = 0.45f), RoundedCornerShape(Radius.sm))
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
private fun SmallRefreshButton(loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(SurfaceMuted)
            .border(1.dp, BorderLine, RoundedCornerShape(Radius.sm))
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(14.dp), color = SurfaceRaised, strokeWidth = 2.dp)
        } else {
            GlyphIcon(Glyph.Refresh, modifier = Modifier.size(15.dp), color = TextTertiary)
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
    onSubmit: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        border = BorderStroke(1.dp, BorderLine),
    ) {
        Column(Modifier.padding(Space.x7)) {
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
            Spacer(Modifier.height(Space.x6))
            TradeAssetInputRow(
                label = "From",
                asset = fromAsset,
                amount = amount,
                balance = controller.tradeBalances.formatted(fromAsset),
                selectableAssets = listOf(TradeAsset.WOCT, TradeAsset.ETH, TradeAsset.USDT),
                onAssetChange = onFromAssetChange,
                onAmountChange = onAmountChange,
                onMax = onMax,
                enabled = !controller.tradeBusy,
                isError = localError != null,
            )
            Spacer(Modifier.height(Space.x5))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IconCircleButton(
                    glyph = Glyph.Trade,
                    contentDescription = "Swap pair",
                    onClick = onSwapPair,
                    enabled = !controller.tradeBusy,
                )
            }
            Spacer(Modifier.height(Space.x5))
            TradeAssetOutputRow(
                label = "To",
                asset = toAsset,
                amount = controller.tradeQuote?.amountOut ?: "",
                balance = controller.tradeBalances.formatted(toAsset),
                selectableAssets = if (fromAsset == TradeAsset.WOCT) listOf(TradeAsset.ETH, TradeAsset.USDT) else listOf(TradeAsset.WOCT),
                onAssetChange = onToAssetChange,
                enabled = !controller.tradeBusy,
            )
            if (localError != null) {
                Spacer(Modifier.height(Space.x5))
                StateBanner(localError, isError = true, compact = true)
            }
            Spacer(Modifier.height(Space.x6))
            TradeSlippageRow(slippageBps = slippageBps, onChange = onSlippageChange, enabled = !controller.tradeBusy)
            if (controller.tradeQuote == null || controller.tradeQuoteLoading || controller.tradeQuoteError != null) {
                Spacer(Modifier.height(Space.x6))
                TradeQuotePanel(controller, slippageBps)
            }
            Spacer(Modifier.height(Space.x7))
            PoctraButton(
                text = "Swap",
                onClick = onSubmit,
                modifier = Modifier.fillMaxWidth(),
                enabled = !controller.tradeBusy && !controller.tradeQuoteLoading,
                loading = controller.tradeBusy,
                leadingGlyph = Glyph.Trade,
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
                    border = BorderStroke(1.dp, BorderLine),
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
            GlyphIcon(Glyph.Trade, modifier = Modifier.size(21.dp), color = TextTertiary)
        }
        Spacer(Modifier.width(Space.x5))
        Column(Modifier.weight(1f)) {
            Text("Review Swap", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
            Text("${fromAsset.symbol} -> ${toAsset.symbol}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(Space.x6))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
        border = BorderStroke(1.dp, BorderLine),
    ) {
        Column(Modifier.padding(horizontal = Space.x6, vertical = Space.x3)) {
            DetailLine("Account", controller.evmAccount?.address ?: "Current wallet EVM account")
            DetailSeparator()
            DetailLine("Network", tradeReviewNetworkLabel(controller))
            DetailSeparator()
            DetailLine("Route", quote.routeLabel)
            DetailSeparator()
            DetailLine("Pay", "${amount.replace(",", ".").trim()} ${tradeAssetLabel(fromAsset)}")
            DetailSeparator()
            DetailLine("Receive estimate", "${quote.amountOut} ${tradeAssetLabel(toAsset)}")
            DetailSeparator()
            DetailLine("Minimum received", "${quote.minimumOut} ${toAsset.symbol}")
            DetailSeparator()
            DetailLine("Slippage", tradeReviewSlippageLabel(slippageBps))
            DetailSeparator()
            DetailLine("Token approval", tradeReviewApprovalLabel(fromAsset))
        }
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
        border = BorderStroke(1.dp, BorderLine),
    ) {
        Column(Modifier.padding(Space.x5)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Network fee", color = TextTertiary, fontWeight = FontWeight.SemiBold)
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
                    BridgeFeePreset.Custom to "Custom",
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
                    supportingText = tradeCustomGasFeeLabel(quote, customGasGwei),
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
        border = BorderStroke(1.dp, BorderLine),
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
        border = BorderStroke(1.dp, BorderLine),
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
                )
                .border(
                    1.dp,
                    when {
                        failed -> ErrorRed
                        done || active -> SurfaceRaised
                        else -> BorderLine
                    },
                    RoundedCornerShape(Radius.md),
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
    if (fromAsset != TradeAsset.ETH) {
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

private fun tradeReviewApprovalLabel(fromAsset: TradeAsset): String = when (fromAsset) {
    TradeAsset.ETH -> "No ERC20 approval required"
    else -> "ERC20 approval and Permit2 may be submitted if allowance is low"
}

@Composable
private fun TradeAssetInputRow(
    label: String,
    asset: TradeAsset,
    amount: String,
    balance: String,
    selectableAssets: List<TradeAsset>,
    onAssetChange: (TradeAsset) -> Unit,
    onAmountChange: (String) -> Unit,
    onMax: () -> Unit,
    enabled: Boolean,
    isError: Boolean,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text(
                "Balance $balance ${asset.symbol}",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(Space.x4))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x5), verticalAlignment = Alignment.CenterVertically) {
            TradeAssetSelector(
                asset = asset,
                selectableAssets = selectableAssets,
                onSelect = onAssetChange,
                enabled = enabled,
                modifier = Modifier.weight(1.12f),
            )
            TradeAmountField(
                value = amount,
                onValueChange = { raw -> onAmountChange(raw.filter { it.isDigit() || it == '.' || it == ',' }) },
                modifier = Modifier.weight(1.18f),
                isError = isError,
                enabled = enabled,
                onMax = onMax,
            )
        }
    }
}

@Composable
private fun TradeAmountField(
    value: String,
    onValueChange: (String) -> Unit,
    isError: Boolean,
    enabled: Boolean,
    onMax: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
        border = BorderStroke(1.dp, if (isError) ErrorRed else BorderLine),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Space.x5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = TextTertiary, fontWeight = FontWeight.SemiBold),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(SurfaceRaised),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isBlank()) {
                            Text("0.0", color = TextSecondary, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                        }
                        innerTextField()
                    }
                },
            )
            Text(
                "Max",
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable(enabled = enabled, onClick = onMax)
                    .padding(horizontal = Space.x3, vertical = Space.x2),
                color = if (enabled) SurfaceRaised else TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
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
    enabled: Boolean,
) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
            Text("Balance $balance ${asset.symbol}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(Space.x4))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x5), verticalAlignment = Alignment.CenterVertically) {
            TradeAssetSelector(
                asset = asset,
                selectableAssets = selectableAssets,
                onSelect = onAssetChange,
                enabled = enabled,
                modifier = Modifier.weight(1.12f),
            )
            Card(
                modifier = Modifier
                    .weight(1.18f)
                    .height(58.dp),
                shape = RoundedCornerShape(Radius.md),
                colors = CardDefaults.cardColors(containerColor = SurfaceMuted.copy(alpha = 0.6f)),
                border = BorderStroke(1.dp, BorderLine),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = Space.x6),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = amount.ifBlank { "0.0" },
                        color = if (amount.isBlank()) TextSecondary else TextTertiary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun TradeAssetSelector(
    asset: TradeAsset,
    selectableAssets: List<TradeAsset>,
    onSelect: (TradeAsset) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(SurfaceBase)
                .border(1.dp, BorderLine, RoundedCornerShape(Radius.md))
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = Space.x5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TradeAssetIcon(asset)
            Spacer(Modifier.width(Space.x4))
            Text(
                tradeAssetLabel(asset),
                color = TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            GlyphIcon(Glyph.ChevronDown, modifier = Modifier.size(14.dp), color = TextSecondary)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            selectableAssets.forEach { option ->
                DropdownMenuItem(
                    text = { Text(tradeAssetLabel(option), color = TextTertiary) },
                    leadingIcon = { TradeAssetIcon(option) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun TradeAssetIcon(asset: TradeAsset) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (asset == TradeAsset.WOCT) SurfaceRaised.copy(alpha = 0.18f) else SurfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        if (asset == TradeAsset.WOCT) {
            Image(
                painter = painterResource(com.poctra.wallet.R.drawable.octra_logo),
                contentDescription = "wOCT",
                modifier = Modifier.size(17.dp),
            )
        } else {
            Image(
                painter = painterResource(tradeAssetIconRes(asset)),
                contentDescription = asset.symbol,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun tradeAssetLabel(asset: TradeAsset): String = "${asset.symbol} (${asset.displayName})"

private fun tradeAssetIconRes(asset: TradeAsset): Int = when (asset) {
    TradeAsset.WOCT -> com.poctra.wallet.R.drawable.octra_logo
    TradeAsset.ETH -> com.poctra.wallet.R.drawable.ic_eth
    TradeAsset.USDT -> com.poctra.wallet.R.drawable.ic_usdt
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
private fun TradeQuotePanel(controller: PoctraController, slippageBps: Int) {
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
            TradeInfoHint(controller.tradeQuoteError.orEmpty(), isError = true)
        }
        controller.tradeQuote != null -> {
            val quote = controller.tradeQuote ?: return
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.md),
                colors = CardDefaults.cardColors(containerColor = SurfaceBase),
                border = BorderStroke(1.dp, BorderLine),
            ) {
                Column(Modifier.padding(horizontal = Space.x6, vertical = Space.x4)) {
                    DetailLine("Route", quote.routeLabel)
                    DetailSeparator()
                    DetailLine("Minimum Received", quote.minimumOut)
                    DetailSeparator()
                    DetailLine("Slippage", "${slippageBps.toBigDecimal().divide(BigDecimal(100)).stripTrailingZeros().toPlainString()}%")
                    DetailSeparator()
                    DetailLine("Estimated Gas", controller.tradeGasInfo())
                }
            }
        }
        else -> {
            TradeInfoHint("Enter amount to quote Uniswap v4 route.", isError = false)
        }
    }
}

@Composable
private fun TradeInfoHint(text: String, isError: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (isError) ErrorRed.copy(alpha = 0.14f) else SurfaceRaised.copy(alpha = 0.14f))
            .border(1.dp, if (isError) ErrorRed.copy(alpha = 0.45f) else SurfaceRaised.copy(alpha = 0.35f), RoundedCornerShape(Radius.sm))
            .padding(horizontal = Space.x5, vertical = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(
            glyph = Glyph.Info,
            modifier = Modifier.size(14.dp),
            color = if (isError) ErrorRed else SurfaceRaised,
        )
        Spacer(Modifier.width(Space.x4))
        Text(
            text,
            color = if (isError) ErrorRed else TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun normalizeTradeToAsset(fromAsset: TradeAsset, currentTo: TradeAsset): TradeAsset {
    if (fromAsset == TradeAsset.WOCT && currentTo != TradeAsset.WOCT) return currentTo
    return if (fromAsset == TradeAsset.WOCT) TradeAsset.ETH else TradeAsset.WOCT
}

private fun tradeReviewKey(fromAsset: TradeAsset, toAsset: TradeAsset, amount: String, slippageBps: Int): String =
    "${fromAsset.symbol}:${toAsset.symbol}:${amount.replace(",", ".").trim()}:$slippageBps"

private data class DAppSite(val title: String, val url: String, val subtitle: String, val iconRes: Int)

@Composable
private fun ExploreDAppsContent(controller: PoctraController) {
    val sites = listOf(
        DAppSite(
            title = "Octra Identity",
            url = "https://devnet.octra.id",
            subtitle = "Devnet identity dApp",
            iconRes = com.poctra.wallet.R.drawable.ic_octra_identity,
        ),
    )
    Column(Modifier.fillMaxWidth()) {
        Text("Explore", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.x3))
        Text("Open dApps in the wallet browser", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(Space.x7))
        CustomDAppLauncher(controller)
        Spacer(Modifier.height(Space.x8))
        Column(verticalArrangement = Arrangement.spacedBy(Space.x6)) {
            sites.forEach { site ->
                DAppSiteRow(site = site, onClick = { controller.openDAppBrowser(site.title, site.url) })
            }
        }
    }
}

@Composable
private fun DAppSiteRow(site: DAppSite, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceStrong)
            .border(1.dp, BorderLine, RoundedCornerShape(Radius.md))
            .clickable(onClick = onClick)
            .padding(Space.x7),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(SurfaceRaised.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(site.iconRes),
                contentDescription = site.title,
                modifier = Modifier.size(24.dp),
            )
        }
        Spacer(Modifier.width(Space.x6))
        Column(Modifier.weight(1f)) {
            Text(site.title, color = TextTertiary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Space.x2))
            Text(site.subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(site.url, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun CustomDAppLauncher(controller: PoctraController) {
    var url by remember { mutableStateOf("") }
    val showOctProtocol = controller.isOctraCircleInput(url)
    val openUrl = {
        if (url.isBlank()) controller.showError("Enter a dApp URL.") else controller.openDAppBrowser("dApp", url)
    }
    PoctraTextField(
        value = url,
        onValueChange = { url = it.trim() },
        label = "dApp URL",
        placeholder = "circle address, octra identity or dApp URL",
        modifier = Modifier.height(54.dp),
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
}

@Composable
private fun OctProtocolBadge() {
    Box(
        modifier = Modifier
            .padding(start = Space.x2)
            .clip(RoundedCornerShape(Radius.sm))
            .background(SurfaceRaised.copy(alpha = 0.16f))
            .border(1.dp, SurfaceRaised.copy(alpha = 0.34f), RoundedCornerShape(Radius.sm))
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
            .background(SurfaceBase)
            .border(1.dp, BorderLine, RoundedCornerShape(Radius.sm))
            .semantics { this.contentDescription = contentDescription }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(glyph, modifier = Modifier.size(17.dp), color = TextTertiary)
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled")
private fun DAppBrowserScreen(controller: PoctraController) {
    val url = controller.selectedDAppUrl.ifBlank { "https://octra.org" }
    val displayUrl = controller.circleDisplayUrl()
    val bridgeToken = remember(url) { UUID.randomUUID().toString() }
    var browserView by remember(url) { mutableStateOf<WebView?>(null) }
    var pageLoading by remember(url) { mutableStateOf(true) }
    var pageError by remember(url) { mutableStateOf<String?>(null) }
    val currentBrowserView by rememberUpdatedState(browserView)

    DisposableEffect(url) {
        onDispose {
            currentBrowserView?.run {
                runCatching { removeJavascriptInterface("PoctraAndroid") }
                runCatching { stopLoading() }
                runCatching { destroy() }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(SurfaceStrong)
                .border(1.dp, BorderLine)
                .padding(horizontal = Space.x5, vertical = Space.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BrowserToolbarButton(Glyph.Back, "Back", controller::closeDAppBrowser)
            Spacer(Modifier.width(Space.x4))
            Column(Modifier.weight(1f)) {
                Text(controller.selectedDAppTitle.ifBlank { "dApp" }, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(displayUrl, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Space.x4))
            BrowserToolbarButton(
                Glyph.Refresh,
                "Refresh",
                onClick = {
                    pageLoading = true
                    pageError = null
                    browserView?.reload()
                },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(SurfaceBase),
        ) {
            key(url) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { context ->
                        WebView(context).apply {
                            browserView = this
                            setBackgroundColor(android.graphics.Color.TRANSPARENT)
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun shouldOverrideUrlLoading(
                                    view: WebView,
                                    request: android.webkit.WebResourceRequest,
                                ): Boolean {
                                    val nextUrl = request.url?.toString().orEmpty()
                                    if (controller.isOctraCircleInput(nextUrl)) {
                                        controller.openDAppBrowser("oct://", nextUrl)
                                        return true
                                    }
                                    return false
                                }

                                override fun shouldInterceptRequest(
                                    view: WebView,
                                    request: android.webkit.WebResourceRequest,
                                ): WebResourceResponse? {
                                    return dAppWebResourceResponse(controller, request, bridgeToken)
                                }

                                override fun onPageStarted(view: WebView, pageUrl: String?, favicon: android.graphics.Bitmap?) {
                                    super.onPageStarted(view, pageUrl, favicon)
                                    schedulePoctraProviderInjection(view, bridgeToken)
                                    pageLoading = true
                                    pageError = null
                                }

                                override fun onPageCommitVisible(view: WebView, pageUrl: String?) {
                                    super.onPageCommitVisible(view, pageUrl)
                                    schedulePoctraProviderInjection(view, bridgeToken)
                                }

                                override fun onPageFinished(view: WebView, pageUrl: String?) {
                                    super.onPageFinished(view, pageUrl)
                                    schedulePoctraProviderInjection(view, bridgeToken)
                                    pageLoading = false
                                }

                                override fun onReceivedError(
                                    view: WebView,
                                    request: android.webkit.WebResourceRequest,
                                    error: android.webkit.WebResourceError,
                                ) {
                                    super.onReceivedError(view, request, error)
                                    if (request.isForMainFrame) {
                                        pageLoading = false
                                        pageError = error.description?.toString() ?: "Unable to load dApp."
                                    }
                                }
                            }
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.javaScriptCanOpenWindowsAutomatically = false
                            settings.setSupportMultipleWindows(false)
                            settings.allowFileAccess = false
                            settings.allowContentAccess = false
                            settings.mediaPlaybackRequiresUserGesture = true
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                settings.safeBrowsingEnabled = true
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            }
                            addJavascriptInterface(
                                PoctraProviderBridge(
                                    controller = controller,
                                    currentOrigin = { canonicalizeOrigin(this.url ?: url) },
                                    expectedToken = { bridgeToken },
                                    evaluate = { script -> post { evaluateJavascript(script, null) } },
                                ),
                                "PoctraAndroid",
                            )
                            loadUrl(url)
                        }
                    },
                    update = { webView ->
                        browserView = webView
                    },
                )
            }
            if (pageLoading) {
                DAppBrowserLoadingOverlay(title = controller.selectedDAppTitle.ifBlank { "dApp" }, url = displayUrl)
            }
            pageError?.let { message ->
                DAppBrowserErrorOverlay(
                    message = message,
                    onRetry = {
                        pageLoading = true
                        pageError = null
                        browserView?.reload()
                    },
                )
            }
        }
    }
}

private fun circleWebResourceResponse(
    controller: PoctraController,
    request: android.webkit.WebResourceRequest,
): WebResourceResponse? {
    val requestUri = request.url ?: return null
    val host = requestUri.host.orEmpty()
    if (!host.equals(PoctraController.CircleBrowserHost, ignoreCase = true)) {
        return if (controller.selectedCircleTarget != null && !request.isForMainFrame) {
            emptyCircleResponse()
        } else {
            null
        }
    }

    val segments = requestUri.pathSegments.orEmpty()
    if (segments.firstOrNull() == "loading") {
        return circleHtmlResponse(
            title = "Resolving oct://",
            message = "Resolving Octra Identity and circle resource...",
        )
    }
    if (segments.firstOrNull() == "error") {
        return circleHtmlResponse(
            title = "Cannot open oct://",
            message = controller.selectedCircleError ?: "Unable to open this oct:// resource.",
        )
    }

    val explicitCircleId = segments.takeIf { it.firstOrNull() == "oct" && it.size >= 2 }?.getOrNull(1)
    val activeTarget = controller.selectedCircleTarget
    val circleId = explicitCircleId ?: activeTarget?.circleId ?: return circleHtmlResponse(
        title = "Resolving oct://",
        message = "Waiting for circle target...",
    )
    val path = if (explicitCircleId != null) {
        val assetSegments = segments.drop(2)
        if (assetSegments.isEmpty()) "/index.html" else "/" + assetSegments.joinToString("/")
    } else {
        requestUri.path?.takeIf { it.isNotBlank() && it != "/" } ?: "/index.html"
    }

    return runCatching {
        circleAssetResponse(controller.fetchCircleAssetForBrowser(circleId, path))
    }.getOrElse { exception ->
        circleHtmlResponse(
            title = "Circle resource unavailable",
            message = exception.message ?: "Unable to load oct:// resource.",
            statusCode = if (request.isForMainFrame) 200 else 404,
            reasonPhrase = if (request.isForMainFrame) "OK" else "Not Found",
        )
    }
}

private fun dAppWebResourceResponse(
    controller: PoctraController,
    request: android.webkit.WebResourceRequest,
    bridgeToken: String,
): WebResourceResponse? {
    circleWebResourceResponse(controller, request)?.let { return it }
    if (controller.selectedCircleTarget != null) return null
    if (!request.isForMainFrame) return null
    if (!request.method.equals("GET", ignoreCase = true)) return null

    val requestUri = request.url ?: return null
    val scheme = requestUri.scheme.orEmpty()
    if (!scheme.equals("http", ignoreCase = true) && !scheme.equals("https", ignoreCase = true)) return null
    if (requestUri.host.equals(PoctraController.CircleBrowserHost, ignoreCase = true)) return null

    return runCatching {
        injectedDAppDocumentResponse(
            url = requestUri.toString(),
            requestHeaders = request.requestHeaders.orEmpty(),
            bridgeToken = bridgeToken,
        )
    }.getOrNull()
}

private fun injectedDAppDocumentResponse(
    url: String,
    requestHeaders: Map<String, String>,
    bridgeToken: String,
): WebResourceResponse? {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        instanceFollowRedirects = true
        connectTimeout = 10_000
        readTimeout = 15_000
        requestMethod = "GET"
        setRequestProperty("Accept-Encoding", "identity")
        if (requestHeaders.none { it.key.equals("Accept", ignoreCase = true) }) {
            setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        }
        if (requestHeaders.none { it.key.equals("User-Agent", ignoreCase = true) }) {
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36 Poctra/0.1",
            )
        }
        requestHeaders.forEach { (key, value) ->
            if (
                key.isNotBlank() &&
                value.isNotBlank() &&
                !key.equals("Host", ignoreCase = true) &&
                !key.equals("Accept-Encoding", ignoreCase = true) &&
                !key.equals("Content-Length", ignoreCase = true)
            ) {
                setRequestProperty(key, value)
            }
        }
        CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() }?.let {
            setRequestProperty("Cookie", it)
        }
    }

    val statusCode = connection.responseCode.takeIf { it >= 100 } ?: 200
    val reasonPhrase = connection.responseMessage?.takeIf { it.isNotBlank() } ?: "OK"
    val contentType = connection.contentType.orEmpty()
    val mimeType = contentType.substringBefore(';').trim().ifBlank { "text/html" }
    val charsetName = responseCharset(contentType)
    val source = if (statusCode >= 400) connection.errorStream else connection.inputStream
    val body = source?.use { it.readBytes() } ?: ByteArray(0)
    val headers = filteredWebHeaders(connection.headerFields)

    return if (mimeType.equals("text/html", ignoreCase = true) || mimeType.equals("application/xhtml+xml", ignoreCase = true)) {
        val charset = runCatching { Charset.forName(charsetName) }.getOrDefault(Charsets.UTF_8)
        val html = body.toString(charset)
        val injected = injectDAppProviderRuntime(html, bridgeToken).toByteArray(charset)
        WebResourceResponse(
            mimeType,
            charsetName,
            statusCode,
            reasonPhrase,
            headers + mapOf("Cache-Control" to "no-store"),
            ByteArrayInputStream(injected),
        )
    } else {
        WebResourceResponse(
            mimeType,
            charsetName,
            statusCode,
            reasonPhrase,
            headers,
            ByteArrayInputStream(body),
        )
    }
}

private fun filteredWebHeaders(raw: Map<String?, List<String>>): Map<String, String> {
    val blocked = setOf(
        "content-length",
        "content-encoding",
        "content-security-policy",
        "content-security-policy-report-only",
    )
    return raw.mapNotNull { (key, values) ->
        val cleanKey = key?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        if (cleanKey.lowercase() in blocked) return@mapNotNull null
        val value = values.firstOrNull { it.isNotBlank() } ?: return@mapNotNull null
        cleanKey to value
    }.toMap()
}

private fun responseCharset(contentType: String): String =
    contentType
        .split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
        ?.substringAfter('=')
        ?.trim()
        ?.ifBlank { null }
        ?: "UTF-8"

private fun injectDAppProviderRuntime(html: String, bridgeToken: String): String {
    val script = "<script>${poctraProviderScript(bridgeToken)}</script>"
    val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
    return if (head != null) {
        html.substring(0, head.range.last + 1) + script + html.substring(head.range.last + 1)
    } else {
        script + html
    }
}

private fun circleAssetResponse(asset: CircleAsset): WebResourceResponse {
    val mimeType = asset.contentType.substringBefore(';').trim().ifBlank { "application/octet-stream" }
    val charset = asset.contentType
        .split(';')
        .map { it.trim() }
        .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
        ?.substringAfter('=')
        ?.trim()
        ?: if (mimeType.startsWith("text/") || mimeType.contains("json") || mimeType.contains("javascript")) "UTF-8" else null
    val body = if (mimeType.equals("text/html", ignoreCase = true)) {
        injectCircleRuntime(asset).toByteArray(Charsets.UTF_8)
    } else {
        asset.body
    }
    return WebResourceResponse(
        mimeType,
        charset,
        200,
        "OK",
        mapOf(
            "Cache-Control" to "no-store",
            "X-Content-Type-Options" to "nosniff",
            "Access-Control-Allow-Origin" to "*",
        ),
        ByteArrayInputStream(body),
    )
}

private fun injectCircleRuntime(asset: CircleAsset): String {
    val html = asset.body.toString(Charsets.UTF_8)
    val context = JSONObject()
        .put("circle_id", asset.circleId)
        .put("path", asset.canonicalPath)
        .put("uri", "oct://${asset.circleId}${asset.canonicalPath}")
        .toString()
    val prelude = """
        <script>
        (function () {
          var context = $context;
          Object.defineProperty(window, 'OctraCircle', {
            configurable: true,
            value: Object.freeze({
              context: Object.freeze(context),
              navigate: function (uri) { window.location.href = String(uri || ''); },
              request: function () { return Promise.reject(new Error('OctraCircle native bridge is not available yet.')); }
            })
          });
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
private fun DAppBrowserLoadingOverlay(title: String, url: String) {
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
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(SurfaceStrong)
                    .border(1.dp, BorderLine, RoundedCornerShape(Radius.md)),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = SurfaceRaised, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            Spacer(Modifier.height(Space.x7))
            Text("Opening $title", color = TextTertiary, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(Space.x3))
            Text(url, color = TextSecondary, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

private class PoctraProviderBridge(
    private val controller: PoctraController,
    private val currentOrigin: () -> String,
    private val expectedToken: () -> String,
    private val evaluate: (String) -> Unit,
) {
    @JavascriptInterface
    fun request(payload: String) {
        val json = runCatching { JSONObject(payload) }.getOrNull()
        val id = json?.optString("id").orEmpty()
        val token = json?.optString("bridgeToken").orEmpty()
        val method = json?.optString("method").orEmpty()
        if (token.isBlank() || token != expectedToken()) {
            if (method == "octra_requestAccounts") {
                controller.showError("Unable to connect dApp. Refresh the page and try again.")
            }
            reject(id, -32600, "Invalid poctra bridge token.")
            return
        }
        val origin = json?.optString("origin").orEmpty().ifBlank {
            runCatching { currentOrigin() }.getOrDefault("")
        }
        controller.handleDAppBridgeRequest(
            originRaw = origin,
            payload = payload,
            onSuccess = { resultJson ->
                evaluate(
                    "window.__poctraResolve && window.__poctraResolve(" +
                        "${JSONObject.quote(id)},$resultJson);",
                )
            },
            onError = { code, message ->
                evaluate(
                    "window.__poctraReject && window.__poctraReject(" +
                        "${JSONObject.quote(id)},$code,${JSONObject.quote(message)});",
                )
            },
        )
    }

    private fun reject(id: String, code: Int, message: String) {
        if (id.isBlank()) return
        evaluate(
            "window.__poctraReject && window.__poctraReject(" +
                "${JSONObject.quote(id)},$code,${JSONObject.quote(message)});",
        )
    }
}

private fun injectPoctraProvider(webView: WebView, bridgeToken: String) {
    runCatching {
        webView.evaluateJavascript(poctraProviderScript(bridgeToken), null)
    }
}

private fun schedulePoctraProviderInjection(webView: WebView, bridgeToken: String) {
    injectPoctraProvider(webView, bridgeToken)
    listOf(80L, 250L, 750L, 1_500L, 3_000L).forEach { delayMs ->
        webView.postDelayed({
            injectPoctraProvider(webView, bridgeToken)
        }, delayMs)
    }
}

private fun poctraProviderScript(bridgeToken: String): String = """
    (function () {
      var bridgeToken = ${JSONObject.quote(bridgeToken)};
      window.__poctraBridgeToken = bridgeToken;
      function dispatchEventSafely(event) {
        try { window.dispatchEvent(event); } catch (_) {}
      }
      function announceProvider(provider, info) {
        dispatchEventSafely(new CustomEvent('octra:announceProvider', {
          detail: { provider: provider, info: info }
        }));
        dispatchEventSafely(new CustomEvent('octraLoaded', {
          detail: { provider: provider, info: info }
        }));
        dispatchEventSafely(new Event('octra#initialized'));
        dispatchEventSafely(new Event('poctra#initialized'));
      }
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
        var existingInfo = window.__poctraProviderInfo || {
          uuid: 'poctra-mobile',
          name: 'poctra',
          rdns: 'id.octra.poctra',
          version: '0.1.0'
        };
        installProviderGlobals(window.octra, existingInfo);
        announceProvider(window.octra, existingInfo);
        return;
      }

      var nextId = 1;
      var pending = {};
      var listeners = {};
      var info = {
        uuid: 'poctra-mobile',
        name: 'poctra',
        rdns: 'id.octra.poctra',
        version: '0.1.0'
      };

      window.__poctraProviderInfo = info;
      window.__poctraResolve = function (id, result) {
        var entry = pending[id];
        if (!entry) return;
        delete pending[id];
        if ((entry.method === 'octra_requestAccounts' || entry.method === 'octra_accounts') && Array.isArray(result)) {
          emit('accountsChanged', result);
          if (result.length > 0) emit('connect', { accounts: result });
        }
        entry.resolve(result);
      };
      window.__poctraReject = function (id, code, message) {
        var entry = pending[id];
        if (!entry) return;
        delete pending[id];
        var error = new Error(message || 'poctra request failed');
        error.code = code;
        entry.reject(error);
      };

      var provider = {
        isOctra: true,
        isPoctra: true,
        providerId: 'poctra',
        name: 'poctra',
        rdns: 'id.octra.poctra',
        version: '0.1.0',
        request: function (args) {
          return new Promise(function (resolve, reject) {
            if (!args || !args.method) {
              var invalid = new Error('Octra provider method is required');
              invalid.code = -32600;
              reject(invalid);
              return;
            }
            var id = String(nextId++);
            pending[id] = { resolve: resolve, reject: reject, method: args.method };
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
        enable: function () {
          return provider.connect();
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
        announceProvider(provider, info);
      }
      window.addEventListener('octra:requestProvider', announce);
      announce();
      setTimeout(announce, 0);
      setTimeout(announce, 250);
      setTimeout(announce, 1000);
      setTimeout(announce, 2500);
      setTimeout(announce, 5000);
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
            .navigationBarsPadding()
            .padding(horizontal = Space.x8, vertical = Space.x6)
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceStrong)
            .border(1.dp, BorderLine, RoundedCornerShape(Radius.md))
            .padding(Space.x3),
        horizontalArrangement = Arrangement.spacedBy(Space.x3),
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
    }
}

@Composable
private fun BottomNavItem(
    label: String,
    glyph: Glyph,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val itemScale by animateFloatAsState(
        targetValue = if (selected) 1.02f else 1f,
        animationSpec = tween(ScrimMotionMillis, easing = FastOutSlowInEasing),
        label = "bottomNavItemScale",
    )
    Column(
        modifier = modifier
            .height(52.dp)
            .graphicsLayer {
                scaleX = itemScale
                scaleY = itemScale
            }
            .clip(RoundedCornerShape(Radius.md))
            .background(if (selected) SurfaceRaised else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.x2, vertical = Space.x4),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphIcon(glyph, modifier = Modifier.size(18.dp), color = if (selected) PrimaryContent else TextSecondary)
        Spacer(Modifier.height(Space.x2))
        Text(
            label,
            color = if (selected) PrimaryContent else TextSecondary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
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
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLine),
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
            .background(if (active) SurfaceRaised.copy(alpha = 0.18f) else SurfaceBase)
            .border(
                1.dp,
                if (active) SurfaceRaised.copy(alpha = 0.9f) else BorderLine,
                RoundedCornerShape(Radius.sm),
            )
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
    Card(
        modifier = modifier
            .height(84.dp)
            .clickable(enabled = !disabled, onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLine),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            GlyphIcon(glyph, modifier = Modifier.size(24.dp), color = if (disabled) TextSecondary else TextTertiary)
            Spacer(Modifier.height(Space.x5))
            Text(label, color = if (disabled) TextSecondary else TextTertiary, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyPanel(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .border(1.dp, BorderLine, RoundedCornerShape(Radius.md))
            .padding(Space.x8),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = TextSecondary)
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
    var localError by remember { mutableStateOf<String?>(null) }
    val recipientLookup = rememberOctraRecipientLookup(controller, recipient)

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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeSend, enabled = !controller.busy)
            Spacer(Modifier.width(Space.x6))
            Text("Send OCT", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(Space.x7))
        CompactScreenIntro(
            glyph = Glyph.Send,
            description = "Send OCT to foo.oct or an Octra address.",
        )
        Spacer(Modifier.height(Space.x8))
        RecipientLookupField(
            value = recipient,
            onValueChange = {
                recipient = it.trim()
                selectedRecipientName = null
                localError = null
            },
            label = "Recipient address",
            placeholder = "foo.oct, app.foo.oct or oct address",
            lookupState = recipientLookup,
            glyph = Glyph.AddressBook,
            onClear = {
                recipient = ""
                selectedRecipientName = null
                localError = null
            },
            directPreviewName = selectedRecipientName,
            showDirectOctraAddressPreview = true,
            isError = localError != null,
            supportingText = localError,
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
                localError = null
            },
            label = "Amount OCT",
            singleLine = true,
            keyboardType = KeyboardType.Decimal,
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
                        localError = null
                    }
                    .padding(horizontal = Space.x4, vertical = Space.x2),
                color = if (!controller.tokenBalance.isLoading && controller.availableBalance > 0.0) SurfaceRaised else TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(Space.x6))
        PoctraTextField(
            value = feeOu,
            onValueChange = {
                feeOu = it.filter(Char::isDigit).take(12)
                feeEdited = true
                localError = null
            },
            label = "Fee / Gas (OU)",
            singleLine = true,
            keyboardType = KeyboardType.Number,
            supportingText = if (controller.feeLoading) {
                "Fetching recommended fee..."
            } else {
                "Recommended ${controller.recommendedFeeOu} OU ~= ${ouToOct(controller.recommendedFeeOu)} OCT"
            },
        )
        Spacer(Modifier.height(Space.x5))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
            val currentFee = feeOu.toLongOrNull()
            FeeOptionButton(
                label = "Auto",
                subtitle = "RPC",
                glyph = Glyph.Check,
                selected = currentFee == controller.recommendedFeeOu && !feeEdited,
                onClick = {
                    feeEdited = false
                    feeOu = controller.recommendedFeeOu.toString()
                },
                modifier = Modifier.weight(1f),
                enabled = !controller.feeLoading,
            )
            FeeOptionButton(
                label = "Fast",
                subtitle = "2x",
                glyph = Glyph.Send,
                selected = currentFee == controller.recommendedFeeOu * 2,
                onClick = {
                    feeEdited = true
                    feeOu = (controller.recommendedFeeOu * 2).toString()
                },
                modifier = Modifier.weight(1f),
                enabled = !controller.feeLoading,
            )
            FeeOptionButton(
                label = "Refresh",
                subtitle = "fee",
                glyph = Glyph.Refresh,
                selected = controller.feeLoading,
                onClick = {
                    feeEdited = false
                    controller.refreshRecommendedFee()
                },
                modifier = Modifier.weight(1f),
                enabled = !controller.feeLoading,
                loading = controller.feeLoading,
            )
        }
        Spacer(Modifier.height(Space.x6))
        PoctraButton(
            text = if (messageExpanded) "Remove Message" else "Add Message",
            onClick = {
                messageExpanded = !messageExpanded
                if (!messageExpanded) message = ""
            },
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Ghost,
            leadingGlyph = if (messageExpanded) Glyph.Revoke else Glyph.Plus,
            enabled = !controller.busy,
        )
        if (messageExpanded) {
            Spacer(Modifier.height(Space.x6))
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
                val validation = when {
                    recipientLookup.loading -> "Wait until recipient lookup finishes."
                    isValidOctraIdentityName(recipient) && recipientLookup.resolution == null -> recipientLookup.error ?: "Resolve recipient first."
                    !isValidOctraRecipientInput(recipientForSubmit) -> "Enter foo.oct or a valid Octra recipient address."
                    amount.replace(",", ".").toDoubleOrNull() == null -> "Enter a valid OCT amount."
                    feeOu.toLongOrNull() == null -> "Enter a valid fee in OU."
                    else -> null
                }
                if (validation != null) {
                    localError = validation
                } else {
                    controller.sendOct(recipientForSubmit, amount, if (messageExpanded) message else "", feeOu)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            loading = controller.busy,
            enabled = !controller.busy,
            leadingGlyph = Glyph.Send,
        )
    }
}

@Composable
private fun FeeOptionButton(
    label: String,
    subtitle: String,
    glyph: Glyph,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
) {
    val borderColor = when {
        selected -> SurfaceRaised
        enabled -> BorderLine
        else -> BorderLine.copy(alpha = 0.45f)
    }
    Row(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(if (selected) SurfaceRaised.copy(alpha = 0.16f) else SurfaceStrong)
            .border(1.dp, borderColor, RoundedCornerShape(Radius.md))
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = Space.x5),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(if (selected) SurfaceRaised else SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(15.dp),
                    color = if (selected) PrimaryContent else TextTertiary,
                    strokeWidth = 2.dp,
                )
            } else {
                GlyphIcon(
                    glyph = glyph,
                    modifier = Modifier.size(16.dp),
                    color = when {
                        !enabled -> TextSecondary
                        selected -> PrimaryContent
                        else -> TextTertiary
                    },
                )
            }
        }
        Spacer(Modifier.width(Space.x4))
        Column {
            Text(
                label,
                color = if (enabled) TextTertiary else TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            Text(
                subtitle,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
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
    var localError by remember { mutableStateOf<String?>(null) }
    val recipientLookup = rememberOctraRecipientLookup(controller, recipient)

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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeSendAsset, enabled = !controller.busy)
            Spacer(Modifier.width(Space.x6))
            Text("Send ${token.symbol}", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(Space.x7))
        CompactScreenIntro(
            glyph = Glyph.Assets,
            description = "Transfer ${token.symbol} to foo.oct or an Octra address.",
        )
        Spacer(Modifier.height(Space.x8))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.sm),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            border = BorderStroke(1.dp, BorderLine),
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
                localError = null
            },
            label = "Recipient address",
            placeholder = "foo.oct, app.foo.oct or oct address",
            lookupState = recipientLookup,
            glyph = Glyph.AddressBook,
            onClear = {
                recipient = ""
                selectedRecipientName = null
                localError = null
            },
            directPreviewName = selectedRecipientName,
            showDirectOctraAddressPreview = true,
            isError = localError != null,
            supportingText = localError,
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
                localError = null
            },
            label = "Amount ${token.symbol}",
            singleLine = true,
            keyboardType = KeyboardType.Decimal,
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
                        localError = null
                    }
                    .padding(horizontal = Space.x4, vertical = Space.x2),
                color = if (token.balanceRaw != "0") SurfaceRaised else TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(Space.x6))
        PoctraTextField(
            value = feeOu,
            onValueChange = {
                feeOu = it.filter(Char::isDigit).take(12)
                feeEdited = true
                localError = null
            },
            label = "Fee / Gas (OU)",
            singleLine = true,
            keyboardType = KeyboardType.Number,
            supportingText = if (controller.feeLoading) {
                "Fetching recommended fee..."
            } else {
                "Recommended ${controller.recommendedFeeOu} OU ~= ${ouToOct(controller.recommendedFeeOu)} OCT"
            },
        )
        Spacer(Modifier.height(Space.x5))
        StateBanner(
            "Token transfer uses a contract call, so OCT is required for gas.",
            isError = false,
        )
        Spacer(Modifier.height(Space.x8))
        PoctraButton(
            text = "Send ${token.symbol}",
            onClick = {
                val recipientForSubmit = recipientLookup.resolution?.address ?: recipient
                when {
                    recipient.isBlank() -> localError = "Recipient address is required."
                    recipientLookup.loading -> localError = "Wait until recipient lookup finishes."
                    isValidOctraIdentityName(recipient) && recipientLookup.resolution == null -> localError = recipientLookup.error ?: "Resolve recipient first."
                    !isValidOctraRecipientInput(recipientForSubmit) -> localError = "Enter foo.oct or a valid Octra address."
                    amount.isBlank() -> localError = "Amount is required."
                    else -> controller.sendOctraToken(token, recipientForSubmit, amount, feeOu)
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !controller.busy,
            loading = controller.busy,
            leadingGlyph = Glyph.Send,
        )
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
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            border = BorderStroke(1.dp, BorderLine),
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
            .background(if (enabled) SurfaceStrong else SurfaceMuted)
            .border(1.dp, BorderLine, RoundedCornerShape(Radius.md))
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
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        border = BorderStroke(1.dp, SurfaceRaised.copy(alpha = 0.45f)),
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
        true -> "Bridge Closed"
        false -> "Bridge Open"
        null -> "Status Syncing"
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
        border = BorderStroke(1.dp, BorderLine),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.x5, vertical = Space.x3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(RoundedCornerShape(Radius.pill))
                    .background(stateColor),
            )
            Spacer(Modifier.width(Space.x3))
            Text(
                stateLabel,
                color = stateColor,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BridgeLimitPill("Lock", "${status.dailyLockRemaining} OCT")
            Spacer(Modifier.width(Space.x2))
            BridgeLimitPill("Unlock", "${status.dailyUnlockRemaining} OCT")
        }
    }
}

@Composable
private fun BridgeLimitPill(label: String, value: String) {
    Text(
        "$label $value",
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(SurfaceBase)
            .padding(horizontal = Space.x3, vertical = Space.x2),
        color = TextSecondary,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
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
                controller.bridgeHistory.forEach { item ->
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        border = BorderStroke(
            1.dp,
            when (item.status) {
                "failed" -> ErrorRed.copy(alpha = 0.55f)
                "claimable" -> SurfaceRaised.copy(alpha = 0.70f)
                else -> BorderLine
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
                    GlyphIcon(Glyph.Bridge, modifier = Modifier.size(19.dp), color = TextTertiary)
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
            }
            Spacer(Modifier.height(Space.x5))
            DetailSeparator()
            DetailLine("From", bridgeHistoryFromAddress(item))
            DetailSeparator()
            DetailLine("To", bridgeHistoryToAddress(item))
            DetailSeparator()
            DetailLine("Step", bridgeStepLabel(item.step))
            DetailSeparator()
            DetailLine("Created", formatBridgeHistoryTime(item.createdAt))
            DetailSeparator()
            DetailLine("Updated", formatBridgeHistoryTime(item.updatedAt))
            if (item.message.isNotBlank()) {
                DetailSeparator()
                DetailLine("Message", item.message)
            }
            item.octraTxHash?.let { hash ->
                DetailSeparator()
                DetailLine("Octra Tx", hash, actionLabel = "Open", onAction = onOpenOctra)
            }
            item.evmTxHash?.let { hash ->
                DetailSeparator()
                DetailLine("EVM Tx", hash, actionLabel = "Open", onAction = onOpenEvm)
            }
            Spacer(Modifier.height(Space.x5))
            if (completed) {
                BridgeHistoryOutcome(text = "Completed successfully", glyph = Glyph.Check, isError = false)
            } else if (recoverable) {
                Spacer(Modifier.height(Space.x5))
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

@Composable
private fun BridgeHistoryOutcome(text: String, glyph: Glyph, isError: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background((if (isError) ErrorRed else SurfaceRaised).copy(alpha = 0.12f))
            .border(1.dp, (if (isError) ErrorRed else SurfaceRaised).copy(alpha = 0.45f), RoundedCornerShape(Radius.sm))
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
            .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(Radius.sm))
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
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(Radius.sm))
            .padding(horizontal = Space.x3, vertical = Space.x1),
        color = color,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 10.sp),
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
    )
}

private fun bridgeHistoryCanRecover(item: BridgeHistoryItem): Boolean {
    if (item.status == "completed") return false
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
            GlyphIcon(Glyph.Bridge, modifier = Modifier.size(25.dp), color = TextTertiary)
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
    Column(Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            border = BorderStroke(1.dp, if (isError) ErrorRed else BorderLine),
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
                        modifier = Modifier.weight(1f),
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
                border = BorderStroke(1.dp, BorderLine),
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
                            GlyphIcon(Glyph.Bridge, modifier = Modifier.size(20.dp), color = TextTertiary)
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
        border = BorderStroke(1.dp, BorderLine),
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
                    GlyphIcon(glyph, modifier = Modifier.size(17.dp), color = TextTertiary)
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
        border = BorderStroke(1.dp, BorderLine),
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
                border = BorderStroke(1.dp, BorderLine),
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
                        )
                        Spacer(Modifier.height(Space.x6))
                        Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
                            PoctraButton(
                                text = "Cancel",
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                                variant = ButtonVariant.Ghost,
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
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(SurfaceRaised.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Bridge, modifier = Modifier.size(21.dp), color = TextTertiary)
        }
        Spacer(Modifier.width(Space.x5))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextTertiary, style = MaterialTheme.typography.titleLarge)
            Text(bridgeReviewRouteLabel(direction), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
    }
    Spacer(Modifier.height(Space.x6))
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
        border = BorderStroke(1.dp, BorderLine),
    ) {
        Column(Modifier.padding(horizontal = Space.x6, vertical = Space.x3)) {
            DetailLine("Amount", "$amount ${if (direction == BridgeDirection.OctToWoct) "OCT" else "wOCT"}")
            DetailSeparator()
            DetailLine(receiverLabel, receiverAddress)
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
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceBase),
        border = BorderStroke(1.dp, BorderLine),
    ) {
        Column(Modifier.padding(Space.x5)) {
            Text("Network fee", color = TextTertiary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Space.x4))
            if (showOctraFee && direction == BridgeDirection.OctToWoct) {
                Text("Octra call fee", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
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
                        BridgeFeePreset.Custom to "Custom",
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
                        supportingText = "${ouToOct(customOctraFeeOu.toLongOrNull() ?: recommendedFeeOu)} OCT",
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
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (direction == BridgeDirection.OctToWoct) "Ethereum claim gas" else "Ethereum bridge gas",
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        Text(
                            gasEstimate?.steps ?: bridgeReviewGasLabel(direction, recommendedFeeOu),
                            color = TextSecondary,
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        )
                    }
                    if (gasLoading) CircularProgressIndicator(modifier = Modifier.size(14.dp), color = SurfaceRaised, strokeWidth = 2.dp)
                }
                Spacer(Modifier.height(Space.x3))
                if (gasEstimate == null) {
                    StateBanner("Gas estimate unavailable. The bridge will use the active RPC gas price.", isError = false, compact = true)
                } else {
                    BridgeFeePresetRow(
                        selected = selectedGas,
                        labels = mapOf(
                            BridgeFeePreset.Low to weiToEthLabel(gasEstimate.lowFeeWei),
                            BridgeFeePreset.Medium to weiToEthLabel(gasEstimate.marketFeeWei),
                            BridgeFeePreset.Market to weiToEthLabel(gasEstimate.fastFeeWei),
                            BridgeFeePreset.Custom to "Custom",
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
                            supportingText = bridgeCustomGasFeeLabel(gasEstimate, customGasGwei),
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
) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
        presets.forEach { preset ->
            val value = labels[preset].orEmpty()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(if (selected == preset) SurfaceRaised.copy(alpha = 0.18f) else SurfaceStrong)
                    .border(1.dp, if (selected == preset) SurfaceRaised else BorderLine, RoundedCornerShape(Radius.sm))
                    .clickable { onSelect(preset) }
                    .padding(horizontal = Space.x2, vertical = Space.x3),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    titles[preset] ?: preset.name.lowercase().replaceFirstChar { it.uppercase() },
                    color = if (selected == preset) TextTertiary else TextSecondary,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
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
        border = BorderStroke(1.dp, BorderLine),
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
        border = BorderStroke(1.dp, BorderLine),
    ) {
        Column(
            modifier = Modifier.padding(Space.x6),
            verticalArrangement = Arrangement.spacedBy(Space.x4),
        ) {
            ActiveNetworkLine(
                glyph = Glyph.Wallet,
                label = "Octra",
                value = octraNetwork?.let { "${it.name} (${networkTypeLabel(it.networkType)})" }
                    ?: "No active Octra RPC",
                detail = octraNetwork?.url ?: "Set an active RPC endpoint",
                onChange = controller::openRpcManagementFromBridge,
            )
            DetailSeparator()
            ActiveNetworkLine(
                glyph = Glyph.Evm,
                label = "EVM",
                value = evmNetwork?.let { "${it.name} (${evmNetworkKindLabel(it)})" }
                    ?: "No active EVM network",
                detail = evmNetwork?.let { "Chain ID ${it.chainId} - ${it.symbol}" } ?: "Set an active EVM network",
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
        border = BorderStroke(1.dp, BorderLine),
    ) {
        ActiveNetworkLine(
            glyph = Glyph.Evm,
            label = "Active EVM Network",
            value = evmNetwork?.let { "${it.name} (${evmNetworkKindLabel(it)})" }
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
    detail: String,
    onChange: () -> Unit,
    modifier: Modifier = Modifier,
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
            GlyphIcon(glyph, modifier = Modifier.size(18.dp), color = TextTertiary)
        }
        Spacer(Modifier.width(Space.x5))
        Column(Modifier.weight(1f)) {
            Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Spacer(Modifier.height(Space.x1))
            Text(value, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(detail, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.x4))
        Text(
            "Change",
            modifier = Modifier
                .clip(RoundedCornerShape(Radius.sm))
                .border(1.dp, BorderLine, RoundedCornerShape(Radius.sm))
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

@Composable
private fun BridgeBalancePanel(controller: PoctraController) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        border = BorderStroke(1.dp, BorderLine),
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
        border = BorderStroke(1.dp, if (selected) SurfaceRaised else BorderLine),
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
        border = BorderStroke(1.dp, BorderLine),
    ) {
        Row(Modifier.padding(horizontal = Space.x5, vertical = Space.x4), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(SurfaceRaised.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(glyph, modifier = Modifier.size(17.dp), color = TextTertiary)
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
        border = BorderStroke(1.dp, BorderLine),
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
        border = BorderStroke(1.dp, if (currentStep == BridgeStep.Failed) ErrorRed.copy(alpha = 0.65f) else BorderLine),
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
                )
                .border(
                    1.dp,
                    when {
                        failed -> ErrorRed
                        done || active -> SurfaceRaised
                        else -> BorderLine
                    },
                    RoundedCornerShape(Radius.md),
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
    LaunchedEffect(Unit) {
        controller.refreshEvmDashboard()
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
    LaunchedEffect(Unit) {
        controller.refreshEvmHistory()
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
    var localError by remember { mutableStateOf<String?>(null) }
    val recipientLookup = rememberEvmRecipientLookup(controller, recipient)

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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeEvmSend, enabled = !controller.evmSending)
            Spacer(Modifier.width(Space.x6))
            Text("Send $symbol", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(Space.x7))
        CompactScreenIntro(
            glyph = Glyph.Send,
            description = when {
                selectedNft != null -> "Send ${selectedNft.symbol} NFT from your derived EVM account."
                selectedToken != null -> "Send ${selectedToken.symbol} ERC20 from your derived EVM account."
                else -> "Send ${network?.symbol ?: "native EVM coin"} from your derived EVM account."
            },
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
            localError = localError,
            recipientLookup = recipientLookup,
            onRecipientChange = {
                recipient = it
                localError = null
            },
            onAmountChange = {
                amount = it
                localError = null
            },
            onMax = {
                if (selectedNft == null) amount = controller.evmMaxAmount(selectedToken)
                localError = null
            },
            onSubmit = {
                val recipientForSubmit = recipientLookup.resolution?.address ?: recipient
                val validation = when {
                    recipientLookup.loading -> "Wait until recipient lookup finishes."
                    isValidEvmNameInput(recipient) && recipientLookup.resolution == null -> recipientLookup.error ?: "Resolve ENS first."
                    !isValidEvmRecipientInput(recipientForSubmit) -> "Enter a valid EVM address or ENS name."
                    selectedNft == null && amount.replace(",", ".").toDoubleOrNull() == null -> "Enter a valid $symbol amount."
                    selectedNft != null && !selectedNft.owned -> "This NFT is not owned by the active EVM account."
                    account?.connected == false -> account.message ?: "EVM RPC is not connected."
                    else -> null
                }
                if (validation != null) {
                    localError = validation
                } else {
                    controller.prepareEvmSendDialog(recipientForSubmit, if (selectedNft != null) "1" else amount)
                }
            },
        )
        Spacer(Modifier.height(Space.x6))
        EvmGasInfoCard(controller = controller, selectedToken = selectedToken, selectedNft = selectedNft)
    }
}

@Composable
private fun EvmReceiveScreen(controller: PoctraController) {
    val clipboard = LocalClipboardManager.current

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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeEvmReceive)
            Spacer(Modifier.width(Space.x6))
            Text("Receive EVM", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(Space.x7))
        CompactScreenIntro(
            glyph = Glyph.Receive,
            description = "Use this address for native EVM coins, ERC20 tokens, and NFTs.",
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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeEvmNetworkSettings, enabled = !controller.busy)
            Spacer(Modifier.width(Space.x6))
            Text("EVM Network", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(Space.x7))
        CompactScreenIntro(
            glyph = Glyph.Wifi,
            description = "Choose the active EVM chain for balances, assets, gas, and transactions.",
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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeEvmAddNetwork, enabled = !controller.busy)
            Spacer(Modifier.width(Space.x6))
            Text(if (isEditing) "Edit Network" else "Add Custom Network", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(Space.x7))
        CompactScreenIntro(
            glyph = if (isEditing) Glyph.Pencil else Glyph.Plus,
            description = if (isEditing) {
                "Update this EVM network endpoint, symbol, and explorer."
            } else {
                "Add an EVM-compatible network with its RPC and explorer endpoint."
            },
        )
        Spacer(Modifier.height(Space.x8))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            border = BorderStroke(1.dp, BorderLine),
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
                        .border(1.dp, SurfaceRaised.copy(alpha = 0.35f), RoundedCornerShape(Radius.md))
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
                    .background(SurfaceStrong)
                    .border(1.dp, BorderLine, RoundedCornerShape(Radius.md))
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
    localError: String?,
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
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        border = BorderStroke(1.dp, BorderLine),
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
                isError = localError != null,
                supportingText = localError,
            )
            Spacer(Modifier.height(Space.x6))
            if (selectedNft == null) {
                PoctraTextField(
                    value = amount,
                    onValueChange = { raw -> onAmountChange(raw.filter { it.isDigit() || it == '.' || it == ',' }) },
                    label = "Amount $symbol",
                    singleLine = true,
                    keyboardType = KeyboardType.Decimal,
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
                    border = BorderStroke(1.dp, BorderLine),
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
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        border = BorderStroke(1.dp, BorderLine),
    ) {
        Column(Modifier.padding(Space.x7)) {
            DetailLine("Network", controller.evmNetworkLabel())
            DetailSeparator()
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
        border = BorderStroke(1.dp, BorderLine),
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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeEvmAddToken, enabled = !controller.evmLookupLoading)
            Spacer(Modifier.width(Space.x6))
            Text("Add ERC20", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(Space.x7))
        CompactScreenIntro(
            glyph = Glyph.Assets,
            description = "Lookup an ERC20 contract and add it to this EVM account.",
        )
        Spacer(Modifier.height(Space.x8))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            border = BorderStroke(1.dp, BorderLine),
        ) {
            Column(Modifier.padding(Space.x7)) {
                Text("Token Contract", color = TextTertiary, fontWeight = FontWeight.SemiBold)
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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeEvmAddNft, enabled = !controller.evmLookupLoading)
            Spacer(Modifier.width(Space.x6))
            Text("Add NFT", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(Space.x7))
        CompactScreenIntro(
            glyph = Glyph.Browser,
            description = "Lookup an NFT contract and token ID before adding it locally.",
        )
        Spacer(Modifier.height(Space.x8))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            border = BorderStroke(1.dp, BorderLine),
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
        border = BorderStroke(1.dp, BorderLine),
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
                .border(1.dp, BorderLine, RoundedCornerShape(Radius.md))
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
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        border = BorderStroke(1.dp, BorderLine),
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
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            controller.evmTransactions.forEachIndexed { index, tx ->
                EvmActivityRow(
                    tx = tx,
                    address = controller.evmAccount?.address.orEmpty(),
                    onClick = { controller.openEvmTransaction(tx) },
                )
                if (index != controller.evmTransactions.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(BorderLine),
                    )
                }
            }
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

private fun isValidOctraAddress(value: String): Boolean = value.startsWith("oct") && value.length == 47

private fun isValidOctraIdentityName(value: String): Boolean {
    val normalized = value.trim().lowercase()
    val path = normalized
        .removeSuffix(".oct")
        .removeSuffix(".octra")
        .removeSuffix(".id")
    val parts = path.split('.')
    return normalized.contains('.') &&
        parts.isNotEmpty() &&
        parts.size <= 2 &&
        parts.all { it.matches(Regex("^[a-z0-9]{3,63}$")) }
}

private data class RecipientResolutionPreview(
    val name: String,
    val address: String,
    val source: String,
)

private data class RecipientLookupUiState(
    val resolution: RecipientResolutionPreview? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

@Composable
private fun rememberOctraRecipientLookup(
    controller: PoctraController,
    value: String,
): RecipientLookupUiState {
    var state by remember { mutableStateOf(RecipientLookupUiState()) }
    LaunchedEffect(value, controller.activeIdentityProvider?.id, controller.activeRpcNetwork?.id) {
        val query = value.trim()
        state = RecipientLookupUiState()
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
                                source = "Octra Identity",
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
                                source = "ENS",
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
private fun RecipientLookupField(
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
) {
    val cleanValue = value.trim()
    val directResolution = if (showDirectOctraAddressPreview && isValidOctraAddress(cleanValue)) {
        RecipientResolutionPreview(
            name = directPreviewName?.takeIf { it.isNotBlank() } ?: cleanValue,
            address = cleanValue,
            source = if (!directPreviewName.isNullOrBlank()) "Octra Identity" else "Octra Address",
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
    )
}

@Composable
private fun ResolvedRecipientCard(
    resolution: RecipientResolutionPreview,
    glyph: Glyph,
    onClear: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised.copy(alpha = 0.14f)),
        border = BorderStroke(1.dp, SurfaceRaised),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = Space.x5, vertical = Space.x4),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(SurfaceRaised),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(glyph, modifier = Modifier.size(17.dp), color = PrimaryContent)
            }
            Spacer(Modifier.width(Space.x4))
            Column(Modifier.weight(1f)) {
                Text(
                    "${resolution.name} (${resolution.source.lowercase()})",
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
            .border(1.dp, if (selected) SurfaceRaised else BorderLine, RoundedCornerShape(Radius.md))
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
        Spacer(Modifier.height(34.dp))
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(if (outgoing) SurfaceMuted else SurfaceRaised.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(if (outgoing) Glyph.Send else Glyph.Receive, modifier = Modifier.size(34.dp), color = TextTertiary)
        }
        Spacer(Modifier.height(Space.x8))
        Text(
            text = "${if (outgoing) "-" else "+"}${formatOctAmount(tx.amount)} OCT",
            color = TextTertiary,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(Space.x4))
        NetworkBadge(tx.status)
        Spacer(Modifier.height(Space.x8))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            border = BorderStroke(1.dp, BorderLine),
        ) {
            Column(Modifier.padding(horizontal = Space.x7, vertical = Space.x2)) {
                DetailLine("Hash", tx.hash)
                DetailSeparator()
                DetailLine(
                    label = "From",
                    value = tx.from,
                    actionLabel = if (fromExplorerUrl != null) "View" else null,
                    onAction = fromExplorerUrl?.let { url -> { controller.openDAppBrowser("Address", url) } },
                )
                DetailSeparator()
                DetailLine(
                    label = "To",
                    value = tx.to,
                    actionLabel = if (toExplorerUrl != null) "View" else null,
                    onAction = toExplorerUrl?.let { url -> { controller.openDAppBrowser("Address", url) } },
                )
                DetailSeparator()
                DetailLine("Type", if (outgoing) "Sent" else "Received")
                DetailSeparator()
                DetailLine("Operation", tx.opType)
                DetailSeparator()
                DetailLine("Timestamp", formatTxTime(tx.timestamp))
                tx.message?.let {
                    DetailSeparator()
                    DetailLine("Message", it)
                }
            }
        }
        Spacer(Modifier.height(Space.x7))
        PoctraButton(
            text = "View on Explorer",
            onClick = { txExplorerUrl?.let { controller.openDAppBrowser("Transaction", it) } },
            modifier = Modifier.fillMaxWidth(),
            leadingGlyph = Glyph.Browser,
            enabled = txExplorerUrl != null,
        )
        Spacer(Modifier.height(Space.x5))
        PoctraButton(
            text = "Copy Hash",
            onClick = {
                clipboard.setText(AnnotatedString(tx.hash))
                controller.showMessage("Transaction hash copied.")
            },
            modifier = Modifier.fillMaxWidth(),
            leadingGlyph = Glyph.Copy,
        )
    }
}

@Composable
private fun DetailLine(
    label: String,
    value: String,
    actionLabel: String? = null,
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
                Text(
                    actionLabel,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.md))
                        .clickable(onClick = onAction)
                        .padding(horizontal = Space.x4, vertical = Space.x2),
                    color = SurfaceRaised,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Spacer(Modifier.height(Space.x2))
        Text(value.ifBlank { "-" }, color = TextTertiary, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DetailSeparator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(BorderLine),
    )
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
                border = BorderStroke(1.dp, BorderLine),
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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", controller::closeSettings)
            Spacer(Modifier.width(Space.x6))
            Text("Settings", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(Space.x7))
        CompactScreenIntro(
            glyph = Glyph.Settings,
            description = "Network, security, and wallet preferences.",
        )
        Spacer(Modifier.height(Space.x8))
        Column(verticalArrangement = Arrangement.spacedBy(Space.x5)) {
            SettingsDestinationButton(
                title = "RPC Management",
                subtitle = controller.activeRpcNetwork?.let { "${it.name} - ${it.networkType}" } ?: controller.currentRpcUrl,
                glyph = Glyph.Wifi,
                onClick = controller::openRpcManagement,
            )
            SettingsDestinationButton(
                title = "Address Book",
                subtitle = if (controller.addressBookContacts.isEmpty()) "Save reusable recipients" else "${controller.addressBookContacts.size} saved recipients",
                glyph = Glyph.AddressBook,
                onClick = controller::openAddressBook,
            )
            SettingsDestinationButton(
                title = "Octra Identity Provider",
                subtitle = controller.activeIdentityProvider?.let { "${it.name} - ${shortAddress(it.contractAddress)}" } ?: "Resolve foo.oct recipients",
                glyph = Glyph.Browser,
                onClick = controller::openIdentityProviderSettings,
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
                title = "Connected dApps",
                subtitle = if (controller.dApps.isEmpty()) "No connected dApps" else "${controller.dApps.size} connected",
                glyph = Glyph.Explore,
                onClick = controller::openConnectedDApps,
            )
            SettingsDestinationButton(
                title = "Dark Mode",
                subtitle = if (controller.darkMode) "Dark mode enabled" else "Light mode enabled",
                glyph = if (controller.darkMode) Glyph.Moon else Glyph.Sun,
                onClick = controller::openAppearanceSettings,
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
                .background(SurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Settings, modifier = Modifier.size(34.dp), color = PrimaryContent)
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
            border = BorderStroke(1.dp, BorderLine),
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
private fun SettingsDestinationButton(
    title: String,
    subtitle: String,
    glyph: Glyph,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        border = BorderStroke(1.dp, BorderLine),
    ) {
        Row(
            modifier = Modifier.padding(Space.x7),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(SurfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(glyph, modifier = Modifier.size(22.dp), color = TextTertiary)
            }
            Spacer(Modifier.width(Space.x6))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextTertiary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(Space.x2))
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            GlyphIcon(Glyph.Go, modifier = Modifier.size(20.dp), color = TextSecondary)
        }
    }
}

@Composable
private fun CompactScreenIntro(glyph: Glyph, description: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, modifier = Modifier.size(22.dp), color = TextTertiary)
        }
        Spacer(Modifier.width(Space.x6))
        Text(
            text = description,
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun SettingsDetailScreen(
    title: String,
    description: String,
    glyph: Glyph,
    onBack: () -> Unit,
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
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", onBack)
            Spacer(Modifier.width(Space.x6))
            Text(title, color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(34.dp))
        Box(
            modifier = Modifier
                .size(70.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(SurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, modifier = Modifier.size(34.dp), color = PrimaryContent)
        }
        Spacer(Modifier.height(Space.x8))
        Text(title, color = TextTertiary, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.x4))
        Text(description, color = TextSecondary, textAlign = TextAlign.Center)
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
    val secret = wallet?.let(::backupSecretFor)
    val clipboard = LocalClipboardManager.current
    val clipboardScope = rememberCoroutineScope()
    var visible by remember(wallet?.address) { mutableStateOf(false) }
    var showReauthPin by remember { mutableStateOf(false) }
    var pendingAction by remember { mutableStateOf<BackupKeyAction?>(null) }

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
            BackupKeyAction.Reveal -> visible = true
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
                pendingAction = action
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
        if (wallet == null || secret == null) {
            EmptyPanel("Wallet locked")
        } else {
            StateBanner(
                text = "Sensitive information. Anyone with this ${secret.label.lowercase()} can control this wallet.",
                isError = true,
            )
            Spacer(Modifier.height(Space.x7))
            SensitiveKeyCard(
                title = secret.label,
                description = secret.description,
                value = secret.value,
                visible = visible,
                onToggle = {
                    if (visible) visible = false else requestSensitiveAccess(BackupKeyAction.Reveal, secret)
                },
                onCopy = {
                    if (visible) copySecret(secret) else requestSensitiveAccess(BackupKeyAction.Copy, secret)
                },
            )
            Spacer(Modifier.height(Space.x7))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.md),
                colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
                border = BorderStroke(1.dp, BorderLine),
            ) {
                Column(Modifier.padding(Space.x7)) {
                    DetailLine("Wallet Name", controller.walletDisplayName(wallet.address))
                    DetailSeparator()
                    DetailLine("Address", wallet.address)
                    DetailSeparator()
                    DetailLine("Wallet Type", walletTypeLabel(wallet))
                }
            }
        }
    }

    if (showReauthPin && secret != null) {
        ReauthPinDialog(
            busy = controller.busy,
            onDismiss = {
                showReauthPin = false
                pendingAction = null
            },
            onConfirm = { pin ->
                val action = pendingAction
                showReauthPin = false
                pendingAction = null
                if (action != null) {
                    controller.confirmSecurityWithPin(pin) {
                        completeVerifiedAction(action, secret)
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

private data class BackupSecret(
    val label: String,
    val value: String,
    val description: String,
)

private fun backupSecretFor(wallet: Wallet): BackupSecret {
    val mnemonic = wallet.mnemonic.orEmpty()
    return when {
        wallet.type != WalletType.ImportedPrivateKey &&
            wallet.type != WalletType.ImportedEthereumPrivateKey &&
            mnemonic.isNotBlank() -> {
            BackupSecret(
                label = "Mnemonic Phrase",
                value = mnemonic,
                description = "Use this phrase to restore the same Octra wallet.",
            )
        }
        wallet.type == WalletType.ImportedEthereumPrivateKey -> {
            BackupSecret(
                label = "Private Key (Hex)",
                value = "0x${wallet.privateKeyBase64.toPrivateKeyHex()}",
                description = "Use this key only in trusted Ethereum or Octra-compatible wallets.",
            )
        }
        else -> {
            BackupSecret(
                label = "Private Key (Base64)",
                value = wallet.privateKeyBase64,
                description = "Use this key only in trusted Octra-compatible wallets.",
            )
        }
    }
}

private fun String.toPrivateKeyHex(): String = runCatching {
    Base64.decode(this, Base64.NO_WRAP).joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xff)
    }
}.getOrDefault(this)

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
        border = BorderStroke(1.dp, BorderLine),
    ) {
        Column(Modifier.padding(Space.x7)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, color = TextTertiary, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(Space.x2))
                    Text(description, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.width(Space.x5))
                SensitiveSmallIconButton(
                    glyph = if (visible) Glyph.EyeOff else Glyph.Eye,
                    contentDescription = if (visible) "Hide backup key" else "Show backup key",
                    onClick = onToggle,
                )
                Spacer(Modifier.width(Space.x5))
                SensitiveSmallIconButton(
                    glyph = Glyph.Copy,
                    contentDescription = "Copy backup key",
                    onClick = onCopy,
                )
            }
            Spacer(Modifier.height(Space.x6))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(SurfaceBase)
                    .border(1.dp, BorderLine, RoundedCornerShape(Radius.sm))
                    .padding(Space.x6),
            ) {
                Text(
                    text = if (visible) value.ifBlank { "-" } else maskedSensitiveValue(value),
                    color = if (visible) TextTertiary else TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    minLines = if (visible && value.contains(" ")) 4 else 2,
                )
            }
        }
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
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            border = BorderStroke(1.dp, BorderLine),
        ) {
            Row(
                modifier = Modifier.padding(Space.x7),
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
                        .clickable(onClick = controller::toggleDarkMode)
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
                                    .border(1.dp, BorderLine, RoundedCornerShape(Radius.md))
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
    return permission.replace('_', ' ').replace('-', ' ').lowercase()
}

@Composable
private fun IdentityProviderSettingsScreen(controller: PoctraController) {
    val activeNetwork = controller.activeRpcNetwork?.networkType ?: "devnet"
    val activeId = controller.activeIdentityProvider?.id

    SettingsDetailScreen(
        title = "Octra Identity Provider",
        description = "Choose the contract used to resolve foo.oct recipients.",
        glyph = Glyph.Browser,
        onBack = controller::closeSettingsDetail,
    ) {
        StateBanner(
            text = "Active network: ${activeNetwork.uppercase()}",
            isError = false,
        )
        Spacer(Modifier.height(Space.x6))

        if (controller.identityProviders.isEmpty()) {
            EmptyPanel("No identity provider for $activeNetwork")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x5)) {
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
        description = "Add the Octra Identity contract used to resolve foo.oct recipients.",
        glyph = Glyph.Browser,
        onBack = controller::closeAddIdentityProvider,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            border = BorderStroke(1.dp, BorderLine),
        ) {
            Column(Modifier.padding(Space.x7)) {
                StateBanner("Network: ${activeNetwork.uppercase()}", isError = false)
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
                            controller.addIdentityProvider(name.trim(), contractAddress.trim(), activeNetwork)
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
                StateBanner("Network: ${activeNetwork.uppercase()}", isError = false)
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
            .background(if (active) SurfaceRaised.copy(alpha = 0.14f) else SurfaceStrong)
            .border(1.dp, if (active) SurfaceRaised else BorderLine, RoundedCornerShape(Radius.md))
            .padding(Space.x6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(Radius.md))
                .border(1.dp, if (active) SurfaceRaised else TextSecondary, RoundedCornerShape(Radius.md))
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
                OpTypeBadge(provider.network.uppercase())
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
            EmptyPanel("No saved recipients")
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
            border = BorderStroke(1.dp, BorderLine),
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
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onUse != null) Modifier.clickable(onClick = onUse) else Modifier),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        border = BorderStroke(1.dp, BorderLine),
    ) {
        Row(
            modifier = Modifier.padding(Space.x6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(SurfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.AddressBook, modifier = Modifier.size(20.dp), color = TextTertiary)
            }
            Spacer(Modifier.width(Space.x5))
            Column(Modifier.weight(1f)) {
                Text(contact.label, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                contact.identityName?.let {
                    Text(
                        "$it (octra identity)",
                        color = SurfaceRaised,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(shortAddress(contact.address), color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
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
                    .border(1.dp, ErrorRed.copy(alpha = 0.45f), RoundedCornerShape(Radius.sm))
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

        Spacer(Modifier.height(34.dp))
        Image(
            painter = painterResource(com.poctra.wallet.R.drawable.poctra_logo),
            contentDescription = "poctra",
            modifier = Modifier.size(78.dp),
        )
        Spacer(Modifier.height(Space.x8))
        Text("poctra", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(Space.x4))
        Text("pocket octra", color = TextSecondary, textAlign = TextAlign.Center)
        Spacer(Modifier.height(Space.x8))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            border = BorderStroke(1.dp, BorderLine),
        ) {
            Column(Modifier.padding(horizontal = Space.x7, vertical = Space.x2)) {
                DetailLine("Version", "Debug")
                DetailSeparator()
                DetailLine("Active Network", controller.activeRpcNetwork?.networkType ?: "mainnet")
                DetailSeparator()
                DetailLine("RPC", controller.currentRpcUrl)
                DetailSeparator()
                DetailLine("Explorer", controller.activeRpcNetwork?.explorerUrl.orEmpty())
                DetailSeparator()
                DetailLine("Wallets", "${controller.vault?.wallets?.size ?: 0}")
            }
        }
    }
}

@Composable
private fun RpcManagementPanel(controller: PoctraController) {
    val activeId = controller.activeRpcNetwork?.id

    SettingsInlinePanel {
        val status = controller.rpcStatus
        val statusText = when (status?.state) {
            RpcStatus.State.Connected -> "Connected${status.latestEpoch?.let { " - epoch $it" } ?: ""}"
            RpcStatus.State.Failed -> status.message ?: "Connection failed"
            else -> "Checking connection"
        }
        StateBanner(text = statusText, isError = status?.state == RpcStatus.State.Failed)
        Spacer(Modifier.height(Space.x6))

        Column(verticalArrangement = Arrangement.spacedBy(Space.x5)) {
            controller.rpcNetworks.forEach { network ->
                RpcNetworkRow(
                    network = network,
                    active = network.id == activeId,
                    canDelete = controller.rpcNetworks.size > 1,
                    onActivate = { controller.activateRpcNetwork(network.id) },
                    onDelete = { controller.deleteRpcNetwork(network.id) },
                    busy = controller.busy,
                )
            }
        }

        Spacer(Modifier.height(Space.x6))
        PoctraButton(
            text = "Add RPC Network",
            onClick = controller::openAddRpcNetwork,
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Ghost,
            leadingGlyph = Glyph.Plus,
            enabled = !controller.busy,
        )
    }
}

@Composable
private fun AddRpcNetworkScreen(controller: PoctraController) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var networkType by remember(controller.activeRpcNetwork?.networkType) {
        mutableStateOf(controller.activeRpcNetwork?.networkType ?: "devnet")
    }
    var explorerUrl by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }

    SettingsDetailScreen(
        title = "Add RPC Network",
        description = "Add a custom Octra RPC endpoint and explorer URL.",
        glyph = Glyph.Wifi,
        onBack = controller::closeAddRpcNetwork,
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            border = BorderStroke(1.dp, BorderLine),
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
                    text = "Save Network",
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
private fun RpcNetworkRow(
    network: RpcNetwork,
    active: Boolean,
    canDelete: Boolean,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
    busy: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(if (active) SurfaceRaised.copy(alpha = 0.14f) else SurfaceStrong)
            .border(1.dp, if (active) SurfaceRaised else BorderLine, RoundedCornerShape(Radius.md))
            .clickable(enabled = !busy, onClick = onActivate)
            .padding(Space.x6),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(Radius.md))
                .border(1.dp, if (active) SurfaceRaised else TextSecondary, RoundedCornerShape(Radius.md))
                .background(if (active) SurfaceRaised else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (active) GlyphIcon(Glyph.Check, modifier = Modifier.size(16.dp), color = PrimaryContent)
        }
        Spacer(Modifier.width(Space.x6))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(network.name, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(Space.x4))
                OpTypeBadge(network.networkType.uppercase())
            }
            Spacer(Modifier.height(Space.x2))
            Text(network.url, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (network.explorerUrl.isNotBlank()) {
                Text(network.explorerUrl, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (canDelete) {
            Text(
                "Delete",
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.md))
                    .clickable(enabled = !busy, onClick = onDelete)
                    .padding(horizontal = Space.x4, vertical = Space.x3),
                color = ErrorRed,
                style = MaterialTheme.typography.bodyMedium,
            )
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
            .background(if (selected) SurfaceRaised.copy(alpha = 0.16f) else SurfaceStrong)
            .border(1.dp, if (selected) SurfaceRaised else BorderLine, RoundedCornerShape(Radius.md))
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(BorderLine),
    )
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
            controller.unlockWithBiometric(authenticatedCipher)
        },
        onError = { message ->
            onPromptClosed?.invoke()
            controller.showError(message)
        },
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
