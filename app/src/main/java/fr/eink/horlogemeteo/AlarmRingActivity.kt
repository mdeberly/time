package fr.eink.horlogemeteo

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Écran affiché quand l'alarme sonne (y compris sur l'écran verrouillé). */
class AlarmRingActivity : Activity() {

    private val finished = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON,
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_alarm_ring)

        findViewById<TextView>(R.id.ring_time).text =
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
        findViewById<TextView>(R.id.ring_label).text = AlarmService.ringingLabel.ifBlank { "Réveil" }
        findViewById<Button>(R.id.ring_snooze).text = "Répéter dans ${AlarmScheduler.SNOOZE_MINUTES} min"
        if (AlarmService.ringingId == AlarmScheduler.TIMER_ID) {
            findViewById<Button>(R.id.ring_snooze).visibility = android.view.View.GONE
        }
        findViewById<Button>(R.id.ring_snooze).setOnClickListener {
            AlarmService.send(this, AlarmService.ACTION_SNOOZE)
            finish()
        }
        findViewById<Button>(R.id.ring_stop).setOnClickListener {
            AlarmService.send(this, AlarmService.ACTION_STOP)
            finish()
        }

        val filter = IntentFilter(AlarmService.ACTION_FINISHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(finished, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(finished, filter)
        }
    }

    override fun onResume() {
        super.onResume()
        if (AlarmService.ringingId == AlarmService.NONE) finish()
    }

    override fun onDestroy() {
        unregisterReceiver(finished)
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Retour = arrêter, pour ne pas laisser sonner sans écran.
        AlarmService.send(this, AlarmService.ACTION_STOP)
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }
}
