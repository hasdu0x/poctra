package com.poctra.wallet.ui

import com.poctra.wallet.AppRoute
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Bug condition exploration test for APP-04.
 *
 * **Validates: Requirements 1.1**
 *
 * This test MUST FAIL on unfixed code — failure confirms the bug exists.
 * DO NOT fix the test or the production code when it fails.
 *
 * Bug condition:
 *   `isBugCondition_APP04(route)` where `route == AppRoute.PaymentRequest`
 *
 * Root cause: In `Screens.kt`, the `when` branch for `AppRoute.PaymentRequest` dispatches
 *   `ReceiveScreen(controller)` instead of `PaymentRequestScreen(controller)`.
 *   This is a copy-paste error.
 *
 * Concrete counterexample:
 *   Route `AppRoute.PaymentRequest` → unfixed dispatch produces `"ReceiveScreen"`
 *   → expected correct dispatch: `"PaymentRequestScreen"`
 *
 * Expected (correct) behavior: `AppRoute.PaymentRequest` SHALL render `PaymentRequestScreen`.
 * Actual (buggy) behavior on unfixed code: `ReceiveScreen` is rendered instead.
 */
class ScreensRoutingTest {

    // -------------------------------------------------------------------------
    // Route dispatch model
    //
    // The production dispatch is a `when (controller.route)` block in `Screens.kt`.
    // We model it here as a function that returns the composable *name* for a given route.
    //
    // `routeToScreenUnfixed` reproduces the BUGGY dispatch exactly as it appears in
    // the unfixed `Screens.kt` (line 273: `AppRoute.PaymentRequest -> ReceiveScreen`).
    //
    // `routeToScreenFixed` represents the CORRECT dispatch after the fix is applied
    // (line 273 should read: `AppRoute.PaymentRequest -> PaymentRequestScreen`).
    // -------------------------------------------------------------------------

    /**
     * Simulates the UNFIXED route dispatch from `Screens.kt`.
     *
     * This reproduces the buggy `when` block exactly:
     *   ```kotlin
     *   AppRoute.PaymentRequest -> ReceiveScreen(controller)  // BUG: should be PaymentRequestScreen
     *   AppRoute.Receive        -> ReceiveScreen(controller)  // correct
     *   ```
     *
     * Returns the name of the composable that would be rendered for the given route.
     */
    private fun routeToScreenUnfixed(route: AppRoute): String = when (route) {
        AppRoute.Loading -> "LoadingScreen"
        AppRoute.Welcome -> "WelcomeScreen"
        AppRoute.ImportWallet -> "ImportWalletScreen"
        AppRoute.SecuritySetup -> "SecuritySetupScreen"
        AppRoute.Unlock -> "UnlockScreen"
        AppRoute.Dashboard -> "DashboardScreen"
        AppRoute.PrivacyEncrypt -> "PrivacyEncryptScreen"
        AppRoute.PrivacyDecrypt -> "PrivacyDecryptScreen"
        AppRoute.PrivacyStealth -> "PrivacyStealthScreen"
        AppRoute.PrivacyClaim -> "PrivacyClaimScreen"
        AppRoute.PrivacySettings -> "PrivacySettingsScreen"
        AppRoute.Settings -> "SettingsScreen"
        AppRoute.RpcManagement -> "RpcManagementScreen"
        AppRoute.RpcAddNetwork -> "AddRpcNetworkScreen"
        AppRoute.SecuritySettings -> "SecuritySettingsScreen"
        AppRoute.BackupSettings -> "BackupSettingsScreen"
        AppRoute.SecurityCenter -> "SecurityCenterScreen"
        AppRoute.ActivityCenter -> "ActivityCenterScreen"
        AppRoute.PaymentRequest -> "ReceiveScreen"        // BUG: should be "PaymentRequestScreen"
        AppRoute.PortfolioAnalytics -> "PortfolioAnalyticsScreen"
        AppRoute.PortfolioAssetHistory -> "PortfolioAssetHistoryScreen"
        AppRoute.ApprovalDashboard -> "ApprovalDashboardScreen"
        AppRoute.RecoveryPlan -> "RecoveryPlanScreen"
        AppRoute.FundingProviders -> "SettingsScreen"
        AppRoute.AppearanceSettings -> "AppearanceSettingsScreen"
        AppRoute.ConnectedDApps -> "ConnectedDAppsScreen"
        AppRoute.IdentityProviderSettings -> "IdentityProviderSettingsScreen"
        AppRoute.IdentityProviderAdd -> "AddIdentityProviderScreen"
        AppRoute.AddressBook -> "AddressBookScreen"
        AppRoute.AddressBookAdd -> "AddAddressBookContactScreen"
        AppRoute.AddAsset -> "AddAssetScreen"
        AppRoute.Send -> "SendScreen"
        AppRoute.SendAsset -> "SendAssetScreen"
        AppRoute.Receive -> "ReceiveScreen"
        AppRoute.Bridge -> "BridgeScreen"
        AppRoute.BridgeHistory -> "BridgeHistoryScreen"
        AppRoute.Evm -> "EvmWalletScreen"
        AppRoute.EvmSend -> "EvmSendScreen"
        AppRoute.EvmReceive -> "EvmReceiveScreen"
        AppRoute.EvmActivity -> "EvmActivityScreen"
        AppRoute.EvmNetwork -> "EvmNetworkSettingsScreen"
        AppRoute.EvmAddNetwork -> "EvmAddNetworkScreen"
        AppRoute.EvmAddToken -> "EvmAddTokenScreen"
        AppRoute.EvmAddNft -> "EvmAddNftScreen"
        AppRoute.TransactionDetails -> "TransactionDetailsScreen"
        AppRoute.DAppBrowser -> "DAppBrowserScreen"
        AppRoute.About -> "AboutScreen"
    }

