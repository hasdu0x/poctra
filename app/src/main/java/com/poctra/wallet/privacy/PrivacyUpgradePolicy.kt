package com.poctra.wallet.privacy

data class PrivacyUpgradeFacts(
    val cipherPresent: Boolean,
    val diagnostics: PrivacyCipherDiagnostics? = null,
    val localPublicKey: String,
    val registeredPublicKey: String?,
    val registeredPublicKeyMatchesLocal: Boolean? = null,
    val compatibility: PrivacyKeyCompatibility? = null,
    val localBalanceRaw: Long? = null,
    val registeredKeyBalanceRaw: Long? = null,
    val legacyOwnerBalanceRaw: Long? = null,
    val migration: PrivacyMigrationNetworkStatus? = null,
    val repairRequired: Boolean = false,
    val forceRefresh: Boolean = false,
)

object PrivacyUpgradePolicy {
    // Keep this in sync with PVAC_PRIVATE_SPEND_MAX_BASE_LAYERS in webcli.
    const val PrivateSpendMaxBaseLayers = 6L
    const val MinimumFeeRaw = 3_000L
    const val MaximumFeeRaw = 10_000_000L
    const val FallbackFeeRaw = 1_000_000L
    const val ResetPhrase = "RESET ENCRYPTED BALANCE"

