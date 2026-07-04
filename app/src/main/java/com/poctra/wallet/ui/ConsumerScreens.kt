package com.poctra.wallet.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.window.DialogProperties
import com.poctra.wallet.R
import com.poctra.wallet.ApprovalReviewItem
import com.poctra.wallet.ConsumerRiskItem
import com.poctra.wallet.ConsumerRiskLevel
import com.poctra.wallet.DomainGuardRule
import com.poctra.wallet.FundingProvider
import com.poctra.wallet.PoctraController
import com.poctra.wallet.PortfolioHolding
import com.poctra.wallet.SimulationCheck
import com.poctra.wallet.UnifiedActivityItem

private const val ConsumerDialogWidthFraction = 0.96f
@Composable
fun SecurityCenterScreen(controller: PoctraController) {
    SettingsDetailScreen(
        title = "Security Center",
        description = "Review wallet protection, dApp permissions, network health, and recovery readiness.",
        glyph = Glyph.Fingerprint,
        onBack = controller::closeSettingsDetail,
    ) {
        SecurityScoreCard(controller.securityRisks)
        Spacer(Modifier.height(Space.x7))
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
private fun SecurityScoreCard(risks: List<ConsumerRiskItem>) {
    val highest = risks.maxByOrNull { it.level.ordinal }?.level ?: ConsumerRiskLevel.Safe
    val score = when (highest) {
        ConsumerRiskLevel.Safe -> "Good"
        ConsumerRiskLevel.Notice -> "Review"
        ConsumerRiskLevel.Warning -> "Caution"
        ConsumerRiskLevel.Critical -> "Action"
    }
    val detail = when (highest) {
        ConsumerRiskLevel.Safe -> "No major issues found."
        ConsumerRiskLevel.Notice -> "A few items deserve attention."
        ConsumerRiskLevel.Warning -> "Review warnings before high-value actions."
        ConsumerRiskLevel.Critical -> "Fix critical protection items first."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        border = BorderStroke(1.dp, riskColor(highest).copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier.padding(Space.x7),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(Radius.md))
                    .background(riskColor(highest).copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(if (highest.ordinal >= ConsumerRiskLevel.Warning.ordinal) Glyph.Info else Glyph.Check, modifier = Modifier.size(24.dp), color = riskColor(highest))
            }
            Spacer(Modifier.width(Space.x6))
            Column(Modifier.weight(1f)) {
                Text(score, color = TextTertiary, style = MaterialTheme.typography.titleLarge)
                Text(detail, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun ConsumerRiskRow(risk: ConsumerRiskItem) {
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                    .size(36.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(riskColor(risk.level).copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(if (risk.level == ConsumerRiskLevel.Safe) Glyph.Check else Glyph.Info, modifier = Modifier.size(18.dp), color = riskColor(risk.level))
            }
            Spacer(Modifier.width(Space.x5))
            Column(Modifier.weight(1f)) {
                Text(risk.title, color = TextTertiary, fontWeight = FontWeight.SemiBold)
                Text(risk.description, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun SecurityCenterActionGrid(controller: PoctraController) {
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
        MiniActionCard(
            title = "Backup",
            subtitle = "Keys",
            glyph = Glyph.Eye,
            modifier = Modifier.weight(1f),
            onClick = controller::openBackupSettings,
        )
        MiniActionCard(
            title = "dApps",
            subtitle = "${controller.dApps.size}",
            glyph = Glyph.Explore,
            modifier = Modifier.weight(1f),
            onClick = controller::openConnectedDApps,
        )
    }
    Spacer(Modifier.height(Space.x5))
    Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
        MiniActionCard(
            title = "Contacts",
            subtitle = "${controller.addressBookContacts.size}",
            glyph = Glyph.AddressBook,
            modifier = Modifier.weight(1f),
            onClick = controller::openAddressBook,
        )
        MiniActionCard(
            title = "Network",
            subtitle = controller.activeRpcNetwork?.networkType ?: "RPC",
            glyph = Glyph.Wifi,
            modifier = Modifier.weight(1f),
            onClick = controller::openRpcManagement,
        )
    }
}

@Composable
private fun MiniActionCard(
    title: String,
    subtitle: String,
    glyph: Glyph,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Card(
        modifier = modifier
            .height(76.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        border = BorderStroke(1.dp, BorderLine),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(Space.x5),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(SurfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                GlyphIcon(glyph, modifier = Modifier.size(17.dp), color = TextTertiary)
            }
            Spacer(Modifier.width(Space.x4))
            Column(Modifier.weight(1f)) {
                Text(title, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun ConsumerPlainDetailScreen(
    title: String,
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
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconCircleButton(Glyph.Back, "Back", onBack)
            Spacer(Modifier.width(Space.x6))
            Text(title, color = TextTertiary, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(Space.x8))
        content()
    }
}

@Composable
private fun PortfolioPieCard(holdings: List<PortfolioHolding>) {
    val slices = portfolioSlices(holdings)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        border = BorderStroke(1.dp, BorderLine),
    ) {
        Column(Modifier.padding(Space.x7), horizontalAlignment = Alignment.CenterHorizontally) {
            if (slices.isEmpty()) {
                Text("No portfolio data yet", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            } else {
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
    val rawValues = holdings.map { holding ->
        when {
            holding.valueUsd > 0.0 -> holding.valueUsd
            holding.balance.replace(",", ".").toDoubleOrNull()?.let { it > 0.0 } == true -> 1.0
            else -> 0.0
        }
    }
    val values = if (rawValues.sum() > 0.0) rawValues else holdings.map { 1.0 }
    val total = values.sum().coerceAtLeast(1.0)
    return holdings.mapIndexed { index, holding ->
        val fraction = values[index] / total
        PortfolioSlice(
            holding = holding,
            color = palette[index % palette.size],
            sweep = (fraction * 360.0).toFloat().coerceAtLeast(2f),
            percent = (fraction * 100.0).toInt().coerceAtLeast(if (fraction > 0.0) 1 else 0),
        )
    }
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
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = Space.x4, vertical = Space.x2),
        color = color,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun sourceColor(source: String): Color = when {
    source.contains("octra", ignoreCase = true) -> SurfaceRaised
    source.contains("ethereum", ignoreCase = true) || source.contains("evm", ignoreCase = true) -> Color(0xFF60A5FA)
    source.contains("bridge", ignoreCase = true) -> Warning
    else -> TextSecondary
}

@Composable
fun ActivityCenterScreen(controller: PoctraController) {
    SettingsDetailScreen(
        title = "Activity Center",
        description = "All Octra, EVM, bridge, swap, and dApp connection events in one timeline.",
        glyph = Glyph.History,
        onBack = controller::closeActivityCenter,
    ) {
        PoctraButton(
            text = if (controller.activityCenterRefreshLoading) "Syncing Activity" else "Refresh Activity",
            onClick = controller::refreshActivityCenter,
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Ghost,
            leadingGlyph = Glyph.Refresh,
            compact = true,
            loading = controller.activityCenterRefreshLoading,
            enabled = !controller.activityCenterRefreshLoading,
        )
        Spacer(Modifier.height(Space.x7))
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
                    PoctraButton(
                        text = "Load More",
                        onClick = controller::loadMoreActivityCenter,
                        modifier = Modifier.fillMaxWidth(),
                        variant = ButtonVariant.Ghost,
                        leadingGlyph = Glyph.Refresh,
                        compact = true,
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
        border = BorderStroke(1.dp, BorderLine),
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
        QrCodeView(text = current.uri)
        Spacer(Modifier.height(Space.x7))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            border = BorderStroke(1.dp, BorderLine),
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
    ) {
        PortfolioPieCard(controller.portfolioHoldings)
        Spacer(Modifier.height(Space.x7))
        PoctraButton(
            text = if (controller.portfolioRefreshLoading) "Syncing Portfolio" else "Refresh Portfolio",
            onClick = controller::refreshPortfolioAnalytics,
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Ghost,
            leadingGlyph = Glyph.Refresh,
            compact = true,
            loading = controller.portfolioRefreshLoading,
            enabled = !controller.portfolioRefreshLoading,
        )
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
            border = BorderStroke(1.dp, BorderLine),
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
    ) {
        StateBanner(
            text = "poctra checks known spenders only: bridge, Uniswap Permit2, and router.",
            isError = false,
            compact = true,
        )
        Spacer(Modifier.height(Space.x6))
        PoctraButton(
            text = "Refresh Approvals",
            onClick = controller::refreshApprovalReviews,
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Ghost,
            leadingGlyph = Glyph.Refresh,
            compact = true,
            enabled = !controller.approvalReviewLoading,
        )
        Spacer(Modifier.height(Space.x7))
        if (controller.approvalReviewLoading) {
            StateBanner("Scanning allowances...", isError = false)
            Spacer(Modifier.height(Space.x6))
        }
        if (!controller.approvalReviewLoading && controller.approvalReviews.isEmpty()) {
            EmptyPanel("No active approvals found")
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
        border = BorderStroke(1.dp, riskColor(item.level).copy(alpha = 0.45f)),
    ) {
        Column(Modifier.padding(Space.x6)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(Radius.sm))
                        .background(riskColor(item.level).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.Revoke, modifier = Modifier.size(18.dp), color = riskColor(item.level))
                }
                Spacer(Modifier.width(Space.x5))
                Column(Modifier.weight(1f)) {
                    Text("${item.tokenSymbol} approval", color = TextTertiary, fontWeight = FontWeight.SemiBold)
                    Text(item.spenderName, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (item.error == null) {
                    PoctraButton(
                        text = "Revoke",
                        onClick = onRevoke,
                        compact = true,
                        variant = ButtonVariant.Danger,
                        leadingGlyph = Glyph.Revoke,
                        enabled = !busy,
                    )
                }
            }
            Spacer(Modifier.height(Space.x5))
            DetailLine("Allowance", item.allowance)
            DetailSeparator()
            DetailLine("Network", item.networkName)
            DetailSeparator()
            DetailLine("Spender", item.spenderAddress)
            item.error?.let {
                DetailSeparator()
                Text(it, color = Warning, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
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
        border = BorderStroke(1.dp, if (rule.trusted) SurfaceRaised.copy(alpha = 0.45f) else ErrorRed.copy(alpha = 0.45f)),
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
                Text(if (rule.trusted) "trusted" else "blocked", color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            IconCircleButton(Glyph.Trash, "Remove", onRemove)
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
fun NotificationSettingsScreen(controller: PoctraController) {
    val prefs = controller.notificationPreferences
    SettingsDetailScreen(
        title = "Notifications",
        description = "Choose which local alerts poctra should surface in-app.",
        glyph = Glyph.Info,
        onBack = controller::closeSettingsDetail,
    ) {
        NotificationPreferenceRow("Security alerts", "Risk and dApp permission reminders", prefs.securityAlerts) {
            controller.setNotificationPreference("security", it)
        }
        Spacer(Modifier.height(Space.x5))
        NotificationPreferenceRow("Transaction alerts", "Accepted, confirmed, rejected, and speed-up states", prefs.transactionAlerts) {
            controller.setNotificationPreference("transactions", it)
        }
        Spacer(Modifier.height(Space.x5))
        NotificationPreferenceRow("Bridge alerts", "Bridge recovery and claim reminders", prefs.bridgeAlerts) {
            controller.setNotificationPreference("bridge", it)
        }
        Spacer(Modifier.height(Space.x5))
        NotificationPreferenceRow("Price alerts", "Optional price movement notices", prefs.priceAlerts) {
            controller.setNotificationPreference("price", it)
        }
        Spacer(Modifier.height(Space.x7))
        StateBanner("Preferences are stored locally. System notifications stay off until a notification service is added.", isError = false, compact = true)
    }
}

@Composable
private fun NotificationPreferenceRow(title: String, subtitle: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        border = BorderStroke(1.dp, if (enabled) SurfaceRaised.copy(alpha = 0.45f) else BorderLine),
    ) {
        Row(
            modifier = Modifier.padding(Space.x6),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = TextTertiary, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                if (enabled) "On" else "Off",
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.sm))
                    .clickable { onToggle(!enabled) }
                    .background(if (enabled) SurfaceRaised else SurfaceMuted)
                    .padding(horizontal = Space.x5, vertical = Space.x3),
                color = if (enabled) PrimaryContent else TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
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
        border = BorderStroke(1.dp, BorderLine),
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
            Text(provider.description, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
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
    ConsumerPlainDetailScreen(
        title = "Octra Profile",
        onBack = controller::closeSettingsDetail,
    ) {
        if (wallet == null) {
            EmptyPanel("Wallet locked")
            return@ConsumerPlainDetailScreen
        }
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            QrCodeView(text = wallet.address)
        }
        Spacer(Modifier.height(Space.x7))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            border = BorderStroke(1.dp, BorderLine),
        ) {
            Column(Modifier.padding(Space.x7)) {
                DetailLine("Display name", identity ?: wallet.name.ifBlank { "poctra wallet" })
                DetailSeparator()
                DetailLine("Octra address", wallet.address)
                DetailSeparator()
                DetailLine("Wallet type", walletTypeLabel(wallet.type))
                DetailSeparator()
                DetailLine("EVM address", evm?.address ?: "Open EVM Account to sync")
                DetailSeparator()
                DetailLine("EVM identity", evm?.ensName ?: "No ENS detected")
            }
        }
        Spacer(Modifier.height(Space.x7))
        PoctraButton(
            text = "Open Octra Identity",
            onClick = { controller.openDAppBrowser("Octra Identity", "https://octra.id") },
            modifier = Modifier.fillMaxWidth(),
            leadingGlyph = Glyph.Browser,
        )
    }
}

@Composable
fun SimulationCenterScreen(controller: PoctraController) {
    SettingsDetailScreen(
        title = "Transaction Simulation",
        description = "Preflight checks used before send, bridge, swap, and dApp approvals.",
        glyph = Glyph.Check,
        onBack = controller::closeSettingsDetail,
    ) {
        PoctraButton(
            text = if (controller.simulationRefreshLoading) "Syncing Checks" else "Refresh Checks",
            onClick = controller::refreshSimulationCenterChecks,
            modifier = Modifier.fillMaxWidth(),
            variant = ButtonVariant.Ghost,
            leadingGlyph = Glyph.Refresh,
            compact = true,
            loading = controller.simulationRefreshLoading,
            enabled = !controller.simulationRefreshLoading,
        )
        Spacer(Modifier.height(Space.x7))
        Column(verticalArrangement = Arrangement.spacedBy(Space.x5)) {
            controller.simulationChecks.forEach { check ->
                SimulationCheckRow(check)
            }
        }
        Spacer(Modifier.height(Space.x7))
        StateBanner("Local checks are active now. Full state-diff simulation requires RPC support.", isError = false, compact = true)
    }
}

@Composable
private fun SimulationCheckRow(check: SimulationCheck) {
    ConsumerRiskRow(
        ConsumerRiskItem(
            title = check.title,
            description = check.detail,
            level = check.level,
        ),
    )
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