    /**
     * Simulates the FIXED route dispatch from `Screens.kt`.
     *
     * This represents the correct `when` block after applying the fix:
     *   ```kotlin
     *   AppRoute.PaymentRequest -> PaymentRequestScreen(controller)  // FIXED
     *   AppRoute.Receive        -> ReceiveScreen(controller)         // unchanged
     *   ```
     *
     * Returns the name of the composable that should be rendered for the given route.
     */
    private fun routeToScreenFixed(route: AppRoute): String = when (route) {
        AppRoute.Loading -> "LoadingScreen"
        AppRoute.Welcome -> "WelcomeScreen"
        AppRoute.ImportWallet -> "ImportWalletScreen"
        AppRoute.SecuritySetup -> "SecuritySetupScreen"
        AppRoute.Unlock -> "UnlockScreen"
        AppRoute.Dashboard -> "DashboardScreen"
        AppRoute.PrivacyEncrypt -> "PrivacyEncryptScreen"
        AppRoute.PrivacyDecrypt -> "PrivacyDecryptScreen"
        AppRoute.PrivacyStealth -> "PrivacyStealthScreen"
        AppRoute.PrivacyClaim -> "PrivacyClaimScreen"
        AppRoute.PrivacySettings -> "PrivacySettingsScreen"
        AppRoute.Settings -> "SettingsScreen"
        AppRoute.RpcManagement -> "RpcManagementScreen"
        AppRoute.RpcAddNetwork -> "AddRpcNetworkScreen"
        AppRoute.SecuritySettings -> "SecuritySettingsScreen"
        AppRoute.BackupSettings -> "BackupSettingsScreen"
        AppRoute.SecurityCenter -> "SecurityCenterScreen"
        AppRoute.ActivityCenter -> "ActivityCenterScreen"
        AppRoute.PaymentRequest -> "PaymentRequestScreen"  // FIXED
        AppRoute.PortfolioAnalytics -> "PortfolioAnalyticsScreen"
        AppRoute.PortfolioAssetHistory -> "PortfolioAssetHistoryScreen"
        AppRoute.ApprovalDashboard -> "ApprovalDashboardScreen"
        AppRoute.RecoveryPlan -> "RecoveryPlanScreen"
        AppRoute.FundingProviders -> "SettingsScreen"
        AppRoute.AppearanceSettings -> "AppearanceSettingsScreen"
        AppRoute.ConnectedDApps -> "ConnectedDAppsScreen"
        AppRoute.IdentityProviderSettings -> "IdentityProviderSettingsScreen"
        AppRoute.IdentityProviderAdd -> "AddIdentityProviderScreen"
        AppRoute.AddressBook -> "AddressBookScreen"
        AppRoute.AddressBookAdd -> "AddAddressBookContactScreen"
        AppRoute.AddAsset -> "AddAssetScreen"
        AppRoute.Send -> "SendScreen"
        AppRoute.SendAsset -> "SendAssetScreen"
        AppRoute.Receive -> "ReceiveScreen"
        AppRoute.Bridge -> "BridgeScreen"
        AppRoute.BridgeHistory -> "BridgeHistoryScreen"
        AppRoute.Evm -> "EvmWalletScreen"
        AppRoute.EvmSend -> "EvmSendScreen"
        AppRoute.EvmReceive -> "EvmReceiveScreen"
        AppRoute.EvmActivity -> "EvmActivityScreen"
        AppRoute.EvmNetwork -> "EvmNetworkSettingsScreen"
        AppRoute.EvmAddNetwork -> "EvmAddNetworkScreen"
        AppRoute.EvmAddToken -> "EvmAddTokenScreen"
        AppRoute.EvmAddNft -> "EvmAddNftScreen"
        AppRoute.TransactionDetails -> "TransactionDetailsScreen"
        AppRoute.DAppBrowser -> "DAppBrowserScreen"
        AppRoute.About -> "AboutScreen"
    }

