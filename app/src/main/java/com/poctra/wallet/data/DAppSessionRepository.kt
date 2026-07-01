package com.poctra.wallet.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class DAppSessionRepository(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("poctra_dapps", Context.MODE_PRIVATE)

    fun listSessions(): List<DAppSession> {
        val raw = prefs.getString(KeyConnectedDApps, null) ?: return emptyList()
        return runCatching {
            if (raw.trim().startsWith("[")) {
                val array = JSONArray(raw)
                buildList {
                    for (index in 0 until array.length()) {
                        add(DAppSession.fromJson(array.getJSONObject(index)))
                    }
                }
            } else {
                val map = JSONObject(raw)
                buildList {
                    for (key in map.keys()) {
                        add(DAppSession.fromJson(map.getJSONObject(key)))
                    }
                }
            }.distinctBy { it.url }
        }.getOrDefault(emptyList())
            .distinctBy { "${canonicalizeOrigin(it.url)}:${it.walletAddress}" }
            .sortedByDescending { it.connectedAt }
    }

    fun revoke(url: String, walletAddress: String? = null) {
        val origin = canonicalizeOrigin(url)
        val next = listSessions().filterNot { session ->
            canonicalizeOrigin(session.url) == origin &&
                (walletAddress == null || session.walletAddress == walletAddress)
        }
        persist(next)
    }

    fun persist(sessions: List<DAppSession>) {
        val map = JSONObject()
        sessions.forEach { session ->
            if (session.url.isNotBlank()) {
                map.put("${canonicalizeOrigin(session.url)}:${session.walletAddress}", session.toJson())
            }
        }
        prefs.edit().putString(KeyConnectedDApps, map.toString()).apply()
    }

    companion object {
        private const val KeyConnectedDApps = "connectedDApps"
    }
}
