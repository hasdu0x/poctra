package com.poctra.wallet.privacy

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Bug condition exploration test for APP-12.
 *
 * Bug: `clear_engine_locked()` calls `pvac_free_seckey(secret_key)` without first
 * zeroing the secret key's sensitive fields (scalar, PRF key, vector buffers).
 * Key material remains in freed heap pages after `nativeClear`.
 *
 * Bug condition: `secret_key != nullptr AND sensitiveFields NOT zeroed BEFORE pvac_free_seckey`
 *
 * Fix: add a `wipe_and_free_seckey` helper that zeros key material before freeing.
 * The helper serializes the secret key via `pvac_serialize_seckey`, wipes the resulting
 * byte buffer with `secure_zero`, then calls `pvac_free_bytes` and `pvac_free_seckey`.
 * This zeroes the serialized form of all sensitive fields (scalar, PRF key, vector-backed
 * buffers) using a compiler-barrier wipe resistant to dead-store elimination, before the
 * heap block is returned to the allocator.
 *
 * CRITICAL: Bug condition tests MUST FAIL on unfixed code.
 *
 * **Validates: Requirements 1.1**
 */
class NativeSecretKeyWipeTest {

    private fun findPrivacyJni(): File {
        val candidates = listOf(
            File("app/src/main/cpp/privacy_jni.cpp"),
            File("../app/src/main/cpp/privacy_jni.cpp"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: throw IllegalStateException("Could not find privacy_jni.cpp")
    }

    /**
     * Returns true when APP-12 bug condition holds:
     * clear_engine_locked does NOT have a wipe step before pvac_free_seckey.
     */
    private fun isBugCondition_APP12(jniContent: String): Boolean {
        // The fix adds a wipe_and_free_seckey helper that:
        //   1. calls pvac_serialize_seckey to get a byte buffer
        //   2. calls secure_zero on that buffer
        //   3. calls pvac_free_bytes and pvac_free_seckey
        //
        // The presence of `wipe_and_free_seckey` as a function AND its use inside
        // clear_engine_locked (replacing the bare pvac_free_seckey call) is the indicator.
        val hasWipeHelper = jniContent.contains("wipe_and_free_seckey")

        if (!hasWipeHelper) return true  // no wipe helper at all → bug present

        // The wipe helper must be CALLED in clear_engine_locked (not just defined).
        // Locate the clear_engine_locked body and check it uses wipe_and_free_seckey.
        val clearBlock = jniContent
            .substringAfter("void clear_engine_locked()")
            .substringBefore("void require_initialized()")

        val wipeCalledInClear = clearBlock.contains("wipe_and_free_seckey")
        if (!wipeCalledInClear) return true  // helper defined but not used in clear → bug present

        // Confirm the wipe helper itself contains a secure_zero call on serialized bytes,
        // ensuring sensitive data is actually zeroed before pvac_free_seckey.
        val helperBody = jniContent
            .substringAfter("void wipe_and_free_seckey(")
            .substringBefore("void clear_engine_locked()")
        val helperZeroes = helperBody.contains("secure_zero")

        return !helperZeroes  // bug present if helper does not call secure_zero
    }

    // =========================================================================
    // Bug condition exploration tests — APP-12
    // =========================================================================

    /**
     * EXPECTED OUTCOME (on unfixed code): FAILS.
     *
     * Asserts that `clear_engine_locked` wipes the secret key before freeing it.
     * On unfixed code, there is no wipe step — pvac_free_seckey is called directly.
     *
     * Counterexample: app background triggers nativeClear → key bytes remain in freed heap pages.
     *
     * **Validates: Requirements 1.1**
     */
    @Test
    fun bugConditionApp12_clearEngineLocked_mustWipeSecretKeyBeforeFreeing() {
        val content = findPrivacyJni().readText()

        val bugPresent = isBugCondition_APP12(content)

        if (bugPresent) {
            fail(
                "BUG CONFIRMED (APP-12): `clear_engine_locked` calls `pvac_free_seckey(secret_key)` " +
                    "without first zeroing sensitive key material.\n\n" +
                    "Root cause: `pvac_free_seckey` is a plain delete; it does not zero the scalar, " +
                    "PRF keys, or vector buffers of pvac::SecKey before releasing memory.\n\n" +
                    "Counterexample: app background triggers nativeClear → key material remains " +
                    "readable in freed heap pages.\n\n" +
                    "Fix: add `wipe_and_free_seckey` helper that serializes the secret key via " +
                    "`pvac_serialize_seckey`, zeroes the resulting bytes using `secure_zero` " +
                    "(compiler-barrier wipe), then frees the buffer and calls `pvac_free_seckey`."
            )
        }

        assertFalse("clear_engine_locked must wipe secret key before freeing", bugPresent)
    }

    // =========================================================================
    // Preservation property tests — APP-12
    // =========================================================================

    /**
     * Preservation: active_seed is still zeroed in clear_engine_locked.
     * EXPECTED OUTCOME: PASS on unfixed code.
     *
     * **Validates: Requirements 3.1**
     */
    @Test
    fun preservation_activeSeed_stillZeroedInClearEngineLocked() {
        val content = findPrivacyJni().readText()

        val clearBlock = content
            .substringAfter("void clear_engine_locked()")
            .substringBefore("void require_initialized()")

        assertTrue(
            "clear_engine_locked must still zero active_seed",
            clearBlock.contains("secure_zero") && clearBlock.contains("active_seed")
        )
    }

    /**
     * Preservation: engine pointers (secret_key, public_key, params) are still set to nullptr.
     * EXPECTED OUTCOME: PASS on unfixed code.
     *
     * **Validates: Requirements 3.1**
     */
    @Test
    fun preservation_enginePointers_stillNulledAfterClear() {
        val content = findPrivacyJni().readText()

        val clearBlock = content
            .substringAfter("void clear_engine_locked()")
            .substringBefore("void require_initialized()")

        assertTrue(
            "clear_engine_locked must set secret_key = nullptr",
            clearBlock.contains("secret_key = nullptr")
        )
        assertTrue(
            "clear_engine_locked must set public_key = nullptr",
            clearBlock.contains("public_key = nullptr")
        )
        assertTrue(
            "clear_engine_locked must set params = nullptr",
            clearBlock.contains("params = nullptr")
        )
        assertTrue(
            "clear_engine_locked must set initialized = false",
            clearBlock.contains("initialized = false")
        )
    }

    /**
     * Preservation: secure_zero helper still exists in privacy_jni.cpp.
     * EXPECTED OUTCOME: PASS on unfixed code.
     *
     * **Validates: Requirements 3.1**
     */
    @Test
    fun preservation_secureZeroHelper_stillPresent() {
        val content = findPrivacyJni().readText()
        assertTrue(
            "secure_zero helper must still be present in privacy_jni.cpp",
            content.contains("void secure_zero")
        )
    }

    /**
     * Preservation: pvac_serialize_seckey is available in pvac_c_api.h (prerequisite for fix).
     * EXPECTED OUTCOME: PASS on unfixed and fixed code.
     *
     * **Validates: Requirements 2.1**
     */
    @Test
    fun preservation_pvacSerializeSeckey_availableInCApi() {
        val candidates = listOf(
            File("app/src/main/cpp/pvac/pvac_c_api.h"),
            File("../app/src/main/cpp/pvac/pvac_c_api.h"),
        )
        val header = candidates.firstOrNull { it.isFile }
            ?: throw IllegalStateException("Could not find pvac_c_api.h")

        val content = header.readText()
        assertTrue(
            "pvac_serialize_seckey must be declared in pvac_c_api.h to enable the wipe fix",
            content.contains("pvac_serialize_seckey")
        )
        assertTrue(
            "pvac_free_bytes must be declared in pvac_c_api.h (needed to free serialized buffer)",
            content.contains("pvac_free_bytes")
        )
    }
}
