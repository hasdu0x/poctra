package com.poctra.wallet.dapp.circle

internal enum class CircleResourceMode {
    PublicRead,
    SealedRead,
}

internal fun parseCircleResourceMode(value: String?): CircleResourceMode = when (value.orEmpty().trim().lowercase()) {
    "sealed_read" -> CircleResourceMode.SealedRead
    "public", "public_read", "public_resources" -> CircleResourceMode.PublicRead
    else -> throw IllegalArgumentException("Unsupported Circle resource mode: ${value.orEmpty().trim()}")
}

internal fun resolveCircleResourceMode(
    resourceMode: String?,
    privacyClass: String? = null,
    browserMode: String? = null,
    requestedMode: String? = null,
): CircleResourceMode {
    resourceMode?.trim()?.takeIf(String::isNotBlank)?.let { return parseCircleResourceMode(it) }
    when (privacyClass.orEmpty().trim().lowercase()) {
        "sealed", "sealed_read", "native_sealed" -> return CircleResourceMode.SealedRead
        "public", "public_read", "public_resources" -> return CircleResourceMode.PublicRead
    }
    when (browserMode.orEmpty().trim().lowercase()) {
        "native_sealed" -> return CircleResourceMode.SealedRead
        "gateway_allowed" -> return CircleResourceMode.PublicRead
    }
    requestedMode?.trim()?.takeIf(String::isNotBlank)?.let { return parseCircleResourceMode(it) }
    throw IllegalArgumentException("Circle metadata does not declare a supported resource mode.")
}

internal val CircleResourceMode.wireValue: String
    get() = when (this) {
        CircleResourceMode.PublicRead -> "public_read"
        CircleResourceMode.SealedRead -> "sealed_read"
    }
