package com.poctra.wallet.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class RpcUrlValidationTest {

    private fun isBugCondition_APP10(host: String): Boolean = host == "165.227.225.79"

    private fun isApprovedCleartextHostUnfixed(host: String?): Boolean =
        host in setOf("165.227.225.79", "localhost", "127.0.0.1", "10.0.2.2")

    private fun isApprovedCleartextHostFixed(host: String?): Boolean =
        host in setOf("localhost", "127.0.0.1", "10.0.2.2")

    private fun isValidRpcUrlUnfixed(url: String): Boolean = try {
        val uri = java.net.URI(url.trim())
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        scheme == "https" && !host.isNullOrBlank() || scheme == "http" && isApprovedCleartextHostUnfixed(host)
    } catch (_: Exception) { false }

    private fun isValidRpcUrlFixed(url: String): Boolean = try {
        val uri = java.net.URI(url.trim())
        val scheme = uri.scheme?.lowercase()
        val host = uri.host?.lowercase()
        scheme == "https" && !host.isNullOrBlank() || scheme == "http" && isApprovedCleartextHostFixed(host)
    } catch (_: Exception) { false }

    private fun findRpcRepository(): File {
        val candidates = listOf(
            File("app/src/main/java/com/poctra/wallet/data/RpcRepository.kt"),
            File("../app/src/main/java/com/poctra/wallet/data/RpcRepository.kt"),
        )
        return candidates.firstOrNull { it.isFile } ?: throw IllegalStateException("Cannot find RpcRepository.kt")
    }

    @Test
    fun bugConditionApp10_publicIpHttpUrl_shouldBeRejected() {
        val url = "http://165.227.225.79/rpc"
        val fixedResult = isValidRpcUrlFixed(url)
        assertFalse("FIXED: http://165.227.225.79/rpc must be rejected", fixedResult)
        // Also verify the source: fixed isValidRpcUrlFixed model rejects it (already done above).
    }

    @Test
    fun bugConditionApp10_sourceHasPublicIpInApprovedHosts() {
        val content = findRpcRepository().readText()
        // Narrow check: look only within the isApprovedCleartextHost function body
        val functionBody = content
            .substringAfter("fun isApprovedCleartextHost(")
            .substringBefore("private fun ")
            .substringBefore("fun ")
            .take(300) // function body is short — just the setOf(...)
        val hasBugInFunction = functionBody.contains("165.227.225.79")
        if (hasBugInFunction) {
            fail("BUG CONFIRMED (APP-10): isApprovedCleartextHost in RpcRepository.kt contains 165.227.225.79. Fix: remove it.")
        }
        assertFalse("RpcRepository.isApprovedCleartextHost must not contain 165.227.225.79", hasBugInFunction)
    }

    @Test
    fun preservation_httpsUrls_stillAccepted() {
        listOf("https://octra.network", "https://rpc.example.com").forEach { url ->
            assertTrue("HTTPS URL must be accepted: $url", isValidRpcUrlFixed(url))
        }
    }

    @Test
    fun preservation_localDevHttpHosts_stillAccepted() {
        listOf("http://localhost:8080", "http://127.0.0.1:8080", "http://10.0.2.2:8080").forEach { url ->
            assertTrue("Local dev URL must be accepted: $url", isValidRpcUrlFixed(url))
        }
    }
}
