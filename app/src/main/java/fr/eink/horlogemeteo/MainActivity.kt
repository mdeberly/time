package fr.eink.horlogemeteo

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Écran principal à 4 onglets : Horloge, Alarme, Chronomètre, Minuteur. */
class MainActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var prefs: Prefs
    private lateinit var pages: List<Page>
    private lateinit var pageViews: List<View>
    private lateinit var timerPage: TimerPage
    private var current = 0
    private var resumed = false

    private val tabIds = listOf(R.id.tab_clock, R.id.tab_alarms, R.id.tab_stopwatch, R.id.tab_timer)
    private val tabIconIds = listOf(R.id.tab_clock_icon, R.id.tab_alarms_icon, R.id.tab_stopwatch_icon, R.id.tab_timer_icon)
    private val iconsOff = listOf(R.drawable.ic_tab_clock, R.drawable.ic_tab_alarm, R.drawable.ic_tab_stopwatch, R.drawable.ic_tab_timer)
    private val iconsOn = listOf(R.drawable.ic_tab_clock_on, R.drawable.ic_tab_alarm_on, R.drawable.ic_tab_stopwatch_on, R.drawable.ic_tab_timer_on)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)

        pageViews = listOf(
            findViewById(R.id.page_clock), findViewById(R.id.page_alarms),
            findViewById(R.id.page_stopwatch), findViewById(R.id.page_timer),
        )
        timerPage = TimerPage(this, pageViews[3])
        pages = listOf(
            ClockPage(this, pageViews[0], scope),
            AlarmsPage(this, pageViews[1]),
            StopwatchPage(this, pageViews[2]),
            timerPage,
        )

        tabIds.forEachIndexed { i, id -> findViewById<View>(id).setOnClickListener { select(i) } }
        findViewById<View>(R.id.header_add).setOnClickListener { pages[current].onAdd() }
        findViewById<View>(R.id.header_edit).setOnClickListener { pages[current].onEdit() }
        findViewById<View>(R.id.header_settings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        current = prefs.lastTab.coerceIn(0, 3)
        applyTab()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        pages[current].onShow()
    }

    override fun onPause() {
        resumed = false
        pages[current].onHide()
        super.onPause()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun select(index: Int) {
        if (index == current) return
        pages[current].onHide()
        current = index
        prefs.lastTab = index
        applyTab()
        if (resumed) pages[current].onShow()
    }

    private fun applyTab() {
        pageViews.forEachIndexed { i, v -> v.visibility = if (i == current) View.VISIBLE else View.GONE }
        tabIconIds.forEachIndexed { i, id ->
            findViewById<ImageView>(id).setImageResource(if (i == current) iconsOn[i] else iconsOff[i])
        }
        val page = pages[current]
        findViewById<TextView>(R.id.header_title).text = page.title
        findViewById<View>(R.id.header_add).visibility = if (page.hasAdd) View.VISIBLE else View.GONE
        findViewById<View>(R.id.header_edit).visibility = if (page.hasEdit) View.VISIBLE else View.GONE
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        SoundPicker.parseResult(this, requestCode, resultCode, data)?.let { (uri, name) ->
            timerPage.onSoundPicked(uri, name)
        }
    }
}
