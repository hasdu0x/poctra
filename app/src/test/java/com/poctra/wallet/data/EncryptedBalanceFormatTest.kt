package com.poctra.wallet.data

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import org.bouncycastle.crypto.params.Ed25519PrivateKeyParameters
import org.bouncycastle.crypto.signers.Ed25519Signer

class EncryptedBalanceFormatTest {
    @Test
    fun `devnet zero sentinel means no encrypted balance`() {
        assertNull(normalizeEncryptedBalanceCipher("0"))
        assertNull(normalizeEncryptedBalanceCipher(" 0 "))
        assertNull(normalizeEncryptedBalanceCipher("null"))
    }

    @Test
    fun `valid hfhe ciphertext is preserved`() {
        assertEquals(
            "hfhe_v1|AQIDBA==",
            normalizeEncryptedBalanceCipher(" hfhe_v1|AQIDBA== "),
        )
    }

    @Test
    fun `malformed nonzero ciphertext is rejected clearly`() {
        assertThrows(IllegalArgumentException::class.java) {
            normalizeEncryptedBalanceCipher("not-base64")
        }
    }

    @Test
    fun `canonical quote matches webcli and does not escape base64 slash`() {
        assertEquals(
            "\"{\\\"cipher\\\":\\\"hfhe_v1|ab/c==\\\"}\"",
            canonicalJsonQuote("{\"cipher\":\"hfhe_v1|ab/c==\"}"),
        )
    }

    @Test
    fun `canonical quote escapes webcli control characters`() {
        assertEquals("\"a\\\\b\\nc\\td\"", canonicalJsonQuote("a\\b\nc\td"))
    }

    @Test
    fun `canonical timestamp never uses scientific notation`() {
        assertEquals("1783832234.567", canonicalOctraTimestamp(1_783_832_234_567L))
        assertEquals("1783832234.0", canonicalOctraTimestamp(1_783_832_234_000L))
    }

    @Test
    fun `encrypt canonical and signature match webcli fixture`() {
        val address = "oct2KagShR4Usj2uARXJeDw7XJEKvQ3XDr84dC47hUB3Uyd"
        val canonical = canonicalOctraTransaction(
            from = address,
            to = address,
            amountRaw = "1",
            nonce = 7,
            ou = "3000",
            timestamp = canonicalOctraTimestamp(1_783_832_234_567L),
            opType = "encrypt",
            encryptedData = "{\"cipher\":\"hfhe_v1|ab/c==\",\"amount_commitment\":\"d/e=\"}",
        )
        assertEquals(
            "{\"from\":\"$address\",\"to_\":\"$address\",\"amount\":\"1\",\"nonce\":7," +
                "\"ou\":\"3000\",\"timestamp\":1783832234.567,\"op_type\":\"encrypt\"," +
                "\"encrypted_data\":\"{\\\"cipher\\\":\\\"hfhe_v1|ab/c==\\\"," +
                "\\\"amount_commitment\\\":\\\"d/e=\\\"}\"}",
            canonical,
        )

        val privateKey = Ed25519PrivateKeyParameters(ByteArray(32), 0)
        val signer = Ed25519Signer().apply {
            init(true, privateKey)
            val bytes = canonical.toByteArray(Charsets.UTF_8)
            update(bytes, 0, bytes.size)
        }
        assertEquals(
            "szktIdN9RUdvcuhjRwfWLlDijeuM888ltnpqJUQlawxN09ASTNg4btoZTxUQHOvzCebZFr4DbAJ/F6EgqhOKDA==",
            Base64.getEncoder().encodeToString(signer.generateSignature()),
        )
    }
}
