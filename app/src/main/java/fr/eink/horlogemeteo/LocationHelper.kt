package fr.eink.horlogemeteo

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.coroutines.resume

/** Position de l'appareil (GPS ou réseau) et nom de la ville correspondante. */
object LocationHelper {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Nécessaire pour suivre la position quand l'application n'est pas à l'écran (widget). */
    fun hasBackgroundPermission(context: Context): Boolean =
        hasPermission(context) && (
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
            )

    /**
     * Position actuelle : une position récente connue (< [maxAgeMs]) sinon une nouvelle mesure
     * (réseau en priorité, puis GPS), avec un délai maximal de [timeoutMs].
     */
    @Suppress("MissingPermission")
    suspend fun current(context: Context, maxAgeMs: Long = 10 * 60_000L, timeoutMs: Long = 30_000L): Location? {
        if (!hasPermission(context)) return null
        val lm = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = runCatching { lm.getProviders(true) }.getOrDefault(emptyList())
        val known = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (known != null && System.currentTimeMillis() - known.time < maxAgeMs) return known

        val order = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.FUSED_PROVIDER)
        val provider = order.firstOrNull { it in providers } ?: return known
        val fresh = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        lm.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }
                    @Deprecated("API < 29")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                try {
                    lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        }
        return fresh ?: known
    }

    /** Nom de la ville : géocodeur Android, sinon service en ligne gratuit (BigDataCloud). */
    suspend fun placeName(context: Context, lat: Double, lon: Double): String = withContext(Dispatchers.IO) {
        geocoder(context, lat, lon) ?: online(lat, lon)
            ?: String.format(Locale.getDefault(), "%.2f, %.2f", lat, lon)
    }

    @Suppress("DEPRECATION")
    private fun geocoder(context: Context, lat: Double, lon: Double): String? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            val a = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
            (a?.locality ?: a?.subAdminArea ?: a?.adminArea)?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    private fun online(lat: Double, lon: Double): String? = runCatching {
        val lang = Locale.getDefault().language.ifEmpty { "fr" }
        val url = String.format(
            Locale.US,
            "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=%.5f&longitude=%.5f&localityLanguage=%s",
            lat, lon, lang,
        )
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 10_000
        try {
            val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
            listOf("city", "locality", "principalSubdivision")
                .map { json.optString(it) }
                .firstOrNull { it.isNotBlank() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    /** Distance en mètres entre deux points. */
    fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val r = FloatArray(1)
        Location.distanceBetween(lat1, lon1, lat2, lon2, r)
        return r[0]
    }
}
