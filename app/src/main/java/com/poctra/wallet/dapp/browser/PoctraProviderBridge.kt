package com.poctra.wallet.dapp.browser

import android.util.Log
import android.webkit.JavascriptInterface
import com.poctra.wallet.BuildConfig
import com.poctra.wallet.CircleDownloadResource
import com.poctra.wallet.PoctraController
import com.poctra.wallet.dapp.DAppBridgeOriginPolicy
import com.poctra.wallet.dapp.EvmDAppSupportPolicy
import org.json.JSONObject

/**
 * Native transport boundary for the three app protocols supported by Poctra's browser.
 * Common web traffic never enters this class; it remains owned by Android WebView.
 */
internal class PoctraProviderBridge(
    private val controller: PoctraController,
    private val currentOrigin: () -> String,
    private val expectedOrigin: () -> String,
    private val expectedToken: () -> String,
    private val onPageInteraction: () -> Unit,
    private val writeClipboard: (String) -> Boolean,
    private val evaluate: (String) -> Unit,
) {
    @JavascriptInterface
    fun pageInteraction(token: String) {
        if (token.isBlank() || token != expectedToken()) return
        onPageInteraction()
    }

    @JavascriptInterface
    fun request(payload: String) {
        if (payload.length > MaxProviderBridgePayloadChars) {
            rejectOctra(providerRequestId(payload), -32600, "Wallet request payload is too large.")
            return
        }
        val json = runCatching { JSONObject(payload) }.getOrNull()
        val id = json?.optString("id").orEmpty()
        val token = json?.optString("bridgeToken").orEmpty()
        val method = json?.optString("method").orEmpty()
        if (BuildConfig.DEBUG) {
            Log.d(
                LogTag,
                "octra bridge received id=$id method=$method claimed=${json?.optString("origin").orEmpty()} token=${token.isNotBlank()}",
            )
        }
        if (token.isBlank() || token != expectedToken()) {
            if (method == "octra_requestAccounts") {
                controller.showError("Unable to connect dApp. Refresh the page and try again.")
            }
            rejectOctra(id, -32600, "Invalid poctra bridge token.")
            return
        }
        val origin = resolveOrigin(json)
        if (origin.isBlank()) {
            if (method == "octra_requestAccounts") {
                controller.showError("Unable to verify this dApp page. Refresh and try again.")
            }
            rejectOctra(id, 4100, "Unable to verify the current dApp origin.")
            return
        }
        if (BuildConfig.DEBUG) Log.d(LogTag, "octra bridge accepted id=$id method=$method origin=$origin")
        controller.handleOctraDAppBridgeRequest(
            originRaw = origin,
            payload = payload,
            onSuccess = { resultJson ->
                evaluate(
                    "if (window.__poctraAndroidRfcO1Resolve) {" +
                        "window.__poctraAndroidRfcO1Resolve(${JSONObject.quote(id)},$resultJson);" +
                        "} else if (window.__poctraResolve) {" +
                        "window.__poctraResolve(${JSONObject.quote(id)},$resultJson);}",
                )
            },
            onError = { code, message -> rejectOctra(id, code, message) },
        )
    }

    @JavascriptInterface
    fun evmRequest(payload: String) {
        if (payload.length > MaxProviderBridgePayloadChars) {
            rejectEvm(providerRequestId(payload), -32600, "EVM wallet request payload is too large.")
            return
        }
        val json = runCatching { JSONObject(payload) }.getOrNull()
        val id = json?.optString("id").orEmpty()
        val token = json?.optString("bridgeToken").orEmpty()
        if (BuildConfig.DEBUG) {
            Log.d(
                LogTag,
                "evm bridge id=$id method=${json?.optString("method").orEmpty()} " +
                    "claimed=${json?.optString("origin").orEmpty()} " +
                    "native=${runCatching { currentOrigin() }.getOrNull().orEmpty()} " +
                    "expected=${runCatching { expectedOrigin() }.getOrNull().orEmpty()} token=${token.isNotBlank()}",
            )
        }
        if (token.isBlank() || token != expectedToken()) {
            rejectEvm(id, -32600, "Invalid poctra EVM bridge token.")
            return
        }
        val origin = resolveOrigin(json)
        if (origin.isBlank()) {
            if (json?.optString("method") in setOf("eth_requestAccounts", "wallet_requestPermissions")) {
                controller.showError("Unable to verify this dApp page. Refresh and try again.")
            }
            rejectEvm(id, 4100, "Unable to verify the current dApp origin.")
            return
        }
        if (!EvmDAppSupportPolicy.isSupported(origin)) {
            rejectEvm(id, 4200, "This dApp is not supported by Poctra's injected EVM wallet.")
            return
        }
        if (BuildConfig.DEBUG) Log.d(LogTag, "evm bridge accepted id=$id origin=$origin")
        controller.handleEvmDAppBridgeRequest(
            originRaw = origin,
            payload = payload,
            onSuccess = { resultJson ->
                evaluate(
                    "window.__poctraEvmResolve && window.__poctraEvmResolve(" +
                        "${JSONObject.quote(id)},$resultJson);",
                )
            },
            onError = { code, message -> rejectEvm(id, code, message) },
        )
    }

    @JavascriptInterface
    fun circleRequest(payload: String) {
        val json = runCatching { JSONObject(payload) }.getOrNull()
        val id = json?.optString("id").orEmpty()
        if (!hasValidToken(json)) {
            rejectCircle(id, -32600, "Invalid poctra circle bridge token.")
            return
        }
        controller.handleCircleBridgeRequest(
            payload = payload,
            onSuccess = { resultJson ->
                evaluate(
                    "window.__poctraCircleResolve && window.__poctraCircleResolve(" +
                        "${JSONObject.quote(id)},$resultJson);",
                )
            },
            onError = { code, message -> rejectCircle(id, code, message) },
        )
    }

    @JavascriptInterface
    fun circleDownload(payload: String) {
        val json = runCatching { JSONObject(payload) }.getOrNull()
        val id = json?.optString("id").orEmpty()
        if (!hasValidToken(json)) {
            rejectCircleDownload(id, "Invalid poctra circle download token.")
            return
        }
        val resources = parseDownloadResources(json)
        controller.downloadCircleAssets(
            resources = resources,
            fileName = json?.optString("fileName").orEmpty(),
            mimeType = json?.optString("mimeType").orEmpty(),
            expectedSha256 = json?.optString("sha256")?.ifBlank { null },
            onProgress = { completed, total ->
                evaluate(
                    "window.__poctraCircleDownloadProgress && window.__poctraCircleDownloadProgress(" +
                        "${JSONObject.quote(id)},$completed,$total);",
                )
            },
            onSuccess = {
                evaluate(
                    "window.__poctraCircleDownloadDone && window.__poctraCircleDownloadDone(" +
                        "${JSONObject.quote(id)});",
                )
            },
            onError = { message -> rejectCircleDownload(id, message) },
        )
    }

    @JavascriptInterface
    fun circleClipboardWrite(payload: String) {
        val json = runCatching { JSONObject(payload) }.getOrNull()
        val id = json?.optString("id").orEmpty()
        if (!hasValidToken(json)) {
            rejectCircleClipboard(id, "Invalid poctra circle clipboard token.")
            return
        }
        val text = json?.optString("text").orEmpty()
        if (text.length > MaxCircleClipboardChars) {
            rejectCircleClipboard(id, "Clipboard text is too large.")
            return
        }
        if (!writeClipboard(text)) {
            rejectCircleClipboard(id, "Unable to copy text.")
            return
        }
        evaluate(
            "window.__poctraCircleClipboardResolve && window.__poctraCircleClipboardResolve(" +
                "${JSONObject.quote(id)});",
        )
    }

    private fun resolveOrigin(json: JSONObject?): String = DAppBridgeOriginPolicy.resolve(
        nativeOrigin = runCatching { currentOrigin() }.getOrNull(),
        claimedOrigin = json?.optString("origin"),
        expectedOrigin = runCatching { expectedOrigin() }.getOrNull(),
    ).orEmpty()

    private fun hasValidToken(json: JSONObject?): Boolean {
        val token = json?.optString("bridgeToken").orEmpty()
        return token.isNotBlank() && token == expectedToken()
    }

    private fun parseDownloadResources(json: JSONObject?): List<CircleDownloadResource> {
        val resources = mutableListOf<CircleDownloadResource>()
        json?.optJSONArray("resources")?.let { items ->
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val circleId = item.optString("circleId").trim()
                val path = item.optString("path").trim()
                if (circleId.isNotBlank() && path.isNotBlank()) resources += CircleDownloadResource(circleId, path)
            }
        }
        if (resources.isNotEmpty()) return resources

        val fallbackCircleId = controller.selectedCircleTarget?.circleId.orEmpty()
        json?.optJSONArray("paths")?.let { paths ->
            for (index in 0 until paths.length()) {
                paths.optString(index).takeIf(String::isNotBlank)?.let { path ->
                    resources += CircleDownloadResource(fallbackCircleId, path)
                }
            }
        }
        return resources
    }

    private fun rejectOctra(id: String, code: Int, message: String) {
        if (id.isBlank()) return
        evaluate(
            "if (window.__poctraAndroidRfcO1Reject) {" +
                "window.__poctraAndroidRfcO1Reject(${JSONObject.quote(id)},$code,${JSONObject.quote(message)});" +
                "} else if (window.__poctraReject) {" +
                "window.__poctraReject(${JSONObject.quote(id)},$code,${JSONObject.quote(message)});}",
        )
    }

    private fun rejectEvm(id: String, code: Int, message: String) {
        if (id.isBlank()) return
        evaluate(
            "window.__poctraEvmReject && window.__poctraEvmReject(" +
                "${JSONObject.quote(id)},$code,${JSONObject.quote(message)});",
        )
    }

    private fun rejectCircle(id: String, code: Int, message: String) {
        if (id.isBlank()) return
        evaluate(
            "window.__poctraCircleReject && window.__poctraCircleReject(" +
                "${JSONObject.quote(id)},$code,${JSONObject.quote(message)});",
        )
    }

    private fun rejectCircleDownload(id: String, message: String) {
        if (id.isBlank()) return
        evaluate(
            "window.__poctraCircleDownloadError && window.__poctraCircleDownloadError(" +
                "${JSONObject.quote(id)},${JSONObject.quote(message)});",
        )
    }

    private fun rejectCircleClipboard(id: String, message: String) {
        if (id.isBlank()) return
        evaluate(
            "window.__poctraCircleClipboardReject && window.__poctraCircleClipboardReject(" +
                "${JSONObject.quote(id)},${JSONObject.quote(message)});",
        )
    }
}

private const val LogTag = "PoctraDApp"
private const val MaxProviderBridgePayloadChars = 524_288
private const val MaxCircleClipboardChars = 16_384
private val ProviderRequestIdPattern = Regex("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]{1,128})\\\"")

private fun providerRequestId(payload: String): String =
    ProviderRequestIdPattern.find(payload.take(2_048))?.groupValues?.getOrNull(1).orEmpty()
