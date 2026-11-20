package fr.eink.horlogemeteo

import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView

/** Onglet Chronomètre : cadran analogique (h, min, s) + affichage digital avec centièmes, tours. */
class StopwatchPage(private val activity: MainActivity, private val root: View) : Page {

    override val title = "Chronomètre"

    private val state = StopwatchState(activity)
    private val handler = Handler(Looper.getMainLooper())
    private val dial: StopwatchDialView = root.findViewById(R.id.sw_dial)
    private val digital: TextView = root.findViewById(R.id.sw_digital)
    private val startBtn: ImageButton = root.findViewById(R.id.sw_start)
    private val resetBtn: ImageButton = root.findViewById(R.id.sw_reset)
    private val lapBtn: ImageButton = root.findViewById(R.id.sw_lap)
    private val laps: LinearLayout = root.findViewById(R.id.sw_laps)

    /** ~30 images/s pendant que le chronomètre tourne (centièmes). */
    private val ticker = object : Runnable {
        override fun run() {
            showTime()
            if (state.running) handler.postDelayed(this, 33)
        }
    }

    init {
        startBtn.setOnClickListener {
            if (state.running) state.pause() else state.start()
            refresh()
        }
        resetBtn.setOnClickListener {
            if (!state.running) {
                state.reset()
                refresh()
            }
        }
        lapBtn.setOnClickListener {
            if (state.running) {
                state.addLap()
                renderLaps()
            }
        }
    }

    override fun onShow() = refresh()

    override fun onHide() {
        handler.removeCallbacksAndMessages(null)
    }

    private fun refresh() {
        handler.removeCallbacks(ticker)
        val running = state.running
        startBtn.setImageResource(if (running) R.drawable.ic_pause_circle else R.drawable.ic_play_circle)
        startBtn.contentDescription = if (running) "Pause" else "Démarrer"
        resetBtn.isEnabled = !running && state.elapsed() > 0
        resetBtn.alpha = if (resetBtn.isEnabled) 1f else 0.35f
        lapBtn.isEnabled = running
        lapBtn.alpha = if (running) 1f else 0.35f
        renderLaps()
        ticker.run()
    }

    private fun showTime() {
        val ms = state.elapsed()
        dial.elapsed = ms
        digital.text = StopwatchState.format(ms)
    }

    /** Tours, le plus récent en haut : « Tour 3   +00:00:20.12   00:01:23.45 ». */
    private fun renderLaps() {
        laps.removeAllViews()
        val all = state.laps()
        for (i in all.indices.reversed()) {
            val split = all[i] - if (i > 0) all[i - 1] else 0
            val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL }
            fun cell(text: String, weight: Float, gravity: Int) = TextView(activity).apply {
                this.text = text
                this.gravity = gravity
                textSize = 16f
                setTextColor(0xFF000000.toInt())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight)
            }
            row.addView(cell("Tour ${i + 1}", 0.8f, Gravity.START))
            row.addView(cell("+" + StopwatchState.format(split), 1.2f, Gravity.CENTER))
            row.addView(cell(StopwatchState.format(all[i]), 1.1f, Gravity.END))
            row.setPadding(0, 6, 0, 6)
            laps.addView(row)
        }
    }
}
