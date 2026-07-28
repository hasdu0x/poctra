package com.poctra.wallet.dapp.circle

import java.net.URLDecoder

private const val MaxCircleResourcePathLength = 512
private val UnsafeCirclePath = Regex("[\\\\?#\\u0000-\\u001f\\u007f]")
private val EncodedUnsafeCirclePath = Regex("%(?:2f|5c|00|25)", RegexOption.IGNORE_CASE)

internal fun normalizeCircleAssetPath(rawPath: String): String {
    val clean = rawPath.trim()
    if (clean.isBlank() || clean == "/") return "/index.html"
    val absolute = if (clean.startsWith('/')) clean else "/$clean"
    require(absolute.length <= MaxCircleResourcePathLength && !UnsafeCirclePath.containsMatchIn(absolute)) {
        "Circle resource path is invalid."
    }
    require(!EncodedUnsafeCirclePath.containsMatchIn(absolute)) { "Circle resource path is invalid." }
    val segments = absolute.split('/').filter(String::isNotBlank)
    segments.forEach { segment ->
        val decoded = runCatching { URLDecoder.decode(segment.replace("+", "%2B"), Charsets.UTF_8.name()) }
            .getOrElse { throw IllegalArgumentException("Circle resource path contains invalid encoding.") }
        require(decoded != "." && decoded != ".." && '/' !in decoded && '\\' !in decoded) {
            "Circle resource path traversal is not allowed."
        }
    }
    return if (segments.isEmpty()) "/index.html" else "/${segments.joinToString("/")}"
}
