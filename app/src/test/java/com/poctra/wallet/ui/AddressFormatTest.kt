package com.poctra.wallet.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class AddressFormatTest {
    private val address = "oct1234567890abcdefghijklmnopqrstuvwxyzABCDEFGH"

    @Test
    fun regularAddressUsesEightCharactersOnEachSide() {
        assertEquals("oct12345...ABCDEFGH", shortAddress(address))
    }

    @Test
    fun overviewHeaderUsesElevenCharactersOnEachSide() {
        assertEquals("oct12345678...xyzABCDEFGH", headerShortAddress(address))
    }
}
