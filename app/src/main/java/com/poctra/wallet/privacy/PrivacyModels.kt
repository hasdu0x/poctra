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
}

enum class PrivacyOperationStage {
    Scanning,
    Review,
    Preparing,
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
