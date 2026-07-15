package com.poctra.wallet.data

import java.net.URI

object OctraIdentityName {
    private val domainSuffixes = listOf(".octra", ".oct", ".id")

    fun isValidDomain(value: String): Boolean {
        val normalized = value.trim().lowercase()
        val suffix = domainSuffixes.firstOrNull { normalized.endsWith(it) } ?: return false
        return isValidPath(normalized.removeSuffix(suffix))
    }

    fun isValidPath(value: String): Boolean {
        val parts = value.trim().lowercase().split('.')
        return when (parts.size) {
            1 -> isValidLabel(parts[0], minimumLength = 3)
            2 -> isValidLabel(parts[0], minimumLength = 1) &&
                isValidLabel(parts[1], minimumLength = 3)
            else -> false
        }
    }

    fun fromWebUrl(value: String): String? {
        val uri = runCatching { URI(value.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") return null
        if (uri.userInfo != null || uri.port != -1) return null
        val host = uri.host?.lowercase()?.takeIf { it.endsWith(".oct") && isValidDomain(it) } ?: return null
        return buildString {
            append(host)
            append(uri.rawPath.orEmpty())
            uri.rawQuery?.let { append('?').append(it) }
            uri.rawFragment?.let { append('#').append(it) }
        }
    }

    private fun isValidLabel(value: String, minimumLength: Int): Boolean {
        if (value.length !in minimumLength..63) return false
        if (!value.first().isAsciiLetterOrDigit() || !value.last().isAsciiLetterOrDigit()) return false
        return value.all { it.isAsciiLetterOrDigit() || it == '-' }
    }

    private fun Char.isAsciiLetterOrDigit(): Boolean = this in 'a'..'z' || this in '0'..'9'
}