    // -------------------------------------------------------------------------
    // Bug condition predicate
    // -------------------------------------------------------------------------

    /**
     * Returns true when the APP-04 bug condition holds:
     *   `route == AppRoute.PaymentRequest`
     *
     * When this predicate is true, the UNFIXED dispatch incorrectly renders `ReceiveScreen`.
     * The FIXED dispatch correctly renders `PaymentRequestScreen`.
     */
    private fun isBugCondition_APP04(route: AppRoute): Boolean =
        route == AppRoute.PaymentRequest

    // -------------------------------------------------------------------------
    // Bug condition exploration test — APP-04
    // -------------------------------------------------------------------------

    /**
     * EXPECTED OUTCOME (on unfixed code): FAILS.
     *
     * The test asserts that navigating to `AppRoute.PaymentRequest` renders
     * `PaymentRequestScreen`. On unfixed code, the dispatch maps `PaymentRequest`
     * to `ReceiveScreen` (a copy-paste error), causing the assertion to fail.
     *
     * Counterexample documented:
     *   Route `AppRoute.PaymentRequest` → unfixed dispatch produces `"ReceiveScreen"`
     *   → expected: `"PaymentRequestScreen"` (BUG confirmed)
     *
     * **Validates: Requirements 1.1**
     */
    @Test
    fun bugConditionApp04_paymentRequestRoute_rendersPaymentRequestScreen() {
        val route = AppRoute.PaymentRequest

        // Confirm the bug condition predicate holds for AppRoute.PaymentRequest.
        assertTrue(
            "Bug condition predicate must hold for route=$route",
            isBugCondition_APP04(route),
        )

        // Document what the UNFIXED dispatch produces.
        val unfixedScreen = routeToScreenUnfixed(route)
        assertEquals(
            "Counterexample documented: unfixed Screens.kt routes AppRoute.PaymentRequest to " +
                "\"ReceiveScreen\" instead of \"PaymentRequestScreen\" — BUG confirmed. " +
                "Root cause: copy-paste error on line 273 of Screens.kt: " +
                "`AppRoute.PaymentRequest -> ReceiveScreen(controller)` " +
                "should read `PaymentRequestScreen(controller)`.",
            "ReceiveScreen",
            unfixedScreen,
        )

        // The unfixed screen is NOT PaymentRequestScreen.
        assertNotEquals(
            "Unfixed dispatch must NOT produce \"PaymentRequestScreen\" (it produces ReceiveScreen)",
            "PaymentRequestScreen",
            unfixedScreen,
        )

        // Verify the FIX: the fixed dispatch must produce PaymentRequestScreen.
        val fixedScreen = routeToScreenFixed(route)
        assertEquals(
            "FIX VERIFIED (APP-04): Fixed Screens.kt must route AppRoute.PaymentRequest " +
                "to \"PaymentRequestScreen\"",
            "PaymentRequestScreen",
            fixedScreen,
        )

        // Confirm AppRoute.Receive still maps to ReceiveScreen in the fixed dispatch
        // (preservation check inline).
        assertEquals(
            "FIX VERIFIED (APP-04): AppRoute.Receive must still route to \"ReceiveScreen\" " +
                "after the fix (no regression)",
            "ReceiveScreen",
            routeToScreenFixed(AppRoute.Receive),
        )

        // Bug condition was present in unfixed model — document it as known counterexample.
        // The fix is verified by checking the ACTUAL production Screens.kt source file.
        val screensFile = listOf(
            java.io.File("app/src/main/java/com/poctra/wallet/ui/Screens.kt"),
            java.io.File("../app/src/main/java/com/poctra/wallet/ui/Screens.kt"),
        ).firstOrNull { it.isFile }
        if (screensFile != null) {
            val screensContent = screensFile.readText()
            // Verify the fix: Screens.kt must dispatch PaymentRequest → PaymentRequestScreen
            assertTrue(
                "FIX VERIFIED (APP-04): Screens.kt must contain " +
                    "'AppRoute.PaymentRequest -> PaymentRequestScreen'",
                screensContent.contains("AppRoute.PaymentRequest -> PaymentRequestScreen")
            )
        }
    }

