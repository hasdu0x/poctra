package com.poctra.wallet.dapp.browser

import com.poctra.wallet.dapp.isRfcO1InpageProviderRequest
import java.net.URI

internal enum class BrowserResourceHandler {
    CommonWeb,
    Circle,
    OctraRfc,
}

/** Routes only network resources. Octra/EVM wallet calls use [PoctraProviderBridge]. */
internal fun routeBrowserResource(
    url: String,
    method: String,
    bridgeToken: String,
    circleBrowserHost: String,
    hasActiveCircle: Boolean,
    isMainFrame: Boolean,
    activeCircleAllowsGatewayRequests: Boolean,
): BrowserResourceHandler {
    if (method.equals("GET", ignoreCase = true) && isRfcO1InpageProviderRequest(url, bridgeToken)) {
        return BrowserResourceHandler.OctraRfc
    }

    val uri = runCatching { URI(url) }.getOrNull()
    val host = uri?.host.orEmpty().lowercase()
    val isDirectCircle = uri?.scheme.equals("oct", ignoreCase = true) && isOctraAddress(host)
    val isCircleGateway = host == circleBrowserHost || host.endsWith(".$circleBrowserHost")
    val mustBlockExternalCircleSubresource = hasActiveCircle &&
        !isMainFrame &&
        !activeCircleAllowsGatewayRequests

    return if (isDirectCircle || isCircleGateway || mustBlockExternalCircleSubresource) {
        BrowserResourceHandler.Circle
    } else {
        BrowserResourceHandler.CommonWeb
    }
}

private fun isOctraAddress(value: String): Boolean =
    value.startsWith("oct") && value.length == OctraAddressLength

private const val OctraAddressLength = 47
