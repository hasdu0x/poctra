package com.poctra.wallet.dapp.circle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CircleResourceModeTest {
    @Test
    fun `supports public aliases and sealed reads`() {
        assertEquals(CircleResourceMode.PublicRead, parseCircleResourceMode("public_read"))
        assertEquals(CircleResourceMode.PublicRead, parseCircleResourceMode("public_resources"))
        assertEquals(CircleResourceMode.SealedRead, parseCircleResourceMode("sealed_read"))
    }

    @Test
    fun `rejects unknown access modes instead of silently exposing them`() {
        assertThrows(IllegalArgumentException::class.java) {
            parseCircleResourceMode("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            parseCircleResourceMode("owner_only")
        }
    }

    @Test
    fun `derives legacy metadata without failing open`() {
        assertEquals(
            CircleResourceMode.PublicRead,
            resolveCircleResourceMode(resourceMode = null, browserMode = "gateway_allowed"),
        )
        assertEquals(
            CircleResourceMode.SealedRead,
            resolveCircleResourceMode(resourceMode = null, privacyClass = "sealed"),
        )
        assertThrows(IllegalArgumentException::class.java) {
            resolveCircleResourceMode(resourceMode = null)
        }
    }
}
