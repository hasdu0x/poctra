package com.poctra.wallet.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class OctPriceRepository {
    suspend fun fetchUsdPrice(): Double? = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(PriceUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8_000
                readTimeout = 8_000
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode !in 200..299) return@withContext null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body)
                .optJSONObject("octra")
                ?.optDouble("usd", 0.0)
                ?.takeIf { it > 0.0 }
        } catch (_: Exception) {
            null
        } finally {
            connection?.disconnect()
        }
    }

    companion object {
        private const val PriceUrl = "https://api.coingecko.com/api/v3/simple/price?ids=octra&vs_currencies=usd"
    }
}
