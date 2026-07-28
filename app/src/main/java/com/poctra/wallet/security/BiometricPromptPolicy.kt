package com.poctra.wallet.security

import java.util.concurrent.atomic.AtomicBoolean

class BiometricPromptGate {
    private val active = AtomicBoolean(false)

    fun tryStart(): Boolean = active.compareAndSet(false, true)

    fun finish() {
        active.set(false)
    }

    fun isActive(): Boolean = active.get()
}

internal object BiometricPromptPolicy {
    private const val ErrorCanceled = 5
    private const val ErrorUserCanceled = 10
    private const val ErrorNegativeButton = 13

    fun isCancellation(errorCode: Int): Boolean = errorCode == ErrorCanceled ||
        errorCode == ErrorUserCanceled ||
        errorCode == ErrorNegativeButton
}
