package fr.eink.horlogemeteo

import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

/** Écran principal : heure du lieu (HH:mm:ss), date, météo et liste des alarmes. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var timeView: TextView
    private lateinit var dateView: TextView
    private lateinit var deviceTimeView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var lastDate = ""

    private val ticker = object : Runnable {
        override fun run() {
            tick()
            // Recalé sur le début de chaque seconde.
            val now = System.currentTimeMillis()
            handler.postAtTime(this, SystemClock.uptimeMillis() + (1000 - now % 1000))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        timeView = findViewById(R.id.main_time)
        dateView = findViewById(R.id.main_date)
        deviceTimeView = findViewById(R.id.main_device_time)

        findViewById<Button>(R.id.add_alarm).setOnClickListener {
            startActivity(Intent(this, AlarmEditActivity::class.java))
        }
        findViewById<Button>(R.id.open_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        findViewById<View>(R.id.main_weather).setOnClickListener {
            if (prefs.canFetch) {
                prefs.refreshingSince = System.currentTimeMillis()
                WidgetRenderer.updateAll(this)
                WeatherWorker.refreshNow(this)
                renderWeather()
                handler.postDelayed({ renderWeather() }, 5_000)
                handler.postDelayed({ renderWeather() }, 15_000)
            } else {
                startActivity(Intent(this, SettingsActivity::class.java))
            }
        }
        findViewById<View>(R.id.exact_alarm_warning).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")),
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        lastDate = ""
        findViewById<TextView>(R.id.main_place).text =
            prefs.placeName.ifBlank { "Heure locale" }
        // Suivi de position : à l'ouverture de l'appli, on relit la position si la météo date un peu.
        val w = prefs.weather
        if (prefs.followLocation && (w == null || System.currentTimeMillis() - w.fetchedAt > 15 * 60_000L)) {
            WeatherWorker.refreshNow(this)
            handler.postDelayed({ onResumeRefreshDone() }, 20_000)
        }
        renderWeather()
        renderAlarms()
        handler.removeCallbacks(ticker)
        ticker.run()
    }

    override fun onPause() {
        handler.removeCallbacksAndMessages(null)
        super.onPause()
    }

    private fun onResumeRefreshDone() {
        findViewById<TextView>(R.id.main_place).text = prefs.placeName.ifBlank { "Heure locale" }
        lastDate = ""
        renderWeather()
    }

    private fun tick() {
        val tz = prefs.timeZone
        val locale = Locale.getDefault()
        val now = Date()
        timeView.text = SimpleDateFormat("HH:mm:ss", locale).apply { timeZone = tz }.format(now)

        // La date et l'heure de l'appareil ne changent qu'à la minute : on évite de redessiner (e-ink).
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
            if (deviceTimeView.text != txt) deviceTimeView.text = txt
            deviceTimeView.visibility = View.VISIBLE
        } else {
            deviceTimeView.visibility = View.GONE
        }
    }

    private fun renderWeather() {
        val w = prefs.weather
        val icon = findViewById<ImageView>(R.id.main_weather_icon)
        val l1 = findViewById<TextView>(R.id.main_weather_line1)
        val l2 = findViewById<TextView>(R.id.main_weather_line2)
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

    private fun renderAlarms() {
        val list = findViewById<LinearLayout>(R.id.alarm_list)
        list.removeAllViews()
        val alarms = AlarmStore(this).all()
        alarms.forEach { alarm ->
            val row = layoutInflater.inflate(R.layout.item_alarm, list, false)
            row.findViewById<TextView>(R.id.item_time).text = alarm.timeText
            row.findViewById<TextView>(R.id.item_days).text =
                listOf(alarm.daysText, alarm.label).filter { it.isNotBlank() }.joinToString(" · ")
            row.findViewById<TextView>(R.id.item_sound).text =
                "♪ " + alarm.soundName.ifBlank { if (alarm.soundUri == null) "Son par défaut" else "Son personnalisé" }
            val sw = row.findViewById<Switch>(R.id.item_switch)
            sw.isChecked = alarm.enabled
            sw.setOnCheckedChangeListener { _, checked ->
                val updated = alarm.copy(enabled = checked)
                AlarmStore(this).save(updated)
                AlarmScheduler.schedule(this, updated)
                renderNextAlarm()
            }
            row.setOnClickListener {
                startActivity(Intent(this, AlarmEditActivity::class.java).putExtra(AlarmEditActivity.EXTRA_ID, alarm.id))
            }
            list.addView(row)
        }
        renderNextAlarm()

        val am = getSystemService(AlarmManager::class.java)
        val missingExact = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()
        findViewById<View>(R.id.exact_alarm_warning).visibility =
            if (missingExact && alarms.any { it.enabled }) View.VISIBLE else View.GONE
    }

    private fun renderNextAlarm() {
        val next = AlarmScheduler.nextAlarm(this)
        val view = findViewById<TextView>(R.id.next_alarm)
        view.text = when {
            AlarmStore(this).all().isEmpty() -> "Aucune alarme. Touchez « + Ajouter »."
            next == null -> "Aucune alarme active."
            else -> {
                val fmt = SimpleDateFormat("EEEE HH:mm", Locale.getDefault())
                "Prochaine : ${fmt.format(Date(next.second))} (dans ${AlarmEditActivity.untilText(next.second)})"
            }
        }
    }
}
