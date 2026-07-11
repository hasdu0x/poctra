package com.poctra.wallet.data

import android.content.Context
import com.poctra.wallet.DomainGuardRule
import com.poctra.wallet.FundingProvider
import com.poctra.wallet.security.DAppUrlPolicy
import org.json.JSONArray
import org.json.JSONObject

class ConsumerSettingsRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("poctra_ui", Context.MODE_PRIVATE)

    fun domainGuardRules(): List<DomainGuardRule> {
        val raw = prefs.getString(KeyDomainGuardRules, null) ?: return defaultDomainGuardRules()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val rule = DomainGuardRule.fromJson(array.getJSONObject(index))
                    if (rule.host.isNotBlank()) add(rule)
                }
            }
        }.getOrDefault(defaultDomainGuardRules())
    }

    fun saveDomainRule(input: String, trusted: Boolean): List<DomainGuardRule> {
        val host = DAppUrlPolicy.normalizeDomainHost(input)
        val next = (domainGuardRules().filterNot { it.host == host } + DomainGuardRule(host, trusted))
            .sortedWith(compareBy<DomainGuardRule> { it.trusted }.thenBy { it.host })
        persistDomainGuardRules(next)
        return next
    }

    fun removeDomainRule(host: String): List<DomainGuardRule> {
        val next = domainGuardRules().filterNot { it.host == host }
        persistDomainGuardRules(next)
        return next
    }

    fun domainGuardRuleForUrl(url: String): DomainGuardRule? {
        val host = runCatching { DAppUrlPolicy.normalizeDomainHost(url) }.getOrNull() ?: return null
        return domainGuardRules().firstOrNull { rule ->
            host == rule.host || host.endsWith(".${rule.host}")
        }
    }

    fun fundingProviders(): List<FundingProvider> = listOf(
        fundingProvider(
            id = FundingProviderBuyId,
            name = "Buy crypto",
            description = "Open your configured on-ramp provider in the dApp browser.",
        ),
        fundingProvider(
            id = FundingProviderSellId,
            name = "Sell crypto",
            description = "Open your configured off-ramp provider in the dApp browser.",
        ),
    )

    fun saveFundingProvider(id: String, url: String, enabled: Boolean): List<FundingProvider> {
        require(id in setOf(FundingProviderBuyId, FundingProviderSellId)) { "Unknown funding provider." }
        val cleanUrl = url.trim().takeIf { it.isNotBlank() }?.let(DAppUrlPolicy::normalizeBrowserUrl).orEmpty()
        require(!enabled || cleanUrl.isNotBlank()) { "Enter a provider URL before enabling it." }
        prefs.edit()
            .putString("${KeyFundingProviderPrefix}_$id", cleanUrl)
            .putBoolean("${KeyFundingProviderPrefix}_${id}_enabled", enabled)
            .apply()
        return fundingProviders()
    }

    private fun persistDomainGuardRules(rules: List<DomainGuardRule>) {
        prefs.edit()
            .putString(KeyDomainGuardRules, JSONArray().apply { rules.forEach { put(it.toJson()) } }.toString())
            .apply()
    }

    private fun defaultDomainGuardRules(): List<DomainGuardRule> = listOf(
        DomainGuardRule("octra.id", trusted = true, note = "Default Octra Identity dApp"),
        DomainGuardRule("octrascan.io", trusted = true, note = "Default Octra explorer"),
        DomainGuardRule("etherscan.io", trusted = true, note = "Ethereum explorer"),
    )

    private fun fundingProvider(id: String, name: String, description: String): FundingProvider {
        val url = prefs.getString("${KeyFundingProviderPrefix}_$id", "")?.trim().orEmpty()
        val enabled = prefs.getBoolean("${KeyFundingProviderPrefix}_${id}_enabled", false)
        return FundingProvider(id, name, description, url, enabled)
    }

    companion object {
        private const val KeyDomainGuardRules = "domain_guard_rules"
        private const val KeyFundingProviderPrefix = "funding_provider"
        private const val FundingProviderBuyId = "buy"
        private const val FundingProviderSellId = "sell"
    }
}
