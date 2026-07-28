package com.poctra.wallet.dapp

import com.poctra.wallet.data.canonicalizeOrigin
import java.net.URI

/** Resolves a provider request origin without trusting page-supplied data by itself. */
object DAppBridgeOriginPolicy {
    fun resolve(
        nativeOrigin: String?,
        claimedOrigin: String?,
        expectedOrigin: String?,
    ): String? {
        val native = webOrigin(nativeOrigin)
        val claimed = webOrigin(claimedOrigin)
        val expected = webOrigin(expectedOrigin)

        if (native.isNotBlank()) {
            return native.takeIf { claimed.isBlank() || claimed == native }
        }

        if (expected.isBlank()) return null
        return expected.takeIf { claimed.isBlank() || claimed == expected }
    }

    fun originRule(value: String?): String? = webOrigin(value).takeIf(String::isNotBlank)

    private fun webOrigin(value: String?): String {
        val origin = canonicalizeOrigin(value.orEmpty())
        if (origin.isBlank()) return ""
        return runCatching {
            val uri = URI(origin)
            origin.takeIf {
                uri.host?.isNotBlank() == true &&
                    (uri.scheme.equals("https", ignoreCase = true) || uri.scheme.equals("http", ignoreCase = true))
            }.orEmpty()
        }.getOrDefault("")
    }
}
