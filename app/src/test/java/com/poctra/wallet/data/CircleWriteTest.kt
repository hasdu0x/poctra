package com.poctra.wallet.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CircleWriteTest {
    @Test
    fun derivesCircleIdUsingRuntimeDomainFraming() {
        assertEquals(
            "oct5meD2ZDgCi8P2yDW12WW6SpVGdiBCBToW9Xi4bm4XcSE",
            deriveCircleDeployId(
                deployer = "oct5ZHUXnGVJwwZowQsQuVrEWmE6qqvDNgak9PorPYoctra",
                nonce = 1L,
                payload = publicCircleDeployPayload(),
            ),
        )
    }

    @Test
    fun canonicalizesSafeAssetPathsAndRejectsTraversal() {
        assertEquals("/assets/app.js", validateCircleWritePath("//assets//app.js"))
        assertThrows(IllegalArgumentException::class.java) {
            validateCircleWritePath("/assets/../secret")
        }
        assertThrows(IllegalArgumentException::class.java) {
            validateCircleWritePath("/assets%2fsecret")
        }
    }

    @Test
    fun validatesAssetMetadataAndFeeTiers() {
        assertEquals("application/json", validateCircleContentType(" Application/JSON "))
        assertEquals("aGVsbG8=", validateCircleAssetBody("aGVsbG8="))
        assertEquals(5_000L, circleAssetFeeOu(5_460))
        assertEquals(10_000L, circleAssetFeeOu(5_464))
        assertThrows(IllegalArgumentException::class.java) {
            validateCircleAssetBody("not base64")
        }
    }
}
