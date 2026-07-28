package com.poctra.wallet.dapp

import org.junit.Assert.assertEquals
import org.junit.Test

class DAppOriginBindingTest {
    @Test
    fun `loading top-level origin can serve requests before commit`() {
        val binding = DAppOriginBinding("https://devnet.octra.id/register")

        binding.onNavigationStarted("https://devnet.octra.id/register")

        assertEquals("https://devnet.octra.id", binding.resolve("https://devnet.octra.id/register"))
    }

    @Test
    fun `committed origin is canonical and accepts paths on same origin`() {
        val binding = DAppOriginBinding()
        binding.onPageCommitted("https://DEVNET.octra.id/register?name=foo")

        assertEquals("https://devnet.octra.id", binding.resolve("https://devnet.octra.id/my-names"))
    }

    @Test
    fun `current page from another origin is rejected`() {
        val binding = DAppOriginBinding()
        binding.onPageCommitted("https://app.uniswap.org")

        assertEquals("", binding.resolve("https://evil.example/connect"))
    }

    @Test
    fun `new navigation clears previous origin`() {
        val binding = DAppOriginBinding()
        binding.onPageCommitted("https://devnet.octra.id")
        binding.onNavigationStarted("https://app.uniswap.org")

        assertEquals("", binding.resolve("https://devnet.octra.id"))
        assertEquals("https://app.uniswap.org", binding.resolve("https://app.uniswap.org/connect"))
    }

    @Test
    fun `trusted origin follows a native redirect before webview url is available`() {
        val binding = DAppOriginBinding("https://uniswap.org")

        binding.onNavigationStarted("https://app.uniswap.org/swap")

        assertEquals("https://app.uniswap.org", binding.trustedOrigin())
        assertEquals("https://app.uniswap.org", binding.resolve(null))
    }

    @Test
    fun `transient unsupported callback does not erase committed origin`() {
        val binding = DAppOriginBinding()
        binding.onPageCommitted("https://app.uniswap.org")

        binding.onNavigationStarted("about:blank")

        assertEquals("https://app.uniswap.org", binding.trustedOrigin())
        assertEquals("https://app.uniswap.org", binding.resolve(null))
    }

    @Test
    fun `circle virtual https origin can be bound`() {
        val binding = DAppOriginBinding()
        binding.onPageCommitted("https://circle.poctra.local/oct/oct123/index.html")

        assertEquals("https://circle.poctra.local", binding.resolve(null))
    }
}
