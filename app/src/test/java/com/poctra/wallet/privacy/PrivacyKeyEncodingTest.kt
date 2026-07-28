package com.poctra.wallet.privacy

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyKeyEncodingTest {
    @Test
    fun canonicalWrapperRequiresExactKeyAndProofProfiles() {
        assertTrue(PrivacyKeyEncoding.matchesCompatibilityFlags(1 or 4))
        assertFalse(PrivacyKeyEncoding.matchesCompatibilityFlags(1))
        assertFalse(PrivacyKeyEncoding.matchesCompatibilityFlags(4))
        assertFalse(PrivacyKeyEncoding.matchesCompatibilityFlags(1 or 2))
    }
}