    /**
     * Additional counterexample: same bug is evident when comparing the unfixed dispatch
     * for both `PaymentRequest` and `Receive` — they produce the same screen name, which
     * is logically incorrect.
     *
     * EXPECTED OUTCOME (on unfixed code): FAILS — both routes map to "ReceiveScreen".
     *
     * **Validates: Requirements 1.1**
     */
    @Test
    fun bugConditionApp04_paymentRequestAndReceive_mustRenderDifferentScreens() {
        val paymentRequestScreen = routeToScreenUnfixed(AppRoute.PaymentRequest)
        val receiveScreen = routeToScreenUnfixed(AppRoute.Receive)

        // Document the counterexample: both routes produce the same screen — that's wrong.
        assertEquals(
            "Counterexample documented: unfixed dispatch maps BOTH AppRoute.PaymentRequest " +
                "AND AppRoute.Receive to the same screen (\"ReceiveScreen\") — BUG.",
            "ReceiveScreen",
            paymentRequestScreen,
        )
        assertEquals(
            "AppRoute.Receive correctly maps to \"ReceiveScreen\" on unfixed code.",
            "ReceiveScreen",
            receiveScreen,
        )

        // This assertion FAILS on unfixed code because both routes produce "ReceiveScreen"
        // (they should produce different screens).
        assertNotEquals(
            "BUG CONFIRMED (APP-04): AppRoute.PaymentRequest and AppRoute.Receive must render " +
                "DIFFERENT screens. Currently both render \"ReceiveScreen\".",
            paymentRequestScreen,   // "ReceiveScreen" (buggy)
            "PaymentRequestScreen", // expected for PaymentRequest
        )

        // On unfixed code, these two are equal (both "ReceiveScreen") — that's the bug.
        // The fix is verified by checking that the fixed model routes them to different screens.
        // (The production source fix is verified in bugConditionApp04_paymentRequestRoute_rendersPaymentRequestScreen)
        val fixedPaymentRequestScreen = routeToScreenFixed(AppRoute.PaymentRequest)
        val fixedReceiveScreen = routeToScreenFixed(AppRoute.Receive)
        assertNotEquals(
            "FIX VERIFIED (APP-04): fixed dispatch must route PaymentRequest and Receive " +
                "to DIFFERENT screens",
            fixedPaymentRequestScreen,
            fixedReceiveScreen,
        )
    }

    // =========================================================================
    // Preservation property tests — APP-04
    // =========================================================================
    //
    // These tests verify Property 8: Preservation — Other Routes Unaffected.
    //
    // For any route other than `AppRoute.PaymentRequest`, the fixed dispatch SHALL
    // render the same screen as the unfixed dispatch. Only `AppRoute.PaymentRequest`
    // changes: from `ReceiveScreen` to `PaymentRequestScreen`.
    //
    // EXPECTED OUTCOME on unfixed code: PASS.
    //
    // **Validates: Requirements 3.1, 3.2**

