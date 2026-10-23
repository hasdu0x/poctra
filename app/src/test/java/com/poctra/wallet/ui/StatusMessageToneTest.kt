package com.poctra.wallet.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatusMessageToneTest {
    @Test
    fun confirmationTimeoutsAreWarnings() {
        assertTrue(isWarningStatusMessage("Confirmation timed out. The transaction may still be pending."))
        assertTrue(isWarningStatusMessage("Confirmation timeout"))
        assertTrue(isWarningStatusMessage("Pending too long? Try speed up."))
    }

    @Test
    fun rejectedAndRevertedTransactionsRemainErrors() {
        assertFalse(isWarningStatusMessage("Transaction rejected by the node."))
        assertFalse(isWarningStatusMessage("Execution reverted."))
        assertFalse(isWarningStatusMessage("Invalid signature."))
    }
}
