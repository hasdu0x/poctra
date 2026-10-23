package com.poctra.wallet.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.poctra.wallet.R
import com.poctra.wallet.ApprovalReviewItem
import com.poctra.wallet.ConsumerRiskItem
import com.poctra.wallet.ConsumerRiskLevel
import com.poctra.wallet.DomainGuardRule
import com.poctra.wallet.FundingProvider
import com.poctra.wallet.PoctraController
import com.poctra.wallet.PortfolioHolding
import com.poctra.wallet.UnifiedActivityItem
import kotlin.math.floor

private const val ConsumerDialogWidthFraction = 0.96f
@Composable
fun SecurityCenterScreen(controller: PoctraController) {
    SettingsDetailScreen(
        title = "Security Center",
        description = "Review wallet protection, dApp permissions, network health, and recovery readiness.",
        glyph = Glyph.Fingerprint,
        onBack = controller::closeSettingsDetail,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x5)) {
            controller.securityRisks.forEach { risk ->
                ConsumerRiskRow(risk)
            }
        }
        Spacer(Modifier.height(Space.x8))
        SecurityCenterActionGrid(controller)
    }
}

@Composable
fun ConsumerRiskRow(risk: ConsumerRiskItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(
            if (risk.level == ConsumerRiskLevel.Safe) Glyph.Check else Glyph.Info,
            modifier = Modifier.size(15.dp),
            color = riskColor(risk.level),
        )
        Spacer(Modifier.width(Space.x4))
        Column(Modifier.weight(1f)) {
            Text(risk.title, color = TextTertiary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Space.x1))
            Text(
                risk.description,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SecurityCenterActionGrid(controller: PoctraController) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
        SecurityActionCard(
            title = "Backup",
            subtitle = "Keys",
            glyph = Glyph.Eye,
            modifier = Modifier.weight(1f),
            onClick = controller::openBackupSettings,
        )
        SecurityActionCard(
            title = "dApps",
            subtitle = "${controller.dApps.size}",
            glyph = Glyph.Explore,
            modifier = Modifier.weight(1f),
            onClick = controller::openConnectedDApps,
        )
    }
    Spacer(Modifier.height(Space.x5))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
        SecurityActionCard(
            title = "Contacts",
            subtitle = "${controller.addressBookContacts.size}",
            glyph = Glyph.AddressBook,
            modifier = Modifier.weight(1f),
            onClick = controller::openAddressBook,
        )
        SecurityActionCard(
            title = "Network",
            subtitle = controller.activeRpcNetwork?.networkType ?: "RPC",
            glyph = Glyph.Wifi,
            modifier = Modifier.weight(1f),
            onClick = controller::openRpcManagement,
        )
    }
}

@Composable
private fun SecurityActionCard(
    title: String,
    subtitle: String,
    glyph: Glyph,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceRaised.copy(alpha = 0.055f)),
    ) {
        Column(Modifier.padding(Space.x6)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(SurfaceRaised.copy(alpha = 0.14f), RoundedCornerShape(Radius.md)),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(glyph, Modifier.size(19.dp), SurfaceRaised)
                }
                Spacer(Modifier.weight(1f))
                GlyphIcon(Glyph.Go, Modifier.size(14.dp), SurfaceRaised)
            }
            Spacer(Modifier.height(Space.x5))
            Text(
                title,
                color = TextTertiary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(Space.x2))
            Text(
                subtitle,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                minLines = 1,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ConsumerPlainDetailScreen(
    title: String,
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
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", onBack)
            Spacer(Modifier.width(Space.x6))
            Text(
                title,
                modifier = Modifier.weight(1f),
                color = TextTertiary,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            headerAction?.invoke()
        }
        Spacer(Modifier.height(Space.x8))
        content()
    }
}

