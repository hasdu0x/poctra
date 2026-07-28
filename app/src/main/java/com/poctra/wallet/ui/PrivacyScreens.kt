package com.poctra.wallet.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.poctra.wallet.PoctraController
import com.poctra.wallet.data.TransactionHistoryItem
import com.poctra.wallet.privacy.PrivacyClaimableTransfer
import com.poctra.wallet.privacy.PrivacyOperationStage
import com.poctra.wallet.privacy.PrivacyOperationState
import com.poctra.wallet.privacy.PrivacyOperationType
import com.poctra.wallet.privacy.PrivacyKeyUpgradeMode
import com.poctra.wallet.privacy.PrivacyUpgradePolicy
import com.poctra.wallet.privacy.PrivacySyncStatus
import com.poctra.wallet.privacy.PrivacySyncStep
import com.poctra.wallet.privacy.formatPrivacyAmount
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun PrivacyContent(controller: PoctraController) {
    if (controller.privacyInitializing) {
        PrivacyInitializationContent(controller)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(Space.x7)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Privacy", color = TextTertiary, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(Space.x2))
                Text(
                    privacyNetworkLabel(controller),
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                PrivacyHeaderAction(
                    label = "Settings",
                    glyph = Glyph.Settings,
                    onClick = controller::openPrivacySettings,
                )
                PrivacyHeaderAction(
                    label = "Refresh",
                    glyph = Glyph.Refresh,
                    loading = controller.privacySyncing,
                    onClick = { controller.refreshPrivacy(force = true) },
                )
            }
        }

        PrivacyBalanceCard(controller)

        controller.privacyKeyStatus
            .takeIf { it.requiresAction }
            ?.let { status ->
                PrivacyKeyAttentionCard(
                    status = status,
                    loading = controller.privacyKeyStatusLoading,
                    onClick = controller::openPrivacySettings,
                )
            }

        controller.privacyOperation?.takeIf { !it.dialogVisible }?.let { operation ->
            PrivacyResumeCard(operation, controller::resumePrivacyOperation)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
            PrivacyActionCard(
                title = "Encrypt",
                subtitle = "Move OCT into private balance",
                glyph = Glyph.Privacy,
                onClick = controller::openPrivacyEncrypt,
                modifier = Modifier.weight(1f),
            )
            PrivacyActionCard(
                title = "Decrypt",
                subtitle = "Move private OCT to public balance",
                glyph = Glyph.Eye,
                onClick = controller::openPrivacyDecrypt,
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
            PrivacyActionCard(
                title = "Stealth",
                subtitle = "Send OCT from private balance",
                glyph = Glyph.EyeOff,
                onClick = controller::openPrivacyStealth,
                modifier = Modifier.weight(1f),
            )
            PrivacyActionCard(
                title = "Claim",
                subtitle = "Find and claim private transfers",
                glyph = Glyph.Download,
                onClick = controller::openPrivacyClaim,
                modifier = Modifier.weight(1f),
            )
        }

        Column {
            Text("Recent Activity", color = TextTertiary, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Space.x5))
            when {
                controller.privacySyncing && controller.privacyHistory.isEmpty() -> {
                    PrivacyEmptyState("Syncing private activity...")
                }
                controller.privacyHistory.isEmpty() -> PrivacyEmptyState("No privacy activity yet")
                else -> controller.privacyHistory.forEach { transaction ->
                    PrivacyHistoryRow(
                        transaction = transaction,
                        onClick = { controller.openTransactionDetails(transaction) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PrivacyKeyAttentionCard(
    status: com.poctra.wallet.privacy.PrivacyKeyUpgradeStatus,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val blocked = status.mode in setOf(
        PrivacyKeyUpgradeMode.Blocked,
        PrivacyKeyUpgradeMode.KeyMismatch,
        PrivacyKeyUpgradeMode.FeeBlocked,
    )
    val tone = if (blocked) ErrorRed else Warning
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(tone.copy(alpha = 0.09f))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = Space.x5, vertical = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = tone,
                strokeWidth = 2.dp,
            )
        } else {
            GlyphIcon(Glyph.Key, Modifier.size(18.dp), tone)
        }
        Spacer(Modifier.width(Space.x4))
        Column(Modifier.weight(1f)) {
            Text(
                privacyUpgradeModeLabel(status.mode),
                color = tone,
                style = MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.height(Space.x1))
            Text(
                status.reason,
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.x3))
        GlyphIcon(Glyph.Go, Modifier.size(16.dp), TextSecondary)
    }
}

@Composable
fun PrivacyOverlay(controller: PoctraController) {
    controller.privacyOperation?.takeIf { it.dialogVisible && it.stage != PrivacyOperationStage.Review }?.let { operation ->
        PrivacyOperationSheet(controller, operation)
    }
}

@Composable
private fun PrivacyHeaderAction(
    label: String,
    glyph: Glyph,
    loading: Boolean = false,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(50.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceMuted.copy(alpha = 0.78f))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(vertical = Space.x2),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(15.dp), color = PrivacyPrimary, strokeWidth = 2.dp)
        } else {
            GlyphIcon(glyph, modifier = Modifier.size(16.dp), color = PrivacyPrimary)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = TextSecondary,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            maxLines = 1,
        )
    }
}

@Composable
private fun PrivacyInitializationContent(controller: PoctraController) {
    val visibleSteps = controller.privacySyncSteps.filter { it.status != PrivacySyncStatus.Waiting }
    val activeStep = visibleSteps.lastOrNull()
    val activeIndex = activeStep?.let(controller.privacySyncSteps::indexOf)?.coerceAtLeast(0) ?: 0
    val activeGlyph = when (activeIndex) {
        1 -> Glyph.Eye
        2 -> Glyph.History
        else -> Glyph.Privacy
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 480.dp)
            .padding(horizontal = Space.x6),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(82.dp)
                .background(PrivacyPrimary.copy(alpha = 0.13f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(activeGlyph, Modifier.size(34.dp), PrivacyPrimary)
        }
        Spacer(Modifier.height(Space.x7))
        Text("Privacy", color = TextTertiary, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(Space.x2))
        Text(privacyNetworkLabel(controller), color = PrivacyPrimary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.height(Space.x8))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(Space.x4),
        ) {
            visibleSteps.forEach { step ->
                AnimatedVisibility(visible = true, enter = fadeIn() + slideInVertically { it / 3 }) {
                    PrivacyInitializationStep(step)
                }
            }
        }
    }
}

