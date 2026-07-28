package com.poctra.wallet.dapp

import java.net.URI

/** Hosts intentionally excluded from Poctra's injected EVM wallet support. */
object EvmDAppSupportPolicy {
    private val unsupportedDomains = setOf("uniswap.org")

    fun isSupported(origin: String): Boolean {
        val host = runCatching { URI(origin).host?.lowercase().orEmpty() }.getOrDefault("")
        if (host.isBlank()) return false
        return unsupportedDomains.none { domain -> host == domain || host.endsWith(".$domain") }
    }
}
