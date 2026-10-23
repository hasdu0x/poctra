package com.poctra.wallet.ui

import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.widget.TextView
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.CombinedChart
import com.github.mikephil.charting.components.LimitLine
import com.github.mikephil.charting.components.MarkerView
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.ValueFormatter
import com.github.mikephil.charting.highlight.Highlight
import com.github.mikephil.charting.utils.MPPointF
import com.poctra.wallet.data.TradeCandle
import com.poctra.wallet.data.TradeChartTimeframe
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AdvancedTradingChartView(
    candles: List<TradeCandle>,
    timeframe: TradeChartTimeframe,
    onExpandClick: () -> Unit,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
    currentPrice: Double = 0.0,
    showVolume: Boolean = true,
) {
    val hasVolume = showVolume && candles.isNotEmpty()
    val mainChartWeight = if (hasVolume) 0.7f else 1f
    
    Column(modifier = modifier) {
        // Main candlestick chart
        MainCandlestickChart(
            candles = candles,
            timeframe = timeframe,
            fullscreen = fullscreen,
            currentPrice = currentPrice,
            modifier = Modifier
                .fillMaxWidth()
                .weight(mainChartWeight)
        )
        
        // Volume bars
        if (hasVolume) {
            Spacer(Modifier.height(Space.x2))
            VolumeSubchart(
                candles = candles,
                timeframe = timeframe,
                fullscreen = fullscreen,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (fullscreen) 100.dp else 60.dp)
            )
        }
    }
}

@Composable
private fun MainCandlestickChart(
    candles: List<TradeCandle>,
    timeframe: TradeChartTimeframe,
    fullscreen: Boolean,
    currentPrice: Double,
    modifier: Modifier = Modifier,
) {
    val surfaceBaseColor = SurfaceBase.toArgb()
    val surfaceRaisedColor = SurfaceRaised.toArgb()
    val errorRedColor = ErrorRed.toArgb()
    val textSecondaryColor = TextSecondary.toArgb()
    val borderLineColor = BorderLine.toArgb()

    AndroidView(
        factory = { context ->
            CombinedChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)
                setBackgroundColor(surfaceBaseColor)
                
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(true)
                    gridColor = borderLineColor
                    textColor = textSecondaryColor
                    textSize = 9f
                    setDrawAxisLine(true)
                    axisLineColor = borderLineColor
                    setDrawLabels(false)
                    valueFormatter = createTimeFormatter(candles, timeframe)
                }
                
                axisRight.isEnabled = false
                axisLeft.apply {
                    setDrawGridLines(true)
                    gridColor = borderLineColor
                    textColor = textSecondaryColor
                    textSize = 9f
                    setPosition(YAxis.YAxisLabelPosition.INSIDE_CHART)
                    setDrawAxisLine(false)
                    valueFormatter = createPriceFormatter()
                }
                
                setVisibleXRangeMaximum(if (fullscreen) 50f else 28f)
                moveViewToX(candles.size.toFloat())
            }
        },
        update = { chart ->
            if (candles.isEmpty()) {
                chart.clear()
                chart.invalidate()
                return@AndroidView
            }
            
            // Set marker for H/L/O/C display
            chart.marker = CandleMarkerView(
                chart.context,
                candles,
                textSecondaryColor,
                surfaceBaseColor
            )
            
            val candleEntries = candles.mapIndexed { index, candle ->
                CandleEntry(
                    index.toFloat(),
                    candle.high.toFloat(),
                    candle.low.toFloat(),
                    candle.open.toFloat(),
                    candle.close.toFloat()
                )
            }
            
            val candleDataSet = CandleDataSet(candleEntries, "Price").apply {
                setDrawIcons(false)
                axisDependency = YAxis.AxisDependency.LEFT
                shadowColor = borderLineColor
                shadowWidth = 1.5f
                decreasingColor = errorRedColor
                decreasingPaintStyle = Paint.Style.FILL
                increasingColor = surfaceRaisedColor
                increasingPaintStyle = Paint.Style.FILL
                neutralColor = textSecondaryColor
                setDrawValues(false)
                highLightColor = AndroidColor.WHITE
            }
            
            val combinedData = CombinedData()
            combinedData.setData(CandleData(candleDataSet))
            
            // Add current price line if available
            if (currentPrice > 0.0) {
                chart.axisLeft.removeAllLimitLines()
                val priceLabel = when {
                    currentPrice >= 1000 -> "%.0fk".format(currentPrice / 1000)
                    currentPrice >= 10 -> "%.2f".format(currentPrice)
                    currentPrice >= 1 -> "%.3f".format(currentPrice)
                    else -> "%.4f".format(currentPrice)
                }
                val priceLine = LimitLine(currentPrice.toFloat(), priceLabel).apply {
                    lineWidth = 2f
                    lineColor = surfaceRaisedColor
                    enableDashedLine(10f, 5f, 0f)
                    labelPosition = LimitLine.LimitLabelPosition.RIGHT_TOP
                    textColor = surfaceRaisedColor
                    textSize = 9f
                }
                chart.axisLeft.addLimitLine(priceLine)
            }
            
            chart.data = combinedData
            chart.notifyDataSetChanged()
            chart.invalidate()
        },
        modifier = modifier
    )
}

