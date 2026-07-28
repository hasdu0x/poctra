package com.poctra.wallet.dapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DAppBrowserCompatibilityTest {
    @Test
    fun `wallet user agent preserves webview compatibility markers`() {
        val result = walletBrowserUserAgent(
            "Mozilla/5.0 (Linux; Android 14; Device Build/UP1A; wv) " +
                "AppleWebKit/537.36 Version/4.0 Chrome/149.0.0.0 Mobile Safari/537.36",
            "1.0.0",
        )

        assertTrue(result.contains("; wv", ignoreCase = true))
        assertTrue(result.contains("Version/4.0", ignoreCase = true))
        assertTrue(result.endsWith("WebView PoctraWallet/1.0.0"))
    }

    @Test
    fun `wallet user agent marker is idempotent`() {
        val userAgent = "Mozilla/5.0 Chrome/149 Mobile Safari/537.36 WebView PoctraWallet/1.0.0"

        assertEquals(userAgent, walletBrowserUserAgent(userAgent, "1.0.0"))
    }
}