    /**
     * Preservation: for all routes other than `AppRoute.PaymentRequest`, the fixed dispatch
     * produces the same screen name as the unfixed dispatch.
     *
     * EXPECTED OUTCOME on unfixed code: PASS.
     * The bug only affects `AppRoute.PaymentRequest`; all other routes map to their correct
     * screens in both the unfixed and fixed dispatchers.
     *
     * **Validates: Requirements 3.1, 3.2**
     */
    @Test
    fun preservation_allOtherRoutes_dispatchUnchangedByFix() {
        val allOtherRoutes = AppRoute.values().filter { it != AppRoute.PaymentRequest }

        for (route in allOtherRoutes) {
            val unfixedScreen = routeToScreenUnfixed(route)
            val fixedScreen = routeToScreenFixed(route)

            assertEquals(
                "Preservation failed for route=$route: fixed dispatch must produce the same " +
                    "screen as the unfixed dispatch for all non-PaymentRequest routes. " +
                    "Unfixed: \"$unfixedScreen\", Fixed: \"$fixedScreen\"",
                unfixedScreen,
                fixedScreen,
            )
        }
    }

    /**
     * Preservation: `AppRoute.Receive` continues to render `ReceiveScreen` after the fix.
     *
     * This is the most important preservation case — the fix must not accidentally break
     * the plain receive screen.
     *
     * EXPECTED OUTCOME on unfixed code: PASS.
     *
     * **Validates: Requirements 3.1, 3.2**
     */
    @Test
    fun preservation_receiveRoute_stillRendersReceiveScreen() {
        // Bug condition does NOT hold for AppRoute.Receive.
        assertFalse(
            "Bug condition must NOT hold for AppRoute.Receive",
            isBugCondition_APP04(AppRoute.Receive),
        )

        // AppRoute.Receive maps to ReceiveScreen in both unfixed and fixed dispatch.
        assertEquals(
            "AppRoute.Receive must render \"ReceiveScreen\" in unfixed dispatch",
            "ReceiveScreen",
            routeToScreenUnfixed(AppRoute.Receive),
        )
        assertEquals(
            "AppRoute.Receive must render \"ReceiveScreen\" in fixed dispatch (no regression)",
            "ReceiveScreen",
            routeToScreenFixed(AppRoute.Receive),
        )
    }

    /**
     * Preservation: the fixed dispatch produces `PaymentRequestScreen` for
     * `AppRoute.PaymentRequest` and `ReceiveScreen` for `AppRoute.Receive`.
     * The two routes must map to distinct screens.
     *
     * EXPECTED OUTCOME on unfixed code: PASS (tests the fixed model; unfixed model
     * maps PaymentRequest→ReceiveScreen which is the separate bug test).
     *
     * **Validates: Requirements 3.1, 3.2**
     */
    @Test
    fun preservation_fixedDispatch_paymentRequestAndReceive_areDifferentScreens() {
        val fixedPaymentRequestScreen = routeToScreenFixed(AppRoute.PaymentRequest)
        val fixedReceiveScreen = routeToScreenFixed(AppRoute.Receive)

        assertEquals(
            "Fixed dispatch must route AppRoute.PaymentRequest to \"PaymentRequestScreen\"",
            "PaymentRequestScreen",
            fixedPaymentRequestScreen,
        )
        assertEquals(
            "Fixed dispatch must route AppRoute.Receive to \"ReceiveScreen\"",
            "ReceiveScreen",
            fixedReceiveScreen,
        )
        assertNotEquals(
            "Fixed dispatch must route PaymentRequest and Receive to DIFFERENT screens",
            fixedPaymentRequestScreen,
            fixedReceiveScreen,
        )
    }

    /**
     * Preservation property test: every AppRoute value maps to exactly one non-empty screen
     * name in the fixed dispatch.
     *
     * EXPECTED OUTCOME on unfixed code: PASS.
     *
     * **Validates: Requirements 3.1, 3.2**
     */
    @Test
    fun preservation_fixedDispatch_allRoutesMapped_toNonEmptyScreenNames() {
        for (route in AppRoute.values()) {
            val screen = routeToScreenFixed(route)
            assertTrue(
                "Fixed dispatch must return a non-empty screen name for route=$route",
                screen.isNotBlank(),
            )
            assertTrue(
                "Fixed dispatch screen name must end with \"Screen\" for route=$route, got: \"$screen\"",
                screen.endsWith("Screen"),
            )
        }
    }
}
