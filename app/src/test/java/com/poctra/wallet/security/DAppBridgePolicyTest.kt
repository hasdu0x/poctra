package com.poctra.wallet.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DAppBridgePolicyTest {
    @Test
    fun allowsSameHttpsOriginAcrossPaths() {
        assertEquals(
            "https://octra.id",
            DAppBridgePolicy.bridgeOrigin(
                currentPageUrl = "https://octra.id/names?query=root",
                requestedOrigin = "https://octra.id",
                fallbackDAppUrl = "https://octra.id",
            ),
        )
    }

    @Test
    fun allowsLocalDevelopmentHttpOrigin() {
        assertEquals(
            "http://127.0.0.1:5173",
            DAppBridgePolicy.bridgeOrigin(
                currentPageUrl = "http://127.0.0.1:5173/app",
                requestedOrigin = "http://127.0.0.1:5173",
                fallbackDAppUrl = "http://127.0.0.1:5173",
            ),
        )
    }

    @Test
    fun allowsValidCurrentOriginAfterRedirect() {
        assertEquals(
            "https://app.octra.id",
            DAppBridgePolicy.bridgeOrigin(
                currentPageUrl = "https://app.octra.id/dashboard",
                requestedOrigin = "https://octra.id",
                fallbackDAppUrl = "https://octra.id",
            ),
        )
    }

    @Test
    fun blocksNonWebOrigins() {
        assertNull(
            DAppBridgePolicy.bridgeOrigin(
                currentPageUrl = "file:///android_asset/index.html",
                requestedOrigin = "",
                fallbackDAppUrl = "file:///android_asset/index.html",
            ),
        )
    }

    @Test
    fun blocksPunycodeOrigins() {
        assertNull(
            DAppBridgePolicy.bridgeOrigin(
                currentPageUrl = "https://xn--octra-9za.id",
                requestedOrigin = "https://xn--octra-9za.id",
                fallbackDAppUrl = "https://xn--octra-9za.id",
            ),
        )
    }

    @Test
    fun fallsBackToRequestedOriginWhenCurrentPageIsBlank() {
        assertEquals(
            "https://octra.id",
            DAppBridgePolicy.bridgeOrigin(
                currentPageUrl = "",
                requestedOrigin = "https://octra.id",
                fallbackDAppUrl = "https://fallback.octra.id",
            ),
        )
    }
}
