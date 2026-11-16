package fr.eink.horlogemeteo

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import java.util.Calendar

/**
 * Widget 4x2. L'heure est gérée par un TextClock (mis à jour par le lanceur chaque minute) ;
 * la date est redessinée à minuit via une alarme, et la météo par WorkManager.
 */
class ClockWeatherWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        mgr.updateAppWidget(ids, WidgetRenderer.build(context))
        WeatherWorker.schedulePeriodic(context)
        scheduleMidnight(context)
        // Météo trop ancienne (ex. après un redémarrage) : on relance.
        val w = Prefs(context).weather
        val minutes = Prefs(context).refreshMinutes
        if (minutes > 0 && Prefs(context).hasLocation &&
            (w == null || System.currentTimeMillis() - w.fetchedAt > minutes * 60_000L * 2)
        ) {
            WeatherWorker.refreshNow(context)
        }
    }

    override fun onDisabled(context: Context) {
        WeatherWorker.cancelAll(context)
        context.getSystemService(AlarmManager::class.java).cancel(midnightIntent(context))
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_REFRESH -> {
                val prefs = Prefs(context)
                prefs.refreshingSince = System.currentTimeMillis()
                WidgetRenderer.updateAll(context) // affiche « Actualisation… »
                WeatherWorker.refreshNow(context)
            }
            ACTION_MIDNIGHT,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_LOCALE_CHANGED -> {
                WidgetRenderer.updateAll(context)
                scheduleMidnight(context)
            }
            else -> super.onReceive(context, intent)
        }
    }

    companion object {
        const val ACTION_REFRESH = "fr.eink.horlogemeteo.REFRESH"
        private const val ACTION_MIDNIGHT = "fr.eink.horlogemeteo.MIDNIGHT"

        fun refreshIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 2,
            Intent(context, ClockWeatherWidget::class.java).setAction(ACTION_REFRESH),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun midnightIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
            context, 3,
            Intent(context, ClockWeatherWidget::class.java).setAction(ACTION_MIDNIGHT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        /** Alarme juste après minuit pour changer la date affichée. */
        fun scheduleMidnight(context: Context) {
            val next = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, 1)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 5)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val am = context.getSystemService(AlarmManager::class.java)
            // Fenêtre de 2 minutes : pas besoin de la permission d'alarme exacte.
            am.setWindow(AlarmManager.RTC_WAKEUP, next, 2 * 60_000L, midnightIntent(context))
        }
    }
}
