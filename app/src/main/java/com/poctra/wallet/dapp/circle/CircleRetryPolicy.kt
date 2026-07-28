package com.poctra.wallet.dapp.circle

import java.io.IOException
import java.net.SocketTimeoutException

private const val DefaultAttempts = 3
private const val BaseDelayMillis = 180L
private const val MaxDelayMillis = 1_200L

private val TerminalCircleError = Regex(
    "(?:not\\s+found|invalid|unsupported|forbidden|denied|unauthori[sz]ed|passphrase|hash\\s+mismatch|integrity|plaintext|ciphertext|sealed\\s+circle\\s+envelope|payload\\s+length|base-?64|path\\s+traversal|address\\s+mismatch|path\\s+mismatch|size\\s+mismatch)",
    RegexOption.IGNORE_CASE,
)
private val TransientCircleError = Regex(
    "(?:timed?\\s*out|timeout|network\\s+error|failed\\s+to\\s+fetch|connection\\s+(?:reset|refused|closed)|temporar(?:y|ily)\\s+unavailable|too\\s+many\\s+requests|rate\\s*limit|node\\s+(?:busy|unavailable)|server\\s+busy|upstream|gateway\\s+timeout|service\\s+unavailable|missing\\s+(?:rpc\\s+)?response|empty\\s+(?:rpc\\s+)?response|malformed\\s+(?:rpc\\s+)?response|econnreset|econnrefused|socket\\s+hang\\s+up)",
    RegexOption.IGNORE_CASE,
)

internal fun <T> withCircleReadRetry(
    attempts: Int = DefaultAttempts,
    sleeper: (Long) -> Unit = Thread::sleep,
    operation: (Int) -> T,
): T {
    var lastFailure: Exception? = null
    repeat(attempts.coerceAtLeast(1)) { index ->
        val attempt = index + 1
        try {
            return operation(attempt)
        } catch (failure: Exception) {
            lastFailure = failure
            if (attempt >= attempts || !isTransientCircleFailure(failure)) throw failure
            val delayMillis = (BaseDelayMillis * (1L shl index)).coerceAtMost(MaxDelayMillis)
            sleeper(delayMillis)
        }
    }
    throw lastFailure ?: IllegalStateException("Circle resource request failed.")
}

internal fun isTransientCircleFailure(failure: Throwable?): Boolean {
    if (failure == null) return false
    val message = generateSequence(failure) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" ")
    if (TerminalCircleError.containsMatchIn(message)) return false
    if (failure is SocketTimeoutException) return true
    if (failure is IOException) return true
    val httpStatus = Regex("HTTP\\s+(\\d{3})", RegexOption.IGNORE_CASE)
        .find(message)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
    if (httpStatus == 408 || httpStatus == 425 || httpStatus == 429 || (httpStatus ?: 0) >= 500) return true
    return TransientCircleError.containsMatchIn(message)
}

internal fun shouldFallbackCircleLookup(failure: Throwable?): Boolean {
    val message = generateSequence(failure) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" ")
    return Regex("(?:method\\s+not\\s+found|-32601|not\\s+found|unknown\\s+(?:method|resource))", RegexOption.IGNORE_CASE)
        .containsMatchIn(message)
}

internal fun isCircleCredentialOrIntegrityFailure(failure: Throwable?): Boolean {
    if (isTransientCircleFailure(failure)) return false
    val message = generateSequence(failure) { it.cause }
        .mapNotNull { it.message }
        .joinToString(" ")
    return Regex(
        "(?:passphrase|decrypt|plaintext\\s+hash|sealed\\s+circle\\s+envelope|payload\\s+length)",
        RegexOption.IGNORE_CASE,
    ).containsMatchIn(message)
}
