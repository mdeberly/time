package fr.eink.horlogemeteo

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.ImageButton
import android.widget.NumberPicker
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Onglet Minuteur : roues h / min / s, démarrer / pause, réinitialiser, choix du son (cloche). */
class TimerPage(private val activity: MainActivity, private val root: View) : Page {

    override val title = "Minuteur"

    private val state = TimerState(activity)
    private val handler = Handler(Looper.getMainLooper())
    private val display: TextView = root.findViewById(R.id.timer_display)
    private val endText: TextView = root.findViewById(R.id.timer_end)
    private val pickers: View = root.findViewById(R.id.timer_pickers)
    private val units: View = root.findViewById(R.id.timer_units)
    private val h: NumberPicker = root.findViewById(R.id.timer_h)
    private val m: NumberPicker = root.findViewById(R.id.timer_m)
    private val s: NumberPicker = root.findViewById(R.id.timer_s)
    private val startBtn: ImageButton = root.findViewById(R.id.timer_start)
    private val resetBtn: ImageButton = root.findViewById(R.id.timer_reset)

    private val ticker = object : Runnable {
        override fun run() {
            if (state.mode == TimerState.Mode.RUNNING) {
                val left = state.remaining()
                display.text = format(left)
                if (left <= 0) {
                    handler.postDelayed({ refresh() }, 1500)
                    return
                }
                handler.postAtTime(this, SystemClock.uptimeMillis() + ((left - 1) % 1000) + 1)
            }
        }
    }

    init {
        h.setupInk(23)
        m.setupInk(59)
        s.setupInk(59)
        val listener = NumberPicker.OnValueChangeListener { _, _, _ ->
            state.duration = (h.value * 3600L + m.value * 60L + s.value) * 1000L
            updateIdle()
        }
        h.setOnValueChangedListener(listener)
        m.setOnValueChangedListener(listener)
        s.setOnValueChangedListener(listener)

        startBtn.setOnClickListener {
            if (state.mode == TimerState.Mode.RUNNING) state.pause(activity) else state.start(activity)
            refresh()
        }
        resetBtn.setOnClickListener {
            state.reset(activity)
            refresh()
        }
        root.findViewById<View>(R.id.timer_sound).setOnClickListener {
            SoundPicker.show(activity, state.soundUri) {
                state.soundUri = null
                state.soundName = ""
                toastSound()
            }
        }
    }

    override fun onShow() = refresh()

    override fun onHide() {
        handler.removeCallbacksAndMessages(null)
    }

    /** Résultat du choix de son (transmis par MainActivity). */
    fun onSoundPicked(uri: String?, name: String) {
        state.soundUri = uri
        state.soundName = name
        toastSound()
    }

    private fun toastSound() {
        Toast.makeText(activity, "Son du minuteur : ${SoundPicker.label(state.soundUri, state.soundName)}", Toast.LENGTH_SHORT).show()
    }

    private fun refresh() {
        handler.removeCallbacksAndMessages(null)
        val mode = state.mode
        val idle = mode == TimerState.Mode.IDLE
        pickers.visibility = if (idle) View.VISIBLE else View.GONE
        units.visibility = if (idle) View.VISIBLE else View.INVISIBLE
        endText.visibility = if (idle) View.GONE else View.VISIBLE
        startBtn.setImageResource(if (mode == TimerState.Mode.RUNNING) R.drawable.ic_pause_circle else R.drawable.ic_play_circle)
        resetBtn.isEnabled = !idle
        resetBtn.alpha = if (idle) 0.35f else 1f
        startBtn.isEnabled = true
        startBtn.alpha = 1f

        when (mode) {
            TimerState.Mode.IDLE -> {
                val total = state.duration / 1000
                h.value = (total / 3600).toInt()
                m.value = (total / 60 % 60).toInt()
                s.value = (total % 60).toInt()
                updateIdle()
            }
            TimerState.Mode.RUNNING -> {
                endText.text = "Fin à " + SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(state.endAt))
                ticker.run()
            }
            TimerState.Mode.PAUSED -> {
                display.text = format(state.remaining())
                endText.text = "En pause"
            }
        }
    }

    private fun updateIdle() {
        display.text = format(state.duration)
        val canStart = state.duration > 0
        startBtn.isEnabled = canStart
        startBtn.alpha = if (canStart) 1f else 0.35f
    }

    /** Affichage arrondi à la seconde supérieure (comme un compte à rebours). */
    private fun format(ms: Long): String {
        val total = (ms + 999) / 1000
        return String.format(Locale.ROOT, "%02d:%02d:%02d", total / 3600, total / 60 % 60, total % 60)
    }
}
