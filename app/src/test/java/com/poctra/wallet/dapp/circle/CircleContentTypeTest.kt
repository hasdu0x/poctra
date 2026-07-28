package com.poctra.wallet.dapp.circle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleContentTypeTest {
    @Test
    fun `infers documented Circle asset content types`() {
        assertEquals("text/html; charset=utf-8", inferredCircleContentType("/index.html"))
        assertEquals("text/css; charset=utf-8", inferredCircleContentType("/styles.css"))
        assertEquals("application/javascript; charset=utf-8", inferredCircleContentType("/app.js"))
        assertEquals("application/json; charset=utf-8", inferredCircleContentType("/data.json"))
        assertEquals("image/svg+xml", inferredCircleContentType("/logo.svg"))
        assertEquals("image/jpeg", inferredCircleContentType("/cover.JPEG"))
        assertEquals("application/octet-stream", inferredCircleContentType("/archive.bin"))
    }

    @Test
    fun `keeps declared type but repairs generic octet stream from path`() {
        assertEquals("image/avif", resolveCircleContentType("image/avif", "/cover.bin").mediaType)
        assertEquals("application/json", resolveCircleContentType("broken-content-type", "/data.json").mediaType)
        val inferred = resolveCircleContentType("application/octet-stream", "/index.html")
        assertEquals("text/html", inferred.mediaType)
        assertEquals("utf-8", inferred.charset?.lowercase())
        assertTrue(inferred.isHtml)
        assertFalse(resolveCircleContentType(null, "/image.png").isHtml)
    }
}
