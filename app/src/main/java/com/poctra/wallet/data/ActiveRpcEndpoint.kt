package com.poctra.wallet.data

import java.net.URI

internal class ActiveRpcEndpoint(
    private val label: String,
) {
    @Volatile
    private var configuredUrl: String? = null

    fun configure(url: String): String {
        val clean = url.trim().trimEnd('/')
        require(clean.isNotBlank()) { "$label RPC URL is required." }
        val uri = runCatching { URI(clean) }.getOrNull()
        require(uri?.scheme?.lowercase() in setOf("http", "https") && !uri?.host.isNullOrBlank()) {
            "$label RPC URL must be a valid HTTP or HTTPS endpoint."
        }
        configuredUrl = clean
        return clean
    }

    fun requireUrl(): String =
        configuredUrl ?: throw IllegalStateException(
            "$label RPC is not configured. Select an active network in Settings and try again.",
        )
}
