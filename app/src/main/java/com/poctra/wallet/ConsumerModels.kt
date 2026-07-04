package com.poctra.wallet

import org.json.JSONObject
import java.net.URLEncoder

enum class ConsumerRiskLevel {
    Safe,
    Notice,
    Warning,
    Critical,
}

data class ConsumerRiskItem(
    val title: String,
    val description: String,
    val level: ConsumerRiskLevel,
)

data class UnifiedActivityItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val amount: String,
    val status: String,
    val timestamp: Long,
    val glyph: String,
    val source: String,
    val explorerUrl: String? = null,
    val assetSymbol: String? = null,
)

data class PortfolioHolding(
    val title: String,
    val subtitle: String,
    val balance: String,
    val fiatEstimate: String,
    val source: String,
    val riskLevel: ConsumerRiskLevel = ConsumerRiskLevel.Safe,
    val assetId: String = title,
    val symbol: String = title,
    val network: String = source,
    val assetAddress: String? = null,
    val valueUsd: Double = 0.0,
)

data class ApprovalReviewItem(
    val id: String,
    val tokenSymbol: String,
    val tokenName: String,
    val tokenAddress: String,
    val spenderName: String,
    val spenderAddress: String,
    val allowance: String,
    val networkName: String,
    val level: ConsumerRiskLevel,
    val error: String? = null,
)

data class DomainGuardRule(
    val host: String,
    val trusted: Boolean,
    val note: String = "",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("host", host)
        .put("trusted", trusted)
        .put("note", note)

    companion object {
        fun fromJson(json: JSONObject): DomainGuardRule = DomainGuardRule(
            host = json.optString("host"),
            trusted = json.optBoolean("trusted", false),
            note = json.optString("note"),
        )
    }
}

data class NotificationPreferences(
    val securityAlerts: Boolean = true,
    val transactionAlerts: Boolean = true,
    val bridgeAlerts: Boolean = true,
    val priceAlerts: Boolean = false,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("securityAlerts", securityAlerts)
        .put("transactionAlerts", transactionAlerts)
        .put("bridgeAlerts", bridgeAlerts)
        .put("priceAlerts", priceAlerts)

    companion object {
        fun fromJson(json: JSONObject?): NotificationPreferences {
            if (json == null) return NotificationPreferences()
            return NotificationPreferences(
                securityAlerts = json.optBoolean("securityAlerts", true),
                transactionAlerts = json.optBoolean("transactionAlerts", true),
                bridgeAlerts = json.optBoolean("bridgeAlerts", true),
                priceAlerts = json.optBoolean("priceAlerts", false),
            )
        }
    }
}

data class FundingProvider(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val enabled: Boolean,
)

data class SimulationCheck(
    val title: String,
    val detail: String,
    val level: ConsumerRiskLevel,
)

data class PaymentRequestDraft(
    val address: String,
    val label: String,
    val amount: String = "",
    val asset: String = "OCT",
    val memo: String = "",
) {
    val uri: String
        get() = buildString {
            append("octra:")
            append(address)
            val params = listOfNotNull(
                amount.trim().takeIf { it.isNotBlank() }?.let { "amount=${encodePaymentParam(it)}" },
                asset.trim().takeIf { it.isNotBlank() }?.let { "asset=${encodePaymentParam(it)}" },
            )
            if (params.isNotEmpty()) append("?").append(params.joinToString("&"))
        }
}

private fun encodePaymentParam(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name())
