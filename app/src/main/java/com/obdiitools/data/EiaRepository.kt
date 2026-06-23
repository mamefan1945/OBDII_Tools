package com.obdiitools.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EiaRepository @Inject constructor() {

    suspend fun fetchRegularGasolinePriceUsdPerGallon(apiKey: String): Float? = withContext(Dispatchers.IO) {
        try {
            val url = URL(
                "https://api.eia.gov/v2/petroleum/pri/gnd/data/" +
                "?api_key=$apiKey" +
                "&frequency=weekly" +
                "&data[0]=value" +
                "&facets[product][]=EPMR" +
                "&facets[duoarea][]=NUS" +
                "&sort[0][column]=period" +
                "&sort[0][direction]=desc" +
                "&length=1"
            )
            val conn = url.openConnection() as HttpURLConnection
            conn.connectTimeout = 6000
            conn.readTimeout    = 6000
            if (conn.responseCode != 200) return@withContext null
            val body = conn.inputStream.bufferedReader().readText()
            val data = JSONObject(body).getJSONObject("response").getJSONArray("data")
            if (data.length() == 0) return@withContext null
            data.getJSONObject(0).getString("value").toFloatOrNull()
        } catch (_: Exception) {
            null
        }
    }
}
