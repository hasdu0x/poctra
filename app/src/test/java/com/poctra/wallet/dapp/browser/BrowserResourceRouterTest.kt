package com.poctra.wallet.dapp.browser

import com.poctra.wallet.dapp.rfcO1InpageProviderPath
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowserResourceRouterTest {
    private val token = "bridge-token"

    @Test
    fun `common web remains owned by WebView`() {
        assertEquals(BrowserResourceHandler.CommonWeb, route("https://example.com/app.js"))
    }

    @Test
    fun `circle gateway and direct oct resources use Circle handler`() {
        assertEquals(BrowserResourceHandler.Circle, route("https://octra-circle.local/oct/address/index.html"))
        val address = "oct" + "A".repeat(44)
        assertEquals(BrowserResourceHandler.Circle, route("oct://$address/index.html"))
    }

    @Test
    fun `restricted Circle blocks external subresources without capturing main-frame web navigation`() {
        assertEquals(
            BrowserResourceHandler.Circle,
            route("https://cdn.example/image.png", activeCircle = true, mainFrame = false, gatewayAllowed = false),
        )
        assertEquals(
            BrowserResourceHandler.CommonWeb,
            route("https://example.com/", activeCircle = true, mainFrame = true, gatewayAllowed = false),
        )
    }

    @Test
    fun `Octra RFC provider resource has priority`() {
        val path = rfcO1InpageProviderPath(token)
        assertEquals(BrowserResourceHandler.OctraRfc, route("https://devnet.octra.id$path"))
    }

    private fun route(
        url: String,
        activeCircle: Boolean = false,
        mainFrame: Boolean = false,
        gatewayAllowed: Boolean = true,
    ) = routeBrowserResource(
        url = url,
        method = "GET",
        bridgeToken = token,
        circleBrowserHost = "octra-circle.local",
        hasActiveCircle = activeCircle,
        isMainFrame = mainFrame,
        activeCircleAllowsGatewayRequests = gatewayAllowed,
    )
}