@Composable
private fun PrivacyInitializationStep(step: PrivacySyncStep) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Space.x2, vertical = Space.x3),
        verticalAlignment = Alignment.Top,
    ) {
        when (step.status) {
            PrivacySyncStatus.Running -> CircularProgressIndicator(Modifier.size(16.dp), color = PrivacyPrimary, strokeWidth = 2.dp)
            PrivacySyncStatus.Complete -> GlyphIcon(Glyph.Check, Modifier.size(16.dp), PrivacyPrimary)
            PrivacySyncStatus.Failed -> GlyphIcon(Glyph.Info, Modifier.size(16.dp), ErrorRed)
            PrivacySyncStatus.Waiting -> Unit
        }
        Spacer(Modifier.width(Space.x4))
        Column(Modifier.weight(1f)) {
            Text(step.label, color = if (step.status == PrivacySyncStatus.Failed) ErrorRed else TextTertiary, style = MaterialTheme.typography.bodySmall)
            step.detail?.let { Text(it, color = TextSecondary, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
private fun PrivacyBalanceCard(controller: PoctraController) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(Modifier.padding(Space.x7)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(PrivacyPrimary.copy(alpha = 0.12f), RoundedCornerShape(Radius.md)),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(Glyph.Privacy, Modifier.size(20.dp), PrivacyPrimary)
                }
                Spacer(Modifier.width(Space.x5))
                Column(Modifier.weight(1f)) {
                    Text("Encrypted balance", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.height(Space.x2))
                    Text(
                        if (controller.privacyBalanceVisible) "${controller.privacyBalanceText} OCT" else "\u2022\u2022\u2022\u2022\u2022\u2022 OCT",
                        color = TextTertiary,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(Space.x1))
                    Text(
                        if (controller.privacyBalanceVisible) {
                            privacyUsdEstimate(controller.privacyBalanceRaw, controller.octUsdPrice)
                        } else {
                            "~ \u2022\u2022\u2022\u2022 USD"
                        },
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .background(PrivacyPrimary.copy(alpha = 0.10f), RoundedCornerShape(Radius.md))
                        .clickable(
                            enabled = !controller.privacyBalanceDecrypting,
                            onClick = controller::togglePrivacyBalance,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    if (controller.privacyBalanceDecrypting) {
                        CircularProgressIndicator(Modifier.size(15.dp), color = PrivacyPrimary, strokeWidth = 2.dp)
                    } else {
                        GlyphIcon(
                            if (controller.privacyBalanceVisible) Glyph.EyeOff else Glyph.Eye,
                            Modifier.size(15.dp),
                            PrivacyPrimary,
                        )
                    }
                }
            }
            Spacer(Modifier.height(Space.x5))
            Text(
                if (controller.privacyEncryptedBalance == null) {
                    "No encrypted funds are stored on the active network."
                } else {
                    "Ciphertext stays on-chain. The amount is revealed locally on this device."
                },
                color = TextSecondary,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PrivacyActionCard(
    title: String,
    subtitle: String,
    glyph: Glyph,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    available: Boolean = true,
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(
            containerColor = if (available) PrivacyPrimary.copy(alpha = 0.055f) else SurfaceStrong,
        ),
    ) {
        Column(Modifier.padding(Space.x6)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            if (available) PrivacyPrimary.copy(alpha = 0.14f) else SurfaceMuted,
                            RoundedCornerShape(Radius.md),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    GlyphIcon(glyph, Modifier.size(19.dp), if (available) PrivacyPrimary else TextSecondary)
                }
                Spacer(Modifier.weight(1f))
                GlyphIcon(Glyph.Go, Modifier.size(14.dp), if (available) PrivacyPrimary else TextSecondary)
            }
            Spacer(Modifier.height(Space.x5))
            Text(title, color = TextTertiary, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Space.x2))
            Text(
                subtitle,
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PrivacyResumeCard(operation: PrivacyOperationState, onResume: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrivacyPrimary.copy(alpha = 0.09f), RoundedCornerShape(Radius.md))
            .clickable(onClick = onResume)
            .padding(Space.x5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (operation.isTerminal) {
            GlyphIcon(Glyph.Check, Modifier.size(18.dp), PrivacyPrimary)
        } else {
            CircularProgressIndicator(Modifier.size(18.dp), color = PrivacyPrimary, strokeWidth = 2.dp)
        }
        Spacer(Modifier.width(Space.x4))
        Column(Modifier.weight(1f)) {
            Text("${operation.type.label} ${operation.stage.label()}", color = TextTertiary, style = MaterialTheme.typography.labelMedium)
            Text("Tap to resume", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
        GlyphIcon(Glyph.ChevronUp, Modifier.size(15.dp), PrivacyPrimary)
    }
}

@Composable
private fun PrivacyHistoryRow(transaction: TransactionHistoryItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Space.x5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(PrivacyPrimary.copy(alpha = 0.10f), RoundedCornerShape(Radius.md)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                when (transaction.opType) {
                    "claim" -> Glyph.Download
                    "decrypt" -> Glyph.Eye
                    "stealth" -> Glyph.EyeOff
                    else -> Glyph.Privacy
                },
                Modifier.size(18.dp),
                PrivacyPrimary,
            )
        }
        Spacer(Modifier.width(Space.x5))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(transaction.opType.replaceFirstChar(Char::uppercase), color = TextTertiary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.width(Space.x3))
                Text(
                    transaction.status.lowercase(),
                    color = PrivacyPrimary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .background(PrivacyPrimary.copy(alpha = 0.09f), RoundedCornerShape(Radius.md))
                        .padding(horizontal = Space.x3, vertical = Space.x1),
                )
            }
            Spacer(Modifier.height(Space.x2))
            Text(formatPrivacyTimestamp(transaction.timestamp), color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            if (transaction.amount > 0.0) "${"%.6f".format(transaction.amount).trimEnd('0').trimEnd('.')} OCT" else "Private",
            color = TextTertiary,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun PrivacyEmptyState(text: String) {
    Text(
        text,
        color = TextSecondary,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.x7),
    )
}

private enum class PrivacyFeeMode { Auto, Fast, Custom }
private enum class PrivacyNoticeTone { Info, Caution, Error }

private fun privacyFailureTone(message: String): PrivacyNoticeTone {
    val normalized = message.lowercase()
    return if (
        normalized.contains("timeout") ||
        normalized.contains("timed out") ||
        normalized.contains("still pending")
    ) {
        PrivacyNoticeTone.Caution
    } else {
        PrivacyNoticeTone.Error
    }
}

@Composable
fun PrivacySettingsScreen(controller: PoctraController) {
    val status = controller.privacyKeyStatus
    val walletAddress = controller.wallet?.address.orEmpty()
    val walletIdentity = walletAddress.takeIf(String::isNotBlank)?.let(controller::walletIdentityDomain)
    var resetPhrase by remember(status.mode) { mutableStateOf("") }
    var reviewVisible by remember { mutableStateOf(false) }
    val actionLabel = privacyUpgradeActionLabel(status.mode, status.repairRequired, status.compactRefresh)
    val statusColor = when (status.mode) {
        PrivacyKeyUpgradeMode.Current -> PrivacyPrimary
        PrivacyKeyUpgradeMode.Blocked,
        PrivacyKeyUpgradeMode.KeyMismatch,
        PrivacyKeyUpgradeMode.FeeBlocked -> ErrorRed
        else -> Warning
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
    ) {
        PrivacyScreenHeader(
            title = "Privacy Settings",
            description = "Manage encryption keys and balance compatibility.",
            onBack = controller::closePrivacySettings,
            action = {
                PrivacyHeaderAction(
                    label = "Refresh",
                    glyph = Glyph.Refresh,
                    loading = controller.privacyKeyStatusLoading,
                    onClick = controller::refreshPrivacyKeyStatus,
                )
            },
        )
        Spacer(Modifier.height(Space.x7))

        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
            shape = RoundedCornerShape(Radius.md),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(Space.x7),
                verticalArrangement = Arrangement.spacedBy(Space.x5),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(statusColor.copy(alpha = 0.12f), RoundedCornerShape(Radius.md)),
                        contentAlignment = Alignment.Center,
                    ) {
                        GlyphIcon(
                            if (status.mode == PrivacyKeyUpgradeMode.Current) Glyph.Check else Glyph.Key,
                            Modifier.size(18.dp),
                            statusColor,
                        )
                    }
                    Spacer(Modifier.width(Space.x5))
                    Column(Modifier.weight(1f)) {
                        Text(
                            privacyUpgradeModeLabel(status.mode),
                            color = TextTertiary,
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Spacer(Modifier.height(Space.x1))
                        Text(
                            status.reason,
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                PrivacyKeyDetail(
                    "Registered key",
                    when {
                        status.registeredKeyCurrent -> "Current"
                        status.registeredKeyCompatible -> "Compatible"
                        status.registeredKeyPresent -> "Different"
                        else -> "Not registered"
                    },
                )
                if (status.cipherPresent) {
                    PrivacyKeyDetail(
                        "Encrypted balance",
                        status.encryptedBalanceRaw?.let { "${formatPrivacyAmount(it)} OCT" } ?: "Protected",
                    )
                    PrivacyKeyDetail(
                        "Cipher",
                        if (status.canonicalRandomnessCommitment) "Key-bound" else "Refresh required",
                    )
                    if (status.baseLayerCount > 0) {
                        PrivacyKeyDetail("Base layers", status.baseLayerCount.toString())
                    }
                }
                status.requiredPublicFeeRaw?.let {
                    PrivacyKeyDetail("Network fee", "${formatPrivacyAmount(it)} OCT")
                }
            }
        }

        if (status.mode == PrivacyKeyUpgradeMode.LegacyZeroReset) {
            Spacer(Modifier.height(Space.x6))
            PrivacyContextBanner(
                text = "This replaces the legacy ciphertext with verified zero. Hidden funds cannot be recovered after confirmation.",
                tone = PrivacyNoticeTone.Error,
            )
            Spacer(Modifier.height(Space.x4))
            Text(
                "Type ${PrivacyUpgradePolicy.ResetPhrase}",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
            )
            Spacer(Modifier.height(Space.x3))
            PoctraTextField(
                value = resetPhrase,
                onValueChange = { resetPhrase = it.take(48) },
                label = "Confirmation phrase",
                placeholder = PrivacyUpgradePolicy.ResetPhrase,
                focusColor = PrivacyPrimary,
            )
        }

        Spacer(Modifier.height(Space.x7))
        when {
            status.canSubmit -> PrivacyPrimaryButton(
                text = actionLabel,
                enabled = status.mode != PrivacyKeyUpgradeMode.LegacyZeroReset ||
                    resetPhrase.trim() == PrivacyUpgradePolicy.ResetPhrase,
                onClick = { reviewVisible = true },
                modifier = Modifier.fillMaxWidth(),
            )
            status.mode == PrivacyKeyUpgradeMode.Current -> PoctraButton(
                text = "Refresh encrypted balance",
                onClick = controller::refreshPrivacyNetworkState,
                enabled = !controller.privacyKeyStatusLoading,
                loading = controller.privacyKeyStatusLoading,
                leadingGlyph = Glyph.Refresh,
                compact = true,
                modifier = Modifier.fillMaxWidth(),
            )
            else -> {
                PrivacyContextBanner(status.reason, PrivacyNoticeTone.Error)
                Spacer(Modifier.height(Space.x4))
                PoctraButton(
                    text = "Recheck network state",
                    onClick = controller::recheckPrivacyKeyState,
                    enabled = !controller.privacyKeyStatusLoading,
                    loading = controller.privacyKeyStatusLoading,
                    variant = ButtonVariant.Ghost,
                    leadingGlyph = Glyph.Refresh,
                    compact = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (status.mode == PrivacyKeyUpgradeMode.KeyMismatch) {
                    Spacer(Modifier.height(Space.x4))
                    Text(
                        "Poctra will never overwrite a foreign Privacy key. If the mismatch remains, restore the wallet that created the registered key or verify that the active RPC is correct.",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Spacer(Modifier.height(Space.x5))
        Text(
            "Key switching is explicit. Proofs are built and verified locally before the transaction is signed.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodySmall,
        )
    }

    if (reviewVisible) {
        PrivacyReviewSheet(
            title = actionLabel,
            glyph = Glyph.Key,
            source = ReviewFlowEndpoint(
                "Current encrypted balance",
                walletAddress,
                walletIdentity,
            ),
            destination = ReviewFlowEndpoint(
                "Current Privacy key",
                walletAddress,
                walletIdentity,
            ),
            actionLabel = actionLabel,
            details = buildList {
                add("Mode" to privacyUpgradeModeLabel(status.mode))
                status.encryptedBalanceRaw?.let {
                    add("Balance preserved" to "${formatPrivacyAmount(it)} OCT")
                }
                status.requiredPublicFeeRaw?.let {
                    add("Network fee" to "${formatPrivacyAmount(it)} OCT")
                }
            },
            notice = if (status.mode == PrivacyKeyUpgradeMode.LegacyZeroReset) {
                "The zero reset is irreversible."
            } else {
                "Poctra rechecks the source cipher before broadcast to prevent a stale migration."
            },
            noticeTone = if (status.mode == PrivacyKeyUpgradeMode.LegacyZeroReset) {
                PrivacyNoticeTone.Error
            } else {
                PrivacyNoticeTone.Info
            },
            onDismiss = { reviewVisible = false },
            onProceed = {
                reviewVisible = false
                controller.startPrivacyKeyUpgrade(resetPhrase)
            },
        )
    }
}

@Composable
private fun PrivacyKeyDetail(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.weight(1f))
        Text(
            value,
            color = TextTertiary,
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.End,
        )
    }
}

private fun privacyUpgradeModeLabel(mode: PrivacyKeyUpgradeMode): String = when (mode) {
    PrivacyKeyUpgradeMode.Current -> "Encryption ready"
    PrivacyKeyUpgradeMode.EmptyKeySwitch -> "Key setup required"
    PrivacyKeyUpgradeMode.KeyBoundMigration -> "Recrypt required"
    PrivacyKeyUpgradeMode.LegacyPublicMigration -> "Legacy upgrade available"
    PrivacyKeyUpgradeMode.LegacyCommitmentMigration -> "History migration available"
    PrivacyKeyUpgradeMode.LegacyZeroReset -> "Verified zero reset"
    PrivacyKeyUpgradeMode.KeyMismatch -> "Key mismatch"
    PrivacyKeyUpgradeMode.FeeBlocked -> "Network fee required"
    PrivacyKeyUpgradeMode.Blocked -> "Migration blocked"
    PrivacyKeyUpgradeMode.Unavailable -> "Status unavailable"
}

private fun privacyUpgradeActionLabel(
    mode: PrivacyKeyUpgradeMode,
    repairRequired: Boolean,
    compactRefresh: Boolean,
): String = when {
    repairRequired -> "Repair encrypted balance"
    compactRefresh -> "Compact encrypted balance"
    mode == PrivacyKeyUpgradeMode.EmptyKeySwitch -> "Activate Privacy key"
    mode == PrivacyKeyUpgradeMode.LegacyZeroReset -> "Reset encrypted balance"
    mode == PrivacyKeyUpgradeMode.KeyBoundMigration -> "Recrypt encrypted balance"
    else -> "Upgrade encrypted balance"
}

@Composable
fun PrivacyEncryptScreen(controller: PoctraController) {
    PrivacyTransferActionScreen(
        controller = controller,
        type = PrivacyOperationType.Encrypt,
        title = "Encrypt OCT",
        description = "Move OCT into encrypted balance.",
        glyph = Glyph.Privacy,
    ) { _, amount, fee -> controller.startPrivacyEncrypt(amount, fee) }
}

@Composable
fun PrivacyDecryptScreen(controller: PoctraController) {
    PrivacyTransferActionScreen(
        controller = controller,
        type = PrivacyOperationType.Decrypt,
        title = "Decrypt OCT",
        description = "Return encrypted OCT to public balance.",
        glyph = Glyph.Eye,
    ) { _, amount, fee -> controller.startPrivacyDecrypt(amount, fee) }
}

@Composable
fun PrivacyStealthScreen(controller: PoctraController) {
    PrivacyTransferActionScreen(
        controller = controller,
        type = PrivacyOperationType.Stealth,
        title = "Stealth Transfer",
        description = "Send privately from encrypted balance.",
        glyph = Glyph.EyeOff,
        requiresRecipient = true,
    ) { recipient, amount, fee -> controller.startPrivacyStealth(recipient, amount, fee) }
}

@Composable
private fun PrivacyTransferActionScreen(
    controller: PoctraController,
    type: PrivacyOperationType,
    title: String,
    description: String,
    glyph: Glyph,
    requiresRecipient: Boolean = false,
    onProceed: (String, String, String) -> Unit,
) {
    var recipient by remember(type) { mutableStateOf("") }
    var amount by remember(type) { mutableStateOf("") }
    var feeMode by remember(type) { mutableStateOf(PrivacyFeeMode.Auto) }
    var customFee by remember(type) { mutableStateOf(controller.privacyRecommendedFeeOu.toString()) }
    var amountError by remember(type) { mutableStateOf<String?>(null) }
    var recipientError by remember(type) { mutableStateOf<String?>(null) }
    var showReview by remember(type) { mutableStateOf(false) }
    val selectedFee = privacySelectedFee(feeMode, controller.privacyRecommendedFeeOu, customFee)
    val feeReady = selectedFee != null && !controller.privacyFeeLoading
    val recipientLookup = rememberOctraRecipientLookup(controller, recipient)
    val resolvedRecipient = recipientLookup.resolution?.address ?: recipient.trim()
    val maximumAmount = selectedFee?.let { controller.privacyMaxAmount(type, it) }.orEmpty()

    LaunchedEffect(type) {
        if (type in setOf(PrivacyOperationType.Decrypt, PrivacyOperationType.Stealth) &&
            controller.privacyBalanceRaw == null &&
            !controller.privacyBalanceDecrypting
        ) {
            controller.togglePrivacyBalance()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(Space.x8),
    ) {
        PrivacyScreenHeader(
            title = title,
            description = description,
            onBack = controller::closePrivacyAction,
        )
        Spacer(Modifier.height(Space.x7))
        if (requiresRecipient) {
            RecipientLookupField(
                value = recipient,
                onValueChange = {
                    recipient = it.trim()
                    recipientError = null
                },
                label = "Recipient",
                placeholder = "foo.oct or oct address",
                lookupState = recipientLookup,
                glyph = Glyph.OctraIdentity,
                onClear = {
                    recipient = ""
                    recipientError = null
                },
                showDirectOctraAddressPreview = true,
                isError = recipientError != null,
                supportingText = recipientError,
                accentColor = PrivacyPrimary,
            )
            Spacer(Modifier.height(Space.x5))
        }
        PoctraTextField(
            value = amount,
            onValueChange = { input ->
                amount = input.filter { it.isDigit() || it == '.' || it == ',' }
                amountError = null
            },
            label = "Amount OCT",
            placeholder = "0.0",
            keyboardType = KeyboardType.Decimal,
            singleLine = true,
            isError = amountError != null,
            supportingText = amountError,
            focusColor = PrivacyPrimary,
        )
        Spacer(Modifier.height(Space.x4))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (type == PrivacyOperationType.Encrypt) {
                        "Available: ${controller.tokenBalance.amount} OCT"
                    } else if (controller.privacyBalanceDecrypting) {
                        "Decrypting available balance..."
                    } else {
                        "Available: ${controller.privacyBalanceText} OCT"
                    },
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Text(
                "Max",
                modifier = Modifier
                    .clip(RoundedCornerShape(Radius.md))
                    .clickable(enabled = maximumAmount.isNotBlank() && maximumAmount != "0") {
                        amount = maximumAmount
                        amountError = null
                    }
                    .padding(horizontal = Space.x4, vertical = Space.x2),
                color = if (maximumAmount.isNotBlank() && maximumAmount != "0") PrivacyPrimary else TextSecondary.copy(alpha = 0.45f),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Spacer(Modifier.height(Space.x6))
        PrivacyFeePanel(
            controller = controller,
            type = type,
            mode = feeMode,
            customFee = customFee,
            onModeChange = { feeMode = it },
            onCustomFeeChange = { customFee = it.filter(Char::isDigit).take(12) },
        )
        Spacer(Modifier.height(Space.x5))
        PrivacyNotice(
            if (type == PrivacyOperationType.Stealth) {
                "Two local range proofs are generated before broadcast. This can take several minutes."
            } else {
                "The proof is generated locally before the on-chain transaction is broadcast."
            },
        )
        Spacer(Modifier.height(Space.x8))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
            PoctraButton(
                text = "Cancel",
                onClick = controller::closePrivacyAction,
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.Ghost,
                fullHeight = true,
            )
            PrivacyPrimaryButton(
                text = "Proceed",
                onClick = {
                    amountError = null
                    recipientError = null
                    val amountRaw = privacyParseAmount(amount)
                    val maximumRaw = privacyParseAmount(maximumAmount)
                    when {
                        selectedFee == null -> amountError = "Select a valid network fee."
                        !controller.privacyFeeAffordable(selectedFee) -> amountError = "Public OCT balance is insufficient for the selected fee."
                        amountRaw == null -> amountError = "Enter an amount with no more than 6 decimal places."
                        maximumRaw == null || amountRaw > maximumRaw -> amountError = "Amount exceeds the available balance."
                        requiresRecipient && recipientLookup.loading -> recipientError = "Wait until recipient lookup finishes."
                        requiresRecipient && recipientLookup.error != null -> recipientError = recipientLookup.error
                        requiresRecipient && !isValidOctraAddress(resolvedRecipient) -> recipientError = "Enter a valid foo.oct name or Octra address."
                        else -> showReview = true
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = amount.isNotBlank() && feeReady && (!requiresRecipient || recipient.isNotBlank()),
            )
        }
    }

    if (showReview && selectedFee != null) {
        val ownerAddress = controller.wallet?.address.orEmpty()
        val ownerIdentity = controller.wallet?.address?.let(controller::walletIdentityDomain)
        val recipientIdentity = recipientLookup.resolution?.name
            ?: controller.identityNameForAddress(resolvedRecipient)
        val source = when (type) {
            PrivacyOperationType.Encrypt -> ReviewFlowEndpoint(
                label = "Public balance",
                address = ownerAddress,
                identityName = ownerIdentity,
            )
            PrivacyOperationType.Decrypt -> ReviewFlowEndpoint(
                label = "Encrypted balance",
                address = ownerAddress,
                identityName = ownerIdentity,
            )
            PrivacyOperationType.Stealth -> ReviewFlowEndpoint(
                label = "Encrypted balance",
                address = ownerAddress,
                identityName = ownerIdentity,
            )
            PrivacyOperationType.Claim -> ReviewFlowEndpoint("Private output", ownerAddress, ownerIdentity)
            PrivacyOperationType.KeySwitch -> ReviewFlowEndpoint("Encrypted balance", ownerAddress, ownerIdentity)
        }
        val destination = when (type) {
            PrivacyOperationType.Encrypt -> ReviewFlowEndpoint(
                label = "Encrypted balance",
                address = ownerAddress,
                identityName = ownerIdentity,
            )
            PrivacyOperationType.Decrypt -> ReviewFlowEndpoint(
                label = "Public balance",
                address = ownerAddress,
                identityName = ownerIdentity,
            )
            PrivacyOperationType.Stealth -> ReviewFlowEndpoint(
                label = "Private recipient",
                address = resolvedRecipient,
                identityName = recipientIdentity,
            )
            PrivacyOperationType.Claim -> ReviewFlowEndpoint("Encrypted balance", ownerAddress, ownerIdentity)
            PrivacyOperationType.KeySwitch -> ReviewFlowEndpoint("Privacy key", ownerAddress, ownerIdentity)
        }
        PrivacyReviewSheet(
            title = "Review ${type.label}",
            glyph = glyph,
            source = source,
            destination = destination,
            actionLabel = type.label,
            details = buildList {
                add("Network" to privacyNetworkLabel(controller))
                add("Amount" to "$amount OCT")
                add("Network fee" to "$selectedFee OU (${formatPrivacyAmount(selectedFee)} OCT)")
            },
            notice = when {
                type == PrivacyOperationType.Stealth -> "Stealth creates two local range proofs and may take several minutes."
                feeMode == PrivacyFeeMode.Fast -> "Fast uses twice the RPC-recommended network fee."
                else -> "The transaction is signed locally and broadcast only after confirmation."
            },
            noticeTone = if (feeMode == PrivacyFeeMode.Fast) PrivacyNoticeTone.Caution else PrivacyNoticeTone.Info,
            onDismiss = { showReview = false },
            onProceed = {
                showReview = false
                onProceed(resolvedRecipient, amount, selectedFee.toString())
            },
        )
    }
}

@Composable
fun PrivacyClaimScreen(controller: PoctraController) {
    var feeMode by remember { mutableStateOf(PrivacyFeeMode.Auto) }
    var customFee by remember { mutableStateOf(controller.privacyRecommendedFeeOu.toString()) }
    var reviewClaim by remember { mutableStateOf<PrivacyClaimableTransfer?>(null) }
    var reviewBulk by remember { mutableStateOf(false) }
    val selectedFee = privacySelectedFee(feeMode, controller.privacyRecommendedFeeOu, customFee)
    val operation = controller.privacyOperation
    val transfers = controller.privacyClaimableTransfers

    LaunchedEffect(transfers.map { it.sender }) {
        controller.refreshIdentityNamesForAddresses(transfers.map { it.sender })
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(Space.x8),
        verticalArrangement = Arrangement.spacedBy(Space.x4),
    ) {
        item {
            PrivacyScreenHeader(
                title = "Claim",
                description = "Claim available private transfers.",
                onBack = controller::closePrivacyAction,
                action = {
                    PrivacyHeaderAction(
                        label = "Refresh",
                        glyph = Glyph.Refresh,
                        loading = operation?.stage == PrivacyOperationStage.Scanning,
                        onClick = { controller.scanPrivacyClaims(showDialog = false, force = true) },
                    )
                },
            )
        }
        item {
            PrivacyFeePanel(
                controller = controller,
                type = PrivacyOperationType.Claim,
                mode = feeMode,
                customFee = customFee,
                onModeChange = { feeMode = it },
                onCustomFeeChange = { customFee = it.filter(Char::isDigit).take(12) },
            )
        }
        val totalClaimFee = selectedFee?.let { privacyBulkFee(it, transfers.size) }
        if (transfers.isNotEmpty() && totalClaimFee != null && !controller.privacyFeeAffordable(totalClaimFee)) {
            item {
                PrivacyContextBanner(
                    text = if (controller.privacyFeeAffordable(selectedFee)) {
                        "Public OCT balance is insufficient for all ${transfers.size} Claim fees. Claim individually or add OCT for fees."
                    } else {
                        "Public OCT balance is insufficient for the selected Claim fee."
                    },
                    tone = PrivacyNoticeTone.Error,
                )
            }
        }
        when {
            operation?.stage == PrivacyOperationStage.Scanning -> item { PrivacyClaimLoading() }
            operation?.stage == PrivacyOperationStage.Failed && transfers.isEmpty() -> item {
                PrivacyClaimState(operation.message, error = true) {
                    controller.scanPrivacyClaims(showDialog = false, force = true)
                }
            }
            transfers.isEmpty() -> item { PrivacyClaimState("No claimable private transfers were found.") }
            else -> {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Claimable Transfers", color = TextTertiary, style = MaterialTheme.typography.titleMedium)
                            Text("${transfers.size} outputs available", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                        }
                        PrivacyPrimaryButton(
                            text = "Claim All",
                            onClick = { reviewBulk = true },
                            modifier = Modifier.width(112.dp),
                            compact = true,
                            enabled = selectedFee != null &&
                                !controller.privacyFeeLoading &&
                                privacyBulkFee(selectedFee, transfers.size)?.let(controller::privacyFeeAffordable) == true,
                        )
                    }
                }
                items(transfers, key = { it.outputId }) { transfer ->
                    PrivacyClaimRow(
                        transfer = transfer,
                        enabled = selectedFee?.let(controller::privacyFeeAffordable) == true && !controller.privacyFeeLoading,
                        onClaim = { reviewClaim = transfer },
                    )
                }
            }
        }
    }

    reviewClaim?.let { transfer ->
        val fee = selectedFee
        if (fee != null) {
            PrivacyReviewSheet(
                title = "Review Claim",
                glyph = Glyph.Download,
                source = ReviewFlowEndpoint(
                    label = "Private output",
                    address = transfer.sender,
                    identityName = controller.identityNameForAddress(transfer.sender),
                ),
                destination = ReviewFlowEndpoint(
                    label = "Encrypted balance",
                    address = controller.wallet?.address.orEmpty(),
                    identityName = controller.wallet?.address?.let(controller::walletIdentityDomain),
                ),
                actionLabel = "Claim",
                details = listOf(
                    "Network" to privacyNetworkLabel(controller),
                    "Amount" to "${transfer.amountOct} OCT",
                    "Output" to transfer.outputId,
                    "Network fee" to "$fee OU (${formatPrivacyAmount(fee)} OCT)",
                ),
                notice = "This claims one private output and broadcasts one on-chain transaction.",
                noticeTone = if (feeMode == PrivacyFeeMode.Fast) PrivacyNoticeTone.Caution else PrivacyNoticeTone.Info,
                onDismiss = { reviewClaim = null },
                onProceed = {
                    reviewClaim = null
                    controller.startPrivacyClaim(transfer, fee.toString())
                },
            )
        }
    }

    if (reviewBulk) {
        val fee = selectedFee
        val totalFee = fee?.let { privacyBulkFee(it, transfers.size) }
        if (fee != null && totalFee != null) {
            val totalAmount = privacyClaimTotalAmount(transfers)
            PrivacyReviewSheet(
                title = "Review Claim All",
                glyph = Glyph.Download,
                source = ReviewFlowEndpoint(
                    label = "Private outputs",
                    address = "${transfers.size} claimable outputs",
                ),
                destination = ReviewFlowEndpoint(
                    label = "Encrypted balance",
                    address = controller.wallet?.address.orEmpty(),
                    identityName = controller.wallet?.address?.let(controller::walletIdentityDomain),
                ),
                actionLabel = "Claim All",
                details = listOf(
                    "Network" to privacyNetworkLabel(controller),
                    "Outputs" to transfers.size.toString(),
                    "Total amount" to "${formatPrivacyAmount(totalAmount)} OCT",
                    "Fee per claim" to "$fee OU",
                    "Maximum total fee" to "$totalFee OU (${formatPrivacyAmount(totalFee)} OCT)",
                ),
                notice = "Claim All submits transactions sequentially and waits for each confirmation before continuing.",
                noticeTone = PrivacyNoticeTone.Info,
                onDismiss = { reviewBulk = false },
                onProceed = {
                    reviewBulk = false
                    controller.startPrivacyBulkClaim(fee.toString())
                },
            )
        }
    }
}

@Composable
private fun PrivacyScreenHeader(
    title: String,
    onBack: () -> Unit,
    description: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconCircleButton(Glyph.Back, "Back", onBack)
        Spacer(Modifier.width(Space.x6))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextTertiary, style = MaterialTheme.typography.titleLarge)
            description?.let {
                Spacer(Modifier.height(Space.x1))
                Text(
                    it,
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
        action?.invoke()
    }
}

@Composable
private fun PrivacyFeePanel(
    controller: PoctraController,
    type: PrivacyOperationType,
    mode: PrivacyFeeMode,
    customFee: String,
    onModeChange: (PrivacyFeeMode) -> Unit,
    onCustomFeeChange: (String) -> Unit,
) {
    val recommended = controller.privacyRecommendedFeeOu.coerceAtLeast(1L)
    val fast = privacyFastFee(recommended)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
    ) {
        Column(Modifier.padding(Space.x5)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Network Fee", color = TextTertiary, style = MaterialTheme.typography.titleSmall)
                    Text(
                        if (controller.privacyFeeLoading) "Fetching recommended fee..." else "Recommended by active RPC",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(PrivacyPrimary.copy(alpha = 0.10f))
                        .clickable(enabled = !controller.privacyFeeLoading) { controller.refreshPrivacyFee(type) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (controller.privacyFeeLoading) {
                        CircularProgressIndicator(Modifier.size(14.dp), color = PrivacyPrimary, strokeWidth = 2.dp)
                    } else {
                        GlyphIcon(Glyph.Refresh, Modifier.size(15.dp), PrivacyPrimary)
                    }
                }
            }
            Spacer(Modifier.height(Space.x5))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                PrivacyFeeOption(
                    title = "Auto",
                    glyph = Glyph.Check,
                    value = "${formatPrivacyAmount(recommended)} OCT",
                    selected = mode == PrivacyFeeMode.Auto,
                    onClick = { onModeChange(PrivacyFeeMode.Auto) },
                    modifier = Modifier.weight(1f),
                )
                PrivacyFeeOption(
                    title = "Fast",
                    glyph = Glyph.Send,
                    value = "${formatPrivacyAmount(fast)} OCT",
                    selected = mode == PrivacyFeeMode.Fast,
                    onClick = { onModeChange(PrivacyFeeMode.Fast) },
                    modifier = Modifier.weight(1f),
                )
                PrivacyFeeOption(
                    title = "Custom",
                    glyph = Glyph.Settings,
                    value = customFee.toLongOrNull()?.takeIf { it > 0L }
                        ?.let { "${formatPrivacyAmount(it)} OCT" }
                        ?: "Set fee",
                    selected = mode == PrivacyFeeMode.Custom,
                    onClick = { onModeChange(PrivacyFeeMode.Custom) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (mode == PrivacyFeeMode.Custom) {
                Spacer(Modifier.height(Space.x4))
                PoctraTextField(
                    value = customFee,
                    onValueChange = onCustomFeeChange,
                    label = "Custom fee (OU)",
                    keyboardType = KeyboardType.Number,
                    singleLine = true,
                    isError = customFee.toLongOrNull()?.let { it <= 0L } != false,
                    supportingText = if (customFee.toLongOrNull()?.let { it > 0L } == true) null else "Enter a valid fee.",
                    focusColor = PrivacyPrimary,
                )
            }
            if (mode == PrivacyFeeMode.Fast) {
                Spacer(Modifier.height(Space.x4))
                PrivacyContextBanner(
                    text = "Fast uses twice the fee recommended by the active RPC.",
                    tone = PrivacyNoticeTone.Caution,
                )
            }
        }
    }
}

@Composable
private fun PrivacyFeeOption(
    title: String,
    glyph: Glyph,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .height(58.dp)
            .clip(RoundedCornerShape(Radius.md))
            .background(if (selected) PrivacyPrimary.copy(alpha = 0.14f) else SurfaceMuted.copy(alpha = 0.72f))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.x2, vertical = Space.x3),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlyphIcon(
                glyph,
                modifier = Modifier.size(11.dp),
                color = if (selected) PrivacyPrimary else TextSecondary,
            )
            Spacer(Modifier.width(Space.x2))
            Text(
                title,
                color = if (selected) PrivacyPrimary else TextTertiary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Spacer(Modifier.height(Space.x1))
        Text(value, color = TextSecondary, style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), maxLines = 1)
    }
}

@Composable
private fun PrivacyNotice(text: String) {
    PrivacyContextBanner(text = text, tone = PrivacyNoticeTone.Info)
}

@Composable
private fun PrivacyContextBanner(text: String, tone: PrivacyNoticeTone) {
    val color = when (tone) {
        PrivacyNoticeTone.Info -> InfoBlue
        PrivacyNoticeTone.Caution -> Warning
        PrivacyNoticeTone.Error -> ErrorRed
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Space.x2),
        verticalAlignment = Alignment.Top,
    ) {
        GlyphIcon(
            Glyph.Info,
            Modifier.offset(y = (-1).dp).size(13.dp),
            color,
        )
        Spacer(Modifier.width(Space.x3))
        Text(
            text,
            color = color,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PrivacyReviewSheet(
    title: String,
    glyph: Glyph,
    source: ReviewFlowEndpoint,
    destination: ReviewFlowEndpoint,
    actionLabel: String,
    details: List<Pair<String, String>>,
    notice: String,
    noticeTone: PrivacyNoticeTone,
    onDismiss: () -> Unit,
    onProceed: () -> Unit,
) {
    PrivacyBottomSheet(onDismiss = onDismiss) {
        PrivacySheetHeader(title, "Verify every detail before signing.", glyph)
        ReviewProcessFlow(
            source = source,
            destination = destination,
            actionLabel = actionLabel,
            actionGlyph = glyph,
            modifier = Modifier.padding(top = Space.x5),
            accentColor = PrivacyPrimary,
        )
        Spacer(Modifier.height(Space.x6))
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(Radius.md),
            colors = CardDefaults.cardColors(containerColor = SurfaceBase),
        ) {
            Column(Modifier.padding(horizontal = Space.x5, vertical = Space.x3)) {
                details.forEachIndexed { index, (label, value) ->
                    PrivacyReviewDetail(label, value)
                    if (index < details.lastIndex) {
                        Spacer(Modifier.height(Space.x2))
                    }
                }
            }
        }
        Spacer(Modifier.height(Space.x5))
        PrivacyContextBanner(notice, noticeTone)
        Spacer(Modifier.height(Space.x6))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
            PoctraButton(
                text = "Cancel",
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
                variant = ButtonVariant.Ghost,
                fullHeight = true,
            )
            PrivacyPrimaryButton(
                text = "Confirm",
                onClick = onProceed,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PrivacyReviewDetail(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.x4),
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(0.40f))
        Text(
            value,
            color = TextTertiary,
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.60f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PrivacyClaimLoading() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = Space.x8),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircularProgressIndicator(Modifier.size(18.dp), color = PrivacyPrimary, strokeWidth = 2.dp)
        Spacer(Modifier.width(Space.x4))
        Text("Scanning claimable transfers...", color = TextSecondary, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun PrivacyClaimState(text: String, error: Boolean = false, onRetry: (() -> Unit)? = null) {
    val stateModifier = if (error || onRetry != null) {
        Modifier.background(
            (if (error) ErrorRed else PrivacyPrimary).copy(alpha = 0.07f),
            RoundedCornerShape(Radius.md),
        )
    } else {
        Modifier
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(stateModifier)
            .padding(Space.x6),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        GlyphIcon(if (error) Glyph.Info else Glyph.Download, Modifier.size(21.dp), if (error) ErrorRed else PrivacyPrimary)
        Spacer(Modifier.height(Space.x3))
        Text(text, color = if (error) ErrorRed else TextSecondary, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        onRetry?.let {
            Spacer(Modifier.height(Space.x4))
            PoctraButton(
                text = "Retry",
                onClick = it,
                variant = ButtonVariant.Ghost,
                compact = true,
                leadingGlyph = Glyph.Refresh,
            )
        }
    }
}

private fun privacySelectedFee(mode: PrivacyFeeMode, recommendedFee: Long, customFee: String): Long? = when (mode) {
    PrivacyFeeMode.Auto -> recommendedFee.coerceAtLeast(1L)
    PrivacyFeeMode.Fast -> privacyFastFee(recommendedFee)
    PrivacyFeeMode.Custom -> customFee.toLongOrNull()?.takeIf { it > 0L }
}

private fun privacyFastFee(recommendedFee: Long): Long =
    recommendedFee.coerceAtLeast(1L).coerceAtMost(Long.MAX_VALUE / 2L) * 2L

private fun privacyParseAmount(value: String): Long? = runCatching {
    val decimal = value.trim().replace(',', '.').toBigDecimal()
    require(decimal.signum() > 0)
    require(decimal.stripTrailingZeros().scale() <= 6)
    decimal.movePointRight(6).longValueExact()
}.getOrNull()

private fun privacyBulkFee(feeOu: Long, count: Int): Long? =
    runCatching { Math.multiplyExact(feeOu, count.toLong()) }.getOrNull()

private fun privacyClaimTotalAmount(transfers: List<PrivacyClaimableTransfer>): Long =
    runCatching { transfers.fold(0L) { total, transfer -> Math.addExact(total, transfer.amountRaw) } }
        .getOrDefault(Long.MAX_VALUE)

private fun privacyUsdEstimate(balanceRaw: Long?, usdPrice: Double?): String {
    if (balanceRaw == null || usdPrice == null || !usdPrice.isFinite()) return "~ -- USD"
    val value = (balanceRaw / 1_000_000.0) * usdPrice
    return "~ \$${String.format(Locale.US, "%,.2f", value)} USD"
}

private fun privacyNetworkLabel(controller: PoctraController): String =
    (controller.activeRpcNetwork?.networkType ?: controller.rpcStatus?.network ?: "network")
        .lowercase()
        .replaceFirstChar(Char::uppercase)

@Composable
private fun PrivacyOperationSheet(controller: PoctraController, operation: PrivacyOperationState) {
    PrivacyBottomSheet(onDismiss = controller::minimizePrivacyOperation) {
        PrivacySheetHeader(
            title = "${operation.type.label} Privacy",
            subtitle = operation.message,
            glyph = when (operation.type) {
                PrivacyOperationType.Claim -> Glyph.Download
                PrivacyOperationType.Decrypt -> Glyph.Eye
                PrivacyOperationType.Stealth -> Glyph.EyeOff
                PrivacyOperationType.Encrypt -> Glyph.Privacy
                PrivacyOperationType.KeySwitch -> Glyph.Key
            },
        )
        Spacer(Modifier.height(Space.x6))
        PrivacyStepper(operation)
        if (operation.stage == PrivacyOperationStage.Failed) {
            Spacer(Modifier.height(Space.x4))
            PrivacyContextBanner(operation.message, privacyFailureTone(operation.message))
        }

        if (operation.stage == PrivacyOperationStage.Review && operation.type == PrivacyOperationType.Claim) {
            Spacer(Modifier.height(Space.x5))
            PrivacyContextBanner("Claimable transfers are reviewed from the dedicated Claim screen.", PrivacyNoticeTone.Info)
        } else {
            operation.amountOct.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Space.x5))
                PrivacyInfoLine("Amount", "$it OCT")
            }
            Spacer(Modifier.height(Space.x3))
            PrivacyElapsedInfo(operation)
            operation.txHash?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(Space.x3))
                PrivacyInfoLine(
                    label = "Transaction",
                    value = it.take(10) + "..." + it.takeLast(8),
                    actionLabel = "View",
                    onAction = controller::openPrivacyTransactionExplorer,
                )
            }
            operation.recipient?.let {
                Spacer(Modifier.height(Space.x3))
                PrivacyInfoLine("Recipient", it)
            }
        }

        Spacer(Modifier.height(Space.x6))
        if (operation.isTerminal || operation.stage == PrivacyOperationStage.Review) {
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x5)) {
                if (operation.stage == PrivacyOperationStage.Review) {
                    PoctraButton(
                        text = "Cancel",
                        onClick = controller::finishPrivacyOperation,
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.Ghost,
                        fullHeight = true,
                    )
                    PoctraButton(
                        text = "Minimize",
                        onClick = controller::minimizePrivacyOperation,
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.Ghost,
                        fullHeight = true,
                    )
                } else if (
                    operation.stage == PrivacyOperationStage.Failed &&
                    operation.message.contains("compact refresh", ignoreCase = true)
                ) {
                    PoctraButton(
                        text = "Close",
                        onClick = controller::finishPrivacyOperation,
                        modifier = Modifier.weight(1f),
                        variant = ButtonVariant.Ghost,
                        fullHeight = true,
                    )
                    PrivacyPrimaryButton(
                        text = "Recrypt",
                        onClick = {
                            controller.finishPrivacyOperation()
                            controller.openPrivacySettings()
                        },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    PrivacyPrimaryButton(
                        text = "Done",
                        onClick = controller::finishPrivacyOperation,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            PoctraButton(
                text = "Minimize",
                onClick = controller::minimizePrivacyOperation,
                modifier = Modifier.fillMaxWidth(),
                variant = ButtonVariant.Ghost,
                fullHeight = true,
            )
        }
    }
}

@Composable
private fun PrivacyBottomSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        var visible by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { visible = true }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.52f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.BottomCenter,
        ) {
            AnimatedVisibility(
                visible = visible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * 0.90f)
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
                                .width(38.dp)
                                .height(4.dp)
                                .background(BorderLine, RoundedCornerShape(Radius.md))
                                .align(Alignment.CenterHorizontally),
                        )
                        Spacer(Modifier.height(Space.x5))
                        content()
                    }
                }
            }
        }
    }
}

@Composable
private fun PrivacySheetHeader(title: String, subtitle: String, glyph: Glyph) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(PrivacyPrimary.copy(alpha = 0.12f), RoundedCornerShape(Radius.md)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph, Modifier.size(21.dp), PrivacyPrimary)
        }
        Spacer(Modifier.width(Space.x5))
        Column(Modifier.weight(1f)) {
            Text(title, color = TextTertiary, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(Space.x2))
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PrivacyStepper(operation: PrivacyOperationState) {
    val steps = when (operation.type) {
        PrivacyOperationType.Encrypt -> listOf(
            "Initialize keys" to "Prepare the encrypted balance context",
            "Build proof" to "Generate and verify the encrypt proof",
            "Submit" to "Sign and broadcast the private transaction",
            "Confirmed" to "Wait for final blockchain confirmation",
        )
        PrivacyOperationType.Decrypt -> listOf(
            "Initialize keys" to "Open the encrypted balance context",
            "Build proof" to "Generate and verify the balance proof",
            "Submit" to "Sign and broadcast the private transaction",
            "Confirmed" to "Wait for final blockchain confirmation",
        )
        PrivacyOperationType.Stealth -> listOf(
            "Resolve recipient" to "Resolve keys and prepare the transfer",
            "Build proofs" to "Generate and verify range proofs",
            "Submit" to "Sign and broadcast the stealth transfer",
            "Confirmed" to "Wait for final blockchain confirmation",
        )
        PrivacyOperationType.Claim -> listOf(
            "Scan outputs" to "Find claimable private outputs",
            "Build claim" to "Generate and verify the claim proof",
            "Submit" to "Sign and broadcast the claim",
            "Confirmed" to "Wait for final blockchain confirmation",
        )
        PrivacyOperationType.KeySwitch -> listOf(
            "Check key" to "Check the key state and required fee",
            "Build proof" to "Generate and verify the migration proof",
            "Submit" to "Sign and broadcast the key switch",
            "Confirmed" to "Wait for final blockchain confirmation",
        )
    }
    val activeIndex = operation.progressIndex
    val failureColor = when (privacyFailureTone(operation.message)) {
        PrivacyNoticeTone.Caution -> Warning
        PrivacyNoticeTone.Info -> InfoBlue
        PrivacyNoticeTone.Error -> ErrorRed
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        steps.forEachIndexed { index, (title, description) ->
            val done = operation.stage == PrivacyOperationStage.Confirmed || index < activeIndex
            val failed = operation.stage == PrivacyOperationStage.Failed && index == activeIndex
            val active = !operation.isTerminal &&
                operation.stage != PrivacyOperationStage.Review &&
                index == activeIndex
            Row(Modifier.fillMaxWidth()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .background(
                                when {
                                    done -> PrivacyPrimary
                                    active -> PrivacyPrimary.copy(alpha = 0.14f)
                                    failed -> failureColor.copy(alpha = 0.14f)
                                    else -> SurfaceMuted
                                },
                                CircleShape,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        when {
                            done -> GlyphIcon(Glyph.Check, Modifier.size(13.dp), Color.White)
                            active -> CircularProgressIndicator(
                                Modifier.size(14.dp),
                                color = PrivacyPrimary,
                                strokeWidth = 2.dp,
                            )
                            failed -> GlyphIcon(Glyph.Info, Modifier.size(13.dp), failureColor)
                            else -> Box(
                                Modifier
                                    .size(6.dp)
                                    .background(TextSecondary.copy(alpha = 0.38f), CircleShape),
                            )
                        }
                    }
                    if (index != steps.lastIndex) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .height(34.dp)
                                .background(
                                    if (done) PrivacyPrimary.copy(alpha = 0.70f)
                                    else SurfaceMuted,
                                ),
                        )
                    }
                }
                Spacer(Modifier.width(Space.x4))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(top = Space.x1),
                ) {
                    Text(
                        title,
                        color = when {
                            failed -> ErrorRed
                            done || active -> TextTertiary
                            else -> TextSecondary
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(Space.x1))
                    Text(
                        description,
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
        }
    }
}

@Composable
private fun PrivacyClaimRow(
    transfer: PrivacyClaimableTransfer,
    enabled: Boolean = true,
    onClaim: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SurfaceBase, RoundedCornerShape(Radius.md))
            .clickable(enabled = enabled, onClick = onClaim)
            .padding(horizontal = Space.x5, vertical = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .background(PrivacyPrimary.copy(alpha = 0.11f), RoundedCornerShape(Radius.md)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(Glyph.Download, Modifier.size(16.dp), PrivacyPrimary)
        }
        Spacer(Modifier.width(Space.x4))
        Column(Modifier.weight(1f)) {
            Text("${transfer.amountOct} OCT", color = TextTertiary, style = MaterialTheme.typography.labelMedium)
            Text("Epoch ${transfer.epoch}", color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        }
        Text(
            "Claim",
            color = if (enabled) PrivacyPrimary else TextSecondary.copy(alpha = 0.45f),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun PrivacyElapsedInfo(operation: PrivacyOperationState) {
    var now by remember(operation.id) { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(operation.id, operation.isTerminal, operation.completedAt) {
        if (operation.isTerminal) {
            now = operation.completedAt ?: System.currentTimeMillis()
        } else {
            while (true) {
                now = System.currentTimeMillis()
                delay(1_000)
            }
        }
    }
    val elapsedSeconds = ((operation.completedAt ?: now) - operation.startedAt)
        .coerceAtLeast(0L) / 1_000L
    val minutes = elapsedSeconds / 60L
    val seconds = elapsedSeconds % 60L
    PrivacyInfoLine(
        label = if (operation.isTerminal) "Completed in" else "Elapsed",
        value = if (minutes > 0L) "${minutes}m ${seconds}s" else "${seconds}s",
    )
}

@Composable
private fun PrivacyInfoLine(
    label: String,
    value: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(PrivacyPrimary.copy(alpha = 0.06f), RoundedCornerShape(Radius.md))
            .then(if (onAction != null) Modifier.clickable(onClick = onAction) else Modifier)
            .padding(horizontal = Space.x5, vertical = Space.x4),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
        Spacer(Modifier.width(Space.x5))
        Text(
            value,
            color = TextTertiary,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.width(Space.x3))
            Text(
                actionLabel,
                color = PrivacyPrimary,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.width(Space.x2))
            GlyphIcon(Glyph.External, Modifier.size(14.dp), PrivacyPrimary)
        }
    }
}

@Composable
private fun PrivacyPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(Radius.md)
    val containerHeight = if (compact) 44.dp else 56.dp
    val buttonHeight = if (compact) 42.dp else 54.dp
    Box(
        modifier = modifier
            .height(containerHeight)
            .clip(shape)
            .background(Color(0xFF00009E).copy(alpha = if (enabled) 1f else 0.30f)),
        contentAlignment = Alignment.TopCenter,
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(buttonHeight),
            shape = shape,
            colors = ButtonDefaults.buttonColors(
                containerColor = PrivacyPrimary,
                contentColor = Color.White,
                disabledContainerColor = PrivacyPrimary.copy(alpha = 0.30f),
                disabledContentColor = Color.White.copy(alpha = 0.55f),
            ),
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun PrivacyOperationStage.label(): String = name.lowercase().replaceFirstChar(Char::uppercase)

private fun formatPrivacyTimestamp(timestamp: Double): String {
    if (timestamp <= 0.0) return "Pending network time"
    return SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date((timestamp * 1000).toLong()))
}
