package com.poctra.wallet.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyRegistrationCacheTest {
    @Test
    fun verificationIsScopedToRpcWalletAndKey() {
        var now = 100L
        val cache = PrivacyRegistrationCache(ttlMs = 1_000L, nowMs = { now })
        val key = byteArrayOf(1, 2, 3)

        cache.remember("https://rpc.example/rpc/", "octWallet", key)

        assertTrue(cache.contains("https://rpc.example/rpc", "OCTWALLET", key))
        assertFalse(cache.contains("https://other.example/rpc", "octWallet", key))
        assertFalse(cache.contains("https://rpc.example/rpc", "octOther", key))
        assertFalse(cache.contains("https://rpc.example/rpc", "octWallet", byteArrayOf(3, 2, 1)))
    }

    @Test
    fun verificationExpiresAndCanBeInvalidated() {
        var now = 100L
        val cache = PrivacyRegistrationCache(ttlMs = 1_000L, nowMs = { now })
        val key = byteArrayOf(7, 8, 9)

        cache.remember("https://rpc.example/rpc", "octWallet", key)
        now = 1_099L
        assertTrue(cache.contains("https://rpc.example/rpc", "octWallet", key))

        cache.invalidate("octWallet", "https://rpc.example/rpc")
        assertFalse(cache.contains("https://rpc.example/rpc", "octWallet", key))

        cache.remember("https://rpc.example/rpc", "octWallet", key)
        now = 2_099L
        assertFalse(cache.contains("https://rpc.example/rpc", "octWallet", key))
    }
}
