package fr.eink.horlogemeteo

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import java.util.Locale

/** État du chronomètre, conservé même si l'appli est fermée. */
class StopwatchState(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("stopwatch", Context.MODE_PRIVATE)

    val running: Boolean get() = sp.getBoolean("running", false)
    private val startedAt: Long get() = sp.getLong("started_at", 0)
    private val accumulated: Long get() = sp.getLong("accumulated", 0)

    /** Temps écoulé en millisecondes. */
    fun elapsed(): Long =
        accumulated + if (running) (SystemClock.elapsedRealtime() - startedAt).coerceAtLeast(0) else 0

    fun start() {
        if (running) return
        sp.edit().putBoolean("running", true).putLong("started_at", SystemClock.elapsedRealtime()).apply()
    }

    fun pause() {
        if (!running) return
        sp.edit().putLong("accumulated", elapsed()).putBoolean("running", false).apply()
    }

    fun reset() {
        sp.edit().clear().apply()
    }

    /** Temps cumulés des tours (le plus ancien en premier). */
    fun laps(): List<Long> {
        val arr = JSONArray(sp.getString("laps", "[]"))
        return (0 until arr.length()).map { arr.getLong(it) }
    }

    fun addLap() {
        val arr = JSONArray(sp.getString("laps", "[]"))
        arr.put(elapsed())
        sp.edit().putString("laps", arr.toString()).apply()
    }

    companion object {
        /** 01:02:03.45 */
        fun format(ms: Long, centis: Boolean = true): String {
            val h = ms / 3_600_000
            val m = ms / 60_000 % 60
            val s = ms / 1000 % 60
            val c = ms / 10 % 100
            return if (centis) String.format(Locale.ROOT, "%02d:%02d:%02d.%02d", h, m, s, c)
            else String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, s)
        }
    }
}

/** État du minuteur. La fin est programmée comme une alarme (sonne même appli fermée). */
class TimerState(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("timer", Context.MODE_PRIVATE)

    enum class Mode { IDLE, RUNNING, PAUSED }

    val mode: Mode get() = runCatching { Mode.valueOf(sp.getString("mode", "IDLE")!!) }.getOrDefault(Mode.IDLE)

    /** Durée réglée sur les roues (ms). */
    var duration: Long
        get() = sp.getLong("duration", 5 * 60_000L)
        set(v) = sp.edit().putLong("duration", v).apply()

    /** Heure de fin (horloge murale, ms) quand le minuteur tourne. */
    val endAt: Long get() = sp.getLong("end_at", 0)

    var soundUri: String?
        get() = sp.getString("sound_uri", null)
        set(v) = sp.edit().putString("sound_uri", v).apply()
    var soundName: String
        get() = sp.getString("sound_name", "") ?: ""
        set(v) = sp.edit().putString("sound_name", v).apply()

    fun remaining(): Long = when (mode) {
        Mode.RUNNING -> (endAt - System.currentTimeMillis()).coerceAtLeast(0)
        Mode.PAUSED -> sp.getLong("remaining", 0)
        Mode.IDLE -> duration
    }

    fun start(context: Context) {
        val left = if (mode == Mode.PAUSED) sp.getLong("remaining", duration) else duration
        if (left <= 0) return
        val end = System.currentTimeMillis() + left
        sp.edit().putString("mode", Mode.RUNNING.name).putLong("end_at", end).apply()
        AlarmScheduler.scheduleTimer(context, end)
    }

    fun pause(context: Context) {
        if (mode != Mode.RUNNING) return
        sp.edit().putString("mode", Mode.PAUSED.name).putLong("remaining", remaining()).apply()
        AlarmScheduler.cancelTimer(context)
    }

    fun reset(context: Context) {
        sp.edit().putString("mode", Mode.IDLE.name).remove("remaining").remove("end_at").apply()
        AlarmScheduler.cancelTimer(context)
    }

    /** Appelé quand le minuteur arrive à zéro. */
    fun finished() {
        sp.edit().putString("mode", Mode.IDLE.name).remove("remaining").remove("end_at").apply()
    }

    /** Alarme « virtuelle » utilisée pour faire sonner la fin du minuteur. */
    fun asAlarm(): Alarm = Alarm(
        id = AlarmScheduler.TIMER_ID, hour = 0, minute = 0, label = "Minuteur terminé",
        soundUri = soundUri, soundName = soundName, vibrate = true,
    )
}
