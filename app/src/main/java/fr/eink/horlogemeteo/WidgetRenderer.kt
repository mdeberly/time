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

    fun updateAll(context: Context, wifiOverride: Boolean? = null, btOverride: Boolean? = null) {
        val mgr = AppWidgetManager.getInstance(context)
        val ids = mgr.getAppWidgetIds(ComponentName(context, ClockWeatherWidget::class.java))
        if (ids.isNotEmpty()) mgr.updateAppWidget(ids, build(context, wifiOverride, btOverride))
    }

    fun build(context: Context, wifiOverride: Boolean? = null, btOverride: Boolean? = null): RemoteViews {
        val prefs = Prefs(context)
        val v = RemoteViews(context.packageName, R.layout.widget_clock_weather)

        // Date : « Lundi 16 novembre 2026 »
        val locale = Locale.getDefault()
        val date = SimpleDateFormat("EEEE d MMMM yyyy", locale).format(Date())
        v.setTextViewText(R.id.date, date.replaceFirstChar { it.titlecase(locale) })

        // Profil sonore
        val mode = RingerMode.current(context)
        v.setTextViewText(R.id.ringer_label, RingerMode.label(mode))
        v.setImageViewResource(R.id.ringer_button, RingerMode.icon(mode))
        v.setInt(
            R.id.ringer_button, "setBackgroundResource",
            if (RingerMode.isActive(mode)) R.drawable.widget_button_on else R.drawable.widget_button,
        )
        v.setOnClickPendingIntent(
            R.id.ringer_button,
            if (RingerMode.hasAccess(context)) {
                ClockWeatherWidget.ringerIntent(context)
            } else {
                // Accès « Ne pas déranger » manquant : ouvre l'écran qui le demande.
                PendingIntent.getActivity(
                    context, 5,
                    Intent(context, SettingsActivity::class.java)
                        .putExtra(SettingsActivity.EXTRA_ASK_DND, true)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            },
        )

        // Wi-Fi / Bluetooth
        val wifiOn = wifiOverride ?: Radios.isWifiOn(context)
        v.setImageViewResource(R.id.wifi_button, if (wifiOn) R.drawable.ic_wifi_on else R.drawable.ic_wifi_off)
        v.setInt(R.id.wifi_button, "setBackgroundResource", if (wifiOn) R.drawable.widget_button_on else R.drawable.widget_button)
        v.setOnClickPendingIntent(R.id.wifi_button, ClockWeatherWidget.wifiIntent(context))

        val btOn = btOverride ?: Radios.isBluetoothOn(context)
        v.setImageViewResource(R.id.bt_button, if (btOn) R.drawable.ic_bt_on else R.drawable.ic_bt_off)
        v.setInt(R.id.bt_button, "setBackgroundResource", if (btOn) R.drawable.widget_button_on else R.drawable.widget_button)
        v.setOnClickPendingIntent(R.id.bt_button, ClockWeatherWidget.bluetoothIntent(context))
        v.setViewVisibility(R.id.bt_button, if (Radios.hasBluetooth(context)) android.view.View.VISIBLE else android.view.View.GONE)

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
            !prefs.canFetch -> "Toucher : réglages"
            !prefs.hasLocation -> "Recherche position…"
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
            if (prefs.canFetch) ClockWeatherWidget.refreshIntent(context) else settings,
        )
        return v
    }

    private fun deg(t: Double): String = "${t.roundToInt()}°"
}
