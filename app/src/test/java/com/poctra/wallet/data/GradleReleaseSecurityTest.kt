package com.poctra.wallet.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Bug condition exploration test for APP-07.
 *
 * Bug: `verifyReleaseSecurity` task is not wired as a dependency of release build tasks
 * in `app/build.gradle.kts`. The `tasks.matching` block only has `dependsOn(verifyReleaseSigning)`.
 *
 * Bug condition: release tasks (assembleRelease, bundleRelease, installRelease) execute without
 * requiring `verifyReleaseSecurity`, allowing a release APK to be built without attestation
 * properties being validated.
 *
 * CRITICAL: Bug condition tests MUST FAIL on unfixed code.
 */
class GradleReleaseSecurityTest {

    /**
     * Returns true when APP-07 bug condition holds:
     * the `tasks.matching` block in `build.gradle.kts` does NOT include
     * `dependsOn("verifyReleaseSecurity")`.
     */
    private fun isBugCondition_APP07(buildGradleContent: String): Boolean {
        // The tasks.matching block must contain dependsOn("verifyReleaseSecurity")
        // Check for the pattern in the matching block
        return !buildGradleContent.contains("dependsOn(\"verifyReleaseSecurity\")")
            && !buildGradleContent.contains("dependsOn('verifyReleaseSecurity')")
    }

    private fun findBuildGradleKts(): File {
        // Try to find the build.gradle.kts relative to the project root
        val candidates = listOf(
            File("app/build.gradle.kts"),
            File("../app/build.gradle.kts"),
            File("../../app/build.gradle.kts"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: throw IllegalStateException(
                "Could not find app/build.gradle.kts. " +
                    "Run tests from project root or app/ directory."
            )
    }

    // =========================================================================
    // Bug condition exploration tests — APP-07
    // =========================================================================

    /**
     * EXPECTED OUTCOME (on unfixed code): FAILS.
     *
     * Asserts that `app/build.gradle.kts` contains `dependsOn("verifyReleaseSecurity")`
     * in the `tasks.matching` block. On unfixed code, this is missing.
     *
     * Counterexample: `./gradlew assembleRelease` with POCTRA_PLAY_CLOUD_PROJECT_NUMBER=0
     * succeeds — no attestation validation runs.
     *
     * **Validates: Requirements 1.1**
     */
    @Test
    fun bugConditionApp07_releaseTasksMustDependOnVerifyReleaseSecurity() {
        val buildFile = findBuildGradleKts()
        val content = buildFile.readText()

        // Confirm bug condition
        val bugPresent = isBugCondition_APP07(content)

        if (bugPresent) {
            fail(
                "BUG CONFIRMED (APP-07): `app/build.gradle.kts` does not wire " +
                    "`verifyReleaseSecurity` as a dependency of release build tasks.\n\n" +
                    "Root cause: the `tasks.matching` block only contains:\n" +
                    "  dependsOn(verifyReleaseSigning)\n" +
                    "It is MISSING:\n" +
                    "  dependsOn(\"verifyReleaseSecurity\")\n\n" +
                    "Counterexample: `./gradlew assembleRelease` with " +
                    "POCTRA_PLAY_CLOUD_PROJECT_NUMBER=0 succeeds — build proceeds without " +
                    "attestation validation.\n\n" +
                    "Fix (DO NOT apply yet): In app/build.gradle.kts, add to the tasks.matching block:\n" +
                    "  tasks.matching {\n" +
                    "      it.name in setOf(\"assembleRelease\", \"bundleRelease\", \"installRelease\")\n" +
                    "  }.configureEach {\n" +
                    "      dependsOn(verifyReleaseSigning)\n" +
                    "      dependsOn(\"verifyReleaseSecurity\")  // ADD THIS\n" +
                    "  }"
            )
        }

        // If we get here, the fix is present — assert it
        assertTrue(
            "FIX VERIFIED: build.gradle.kts contains dependsOn(\"verifyReleaseSecurity\")",
            content.contains("dependsOn(\"verifyReleaseSecurity\")")
        )
    }

    // =========================================================================
    // Preservation property tests — APP-07
    // =========================================================================

    /**
     * Preservation: `verifyReleaseSigning` must still be present (not removed by the fix).
     * EXPECTED OUTCOME: PASS on unfixed code.
     */
    @Test
    fun preservation_verifyReleaseSigningStillPresent() {
        val buildFile = findBuildGradleKts()
        val content = buildFile.readText()

        assertTrue(
            "Preservation: verifyReleaseSigning must still be wired to release tasks",
            content.contains("dependsOn(verifyReleaseSigning)")
        )
    }

    /**
     * Preservation: `verifyReleaseSecurity` task itself must still exist.
     * EXPECTED OUTCOME: PASS on unfixed code.
     */
    @Test
    fun preservation_verifyReleaseSecurityTaskExists() {
        val buildFile = findBuildGradleKts()
        val content = buildFile.readText()

        assertTrue(
            "Preservation: verifyReleaseSecurity task must be defined",
            content.contains("tasks.register(\"verifyReleaseSecurity\")")
        )
    }

    /**
     * Preservation: debug build tasks are NOT in the tasks.matching set.
     * EXPECTED OUTCOME: PASS on unfixed code (and after fix).
     */
    @Test
    fun preservation_debugBuildNotInReleaseTaskSet() {
        val buildFile = findBuildGradleKts()
        val content = buildFile.readText()

        // The tasks.matching block must only contain the three release task names
        assertTrue(
            "Preservation: tasks.matching block must include assembleRelease",
            content.contains("assembleRelease")
        )
        // assembleDebug must NOT be in the tasks.matching set
        val tasksMatchingBlock = content.substringAfter("tasks.matching {").substringBefore(".configureEach {")
        assertFalse(
            "Preservation: assembleDebug must NOT be in the tasks.matching release task set",
            tasksMatchingBlock.contains("assembleDebug")
        )
    }
}
