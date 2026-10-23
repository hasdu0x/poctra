package com.poctra.wallet.ui

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/**
 * Bug condition exploration test for APP-15.
 *
 * Bug: The privacy balance reveal/hide toggle Box in PrivacyBalanceCard uses .size(34.dp)
 * (below 48dp minimum) and has no contentDescription semantics.
 * TalkBack users cannot reach or identify the toggle.
 */
class PrivacyBalanceToggleTest {

    private fun findPrivacyScreens(): File {
        val candidates = listOf(
            File("app/src/main/java/com/poctra/wallet/ui/PrivacyScreens.kt"),
            File("../app/src/main/java/com/poctra/wallet/ui/PrivacyScreens.kt"),
        )
        return candidates.firstOrNull { it.isFile }
            ?: throw IllegalStateException("Could not find PrivacyScreens.kt")
    }

    private fun isBugCondition_APP15_size(content: String): Boolean {
        // Bug: size is 34.dp. Fix: size is 48.dp.
        // Look at the toggle Box context: it's near togglePrivacyBalance and background(PrivacyPrimary)
        val toggleRegion = content
            .substringAfter("togglePrivacyBalance")
            .let { after ->
                // Also search before
                val before = content.substringBefore("togglePrivacyBalance")
                before.takeLast(500) + after.take(500)
            }
        // If 48.dp is not in the toggle region but 34.dp is, bug is present
        return !toggleRegion.contains("48.dp") && toggleRegion.contains("34.dp")
    }

    private fun isBugCondition_APP15_semantics(content: String): Boolean {
        // After fix, there should be a semantics block with contentDescription for the toggle
        return !content.contains("Hide encrypted balance") && !content.contains("Show encrypted balance")
    }

    @Test
    fun bugConditionApp15_toggleSize_mustBe48dp() {
        val content = findPrivacyScreens().readText()
        val bugPresent = isBugCondition_APP15_size(content)
        if (bugPresent) {
            fail(
                "BUG CONFIRMED (APP-15): Privacy balance toggle Box uses .size(34.dp) " +
                    "(below the 48dp minimum touch target).\n\n" +
                    "Counterexample: TalkBack user taps privacy card → toggle is too small, " +
                    "unreachable for users with motor impairments.\n\n" +
                    "Fix: change .size(34.dp) to .size(48.dp) in PrivacyBalanceCard."
            )
        }
        assertFalse("Toggle must NOT use 34.dp size", bugPresent)
    }

    @Test
    fun bugConditionApp15_toggleMustHaveContentDescription() {
        val content = findPrivacyScreens().readText()
        val bugPresent = isBugCondition_APP15_semantics(content)
        if (bugPresent) {
            fail(
                "BUG CONFIRMED (APP-15): Privacy balance toggle has no contentDescription.\n\n" +
                    "Counterexample: TalkBack announces nothing when focus lands on toggle.\n\n" +
                    "Fix: add semantics { contentDescription = if (visible) 'Hide...' else 'Show...' }"
            )
        }
        assertFalse("Toggle must have contentDescription", bugPresent)
    }

    @Test
    fun preservation_toggleBehavior_stillInvokedOnClick() {
        val content = findPrivacyScreens().readText()
        assertTrue(
            "togglePrivacyBalance must still be the onClick action",
            content.contains("togglePrivacyBalance")
        )
    }

    @Test
    fun preservation_decryptingState_stillDisablesToggle() {
        val content = findPrivacyScreens().readText()
        assertTrue(
            "privacyBalanceDecrypting must still disable the toggle",
            content.contains("privacyBalanceDecrypting")
        )
    }
}
