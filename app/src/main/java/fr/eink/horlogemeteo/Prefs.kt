package fr.eink.horlogemeteo

import android.content.Context
import android.content.SharedPreferences

/** Réglages et dernière météo connue, stockés dans les SharedPreferences. */
class Prefs(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("horloge_meteo", Context.MODE_PRIVATE)

    // --- Lieu ---
    val hasLocation: Boolean get() = sp.contains(K_LAT) && sp.contains(K_LON)
    val latitude: Double get() = java.lang.Double.longBitsToDouble(sp.getLong(K_LAT, 0))
    val longitude: Double get() = java.lang.Double.longBitsToDouble(sp.getLong(K_LON, 0))
    var placeName: String
        get() = sp.getString(K_PLACE, "") ?: ""
        set(v) = sp.edit().putString(K_PLACE, v).apply()

    /** Fuseau horaire du lieu choisi (null = celui de l'appareil). */
    var timeZoneId: String?
        get() = sp.getString(K_TZ, null)
        set(v) = sp.edit().putString(K_TZ, v).apply()

    val timeZone: java.util.TimeZone
        get() = timeZoneId?.let { java.util.TimeZone.getTimeZone(it) } ?: java.util.TimeZone.getDefault()

    fun setLocation(lat: Double, lon: Double, name: String, tz: String?) {
        sp.edit()
            .putString(K_TZ, tz)
            .putLong(K_LAT, java.lang.Double.doubleToRawLongBits(lat))
            .putLong(K_LON, java.lang.Double.doubleToRawLongBits(lon))
            .putString(K_PLACE, name)
            .remove(K_W_TIME) // météo de l'ancien lieu invalide
            .apply()
    }

    // --- Fréquence d'actualisation automatique (minutes, 0 = manuelle uniquement) ---
    var refreshMinutes: Int
        get() = sp.getInt(K_REFRESH, 60)
        set(v) = sp.edit().putInt(K_REFRESH, v).apply()

    // --- Dernière météo ---
    val weather: Weather?
        get() {
            if (!sp.contains(K_W_TIME)) return null
            return Weather(
                temperature = sp.getFloat(K_W_TEMP, 0f).toDouble(),
                feelsLike = sp.getFloat(K_W_FEELS, 0f).toDouble(),
                code = sp.getInt(K_W_CODE, -1),
                isDay = sp.getBoolean(K_W_DAY, true),
                min = sp.getFloat(K_W_MIN, 0f).toDouble(),
                max = sp.getFloat(K_W_MAX, 0f).toDouble(),
                fetchedAt = sp.getLong(K_W_TIME, 0),
            )
        }

    fun saveWeather(w: Weather) {
        val e = sp.edit()
        if (w.timeZone != null) e.putString(K_TZ, w.timeZone)
        e
            .putFloat(K_W_TEMP, w.temperature.toFloat())
            .putFloat(K_W_FEELS, w.feelsLike.toFloat())
            .putInt(K_W_CODE, w.code)
            .putBoolean(K_W_DAY, w.isDay)
            .putFloat(K_W_MIN, w.min.toFloat())
            .putFloat(K_W_MAX, w.max.toFloat())
            .putLong(K_W_TIME, w.fetchedAt)
            .putBoolean(K_LAST_FAILED, false)
            .apply()
    }

    var lastAttemptFailed: Boolean
        get() = sp.getBoolean(K_LAST_FAILED, false)
        set(v) = sp.edit().putBoolean(K_LAST_FAILED, v).apply()

    /** Horodatage du début d'une actualisation en cours (0 = aucune). */
    var refreshingSince: Long
        get() = sp.getLong(K_REFRESHING, 0)
        set(v) {
            sp.edit().putLong(K_REFRESHING, v).commit()
        }

    val isRefreshing: Boolean
        get() {
            val since = refreshingSince
            return since > 0 && System.currentTimeMillis() - since < 90_000
        }

    private companion object {
        const val K_LAT = "lat"
        const val K_LON = "lon"
        const val K_PLACE = "place"
        const val K_TZ = "timezone"
        const val K_REFRESH = "refresh_minutes"
        const val K_W_TEMP = "w_temp"
        const val K_W_FEELS = "w_feels"
        const val K_W_CODE = "w_code"
        const val K_W_DAY = "w_day"
        const val K_W_MIN = "w_min"
        const val K_W_MAX = "w_max"
        const val K_W_TIME = "w_time"
        const val K_LAST_FAILED = "last_failed"
        const val K_REFRESHING = "refreshing_since"
    }
}
