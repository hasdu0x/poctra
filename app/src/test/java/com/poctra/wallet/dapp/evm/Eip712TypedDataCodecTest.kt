package com.poctra.wallet.dapp.evm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Bug condition exploration test for APP-01.
 *
 * **Validates: Requirements 1.1, 1.2**
 *
 * This test MUST FAIL on unfixed code — failure confirms the bug exists.
 * DO NOT fix the test or the production code when it fails.
 *
 * Bug condition: `isBugCondition_APP01(payload)` where
 *   `length(prettyValue(payload.message)) > 4096`
 *
 * Expected (correct) behavior: `parse(oversizedPayload)` throws
 *   `IllegalArgumentException` containing "too large to review".
 *
 * Actual (buggy) behavior on unfixed code: `parse()` returns `Eip712TypedData`
 *   silently truncating the preview without rejecting the request.
 */
class Eip712TypedDataCodecTest {

    /**
     * Mirrors the bug-condition predicate.
     * Reconstructs `prettyValue` inline (the real one is private in Eip712TypedDataCodec)
     * to measure the preview length before calling parse().
     */
    private fun isBugCondition_APP01(payload: Eip712Payload): Boolean {
        fun prettyValue(value: Any?, depth: Int): String {
            if (depth > 8) return "..."
            return when (value) {
                null -> "null"
                is Map<*, *> -> value.entries.joinToString(", ", "{", "}") { (key, item) ->
                    "\"$key\": ${prettyValue(item, depth + 1)}"
                }
                is List<*> -> value.joinToString(", ", "[", "]") { prettyValue(it, depth + 1) }
                is String -> "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""
                else -> value.toString()
            }
        }
        return prettyValue(payload.message, 0).length > 4096
    }

    @Test
    fun bugCondition_APP01_oversizedPermitBatchNotRejected() {
        // Build a PermitBatch with 25 allowances.
        // Each entry contributes ~200 chars; 25 × 200 = ~5,000 chars for the array alone —
        // comfortably above the 4,096-char threshold.
        // uint160 max = 2^160 - 1 = 1461501637330902918203684832716283019655932542975
        val uint160Max = "1461501637330902918203684832716283019655932542975"
        val allowances = (1..25).map { i ->
            mapOf(
                "token" to "0x${"a".repeat(40)}",
                "amount" to uint160Max,
                "expiration" to "1800000000",
                "nonce" to "$i",
            )
        }

        val oversizedPayload = Eip712Payload(
            primaryType = "PermitBatch",
            types = mapOf(
                "PermitDetails" to listOf(
                    Eip712Field("token", "address"),
                    Eip712Field("amount", "uint160"),
                    Eip712Field("expiration", "uint48"),
                    Eip712Field("nonce", "uint48"),
                ),
                "PermitBatch" to listOf(
                    Eip712Field("details", "PermitDetails[]"),
                    Eip712Field("spender", "address"),
                    Eip712Field("sigDeadline", "uint256"),
                ),
            ),
            domain = mapOf(
                "name" to "Permit2",
                "chainId" to 1,
                "verifyingContract" to "0x000000000022D473030F116dDEE9F6B43aC78BA3",
            ),
            message = mapOf(
                "details" to allowances,
                "spender" to "0xDEF171Fe48CF0115B1d80b88dc8eAB59176FEe57",
                "sigDeadline" to "2000000000",
            ),
        )

        // Confirm the bug condition holds before invoking parse().
        assertTrue(
            "Bug condition not met: prettyValue output should exceed 4,096 chars with 25 allowances",
            isBugCondition_APP01(oversizedPayload),
        )

        // EXPECTED: parse() should throw IllegalArgumentException containing "too large to review".
        // ACTUAL on unfixed code: parse() returns Eip712TypedData without throwing (BUG).
        try {
            Eip712TypedDataCodec.parse(oversizedPayload)
            // Reaching this line means no exception was thrown — the bug is confirmed.
            fail(
                "BUG CONFIRMED (APP-01): parse() returned Eip712TypedData for a PermitBatch with 25 allowances " +
                    "whose prettyValue output exceeds 4,096 characters. " +
                    "Expected IllegalArgumentException containing 'too large to review' but no exception was thrown. " +
                    "Counterexample: PermitBatch with 25 token allowances (token=0xaaaa...aaaa, " +
                    "amount=1461501637330902918203684832716283019655932542975 (uint160 max), " +
                    "expiration=1800000000, nonce=1..25) parses successfully and returns Eip712TypedData " +
                    "instead of rejecting with an oversized-preview error.",
            )
        } catch (e: IllegalArgumentException) {
            // This is the CORRECT behavior — but will NOT be reached on unfixed code.
            assertTrue(
                "Exception message should contain 'too large to review' but was: ${e.message}",
                e.message?.contains("too large to review", ignoreCase = true) == true,
            )
        }
    }

