package fr.eink.horlogemeteo

import android.app.AlertDialog
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/** Onglet Horloge : heure du lieu (HH:mm:ss), date, météo, et une 2e heure au choix. */
class ClockPage(private val activity: MainActivity, private val root: View, private val scope: CoroutineScope) : Page {

    override val title = "Horloge"
    override val hasAdd = true

    private val prefs = Prefs(activity)
    private val handler = Handler(Looper.getMainLooper())
    private val timeView: TextView = root.findViewById(R.id.main_time)
    private val dateView: TextView = root.findViewById(R.id.main_date)
    private val deviceTimeView: TextView = root.findViewById(R.id.main_device_time)
    private val secondCard: View = root.findViewById(R.id.second_clock)
    private val secondTime: TextView = root.findViewById(R.id.second_time)
    private val secondInfo: TextView = root.findViewById(R.id.second_info)
    private var lastDate = ""
    private var lastSecondInfo = ""

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            val now = System.currentTimeMillis()
            handler.postAtTime(this, SystemClock.uptimeMillis() + (1000 - now % 1000))
        }
    }

    init {
        root.findViewById<View>(R.id.main_weather).setOnClickListener {
            if (prefs.canFetch) {
                prefs.refreshingSince = System.currentTimeMillis()
                WidgetRenderer.updateAll(activity)
                WeatherWorker.refreshNow(activity)
                renderWeather()
                handler.postDelayed({ renderWeather() }, 5_000)
                handler.postDelayed({ renderAll() }, 20_000)
            } else {
                activity.startActivity(Intent(activity, SettingsActivity::class.java))
            }
        }
        root.findViewById<Button>(R.id.second_add).setOnClickListener { onAdd() }
        secondCard.setOnClickListener { onAdd() }
        root.findViewById<View>(R.id.second_remove).setOnClickListener {
            prefs.setSecondClock(null, null)
            renderSecond()
        }
    }

    override fun onShow() {
        // Suivi de position : à l'ouverture, on relit la position si la météo date un peu.
        val w = prefs.weather
        if (prefs.followLocation && (w == null || System.currentTimeMillis() - w.fetchedAt > 15 * 60_000L)) {
            WeatherWorker.refreshNow(activity)
            handler.postDelayed({ renderAll() }, 20_000)
        }
        renderAll()
        handler.removeCallbacks(ticker)
        ticker.run()
    }

    override fun onHide() {
        handler.removeCallbacksAndMessages(null)
    }

    /** « + » : choisir (ou changer) le lieu de la 2e heure. */
    override fun onAdd() {
        val field = EditText(activity).apply {
            hint = "Ville (ex. Tokyo, New York)"
            setSingleLine()
            setTextColor(0xFF000000.toInt())
        }
        val pad = (20 * activity.resources.displayMetrics.density).toInt()
        val box = android.widget.FrameLayout(activity).apply { setPadding(pad, pad / 2, pad, 0); addView(field) }
        AlertDialog.Builder(activity, R.style.InkDialog)
            .setTitle("2e heure : choisir un lieu")
            .setView(box)
            .setPositiveButton("Rechercher") { _, _ -> searchSecond(field.text.toString()) }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun searchSecond(query: String) {
        if (query.isBlank()) return
        scope.launch {
            val found = try {
                withContext(Dispatchers.IO) { WeatherApi.searchPlaces(query) }
            } catch (e: Exception) {
                Toast.makeText(activity, "Recherche impossible (connexion ?)", Toast.LENGTH_LONG).show()
                return@launch
            }
            val places = found.filter { it.timeZone != null }
            if (places.isEmpty()) {
                Toast.makeText(activity, "Aucun résultat pour « $query »", Toast.LENGTH_LONG).show()
                return@launch
            }
            AlertDialog.Builder(activity, R.style.InkDialog)
                .setTitle("Choisissez")
                .setItems(places.map { if (it.detail.isBlank()) it.name else "${it.name} — ${it.detail}" }.toTypedArray()) { _, i ->
                    prefs.setSecondClock(places[i].name, places[i].timeZone)
                    lastSecondInfo = ""
                    renderSecond()
                }
                .show()
        }
    }

    private fun renderAll() {
        root.findViewById<TextView>(R.id.main_place).text = prefs.placeName.ifBlank { "Heure locale" }
        lastDate = ""
        renderWeather()
        renderSecond()
        tick()
    }

    private fun renderSecond() {
        val name = prefs.secondPlace
        val tz = prefs.secondTimeZone
        val show = name != null && tz != null
        secondCard.visibility = if (show) View.VISIBLE else View.GONE
        root.findViewById<View>(R.id.second_add).visibility = if (show) View.GONE else View.VISIBLE
        if (show) root.findViewById<TextView>(R.id.second_place).text = name
        lastSecondInfo = ""
    }

    private fun tick() {
        val tz = prefs.timeZone
        val locale = Locale.getDefault()
        val now = Date()
        timeView.text = SimpleDateFormat("HH:mm:ss", locale).apply { timeZone = tz }.format(now)

        // Date et lignes secondaires : redessinées seulement quand elles changent (e-ink).
        val date = SimpleDateFormat("EEEE d MMMM yyyy", locale).apply { timeZone = tz }.format(now)
            .replaceFirstChar { it.titlecase(locale) }
        if (date != lastDate) {
            dateView.text = date
            lastDate = date
        }
        val device = TimeZone.getDefault()
        if (device.getOffset(now.time) != tz.getOffset(now.time)) {
            val txt = "Heure de l'appareil : " +
                SimpleDateFormat("HH:mm", locale).apply { timeZone = device }.format(now)
            if (deviceTimeView.text.toString() != txt) deviceTimeView.text = txt
            deviceTimeView.visibility = View.VISIBLE
        } else {
            deviceTimeView.visibility = View.GONE
        }

        val tz2 = prefs.secondTimeZone
        if (tz2 != null && secondCard.visibility == View.VISIBLE) {
            secondTime.text = SimpleDateFormat("HH:mm:ss", locale).apply { timeZone = tz2 }.format(now)
            val diffMin = (tz2.getOffset(now.time) - tz.getOffset(now.time)) / 60_000
            val diff = when {
                diffMin == 0 -> "même heure"
                diffMin % 60 == 0 -> "${if (diffMin > 0) "+" else "−"}${abs(diffMin) / 60} h"
                else -> "${if (diffMin > 0) "+" else "−"}${abs(diffMin) / 60} h ${abs(diffMin) % 60}"
            }
            val info = SimpleDateFormat("EEEE d MMMM", locale).apply { timeZone = tz2 }.format(now)
                .replaceFirstChar { it.titlecase(locale) } + "  ·  " + diff
            if (info != lastSecondInfo) {
                secondInfo.text = info
                lastSecondInfo = info
            }
        }
    }

    private fun renderWeather() {
        val w = prefs.weather
        val icon = root.findViewById<ImageView>(R.id.main_weather_icon)
        val l1 = root.findViewById<TextView>(R.id.main_weather_line1)
        val l2 = root.findViewById<TextView>(R.id.main_weather_line2)
        if (!prefs.canFetch) {
            icon.setImageResource(R.drawable.ic_w_unknown)
            l1.text = "Choisir un lieu"
            l2.text = "Toucher pour régler la météo"
            return
        }
        val hhmm = SimpleDateFormat("HH:mm", Locale.getDefault())
        if (w == null) {
            icon.setImageResource(R.drawable.ic_w_unknown)
            l1.text = "--°"
            l2.text = if (prefs.isRefreshing) "Actualisation…" else "Toucher pour actualiser"
            return
        }
        icon.setImageResource(WeatherIcons.forCode(w.code, w.isDay))
        l1.text = "${w.temperature.roundToInt()}°  (ressenti ${w.feelsLike.roundToInt()}°)"
        val status = when {
            prefs.isRefreshing -> "actualisation…"
            prefs.lastAttemptFailed -> "échec, màj ${hhmm.format(Date(w.fetchedAt))}"
            else -> "màj ${hhmm.format(Date(w.fetchedAt))}"
        }
        l2.text = "↓${w.min.roundToInt()}°  ↑${w.max.roundToInt()}°  ·  $status ⟳"
    }
}
