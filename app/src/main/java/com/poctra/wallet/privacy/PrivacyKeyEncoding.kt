package com.poctra.wallet.privacy

import android.util.Base64
import java.security.MessageDigest

internal object PrivacyKeyEncoding {
    private const val ExtendsLocalKeyFlag = 1
    private const val ProofProfileFlag = 4

    fun matches(encodedPublicKey: String, expectedPublicKey: ByteArray): Boolean {
        if (encodedPublicKey.isBlank() || expectedPublicKey.isEmpty()) return false
        val expectedEncodedLength = ((expectedPublicKey.size + 2L) / 3L) * 4L
        val maximumWhitespace = (expectedEncodedLength / 16L).coerceAtLeast(4_096L)
        if (encodedPublicKey.length.toLong() > expectedEncodedLength + maximumWhitespace) return false
        val actualEncodedLength = encodedPublicKey.count { !it.isWhitespace() }.toLong()
        if (actualEncodedLength !in (expectedEncodedLength - 2L)..expectedEncodedLength) return false
        val decoded = decode(encodedPublicKey) ?: return false
        return try {
            decoded.size == expectedPublicKey.size &&
                MessageDigest.isEqual(decoded, expectedPublicKey)
        } finally {
            decoded.fill(0)
        }
    }

    fun matchesCompatibilityFlags(flags: Int): Boolean =
        flags and ExtendsLocalKeyFlag != 0 &&
            flags and ProofProfileFlag != 0

    private fun decode(value: String): ByteArray? {
        val encoded = value.trim()
        return runCatching { Base64.decode(encoded, Base64.DEFAULT) }
            .recoverCatching { Base64.decode(encoded, Base64.URL_SAFE or Base64.NO_WRAP) }
            .getOrNull()
            ?.takeIf(ByteArray::isNotEmpty)
    }
}
