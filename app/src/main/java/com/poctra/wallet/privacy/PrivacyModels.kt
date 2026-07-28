package com.poctra.wallet.privacy

import com.poctra.wallet.data.TransactionHistoryItem

enum class PrivacySyncStatus {
    Waiting,
    Running,
    Complete,
    Failed,
}

data class PrivacySyncStep(
    val label: String,
    val status: PrivacySyncStatus = PrivacySyncStatus.Waiting,
    val detail: String? = null,
)

enum class PrivacyOperationType(val label: String) {
    Encrypt("Encrypt"),
    Decrypt("Decrypt"),
    Stealth("Stealth"),
    Claim("Claim"),
    KeySwitch("Key Switch"),
}

enum class PrivacyOperationStage {
    Scanning,
    Review,
    Checking,
    Preparing,
    BuildingProof,
    Registering,
    Submitting,
    Accepted,
    Confirmed,
    Empty,
    Failed,
}

data class PrivacyOperationState(
    val id: Long,
    val type: PrivacyOperationType,
    val stage: PrivacyOperationStage,
    val amountOct: String = "",
    val recipient: String? = null,
    val claimOutputIds: List<String> = emptyList(),
    val message: String,
    val progressIndex: Int = 0,
    val txHash: String? = null,
    val dialogVisible: Boolean = true,
    val startedAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
) {
    val isTerminal: Boolean
        get() = stage in setOf(
            PrivacyOperationStage.Confirmed,
            PrivacyOperationStage.Empty,
            PrivacyOperationStage.Failed,
        )

    val hasSubmittedTransaction: Boolean
        get() = stage in setOf(
            PrivacyOperationStage.Submitting,
            PrivacyOperationStage.Accepted,
            PrivacyOperationStage.Confirmed,
        ) || !txHash.isNullOrBlank()
}

data class PrivacyEncryptedBalance(
    val cipher: String,
)

data class PrivacyCipherDiagnostics(
    val keyBound: Boolean,
    val canonicalRandomnessCommitment: Boolean,
    val baseLayerCount: Long,
)

data class PrivacyKeyCompatibility(
    val extendsLocalKey: Boolean,
    val legacyV1Profile: Boolean,
    val proofProfile: Boolean,
)

data class PrivacyMigrationNetworkStatus(
    val auditClass: String = "",
    val reason: String = "",
    val cipherClass: String = "",
    val canPublicMigrate: Boolean = false,
    val publicNetRaw: Long? = null,
    val commitmentNet: String? = null,
)

enum class PrivacyKeyUpgradeMode {
    Current,
    EmptyKeySwitch,
    KeyBoundMigration,
    LegacyPublicMigration,
    LegacyCommitmentMigration,
    LegacyZeroReset,
    KeyMismatch,
    FeeBlocked,
    Blocked,
    Unavailable,
}

data class PrivacyKeyUpgradeStatus(
    val mode: PrivacyKeyUpgradeMode = PrivacyKeyUpgradeMode.Unavailable,
    val canSubmit: Boolean = false,
    val reason: String = "Privacy key status is unavailable.",
    val cipherPresent: Boolean = false,
    val encryptedBalanceRaw: Long? = null,
    val registeredKeyPresent: Boolean = false,
    val registeredKeyCurrent: Boolean = false,
    val registeredKeyCompatible: Boolean = false,
    val repairRequired: Boolean = false,
    val compactRefresh: Boolean = false,
    val baseLayerCount: Long = 0,
    val canonicalRandomnessCommitment: Boolean = false,
    val legacyCommitmentNet: String? = null,
    val requiredPublicFeeRaw: Long? = null,
) {
    val requiresAction: Boolean
        get() = mode !in setOf(
            PrivacyKeyUpgradeMode.Current,
            PrivacyKeyUpgradeMode.Unavailable,
        )

    val isReadyForPrivateOperations: Boolean
        get() = mode == PrivacyKeyUpgradeMode.Current
}

data class PrivacyMigrationPayload(
    val newCipher: String,
    val newZeroProof: String,
    val amountCommitment: String,
    val oldZeroProof: String? = null,
    val amountBlinding: String? = null,
)

data class PrivacyIdentity(
    val publicKey: ByteArray,
    val aesKat: ByteArray,
)

data class PrivacyEncryptPayload(
    val cipher: String,
    val amountCommitment: String,
    val zeroProof: String,
    val blinding: String,
)

data class PrivacyClaimPayload(
    val cipher: String,
    val commitment: String,
    val zeroProof: String,
)

data class PrivacyDecryptPayload(
    val cipher: String,
    val amountCommitment: String,
    val zeroProof: String,
    val blinding: String,
    val rangeProofBalance: String,
)

data class PrivacyStealthProofPayload(
    val deltaCipher: String,
    val commitment: String,
    val rangeProofDelta: String,
    val rangeProofBalance: String,
    val amountCommitment: String,
    val zeroProof: String,
)

data class PrivacyStealthEnvelope(
    val ephemeralPublicKey: ByteArray,
    val stealthTag: ByteArray,
    val encryptedAmount: String,
    val claimPublicKey: ByteArray,
    val blinding: ByteArray,
)

data class PrivacyClaimableTransfer(
    val outputId: String,
    val amountRaw: Long,
    val epoch: Long,
    val sender: String,
    val txHash: String,
    val claimSecret: ByteArray,
    val blinding: ByteArray,
) {
    val amountOct: String
        get() = formatPrivacyAmount(amountRaw)
}

data class PrivacySnapshot(
    val encryptedBalance: PrivacyEncryptedBalance?,
    val history: List<TransactionHistoryItem>,
)

internal fun formatPrivacyAmount(raw: Long): String {
    val whole = raw / 1_000_000L
    val fraction = (raw % 1_000_000L).toString().padStart(6, '0').trimEnd('0')
    return if (fraction.isBlank()) whole.toString() else "$whole.$fraction"
}