// Helper functions
private fun createTimeFormatter(candles: List<TradeCandle>, timeframe: TradeChartTimeframe): ValueFormatter {
    return object : ValueFormatter() {
        private val dateFormat = SimpleDateFormat(
            when (timeframe) {
                TradeChartTimeframe.H1, TradeChartTimeframe.H4 -> "HH:mm"
                TradeChartTimeframe.D1 -> "dd MMM"
                TradeChartTimeframe.W1 -> "dd MMM"
            },
            Locale.US
        )
        override fun getFormattedValue(value: Float): String {
            val index = value.toInt()
            if (index < 0 || index >= candles.size) return ""
            return dateFormat.format(Date(candles[index].timestamp * 1000))
        }
    }
}

private fun createPriceFormatter(): ValueFormatter {
    return object : ValueFormatter() {
        override fun getFormattedValue(value: Float): String {
            return when {
                value >= 1000 -> "%.0fk".format(value / 1000)
                value >= 10 -> "%.1f".format(value)
                value >= 1 -> "%.2f".format(value)
                else -> "%.4f".format(value)
            }
        }
    }
}

// Custom marker for H/L/O/C display
class CandleMarkerView(
    context: android.content.Context,
    private val candles: List<TradeCandle>,
    private val textColor: Int,
    private val bgColor: Int,
) : MarkerView(context, com.poctra.wallet.R.layout.chart_marker) {
    private val tvContent: TextView = findViewById(com.poctra.wallet.R.id.tvContent)
    
    override fun refreshContent(e: com.github.mikephil.charting.data.Entry?, highlight: Highlight?) {
        if (e is CandleEntry && e.x.toInt() in candles.indices) {
            val candle = candles[e.x.toInt()]
            tvContent.text = "O: %.4f\nH: %.4f\nL: %.4f\nC: %.4f".format(
                candle.open, candle.high, candle.low, candle.close
            )
            tvContent.setTextColor(textColor)
            tvContent.setBackgroundColor(bgColor)
        }
        super.refreshContent(e, highlight)
    }
    
    override fun getOffset(): MPPointF {
        return MPPointF((-(width / 2)).toFloat(), (-height).toFloat())
    }
}

@Composable
private fun VolumeSubchart(
    candles: List<TradeCandle>,
    timeframe: TradeChartTimeframe,
    fullscreen: Boolean,
    modifier: Modifier = Modifier,
) {
    val surfaceBaseColor = SurfaceBase.toArgb()
    val surfaceRaisedColor = SurfaceRaised.toArgb()
    val errorRedColor = ErrorRed.toArgb()
    val textSecondaryColor = TextSecondary.toArgb()
    val borderLineColor = BorderLine.toArgb()

    AndroidView(
        factory = { context ->
            BarChart(context).apply {
                description.isEnabled = false
                legend.isEnabled = false
                setTouchEnabled(true)
                isDragEnabled = true
                setScaleEnabled(true)
                setPinchZoom(true)
                setDrawGridBackground(false)
                setBackgroundColor(surfaceBaseColor)
                
                xAxis.apply {
                    position = XAxis.XAxisPosition.BOTTOM
                    setDrawGridLines(false)
                    textColor = textSecondaryColor
                    textSize = 8f
                    setDrawAxisLine(true)
                    axisLineColor = borderLineColor
                    setDrawLabels(false)
                    valueFormatter = createTimeFormatter(candles, timeframe)
                }
                
                axisRight.isEnabled = false
                axisLeft.apply {
                    setDrawGridLines(false)
                    setDrawLabels(false)
                    setDrawAxisLine(false)
                }
                
                setVisibleXRangeMaximum(if (fullscreen) 50f else 28f)
                moveViewToX(candles.size.toFloat())
            }
        },
        update = { chart ->
            if (candles.isEmpty()) {
                chart.clear()
                chart.invalidate()
                return@AndroidView
            }
            
            val volumeEntries = candles.mapIndexed { index, candle ->
                BarEntry(index.toFloat(), candle.volume.toFloat())
            }
            
            val volumeDataSet = BarDataSet(volumeEntries, "Volume").apply {
                setColors(
                    candles.mapIndexed { _, candle ->
                        if (candle.close >= candle.open) surfaceRaisedColor else errorRedColor
                    }.toIntArray(),
                    255
                )
                setDrawValues(false)
                highLightAlpha = 200
            }
            
            val barData = BarData(volumeDataSet)
            barData.barWidth = 0.7f
            chart.data = barData
            chart.notifyDataSetChanged()
            chart.invalidate()
        },
        modifier = modifier
    )
}
