package com.poctra.wallet.data

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

internal class PrivacyRegistrationCache(
    private val ttlMs: Long = DefaultTtlMs,
    private val maxEntries: Int = DefaultMaxEntries,
    private val nowMs: () -> Long = { System.nanoTime() / 1_000_000L },
) {
    private val verifiedAtByKey = ConcurrentHashMap<String, Long>()

    fun contains(rpcUrl: String, address: String, publicKey: ByteArray): Boolean {
        val key = cacheKey(rpcUrl, address, publicKey)
        val verifiedAt = verifiedAtByKey[key] ?: return false
        val age = nowMs() - verifiedAt
        if (age in 0 until ttlMs) return true
        verifiedAtByKey.remove(key, verifiedAt)
        return false
    }

    fun remember(rpcUrl: String, address: String, publicKey: ByteArray) {
        pruneExpired()
        val key = cacheKey(rpcUrl, address, publicKey)
        if (verifiedAtByKey.size >= maxEntries && !verifiedAtByKey.containsKey(key)) {
            verifiedAtByKey.minByOrNull { it.value }
                ?.key
                ?.let(verifiedAtByKey::remove)
        }
        verifiedAtByKey[key] = nowMs()
    }

    fun invalidate(address: String, rpcUrl: String? = null) {
        val normalizedAddress = address.trim().lowercase()
        val normalizedRpc = rpcUrl?.let(::normalizeRpc)
        verifiedAtByKey.keys.removeAll { key ->
            val parts = key.split('|', limit = 3)
            parts.size == 3 &&
                parts[1] == normalizedAddress &&
                (normalizedRpc == null || parts[0] == normalizedRpc)
        }
    }

    private fun pruneExpired() {
        val now = nowMs()
        verifiedAtByKey.entries.removeAll { (_, verifiedAt) ->
            val age = now - verifiedAt
            age !in 0 until ttlMs
        }
    }

    private fun cacheKey(rpcUrl: String, address: String, publicKey: ByteArray): String {
        val fingerprint = MessageDigest.getInstance("SHA-256")
            .digest(publicKey)
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
        return "${normalizeRpc(rpcUrl)}|${address.trim().lowercase()}|$fingerprint"
    }

    private fun normalizeRpc(value: String): String = value.trim().trimEnd('/')

    private companion object {
        const val DefaultTtlMs = 15 * 60 * 1_000L
        const val DefaultMaxEntries = 64
    }
}
