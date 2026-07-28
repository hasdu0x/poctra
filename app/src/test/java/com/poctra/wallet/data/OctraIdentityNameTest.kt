package com.poctra.wallet.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OctraIdentityNameTest {
    @Test
    fun acceptsRootAndSubdomainNamesWithMiddleHyphens() {
        assertTrue(OctraIdentityName.isValidDomain("ab.oct"))
        assertTrue(OctraIdentityName.isValidDomain("foo.oct"))
        assertTrue(OctraIdentityName.isValidDomain("x.ab.oct"))
        assertTrue(OctraIdentityName.isValidDomain("dev-id.poctra.oct"))
        assertTrue(OctraIdentityName.isValidDomain("a.poctra.oct"))
        assertTrue(OctraIdentityName.isValidDomain("app.po-ctra.oct"))
    }

    @Test
    fun rejectsInvalidLengthsAndBoundaryHyphens() {
        assertFalse(OctraIdentityName.isValidDomain("a.oct"))
        assertFalse(OctraIdentityName.isValidDomain("app.a.oct"))
        assertFalse(OctraIdentityName.isValidDomain("-dev.poctra.oct"))
        assertFalse(OctraIdentityName.isValidDomain("dev-.poctra.oct"))
        assertFalse(OctraIdentityName.isValidDomain("dev.poctra-.oct"))
    }

    @Test
    fun validatesContractPathsWithoutAProtocolSuffix() {
        assertTrue(OctraIdentityName.isValidPath("dev-id.poctra"))
        assertFalse(OctraIdentityName.isValidPath("dev-id.poctra.extra"))
    }

    @Test
    fun normalizesNavigableOctraWebUrls() {
        assertEquals("poctra.oct/", OctraIdentityName.fromWebUrl("https://poctra.oct/"))
        assertEquals(
            "dev-id.poctra.oct/app?tab=names#active",
            OctraIdentityName.fromWebUrl("https://dev-id.poctra.oct/app?tab=names#active"),
        )
    }

    @Test
    fun ignoresRegularOrAmbiguousWebUrls() {
        assertNull(OctraIdentityName.fromWebUrl("https://octra.id"))
        assertNull(OctraIdentityName.fromWebUrl("https://user@poctra.oct/"))
        assertNull(OctraIdentityName.fromWebUrl("https://poctra.oct:8443/"))
    }
}
