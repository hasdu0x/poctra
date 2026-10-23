package com.poctra.wallet.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class TradeAssetTest {
    private val mainnet = EvmNetwork(
        id = "eth-mainnet",
        name = "Ethereum Mainnet",
        chainId = 1L,
        rpcUrl = "https://rpc.example",
        symbol = "ETH",
        explorerUrl = "https://explorer.example",
        isTestnet = false,
    )

    @Test
    fun `network assets include native and deduplicate token contracts case-insensitively`() {
        val customAddress = "0x1111111111111111111111111111111111111111"
        val assets = TradeAsset.forNetwork(
            network = mainnet,
            tokens = listOf(
                token(TradeAsset.WOCT.address, "Wrapped OCT", "wOCT", 6, 1L),
                token(customAddress, "Example", "EXM", 8, 1L),
                token(customAddress.uppercase(), "Duplicate", "EXM", 8, 1L),
                token("0x2222222222222222222222222222222222222222", "Foreign", "FOR", 18, 10L),
            ),
        )

        assertEquals(listOf("wOCT", "ETH", "EXM"), assets.map(TradeAsset::symbol))
        assertTrue(assets.first().isWOct)
        assertTrue(assets[1].isNative)
        assertFalse(assets.last().isNative)
    }

    @Test
    fun `dynamic balances use chain and address key with token decimals`() {
        val token = TradeAsset(
            symbol = "EXM",
            displayName = "Example",
            address = "0x1111111111111111111111111111111111111111",
            decimals = 6,
            chainId = 1L,
        )
        val balances = TradeBalances(mapOf(token.key to BigInteger("1234567")))

        assertEquals("1.234567", balances.formatted(token))
        assertEquals(BigInteger("1234567"), balances.raw(token.copy(address = token.address.uppercase())))
    }

    @Test
    fun `mainnet assets allow direct pairs but reject the two hop wOCT USDT pair`() {
        val custom = TradeAsset(
            symbol = "EXM",
            displayName = "Example",
            address = "0x1111111111111111111111111111111111111111",
            decimals = 18,
            chainId = 1L,
        )

        assertTrue(TradeAsset.ETH.canPairWith(TradeAsset.USDT))
        assertTrue(TradeAsset.USDT.canPairWith(TradeAsset.ETH))
        assertFalse(TradeAsset.ETH.canPairWith(custom))
        assertFalse(custom.canPairWith(TradeAsset.WOCT))
        assertFalse(TradeAsset.WOCT.canPairWith(TradeAsset.USDT))
        assertFalse(TradeAsset.USDT.canPairWith(TradeAsset.WOCT))
        assertFalse(custom.canPairWith(custom))
        assertFalse(custom.canPairWith(custom.copy(chainId = 11155111L)))
    }

    private fun token(
        address: String,
        name: String,
        symbol: String,
        decimals: Int,
        chainId: Long,
    ) = EvmToken(
        address = address,
        name = name,
        symbol = symbol,
        decimals = decimals,
        balance = "0",
        chainId = chainId,
    )
}
