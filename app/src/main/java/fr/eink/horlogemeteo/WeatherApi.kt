package fr.eink.horlogemeteo

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

data class Weather(
    val temperature: Double,
    val feelsLike: Double,
    val code: Int,
    val isDay: Boolean,
    val min: Double,
    val max: Double,
    val fetchedAt: Long,
)

data class Place(val name: String, val detail: String, val latitude: Double, val longitude: Double)

/** Accès à Open-Meteo (gratuit, sans clé d'API). Appels bloquants : à lancer hors du thread UI. */
object WeatherApi {

    fun fetch(lat: Double, lon: Double): Weather {
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=${fmt(lat)}&longitude=${fmt(lon)}" +
            "&current=temperature_2m,apparent_temperature,weather_code,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min" +
            "&timezone=auto&forecast_days=1"
        val json = JSONObject(get(url))
        val current = json.getJSONObject("current")
        val daily = json.getJSONObject("daily")
        return Weather(
            temperature = current.getDouble("temperature_2m"),
            feelsLike = current.getDouble("apparent_temperature"),
            code = current.getInt("weather_code"),
            isDay = current.optInt("is_day", 1) == 1,
            min = daily.getJSONArray("temperature_2m_min").getDouble(0),
            max = daily.getJSONArray("temperature_2m_max").getDouble(0),
            fetchedAt = System.currentTimeMillis(),
        )
    }

    fun searchPlaces(query: String): List<Place> {
        val lang = Locale.getDefault().language.ifEmpty { "fr" }
        val url = "https://geocoding-api.open-meteo.com/v1/search?count=10&format=json" +
            "&language=$lang&name=" + URLEncoder.encode(query.trim(), "UTF-8")
        val results = JSONObject(get(url)).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { i ->
            val r = results.getJSONObject(i)
            val detail = listOfNotNull(
                r.optString("admin1").takeIf { it.isNotBlank() },
                r.optString("country").takeIf { it.isNotBlank() },
            ).joinToString(", ")
            Place(r.getString("name"), detail, r.getDouble("latitude"), r.getDouble("longitude"))
        }
    }

    private fun fmt(d: Double) = String.format(Locale.US, "%.4f", d)

    private fun get(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("Accept", "application/json")
        try {
            val code = conn.responseCode
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}

/** Codes météo WMO → icône noir et blanc. */
object WeatherIcons {
    fun forCode(code: Int, isDay: Boolean): Int = when (code) {
        0, 1 -> if (isDay) R.drawable.ic_w_clear_day else R.drawable.ic_w_clear_night
        2 -> if (isDay) R.drawable.ic_w_partly_day else R.drawable.ic_w_partly_night
        3 -> R.drawable.ic_w_cloudy
        45, 48 -> R.drawable.ic_w_fog
        51, 53, 55, 56, 57 -> R.drawable.ic_w_drizzle
        61, 63, 65, 66, 67, 80, 81, 82 -> R.drawable.ic_w_rain
        71, 73, 75, 77, 85, 86 -> R.drawable.ic_w_snow
        95, 96, 99 -> R.drawable.ic_w_thunder
        else -> R.drawable.ic_w_unknown
    }
}
