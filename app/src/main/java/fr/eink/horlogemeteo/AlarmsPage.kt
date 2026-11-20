package fr.eink.horlogemeteo

import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.Switch
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Onglet Alarme : cartes (grande heure, nom + jour), interrupteur ; crayon = mode suppression. */
class AlarmsPage(private val activity: MainActivity, private val root: View) : Page {

    override val title = "Alarme"
    override val hasAdd = true
    override val hasEdit = true

    private var deleteMode = false

    init {
        root.findViewById<View>(R.id.exact_alarm_warning).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    activity.startActivity(
                        Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${activity.packageName}")),
                    )
                }
            }
        }
    }

    override fun onShow() = render()

    override fun onHide() {
        deleteMode = false
    }

    override fun onAdd() {
        deleteMode = false
        activity.startActivity(Intent(activity, AlarmEditActivity::class.java))
    }

    override fun onEdit() {
        deleteMode = !deleteMode
        render()
    }

    private fun render() {
        val list = root.findViewById<LinearLayout>(R.id.alarm_list)
        list.removeAllViews()
        val store = AlarmStore(activity)
        val alarms = store.all()
        alarms.forEach { alarm ->
            val row = activity.layoutInflater.inflate(R.layout.item_alarm, list, false)
            val time = row.findViewById<TextView>(R.id.item_time)
            time.text = alarm.timeText
            time.setTextColor(if (alarm.enabled) 0xFF000000.toInt() else 0xFF8A8A8A.toInt())
            val sub = row.findViewById<TextView>(R.id.item_days)
            sub.text = subtitle(alarm)
            // Alarme désactivée : heure et nom barrés
            val strike = android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            listOf(time, sub).forEach {
                it.paintFlags = if (alarm.enabled) it.paintFlags and strike.inv() else it.paintFlags or strike
            }

            val sw = row.findViewById<Switch>(R.id.item_switch)
            val del = row.findViewById<View>(R.id.item_delete)
            sw.visibility = if (deleteMode) View.GONE else View.VISIBLE
            del.visibility = if (deleteMode) View.VISIBLE else View.GONE
            sw.isChecked = alarm.enabled
            sw.setOnCheckedChangeListener { _, checked ->
                val updated = alarm.copy(enabled = checked)
                store.save(updated)
                AlarmScheduler.schedule(activity, updated)
                render()
            }
            del.setOnClickListener {
                AlarmScheduler.cancel(activity, alarm.id)
                store.delete(alarm.id)
                render()
            }
            row.setOnClickListener {
                activity.startActivity(
                    Intent(activity, AlarmEditActivity::class.java).putExtra(AlarmEditActivity.EXTRA_ID, alarm.id),
                )
            }
            list.addView(row)
        }

        val next = AlarmScheduler.nextAlarm(activity)
        root.findViewById<TextView>(R.id.next_alarm).text = when {
            alarms.isEmpty() -> "Aucune alarme. Touchez ⊕ en haut pour en ajouter une."
            deleteMode -> "Touchez la corbeille pour supprimer une alarme, ou le crayon pour terminer."
            next == null -> "Aucune alarme active."
            else -> "Prochaine alarme dans ${AlarmEditActivity.untilText(next.second)}"
        }

        val am = activity.getSystemService(AlarmManager::class.java)
        val missingExact = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()
        root.findViewById<View>(R.id.exact_alarm_warning).visibility =
            if (missingExact && alarms.any { it.enabled }) View.VISIBLE else View.GONE
    }

    /** « Réveil Travail   Demain » ; répétition affichée si l'alarme est récurrente. */
    private fun subtitle(alarm: Alarm): String {
        val name = alarm.label.ifBlank { "Alarme" }
        val parts = mutableListOf(name)
        if (alarm.days != 0) parts += alarm.daysText
        if (alarm.enabled) parts += relativeDay(alarm.nextTrigger()) else if (alarm.days == 0) parts += "Désactivée"
        return parts.joinToString("   ")
    }

    companion object {
        fun relativeDay(at: Long): String {
            fun dayNumber(ms: Long): Long {
                val c = Calendar.getInstance().apply { timeInMillis = ms }
                return c.get(Calendar.YEAR) * 400L + c.get(Calendar.DAY_OF_YEAR)
            }
            val today = Calendar.getInstance()
            val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
            val days = when (dayNumber(at)) {
                dayNumber(today.timeInMillis) -> 0
                dayNumber(tomorrow.timeInMillis) -> 1
                else -> 2
            }
            return when (days) {
                0 -> "Aujourd'hui"
                1 -> "Demain"
                else -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(at))
                    .replaceFirstChar { it.titlecase(Locale.getDefault()) }
            }
        }
    }
}
