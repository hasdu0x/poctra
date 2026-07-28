package com.poctra.wallet.dapp

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EvmDAppSupportPolicyTest {
    @Test
    fun `Uniswap domains are excluded`() {
        assertFalse(EvmDAppSupportPolicy.isSupported("https://uniswap.org"))
        assertFalse(EvmDAppSupportPolicy.isSupported("https://app.uniswap.org"))
    }

    @Test
    fun `Near remains supported`() {
        assertTrue(EvmDAppSupportPolicy.isSupported("https://near.com"))
        assertTrue(EvmDAppSupportPolicy.isSupported("https://app.near.com"))
    }

    @Test
    fun `lookalike domains are not blocked`() {
        assertTrue(EvmDAppSupportPolicy.isSupported("https://uniswap.org.example.com"))
    }
}
