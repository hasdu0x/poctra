package com.poctra.wallet.core

import java.math.BigInteger

object Base58 {
    private const val Alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""

        var number = BigInteger(1, input)
        val base = BigInteger.valueOf(58)
        val out = StringBuilder()

        while (number > BigInteger.ZERO) {
            val divRem = number.divideAndRemainder(base)
            out.insert(0, Alphabet[divRem[1].toInt()])
            number = divRem[0]
        }

        input.takeWhile { it == 0.toByte() }.forEach { _ ->
            out.insert(0, '1')
        }

        return out.toString()
    }
}
