package com.poctra.wallet.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CircleBrowserCompatibilityTest {
    @Test
    fun parsesBoundedAndOpenEndedRanges() {
        assertEquals(10..19, parseCircleByteRange("bytes=10-19", 100))
        assertEquals(90..99, parseCircleByteRange("bytes=90-", 100))
    }

    @Test
    fun parsesSuffixRange() {
        assertEquals(75..99, parseCircleByteRange("bytes=-25", 100))
    }

    @Test
    fun rejectsInvalidOrUnsatisfiableRanges() {
        assertNull(parseCircleByteRange("items=0-10", 100))
        assertNull(parseCircleByteRange("bytes=100-120", 100))
        assertNull(parseCircleByteRange("bytes=20-10", 100))
        assertNull(parseCircleByteRange("bytes=-0", 100))
    }
}