    // -------------------------------------------------------------------------
    // Preservation property tests — APP-01 (BEFORE implementing fix)
    //
    // Property 2: Preservation — Normal EIP-712 Signing Unaffected
    //
    // For any EIP-712 payload where isBugCondition_APP01 does NOT hold (preview
    // fits within 4,096 characters), parse() SHALL succeed and return an
    // Eip712TypedData with a deterministic signing hash identical to what the
    // original function produces.
    //
    // **Validates: Requirements 3.1, 3.2**
    //
    // EXPECTED OUTCOME: ALL tests PASS on unfixed code.
    // This confirms the baseline behavior that the fix must preserve.
    // -------------------------------------------------------------------------

    /**
     * Baseline observation: a minimal Transfer(to, amount) payload whose preview
     * comfortably fits within 4,096 chars parses successfully on unfixed code.
     *
     * **Validates: Requirements 3.1, 3.2**
     */
    @Test
    fun preservation_APP01_minimalTransferPayload_parseSucceeds() {
        val payload = minimalTransferPayload()

        // Confirm the bug condition does NOT hold (preview ≤ 4,096 chars).
        assertFalse(
            "Preservation precondition failed: prettyValue output must be ≤ 4,096 chars for the Transfer payload",
            isBugCondition_APP01(payload),
        )

        // parse() must not throw for a normal-sized payload.
        val result = Eip712TypedDataCodec.parse(payload)

        assertNotNull("parse() must return Eip712TypedData for a normal-sized Transfer payload", result)
        assertEquals("Transfer", result.primaryType)
        assertNotNull("signingHash must not be null", result.signingHash)
        assertEquals(32, result.signingHash.size)
        assertTrue("review.messagePreview must not contain '[truncated]'", !result.review.messagePreview.contains("[truncated]"))
    }

    /**
     * Preservation property: the signing hash is deterministic — calling parse()
     * twice on the same payload always produces the same signing hash.
     *
     * **Validates: Requirements 3.1, 3.2**
     */
    @Test
    fun preservation_APP01_signingHashIsDeterministic() {
        val payload = minimalTransferPayload()

        assertFalse(
            "Preservation precondition failed: payload preview must be ≤ 4,096 chars",
            isBugCondition_APP01(payload),
        )

        val first = Eip712TypedDataCodec.parse(payload)
        val second = Eip712TypedDataCodec.parse(payload)

        assertTrue(
            "Signing hash must be deterministic: two calls on the same payload must produce identical hashes",
            first.signingHash.contentEquals(second.signingHash),
        )
        assertEquals(
            "review.signingHash (hex) must be identical across two calls",
            first.review.signingHash,
            second.review.signingHash,
        )
    }

