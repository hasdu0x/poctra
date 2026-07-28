package com.poctra.wallet.ui

import android.graphics.Path as AndroidPath
import android.graphics.PathMeasure as AndroidPathMeasure
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

internal const val PoctraLaunchDisplayMillis = 1_080L
internal const val PoctraLaunchExitMillis = 140L

private const val TraceDelayMillis = 70L
private const val TraceDurationMillis = 560
private const val FillDelayMillis = 680L
private const val FillDurationMillis = 260
private const val LogoViewBoxSize = 64f

private val LaunchBackground = Color(0xFF080B09)
private val LaunchLogoFill = Color(0xFF10B981)
private val TraceEasing = CubicBezierEasing(0.30f, 0f, 0.45f, 1f)
private val BloomEasing = CubicBezierEasing(0.20f, 0.80f, 0.24f, 1f)

@Composable
internal fun PoctraLaunchScreen(modifier: Modifier = Modifier) {
    val traceProgress = remember { Animatable(0f) }
    val fillProgress = remember { Animatable(0f) }
    val letterPath = remember { poctraLetterPath() }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch {
                delay(TraceDelayMillis)
                traceProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = TraceDurationMillis,
                        easing = TraceEasing,
                    ),
                )
            }
            launch {
                delay(FillDelayMillis)
                fillProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = FillDurationMillis,
                        easing = BloomEasing,
                    ),
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(LaunchBackground),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier
                .size(120.dp)
                .semantics { contentDescription = "poctra" },
        ) {
            val logoSize = size.minDimension
            val scale = logoSize / LogoViewBoxSize
            val left = (size.width - logoSize) / 2f
            val top = (size.height - logoSize) / 2f

            withTransform({
                translate(left = left, top = top)
                scale(scaleX = scale, scaleY = scale, pivot = Offset.Zero)
            }) {
                drawPath(
                    path = letterPath,
                    color = LaunchLogoFill,
                    alpha = fillProgress.value,
                )
                drawPath(
                    path = trimmedPath(letterPath, traceProgress.value),
                    color = LaunchLogoFill,
                    alpha = 1f - fillProgress.value,
                    style = Stroke(
                        width = 1.8f,
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    ),
                )
            }
        }
    }
}

private fun trimmedPath(source: Path, progress: Float): Path {
    if (progress <= 0f) return Path()
    if (progress >= 1f) return source

    val sourcePath = source.asAndroidPath()
    val lengthMeasure = AndroidPathMeasure(sourcePath, false)
    var totalLength = 0f
    do {
        totalLength += lengthMeasure.length
    } while (lengthMeasure.nextContour())

    var remaining = totalLength * progress.coerceIn(0f, 1f)
    val destination = AndroidPath()
    val segmentMeasure = AndroidPathMeasure(sourcePath, false)
    do {
        val contourLength = segmentMeasure.length
        val stop = min(remaining, contourLength)
        if (stop > 0f) {
            segmentMeasure.getSegment(0f, stop, destination, true)
            remaining -= stop
        }
    } while (remaining > 0f && segmentMeasure.nextContour())
    return destination.asComposePath()
}

private fun poctraLetterPath(): Path = Path().apply {
    fillType = PathFillType.EvenOdd

    moveTo(34.626f, 12f)
    cubicTo(32f, 12f, 29.163f, 13.129f, 26.905f, 15.992f)
    lineTo(26.537f, 12.919f)
    lineTo(19.183f, 12.919f)
    lineTo(19.183f, 52f)
    lineTo(27.483f, 51.16f)
    lineTo(27.483f, 38.5f)
    cubicTo(29.163f, 40.391f, 31.343f, 41.389f, 34.101f, 41.389f)
    cubicTo(40.903f, 41.389f, 44.817f, 35.139f, 44.817f, 26.629f)
    cubicTo(44.817f, 17.673f, 41.902f, 12f, 34.626f, 12f)
    close()

    moveTo(32.053f, 18.12f)
    cubicTo(34.679f, 18.12f, 36.255f, 19.905f, 36.255f, 26.734f)
    cubicTo(36.255f, 32.906f, 34.521f, 35.296f, 31.58f, 35.296f)
    cubicTo(29.82f, 35.296f, 28.638f, 34.534f, 27.483f, 32.854f)
    lineTo(27.483f, 21.271f)
    cubicTo(28.664f, 19.354f, 30.109f, 18.12f, 32.053f, 18.12f)
    close()
}
