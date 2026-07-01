package com.poctra.wallet.data

import android.content.Context
import org.json.JSONArray

class AssetRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("poctra_assets", Context.MODE_PRIVATE)

    fun savedTokenAddresses(network: String): List<String> {
        val raw = prefs.getString(key(network), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val address = array.optString(index)
                    if (address.isNotBlank()) add(address)
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveTokenAddress(network: String, address: String) {
        val next = (savedTokenAddresses(network) + address).distinct()
        prefs.edit().putString(key(network), JSONArray(next).toString()).apply()
    }

    fun removeTokenAddress(network: String, address: String) {
        val next = savedTokenAddresses(network).filterNot { it == address }
        prefs.edit().putString(key(network), JSONArray(next).toString()).apply()
    }

    private fun key(network: String): String = "octra_tokens_$network"
}
