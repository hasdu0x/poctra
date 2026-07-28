package com.poctra.wallet.dapp

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OctraProviderDiscoveryTest {
    @Test
    fun `announcement metadata satisfies strict RFC O 1 discovery`() {
        val uuidV4 = Regex(
            "^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$",
        )
        val rdnsLabel = Regex("^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$")

        assertTrue(uuidV4.matches(POCTRA_PROVIDER_UUID))
        assertTrue(POCTRA_PROVIDER_NAME.isNotBlank())
        assertTrue(POCTRA_PROVIDER_RDNS.contains('.'))
        assertTrue(POCTRA_PROVIDER_RDNS.split('.').all(rdnsLabel::matches))
        assertTrue(POCTRA_PROVIDER_ICON.startsWith("data:image/png;"))
        assertTrue(POCTRA_PROVIDER_ICON.length <= 128 * 1024)
        assertTrue(POCTRA_PROVIDER_HOMEPAGE.startsWith("https://"))
        assertEquals("1.0.0", POCTRA_PROVIDER_VERSION)
    }

    @Test
    fun `provider icon is a WebView compatible PNG`() {
        val encoded = POCTRA_PROVIDER_ICON.substringAfter("base64,")
        val png = Base64.getDecoder().decode(encoded)

        assertTrue(png.size > 1_000)
        assertEquals(0x89, png[0].toInt() and 0xff)
        assertEquals('P'.code, png[1].toInt())
        assertEquals('N'.code, png[2].toInt())
        assertEquals('G'.code, png[3].toInt())
    }

    @Test
    fun `navigation lifecycle provider uses canonical provider icon`() {
        val source = """const info = { icon: "data:image/svg+xml;base64,legacy", version: "1" };"""
        val result = replaceProviderIconMetadata(source)

        assertTrue(result.contains("icon: \"$POCTRA_PROVIDER_ICON\""))
        assertTrue(!result.contains("data:image/svg+xml"))
    }

    @Test
    fun `Octra Identity hosts use navigation lifecycle provider injection`() {
        assertTrue(requiresNavigationLifecycleProvider("https://octra.id/"))
        assertTrue(requiresNavigationLifecycleProvider("https://devnet.octra.id/names"))
        assertTrue(requiresNavigationLifecycleProvider("https://app.devnet.octra.id/"))
        assertTrue(!requiresNavigationLifecycleProvider("https://octra.id.evil.example/"))
        assertTrue(!requiresNavigationLifecycleProvider("https://near.com/"))
    }

    @Test
    fun `RFC O 1 inpage provider resource is scoped to trusted host and bridge token`() {
        val token = "4f43abcf-c36c-4d9f-9cf1-93492b7156ee"
        val path = rfcO1InpageProviderPath(token)

        assertTrue(isRfcO1InpageProviderRequest("https://devnet.octra.id$path", token))
        assertTrue(isRfcO1InpageProviderRequest("https://octra.id$path", token))
        assertTrue(!isRfcO1InpageProviderRequest("http://devnet.octra.id$path", token))
        assertTrue(!isRfcO1InpageProviderRequest("https://octra.id.evil.example$path", token))
        assertTrue(!isRfcO1InpageProviderRequest("https://devnet.octra.id$path?token=$token", token))
        assertTrue(!isRfcO1InpageProviderRequest("https://devnet.octra.id${rfcO1InpageProviderPath("other")}", token))
    }

}
