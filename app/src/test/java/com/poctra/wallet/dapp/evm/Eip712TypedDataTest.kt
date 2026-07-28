package com.poctra.wallet.dapp.evm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger

class Eip712TypedDataTest {
    @Test
    fun matchesCanonicalEtherMailVector() {
        val result = Eip712TypedDataCodec.parse(canonicalMail())

        assertEquals(
            "0xbe609aee343fb3c4b28e1df9e632fca64fcfaede20f02e86244efddf30957bd2",
            result.review.signingHash,
        )
        assertEquals(BigInteger.ONE, result.review.chainId)
        assertEquals("0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC", result.review.verifyingContract)
        assertTrue(result.review.typeSummary.contains("Mail("))
    }

    @Test
    fun matchesPermit2TransferVector() {
        val result = Eip712TypedDataCodec.parse(
            Eip712Payload(
                primaryType = "PermitTransferFrom",
                types = mapOf(
                    "TokenPermissions" to listOf(
                        Eip712Field("token", "address"),
                        Eip712Field("amount", "uint256"),
                    ),
                    "PermitTransferFrom" to listOf(
                        Eip712Field("permitted", "TokenPermissions"),
                        Eip712Field("nonce", "uint256"),
                        Eip712Field("deadline", "uint256"),
                    ),
                ),
                domain = mapOf(
                    "name" to "Permit2",
                    "chainId" to 1,
                    "verifyingContract" to "0x000000000022D473030F116dDEE9F6B43aC78BA3",
                ),
                message = mapOf(
                    "permitted" to mapOf(
                        "token" to "0x4647e1fE715c9e23959022C2416C71867F5a6E80",
                        "amount" to "1000000000000000000",
                    ),
                    "nonce" to "42",
                    "deadline" to "2000000000",
                ),
            ),
        )

        assertEquals(
            "0xfb092e651af045e2c108e7d5e5b72cad1f56763208853f93819687d82555383d",
            result.review.signingHash,
        )
        assertTrue(result.review.warnings.any { it.contains("spending permission") })
    }

    @Test
    fun matchesDynamicStructArrayVector() {
        val result = Eip712TypedDataCodec.parse(
            Eip712Payload(
                primaryType = "Batch",
                types = mapOf(
                    "Transfer" to listOf(
                        Eip712Field("to", "address"),
                        Eip712Field("amount", "uint256"),
                    ),
                    "Batch" to listOf(
                        Eip712Field("transfers", "Transfer[]"),
                        Eip712Field("salt", "bytes32"),
                    ),
                ),
                domain = mapOf(
                    "name" to "Batch",
                    "version" to "1",
                    "chainId" to 999,
                    "verifyingContract" to "0x1111111111111111111111111111111111111111",
                ),
                message = mapOf(
                    "transfers" to listOf(
                        mapOf("to" to "0x2222222222222222222222222222222222222222", "amount" to "1"),
                        mapOf("to" to "0x3333333333333333333333333333333333333333", "amount" to "2"),
                    ),
                    "salt" to "0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                ),
            ),
        )

        assertEquals(
            "0x9eec41556c45bbc7e00fc1f62373346e198c60459beff723d6a314c9effd19d3",
            result.review.signingHash,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnexpectedMessageFields() {
        Eip712TypedDataCodec.parse(
            Eip712Payload(
                primaryType = "Mail",
                types = mapOf("Mail" to listOf(Eip712Field("contents", "string"))),
                domain = emptyMap(),
                message = mapOf("contents" to "hello", "unexpected" to true),
            ),
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsRecursiveTypes() {
        Eip712TypedDataCodec.parse(
            Eip712Payload(
                primaryType = "Node",
                types = mapOf("Node" to listOf(Eip712Field("next", "Node"))),
                domain = emptyMap(),
                message = mapOf("next" to mapOf("next" to emptyMap<String, Any?>())),
            ),
        )
    }

    private fun canonicalMail(): Eip712Payload = Eip712Payload(
        primaryType = "Mail",
        types = mapOf(
            "EIP712Domain" to listOf(
                Eip712Field("name", "string"),
                Eip712Field("version", "string"),
                Eip712Field("chainId", "uint256"),
                Eip712Field("verifyingContract", "address"),
            ),
            "Person" to listOf(
                Eip712Field("name", "string"),
                Eip712Field("wallet", "address"),
            ),
            "Mail" to listOf(
                Eip712Field("from", "Person"),
                Eip712Field("to", "Person"),
                Eip712Field("contents", "string"),
            ),
        ),
        domain = mapOf(
            "name" to "Ether Mail",
            "version" to "1",
            "chainId" to 1,
            "verifyingContract" to "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC",
        ),
        message = mapOf(
            "from" to mapOf(
                "name" to "Cow",
                "wallet" to "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826",
            ),
            "to" to mapOf(
                "name" to "Bob",
                "wallet" to "0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB",
            ),
            "contents" to "Hello, Bob!",
        ),
    )
}
