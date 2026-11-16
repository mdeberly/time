package fr.eink.horlogemeteo

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Reçoit le déclenchement d'une alarme, et reprogramme tout au démarrage / changement d'heure. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_FIRE -> {
                val id = intent.getIntExtra(EXTRA_ID, -1)
                val snooze = intent.getBooleanExtra(EXTRA_SNOOZE, false)
                val alarm = AlarmStore(context).get(id) ?: return
                if (!snooze) {
                    // Alarme ponctuelle → désactivée ; récurrente → prochaine occurrence.
                    if (alarm.days == 0) AlarmStore(context).save(alarm.copy(enabled = false))
                    else AlarmScheduler.schedule(context, alarm)
                }
                context.startForegroundService(
                    Intent(context, AlarmService::class.java)
                        .setAction(AlarmService.ACTION_RING)
                        .putExtra(EXTRA_ID, id),
                )
            }
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED",
            -> AlarmScheduler.scheduleAll(context)
        }
    }

    companion object {
        const val ACTION_FIRE = "fr.eink.horlogemeteo.ALARM_FIRE"
        const val EXTRA_ID = "alarm_id"
        const val EXTRA_SNOOZE = "snooze"
    }
}