    fun evaluate(facts: PrivacyUpgradeFacts): PrivacyKeyUpgradeStatus {
        val registered = facts.registeredPublicKey.orEmpty()
        val registeredPresent = registered.isNotBlank()
        val registeredCurrent = registeredPresent &&
            (facts.registeredPublicKeyMatchesLocal ?: (registered == facts.localPublicKey))
        val registeredCompatible = registeredCurrent ||
            facts.compatibility?.let {
                it.extendsLocalKey || it.legacyV1Profile || it.proofProfile
            } == true
        val diagnostics = facts.diagnostics

        fun status(
            mode: PrivacyKeyUpgradeMode,
            canSubmit: Boolean,
            reason: String,
            amount: Long? = facts.localBalanceRaw,
            repair: Boolean = false,
            compact: Boolean = false,
            legacyCommitment: String? = null,
        ) = PrivacyKeyUpgradeStatus(
            mode = mode,
            canSubmit = canSubmit,
            reason = reason,
            cipherPresent = facts.cipherPresent,
            encryptedBalanceRaw = amount,
            registeredKeyPresent = registeredPresent,
            registeredKeyCurrent = registeredCurrent,
            registeredKeyCompatible = registeredCompatible,
            repairRequired = repair,
            compactRefresh = compact,
            baseLayerCount = diagnostics?.baseLayerCount ?: 0,
            canonicalRandomnessCommitment = diagnostics?.canonicalRandomnessCommitment == true,
            legacyCommitmentNet = legacyCommitment,
        )

        if (!facts.cipherPresent) {
            return if (registeredCurrent) {
                status(PrivacyKeyUpgradeMode.Current, false, "Privacy key is current.", amount = 0L)
            } else {
                status(
                    PrivacyKeyUpgradeMode.EmptyKeySwitch,
                    true,
                    if (registeredCompatible) {
                        "Compatible Privacy key profile requires confirmation."
                    } else {
                        "Register the current Privacy key before using private operations."
                    },
                    amount = 0L,
                )
            }
        }

        if (diagnostics == null) {
            return status(PrivacyKeyUpgradeMode.Blocked, false, "Encrypted balance diagnostics are unavailable.")
        }

        if (!diagnostics.keyBound) {
            val migration = facts.migration
                ?: return status(
                    PrivacyKeyUpgradeMode.Blocked,
                    false,
                    "The active network does not expose legacy migration status.",
                    amount = facts.legacyOwnerBalanceRaw,
                )
            val replayAmount = migration.publicNetRaw
            if (migration.canPublicMigrate && replayAmount != null) {
                if (facts.legacyOwnerBalanceRaw != null && facts.legacyOwnerBalanceRaw != replayAmount) {
                    return status(
                        PrivacyKeyUpgradeMode.Blocked,
                        false,
                        "Network replay does not match the locally decrypted balance.",
                        amount = facts.legacyOwnerBalanceRaw,
                    )
                }
                return if (replayAmount == 0L) {
                    status(
                        PrivacyKeyUpgradeMode.LegacyZeroReset,
                        true,
                        "Legacy public history proves a zero encrypted balance.",
                        amount = 0L,
                    )
                } else {
                    status(
                        PrivacyKeyUpgradeMode.LegacyPublicMigration,
                        true,
                        "Legacy encrypted balance can be preserved using network replay.",
                        amount = replayAmount,
                    )
                }
            }
            if (migration.auditClass == "hidden_witness") {
                val amount = facts.legacyOwnerBalanceRaw
                if (amount != null && amount > 0 && !migration.commitmentNet.isNullOrBlank()) {
                    return status(
                        PrivacyKeyUpgradeMode.LegacyCommitmentMigration,
                        true,
                        "Legacy hidden balance can be preserved using commitment history.",
                        amount = amount,
                        legacyCommitment = migration.commitmentNet,
                    )
                }
                return status(
                    PrivacyKeyUpgradeMode.Blocked,
                    false,
                    "Legacy hidden balance cannot be reconstructed safely.",
                    amount = amount,
                )
            }
            if (
                migration.cipherClass == "legacy_hfhe" &&
                facts.legacyOwnerBalanceRaw == 0L
            ) {
                return status(
                    PrivacyKeyUpgradeMode.LegacyZeroReset,
                    true,
                    "Legacy ciphertext cannot be preserved; a verified zero reset is available.",
                    amount = 0L,
                )
            }
            return status(
                PrivacyKeyUpgradeMode.Blocked,
                false,
                migration.reason.ifBlank { "Legacy encrypted balance cannot be migrated safely." },
                amount = facts.legacyOwnerBalanceRaw,
            )
        }

        if (!registeredPresent) {
            return status(
                PrivacyKeyUpgradeMode.Blocked,
                false,
                "Registered Privacy key is missing for this encrypted balance.",
            )
        }
        if (!registeredCurrent) {
            return if (facts.compatibility?.proofProfile == true && facts.registeredKeyBalanceRaw != null) {
                status(
                    PrivacyKeyUpgradeMode.KeyBoundMigration,
                    true,
                    "Encrypted balance must be recrypted to the current Privacy key.",
                    amount = facts.registeredKeyBalanceRaw,
                )
            } else {
                status(
                    PrivacyKeyUpgradeMode.KeyMismatch,
                    false,
                    "Registered Privacy key does not match this wallet. Recheck the network state after a recent key switch.",
                )
            }
        }
        if (facts.localBalanceRaw == null) {
            return status(PrivacyKeyUpgradeMode.Blocked, false, "Encrypted balance cannot be decrypted.")
        }
        if (facts.forceRefresh || facts.repairRequired || !diagnostics.canonicalRandomnessCommitment) {
            return status(
                PrivacyKeyUpgradeMode.KeyBoundMigration,
                true,
                if (facts.repairRequired) {
                    "Encrypted balance repair is required after a local proof check failed."
                } else {
                    "Encrypted balance privacy refresh is required."
                },
                repair = facts.repairRequired,
            )
        }
        if (diagnostics.baseLayerCount > PrivateSpendMaxBaseLayers) {
            return status(
                PrivacyKeyUpgradeMode.KeyBoundMigration,
                true,
                "Encrypted balance compact refresh is required before private spend.",
                compact = true,
            )
        }
        return status(
            PrivacyKeyUpgradeMode.Current,
            false,
            "Encrypted balance is bound to the current Privacy key.",
        )
    }

    fun applyFeeGate(
        status: PrivacyKeyUpgradeStatus,
        recommendedFeeRaw: Long?,
        publicBalanceRaw: Long?,
    ): PrivacyKeyUpgradeStatus {
        if (!status.canSubmit) return status
        val fee = recommendedFeeRaw
            ?: return status.copy(
                mode = PrivacyKeyUpgradeMode.FeeBlocked,
                canSubmit = false,
                reason = "Unable to read the key switch fee.",
            )
        if (fee !in MinimumFeeRaw..MaximumFeeRaw) {
            return status.copy(
                mode = PrivacyKeyUpgradeMode.FeeBlocked,
                canSubmit = false,
                reason = "The network key switch fee is outside the wallet safety limit.",
            )
        }
        if (publicBalanceRaw == null || publicBalanceRaw < fee) {
            return status.copy(
                mode = PrivacyKeyUpgradeMode.FeeBlocked,
                canSubmit = false,
                reason = "Public OCT balance is insufficient for the key switch fee.",
                requiredPublicFeeRaw = fee,
            )
        }
        return status.copy(requiredPublicFeeRaw = fee)
    }
}
