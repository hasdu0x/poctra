package com.poctra.wallet.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.poctra.wallet.R
import com.poctra.wallet.data.DAppSession
import com.poctra.wallet.data.OctraToken
import com.poctra.wallet.data.TokenBalance

enum class ButtonVariant {
    Primary,
    Ghost,
    Danger,
}

@Composable
fun PoctraButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    variant: ButtonVariant = ButtonVariant.Primary,
    leadingGlyph: Glyph? = null,
    compact: Boolean = false,
    fullHeight: Boolean = false,
) {
    val shape = RoundedCornerShape(Radius.md)
    val visualCompact = !fullHeight && (compact || variant != ButtonVariant.Primary)
    val buttonHeight = if (visualCompact) 44.dp else 56.dp
    val keyEdgeHeight = if (visualCompact) 1.dp else 2.dp
    val contentIconSize = if (visualCompact) 16.dp else 18.dp
    val contentGap = if (visualCompact) Space.x3 else Space.x4
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed && enabled && !loading) 0.982f else 1f,
        animationSpec = tween(130, easing = FastOutSlowInEasing),
        label = "poctraButtonPressScale",
    )
    val buttonContentColor = when (variant) {
        ButtonVariant.Primary -> PrimaryContent
        ButtonVariant.Danger -> ErrorRed
        ButtonVariant.Ghost -> TextTertiary
    }
    val content: @Composable () -> Unit = {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(contentIconSize),
                    color = buttonContentColor,
                    strokeWidth = 2.dp,
                )
            } else {
                leadingGlyph?.let {
                    GlyphIcon(
                        glyph = it,
                        modifier = Modifier.size(contentIconSize),
                        color = buttonContentColor,
                    )
                    Spacer(Modifier.width(contentGap))
                }
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }

    val edgeColor = when (variant) {
        ButtonVariant.Primary -> Color(0xFF047857)
        ButtonVariant.Ghost -> BorderLine.copy(alpha = 0.85f)
        ButtonVariant.Danger -> ErrorRed.copy(alpha = 0.55f)
    }
    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
            }
            .height(buttonHeight)
            .clip(shape)
            .background(edgeColor.copy(alpha = if (enabled) 1f else 0.35f)),
        contentAlignment = Alignment.TopCenter,
    ) {
        when (variant) {
            ButtonVariant.Primary -> Button(
                onClick = onClick,
                enabled = enabled && !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight - keyEdgeHeight),
                shape = shape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SurfaceRaised,
                    contentColor = PrimaryContent,
                    disabledContainerColor = SurfaceRaised.copy(alpha = 0.34f),
                    disabledContentColor = PrimaryContent.copy(alpha = 0.5f),
                ),
                interactionSource = interactionSource,
                content = { content() },
            )

            ButtonVariant.Ghost -> OutlinedButton(
                onClick = onClick,
                enabled = enabled && !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight - keyEdgeHeight),
                shape = shape,
                border = BorderStroke(1.dp, BorderLine.copy(alpha = if (enabled) 1f else 0.45f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = SurfaceStrong,
                    contentColor = TextTertiary,
                    disabledContentColor = TextTertiary.copy(alpha = 0.35f),
                ),
                interactionSource = interactionSource,
                content = { content() },
            )

            ButtonVariant.Danger -> OutlinedButton(
                onClick = onClick,
                enabled = enabled && !loading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(buttonHeight - keyEdgeHeight),
                shape = shape,
                border = BorderStroke(1.dp, ErrorRed.copy(alpha = if (enabled) 0.7f else 0.25f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = SurfaceStrong,
                    contentColor = ErrorRed,
                    disabledContentColor = ErrorRed.copy(alpha = 0.35f),
                ),
                interactionSource = interactionSource,
                content = { content() },
            )
        }
    }
}

