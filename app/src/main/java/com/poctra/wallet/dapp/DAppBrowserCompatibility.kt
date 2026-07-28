package com.poctra.wallet.dapp

/**
 * Keeps Android's WebView compatibility tokens intact and appends a stable
 * wallet identifier. Modern dApps use the WebView tokens for viewport and
 * portal behavior, while EIP-6963 handles wallet discovery independently.
 */
internal fun walletBrowserUserAgent(defaultUserAgent: String, appVersion: String): String {
    val normalized = defaultUserAgent.trim()
    if (normalized.contains("PoctraWallet/", ignoreCase = true)) return normalized

    val safeVersion = appVersion.trim().ifBlank { "unknown" }
    return "$normalized WebView PoctraWallet/$safeVersion"
}
