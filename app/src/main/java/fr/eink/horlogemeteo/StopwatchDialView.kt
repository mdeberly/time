package fr.eink.horlogemeteo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Cadran analogique du chronomètre : 60 graduations, chiffres 0 à 11,
 * aiguilles des heures (courte), des minutes et des secondes (fine). Noir sur blanc.
 */
class StopwatchDialView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null,
) : View(context, attrs) {

    /** Temps affiché, en millisecondes. */
    var elapsed: Long = 0
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    private val density = resources.displayMetrics.density
    private val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeCap = Paint.Cap.ROUND }
    private val hand = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeCap = Paint.Cap.ROUND }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textAlign = Paint.Align.CENTER }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 2 * density

        // Graduations
        for (i in 0 until 60) {
            val major = i % 5 == 0
            val len = if (major) r * 0.11f else r * 0.055f
            tick.strokeWidth = if (major) 2.6f * density else 1.4f * density
            val a = Math.toRadians(i * 6.0 - 90)
            val ca = cos(a).toFloat()
            val sa = sin(a).toFloat()
            canvas.drawLine(cx + ca * r, cy + sa * r, cx + ca * (r - len), cy + sa * (r - len), tick)
        }

        // Chiffres 0 à 11
        text.textSize = r * 0.13f
        val tr = r * 0.74f
        for (i in 0 until 12) {
            val a = Math.toRadians(i * 30.0 - 90)
            val x = cx + cos(a).toFloat() * tr
            val y = cy + sin(a).toFloat() * tr - (text.ascent() + text.descent()) / 2
            canvas.drawText(i.toString(), x, y, text)
        }

        val totalSec = elapsed / 1000.0
        val seconds = totalSec % 60
        val minutes = (totalSec / 60) % 60
        val hours = (totalSec / 3600) % 12

        drawHand(canvas, cx, cy, hours * 30, r * 0.42f, 6f * density, 0f)
        drawHand(canvas, cx, cy, minutes * 6, r * 0.62f, 3.6f * density, 0f)
        drawHand(canvas, cx, cy, seconds * 6, r * 0.86f, 1.6f * density, r * 0.16f)
        canvas.drawCircle(cx, cy, 5f * density, dot)
    }

    /** Aiguille à [deg] degrés depuis midi, avec un petit contrepoids [tail]. */
    private fun drawHand(canvas: Canvas, cx: Float, cy: Float, deg: Double, length: Float, w: Float, tail: Float) {
        val a = Math.toRadians(deg - 90)
        val ca = cos(a).toFloat()
        val sa = sin(a).toFloat()
        hand.strokeWidth = w
        canvas.drawLine(cx - ca * tail, cy - sa * tail, cx + ca * length, cy + sa * length, hand)
    }
}
