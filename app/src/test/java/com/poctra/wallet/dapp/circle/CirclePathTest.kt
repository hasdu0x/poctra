package com.poctra.wallet.dapp.circle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CirclePathTest {
    @Test
    fun `normalizes canonical Circle paths`() {
        assertEquals("/index.html", normalizeCircleAssetPath("/"))
        assertEquals("/assets/app.js", normalizeCircleAssetPath("assets/app.js"))
        assertEquals("/images/cover%20art.webp", normalizeCircleAssetPath("/images/cover%20art.webp"))
    }

    @Test
    fun `rejects traversal and ambiguous encoding`() {
        assertThrows(IllegalArgumentException::class.java) { normalizeCircleAssetPath("/../secret") }
        assertThrows(IllegalArgumentException::class.java) { normalizeCircleAssetPath("/%2e%2e/secret") }
        assertThrows(IllegalArgumentException::class.java) { normalizeCircleAssetPath("/assets%2fsecret") }
    }
}
