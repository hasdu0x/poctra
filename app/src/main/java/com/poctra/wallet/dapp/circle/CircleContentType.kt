package com.poctra.wallet.dapp.circle

internal data class CircleContentType(
    val value: String,
    val mediaType: String,
    val charset: String?,
) {
    val isHtml: Boolean
        get() = mediaType == "text/html" || mediaType == "application/xhtml+xml"
    val isTextPreview: Boolean
        get() = mediaType.startsWith("text/") || mediaType in Utf8ApplicationTypes
    val isImagePreview: Boolean
        get() = mediaType in PreviewableImageTypes
    val isPreviewable: Boolean
        get() = isHtml || isTextPreview || isImagePreview
}

internal fun resolveCircleContentType(declaredContentType: String?, path: String): CircleContentType {
    val declared = declaredContentType.orEmpty().trim()
    val declaredMediaType = declared.substringBefore(';').trim().lowercase()
    val value = if (
        declared.isBlank() ||
        declaredMediaType == DefaultContentType ||
        !ValidMediaType.matches(declaredMediaType)
    ) {
        inferredCircleContentType(path)
    } else {
        declared
    }
    val mediaType = value.substringBefore(';').trim().lowercase().ifBlank { DefaultContentType }
    val charset = CharsetPattern.find(value)?.groupValues?.getOrNull(1)?.trim()?.trim('"', '\'')
        ?: if (mediaType.startsWith("text/") || mediaType in Utf8ApplicationTypes) "UTF-8" else null
    return CircleContentType(value = value, mediaType = mediaType, charset = charset)
}

internal fun inferredCircleContentType(path: String): String {
    val extension = path.substringBefore('?').substringBefore('#').substringAfterLast('.', "").lowercase()
    return ContentTypeByExtension[extension] ?: DefaultContentType
}

private const val DefaultContentType = "application/octet-stream"
private val ValidMediaType = Regex("^[a-z0-9][a-z0-9!#$&^_.+-]{0,63}/[a-z0-9][a-z0-9!#$&^_.+-]{0,63}$")
private val CharsetPattern = Regex("(?:^|;)\\s*charset\\s*=\\s*([^;]+)", RegexOption.IGNORE_CASE)
private val Utf8ApplicationTypes = setOf(
    "application/json",
    "application/javascript",
    "application/xml",
    "application/xhtml+xml",
)
private val PreviewableImageTypes = setOf(
    "image/svg+xml",
    "image/png",
    "image/jpeg",
    "image/gif",
    "image/webp",
)
private val ContentTypeByExtension = mapOf(
    "html" to "text/html; charset=utf-8",
    "htm" to "text/html; charset=utf-8",
    "css" to "text/css; charset=utf-8",
    "js" to "application/javascript; charset=utf-8",
    "mjs" to "application/javascript; charset=utf-8",
    "json" to "application/json; charset=utf-8",
    "xml" to "application/xml; charset=utf-8",
    "svg" to "image/svg+xml",
    "png" to "image/png",
    "jpg" to "image/jpeg",
    "jpeg" to "image/jpeg",
    "gif" to "image/gif",
    "webp" to "image/webp",
    "txt" to "text/plain; charset=utf-8",
)
