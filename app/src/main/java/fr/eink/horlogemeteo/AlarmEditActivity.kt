package fr.eink.horlogemeteo

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.NumberPicker
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import java.util.Calendar

/** Création / modification d'une alarme : roues heure:minute, puis Répéter / Nom / Sonnerie / Vibreur. */
class AlarmEditActivity : Activity() {

    private lateinit var store: AlarmStore
    private var alarmId = -1
    private var days = 0
    private var label = ""
    private var soundUri: String? = null
    private var soundName = ""
    private lateinit var hour: NumberPicker
    private lateinit var minute: NumberPicker
    private lateinit var vibrate: Switch
    private lateinit var testButton: Button
    private var preview: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_edit)
        store = AlarmStore(this)
        alarmId = intent.getIntExtra(EXTRA_ID, -1)
        val existing = store.get(alarmId)

        hour = findViewById(R.id.pick_hour)
        minute = findViewById(R.id.pick_minute)
        hour.setupInk(23, 34f)
        minute.setupInk(59, 34f)
        vibrate = findViewById(R.id.vibrate)
        testButton = findViewById(R.id.sound_test)

        if (existing != null) {
            findViewById<TextView>(R.id.edit_title).text = "Modifier l'alarme"
            hour.value = existing.hour
            minute.value = existing.minute
            days = existing.days
            label = existing.label
            soundUri = existing.soundUri
            soundName = existing.soundName
            vibrate.isChecked = existing.vibrate
        } else {
            val now = Calendar.getInstance()
            hour.value = now.get(Calendar.HOUR_OF_DAY)
            minute.value = now.get(Calendar.MINUTE)
            vibrate.isChecked = true
            findViewById<View>(R.id.delete).visibility = View.GONE
        }
        renderRows()

        findViewById<View>(R.id.edit_cancel).setOnClickListener { finish() }
        findViewById<View>(R.id.edit_save).setOnClickListener { save() }
        findViewById<View>(R.id.row_repeat).setOnClickListener { pickDays() }
        findViewById<View>(R.id.row_name).setOnClickListener { pickName() }
        findViewById<View>(R.id.row_sound).setOnClickListener {
            SoundPicker.show(this, soundUri) {
                soundUri = null
                soundName = ""
                renderRows()
            }
        }
        testButton.setOnClickListener { togglePreview() }
        findViewById<View>(R.id.delete).setOnClickListener {
            AlarmScheduler.cancel(this, alarmId)
            store.delete(alarmId)
            finish()
        }

        askNotificationPermission()
    }

    override fun onPause() {
        stopPreview()
        super.onPause()
    }

    private fun renderRows() {
        findViewById<TextView>(R.id.value_repeat).text = if (days == 0) "Jamais" else Alarm(0, 0, 0, days).daysText
        findViewById<TextView>(R.id.value_name).text = label.ifBlank { "Alarme" }
        findViewById<TextView>(R.id.value_sound).text = SoundPicker.label(soundUri, soundName)
    }

    private fun save() {
        val alarm = Alarm(
            id = if (alarmId >= 0) alarmId else store.newId(),
            hour = hour.value,
            minute = minute.value,
            days = days,
            enabled = true,
            label = label.trim(),
            soundUri = soundUri,
            soundName = soundName,
            vibrate = vibrate.isChecked,
        )
        store.save(alarm)
        AlarmScheduler.schedule(this, alarm)
        Toast.makeText(this, "Alarme dans ${untilText(alarm.nextTrigger())}", Toast.LENGTH_LONG).show()
        finish()
    }

    private fun pickDays() {
        val names = arrayOf("Lundi", "Mardi", "Mercredi", "Jeudi", "Vendredi", "Samedi", "Dimanche")
        val checked = BooleanArray(7) { days and (1 shl it) != 0 }
        AlertDialog.Builder(this, R.style.InkDialog)
            .setTitle("Répéter")
            .setMultiChoiceItems(names, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("OK") { _, _ ->
                days = checked.foldIndexed(0) { i, acc, on -> if (on) acc or (1 shl i) else acc }
                renderRows()
            }
            .setNeutralButton("Jamais") { _, _ ->
                days = 0
                renderRows()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun pickName() {
        val field = EditText(this).apply {
            setText(label)
            hint = "ex. Réveil Travail"
            setSingleLine()
            setTextColor(0xFF000000.toInt())
            setSelection(text.length)
        }
        val pad = (20 * resources.displayMetrics.density).toInt()
        val box = FrameLayout(this).apply { setPadding(pad, pad / 2, pad, 0); addView(field) }
        AlertDialog.Builder(this, R.style.InkDialog)
            .setTitle("Nom")
            .setView(box)
            .setPositiveButton("OK") { _, _ ->
                label = field.text.toString().trim()
                renderRows()
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        SoundPicker.parseResult(this, requestCode, resultCode, data)?.let { (uri, name) ->
            soundUri = uri
            soundName = name
            renderRows()
        }
    }

    private fun togglePreview() {
        if (preview != null) {
            stopPreview()
            return
        }
        preview = AlarmService.startSound(this, soundUri?.let(Uri::parse), loop = false)
        if (preview == null) {
            Toast.makeText(this, "Impossible de lire ce son.", Toast.LENGTH_SHORT).show()
            return
        }
        testButton.text = "■ Arrêter l'écoute"
        preview?.setOnCompletionListener { stopPreview() }
        handler.postDelayed({ stopPreview() }, 15_000)
    }

    private fun stopPreview() {
        handler.removeCallbacksAndMessages(null)
        preview?.runCatching { stop(); release() }
        preview = null
        testButton.text = "▶ Écouter la sonnerie"
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
    }

    companion object {
        const val EXTRA_ID = "alarm_id"
        private const val REQ_NOTIF = 3

        fun untilText(at: Long): String {
            val mins = ((at - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0) + 1
            val h = mins / 60
            val m = mins % 60
            return when {
                h >= 24 -> "${h / 24} j ${h % 24} h"
                h > 0 -> "$h h $m min"
                else -> "$m min"
            }
        }
    }
}
