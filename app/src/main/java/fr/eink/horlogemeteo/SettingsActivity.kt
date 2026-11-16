package fr.eink.horlogemeteo

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

/** Écran de réglages, servant aussi d'écran de configuration du widget. */
class SettingsActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: Prefs
    private lateinit var currentPlace: TextView
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private lateinit var searchField: EditText

    private val refreshOptions = mapOf(
        R.id.r15 to 15, R.id.r30 to 30, R.id.r60 to 60, R.id.r180 to 180, R.id.r0 to 0,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        prefs = Prefs(this)

        // Lancé comme écran de configuration : on valide d'office pour que le widget soit ajouté.
        val widgetId = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        }

        currentPlace = findViewById(R.id.current_place)
        status = findViewById(R.id.status)
        results = findViewById(R.id.results)
        searchField = findViewById(R.id.search_field)

        findViewById<Button>(R.id.search_button).setOnClickListener { search() }
        searchField.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { search(); true } else false
        }
        findViewById<Button>(R.id.gps_button).setOnClickListener { useMyLocation() }
        findViewById<Button>(R.id.refresh_button).setOnClickListener { refreshNow() }
        findViewById<Button>(R.id.done_button).setOnClickListener { finish() }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            findViewById<Button>(R.id.done_button).text = "Retour à l'horloge"
        }

        val group = findViewById<RadioGroup>(R.id.refresh_group)
        val selected = refreshOptions.entries.firstOrNull { it.value == prefs.refreshMinutes }?.key ?: R.id.r60
        group.check(selected)
        group.setOnCheckedChangeListener { _, id ->
            prefs.refreshMinutes = refreshOptions[id] ?: 60
            WeatherWorker.schedulePeriodic(this, replace = true)
        }

        findViewById<Button>(R.id.dnd_button).setOnClickListener { openDndSettings() }
        renderState()

        if (intent?.getBooleanExtra(EXTRA_ASK_DND, false) == true && !RingerMode.hasAccess(this)) {
            android.widget.Toast.makeText(
                this,
                "Autorisez « Horloge Météo » pour pouvoir changer le mode sonore depuis le widget.",
                android.widget.Toast.LENGTH_LONG,
            ).show()
            openDndSettings()
        }
    }

    override fun onResume() {
        super.onResume()
        val granted = RingerMode.hasAccess(this)
        findViewById<TextView>(R.id.dnd_status).text = if (granted) {
            "Accès accordé : la touche du widget passe de Sonnerie activée à Vibreur puis Muet."
        } else {
            "Pour changer le mode sonore depuis le widget, Android demande l'accès « Ne pas déranger »."
        }
        findViewById<Button>(R.id.dnd_button).visibility = if (granted) android.view.View.GONE else android.view.View.VISIBLE
        WidgetRenderer.updateAll(this) // la touche du widget change de comportement selon l'accès
    }

    private fun openDndSettings() {
        runCatching {
            startActivity(Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun renderState() {
        currentPlace.text = if (prefs.hasLocation) {
            "Actuel : ${prefs.placeName.ifBlank { "position enregistrée" }}"
        } else {
            "Aucun lieu choisi : recherchez une ville ou utilisez votre position."
        }
        val w = prefs.weather
        status.text = when {
            w == null -> ""
            else -> "Dernière météo : ${Math.round(w.temperature)}° à " +
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(w.fetchedAt)) +
                if (prefs.lastAttemptFailed) " (dernière tentative échouée)" else ""
        }
    }

    // ---------- Recherche de ville ----------

    private fun search() {
        val q = searchField.text.toString().trim()
        if (q.length < 2) return
        hideKeyboard()
        results.removeAllViews()
        status.text = "Recherche…"
        scope.launch {
            val places = try {
                withContext(Dispatchers.IO) { WeatherApi.searchPlaces(q) }
            } catch (e: Exception) {
                status.text = "Recherche impossible (connexion ?)"
                return@launch
            }
            status.text = if (places.isEmpty()) "Aucun résultat pour « $q »" else "Choisissez :"
            places.forEach { p -> results.addView(placeButton(p)) }
        }
    }

    private fun placeButton(p: Place): Button {
        val b = Button(this, null, 0, R.style.InkButton)
        b.layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { topMargin = (8 * resources.displayMetrics.density).toInt() }
        b.text = if (p.detail.isBlank()) p.name else "${p.name} — ${p.detail}"
        b.setOnClickListener {
            results.removeAllViews()
            searchField.setText("")
            applyLocation(p.latitude, p.longitude, p.name, p.timeZone)
        }
        return b
    }

    private fun applyLocation(lat: Double, lon: Double, name: String, tz: String?) {
        prefs.setLocation(lat, lon, name, tz)
        renderState()
        WidgetRenderer.updateAll(this)
        refreshNow()
    }

    // ---------- Position GPS / réseau ----------

    private fun useMyLocation() {
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
            return
        }
        status.text = "Localisation en cours…"
        scope.launch {
            val loc = findLocation()
            if (loc == null) {
                status.text = "Position introuvable. Activez la localisation ou recherchez une ville."
                return@launch
            }
            val name = withContext(Dispatchers.IO) { reverseGeocode(loc) }
                ?: String.format(Locale.getDefault(), "%.2f, %.2f", loc.latitude, loc.longitude)
            applyLocation(loc.latitude, loc.longitude, name, java.util.TimeZone.getDefault().id)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_LOCATION) {
            if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) useMyLocation()
            else status.text = "Permission refusée : recherchez une ville à la place."
        }
    }

    @Suppress("MissingPermission")
    private suspend fun findLocation(): Location? {
        val lm = getSystemService(LocationManager::class.java) ?: return null
        val providers = lm.getProviders(true)
        val recent = providers.mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
            .filter { System.currentTimeMillis() - it.time < 30 * 60_000L }
            .maxByOrNull { it.time }
        if (recent != null) return recent

        val provider = when {
            LocationManager.NETWORK_PROVIDER in providers -> LocationManager.NETWORK_PROVIDER
            LocationManager.GPS_PROVIDER in providers -> LocationManager.GPS_PROVIDER
            else -> return providers.firstNotNullOfOrNull {
                runCatching { lm.getLastKnownLocation(it) }.getOrNull()
            }
        }
        return withTimeoutOrNull(30_000) {
            suspendCancellableCoroutine { cont ->
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
                lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
                cont.invokeOnCancellation { lm.removeUpdates(listener) }
            }
        } ?: runCatching { lm.getLastKnownLocation(provider) }.getOrNull()
    }

    @Suppress("DEPRECATION")
    private fun reverseGeocode(loc: Location): String? {
        if (!Geocoder.isPresent()) return null
        return runCatching {
            val a = Geocoder(this, Locale.getDefault()).getFromLocation(loc.latitude, loc.longitude, 1)?.firstOrNull()
            a?.locality ?: a?.subAdminArea ?: a?.adminArea
        }.getOrNull()
    }

    // ---------- Divers ----------

    private fun refreshNow() {
        if (!prefs.hasLocation) {
            status.text = "Choisissez d'abord un lieu."
            return
        }
        prefs.refreshingSince = System.currentTimeMillis()
        WidgetRenderer.updateAll(this)
        WeatherWorker.refreshNow(this)
        status.text = "Actualisation demandée…"
        scope.launch {
            val start = System.currentTimeMillis()
            while (prefs.isRefreshing && System.currentTimeMillis() - start < 30_000) {
                kotlinx.coroutines.delay(500)
            }
            renderState()
        }
    }

    private fun hideKeyboard() {
        getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(searchField.windowToken, 0)
    }

    companion object {
        const val EXTRA_ASK_DND = "ask_dnd"
        private const val REQ_LOCATION = 42
    }
}
