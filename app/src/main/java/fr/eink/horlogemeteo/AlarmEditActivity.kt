package fr.eink.horlogemeteo

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.TimePicker
import android.widget.Toast
import java.util.Calendar

/** Création / modification d'une alarme : heure, jours, nom, son, vibration. */
class AlarmEditActivity : Activity() {

    private lateinit var store: AlarmStore
    private var alarmId = -1
    private var soundUri: String? = null
    private var soundName = ""
    private val dayBoxes = mutableListOf<CheckBox>()
    private lateinit var picker: TimePicker
    private lateinit var soundLabel: TextView
    private lateinit var daysSummary: TextView
    private lateinit var testButton: Button
    private var preview: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_alarm_edit)
        store = AlarmStore(this)
        alarmId = intent.getIntExtra(EXTRA_ID, -1)
        val existing = store.get(alarmId)

        picker = findViewById(R.id.time_picker)
        picker.setIs24HourView(true)
        soundLabel = findViewById(R.id.sound_name)
        daysSummary = findViewById(R.id.days_summary)
        testButton = findViewById(R.id.sound_test)

        // Jours de la semaine (L M M J V S D)
        val row = findViewById<LinearLayout>(R.id.days_row)
        Alarm.DAY_LETTER.forEachIndexed { i, letter ->
            val cb = CheckBox(this, null, 0, R.style.DayToggle)
            cb.layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply {
                marginStart = dp(3); marginEnd = dp(3)
            }
            cb.text = letter
            cb.contentDescription = Alarm.DAY_SHORT[i]
            cb.setOnCheckedChangeListener { _, _ -> updateDaysSummary() }
            row.addView(cb)
            dayBoxes += cb
        }

        val label = findViewById<EditText>(R.id.label_field)
        val vibrate = findViewById<CheckBox>(R.id.vibrate)

        if (existing != null) {
            findViewById<TextView>(R.id.edit_title).text = "Modifier l'alarme"
            picker.hour = existing.hour
            picker.minute = existing.minute
            dayBoxes.forEachIndexed { i, cb -> cb.isChecked = existing.days and (1 shl i) != 0 }
            label.setText(existing.label)
            vibrate.isChecked = existing.vibrate
            soundUri = existing.soundUri
            soundName = existing.soundName
        } else {
            val now = Calendar.getInstance()
            picker.hour = now.get(Calendar.HOUR_OF_DAY)
            picker.minute = now.get(Calendar.MINUTE)
            vibrate.isChecked = true
            findViewById<Button>(R.id.delete).visibility = android.view.View.GONE
        }
        updateDaysSummary()
        updateSoundLabel()

        findViewById<Button>(R.id.sound_system).setOnClickListener { pickSystemSound() }
        findViewById<Button>(R.id.sound_file).setOnClickListener { pickAudioFile() }
        findViewById<Button>(R.id.sound_default).setOnClickListener {
            soundUri = null; soundName = ""; updateSoundLabel()
        }
        testButton.setOnClickListener { togglePreview() }

        findViewById<Button>(R.id.save).setOnClickListener {
            val days = dayBoxes.foldIndexed(0) { i, acc, cb -> if (cb.isChecked) acc or (1 shl i) else acc }
            val alarm = Alarm(
                id = if (alarmId >= 0) alarmId else store.newId(),
                hour = picker.hour,
                minute = picker.minute,
                days = days,
                enabled = true,
                label = label.text.toString().trim(),
                soundUri = soundUri,
                soundName = soundName,
                vibrate = vibrate.isChecked,
            )
            store.save(alarm)
            AlarmScheduler.schedule(this, alarm)
            Toast.makeText(this, "Alarme dans ${untilText(alarm.nextTrigger())}", Toast.LENGTH_LONG).show()
            finish()
        }
        findViewById<Button>(R.id.delete).setOnClickListener {
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

    private fun updateDaysSummary() {
        val days = dayBoxes.foldIndexed(0) { i, acc, cb -> if (cb.isChecked) acc or (1 shl i) else acc }
        daysSummary.text = Alarm(0, 0, 0, days).daysText
    }

    private fun updateSoundLabel() {
        soundLabel.text = when {
            soundUri == null -> "Son d'alarme par défaut"
            soundName.isNotBlank() -> soundName
            else -> "Son personnalisé"
        }
    }

    // ---------- Choix du son ----------

    private fun pickSystemSound() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Son de l'alarme")
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, soundUri?.let(Uri::parse))
        try {
            startActivityForResult(intent, REQ_RINGTONE)
        } catch (e: Exception) {
            Toast.makeText(this, "Sélecteur de sonneries indisponible : choisissez un fichier audio.", Toast.LENGTH_LONG).show()
        }
    }

    private fun pickAudioFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("audio/*")
        try {
            startActivityForResult(intent, REQ_FILE)
        } catch (e: Exception) {
            Toast.makeText(this, "Aucun gestionnaire de fichiers disponible.", Toast.LENGTH_LONG).show()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data == null) return
        when (requestCode) {
            REQ_RINGTONE -> {
                val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                    data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                }
                if (uri == null || RingtoneManager.isDefault(uri)) {
                    soundUri = null; soundName = ""
                } else {
                    soundUri = uri.toString()
                    soundName = runCatching { RingtoneManager.getRingtone(this, uri)?.getTitle(this) }
                        .getOrNull() ?: ""
                }
            }
            REQ_FILE -> {
                val uri = data.data ?: return
                // Garde l'accès au fichier après redémarrage.
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                soundUri = uri.toString()
                soundName = runCatching {
                    contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    }
                }.getOrNull() ?: "Fichier audio"
            }
        }
        updateSoundLabel()
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
        testButton.text = "■ Arrêter"
        preview?.setOnCompletionListener { stopPreview() }
        handler.postDelayed({ stopPreview() }, 15_000)
    }

    private fun stopPreview() {
        handler.removeCallbacksAndMessages(null)
        preview?.runCatching { stop(); release() }
        preview = null
        testButton.text = "▶ Écouter"
    }

    // ---------- Divers ----------

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIF)
        }
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_ID = "alarm_id"
        private const val REQ_RINGTONE = 1
        private const val REQ_FILE = 2
        private const val REQ_NOTIF = 3

        fun untilText(at: Long): String {
            val mins = ((at - System.currentTimeMillis()) / 60_000L).coerceAtLeast(0) + 1
            val h = mins / 60
            val m = mins % 60
            return when {
                h >= 24 -> "${h / 24} j ${h % 24} h"
                h > 0 -> "$h h ${m} min"
                else -> "$m min"
            }
        }
    }
}
