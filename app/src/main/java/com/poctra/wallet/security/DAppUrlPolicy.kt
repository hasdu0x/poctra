package com.poctra.wallet.security

import java.net.URI

object DAppUrlPolicy {
    fun normalizeBrowserUrl(input: String): String {
        val raw = input.trim()
        require(raw.isNotBlank()) { "Enter a dApp URL." }

        val explicitScheme = raw.indexOf(':')
            .takeIf { it > 0 }
            ?.let { raw.take(it).matches(Regex("^[A-Za-z][A-Za-z0-9+.-]*$")) }
            ?: false
        if (explicitScheme &&
            !raw.startsWith("http://", ignoreCase = true) &&
            !raw.startsWith("https://", ignoreCase = true)
        ) {
            throw IllegalArgumentException("Only HTTPS dApp URLs are supported.")
        }

        val candidate = if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            raw
        } else {
            "https://$raw"
        }
        val uri = candidate.toUriOrThrow("Enter a valid dApp URL.")
        val scheme = uri.scheme?.lowercase().orEmpty()
        val host = uri.normalizedHost()

        require(uri.userInfo.isNullOrBlank()) { "URLs with embedded credentials are blocked." }
        require(host.isNotBlank()) { "Enter a valid dApp URL." }
        require(!hasPunycodeLabel(host)) { "Punycode domains are blocked by Phishing Guard." }
        require(!isIpAddress(host) || isLocalDevelopmentHost(host)) {
            "IP-address dApps are blocked unless they are local development hosts."
        }
        require(scheme == "https" || (scheme == "http" && isLocalDevelopmentHost(host))) {
            "Use HTTPS, or a local development host."
        }
        return candidate
    }

    fun normalizeDomainHost(input: String): String {
        val raw = input.trim().lowercase()
        require(raw.isNotBlank()) { "Enter a domain." }
        val withScheme = if (raw.startsWith("http://") || raw.startsWith("https://")) raw else "https://$raw"
        val uri = withScheme.toUriOrThrow("Enter a valid domain host.")
        require(uri.userInfo.isNullOrBlank()) { "Domain must not include credentials." }
        val host = uri.normalizedHost()
        require(host.matches(Regex("^[a-z0-9.-]+\\.[a-z]{2,}$"))) { "Enter a valid domain host." }
        require(!hasPunycodeLabel(host)) { "Punycode domains are blocked by Phishing Guard." }
        return host
    }

    fun isSuspiciousOrigin(origin: String): Boolean {
        val lower = origin.lowercase()
        val host = runCatching { URI(lower).normalizedHost() }.getOrDefault(lower)
        return !lower.startsWith("https://") || hasPunycodeLabel(host) || isIpAddress(host)
    }

    private fun String.toUriOrThrow(message: String): URI =
        runCatching { URI(this) }.getOrElse { throw IllegalArgumentException(message) }

    private fun URI.normalizedHost(): String =
        host?.lowercase()?.removePrefix("www.")?.trim().orEmpty()

    private fun isLocalDevelopmentHost(host: String): Boolean =
        host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2" || host == "::1" || host == "[::1]"

    private fun isIpAddress(host: String): Boolean =
        host.matches(Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")) || host.contains(':')

    private fun hasPunycodeLabel(host: String): Boolean =
        host.split('.').any { it.startsWith("xn--") }
}
