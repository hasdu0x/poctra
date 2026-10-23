package com.poctra.wallet.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun QrCodeView(
    text: String,
    modifier: Modifier = Modifier,
) {
    val matrix = remember(text) {
        try { SimpleQrCode.encode(text) } catch (e: IllegalArgumentException) { null }
    }
    if (matrix == null) {
        // Show a placeholder when the QR payload is too large
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(Radius.md))
                .background(SurfaceStrong)
                .padding(14.dp),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Text(
                "QR too large",
                color = TextSecondary,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
        return
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Radius.md))
            .background(SurfaceStrong)
            .padding(14.dp),
    ) {
        Canvas(Modifier.size(220.dp)) {
            val count = matrix.size
            val module = size.minDimension / count
            val offset = (size.minDimension - module * count) / 2f
            for (y in 0 until count) {
                for (x in 0 until count) {
                    if (matrix[y][x]) {
                        drawRect(
                            color = TextTertiary,
                            topLeft = Offset(offset + x * module, offset + y * module),
                            size = Size(module + 0.35f, module + 0.35f),
                        )
                    }
                }
            }
        }
    }
}

private object SimpleQrCode {
    private const val Version = 5
    private const val Size = Version * 4 + 17
    private const val DataCodewords = 108
    private const val ErrorCodewords = 26

    fun encode(text: String): Array<BooleanArray> {
        val data = makeDataCodewords(text.encodeToByteArray())
        val ecc = reedSolomonRemainder(data, ErrorCodewords)
        val codewords = data + ecc
        val modules = Array(Size) { BooleanArray(Size) }
        val function = Array(Size) { BooleanArray(Size) }

        drawFunctionPatterns(modules, function)
        drawCodewords(codewords, modules, function)
        applyMask(modules, function)
        drawFormatBits(modules, function)
        return modules
    }

    private fun makeDataCodewords(bytes: ByteArray): ByteArray {
        require(bytes.size <= 106) { "QR payload is too long." }
        val bits = mutableListOf<Int>()
        fun append(value: Int, length: Int) {
            for (i in length - 1 downTo 0) bits += (value ushr i) and 1
        }
        append(0b0100, 4)
        append(bytes.size, 8)
        bytes.forEach { append(it.toInt() and 0xff, 8) }
        repeat(minOf(4, DataCodewords * 8 - bits.size)) { bits += 0 }
        while (bits.size % 8 != 0) bits += 0

        val out = MutableList(bits.size / 8) { 0 }
        for (i in out.indices) {
            var value = 0
            for (j in 0 until 8) value = (value shl 1) or bits[i * 8 + j]
            out[i] = value
        }
        var pad = 0
        while (out.size < DataCodewords) {
            out += if (pad % 2 == 0) 0xEC else 0x11
            pad++
        }
        return ByteArray(DataCodewords) { out[it].toByte() }
    }

    private fun drawFunctionPatterns(modules: Array<BooleanArray>, function: Array<BooleanArray>) {
        drawFinder(modules, function, 3, 3)
        drawFinder(modules, function, Size - 4, 3)
        drawFinder(modules, function, 3, Size - 4)
        drawAlignment(modules, function, 30, 30)

        for (i in 0 until Size) {
            if (!function[i][6]) setFunction(modules, function, 6, i, i % 2 == 0)
            if (!function[6][i]) setFunction(modules, function, i, 6, i % 2 == 0)
        }
        setFunction(modules, function, 8, 29, true)

        for (i in 0 until 9) {
            if (i != 6) {
                setFunction(modules, function, 8, i, false)
                setFunction(modules, function, i, 8, false)
            }
        }
        for (i in 0 until 8) {
            setFunction(modules, function, Size - 1 - i, 8, false)
            setFunction(modules, function, 8, Size - 1 - i, false)
        }
    }

    private fun drawFinder(modules: Array<BooleanArray>, function: Array<BooleanArray>, cx: Int, cy: Int) {
        for (dy in -4..4) {
            for (dx in -4..4) {
                val x = cx + dx
                val y = cy + dy
                if (x !in 0 until Size || y !in 0 until Size) continue
                val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                setFunction(modules, function, x, y, dist != 2 && dist != 4)
            }
        }
    }

