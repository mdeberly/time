package fr.eink.horlogemeteo

import android.Manifest
import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
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
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Écran de réglages, servant aussi d'écran de configuration du widget. */
class SettingsActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: Prefs
    private lateinit var currentPlace: TextView
    private lateinit var status: TextView
    private lateinit var results: LinearLayout
    private lateinit var searchField: EditText
    private lateinit var followBox: android.widget.CheckBox
    private lateinit var followHint: TextView

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
        followBox = findViewById(R.id.follow_location)
        followHint = findViewById(R.id.follow_hint)

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
        renderState()
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
        currentPlace.text = when {
            prefs.followLocation && prefs.hasLocation ->
                "Actuel : ${prefs.placeName.ifBlank { "position" }} (suit vos déplacements)"
            prefs.followLocation -> "Suit vos déplacements (position en attente)"
            prefs.hasLocation -> "Actuel : ${prefs.placeName.ifBlank { "position enregistrée" }}"
            else -> "Aucun lieu choisi : recherchez une ville ou utilisez votre position."
        }
        followBox.setOnCheckedChangeListener(null)
        followBox.isChecked = prefs.followLocation
        followBox.setOnCheckedChangeListener { _, checked -> setFollow(checked) }
        followHint.text = when {
            !prefs.followLocation -> "Le lieu et l'heure locale se mettent à jour à chaque actualisation de la météo."
            !LocationHelper.hasBackgroundPermission(this) ->
                "⚠ Localisation « Toujours autoriser » non accordée : le suivi ne se fera que lorsque l'appli est ouverte. Touchez la case pour la demander."
            else -> "Le lieu se met à jour à chaque actualisation de la météo (localisation approximative, économe en batterie)."
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
        prefs.followLocation = false // une ville choisie à la main remplace le suivi
        prefs.setLocation(lat, lon, name, tz)
        renderState()
        WidgetRenderer.updateAll(this)
        refreshNow()
    }

    // ---------- Position GPS / réseau ----------

    private val locationPermissions = arrayOf(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
    )

    /** Position mesurée une fois (bouton « Utiliser ma position actuelle »). */
    private fun useMyLocation() {
        if (!LocationHelper.hasPermission(this)) {
            requestPermissions(locationPermissions, REQ_LOCATION)
            return
        }
        status.text = "Localisation en cours…"
        scope.launch {
            val loc = LocationHelper.current(this@SettingsActivity, maxAgeMs = 30 * 60_000L)
            if (loc == null) {
                status.text = "Position introuvable. Activez la localisation ou recherchez une ville."
                return@launch
            }
            val name = LocationHelper.placeName(this@SettingsActivity, loc.latitude, loc.longitude)
            applyLocation(loc.latitude, loc.longitude, name, null)
        }
    }

    /** Case « Suivre ma position » : demande les autorisations nécessaires, étape par étape. */
    private fun setFollow(enabled: Boolean) {
        if (!enabled) {
            prefs.followLocation = false
            renderState()
            return
        }
        when {
            !LocationHelper.hasPermission(this) -> {
                requestPermissions(locationPermissions, REQ_FOLLOW)
                return
            }
            !LocationHelper.hasBackgroundPermission(this) -> {
                status.text = "Pour suivre vos déplacements même quand l'appli est fermée, choisissez " +
                    "« Toujours autoriser » dans l'écran qui s'ouvre, puis revenez."
                requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ_BACKGROUND)
            }
        }
        prefs.followLocation = true
        renderState()
        status.text = "Recherche de votre position…"
        refreshNow()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        val granted = grantResults.any { it == PackageManager.PERMISSION_GRANTED }
        when (requestCode) {
            REQ_LOCATION ->
                if (granted) useMyLocation() else status.text = "Permission refusée : recherchez une ville à la place."
            REQ_FOLLOW ->
                if (granted) setFollow(true) else {
                    followBox.isChecked = false
                    status.text = "Permission refusée : impossible de suivre votre position."
                }
            REQ_BACKGROUND -> renderState()
        }
    }

    // ---------- Divers ----------

    private fun refreshNow() {
        if (!prefs.canFetch) {
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
        private const val REQ_FOLLOW = 43
        private const val REQ_BACKGROUND = 44
    }
}
