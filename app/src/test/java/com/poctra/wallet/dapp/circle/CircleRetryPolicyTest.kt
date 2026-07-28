package com.poctra.wallet.dapp.circle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CircleRetryPolicyTest {
    @Test
    fun `retries transient reads with bounded backoff`() {
        var attempts = 0
        val delays = mutableListOf<Long>()
        val result = withCircleReadRetry(sleeper = delays::add) {
            attempts += 1
            if (attempts < 3) throw IllegalStateException("HTTP 503")
            "ready"
        }
        assertEquals("ready", result)
        assertEquals(3, attempts)
        assertEquals(listOf(180L, 360L), delays)
    }

    @Test
    fun `does not retry terminal integrity failures`() {
        var attempts = 0
        assertThrows(IllegalArgumentException::class.java) {
            withCircleReadRetry(sleeper = {}) {
                attempts += 1
                throw IllegalArgumentException("Circle asset size mismatch.")
            }
        }
        assertEquals(1, attempts)
        assertFalse(isTransientCircleFailure(IllegalStateException("Sealed circle plaintext hash mismatch.")))
        assertTrue(isCircleCredentialOrIntegrityFailure(IllegalStateException("Passphrase did not match.")))
    }
}
