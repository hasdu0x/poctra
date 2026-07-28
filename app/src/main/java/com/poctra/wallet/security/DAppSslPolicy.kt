package com.poctra.wallet.security

import java.net.URI

object DAppSslPolicy {
    fun affectsVisiblePage(pageUrl: String?, failedUrl: String?): Boolean {
        val pageHost = host(pageUrl)
        val failedHost = host(failedUrl)
        return pageHost.isBlank() || failedHost.isBlank() || pageHost.equals(failedHost, ignoreCase = true)
    }

    private fun host(value: String?): String = runCatching {
        URI(value.orEmpty()).host.orEmpty().lowercase()
    }.getOrDefault("")
}
