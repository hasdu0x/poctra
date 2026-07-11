package com.poctra.wallet.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun WalletIdenticonView(
    svg: String,
    modifier: Modifier = Modifier,
    size: Dp = 34.dp,
) {
    val spec = remember(svg) { WalletIdenticonSpec.fromSvg(svg) }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(Radius.md))
            .background(if (spec.letter != null) SurfaceMuted else spec.background)
            .border(1.dp, BorderLine.copy(alpha = 0.72f), RoundedCornerShape(Radius.md)),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val background = if (spec.letter != null) SurfaceMuted else spec.background
            val primary = if (spec.letter != null) TextSecondary else spec.primary
            val secondary = if (spec.letter != null) BorderLine else spec.secondary
            val corner = CornerRadius(8.dp.toPx(), 8.dp.toPx())
            drawRoundRect(
                color = background,
                topLeft = Offset.Zero,
                size = this.size,
                cornerRadius = corner,
            )
            if (spec.letter == null) {
                val gap = this.size.width * 0.075f
                val cell = (this.size.width - gap * 6f) / 5f
                spec.cells.forEachIndexed { index, enabled ->
                    if (!enabled) return@forEachIndexed
                    val col = index % 5
                    val row = index / 5
                    val color = if ((index + col + row) % 4 == 0) secondary else primary
                    drawRoundRect(
                        color = color,
                        topLeft = Offset(gap + col * (cell + gap), gap + row * (cell + gap)),
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(cell * 0.28f, cell * 0.28f),
                    )
                }
            } else {
                drawCircle(
                    color = secondary.copy(alpha = 0.64f),
                    radius = this.size.width * 0.22f,
                    center = Offset(this.size.width * 0.78f, this.size.height * 0.20f),
                )
            }
        }
        spec.letter?.let { letter ->
            Text(
                text = letter,
                color = TextSecondary,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontSize = (size.value * 0.42f).sp,
                    lineHeight = (size.value * 0.46f).sp,
                ),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private data class WalletIdenticonSpec(
    val letter: String?,
    val background: Color,
    val primary: Color,
    val secondary: Color,
    val cells: List<Boolean>,
) {
    companion object {
        fun fromSvg(svg: String): WalletIdenticonSpec {
            val letter = svg.dataAttribute("letter")?.take(1)?.uppercase()
            val background = svg.dataAttribute("bg")?.toComposeColor() ?: Color(0xFF151515)
            val primary = svg.dataAttribute("primary")?.toComposeColor() ?: SurfaceRaised
            val secondary = svg.dataAttribute("secondary")?.toComposeColor() ?: InfoBlue
            val cells = svg.dataAttribute("cells")
                ?.map { it == '1' }
                ?.takeIf { it.size == 25 }
                ?: fallbackCells()
            return WalletIdenticonSpec(letter, background, primary, secondary, cells)
        }

        private fun fallbackCells(): List<Boolean> = listOf(
            true, false, true, false, true,
            false, true, true, true, false,
            true, true, false, true, true,
            false, true, true, true, false,
            true, false, true, false, true,
        )
    }
}

private fun String.dataAttribute(name: String): String? {
    return Regex("""data-$name="([^"]+)"""").find(this)?.groupValues?.getOrNull(1)
}

private fun String.toComposeColor(): Color {
    return runCatching { Color(AndroidColor.parseColor(this)) }.getOrDefault(SurfaceRaised)
}
