package com.poctra.wallet.dapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DAppBridgeOriginPolicyTest {
    @Test
    fun `native origin wins when page claim matches`() {
        assertEquals(
            "https://devnet.octra.id",
            DAppBridgeOriginPolicy.resolve(
                nativeOrigin = "https://devnet.octra.id/register",
                claimedOrigin = "https://devnet.octra.id",
                expectedOrigin = "https://devnet.octra.id",
            ),
        )
    }

    @Test
    fun `sanitized expected origin recovers an unavailable WebView url`() {
        assertEquals(
            "https://devnet.octra.id",
            DAppBridgeOriginPolicy.resolve(
                nativeOrigin = null,
                claimedOrigin = "https://devnet.octra.id",
                expectedOrigin = "https://devnet.octra.id/register",
            ),
        )
    }

    @Test
    fun `native and claimed origin mismatch is rejected`() {
        assertNull(
            DAppBridgeOriginPolicy.resolve(
                nativeOrigin = "https://devnet.octra.id",
                claimedOrigin = "https://evil.example",
                expectedOrigin = "https://devnet.octra.id",
            ),
        )
    }

    @Test
    fun `claimed origin cannot override expected origin fallback`() {
        assertNull(
            DAppBridgeOriginPolicy.resolve(
                nativeOrigin = "about:blank",
                claimedOrigin = "https://evil.example",
                expectedOrigin = "https://devnet.octra.id",
            ),
        )
    }

    @Test
    fun `opaque schemes are rejected`() {
        assertNull(DAppBridgeOriginPolicy.resolve("file:///tmp/app.html", null, "oct://foo.oct"))
    }
}
