package com.poctra.wallet.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricPromptPolicyTest {
    @Test
    fun `single flight gate rejects overlapping prompts`() {
        val gate = BiometricPromptGate()

        assertTrue(gate.tryStart())
        assertTrue(gate.isActive())
        assertFalse(gate.tryStart())

        gate.finish()

        assertFalse(gate.isActive())
        assertTrue(gate.tryStart())
    }

    @Test
    fun `user cancellation codes are not treated as authentication errors`() {
        assertTrue(BiometricPromptPolicy.isCancellation(5))
        assertTrue(BiometricPromptPolicy.isCancellation(10))
        assertTrue(BiometricPromptPolicy.isCancellation(13))
        assertFalse(BiometricPromptPolicy.isCancellation(7))
    }
}
