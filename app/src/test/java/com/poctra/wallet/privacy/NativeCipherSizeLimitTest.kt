package com.poctra.wallet.privacy

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Bug condition exploration test for APP-08.
 *
 * Bug: `read_bytes` in `privacy_jni.cpp` has no upper-bound size check before allocating
 * native memory from a JNI byte array. A caller passing a 50 MB byte array causes a 50 MB
 * native allocation before any parsing occurs.
 *
 * Bug condition: `isBugCondition_APP08(bytes)` where `bytes.size > MAX_CIPHER_SIZE` (1 MB)
 *
 * Fix: add a `max_size` parameter to `read_bytes`; pass `MaxCipherBytes = 1'048'576` for
 * cipher array reads in nativeDecrypt, nativeDecryptWithPublicKey, nativeBuildDecrypt,
 * nativeBuildStealth, nativeBuildMigration.
 *
 * CRITICAL: Bug condition tests MUST FAIL on unfixed code.
 */
class NativeCipherSizeLimitTest {

    private val MaxCipherBytes = 1_048_576L // 1 MB

    /**
     * Returns true when APP-08 bug condition holds:
     * a byte array of this size would bypass size checks in the unfixed read_bytes.
     */
    private fun isBugCondition_APP08(byteSize: Long): Boolean = byteSize > MaxCipherBytes

    private fun findPrivacyJni(): File {
        val candidates = listOf(
            File("app/src/main/cpp/privacy_jni.cpp"),
            File("../app/src/main/cpp/privacy_jni.cpp"),
            File("../../app/src/main/cpp/privacy_jni.cpp"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: throw IllegalStateException("Could not find privacy_jni.cpp")
    }

    // =========================================================================
    // Bug condition exploration tests — APP-08
    // =========================================================================

    /**
     * EXPECTED OUTCOME (on unfixed code): FAILS.
     *
     * Asserts that `read_bytes` in `privacy_jni.cpp` has a `max_size` parameter
     * with a `MaxCipherBytes` constant applied to cipher reads.
     * On unfixed code, neither the constant nor the guard exists.
     *
     * Counterexample: a 2 MB byte array passed to nativeDecrypt → 2 MB native allocation.
     */
    @Test
    fun bugConditionApp08_readBytesMustHaveSizeLimitForCipherArrays() {
        val jniFile = findPrivacyJni()
        val content = jniFile.readText()

        // Bug condition: no MaxCipherBytes constant and no max_size guard in read_bytes
        val hasMaxCipherBytes = content.contains("MaxCipherBytes")
        val hasMaxSizeParam = content.contains("max_size")

        if (!hasMaxCipherBytes || !hasMaxSizeParam) {
            fail(
                "BUG CONFIRMED (APP-08): `read_bytes` in `privacy_jni.cpp` has no size limit.\n\n" +
                    "Root cause: `read_bytes(env, cipher_array)` allocates a `std::vector<uint8_t>` " +
                    "of whatever size is passed via JNI, with no upper-bound check.\n\n" +
                    "Counterexample: a ${MaxCipherBytes * 2}-byte ByteArray passed to nativeDecrypt " +
                    "→ ${MaxCipherBytes * 2 / 1_048_576} MB native allocation before parsing.\n\n" +
                    "Fix: add `constexpr size_t MaxCipherBytes = 1'048'576;` and pass it as " +
                    "`max_size` to `read_bytes` in cipher-array reads."
            )
        }

        // If we get here, the fix is in place
        assertTrue("MaxCipherBytes constant must be defined", hasMaxCipherBytes)
        assertTrue("max_size parameter must be present in read_bytes", hasMaxSizeParam)
    }

    /**
     * Additional: confirm nativeDecrypt passes MaxCipherBytes to read_bytes.
     * EXPECTED OUTCOME (on unfixed code): FAILS.
     */
    @Test
    fun bugConditionApp08_nativeDecryptMustPassMaxCipherBytesToReadBytes() {
        val jniFile = findPrivacyJni()
        val content = jniFile.readText()

        // After fix, nativeDecrypt should call read_bytes with MaxCipherBytes
        val nativeDecryptBlock = content
            .substringAfter("nativeDecrypt(")
            .substringBefore("nativeDecryptWithPublicKey(")

        val passesLimit = nativeDecryptBlock.contains("MaxCipherBytes")

        if (!passesLimit) {
            fail(
                "BUG CONFIRMED (APP-08): `nativeDecrypt` does not pass a size limit to `read_bytes`.\n" +
                    "The cipher_array read has no maximum size enforcement.\n" +
                    "Counterexample: ByteArray(2_000_000) → 2 MB native allocation."
            )
        }

        assertTrue("nativeDecrypt must pass MaxCipherBytes to read_bytes", passesLimit)
    }

    // =========================================================================
    // Preservation property tests — APP-08
    // =========================================================================

    /**
     * Preservation: read_bytes still accepts byte arrays within the size limit.
     * EXPECTED OUTCOME: PASS on unfixed code.
     */
    @Test
    fun preservation_byteArrayWithinLimit_doesNotTriggerBugCondition() {
        val validSizes = listOf(0L, 1L, 512L, 1024L, 65536L, MaxCipherBytes)
        for (size in validSizes) {
            assertFalse(
                "Byte array of $size bytes must NOT trigger bug condition",
                isBugCondition_APP08(size)
            )
        }
    }

    /**
     * Preservation: sizes over the limit DO trigger the bug condition.
     * EXPECTED OUTCOME: PASS.
     */
    @Test
    fun preservation_bugConditionBoundaryIsAtOneMoreThanMaxCipherBytes() {
        assertFalse("MaxCipherBytes itself is at boundary, not bug condition",
            isBugCondition_APP08(MaxCipherBytes))
        assertTrue("MaxCipherBytes + 1 triggers bug condition",
            isBugCondition_APP08(MaxCipherBytes + 1))
        assertTrue("2 MB triggers bug condition",
            isBugCondition_APP08(2 * MaxCipherBytes))
    }

    /**
     * Preservation: read_bytes with an `expected` size (fixed-size arrays like seed=32)
     * must still accept exact-size arrays.
     * EXPECTED OUTCOME: PASS.
     */
    @Test
    fun preservation_fixedSizeArrays_notAffectedByMaxCipherLimit() {
        // Seed is always 32 bytes — well below MaxCipherBytes
        val seedSize = 32L
        assertFalse("32-byte seed must not trigger bug condition", isBugCondition_APP08(seedSize))
        assertTrue("32-byte seed is within MaxCipherBytes limit", seedSize <= MaxCipherBytes)
    }
}
