package com.poctra.wallet.dapp.evm

import org.junit.Assert.*
import org.junit.Test

/**
 * Bug condition exploration test for APP-14.
 *
 * Bug: `prettyValue` only escapes \\ and \". Control chars (\n, \r, \t) and Unicode bidi
 * control characters (U+202A-U+202E, U+2066-U+2069, U+200F, U+200E) are passed through
 * literally into the EIP-712 approval review UI, enabling spoofing attacks.
 */
class Eip712TypedDataCodecEscapeTest {

    private fun isBugCondition_APP14(value: String): Boolean =
        value.any { it == '\n' || it == '\r' || it == '\t' ||
            it.code in 0x202A..0x202E || it.code in 0x2066..0x2069 ||
            it == '\u200F' || it == '\u200E' }

    // =========================================================================
    // Bug condition tests
    // =========================================================================

    @Test
    fun bugConditionApp14_controlChars_mustBeEscaped() {
        val payload = Eip712Payload(
            primaryType = "Transfer",
            types = mapOf(
                "Transfer" to listOf(
                    Eip712Field("to", "address"),
                    Eip712Field("note", "string"),
                ),
                "EIP712Domain" to listOf(
                    Eip712Field("name", "string"),
                    Eip712Field("chainId", "uint256"),
                ),
            ),
            domain = mapOf("name" to "Test", "chainId" to 1),
            message = mapOf(
                "to" to "0x0000000000000000000000000000000000000001",
                "note" to "Transfer\nTo charity\r[ACTUALLY: drain all]",
            ),
        )

        val typed = Eip712TypedDataCodec.parse(payload)
        val preview = typed.review.messagePreview

        // After fix: no raw newlines or carriage returns in preview
        assertFalse(
            "BUG CONFIRMED (APP-14): prettyValue must NOT include literal \\n in output.\n" +
                "Counterexample: 'Transfer\\nTo charity...' → raw newline in approval UI.\n" +
                "Fix: add .replace('\\n', '\\\\n') etc. to prettyValue string branch.",
            preview.contains('\n') || preview.contains('\r')
        )
        // After fix: should contain escaped versions
        assertTrue(
            "Fixed prettyValue must render \\n as the literal string '\\n'",
            preview.contains("\\n") || preview.contains("\\r")
        )
    }

    @Test
    fun bugConditionApp14_bidiChars_mustBeEscaped() {
        val rlo = "\u202E"  // Right-to-left override
        val payload = Eip712Payload(
            primaryType = "Transfer",
            types = mapOf(
                "Transfer" to listOf(
                    Eip712Field("to", "address"),
                    Eip712Field("memo", "string"),
                ),
                "EIP712Domain" to listOf(
                    Eip712Field("name", "string"),
                    Eip712Field("chainId", "uint256"),
                ),
            ),
            domain = mapOf("name" to "Test", "chainId" to 1),
            message = mapOf(
                "to" to "0x0000000000000000000000000000000000000001",
                "memo" to "Safe${rlo}DRAIN",
            ),
        )

        val typed = Eip712TypedDataCodec.parse(payload)
        val preview = typed.review.messagePreview

        assertFalse(
            "BUG CONFIRMED (APP-14): prettyValue must NOT include literal bidi control chars.\n" +
                "Counterexample: U+202E (RLO) passed through → text direction flip in UI.\n" +
                "Fix: add Regex escape for bidi chars in prettyValue.",
            preview.contains('\u202E')
        )
        assertTrue(
            "Fixed prettyValue must render U+202E as '[U+202E]'",
            preview.contains("[U+202E]")
        )
    }

    // =========================================================================
    // Preservation tests
    // =========================================================================

    @Test
    fun preservation_plainAsciiStrings_renderedUnchanged() {
        val payload = Eip712Payload(
            primaryType = "Transfer",
            types = mapOf(
                "Transfer" to listOf(
                    Eip712Field("to", "address"),
                    Eip712Field("note", "string"),
                ),
                "EIP712Domain" to listOf(
                    Eip712Field("name", "string"),
                    Eip712Field("chainId", "uint256"),
                ),
            ),
            domain = mapOf("name" to "Test", "chainId" to 1),
            message = mapOf(
                "to" to "0x0000000000000000000000000000000000000001",
                "note" to "Transfer 1.0 USDC",
            ),
        )

        val typed = Eip712TypedDataCodec.parse(payload)
        val preview = typed.review.messagePreview
        assertTrue("Plain ASCII string must appear in preview", preview.contains("Transfer 1.0 USDC"))
    }

    @Test
    fun preservation_backslashAndQuoteStillEscaped() {
        val payload = Eip712Payload(
            primaryType = "Transfer",
            types = mapOf(
                "Transfer" to listOf(
                    Eip712Field("to", "address"),
                    Eip712Field("note", "string"),
                ),
                "EIP712Domain" to listOf(
                    Eip712Field("name", "string"),
                    Eip712Field("chainId", "uint256"),
                ),
            ),
            domain = mapOf("name" to "Test", "chainId" to 1),
            message = mapOf(
                "to" to "0x0000000000000000000000000000000000000001",
                "note" to "path\\file\"name",
            ),
        )

        val typed = Eip712TypedDataCodec.parse(payload)
        val preview = typed.review.messagePreview
        assertTrue("Backslash must be escaped", preview.contains("\\\\"))
        assertTrue("Quote must be escaped", preview.contains("\\\""))
    }
}
