package fr.eink.horlogemeteo

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.os.Build

/** Met à jour le widget quand le profil sonore change (tant que l'application est en mémoire). */
class App : Application() {
    override fun onCreate() {
        super.onCreate()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = WidgetRenderer.updateAll(context)
        }
        val filter = IntentFilter(AudioManager.RINGER_MODE_CHANGED_ACTION).apply {
            addAction(android.net.wifi.WifiManager.WIFI_STATE_CHANGED_ACTION)
            addAction(android.bluetooth.BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, filter)
        }
        // Canal créé dès le démarrage : Android demande alors l'autorisation des notifications
        // à la première ouverture de l'appli (nécessaire pour l'écran d'alarme).
        AlarmService.ensureChannel(this)
    }
}
