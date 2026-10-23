package com.poctra.wallet.privacy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrivacyUpgradePolicyTest {
    @Test
    fun emptyBalanceRegistersMissingPrivacyKey() {
        val result = PrivacyUpgradePolicy.evaluate(facts(cipherPresent = false, registeredKey = null))

        assertEquals(PrivacyKeyUpgradeMode.EmptyKeySwitch, result.mode)
        assertTrue(result.canSubmit)
        assertEquals(0L, result.encryptedBalanceRaw)
    }

    @Test
    fun emptyBalanceWithCurrentKeyIsReady() {
        val result = PrivacyUpgradePolicy.evaluate(facts(cipherPresent = false))

        assertEquals(PrivacyKeyUpgradeMode.Current, result.mode)
        assertFalse(result.requiresAction)
    }

    @Test
    fun currentCanonicalCipherIsReady() {
        val result = PrivacyUpgradePolicy.evaluate(facts())

        assertEquals(PrivacyKeyUpgradeMode.Current, result.mode)
        assertTrue(result.isReadyForPrivateOperations)
    }

    @Test
    fun ordinaryNetworkRefreshDoesNotRequestRecrypt() {
        val result = PrivacyUpgradePolicy.evaluate(facts(forceRefresh = false))

        assertEquals(PrivacyKeyUpgradeMode.Current, result.mode)
        assertFalse(result.requiresAction)
    }

    @Test
    fun explicitPrivacyRefreshRequestsRecrypt() {
        val result = PrivacyUpgradePolicy.evaluate(facts(forceRefresh = true))

        assertEquals(PrivacyKeyUpgradeMode.KeyBoundMigration, result.mode)
        assertTrue(result.canSubmit)
    }

    @Test
    fun canonicalKeyMatchOverridesDifferentBase64Text() {
        val result = PrivacyUpgradePolicy.evaluate(
            facts(
                registeredKey = "equivalent-encoded-key",
                registeredKeyMatchesLocal = true,
            ),
        )

        assertEquals(PrivacyKeyUpgradeMode.Current, result.mode)
        assertTrue(result.registeredKeyCurrent)
    }

    @Test
    fun compatibleRegisteredKeyUsesKeyBoundMigration() {
        val result = PrivacyUpgradePolicy.evaluate(
            facts(
                registeredKey = "old-key",
                compatibility = PrivacyKeyCompatibility(
                    extendsLocalKey = false,
                    legacyV1Profile = false,
                    proofProfile = false,
                    secretProfile = true,
                ),
                registeredBalance = 3_000_000L,
            ),
        )

        assertEquals(PrivacyKeyUpgradeMode.KeyBoundMigration, result.mode)
        assertEquals(3_000_000L, result.encryptedBalanceRaw)
        assertTrue(result.canSubmit)
    }

    @Test
    fun matchingPublicProofShapeWithoutSecretOwnershipIsBlocked() {
        val result = PrivacyUpgradePolicy.evaluate(
            facts(
                registeredKey = "foreign-key",
                compatibility = PrivacyKeyCompatibility(
                    extendsLocalKey = false,
                    legacyV1Profile = false,
                    proofProfile = true,
                    secretProfile = false,
                ),
                registeredBalance = 3_000_000L,
            ),
        )

        assertEquals(PrivacyKeyUpgradeMode.KeyMismatch, result.mode)
        assertFalse(result.canSubmit)
    }

    @Test
    fun incompatibleRegisteredKeyIsBlocked() {
        val result = PrivacyUpgradePolicy.evaluate(
            facts(
                registeredKey = "foreign-key",
                compatibility = PrivacyKeyCompatibility(false, false, false),
                registeredBalance = null,
            ),
        )

        assertEquals(PrivacyKeyUpgradeMode.KeyMismatch, result.mode)
        assertFalse(result.canSubmit)
    }

    @Test
    fun nonCanonicalOrDeepCipherRequiresRefresh() {
        val nonCanonical = PrivacyUpgradePolicy.evaluate(
            facts(diagnostics = diagnostics(canonical = false)),
        )
        val deep = PrivacyUpgradePolicy.evaluate(
            facts(diagnostics = diagnostics(layers = 5)),
        )
        val boundary = PrivacyUpgradePolicy.evaluate(
            facts(diagnostics = diagnostics(layers = 4)),
        )
        val belowBoundary = PrivacyUpgradePolicy.evaluate(
            facts(diagnostics = diagnostics(layers = 3)),
        )

        assertEquals(PrivacyKeyUpgradeMode.KeyBoundMigration, nonCanonical.mode)
        assertEquals(PrivacyKeyUpgradeMode.KeyBoundMigration, deep.mode)
        assertTrue(deep.compactRefresh)
        assertEquals(PrivacyKeyUpgradeMode.KeyBoundMigration, boundary.mode)
        assertTrue(boundary.compactRefresh)
        assertEquals(PrivacyKeyUpgradeMode.Current, belowBoundary.mode)
    }

    @Test
    fun legacyPublicReplayPreservesBalance() {
        val result = PrivacyUpgradePolicy.evaluate(
            facts(
                diagnostics = diagnostics(keyBound = false),
                legacyBalance = 2_500_000L,
                migration = PrivacyMigrationNetworkStatus(
                    canPublicMigrate = true,
                    publicNetRaw = 2_500_000L,
                ),
            ),
        )

        assertEquals(PrivacyKeyUpgradeMode.LegacyPublicMigration, result.mode)
        assertEquals(2_500_000L, result.encryptedBalanceRaw)
    }

    @Test
    fun legacyPublicReplayMismatchIsBlocked() {
        val result = PrivacyUpgradePolicy.evaluate(
            facts(
                diagnostics = diagnostics(keyBound = false),
                legacyBalance = 2_000_000L,
                migration = PrivacyMigrationNetworkStatus(
                    canPublicMigrate = true,
                    publicNetRaw = 3_000_000L,
                ),
            ),
        )

        assertEquals(PrivacyKeyUpgradeMode.Blocked, result.mode)
        assertFalse(result.canSubmit)
    }

    @Test
    fun legacyHiddenWitnessUsesCommitmentMigration() {
        val result = PrivacyUpgradePolicy.evaluate(
            facts(
                diagnostics = diagnostics(keyBound = false),
                legacyBalance = 4_000_000L,
                migration = PrivacyMigrationNetworkStatus(
                    auditClass = "hidden_witness",
                    commitmentNet = "commitment",
                ),
            ),
        )

        assertEquals(PrivacyKeyUpgradeMode.LegacyCommitmentMigration, result.mode)
        assertEquals("commitment", result.legacyCommitmentNet)
    }

    @Test
    fun verifiedLegacyZeroUsesExplicitReset() {
        val result = PrivacyUpgradePolicy.evaluate(
            facts(
                diagnostics = diagnostics(keyBound = false),
                legacyBalance = 0L,
                migration = PrivacyMigrationNetworkStatus(
                    cipherClass = "legacy_hfhe",
                    reason = "legacy",
                ),
            ),
        )

        assertEquals(PrivacyKeyUpgradeMode.LegacyZeroReset, result.mode)
        assertTrue(result.canSubmit)
    }

    @Test
    fun feeGateRejectsUnsafeOrUnaffordableFee() {
        val actionable = PrivacyUpgradePolicy.evaluate(
            facts(cipherPresent = false, registeredKey = null),
        )

        val unavailable = PrivacyUpgradePolicy.applyFeeGate(actionable, null, 10_000_000L)
        val unsafe = PrivacyUpgradePolicy.applyFeeGate(actionable, 2_999L, 10_000_000L)
        val unaffordable = PrivacyUpgradePolicy.applyFeeGate(actionable, 1_000_000L, 999_999L)
        val allowed = PrivacyUpgradePolicy.applyFeeGate(actionable, 1_000_000L, 1_000_000L)

        assertEquals(PrivacyKeyUpgradeMode.FeeBlocked, unavailable.mode)
        assertEquals(PrivacyKeyUpgradeMode.FeeBlocked, unsafe.mode)
        assertEquals(PrivacyKeyUpgradeMode.FeeBlocked, unaffordable.mode)
        assertEquals(PrivacyKeyUpgradeMode.EmptyKeySwitch, allowed.mode)
        assertTrue(allowed.canSubmit)
        assertEquals(1_000_000L, allowed.requiredPublicFeeRaw)
    }

    private fun facts(
        cipherPresent: Boolean = true,
        diagnostics: PrivacyCipherDiagnostics = diagnostics(),
        registeredKey: String? = LocalKey,
        registeredKeyMatchesLocal: Boolean? = null,
        compatibility: PrivacyKeyCompatibility? = null,
        localBalance: Long? = 1_000_000L,
        registeredBalance: Long? = null,
        legacyBalance: Long? = null,
        migration: PrivacyMigrationNetworkStatus? = null,
        forceRefresh: Boolean = false,
    ) = PrivacyUpgradeFacts(
        cipherPresent = cipherPresent,
        diagnostics = if (cipherPresent) diagnostics else null,
        localPublicKey = LocalKey,
        registeredPublicKey = registeredKey,
        registeredPublicKeyMatchesLocal = registeredKeyMatchesLocal,
        compatibility = compatibility,
        localBalanceRaw = localBalance,
        registeredKeyBalanceRaw = registeredBalance,
        legacyOwnerBalanceRaw = legacyBalance,
        migration = migration,
        forceRefresh = forceRefresh,
    )

    private companion object {
        const val LocalKey = "local-key"

        fun diagnostics(
            keyBound: Boolean = true,
            canonical: Boolean = true,
            layers: Long = 1,
        ) = PrivacyCipherDiagnostics(
            keyBound = keyBound,
            canonicalRandomnessCommitment = canonical,
            baseLayerCount = layers,
        )
    }
}
