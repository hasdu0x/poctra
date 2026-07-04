package com.poctra.wallet.security

import com.poctra.wallet.data.canonicalizeOrigin

object DAppBridgePolicy {
    fun bridgeOrigin(
        currentPageUrl: String,
        requestedOrigin: String,
        fallbackDAppUrl: String,
    ): String? = listOf(currentPageUrl, requestedOrigin, fallbackDAppUrl)
        .firstNotNullOfOrNull(::canonicalWebOrigin)

    private fun canonicalWebOrigin(value: String): String? {
        val origin = canonicalizeOrigin(value).takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            DAppUrlPolicy.normalizeBrowserUrl(origin)
            val uri = java.net.URI(origin)
            val scheme = uri.scheme?.lowercase().orEmpty()
            val host = uri.host?.lowercase()?.removePrefix("www.")?.trim().orEmpty()
            if (scheme.isBlank() || host.isBlank()) null else buildString {
                append(scheme)
                append("://")
                append(host)
                if (uri.port >= 0) {
                    append(':')
                    append(uri.port)
                }
            }
        }.getOrNull()
    }
}
