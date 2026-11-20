package fr.eink.horlogemeteo

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.net.wifi.WifiManager

/**
 * Wi-Fi et Bluetooth : lecture de l'état et bascule directe.
 * La bascule directe n'est autorisée par Android que pour les applis ciblant une ancienne version
 * (targetSdk 28 pour le Wi-Fi, < 33 pour le Bluetooth) : c'est pourquoi l'appli cible Android 9.
 */
object Radios {

    private fun wifi(context: Context) =
        context.applicationContext.getSystemService(WifiManager::class.java)

    private fun bluetooth(context: Context): BluetoothAdapter? =
        context.getSystemService(BluetoothManager::class.java)?.adapter

    /** Wi-Fi activé (ou en cours d'activation). */
    fun isWifiOn(context: Context): Boolean = runCatching {
        when (wifi(context)?.wifiState) {
            WifiManager.WIFI_STATE_ENABLED, WifiManager.WIFI_STATE_ENABLING -> true
            else -> false
        }
    }.getOrDefault(false)

    /** Bluetooth activé (ou en cours d'activation). */
    fun isBluetoothOn(context: Context): Boolean = runCatching {
        when (bluetooth(context)?.state) {
            BluetoothAdapter.STATE_ON, BluetoothAdapter.STATE_TURNING_ON -> true
            else -> false
        }
    }.getOrDefault(false)

    fun hasBluetooth(context: Context): Boolean = bluetooth(context) != null

    /** Inverse l'état du Wi-Fi. Retourne le nouvel état demandé, ou null en cas d'échec. */
    @Suppress("DEPRECATION")
    fun toggleWifi(context: Context): Boolean? {
        val wm = wifi(context) ?: return null
        val target = !isWifiOn(context)
        return runCatching { if (wm.setWifiEnabled(target)) target else null }.getOrNull()
    }

    /** Inverse l'état du Bluetooth. Retourne le nouvel état demandé, ou null en cas d'échec. */
    @Suppress("DEPRECATION", "MissingPermission")
    fun toggleBluetooth(context: Context): Boolean? {
        val bt = bluetooth(context) ?: return null
        val target = !isBluetoothOn(context)
        return runCatching { if (if (target) bt.enable() else bt.disable()) target else null }.getOrNull()
    }
}
