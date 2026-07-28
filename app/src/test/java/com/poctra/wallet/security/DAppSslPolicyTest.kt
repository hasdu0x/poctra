package com.poctra.wallet.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DAppSslPolicyTest {
    @Test
    fun reportsCertificateErrorsForTheVisibleSite() {
        assertTrue(
            DAppSslPolicy.affectsVisiblePage(
                "https://app.uniswap.org/swap",
                "https://app.uniswap.org/assets/app.js",
            ),
        )
        assertTrue(DAppSslPolicy.affectsVisiblePage(null, "https://app.uniswap.org"))
    }

    @Test
    fun keepsThePageVisibleWhenABlockedThirdPartyResourceFailsTls() {
        assertFalse(
            DAppSslPolicy.affectsVisiblePage(
                "https://app.uniswap.org/swap",
                "https://third-party.example/telemetry.js",
            ),
        )
    }
}
