package com.poctra.wallet.data

import android.content.Context
import org.json.JSONArray

class OctraIdentityProviderRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("poctra_identity_providers", Context.MODE_PRIVATE)

    fun providers(network: String): List<OctraIdentityProvider> {
        val all = allProviders()
        return all.filter { it.network == network }
    }

    fun activeProvider(network: String): OctraIdentityProvider? {
        val list = providers(network)
        val activeId = prefs.getString(activeKey(network), null)
        return list.firstOrNull { it.id == activeId } ?: list.firstOrNull()?.also {
            prefs.edit().putString(activeKey(network), it.id).apply()
        }
    }

    fun saveProvider(provider: OctraIdentityProvider): List<OctraIdentityProvider> {
        require(provider.name.isNotBlank()) { "Provider name is required." }
        require(isOctraAddress(provider.contractAddress)) { "Enter a valid Octra contract address." }
        require(provider.network == "mainnet" || provider.network == "devnet") { "Network must be mainnet or devnet." }
        val clean = provider.copy(
            id = provider.id.ifBlank { "identity-${System.currentTimeMillis()}" },
            name = provider.name.trim().take(32),
            contractAddress = provider.contractAddress.trim(),
        )
        val next = allProviders()
            .filterNot { it.id == clean.id || (it.network == clean.network && it.contractAddress == clean.contractAddress) } + clean
        persist(next)
        if (prefs.getString(activeKey(clean.network), null).isNullOrBlank()) {
            prefs.edit().putString(activeKey(clean.network), clean.id).apply()
        }
        return providers(clean.network)
    }

    fun setActiveProvider(network: String, id: String): OctraIdentityProvider {
        val provider = providers(network).firstOrNull { it.id == id } ?: throw IllegalArgumentException("Identity provider not found.")
        prefs.edit().putString(activeKey(network), provider.id).apply()
        return provider
    }

    fun deleteProvider(network: String, id: String): List<OctraIdentityProvider> {
        val nextAll = allProviders().filterNot { it.network == network && it.id == id }
        persist(nextAll)
        val next = providers(network)
        if (prefs.getString(activeKey(network), null) == id) {
            prefs.edit().putString(activeKey(network), next.firstOrNull()?.id).apply()
        }
        return next
    }

    private fun allProviders(): List<OctraIdentityProvider> {
        val raw = prefs.getString(KeyProviders, null) ?: return listOf(defaultDevnetProvider()).also(::persist)
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val provider = migrateProvider(OctraIdentityProvider.fromJson(array.getJSONObject(index)))
                    if (provider.contractAddress.isNotBlank()) add(provider)
                }
            }.also(::persist)
        }.getOrDefault(emptyList())
    }

    private fun persist(providers: List<OctraIdentityProvider>) {
        val distinct = providers.distinctBy { "${it.network}:${it.contractAddress}" }
        prefs.edit().putString(KeyProviders, JSONArray().apply { distinct.forEach { put(it.toJson()) } }.toString()).apply()
    }

    private fun activeKey(network: String): String = "active_provider_$network"

    private fun isOctraAddress(address: String): Boolean = address.startsWith("oct") && address.length == 47

    private fun migrateProvider(provider: OctraIdentityProvider): OctraIdentityProvider =
        if (provider.network == "devnet" && provider.contractAddress in LegacyDevnetProviderContracts) {
            provider.copy(
                id = DefaultDevnetProviderId,
                name = "Octra Identity",
                contractAddress = DefaultDevnetProviderContract,
            )
        } else {
            provider
        }

    private fun defaultDevnetProvider(): OctraIdentityProvider = OctraIdentityProvider(
        id = DefaultDevnetProviderId,
        name = "Octra Identity",
        contractAddress = DefaultDevnetProviderContract,
        network = "devnet",
    )

    companion object {
        private const val KeyProviders = "providers"
        private const val DefaultDevnetProviderId = "identity-devnet-octra-id"
        private val LegacyDevnetProviderContracts = setOf("octD5RqXkTbHMCpaMaw2QnMSTpHNu7LRdgidbVDNmkDCuzb", "octAuuNU6wRkTXY7H9JXquwtgpNeCJTRhE7DmQSWSfKQ5nb", "octCL7symfEG2YMtH9KTvY8u823xJ2a7azyVzw6jVTKD5JT", "oct94s4b1cN2Tn3YVdqeayitwQBYkNBL8SMDUEdoaVqNV95", "oct7HNMuQDUNhZWgAPBs1PnFeZUfqcYoWvFCAtd7UbNMT1Z")
        private const val DefaultDevnetProviderContract = "oct8oCgpxvdEytE7tkfjyF8am3xvK28J6rJApkv3omRQnKH"
    }
}
