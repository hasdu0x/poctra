package com.poctra.wallet.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyModelsTest {
    @Test
    fun formatsRawOctraAmountsWithoutFloatingPoint() {
        assertEquals("0", formatPrivacyAmount(0L))
        assertEquals("1", formatPrivacyAmount(1_000_000L))
        assertEquals("12.3456", formatPrivacyAmount(12_345_600L))
        assertEquals("0.000001", formatPrivacyAmount(1L))
    }

    @Test
    fun onlyFinalOperationStagesAreTerminal() {
        val preparing = operation(PrivacyOperationStage.Preparing)
        assertFalse(preparing.isTerminal)
        assertTrue(operation(PrivacyOperationStage.Confirmed).isTerminal)
        assertTrue(operation(PrivacyOperationStage.Empty).isTerminal)
        assertTrue(operation(PrivacyOperationStage.Failed).isTerminal)
    }

    private fun operation(stage: PrivacyOperationStage) = PrivacyOperationState(
        id = 1L,
        type = PrivacyOperationType.Encrypt,
        stage = stage,
        message = "test",
    )
}
