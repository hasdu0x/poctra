package com.poctra.wallet.dapp

import java.net.URI

internal const val POCTRA_PROVIDER_UUID = "8f286d3e-891a-4cc4-98a5-48dc905b36bb"
internal const val POCTRA_PROVIDER_NAME = "Poctra Wallet"
internal const val POCTRA_PROVIDER_RDNS = "app.poctra.wallet"
internal const val POCTRA_PROVIDER_VERSION = "1.0.0"
internal const val POCTRA_PROVIDER_HOMEPAGE = "https://poctra.github.io/"
internal const val POCTRA_PROVIDER_ICON =
    "data:image/png;base64,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"

private val providerIconPattern = Regex("""icon:\s*"data:image/[^"]+"""")

internal fun replaceProviderIconMetadata(source: String): String =
    providerIconPattern.replaceFirst(source, "icon: \"$POCTRA_PROVIDER_ICON\"")

/** Hosts whose RFC-O-1 provider is installed after navigation starts. */
internal fun requiresNavigationLifecycleProvider(url: String): Boolean {
    val host = runCatching { URI(url).host?.lowercase() }.getOrNull() ?: return false
    return host == "octra.id" || host.endsWith(".octra.id")
}

internal fun rfcO1InpageProviderPath(bridgeToken: String): String =
    "/.well-known/poctra/rfc-o-1-$bridgeToken.js"

internal fun isRfcO1InpageProviderRequest(url: String, bridgeToken: String): Boolean {
    val uri = runCatching { URI(url) }.getOrNull() ?: return false
    return uri.scheme.equals("https", ignoreCase = true) &&
        requiresNavigationLifecycleProvider(url) &&
        uri.rawQuery == null &&
        uri.rawFragment == null &&
        uri.path == rfcO1InpageProviderPath(bridgeToken)
}