    private fun drawAlignment(modules: Array<BooleanArray>, function: Array<BooleanArray>, cx: Int, cy: Int) {
        for (dy in -2..2) {
            for (dx in -2..2) {
                val dist = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dy))
                setFunction(modules, function, cx + dx, cy + dy, dist != 1)
            }
        }
    }

    private fun drawCodewords(codewords: ByteArray, modules: Array<BooleanArray>, function: Array<BooleanArray>) {
        val bits = codewords.flatMap { byte ->
            (7 downTo 0).map { (byte.toInt() ushr it) and 1 }
        }
        var bitIndex = 0
        var right = Size - 1
        var upward = true
        while (right >= 1) {
            if (right == 6) right--
            for (vert in 0 until Size) {
                val y = if (upward) Size - 1 - vert else vert
                for (j in 0..1) {
                    val x = right - j
                    if (!function[y][x] && bitIndex < bits.size) {
                        modules[y][x] = bits[bitIndex] == 1
                        bitIndex++
                    }
                }
            }
            upward = !upward
            right -= 2
        }
    }

    private fun applyMask(modules: Array<BooleanArray>, function: Array<BooleanArray>) {
        for (y in 0 until Size) {
            for (x in 0 until Size) {
                if (!function[y][x] && (x + y) % 2 == 0) modules[y][x] = !modules[y][x]
            }
        }
    }

    private fun drawFormatBits(modules: Array<BooleanArray>, function: Array<BooleanArray>) {
        val bits = getFormatBits(errorCorrectionLevelBits = 1, mask = 0)
        fun bit(i: Int): Boolean = ((bits ushr i) and 1) != 0
        for (i in 0..5) setFunction(modules, function, 8, i, bit(i))
        setFunction(modules, function, 8, 7, bit(6))
        setFunction(modules, function, 8, 8, bit(7))
        setFunction(modules, function, 7, 8, bit(8))
        for (i in 9..14) setFunction(modules, function, 14 - i, 8, bit(i))
        for (i in 0..7) setFunction(modules, function, Size - 1 - i, 8, bit(i))
        for (i in 8..14) setFunction(modules, function, 8, Size - 15 + i, bit(i))
        setFunction(modules, function, 8, Size - 8, true)
    }

    private fun getFormatBits(errorCorrectionLevelBits: Int, mask: Int): Int {
        val data = (errorCorrectionLevelBits shl 3) or mask
        var rem = data
        repeat(10) {
            rem = (rem shl 1) xor if ((rem ushr 9) != 0) 0x537 else 0
        }
        return ((data shl 10) or rem) xor 0x5412
    }

    private fun setFunction(
        modules: Array<BooleanArray>,
        function: Array<BooleanArray>,
        x: Int,
        y: Int,
        value: Boolean,
    ) {
        modules[y][x] = value
        function[y][x] = true
    }

    private fun reedSolomonRemainder(data: ByteArray, degree: Int): ByteArray {
        val divisor = reedSolomonDivisor(degree)
        val result = ByteArray(degree)
        data.forEach { byte ->
            val factor = (byte.toInt() xor result[0].toInt()) and 0xff
            result.copyInto(result, 0, 1, degree)
            result[degree - 1] = 0
            for (i in 0 until degree) {
                result[i] = (result[i].toInt() xor reedSolomonMultiply(divisor[i].toInt(), factor)).toByte()
            }
        }
        return result
    }

    private fun reedSolomonDivisor(degree: Int): ByteArray {
        val result = ByteArray(degree)
        result[degree - 1] = 1
        var root = 1
        for (i in 0 until degree) {
            for (j in 0 until degree) {
                result[j] = reedSolomonMultiply(result[j].toInt(), root).toByte()
                if (j + 1 < degree) result[j] = (result[j].toInt() xor result[j + 1].toInt()).toByte()
            }
            root = reedSolomonMultiply(root, 0x02)
        }
        return result
    }

    private fun reedSolomonMultiply(x: Int, y: Int): Int {
        var z = 0
        for (i in 7 downTo 0) {
            z = (z shl 1) xor if ((z ushr 7) != 0) 0x11D else 0
            if (((y ushr i) and 1) != 0) z = z xor x
        }
        return z and 0xff
    }
}