@Composable
fun PoctraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    isError: Boolean = false,
    supportingText: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
    leadingContent: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        placeholder = placeholder?.let {
            { Text(it, color = TextSecondary.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium) }
        },
        singleLine = singleLine,
        minLines = minLines,
        isError = isError,
        supportingText = supportingText?.let { { Text(it, style = MaterialTheme.typography.labelSmall) } },
        leadingIcon = leadingContent,
        trailingIcon = trailingContent,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
        shape = RoundedCornerShape(Radius.md),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TextTertiary,
            unfocusedTextColor = TextTertiary,
            focusedContainerColor = SurfaceStrong,
            unfocusedContainerColor = SurfaceStrong,
            focusedBorderColor = SurfaceRaised,
            unfocusedBorderColor = BorderLine,
            focusedLabelColor = SurfaceRaised,
            unfocusedLabelColor = TextSecondary,
            cursorColor = SurfaceRaised,
            errorBorderColor = ErrorRed,
            errorLabelColor = ErrorRed,
            errorSupportingTextColor = ErrorRed,
            focusedLeadingIconColor = SurfaceRaised,
            unfocusedLeadingIconColor = TextSecondary,
            focusedTrailingIconColor = SurfaceRaised,
            unfocusedTrailingIconColor = TextSecondary,
        ),
    )
}

@Composable
fun UrlGoButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(end = Space.x1)
            .size(22.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (enabled) SurfaceRaised.copy(alpha = 0.14f) else SurfaceMuted)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "Open dApp URL" },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            glyph = Glyph.Go,
            modifier = Modifier.size(11.dp),
            color = if (enabled) SurfaceRaised else TextSecondary.copy(alpha = 0.45f),
        )
    }
}

@Composable
fun AddressBookFieldButton(
    onClick: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .padding(end = Space.x1)
            .size(22.dp)
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (enabled) SurfaceRaised.copy(alpha = 0.14f) else SurfaceMuted)
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = "Choose from Address Book" },
        contentAlignment = Alignment.Center,
    ) {
        GlyphIcon(
            glyph = Glyph.AddressBook,
            modifier = Modifier.size(12.dp),
            color = if (enabled) SurfaceRaised else TextSecondary.copy(alpha = 0.45f),
        )
    }
}

@Composable
fun PinField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    modifier: Modifier = Modifier,
) {
    PoctraTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter(Char::isDigit).take(6)) },
        label = label,
        modifier = modifier,
        keyboardType = KeyboardType.NumberPassword,
        password = true,
        isError = error != null,
        supportingText = error,
    )
}

@Composable
fun StateBanner(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = true,
    compact: Boolean = false,
    solid: Boolean = false,
) {
    val color = if (isError) ErrorRed else InfoBlue
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (solid) SurfaceStrong else color.copy(alpha = if (compact) 0.10f else 0.12f))
            .border(1.dp, color.copy(alpha = if (compact) 0.28f else 0.36f), RoundedCornerShape(Radius.sm))
            .padding(horizontal = if (compact) Space.x5 else Space.x6, vertical = if (compact) Space.x3 else Space.x5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 22.dp else 28.dp)
                .clip(RoundedCornerShape(Radius.md))
                .background(color.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(
                glyph = Glyph.Info,
                modifier = Modifier.size(if (compact) 14.dp else 16.dp),
                color = color,
            )
        }
        Spacer(Modifier.width(if (compact) Space.x4 else Space.x5))
        Text(
            text = text,
            color = if (isError) ErrorRed else TextTertiary,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = if (compact) 2 else 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SecurityOptionCard(
    title: String,
    subtitle: String,
    glyph: Glyph,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceStrong)
            .border(
                width = if (focused) 2.dp else 1.dp,
                color = if (focused) SurfaceRaised else BorderLine.copy(alpha = if (enabled) 1f else 0.55f),
                shape = RoundedCornerShape(Radius.md),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .onFocusChanged { focused = it.isFocused }
            .padding(Space.x7),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(Radius.sm))
                .background(if (enabled) SurfaceRaised.copy(alpha = 0.18f) else SurfaceMuted),
            contentAlignment = Alignment.Center,
        ) {
            GlyphIcon(glyph = glyph, modifier = Modifier.size(24.dp), color = if (enabled) SurfaceRaised else TextSecondary)
        }
        Spacer(Modifier.width(Space.x6))
        Column(Modifier.weight(1f)) {
            Text(title, color = if (enabled) TextTertiary else TextSecondary, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(Space.x2))
            Text(subtitle, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun IconCircleButton(
    glyph: Glyph,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    buttonSize: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled) 0.94f else 1f,
        animationSpec = tween(120, easing = FastOutSlowInEasing),
        label = "iconButtonScale",
    )
    IconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .size(buttonSize)
            .semantics { this.contentDescription = contentDescription }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(Radius.md))
            .background(if (enabled) SurfaceStrong else SurfaceMuted)
            .border(1.dp, BorderLine.copy(alpha = 0.82f), RoundedCornerShape(Radius.md)),
    ) {
        GlyphIcon(
            glyph = glyph,
            modifier = Modifier.size(iconSize),
            color = if (enabled) TextTertiary else TextSecondary.copy(alpha = 0.45f),
        )
    }
}