@Composable
private fun HeaderTextAction(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(SurfaceMuted.copy(alpha = 0.78f))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.x4, vertical = Space.x2),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = SurfaceRaised, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun HeaderRefreshAction(
    loading: Boolean,
    onClick: () -> Unit,
    label: String = "Refresh",
) {
    val enabled = !loading
    Column(
        modifier = Modifier
            .width(50.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(Radius.sm))
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
                Glyph.Refresh,
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
private fun PortfolioPieCard(holdings: List<PortfolioHolding>) {
    val slices = portfolioSlices(holdings)
    val totalUsd = holdings.sumOf { it.valueUsd.coerceAtLeast(0.0) }
    // Count unpriced assets: have balance but no USD value
    val unpricedCount = holdings.count { holding ->
        val hasBalance = holding.balance.replace(",", ".").toDoubleOrNull()?.let { it > 0.0 } == true
        hasBalance && holding.valueUsd == 0.0
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(Modifier.padding(Space.x7), horizontalAlignment = Alignment.CenterHorizontally) {
            if (slices.isEmpty()) {
                Text("No portfolio data yet", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            } else {
                Text("Total Value Estimate", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.height(Space.x2))
                Text(formatPortfolioUsd(totalUsd), color = TextTertiary, style = MaterialTheme.typography.titleLarge)
                if (unpricedCount > 0) {
                    Spacer(Modifier.height(Space.x2))
                    Text(
                        "+$unpricedCount asset${if (unpricedCount > 1) "s" else ""} without USD pricing",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Spacer(Modifier.height(Space.x6))
                Canvas(modifier = Modifier.size(158.dp)) {
                    var startAngle = -90f
                    slices.forEach { slice ->
                        drawArc(
                            color = slice.color,
                            startAngle = startAngle,
                            sweepAngle = slice.sweep,
                            useCenter = true,
                        )
                        startAngle += slice.sweep
                    }
                    drawCircle(color = SurfaceStrong, radius = size.minDimension * 0.31f)
                }
                Spacer(Modifier.height(Space.x7))
                Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
                    slices.take(6).forEach { slice ->
                        PortfolioLegendRow(slice)
                    }
                }
            }
        }
    }
}

private fun formatPortfolioUsd(value: Double): String {
    if (!value.isFinite() || value <= 0.0) return "~ \$0.00"
    val decimals = if (value >= 100.0) 2 else 4
    return "~ \$" + "%,.${decimals}f".format(value)
}

@Composable
private fun PortfolioLegendRow(slice: PortfolioSlice) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(slice.color),
        )
        Spacer(Modifier.width(Space.x4))
        Text(slice.holding.title, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
        Text("${slice.percent}%", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
    }
}

private data class PortfolioSlice(
    val holding: PortfolioHolding,
    val color: Color,
    val sweep: Float,
    val percent: Int,
)

private fun portfolioSlices(holdings: List<PortfolioHolding>): List<PortfolioSlice> {
    if (holdings.isEmpty()) return emptyList()
    val palette = listOf(
        SurfaceRaised,
        Color(0xFF60A5FA),
        Warning,
        Color(0xFFA78BFA),
        Color(0xFFF472B6),
        Color(0xFF22D3EE),
        Color(0xFFFBBF24),
    )
    val hasUsdValues = holdings.any { it.valueUsd > 0.0 }
    val rawValues = if (hasUsdValues) {
        holdings.map { it.valueUsd.coerceAtLeast(0.0) }
    } else {
        holdings.map { holding ->
            when {
                holding.balance.replace(",", ".").toDoubleOrNull()?.let { it > 0.0 } == true -> 1.0
                else -> 0.0
            }
        }
    }
    val values = if (rawValues.sum() > 0.0) rawValues else holdings.map { 1.0 }
    val total = values.sum().coerceAtLeast(1.0)
    val percents = normalizedPortfolioPercents(values)
    var remainingSweep = 360.0
    var remainingPositive = values.count { it > 0.0 }
    return holdings.mapIndexed { index, holding ->
        val fraction = values[index] / total
        val sweep = if (values[index] <= 0.0) {
            0f
        } else {
            remainingPositive -= 1
            val nextSweep = if (remainingPositive == 0) remainingSweep else fraction * 360.0
            remainingSweep -= nextSweep
            nextSweep.toFloat().coerceAtLeast(0f)
        }
        PortfolioSlice(
            holding = holding,
            color = palette[index % palette.size],
            sweep = sweep,
            percent = percents.getOrElse(index) { 0 },
        )
    }
}

private fun normalizedPortfolioPercents(values: List<Double>): List<Int> {
    val total = values.sum()
    if (total <= 0.0) return values.map { 0 }
    val rawPercents = values.map { value -> if (value > 0.0) value / total * 100.0 else 0.0 }
    val floors = rawPercents.map { floor(it).toInt() }.toMutableList()
    
    // Ensure minimum 1% for values > 0 to show in pie chart
    rawPercents.forEachIndexed { index, raw ->
        if (raw > 0.0 && floors[index] == 0) {
            floors[index] = 1
        }
    }
    
    var currentSum = floors.sum()
    
    // If forcing minimums pushed total over 100, take back from largest values
    if (currentSum > 100) {
        val excess = currentSum - 100
        rawPercents
            .mapIndexed { index, raw -> index to raw }
            .filter { floors[it.first] > 1 }
            .sortedByDescending { it.second }
            .take(excess)
            .forEach { (index, _) ->
                floors[index] -= 1
                currentSum -= 1
            }
    }
    
    // Distribute remaining to largest fractional parts
    var remaining = 100 - currentSum
    if (remaining > 0) {
        rawPercents
            .mapIndexed { index, raw -> index to (raw - floors[index]) }
            .filter { values[it.first] > 0.0 }
            .sortedByDescending { it.second }
            .forEach { (index, _) ->
                if (remaining > 0) {
                    floors[index] += 1
                    remaining -= 1
                }
            }
    }
    
    return floors
}

@Composable
private fun AssetGlyphBubble(symbol: String, fallbackGlyph: Glyph, source: String) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(sourceColor(source).copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        if (assetIconRes(symbol, source) != null) {
            AssetTokenIcon(symbol = symbol, source = source, modifier = Modifier.size(21.dp))
        } else {
            GlyphIcon(fallbackGlyph, modifier = Modifier.size(19.dp), color = sourceColor(source))
        }
    }
}

@Composable
private fun AssetTokenIcon(symbol: String, source: String, modifier: Modifier = Modifier) {
    val resId = assetIconRes(symbol, source)
    if (resId == null) {
        GlyphIcon(Glyph.Assets, modifier = modifier, color = sourceColor(source))
    } else {
        Image(
            painter = painterResource(resId),
            contentDescription = null,
            modifier = modifier,
        )
    }
}

private fun assetIconRes(symbol: String, source: String): Int? = when (symbol.uppercase()) {
    "OCT", "WOCT" -> R.drawable.octra_logo
    "ETH" -> R.drawable.ic_eth
    "USDT" -> R.drawable.ic_usdt
    "NFT", "NFTS" -> R.drawable.ic_assets
    else -> if (source.contains("evm", ignoreCase = true) || source.contains("ethereum", ignoreCase = true)) R.drawable.ic_evm else null
}

@Composable
private fun SourceBadge(source: String) {
    BadgePill(text = source, color = sourceColor(source))
}

@Composable
private fun StatusBadge(status: String) {
    BadgePill(text = status.lowercase(), color = statusTextColor(status))
}

@Composable
private fun BadgePill(text: String, color: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(color.copy(alpha = 0.18f))
            .padding(horizontal = Space.x4, vertical = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.x2),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(RoundedCornerShape(Radius.pill))
                .background(color),
        )
        Text(
            text = text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun sourceColor(source: String): Color = when {
    source.contains("octra", ignoreCase = true) -> SurfaceRaised
    source.contains("ethereum", ignoreCase = true) || source.contains("evm", ignoreCase = true) -> Color(0xFF60A5FA)
    source.contains("bridge", ignoreCase = true) -> Warning
    else -> TextSecondary
}

@Composable
fun ActivityCenterScreen(controller: PoctraController) {
    val context = androidx.compose.ui.platform.LocalContext.current
    SettingsDetailScreen(
        title = "Activity Center",
        description = "All Octra, EVM, bridge, swap, and dApp connection events in one timeline.",
        glyph = Glyph.History,
        onBack = controller::closeActivityCenter,
        headerAction = {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
                if (controller.unifiedActivity.isNotEmpty()) {
                    HeaderTextAction(
                        label = "CSV",
                        onClick = { exportActivityCsv(context, controller.unifiedActivity) },
                    )
                }
                HeaderRefreshAction(
                    loading = controller.activityCenterRefreshLoading,
                    onClick = controller::refreshActivityCenter,
                )
            }
        },
    ) {
        if (controller.activityCenterRefreshLoading && controller.unifiedActivity.isEmpty()) {
            StateBanner("Syncing all activity sources...", isError = false)
        } else if (controller.unifiedActivity.isEmpty()) {
            EmptyPanel("No activity yet")
        } else {
            if (controller.activityCenterRefreshLoading) {
                StateBanner("Refreshing activity timeline...", isError = false, compact = true)
                Spacer(Modifier.height(Space.x5))
            }
            Column(verticalArrangement = Arrangement.spacedBy(Space.x5)) {
                controller.visibleUnifiedActivity.forEach { item ->
                    UnifiedActivityRow(
                        item = item,
                        onClick = item.explorerUrl?.let { { controller.openPortfolioActivityExplorer(item) } },
                    )
                }
                if (controller.activityCenterCanLoadMore) {
                    InlineListAction(
                        text = "Load More",
                        onClick = controller::loadMoreActivityCenter,
                        loading = controller.activityCenterRefreshLoading,
                        enabled = !controller.activityCenterRefreshLoading,
                    )
                }
            }
        }
    }
}

@Composable
private fun UnifiedActivityRow(item: UnifiedActivityItem, onClick: (() -> Unit)? = null) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(Modifier.padding(Space.x6)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssetGlyphBubble(
                    symbol = item.assetSymbol ?: item.source,
                    fallbackGlyph = activityGlyph(item.glyph),
                    source = item.source,
                )
                Spacer(Modifier.width(Space.x5))
                Column(Modifier.weight(1f)) {
                    Text(item.title, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(Space.x2))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                        SourceBadge(item.source)
                        StatusBadge(item.status)
                    }
                }
                Spacer(Modifier.width(Space.x4))
                Text(
                    item.amount,
                    color = TextTertiary,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(Space.x5))
            Text(item.subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(Space.x3))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatActivityTime(item.timestamp), color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                if (onClick != null) {
                    GlyphIcon(Glyph.External, modifier = Modifier.size(15.dp), color = TextSecondary)
                }
            }
        }
    }
}

