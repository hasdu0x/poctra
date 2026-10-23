package com.poctra.wallet.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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

var SurfaceBase by mutableStateOf(Color(0xFFE9EFEB))
var TextSecondary by mutableStateOf(Color(0xFF55635A))
var TextTertiary by mutableStateOf(Color(0xFF111612))
var SurfaceRaised by mutableStateOf(Color(0xFF0B7A55))
var SurfaceStrong by mutableStateOf(Color(0xFFFFFFFF))
var SurfaceMuted by mutableStateOf(Color(0xFFDCE5DE))
var BorderLine by mutableStateOf(Color(0xFFC0CCC3))
var ControlOutline by mutableStateOf(Color(0xFF84958A))
var ErrorRed by mutableStateOf(Color(0xFFC43D4D))
var Warning by mutableStateOf(Color(0xFF966316))
var InfoBlue by mutableStateOf(Color(0xFF2467C9))
var Success by mutableStateOf(Color(0xFF0B7A55))
var PrivacyPrimary by mutableStateOf(Color(0xFF3434C5))
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
    val x9 = 24.dp
    val x10 = 32.dp
    val x11 = 40.dp
    val x12 = 48.dp
}

object Radius {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val pill = 999.dp
}

private val FiraCode = FontFamily(
    Font(R.font.fira_code_regular, FontWeight.Normal),
    Font(R.font.fira_code_medium, FontWeight.Medium),
    Font(R.font.fira_code_semibold, FontWeight.SemiBold),
    Font(R.font.fira_code_bold, FontWeight.Bold),
)

private val PoctraTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Bold,
        fontSize = 52.sp,
        lineHeight = 60.sp,
        letterSpacing = (-0.5).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Bold,
        fontSize = 40.sp,
        lineHeight = 48.sp,
        letterSpacing = (-0.25).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = 0.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 32.sp,
        letterSpacing = 0.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = 0.sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 42.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.sp,
    ),
)

@Composable
fun PoctraTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    val palette = Palette(
        base = Color(0xFF0B0E11),      // Binance deepest background
        secondary = Color(0xFF848E9C), // Binance secondary text
        text = Color(0xFFEAECEF),      // Binance primary text
        raised = Color(0xFF0ECB81),    // Poctra green accent (keep brand)
        strong = Color(0xFF1E2026),    // Binance card surface
        muted = Color(0xFF181A20),     // Binance secondary surface
        border = Color(0xFF2B2F36),    // Binance border
        controlOutline = Color(0xFF474D57), // Binance input outline
        error = Color(0xFFF6465D),     // Binance red
        warning = Color(0xFFF0B90B),   // Binance yellow (warning)
        info = Color(0xFF1890FF),      // Info blue
    )
    val primaryContent = Color(0xFF0B0E11)
    SideEffect {
        SurfaceBase = palette.base
        TextSecondary = palette.secondary
        TextTertiary = palette.text
        SurfaceRaised = palette.raised
        SurfaceStrong = palette.strong
        SurfaceMuted = palette.muted
        BorderLine = palette.border
        ControlOutline = palette.controlOutline
        ErrorRed = palette.error
        Warning = palette.warning
        InfoBlue = palette.info
        Success = Color(0xFF0ECB81)
        PrivacyPrimary = Color(0xFF7B61FF)
        PrimaryContent = primaryContent
    }

    val colorScheme = darkColorScheme(
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
    val controlOutline: Color,
    val error: Color,
    val warning: Color,
    val info: Color,
)
