package com.poctra.wallet.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
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

var SurfaceBase by mutableStateOf(Color(0xFFF5F4ED))
var TextSecondary by mutableStateOf(Color(0xFF757571))
var TextTertiary by mutableStateOf(Color(0xFF000000))
var SurfaceRaised by mutableStateOf(Color(0xFF10B981))
var SurfaceStrong by mutableStateOf(Color(0xFFFFFFFF))
var SurfaceMuted by mutableStateOf(Color(0xFFEAE8DF))
var BorderLine by mutableStateOf(Color(0xFFE0DED3))
var ErrorRed by mutableStateOf(Color(0xFFD84D4D))
var Warning by mutableStateOf(Color(0xFFB7791F))
var InfoBlue by mutableStateOf(Color(0xFF4F8DF7))
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
        letterSpacing = 0.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        letterSpacing = 0.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        letterSpacing = 0.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        letterSpacing = 0.sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = FiraCode,
        fontWeight = FontWeight.Bold,
        fontSize = 44.sp,
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
            base = Color(0xFF0F100E),
            secondary = Color(0xFF9DA199),
            text = Color(0xFFF7F7F2),
            raised = Color(0xFF10B981),
            strong = Color(0xFF191B18),
            muted = Color(0xFF262921),
            border = Color(0xFF30352B),
            error = Color(0xFFFF7676),
            warning = Color(0xFFFFC857),
            info = Color(0xFF74A7FF),
        )
    } else {
        Palette(
            base = Color(0xFFF5F4ED),
            secondary = Color(0xFF757571),
            text = Color(0xFF000000),
            raised = Color(0xFF10B981),
            strong = Color(0xFFFFFFFF),
            muted = Color(0xFFEAE8DF),
            border = Color(0xFFE0DED3),
            error = Color(0xFFD84D4D),
            warning = Color(0xFFB7791F),
            info = Color(0xFF4F8DF7),
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

    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = palette.raised,
            onPrimary = primaryContent,
            background = palette.base,
            onBackground = palette.text,
            surface = palette.base,
            onSurface = palette.text,
            surfaceVariant = palette.muted,
            onSurfaceVariant = palette.secondary,
            secondary = palette.strong,
            onSecondary = palette.text,
            error = palette.error,
            onError = palette.strong,
        ),
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
