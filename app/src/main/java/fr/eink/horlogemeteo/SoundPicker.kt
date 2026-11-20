package fr.eink.horlogemeteo

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.widget.Toast

/** Choix d'un son (sonnerie système ou fichier audio), partagé par les alarmes et le minuteur. */
object SoundPicker {
    const val REQ_SYSTEM = 501
    const val REQ_FILE = 502

    /** Affiche le choix : sonnerie du système / fichier audio / son par défaut. */
    fun show(activity: Activity, currentUri: String?, onDefault: () -> Unit) {
        AlertDialog.Builder(activity, R.style.InkDialog)
            .setTitle("Son")
            .setItems(arrayOf("Sonneries du système", "Fichier audio (MP3…)", "Son d'alarme par défaut")) { _, which ->
                when (which) {
                    0 -> pickSystem(activity, currentUri)
                    1 -> pickFile(activity)
                    else -> onDefault()
                }
            }
            .show()
    }

    private fun pickSystem(activity: Activity, currentUri: String?) {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM or RingtoneManager.TYPE_RINGTONE)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Son")
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, currentUri?.let(Uri::parse))
        try {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQ_SYSTEM)
        } catch (e: Exception) {
            Toast.makeText(activity, "Sélecteur de sonneries indisponible : choisissez un fichier audio.", Toast.LENGTH_LONG).show()
        }
    }

    private fun pickFile(activity: Activity) {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*")
        try {
            @Suppress("DEPRECATION")
            activity.startActivityForResult(intent, REQ_FILE)
        } catch (e: Exception) {
            Toast.makeText(activity, "Aucun gestionnaire de fichiers disponible.", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Résultat d'un choix de son : (uri, nom), uri null = son par défaut.
     * Retourne null si ce résultat ne concerne pas le choix de son.
     */
    fun parseResult(context: Context, requestCode: Int, resultCode: Int, data: Intent?): Pair<String?, String>? {
        if (requestCode != REQ_SYSTEM && requestCode != REQ_FILE) return null
        if (resultCode != Activity.RESULT_OK || data == null) return null
        return if (requestCode == REQ_SYSTEM) {
            val uri: Uri? = if (Build.VERSION.SDK_INT >= 33) {
                data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                data.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            if (uri == null || RingtoneManager.isDefault(uri)) {
                null to ""
            } else {
                uri.toString() to (runCatching { RingtoneManager.getRingtone(context, uri)?.getTitle(context) }.getOrNull() ?: "")
            }
        } else {
            val uri = data.data ?: return null
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val name = runCatching {
                context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                    if (it.moveToFirst()) it.getString(0) else null
                }
            }.getOrNull() ?: "Fichier audio"
            uri.toString() to name.substringBeforeLast('.')
        }
    }

    fun label(uri: String?, name: String): String = when {
        uri == null -> "Par défaut"
        name.isNotBlank() -> name
        else -> "Personnalisé"
    }
}
