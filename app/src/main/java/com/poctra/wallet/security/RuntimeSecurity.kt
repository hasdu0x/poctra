package com.poctra.wallet.security

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import com.poctra.wallet.BuildConfig
import java.io.File
import java.security.MessageDigest

enum class RuntimeSecuritySignal {
    DebuggableRelease,
    DebuggerAttached,
    SignatureMismatch,
    SignerAllowlistMissing,
    HookingFramework,
    RootIndicators,
    Emulator,
}

data class RuntimeSecurityAssessment(
    val signerSha256: Set<String>,
    val signals: Set<RuntimeSecuritySignal>,
) {
    val hasCriticalSignal: Boolean
        get() = signals.any {
            it in setOf(
                RuntimeSecuritySignal.DebuggableRelease,
                RuntimeSecuritySignal.DebuggerAttached,
                RuntimeSecuritySignal.SignatureMismatch,
                RuntimeSecuritySignal.HookingFramework,
            )
        }
}

class RuntimeSecurity(context: Context) {
    private val appContext = context.applicationContext

    fun assess(): RuntimeSecurityAssessment {
        val signerDigests = signerSha256Digests(appContext)
        val signals = buildSet {
            if (BuildConfig.SECURITY_HARDENING_ENABLED && isApplicationDebuggable()) {
                add(RuntimeSecuritySignal.DebuggableRelease)
            }
            if (BuildConfig.SECURITY_HARDENING_ENABLED && Debug.isDebuggerConnected()) {
                add(RuntimeSecuritySignal.DebuggerAttached)
            }

            val allowedSigners = configuredSignerDigests()
            if (BuildConfig.SECURITY_HARDENING_ENABLED && allowedSigners.isEmpty()) {
                add(RuntimeSecuritySignal.SignerAllowlistMissing)
            } else if (allowedSigners.isNotEmpty() && signerDigests.none(allowedSigners::contains)) {
                add(RuntimeSecuritySignal.SignatureMismatch)
            }

            if (hasHookingIndicators()) add(RuntimeSecuritySignal.HookingFramework)
            if (hasRootIndicators()) add(RuntimeSecuritySignal.RootIndicators)
            if (isLikelyEmulator()) add(RuntimeSecuritySignal.Emulator)
        }
        return RuntimeSecurityAssessment(signerSha256 = signerDigests, signals = signals)
    }

    private fun isApplicationDebuggable(): Boolean =
        appContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun configuredSignerDigests(): Set<String> = BuildConfig.ALLOWED_SIGNER_SHA256
        .split(',', ';', ' ', '\n')
        .map(::normalizeDigest)
        .filterTo(mutableSetOf()) { it.matches(Sha256HexRegex) }

    private fun hasHookingIndicators(): Boolean {
        val loadedClass = HookingClassNames.any { className ->
            runCatching { Class.forName(className, false, javaClass.classLoader) }.isSuccess
        }
        if (loadedClass) return true
        return runCatching {
            File("/proc/self/maps").useLines { lines ->
                lines.any { line -> HookingMarkers.any { marker -> line.contains(marker, ignoreCase = true) } }
            }
        }.getOrDefault(false)
    }

    private fun hasRootIndicators(): Boolean =
        Build.TAGS?.contains("test-keys", ignoreCase = true) == true ||
            RootPaths.any { File(it).exists() }

    private fun isLikelyEmulator(): Boolean {
        val fingerprint = Build.FINGERPRINT.orEmpty().lowercase()
        val model = Build.MODEL.orEmpty().lowercase()
        val product = Build.PRODUCT.orEmpty().lowercase()
        val hardware = Build.HARDWARE.orEmpty().lowercase()
        return fingerprint.startsWith("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("sdk_gphone") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            product.contains("sdk") ||
            hardware.contains("goldfish") ||
            hardware.contains("ranchu")
    }

    companion object {
        private const val Sha256HexLength = 64
        private val Sha256HexRegex = Regex("[0-9A-F]{$Sha256HexLength}")
        private val HookingClassNames = listOf(
            "de.robv.android.xposed.XposedBridge",
            "com.saurik.substrate.MS\$2",
        )
        private val HookingMarkers = listOf("frida", "xposed", "substrate", "zygisk")
        private val RootPaths = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "/su/bin/su",
            "/data/adb/magisk",
        )

        fun signerSha256Digests(context: Context): Set<String> = runCatching {
            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, PackageManager.GET_SIGNATURES)
            }
            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageInfo.signingInfo
                if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures
            }
            signatures.orEmpty().mapTo(mutableSetOf()) { signature ->
                MessageDigest.getInstance("SHA-256")
                    .digest(signature.toByteArray())
                    .joinToString("") { byte -> "%02X".format(byte) }
            }
        }.getOrDefault(emptySet())

        private fun normalizeDigest(value: String): String =
            value.filter(Char::isLetterOrDigit).uppercase()
    }
}
