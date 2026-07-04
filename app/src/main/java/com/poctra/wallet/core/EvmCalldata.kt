package com.poctra.wallet.core

import java.math.BigInteger

object EvmCalldata {
    fun erc20BalanceOf(ownerAddress: String): String =
        "0x70a08231${addressWord(ownerAddress)}"

    fun erc20Allowance(ownerAddress: String, spenderAddress: String): String =
        "0xdd62ed3e${addressWord(ownerAddress)}${addressWord(spenderAddress)}"

    fun erc20Transfer(recipientAddress: String, amountRaw: BigInteger): ByteArray =
        "a9059cbb${addressWord(recipientAddress)}${amountRaw.toAbiWord()}".hexToBytes()

    fun erc20Approve(spenderAddress: String, amountRaw: BigInteger): ByteArray =
        "095ea7b3${addressWord(spenderAddress)}${amountRaw.toAbiWord()}".hexToBytes()

    fun erc721SafeTransferFrom(fromAddress: String, recipientAddress: String, tokenId: BigInteger): ByteArray =
        "42842e0e${addressWord(fromAddress)}${addressWord(recipientAddress)}${tokenId.toAbiWord()}".hexToBytes()

    private fun addressWord(address: String): String =
        address.cleanHex().padStart(64, '0')

    private fun BigInteger.toAbiWord(): String =
        toString(16).padStart(64, '0')

    private fun String.cleanHex(): String =
        removePrefix("0x").removePrefix("0X")

    private fun String.hexToBytes(): ByteArray {
        val clean = cleanHex()
        require(clean.length % 2 == 0) { "Hex string must have an even length." }
        return ByteArray(clean.length / 2) { index ->
            clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
        }
    }
}
