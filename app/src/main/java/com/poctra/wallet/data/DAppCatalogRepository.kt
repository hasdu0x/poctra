package com.poctra.wallet.data

import android.content.Context
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

data class DAppCatalogEntry(
    val title: String,
    val description: String,
    val dappLink: String,
    val logoSvg: String,
)

class DAppCatalogRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PrefsName, Context.MODE_PRIVATE)

    fun cachedEntries(): List<DAppCatalogEntry> = prefs.getString(KeyCatalogJson, null)
        ?.let { runCatching { parseCatalog(it) }.getOrNull() }
        .orEmpty()

    suspend fun fetchEntries(): List<DAppCatalogEntry> = withContext(Dispatchers.IO) {
        val cachedJson = prefs.getString(KeyCatalogJson, null)
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(CatalogUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = NetworkTimeoutMs
                readTimeout = NetworkTimeoutMs
                instanceFollowRedirects = false
                useCaches = false
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "poctra-android/1.0")
                prefs.getString(KeyCatalogEtag, null)?.takeIf { it.isNotBlank() }?.let {
                    setRequestProperty("If-None-Match", it)
                }
            }
            when (val status = connection.responseCode) {
                HttpURLConnection.HTTP_NOT_MODIFIED -> {
                    require(!cachedJson.isNullOrBlank()) { "The dApp catalog cache is empty." }
                    parseCatalog(cachedJson)
                }

                in 200..299 -> {
                    val body = connection.inputStream.use { it.readLimited(MaxCatalogBytes) }
                        .toString(Charsets.UTF_8)
                    val entries = parseCatalog(body)
                    prefs.edit()
                        .putString(KeyCatalogJson, body)
                        .putString(KeyCatalogEtag, connection.getHeaderField("ETag").orEmpty())
                        .apply()
                    entries
                }

                else -> throw IllegalStateException("dApp catalog returned HTTP $status.")
            }
        } catch (error: Exception) {
            if (!cachedJson.isNullOrBlank()) {
                parseCatalog(cachedJson)
            } else {
                throw IllegalStateException(error.message ?: "Unable to load the dApp catalog.", error)
            }
        } finally {
            connection?.disconnect()
        }
    }

    internal fun parseCatalog(rawJson: String): List<DAppCatalogEntry> {
        require(rawJson.toByteArray(Charsets.UTF_8).size <= MaxCatalogBytes) { "dApp catalog is too large." }
        val root = JSONObject(rawJson)
        require(root.optInt("schema_version", -1) == SupportedSchemaVersion) { "Unsupported dApp catalog schema." }
        val items = root.optJSONArray("dapps") ?: throw IllegalArgumentException("dApp catalog is missing dapps.")
        require(items.length() <= MaxCatalogEntries) { "dApp catalog has too many entries." }
        val entries = buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val title = item.optString("title").trim()
                val description = item.optString("description").trim()
                val dappLink = item.optString("dapp_link").trim()
                val logoSvg = item.optString("logo_svg").trim()
                if (
                    title.isBlank() || title.length > MaxTitleLength ||
                    description.isBlank() || description.length > MaxDescriptionLength ||
                    !isSupportedDAppLink(dappLink) || !isSafeSvgData(logoSvg)
                ) continue
                add(DAppCatalogEntry(title, description, dappLink, logoSvg))
            }
        }.distinctBy { it.dappLink.lowercase() }
        require(items.length() == 0 || entries.isNotEmpty()) { "dApp catalog has no valid entries." }
        return entries
    }

    private fun isSupportedDAppLink(value: String): Boolean {
        if (value.length !in 1..MaxLinkLength || value.any(Char::isWhitespace)) return false
        if (OctraAddress.matches(value) || OctraIdentity.matches(value)) return true
        if (value.startsWith("oct://", ignoreCase = true)) {
            val target = value.substringAfter("oct://").substringBefore('/').substringBefore('?').substringBefore('#')
            return OctraAddress.matches(target) || OctraIdentity.matches(target)
        }
        return runCatching {
            val uri = URI(value)
            uri.scheme.equals("https", ignoreCase = true) &&
                !uri.host.isNullOrBlank() && uri.userInfo == null
        }.getOrDefault(false)
    }

    private fun isSafeSvgData(value: String): Boolean {
        if (!value.startsWith(SvgDataPrefix, ignoreCase = true)) return false
        val payload = value.substring(SvgDataPrefix.length)
        val bytes = runCatching { Base64.decode(payload, Base64.DEFAULT) }.getOrNull() ?: return false
        if (bytes.isEmpty() || bytes.size > MaxSvgBytes) return false
        val svg = bytes.toString(Charsets.UTF_8).trimStart()
        return svg.startsWith("<svg", ignoreCase = true) &&
            !svg.contains("<!DOCTYPE", ignoreCase = true) &&
            !svg.contains("<!ENTITY", ignoreCase = true) &&
            !svg.contains("<script", ignoreCase = true)
    }

    private fun java.io.InputStream.readLimited(limit: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            total += count
            require(total <= limit) { "dApp catalog is too large." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    companion object {
        const val CatalogUrl = "https://m-tq.github.io/poctra/assets/dapp_list.json"
        const val SvgDataPrefix = "data:image/svg+xml;base64,"

        private const val PrefsName = "poctra_dapp_catalog"
        private const val SupportedSchemaVersion = 1
        private const val KeyCatalogJson = "catalog_json"
        private const val KeyCatalogEtag = "catalog_etag"
        private const val NetworkTimeoutMs = 8_000
        private const val MaxCatalogBytes = 256 * 1024
        private const val MaxCatalogEntries = 40
        private const val MaxSvgBytes = 64 * 1024
        private const val MaxTitleLength = 64
        private const val MaxDescriptionLength = 240
        private const val MaxLinkLength = 512
        private val OctraAddress = Regex("^oct[1-9A-HJ-NP-Za-km-z]{44}$")
        private val OctraIdentity = Regex("^(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+oct$", RegexOption.IGNORE_CASE)
    }
}