    /**
     * Property-based preservation sweep: generate multiple distinct small EIP-712 payloads
     * (varying recipient addresses, amounts, and chain IDs), confirm each has a preview
     * ≤ 4,096 chars, then assert parse() succeeds and each returns a 32-byte signing hash.
     *
     * This covers the "for all small/normal payloads" requirement across a range of inputs.
     *
     * **Validates: Requirements 3.1, 3.2**
     */
    @Test
    fun preservation_APP01_propertyBased_allSmallPayloadsParseSucessfully() {
        // Generate a representative spread of valid Transfer payloads — varying recipient,
        // amount, chainId — all with previews well within 4,096 chars.
        val testCases = buildList {
            // Varying chainIds (mainnet, polygon, arbitrum, optimism, BSC).
            // Use fixed valid 40-hex-char addresses (padded to exactly 40 hex chars).
            for ((index, chainId) in listOf(1, 137, 42161, 10, 56).withIndex()) {
                // Produce a deterministic but distinct valid 40-hex address for each chainId.
                val hexIndex = index.toString(16).padStart(2, '0')
                add(transferPayload(
                    to = "0x${"00".repeat(19)}$hexIndex",
                    amount = "1000000000000000000",
                    chainId = chainId,
                ))
            }
            // Varying amounts (0, 1 wei, 1 ETH, max uint256)
            for (amount in listOf("0", "1", "1000000000000000000", "115792089237316195423570985008687907853269984665640564039457584007913129639935")) {
                add(transferPayload(
                    to = "0xDEF171Fe48CF0115B1d80b88dc8eAB59176FEe57",
                    amount = amount,
                    chainId = 1,
                ))
            }
            // Minimal domain (only chainId, no name/version/contract)
            add(Eip712Payload(
                primaryType = "Transfer",
                types = mapOf(
                    "Transfer" to listOf(
                        Eip712Field("to", "address"),
                        Eip712Field("amount", "uint256"),
                    ),
                ),
                domain = mapOf("chainId" to 1),
                message = mapOf(
                    "to" to "0xDEF171Fe48CF0115B1d80b88dc8eAB59176FEe57",
                    "amount" to "1000000000000000000",
                ),
            ))
        }

        testCases.forEachIndexed { index, payload ->
            // Confirm bug condition does NOT hold.
            assertFalse(
                "Test case $index: bug condition must NOT hold (preview must be ≤ 4,096 chars)",
                isBugCondition_APP01(payload),
            )

            // parse() must succeed without throwing.
            val result = runCatching { Eip712TypedDataCodec.parse(payload) }
                .getOrElse { e ->
                    fail("Test case $index: parse() threw unexpectedly: ${e.message}")
                    return
                }

            assertEquals("Test case $index: primaryType must be preserved", payload.primaryType, result.primaryType)
            assertEquals("Test case $index: signingHash must be 32 bytes", 32, result.signingHash.size)
            assertFalse("Test case $index: messagePreview must not contain '[truncated]'",
                result.review.messagePreview.contains("[truncated]"))
        }
    }

    /**
     * Preservation: signing hashes differ across distinct payloads — i.e., the hash
     * correctly reflects the message content rather than being a constant.
     *
     * **Validates: Requirements 3.1, 3.2**
     */
    @Test
    fun preservation_APP01_distinctPayloadsProduceDifferentSigningHashes() {
        val payload1 = transferPayload(
            to = "0xDEF171Fe48CF0115B1d80b88dc8eAB59176FEe57",
            amount = "1000000000000000000",
            chainId = 1,
        )
        val payload2 = transferPayload(
            to = "0xDEF171Fe48CF0115B1d80b88dc8eAB59176FEe57",
            amount = "2000000000000000000", // different amount
            chainId = 1,
        )

        assertFalse(isBugCondition_APP01(payload1))
        assertFalse(isBugCondition_APP01(payload2))

        val result1 = Eip712TypedDataCodec.parse(payload1)
        val result2 = Eip712TypedDataCodec.parse(payload2)

        assertFalse(
            "Distinct payloads (different amounts) must produce different signing hashes",
            result1.signingHash.contentEquals(result2.signingHash),
        )
    }

    // -------------------------------------------------------------------------
    // Shared helpers
    // -------------------------------------------------------------------------

    /** Returns a minimal Transfer(to, amount) payload with a preview well under 4,096 chars. */
    private fun minimalTransferPayload(): Eip712Payload = transferPayload(
        to = "0xDEF171Fe48CF0115B1d80b88dc8eAB59176FEe57",
        amount = "1000000000000000000",
        chainId = 1,
    )

    private fun transferPayload(to: String, amount: String, chainId: Int): Eip712Payload =
        Eip712Payload(
            primaryType = "Transfer",
            types = mapOf(
                "Transfer" to listOf(
                    Eip712Field("to", "address"),
                    Eip712Field("amount", "uint256"),
                ),
            ),
            domain = mapOf(
                "name" to "MyToken",
                "version" to "1",
                "chainId" to chainId,
                "verifyingContract" to "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
            ),
            message = mapOf(
                "to" to to,
                "amount" to amount,
            ),
        )
}
