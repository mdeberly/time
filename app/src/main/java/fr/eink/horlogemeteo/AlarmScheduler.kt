package fr.eink.horlogemeteo

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/** Programme les alarmes auprès du système (AlarmManager.setAlarmClock = alarme exacte, réveille l'appareil). */
object AlarmScheduler {
    const val SNOOZE_MINUTES = 10
    private const val SNOOZE_OFFSET = 100_000

    fun schedule(context: Context, alarm: Alarm) {
        cancel(context, alarm.id)
        if (alarm.enabled) setAt(context, alarm.id, alarm.nextTrigger(), snooze = false)
    }

    fun scheduleAll(context: Context) {
        AlarmStore(context).all().forEach { schedule(context, it) }
    }

    fun snooze(context: Context, alarmId: Int) {
        setAt(context, alarmId, System.currentTimeMillis() + SNOOZE_MINUTES * 60_000L, snooze = true)
    }

    fun cancel(context: Context, alarmId: Int) {
        val am = context.getSystemService(AlarmManager::class.java)
        am.cancel(pending(context, alarmId, snooze = false))
        am.cancel(pending(context, alarmId, snooze = true))
    }

    /** Prochaine alarme active, pour l'affichage. */
    fun nextAlarm(context: Context): Pair<Alarm, Long>? =
        AlarmStore(context).all().filter { it.enabled }
            .map { it to it.nextTrigger() }
            .minByOrNull { it.second }

    private fun setAt(context: Context, alarmId: Int, at: Long, snooze: Boolean) {
        val am = context.getSystemService(AlarmManager::class.java)
        val pi = pending(context, alarmId, snooze)
        val show = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val exactAllowed = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exactAllowed) {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pending(context: Context, alarmId: Int, snooze: Boolean): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction(AlarmReceiver.ACTION_FIRE)
            .putExtra(AlarmReceiver.EXTRA_ID, alarmId)
            .putExtra(AlarmReceiver.EXTRA_SNOOZE, snooze)
        return PendingIntent.getBroadcast(
            context, if (snooze) alarmId + SNOOZE_OFFSET else alarmId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
