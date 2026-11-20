package fr.eink.horlogemeteo

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * Une alarme. [days] : masque de bits, bit 0 = lundi … bit 6 = dimanche ; 0 = une seule fois.
 * [soundUri] null = son d'alarme par défaut du système.
 */
data class Alarm(
    val id: Int,
    val hour: Int,
    val minute: Int,
    val days: Int = 0,
    val enabled: Boolean = true,
    val label: String = "",
    val soundUri: String? = null,
    val soundName: String = "",
    val vibrate: Boolean = true,
) {
    val timeText: String get() = String.format(java.util.Locale.ROOT, "%02d:%02d", hour, minute)

    val daysText: String
        get() = when (days) {
            0 -> "Une fois"
            0b1111111 -> "Tous les jours"
            0b0011111 -> "Du lundi au vendredi"
            0b1100000 -> "Le week-end"
            else -> DAY_SHORT.indices.filter { days and (1 shl it) != 0 }.joinToString(" ") { DAY_SHORT[it] }
        }

    /** Prochain déclenchement (heure de l'appareil) strictement après [from]. */
    fun nextTrigger(from: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance().apply {
            timeInMillis = from
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        repeat(8) {
            if (c.timeInMillis > from && (days == 0 || days and (1 shl dayIndex(c)) != 0)) return c.timeInMillis
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        return c.timeInMillis
    }

    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("hour", hour).put("minute", minute).put("days", days)
        .put("enabled", enabled).put("label", label).put("soundUri", soundUri ?: "")
        .put("soundName", soundName).put("vibrate", vibrate)

    companion object {
        val DAY_SHORT = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")
        val DAY_LETTER = listOf("L", "M", "M", "J", "V", "S", "D")

        /** Calendar.DAY_OF_WEEK → index 0 (lundi) … 6 (dimanche). */
        fun dayIndex(c: Calendar): Int = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7

        fun fromJson(o: JSONObject) = Alarm(
            id = o.getInt("id"),
            hour = o.getInt("hour"),
            minute = o.getInt("minute"),
            days = o.optInt("days", 0),
            enabled = o.optBoolean("enabled", true),
            label = o.optString("label", ""),
            soundUri = o.optString("soundUri", "").ifBlank { null },
            soundName = o.optString("soundName", ""),
            vibrate = o.optBoolean("vibrate", true),
        )
    }
}

/** Stockage des alarmes (JSON dans les SharedPreferences). */
class AlarmStore(context: Context) {
    private val appContext = context.applicationContext
    private val sp = appContext.getSharedPreferences("alarms", Context.MODE_PRIVATE)

    fun all(): List<Alarm> {
        val arr = JSONArray(sp.getString("list", "[]"))
        return (0 until arr.length()).map { Alarm.fromJson(arr.getJSONObject(it)) }
            .sortedWith(compareBy({ it.hour }, { it.minute }))
    }

    fun get(id: Int): Alarm? = all().firstOrNull { it.id == id }

    fun save(alarm: Alarm) = write(all().filter { it.id != alarm.id } + alarm)

    fun delete(id: Int) = write(all().filter { it.id != id })

    fun newId(): Int = (all().maxOfOrNull { it.id } ?: 0) + 1

    private fun write(list: List<Alarm>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        sp.edit().putString("list", arr.toString()).commit()
        WidgetRenderer.updateAll(appContext) // la prochaine alarme affichée sur le widget change
    }
}
