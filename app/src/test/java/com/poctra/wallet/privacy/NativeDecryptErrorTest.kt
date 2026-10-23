package com.poctra.wallet.privacy

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Bug condition exploration test for APP-13.
 *
 * Bug: `pvac_dec_value_fp` has a void return type and catches all exceptions internally,
 * writing (lo=0, hi=0) with no error indicator. `nativeDecrypt` cannot distinguish a
 * decryption failure from a genuine zero balance, so it returns "0" to Kotlin on error.
 *
 * Bug condition: cipher deserializes successfully but `pvac_dec_value_fp` throws internally.
 *
 * Fix: change `pvac_dec_value_fp` to return `int` (1=success, 0=failure); check return in
 * `nativeDecrypt` and throw `std::runtime_error` when 0.
 *
 * CRITICAL: Bug condition tests MUST FAIL on unfixed code.
 */
class NativeDecryptErrorTest {

    private fun findFile(vararg paths: String): File {
        for (path in paths) {
            val candidates = listOf(File(path), File("../$path"))
            candidates.firstOrNull { it.isFile }?.let { return it }
        }
        throw IllegalStateException("Could not find any of: ${paths.toList()}")
    }

    private fun findCApi() = findFile(
        "app/src/main/cpp/pvac/pvac_c_api.h",
        "app/src/main/cpp/pvac/pvac_c_api.cpp",
    )

    private fun findPrivacyJni() = findFile("app/src/main/cpp/privacy_jni.cpp")

    /** Returns true when APP-13 bug condition holds in the C API header. */
    private fun isBugCondition_APP13_header(headerContent: String): Boolean {
        // Look for the old void signature
        val voidDecl = Regex("void\\s+pvac_dec_value_fp\\s*\\(")
        val intDecl = Regex("int\\s+pvac_dec_value_fp\\s*\\(")
        return voidDecl.containsMatchIn(headerContent) && !intDecl.containsMatchIn(headerContent)
    }

    /** Returns true when APP-13 bug condition holds in nativeDecrypt (no return check). */
    private fun isBugCondition_APP13_jni(jniContent: String): Boolean {
        // After fix, nativeDecrypt should check the return value of pvac_dec_value_fp
        // Look for pattern: "if (pvac_dec_value_fp(" or "int.*pvac_dec_value_fp" or "== 0" after pvac_dec_value_fp
        val hasReturnCheck = jniContent.contains("pvac_dec_value_fp(") &&
            (jniContent.contains("if (pvac_dec_value_fp(") ||
             Regex("pvac_dec_value_fp[^;]+==\\s*0").containsMatchIn(jniContent) ||
             jniContent.contains("int result = pvac_dec_value_fp") ||
             jniContent.contains("const int dec_ok") ||
             jniContent.contains("int dec_result"))
        // If there is no return check, the bug is present
        val hasVoidCall = !hasReturnCheck
        return hasVoidCall
    }

    // =========================================================================
    // Bug condition exploration tests — APP-13
    // =========================================================================

    @Test
    fun bugConditionApp13_pvacDecValueFp_mustReturnIntNotVoid() {
        val headerFile = findFile("app/src/main/cpp/pvac/pvac_c_api.h")
        val content = headerFile.readText()

        val bugPresent = isBugCondition_APP13_header(content)

        if (bugPresent) {
            fail(
                "BUG CONFIRMED (APP-13): `pvac_dec_value_fp` is declared as `void` in pvac_c_api.h.\n\n" +
                    "Root cause: void return means nativeDecrypt cannot tell if decryption failed " +
                    "or if the balance is genuinely zero.\n\n" +
                    "Counterexample: corrupted on-chain cipher → nativeDecrypt returns \"0\" " +
                    "instead of throwing IllegalStateException.\n\n" +
                    "Fix: change to `int pvac_dec_value_fp(...)` returning 1=success, 0=failure."
            )
        }

        assertFalse("pvac_dec_value_fp must NOT be declared void", bugPresent)
    }

    @Test
    fun bugConditionApp13_nativeDecrypt_mustCheckDecValueFpReturnCode() {
        val jniContent = findPrivacyJni().readText()

        val bugPresent = isBugCondition_APP13_jni(jniContent)

        if (bugPresent) {
            fail(
                "BUG CONFIRMED (APP-13): `nativeDecrypt` calls `pvac_dec_value_fp` without " +
                    "checking its return value.\n\n" +
                    "Root cause: if pvac_dec_value_fp returns 0 (failure), nativeDecrypt does " +
                    "not detect the error and returns \"0\" to Kotlin.\n\n" +
                    "Fix: check return value and throw std::runtime_error if 0."
            )
        }

        assertFalse("nativeDecrypt must check pvac_dec_value_fp return code", bugPresent)
    }

    // =========================================================================
    // Preservation property tests — APP-13
    // =========================================================================

    @Test
    fun preservation_nativeDecrypt_stillReturnsStringOnSuccess() {
        val jniContent = findPrivacyJni().readText()
        // nativeDecrypt must still return a jstring (to_string(low))
        assertTrue(
            "nativeDecrypt must still call std::to_string on the decrypted low value",
            jniContent.contains("to_string(low)")
        )
    }

    @Test
    fun preservation_pvacFreeBytes_stillPresentInHeader() {
        val headerContent = findFile("app/src/main/cpp/pvac/pvac_c_api.h").readText()
        assertTrue("pvac_free_bytes must still be declared", headerContent.contains("pvac_free_bytes"))
    }
}