@Composable
fun PaymentRequestScreen(controller: PoctraController) {
    val clipboard = LocalClipboardManager.current
    val draft = controller.paymentRequestDraft
    var amount by remember(draft?.address) { mutableStateOf(draft?.amount.orEmpty()) }
    var asset by remember(draft?.address) { mutableStateOf(draft?.asset ?: "OCT") }
    var memo by remember(draft?.address) { mutableStateOf(draft?.memo.orEmpty()) }

    LaunchedEffect(amount, asset, memo) {
        controller.updatePaymentRequest(amount, asset, memo)
    }

    SettingsDetailScreen(
        title = "Payment Request",
        description = "Create a QR request with amount and asset for quick receive flows.",
        glyph = Glyph.Receive,
        onBack = controller::closePaymentRequest,
    ) {
        val current = controller.paymentRequestDraft ?: draft
        if (current == null) {
            EmptyPanel("Wallet is not ready")
            return@SettingsDetailScreen
        }
        val uriTooLong = current.uri.encodeToByteArray().size > 106
        if (uriTooLong) {
            StateBanner(
                text = "Payment request URI is too long for QR. Shorten amount or asset name, or use Copy Request.",
                isError = true,
                compact = true,
            )
        } else {
            QrCodeView(text = current.uri)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        ) {
            Column(Modifier.padding(Space.x7)) {
                DetailLine("Receiver", current.label)
                DetailSeparator()
                DetailLine("Address", current.address)
                DetailSeparator()
                DetailLine("Request URI", current.uri)
            }
        }
        Spacer(Modifier.height(Space.x7))
        PoctraTextField(
            value = amount,
            onValueChange = { amount = it.filter { char -> char.isDigit() || char == '.' || char == ',' }.take(24) },
            label = "Amount",
            placeholder = "0.0",
            keyboardType = KeyboardType.Decimal,
        )
        Spacer(Modifier.height(Space.x5))
        PoctraTextField(
            value = asset,
            onValueChange = { asset = it.uppercase().take(12) },
            label = "Asset",
            placeholder = "OCT",
        )
        Spacer(Modifier.height(Space.x5))
        PoctraTextField(
            value = memo,
            onValueChange = { memo = it.take(48) },
            label = "Memo (optional)",
            singleLine = false,
            minLines = 2,
            supportingText = "Memo is shown here and copied with details, but kept out of QR for compatibility.",
        )
        Spacer(Modifier.height(Space.x7))
        PoctraButton(
            text = "Copy Request",
            onClick = {
                val copyText = buildString {
                    appendLine(current.uri)
                    if (memo.isNotBlank()) append("Memo: ").append(memo)
                }
                clipboard.setText(AnnotatedString(copyText))
                controller.showMessage("Payment request copied.")
            },
            modifier = Modifier.fillMaxWidth(),
            leadingGlyph = Glyph.Copy,
        )
    }
}

