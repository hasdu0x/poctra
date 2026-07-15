package com.poctra.wallet.ui

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.Path
import android.util.Base64
import android.util.Xml
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.PathParser
import com.poctra.wallet.data.DAppCatalogRepository
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import kotlin.math.min

@Composable
fun DAppLogoView(
    logoSvg: String,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
) {
    val spec = remember(logoSvg) { DAppSvgSpec.parse(logoSvg) }
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceMuted),
        contentAlignment = Alignment.Center,
    ) {
        if (spec == null) {
            GlyphIcon(
                Glyph.Browser,
                modifier = Modifier.size(size * 0.48f),
                color = TextSecondary,
            )
        } else {
            Canvas(
                modifier = Modifier
                    .size(size)
                    .semantics { this.contentDescription = contentDescription },
            ) {
                val scale = min(this.size.width / spec.width, this.size.height / spec.height)
                val left = (this.size.width - spec.width * scale) / 2f
                val top = (this.size.height - spec.height * scale) / 2f
                drawContext.canvas.nativeCanvas.apply {
                    save()
                    translate(left, top)
                    scale(scale, scale)
                    spec.paths.forEach { item -> drawPath(item.path, item.paint) }
                    restore()
                }
            }
        }
    }
}

private data class DAppSvgSpec(
    val width: Float,
    val height: Float,
    val paths: List<DAppSvgPath>,
) {
    companion object {
        fun parse(dataUri: String): DAppSvgSpec? = runCatching {
            require(dataUri.startsWith(DAppCatalogRepository.SvgDataPrefix, ignoreCase = true))
            val encoded = dataUri.substring(DAppCatalogRepository.SvgDataPrefix.length)
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            require(bytes.size in 1..MaxSvgBytes)
            val svg = bytes.toString(Charsets.UTF_8)
            require(!svg.contains("<!DOCTYPE", ignoreCase = true))
            require(!svg.contains("<!ENTITY", ignoreCase = true))

            var viewBox = floatArrayOf(0f, 0f, 64f, 64f)
            val paths = mutableListOf<DAppSvgPath>()
            val parser = Xml.newPullParser().apply { setInput(StringReader(svg)) }
            while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                if (parser.eventType == XmlPullParser.START_TAG) {
                    when (parser.name.lowercase()) {
                        "svg" -> parser.attribute("viewBox")?.parseViewBox()?.let { viewBox = it }
                        "path" -> parser.parsePath()?.let(paths::add)
                        "script", "image", "foreignobject", "use" -> error("Unsupported SVG element.")
                    }
                }
                parser.next()
            }
            require(paths.isNotEmpty() && paths.size <= MaxSvgPaths)
            val width = viewBox[2]
            val height = viewBox[3]
            require(width > 0f && height > 0f)
            DAppSvgSpec(width, height, paths)
        }.getOrNull()

        private fun XmlPullParser.parsePath(): DAppSvgPath? {
            val pathData = attribute("d")?.takeIf { it.length <= MaxPathDataLength } ?: return null
            val fill = attribute("fill")?.takeUnless { it.equals("none", ignoreCase = true) } ?: "#000000"
            val color = runCatching { AndroidColor.parseColor(fill) }.getOrNull() ?: return null
            val path = PathParser.createPathFromPathData(pathData)
            if (attribute("fill-rule").equals("evenodd", ignoreCase = true)) {
                path.fillType = Path.FillType.EVEN_ODD
            }
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                this.color = color
            }
            return DAppSvgPath(path, paint)
        }

        private fun String.parseViewBox(): FloatArray? {
            val values = trim().split(Regex("[\\s,]+"))
                .mapNotNull(String::toFloatOrNull)
                .takeIf { it.size == 4 }
                ?: return null
            if (values[0] != 0f || values[1] != 0f) return null
            return values.toFloatArray()
        }

        private fun XmlPullParser.attribute(name: String): String? =
            getAttributeValue(null, name)?.trim()

        private const val MaxSvgBytes = 64 * 1024
        private const val MaxSvgPaths = 32
        private const val MaxPathDataLength = 32 * 1024
    }
}

private data class DAppSvgPath(
    val path: Path,
    val paint: Paint,
)
