@file:Suppress("DEPRECATION")

package com.poctra.wallet.security

import android.annotation.TargetApi
import android.app.Activity
import android.app.KeyguardManager
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.hardware.fingerprint.FingerprintManager
import android.os.Build
import android.os.CancellationSignal
import java.util.concurrent.atomic.AtomicBoolean
import javax.crypto.Cipher

object DeviceSecurity {
    fun isBiometricAvailable(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false

        val keyguard = activity.getSystemService(KeyguardManager::class.java)
        if (keyguard?.isDeviceSecure != true) return false

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val biometricManager = activity.getSystemService(BiometricManager::class.java)
            @Suppress("DEPRECATION")
            biometricManager?.canAuthenticate() == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            @Suppress("DEPRECATION")
            val fingerprint = activity.getSystemService(FingerprintManager::class.java)
            @Suppress("DEPRECATION")
            fingerprint?.isHardwareDetected == true && fingerprint.hasEnrolledFingerprints()
        }
    }

    fun authenticateWithCipher(
        activity: Activity,
        title: String,
        subtitle: String,
        negativeText: String,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit = {},
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            onError("Biometric prompt requires Android 9 or newer.")
            return
        }

        authenticateWithCipherApi28(activity, title, subtitle, negativeText, cipher, onSuccess, onError, onCancelled)
    }

    @TargetApi(Build.VERSION_CODES.P)
    private fun authenticateWithCipherApi28(
        activity: Activity,
        title: String,
        subtitle: String,
        negativeText: String,
        cipher: Cipher,
        onSuccess: (Cipher) -> Unit,
        onError: (String) -> Unit,
        onCancelled: () -> Unit,
    ) {
        val terminalCallbackSent = AtomicBoolean(false)
        fun complete(callback: () -> Unit) {
            if (terminalCallbackSent.compareAndSet(false, true)) callback()
        }

        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle(subtitle)
            .setNegativeButton(negativeText, activity.mainExecutor) { _, _ ->
                complete(onCancelled)
            }
            .build()

        prompt.authenticate(
            BiometricPrompt.CryptoObject(cipher),
            CancellationSignal(),
            activity.mainExecutor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    val resultCipher = result.cryptoObject?.cipher
                    if (resultCipher != null) {
                        complete { onSuccess(resultCipher) }
                    } else {
                        complete { onError("Biometric prompt did not return an authenticated cipher.") }
                    }
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (BiometricPromptPolicy.isCancellation(errorCode)) {
                        complete(onCancelled)
                    } else {
                        complete { onError(errString.toString()) }
                    }
                }

                override fun onAuthenticationFailed() {
                    // The platform keeps the same prompt open and shows its own retry feedback.
                }
            },
        )
    }
}
