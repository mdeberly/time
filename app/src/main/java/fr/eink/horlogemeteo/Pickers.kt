package fr.eink.horlogemeteo

import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.widget.NumberPicker
import java.util.Locale

/** Roue de sélection noir et blanc, valeurs sur 2 chiffres (00, 01…). */
fun NumberPicker.setupInk(max: Int, textSp: Float = 30f) {
    minValue = 0
    maxValue = max
    displayedValues = Array(max + 1) { String.format(Locale.ROOT, "%02d", it) }
    wrapSelectorWheel = true
    descendantFocusability = NumberPicker.FOCUS_BLOCK_DESCENDANTS
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, textSp, resources.displayMetrics)
        selectionDividerHeight = 0
        setTextColor(Color.BLACK)
    }
}