@Composable
fun PortfolioAnalyticsScreen(controller: PoctraController) {
    ConsumerPlainDetailScreen(
        title = "Portfolio",
        onBack = controller::closeSettingsDetail,
        headerAction = {
            HeaderRefreshAction(
                loading = controller.portfolioRefreshLoading,
                onClick = controller::refreshPortfolioAnalytics,
            )
        },
    ) {
        PortfolioPieCard(controller.portfolioHoldings)
        Spacer(Modifier.height(Space.x7))
        if (controller.portfolioHoldings.isEmpty()) {
            EmptyPanel("No portfolio data yet")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x5)) {
                controller.portfolioHoldings.forEach { holding ->
                    PortfolioHoldingRow(
                        holding = holding,
                        onClick = { controller.openPortfolioAssetHistory(holding) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PortfolioHoldingRow(holding: PortfolioHolding, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Row(
            modifier = Modifier.padding(Space.x6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(riskColor(holding.riskLevel).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                AssetTokenIcon(
                    symbol = holding.symbol,
                    source = holding.source,
                    modifier = Modifier.size(21.dp),
                )
            }
            Spacer(Modifier.width(Space.x5))
            Column(Modifier.weight(1f)) {
                Text(holding.title, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(holding.subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(holding.network, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Spacer(Modifier.width(Space.x4))
            Column(horizontalAlignment = Alignment.End) {
                Text(holding.balance, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(holding.fiatEstimate, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
            Spacer(Modifier.width(Space.x3))
            GlyphIcon(Glyph.Go, modifier = Modifier.size(16.dp), color = TextSecondary)
        }
    }
}

@Composable
fun PortfolioAssetHistoryScreen(controller: PoctraController) {
    val holding = controller.selectedPortfolioHolding
    ConsumerPlainDetailScreen(
        title = holding?.title?.let { "$it History" } ?: "Asset History",
        onBack = controller::closePortfolioAssetHistory,
    ) {
        if (holding == null) {
            EmptyPanel("Asset is no longer selected")
            return@ConsumerPlainDetailScreen
        }
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        ) {
            Row(
                modifier = Modifier.padding(Space.x6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssetGlyphBubble(symbol = holding.symbol, fallbackGlyph = Glyph.Assets, source = holding.source)
                Spacer(Modifier.width(Space.x5))
                Column(Modifier.weight(1f)) {
                    Text(holding.title, color = TextTertiary, fontWeight = FontWeight.SemiBold)
                    Text(holding.network, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(holding.balance, color = TextTertiary, fontWeight = FontWeight.SemiBold)
                    Text(holding.fiatEstimate, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
        Spacer(Modifier.height(Space.x7))
        if (controller.portfolioAssetActivity.isEmpty()) {
            EmptyPanel("No ${holding.symbol} activity found")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x5)) {
                controller.portfolioAssetActivity.forEach { item ->
                    UnifiedActivityRow(
                        item = item,
                        onClick = item.explorerUrl?.let { { controller.openPortfolioActivityExplorer(item) } },
                    )
                }
            }
        }
    }
}

@Composable
fun ApprovalDashboardScreen(controller: PoctraController) {
    var pendingRevoke by remember { mutableStateOf<ApprovalReviewItem?>(null) }

    SettingsDetailScreen(
        title = "Approvals",
        description = "Scan saved ERC20 assets for known bridge and swap spenders.",
        glyph = Glyph.Revoke,
        onBack = controller::closeSettingsDetail,
        headerAction = {
            HeaderRefreshAction(
                loading = controller.approvalReviewLoading,
                onClick = controller::refreshApprovalReviews,
            )
        },
    ) {
        StateBanner(
            text = "poctra checks known spenders only: bridge, Uniswap Permit2, and router.",
            isError = false,
            compact = true,
        )
        Spacer(Modifier.height(Space.x7))
        if (controller.approvalReviewLoading) {
            StateBanner("Scanning allowances...", isError = false)
            Spacer(Modifier.height(Space.x6))
        }
        if (!controller.approvalReviewLoading && controller.approvalReviews.isEmpty()) {
            EmptyPanel("No active approvals found", compact = true)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x5)) {
                controller.approvalReviews.forEach { item ->
                    ApprovalReviewRow(
                        item = item,
                        busy = controller.busy,
                        onRevoke = { pendingRevoke = item },
                    )
                }
            }
        }
    }

    pendingRevoke?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingRevoke = null },
            modifier = Modifier.fillMaxWidth(ConsumerDialogWidthFraction),
            properties = DialogProperties(usePlatformDefaultWidth = false),
            containerColor = SurfaceStrong,
            title = { Text("Revoke ${item.tokenSymbol} approval?", color = TextTertiary) },
            text = {
                Column {
                    Text(
                        "This sends an on-chain transaction that sets the spender allowance to zero.",
                        color = TextSecondary,
                    )
                    Spacer(Modifier.height(Space.x5))
                    DetailLine("Spender", item.spenderName)
                    DetailSeparator()
                    DetailLine("Address", item.spenderAddress)
                    DetailSeparator()
                    DetailLine("Current allowance", item.allowance)
                }
            },
            confirmButton = {
                PoctraButton(
                    text = "Revoke",
                    onClick = {
                        pendingRevoke = null
                        controller.revokeApproval(item)
                    },
                    variant = ButtonVariant.Danger,
                    compact = true,
                    leadingGlyph = Glyph.Revoke,
                    enabled = !controller.busy,
                )
            },
            dismissButton = {
                TextButton(onClick = { pendingRevoke = null }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
        )
    }
}

@Composable
private fun ApprovalReviewRow(item: ApprovalReviewItem, busy: Boolean, onRevoke: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(Modifier.padding(horizontal = Space.x5, vertical = Space.x4)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(riskColor(item.level).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.Revoke, modifier = Modifier.size(16.dp), color = riskColor(item.level))
                }
                Spacer(Modifier.width(Space.x4))
                Column(Modifier.weight(1f)) {
                    Text("${item.tokenSymbol} approval", color = TextTertiary, fontWeight = FontWeight.SemiBold)
                    Text(item.spenderName, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.error == null) {
                    CompactRevokeAction(
                        onClick = onRevoke,
                        enabled = !busy,
                    )
                }
            }
            Spacer(Modifier.height(Space.x3))
            CompactDetailLine("Allowance", item.allowance)
            CompactDetailSeparator()
            CompactDetailLine("Network", item.networkName)
            CompactDetailSeparator()
            CompactDetailLine("Spender", item.spenderAddress)
            item.error?.let {
                CompactDetailSeparator()
                Text(it, color = Warning, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun CompactRevokeAction(
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(ErrorRed.copy(alpha = if (enabled) 0.12f else 0.06f))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.x3, vertical = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.Revoke, modifier = Modifier.size(13.dp), color = ErrorRed.copy(alpha = if (enabled) 1f else 0.45f))
        Spacer(Modifier.width(Space.x2))
        Text(
            "Revoke",
            color = ErrorRed.copy(alpha = if (enabled) 1f else 0.45f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun CompactDetailLine(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.x2),
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(Space.x1))
        Text(
            value.ifBlank { "-" },
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CompactDetailSeparator() {
    Spacer(Modifier.height(Space.x2))
}

@Composable
fun DomainGuardScreen(controller: PoctraController) {
    var trustedDomain by remember { mutableStateOf("") }
    var blockedDomain by remember { mutableStateOf("") }

    SettingsDetailScreen(
        title = "Phishing Guard",
        description = "Trust or block dApp domains before they open inside poctra.",
        glyph = Glyph.Browser,
        onBack = controller::closeSettingsDetail,
    ) {
        PoctraTextField(
            value = trustedDomain,
            onValueChange = { trustedDomain = it.take(80) },
            label = "Trusted domain",
            placeholder = "octra.id",
        )
        Spacer(Modifier.height(Space.x4))
        PoctraButton(
            text = "Trust Domain",
            onClick = {
                controller.addDomainGuardRule(trustedDomain, trusted = true)
                trustedDomain = ""
            },
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Ghost,
            leadingGlyph = Glyph.Check,
            compact = true,
        )
        Spacer(Modifier.height(Space.x7))
        PoctraTextField(
            value = blockedDomain,
            onValueChange = { blockedDomain = it.take(80) },
            label = "Blocked domain",
            placeholder = "fake-domain.example",
        )
        Spacer(Modifier.height(Space.x4))
        PoctraButton(
            text = "Block Domain",
            onClick = {
                controller.addDomainGuardRule(blockedDomain, trusted = false)
                blockedDomain = ""
            },
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Danger,
            leadingGlyph = Glyph.Revoke,
            compact = true,
        )
        Spacer(Modifier.height(Space.x8))
        if (controller.domainGuardRules.isEmpty()) {
            EmptyPanel("No domain rules")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.x5)) {
                controller.domainGuardRules.forEach { rule ->
                    DomainGuardRuleRow(rule = rule, onRemove = { controller.removeDomainGuardRule(rule.host) })
                }
            }
        }
    }
}

@Composable
private fun DomainGuardRuleRow(rule: DomainGuardRule, onRemove: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Row(
            modifier = Modifier.padding(Space.x6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background((if (rule.trusted) SurfaceRaised else ErrorRed).copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(if (rule.trusted) Glyph.Check else Glyph.Revoke, modifier = Modifier.size(17.dp), color = if (rule.trusted) SurfaceRaised else ErrorRed)
            }
            Spacer(Modifier.width(Space.x5))
            Column(Modifier.weight(1f)) {
                Text(rule.host, color = TextTertiary, fontWeight = FontWeight.SemiBold)
                Text(if (rule.trusted) "Trusted" else "Blocked", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(Glyph.Trash, modifier = Modifier.size(17.dp), color = ErrorRed)
            }
        }
    }
}

@Composable
fun RecoveryPlanScreen(controller: PoctraController) {
    val wallet = controller.wallet
    val items = listOf(
        ConsumerRiskItem(
            "Security method",
            controller.currentSecurityMethod?.let { "${it.label} protects unlock and sensitive backup access." } ?: "Set biometric or PIN security.",
            if (controller.currentSecurityMethod == null) ConsumerRiskLevel.Critical else ConsumerRiskLevel.Safe,
        ),
        ConsumerRiskItem(
            "Backup material",
            wallet?.type?.let { "This ${walletTypeLabel(it)} wallet can be revealed from Backup Keys after re-auth." } ?: "Unlock wallet to inspect backup status.",
            if (wallet == null) ConsumerRiskLevel.Warning else ConsumerRiskLevel.Notice,
        ),
        ConsumerRiskItem(
            "Device backup",
            "Android app backup is disabled; keep your seed/key outside this device.",
            ConsumerRiskLevel.Notice,
        ),
    )

    SettingsDetailScreen(
        title = "Recovery Plan",
        description = "A compact checklist for keeping access to this wallet.",
        glyph = Glyph.Download,
        onBack = controller::closeSettingsDetail,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x5)) {
            items.forEach { ConsumerRiskRow(it) }
        }
        Spacer(Modifier.height(Space.x7))
        PoctraButton(
            text = "Open Backup Keys",
            onClick = controller::openBackupSettings,
            modifier = Modifier.fillMaxWidth(),
            leadingGlyph = Glyph.Eye,
        )
    }
}

@Composable
fun FundingProvidersScreen(controller: PoctraController) {
    SettingsDetailScreen(
        title = "Funding Providers",
        description = "Configure HTTPS on-ramp or off-ramp links that open in the dApp browser.",
        glyph = Glyph.External,
        onBack = controller::closeSettingsDetail,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.x6)) {
            controller.fundingProviders.forEach { provider ->
                FundingProviderCard(provider = provider, controller = controller)
            }
        }
    }
}

@Composable
private fun FundingProviderCard(provider: FundingProvider, controller: PoctraController) {
    var url by remember(provider.id, provider.url) { mutableStateOf(provider.url) }
    var enabled by remember(provider.id, provider.enabled) { mutableStateOf(provider.enabled) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(Modifier.padding(Space.x6)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlyphIcon(Glyph.External, modifier = Modifier.size(18.dp), color = TextTertiary)
                Spacer(Modifier.width(Space.x4))
                Text(provider.name, color = TextTertiary, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Text(
                    if (enabled) "On" else "Off",
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.sm))
                        .clickable { enabled = !enabled }
                        .background(if (enabled) SurfaceRaised else SurfaceMuted)
                        .padding(horizontal = Space.x5, vertical = Space.x3),
                    color = if (enabled) PrimaryContent else TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Spacer(Modifier.height(Space.x3))
            Text(provider.description, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
            Spacer(Modifier.height(Space.x5))
            PoctraTextField(
                value = url,
                onValueChange = { url = it.take(180) },
                label = "Provider URL",
                placeholder = "https://provider.example",
            )
            Spacer(Modifier.height(Space.x5))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
                PoctraButton(
                    text = "Save",
                    onClick = { controller.saveFundingProvider(provider.id, url, enabled) },
                    modifier = Modifier.weight(1f),
                    compact = true,
                    leadingGlyph = Glyph.Check,
                )
                PoctraButton(
                    text = "Open",
                    onClick = { controller.openFundingProvider(provider.copy(url = url, enabled = enabled)) },
                    modifier = Modifier.weight(1f),
                    variant = ButtonVariant.Ghost,
                    compact = true,
                    leadingGlyph = Glyph.Go,
                )
            }
        }
    }
}

@Composable
fun OctraProfileScreen(controller: PoctraController) {
    val wallet = controller.wallet
    val identity = wallet?.address?.let { controller.walletIdentityNames[it] }
    val evm = controller.evmAccount
    val evmAddress = controller.currentEvmAddress()
    val clipboard = LocalClipboardManager.current
    ConsumerPlainDetailScreen(
        title = "Octra Profile",
        onBack = controller::closeSettingsDetail,
    ) {
        if (wallet == null) {
            EmptyPanel("Wallet locked")
            return@ConsumerPlainDetailScreen
        }
        val walletName = wallet.name.ifBlank { "poctra wallet" }
        val displayName = identity ?: walletName
        val octraNetwork = controller.activeRpcNetwork?.networkType
            ?.replaceFirstChar { it.uppercase() }
            ?: "Octra"

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Space.x8),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                WalletIdenticonView(
                    svg = controller.walletIdenticonSvg(wallet.address),
                    size = 104.dp,
                )
                Spacer(Modifier.height(Space.x6))
                Text(
                    displayName,
                    color = TextTertiary,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (identity != null && walletName != identity) {
                    Spacer(Modifier.height(Space.x2))
                    Text(walletName, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                Spacer(Modifier.height(Space.x5))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                    ProfileBadge(if (identity != null) "Octra Identity" else "Local Identity", SurfaceRaised)
                    ProfileBadge(octraNetwork, InfoBlue)
                }
            }
        }
        Spacer(Modifier.height(Space.x5))
        ProfileAddressCard(
            title = "Octra Account",
            identity = identity,
            address = wallet.address,
            glyph = Glyph.Wallet,
            accent = SurfaceRaised,
            onCopy = {
                clipboard.setText(AnnotatedString(wallet.address))
                controller.showMessage("Octra address copied.")
            },
        )
        Spacer(Modifier.height(Space.x5))
        ProfileAddressCard(
            title = "EVM Account",
            identity = evm?.ensName,
            address = evmAddress.orEmpty(),
            emptyText = "EVM address unavailable",
            glyph = Glyph.Evm,
            accent = InfoBlue,
            onCopy = evmAddress?.takeIf { it.isNotBlank() }?.let { address ->
                {
                    clipboard.setText(AnnotatedString(address))
                    controller.showMessage("EVM address copied.")
                }
            },
        )
        Spacer(Modifier.height(Space.x5))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        ) {
            Column(Modifier.padding(Space.x6)) {
                Text("Wallet Details", color = TextTertiary, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(Space.x5))
                Row(horizontalArrangement = Arrangement.spacedBy(Space.x4)) {
                    ProfileFact("Wallet Type", walletTypeLabel(wallet.type), Modifier.weight(1f))
                    ProfileFact("EVM Network", controller.activeEvmNetwork?.name ?: "Not Synced", Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(Space.x6))
        PoctraButton(
            text = "Open Octra Identity",
            onClick = { controller.openDAppBrowser("Octra Identity", controller.octraIdentityDAppUrl()) },
            modifier = Modifier.fillMaxWidth(),
            leadingGlyph = Glyph.Browser,
            compact = true,
        )
    }
}

@Composable
private fun ProfileBadge(text: String, accent: Color) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(accent.copy(alpha = 0.14f))
            .padding(horizontal = Space.x4, vertical = Space.x2),
        color = accent,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun ProfileAddressCard(
    title: String,
    identity: String?,
    address: String,
    emptyText: String = "Address unavailable",
    glyph: Glyph,
    accent: Color,
    onCopy: (() -> Unit)?,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(Modifier.padding(Space.x6)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(accent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(glyph, modifier = Modifier.size(19.dp), color = accent)
                }
                Spacer(Modifier.width(Space.x5))
                Column(Modifier.weight(1f)) {
                    Text(title, color = TextTertiary, fontWeight = FontWeight.SemiBold)
                    Text(
                        identity?.takeIf { it.isNotBlank() } ?: "No identity linked",
                        color = if (identity.isNullOrBlank()) TextSecondary else accent,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(Space.x5))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(SurfaceBase)
                    .padding(horizontal = Space.x5, vertical = Space.x4),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    address.ifBlank { emptyText },
                    modifier = Modifier.weight(1f),
                    color = if (address.isBlank()) TextSecondary else TextTertiary,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (onCopy != null) {
                    Spacer(Modifier.width(Space.x4))
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(Radius.sm))
                            .background(accent.copy(alpha = 0.14f))
                            .clickable(onClick = onCopy),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(Glyph.Copy, modifier = Modifier.size(14.dp), color = accent)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileFact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(SurfaceBase)
            .padding(Space.x5),
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(Space.x2))
        Text(
            value,
            color = TextTertiary,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

fun securityCenterSubtitle(controller: PoctraController): String {
    val highest = controller.securityRisks.maxByOrNull { it.level.ordinal }?.level ?: ConsumerRiskLevel.Safe
    return when (highest) {
        ConsumerRiskLevel.Safe -> "Protection looks good"
        ConsumerRiskLevel.Notice -> "Review recommended items"
        ConsumerRiskLevel.Warning -> "Warnings need attention"
        ConsumerRiskLevel.Critical -> "Critical action required"
    }
}

private fun riskColor(level: ConsumerRiskLevel): Color = when (level) {
    ConsumerRiskLevel.Safe -> SurfaceRaised
    ConsumerRiskLevel.Notice -> TextSecondary
    ConsumerRiskLevel.Warning -> Warning
    ConsumerRiskLevel.Critical -> ErrorRed
}

private fun activityGlyph(value: String): Glyph = when (value) {
    "send" -> Glyph.Send
    "receive" -> Glyph.Receive
    "bridge" -> Glyph.Bridge
    "dapp" -> Glyph.Browser
    else -> Glyph.History
}

private fun statusTextColor(status: String): Color = when (status.lowercase()) {
    "failed", "rejected", "dropped", "error" -> ErrorRed
    "pending", "running", "claimable", "accepted" -> Warning
    "confirmed", "completed", "connected", "success" -> SurfaceRaised
    else -> TextSecondary
}

private fun formatActivityTime(timestamp: Long): String {
    if (timestamp <= 0L) return "-"
    return android.text.format.DateFormat.format("MMM d, HH:mm", timestamp).toString()
}

private fun exportActivityCsv(context: Context, items: List<UnifiedActivityItem>) {
    val csv = buildString {
        appendLine("Date,Title,Subtitle,Amount,Status,Source,TxHash")
        items.forEach { item ->
            val date = if (item.timestamp > 0L)
                java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                    .format(java.util.Date(item.timestamp))
            else "-"
            appendLine("\"$date\",\"${item.title}\",\"${item.subtitle}\",\"${item.amount}\",\"${item.status}\",\"${item.source}\",\"${item.explorerUrl ?: ""}\"")
        }
    }
    val filename = "poctra_activity_${System.currentTimeMillis()}.csv"
    val file = java.io.File(context.getExternalFilesDir(null), filename)
    file.writeText(csv)
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        file,
    )
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(android.content.Intent.EXTRA_STREAM, uri)
        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(android.content.Intent.createChooser(intent, "Export Activity CSV"))
}
