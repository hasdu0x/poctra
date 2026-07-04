package com.poctra.wallet.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class EvmCalldataTest {
    @Test
    fun encodesErc20ApproveZeroForRevoke() {
        val spender = "0x000000000022D473030F116dDEE9F6B43aC78BA3"
        val calldata = EvmCalldata.erc20Approve(spender, BigInteger.ZERO).toHex()

        assertTrue(calldata.startsWith("095ea7b3"))
        assertTrue(calldata.contains(spender.removePrefix("0x").lowercase()))
        assertTrue(calldata.endsWith("0".repeat(64)))
        assertEquals(4 + 32 + 32, calldata.length / 2)
    }

    @Test
    fun encodesErc20AllowanceCall() {
        val owner = "0x1111111111111111111111111111111111111111"
        val spender = "0x2222222222222222222222222222222222222222"
        val calldata = EvmCalldata.erc20Allowance(owner, spender)

        assertTrue(calldata.startsWith("0xdd62ed3e"))
        assertTrue(calldata.contains(owner.removePrefix("0x")))
        assertTrue(calldata.contains(spender.removePrefix("0x")))
        assertEquals(2 + 8 + 64 + 64, calldata.length)
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
