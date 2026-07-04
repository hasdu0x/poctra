package com.poctra.wallet.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DAppUrlPolicyTest {
    @Test
    fun normalizesBareDomainToHttps() {
        assertEquals("https://octra.id", DAppUrlPolicy.normalizeBrowserUrl("octra.id"))
    }

    @Test
    fun allowsHttpsUrl() {
        assertEquals("https://octra.id/app", DAppUrlPolicy.normalizeBrowserUrl("https://octra.id/app"))
    }

    @Test
    fun allowsLocalHttpForDevelopment() {
        assertEquals("http://127.0.0.1:8080", DAppUrlPolicy.normalizeBrowserUrl("http://127.0.0.1:8080"))
    }

    @Test
    fun blocksNonHttpSchemes() {
        assertPolicyRejects("javascript:alert(1)")
    }

    @Test
    fun blocksEmbeddedCredentials() {
        assertPolicyRejects("https://user:pass@octra.id")
    }

    @Test
    fun blocksPunycodeDomains() {
        assertPolicyRejects("https://xn--octra-9za.id")
    }

    @Test
    fun blocksNonLocalIpHosts() {
        assertPolicyRejects("http://192.168.1.10:8080")
    }

    @Test
    fun detectsSuspiciousOrigins() {
        assertTrue(DAppUrlPolicy.isSuspiciousOrigin("http://192.168.1.10"))
        assertTrue(DAppUrlPolicy.isSuspiciousOrigin("https://xn--octra-9za.id"))
    }

    private fun assertPolicyRejects(value: String) {
        val rejected = runCatching { DAppUrlPolicy.normalizeBrowserUrl(value) }.isFailure
        assertTrue("$value should be rejected", rejected)
    }
}
