package com.poctra.wallet.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.poctra.wallet.R

var SurfaceBase by mutableStateOf(Color(0xFFF6F8F6))
var TextSecondary by mutableStateOf(Color(0xFF68716B))
var TextTertiary by mutableStateOf(Color(0xFF111512))
var SurfaceRaised by mutableStateOf(Color(0xFF10B981))
var SurfaceStrong by mutableStateOf(Color(0xFFFFFFFF))
var SurfaceMuted by mutableStateOf(Color(0xFFEDF1EE))
var BorderLine by mutableStateOf(Color(0xFFDDE4DF))
var ErrorRed by mutableStateOf(Color(0xFFD34F4F))
var Warning by mutableStateOf(Color(0xFFB7791F))
var InfoBlue by mutableStateOf(Color(0xFF3F7FE8))
var PrimaryContent by mutableStateOf(Color(0xFF07110D))

object Space {
    val x1 = 2.dp
    val x2 = 4.dp
    val x3 = 6.dp
    val x4 = 8.dp
    val x5 = 10.dp
    val x6 = 12.dp
    val x7 = 16.dp
    val x8 = 20.dp
}

object Radius {
    val xs = 8.dp
    val sm = 8.dp
    val md = 8.dp
    val pill = 8.dp
}

private val FiraCode = FontFamily(
    Font(R.font.fira_code_regular, FontWeight.Normal),
    Font(R.font.fira_code_medium, FontWeight.Medium),
    Font(R.font.fira_code_semibold, FontWeight.SemiBold),
    Font(R.font.fira_code_bold, FontWeight.Bold),
)

private val PoctraTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 23.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 34.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 50.sp,
        letterSpacing = 0.sp,
    ),
)

@Composable
fun PoctraTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit,
) {
    val palette = if (darkTheme) {
        Palette(
            base = Color(0xFF0D100E),
            secondary = Color(0xFFA2ABA5),
            text = Color(0xFFF4F7F5),
            raised = Color(0xFF10B981),
            strong = Color(0xFF171B18),
            muted = Color(0xFF222824),
            border = Color(0xFF303832),
            error = Color(0xFFFF7373),
            warning = Color(0xFFF2BE54),
            info = Color(0xFF72A4F7),
        )
    } else {
        Palette(
            base = Color(0xFFF6F8F6),
            secondary = Color(0xFF68716B),
            text = Color(0xFF111512),
            raised = Color(0xFF10B981),
            strong = Color(0xFFFFFFFF),
            muted = Color(0xFFEDF1EE),
            border = Color(0xFFDDE4DF),
            error = Color(0xFFD34F4F),
            warning = Color(0xFFB7791F),
            info = Color(0xFF3F7FE8),
        )
    }
    val primaryContent = if (darkTheme) Color(0xFF07110D) else Color.White
    SideEffect {
        SurfaceBase = palette.base
        TextSecondary = palette.secondary
        TextTertiary = palette.text
        SurfaceRaised = palette.raised
        SurfaceStrong = palette.strong
        SurfaceMuted = palette.muted
        BorderLine = palette.border
        ErrorRed = palette.error
        Warning = palette.warning
        InfoBlue = palette.info
        PrimaryContent = primaryContent
    }

    val colorScheme = if (darkTheme) {
        darkColorScheme(
            primary = palette.raised,
            onPrimary = primaryContent,
            background = palette.base,
            onBackground = palette.text,
            surface = palette.strong,
            onSurface = palette.text,
            surfaceVariant = palette.muted,
            onSurfaceVariant = palette.secondary,
            secondary = palette.info,
            onSecondary = palette.text,
            error = palette.error,
            onError = palette.text,
            outline = palette.border,
        )
    } else {
        lightColorScheme(
            primary = palette.raised,
            onPrimary = primaryContent,
            background = palette.base,
            onBackground = palette.text,
            surface = palette.strong,
            onSurface = palette.text,
            surfaceVariant = palette.muted,
            onSurfaceVariant = palette.secondary,
            secondary = palette.info,
            onSecondary = Color.White,
            error = palette.error,
            onError = Color.White,
            outline = palette.border,
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PoctraTypography,
        content = content,
    )
}

private data class Palette(
    val base: Color,
    val secondary: Color,
    val text: Color,
    val raised: Color,
    val strong: Color,
    val muted: Color,
    val border: Color,
    val error: Color,
    val warning: Color,
    val info: Color,
)
