package com.poctra.wallet.dapp

import com.poctra.wallet.data.canonicalizeOrigin
import java.net.URI

/**
 * Binds wallet requests to the top-level page that WebView has committed.
 * A navigation clears the previous binding so a new document cannot reuse it.
 */
class DAppOriginBinding(initialUrl: String? = null) {
    @Volatile
    private var committedOrigin: String = ""

    @Volatile
    private var loadingOrigin: String = supportedWebOrigin(initialUrl)

    fun onNavigationStarted(url: String?) {
        val origin = supportedWebOrigin(url)
        // Unsupported main-frame schemes are handled by the browser policy.
        // Do not let transient about:/blob: callbacks erase a trusted web origin.
        if (origin.isBlank()) return
        committedOrigin = ""
        loadingOrigin = origin
    }

    fun onPageCommitted(url: String?) {
        val origin = supportedWebOrigin(url)
        if (origin.isBlank()) return
        committedOrigin = origin
        loadingOrigin = origin
    }

    fun resolve(currentUrl: String?): String {
        val current = supportedWebOrigin(currentUrl)
        val active = loadingOrigin
        val committed = committedOrigin
        return when {
            active.isNotBlank() && (current.isBlank() || current == active) -> active
            committed.isNotBlank() && (current.isBlank() || current == committed) -> committed
            else -> ""
        }
    }

    /** Native main-frame origin used only as a fallback while WebView.url is unavailable. */
    fun trustedOrigin(): String = committedOrigin.ifBlank { loadingOrigin }

    private fun supportedWebOrigin(value: String?): String {
        val origin = canonicalizeOrigin(value.orEmpty())
        if (origin.isBlank()) return ""
        return runCatching {
            val scheme = URI(origin).scheme?.lowercase()
            origin.takeIf { scheme == "https" || scheme == "http" }.orEmpty()
        }.getOrDefault("")
    }
}
