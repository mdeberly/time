package fr.eink.horlogemeteo

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Construit et pousse le contenu de tous les widgets. */
object WidgetRenderer {

    fun updateAll(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, ClockWeatherWidget::class.java))
        if (ids.isNotEmpty()) mgr.updateAppWidget(ids, build(context))
    }

    fun build(context: Context): RemoteViews {
        val prefs = Prefs(context)
        val v = RemoteViews(context.packageName, R.layout.widget_clock_weather)

        // Date : « Lundi 16 novembre 2026 »
        val locale = Locale.getDefault()
        val date = SimpleDateFormat("EEEE d MMMM yyyy", locale).format(Date())
        v.setTextViewText(R.id.date, date.replaceFirstChar { it.titlecase(locale) })

        // Météo
        val w = prefs.weather
        if (w != null) {
            v.setImageViewResource(R.id.weather_icon, WeatherIcons.forCode(w.code, w.isDay))
            v.setTextViewText(R.id.temp, deg(w.temperature))
            v.setTextViewText(R.id.feels, "Ressenti ${deg(w.feelsLike)}")
            v.setTextViewText(R.id.minmax, "↓${deg(w.min)}  ↑${deg(w.max)}")
        } else {
            v.setImageViewResource(R.id.weather_icon, R.drawable.ic_w_unknown)
            v.setTextViewText(R.id.temp, "--°")
            v.setTextViewText(R.id.feels, "Ressenti --°")
            v.setTextViewText(R.id.minmax, "↓--°  ↑--°")
        }
        v.setTextViewText(
            R.id.place,
            prefs.placeName.ifBlank { if (prefs.hasLocation) "—" else "Choisir un lieu" },
        )

        val hhmm = SimpleDateFormat("HH:mm", locale)
        val status = when {
            prefs.isRefreshing -> "Actualisation…"
            !prefs.hasLocation -> "Toucher : réglages"
            w == null && prefs.lastAttemptFailed -> "Échec – toucher"
            w == null -> "Toucher pour màj"
            prefs.lastAttemptFailed -> "Échec · ${hhmm.format(Date(w.fetchedAt))}"
            else -> "Màj ${hhmm.format(Date(w.fetchedAt))}"
        }
        v.setTextViewText(R.id.updated, status)

        // Actions : heure/date → application horloge ; météo → actualisation manuelle
        val settings = PendingIntent.getActivity(
            context, 1,
            Intent(context, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val clockApp = PendingIntent.getActivity(
            context, 4,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        v.setOnClickPendingIntent(R.id.clock_area, clockApp)
        v.setOnClickPendingIntent(
            R.id.weather_area,
            if (prefs.hasLocation) ClockWeatherWidget.refreshIntent(context) else settings,
        )
        return v
    }

    private fun deg(t: Double): String = "${t.roundToInt()}°"
}