@Composable
fun InlineListAction(
    text: String,
    onClick: () -> Unit,
    glyph: Glyph = Glyph.ChevronDown,
    loading: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(BorderLine.copy(alpha = 0.62f)),
        )
        Row(
            modifier = Modifier
                .padding(horizontal = Space.x3)
                .clip(RoundedCornerShape(Radius.pill))
                .clickable(enabled = enabled && !loading, onClick = onClick)
                .padding(horizontal = Space.x4, vertical = Space.x2),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    color = SurfaceRaised,
                    strokeWidth = 1.5.dp,
                )
            } else {
                GlyphIcon(
                    glyph,
                    modifier = Modifier.size(12.dp),
                    color = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.45f),
                )
            }
            Spacer(Modifier.width(Space.x2))
            Text(
                text,
                color = if (enabled) TextSecondary else TextSecondary.copy(alpha = 0.45f),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(BorderLine.copy(alpha = 0.62f)),
        )
    }
}

@Composable
fun NetworkBadge(text: String, state: String? = null, modifier: Modifier = Modifier) {
    val resolvedState = state ?: when (text.trim().lowercase()) {
        "confirmed", "completed", "connected", "success", "active", "open" -> "connected"
        "failed", "rejected", "dropped", "error", "closed" -> "failed"
        else -> null
    }
    val borderColor = when (resolvedState) {
        "connected" -> SurfaceRaised
        "failed" -> ErrorRed
        else -> InfoBlue
    }
    Text(
        text = text.trim().replaceFirstChar { it.uppercase() },
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(borderColor.copy(alpha = 0.10f))
            .border(1.dp, borderColor.copy(alpha = 0.30f), RoundedCornerShape(Radius.md))
            .padding(horizontal = Space.x6, vertical = Space.x3),
        color = borderColor,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
fun TokenRow(token: TokenBalance, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.fillMaxWidth(),
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
                if (token.isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = SurfaceRaised, strokeWidth = 2.dp)
                } else {
                    Image(
                        painter = painterResource(R.drawable.octra_logo),
                        contentDescription = "Octra",
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
            Spacer(Modifier.width(Space.x6))
            Column(Modifier.weight(1f)) {
                Text(token.name, color = TextTertiary, fontWeight = FontWeight.SemiBold)
                Text(token.symbol, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (token.isLoading) "--" else token.amount,
                    color = TextTertiary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Text(token.error ?: token.fiatEstimate, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
fun OctraTokenRow(
    token: OctraToken,
    onSend: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onSend),
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
                    .background(SurfaceMuted),
                contentAlignment = Alignment.Center,
            ) {
                Text(token.symbol.take(2).uppercase(), color = SurfaceRaised, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
            Spacer(Modifier.width(Space.x6))
            Column(Modifier.weight(1f)) {
                Text(token.name, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(token.address, color = TextSecondary, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(token.formattedBalance, color = TextTertiary, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text(token.symbol, color = TextSecondary, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(Space.x3))
                Text(
                    "Remove",
                    modifier = Modifier
                        .clip(RoundedCornerShape(Radius.pill))
                        .clickable(onClick = onRemove)
                        .padding(horizontal = Space.x4, vertical = Space.x2),
                    color = ErrorRed,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
fun DAppRow(
    session: DAppSession,
    onRevoke: () -> Unit,
    onPermissionsClick: (() -> Unit)? = null,
    riskLabels: List<String> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(Radius.md),
        colors = CardDefaults.cardColors(containerColor = SurfaceStrong),
        border = BorderStroke(1.dp, BorderLine),
    ) {
        Column(
            modifier = Modifier.padding(Space.x7),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(Radius.md))
                        .background(SurfaceMuted),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(session.name.take(1).uppercase(), color = SurfaceRaised, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(Space.x6))
                Column(Modifier.weight(1f)) {
                    Text(
                        session.name,
                        color = TextTertiary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(Space.x2))
                    Text(
                        "Connected ${formatDate(session.connectedAt)}",
                        color = TextSecondary,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                    )
                }
            }
            Spacer(Modifier.height(Space.x6))
            Text(
                session.url,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(Radius.sm))
                    .background(SurfaceBase)
                    .border(1.dp, BorderLine, RoundedCornerShape(Radius.sm))
                    .padding(horizontal = Space.x5, vertical = Space.x4),
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.x5))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
                val permissionCount = session.permissions.size
                DAppMetaChip(session.network.ifBlank { "network" }.lowercase())
                DAppMetaChip(
                    text = if (permissionCount == 1) "1 permission" else "$permissionCount permissions",
                    onClick = onPermissionsClick,
                )
            }
            if (riskLabels.isNotEmpty()) {
                Spacer(Modifier.height(Space.x4))
                Column(verticalArrangement = Arrangement.spacedBy(Space.x3)) {
                    riskLabels.take(2).forEach { label ->
                        DAppRiskLabel(label)
                    }
                }
            }
            Spacer(Modifier.height(Space.x4))
            Text(
                text = "Wallet ${shortAddress(session.walletAddress)}",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(Space.x6))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                DAppRevokeAction(onRevoke)
            }
        }
    }
}

@Composable
private fun DAppRevokeAction(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(ErrorRed.copy(alpha = 0.12f))
            .border(1.dp, ErrorRed.copy(alpha = 0.42f), RoundedCornerShape(Radius.sm))
            .clickable(onClick = onClick)
            .padding(horizontal = Space.x3, vertical = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.Revoke, modifier = Modifier.size(13.dp), color = ErrorRed)
        Spacer(Modifier.width(Space.x2))
        Text(
            "Revoke",
            color = ErrorRed,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun DAppRiskLabel(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Radius.sm))
            .background(Warning.copy(alpha = 0.14f))
            .border(1.dp, Warning.copy(alpha = 0.45f), RoundedCornerShape(Radius.sm))
            .padding(horizontal = Space.x4, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlyphIcon(Glyph.Info, modifier = Modifier.size(13.dp), color = Warning)
        Spacer(Modifier.width(Space.x3))
        Text(text, color = TextTertiary, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DAppMetaChip(text: String, onClick: (() -> Unit)? = null) {
    Text(
        text = text,
        modifier = Modifier
            .clip(RoundedCornerShape(Radius.sm))
            .background(if (onClick != null) SurfaceRaised.copy(alpha = 0.14f) else SurfaceMuted)
            .border(
                1.dp,
                if (onClick != null) SurfaceRaised.copy(alpha = 0.45f) else BorderLine,
                RoundedCornerShape(Radius.sm),
            )
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(onClick = onClick)
                        .semantics { contentDescription = "$text, view details" }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = Space.x4, vertical = Space.x2),
        color = if (onClick != null) SurfaceRaised else TextSecondary,
        style = MaterialTheme.typography.labelSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

fun shortAddress(address: String): String {
    if (address.length <= 16) return address
    return "${address.take(8)}...${address.takeLast(4)}"
}

fun headerShortAddress(address: String): String {
    if (address.length <= 24) return address
    return "${address.take(10)}...${address.takeLast(7)}"
}

private fun formatDate(timestamp: Long): String {
    if (timestamp <= 0) return "unknown"
    return android.text.format.DateFormat.format("MMM d, HH:mm", timestamp).toString()
}
