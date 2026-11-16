package fr.eink.horlogemeteo

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/** Télécharge la météo puis redessine les widgets. */
class WeatherWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        if (!prefs.hasLocation) {
            prefs.refreshingSince = 0
            WidgetRenderer.updateAll(ctx)
            return Result.success()
        }
        return try {
            val w = withContext(Dispatchers.IO) { WeatherApi.fetch(prefs.latitude, prefs.longitude) }
            prefs.saveWeather(w)
            Result.success()
        } catch (e: Exception) {
            prefs.lastAttemptFailed = true
            Result.success() // pas de nouvelle tentative en rafale : économie de batterie
        } finally {
            prefs.refreshingSince = 0
            WidgetRenderer.updateAll(ctx)
        }
    }

    companion object {
        private const val PERIODIC = "weather_periodic"
        private const val NOW = "weather_now"

        private val network = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        /** Actualisation immédiate (bouton du widget ou réglages). */
        fun refreshNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<WeatherWorker>()
                .setConstraints(network)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
        }

        /** (Re)programme l'actualisation périodique selon les réglages. */
        fun schedulePeriodic(context: Context, replace: Boolean = false) {
            val wm = WorkManager.getInstance(context)
            val minutes = Prefs(context).refreshMinutes
            if (minutes <= 0) {
                wm.cancelUniqueWork(PERIODIC)
                return
            }
            val req = PeriodicWorkRequestBuilder<WeatherWorker>(
                minutes.coerceAtLeast(15).toLong(), TimeUnit.MINUTES,
            ).setConstraints(network).build()
            wm.enqueueUniquePeriodicWork(
                PERIODIC,
                if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                req,
            )
        }

        fun cancelAll(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
            WorkManager.getInstance(context).cancelUniqueWork(NOW)
        }
    }
}
