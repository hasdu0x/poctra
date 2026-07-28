package com.poctra.wallet.data

import org.junit.Assert.assertEquals
import org.junit.Test

class DAppSessionIdentityTest {
    @Test
    fun `session identity includes origin and wallet address`() {
        val first = dAppSessionIdentity("https://devnet.octra.id/", "octFirst")
        val second = dAppSessionIdentity("https://devnet.octra.id", "octSecond")

        assertEquals("https://devnet.octra.id:octFirst", first)
        assertEquals("https://devnet.octra.id:octSecond", second)
    }

    @Test
    fun `default ports share the same session origin`() {
        assertEquals(
            dAppSessionIdentity("https://devnet.octra.id:443/path", "octWallet"),
            dAppSessionIdentity("https://devnet.octra.id", "octWallet"),
        )
    }
}
