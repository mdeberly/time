package fr.eink.horlogemeteo

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager

/** Profil sonore : Sonnerie activée → Vibreur → Muet → Sonnerie activée… */
object RingerMode {

    fun current(context: Context): Int =
        context.getSystemService(AudioManager::class.java)?.ringerMode ?: AudioManager.RINGER_MODE_NORMAL

    fun label(mode: Int): String = when (mode) {
        AudioManager.RINGER_MODE_SILENT -> "Muet"
        AudioManager.RINGER_MODE_VIBRATE -> "Vibreur"
        else -> "Sonnerie activée"
    }

    fun icon(mode: Int): Int = when (mode) {
        AudioManager.RINGER_MODE_SILENT -> R.drawable.ic_ringer_silent
        AudioManager.RINGER_MODE_VIBRATE -> R.drawable.ic_ringer_vibrate
        else -> R.drawable.ic_ringer_normal
    }

    /** Le mode « Muet » (et la sortie de ce mode) exige l'accès « Ne pas déranger ». */
    fun hasAccess(context: Context): Boolean =
        context.getSystemService(NotificationManager::class.java)?.isNotificationPolicyAccessGranted == true

    /** Passe au mode suivant. Retourne false si l'accès « Ne pas déranger » manque. */
    fun cycle(context: Context): Boolean {
        val am = context.getSystemService(AudioManager::class.java) ?: return false
        val next = when (am.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        return try {
            am.ringerMode = next
            true
        } catch (e: SecurityException) {
            false
        }
    }
}
