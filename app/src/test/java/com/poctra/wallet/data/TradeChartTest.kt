package com.poctra.wallet.data

import org.junit.Assert.assertEquals
import org.junit.Test

class TradeChartTest {
    @Test
    fun weeklyChartAggregatesDailyCandlesWithoutChangingOhlcOrder() {
        val day = 24 * 60 * 60L
        val candles = (0 until 14).map { index ->
            val open = 10.0 + index
            TradeCandle(
                timestamp = index * day,
                open = open,
                high = open + 2.0,
                low = open - 1.0,
                close = open + 1.0,
            )
        }

        val weekly = aggregateTradeCandles(candles, TradeChartTimeframe.W1)

        assertEquals(2, weekly.size)
        assertEquals(10.0, weekly.first().open, 0.0)
        assertEquals(18.0, weekly.first().high, 0.0)
        assertEquals(9.0, weekly.first().low, 0.0)
        assertEquals(17.0, weekly.first().close, 0.0)
        assertEquals(17.0, weekly.last().open, 0.0)
        assertEquals(25.0, weekly.last().high, 0.0)
        assertEquals(16.0, weekly.last().low, 0.0)
        assertEquals(24.0, weekly.last().close, 0.0)
    }

    @Test
    fun nonWeeklyChartKeepsProviderCandles() {
        val candles = listOf(TradeCandle(1L, 1.0, 2.0, 0.5, 1.5))

        assertEquals(candles, aggregateTradeCandles(candles, TradeChartTimeframe.H1))
    }
}
