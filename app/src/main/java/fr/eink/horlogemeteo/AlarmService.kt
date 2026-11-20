package fr.eink.horlogemeteo

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator

/** Service au premier plan qui fait sonner l'alarme (son en boucle + vibration). */
class AlarmService : Service() {

    private var player: MediaPlayer? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val handler = Handler(Looper.getMainLooper())
    private val autoStop = Runnable { stopRinging() }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RING -> {
                val id = intent.getIntExtra(AlarmReceiver.EXTRA_ID, -1)
                val alarm = if (id == AlarmScheduler.TIMER_ID) TimerState(this).asAlarm() else AlarmStore(this).get(id)
                if (alarm == null) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                ring(alarm)
            }
            ACTION_SNOOZE -> {
                if (ringingId >= 0 && ringingId != AlarmScheduler.TIMER_ID) AlarmScheduler.snooze(this, ringingId) // (pas de répétition pour le minuteur)
                stopRinging()
            }
            ACTION_STOP -> stopRinging()
            else -> stopRinging()
        }
        return START_NOT_STICKY
    }

    private fun ring(alarm: Alarm) {
        releaseMedia()
        ringingId = alarm.id
        ringingLabel = alarm.label

        val notification = buildNotification(alarm)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else {
            startForeground(NOTIF_ID, notification)
        }

        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HorlogeMeteo:alarm")
            .apply { acquire(AUTO_STOP_MS + 5_000) }

        player = startSound(this, alarm.soundUri?.let(Uri::parse), loop = true)
        if (alarm.vibrate) {
            vibrator = getSystemService(Vibrator::class.java)?.also {
                it.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 800, 800), 0))
            }
        }
        // Ouvre l'écran d'alarme si possible (sinon la notification plein écran s'en charge).
        runCatching { startActivity(ringIntent(this).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }

        handler.removeCallbacks(autoStop)
        handler.postDelayed(autoStop, AUTO_STOP_MS)
    }

    private fun stopRinging() {
        handler.removeCallbacks(autoStop)
        releaseMedia()
        ringingId = NONE
        sendBroadcast(Intent(ACTION_FINISHED).setPackage(packageName))
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun releaseMedia() {
        player?.runCatching { stop(); release() }
        player = null
        vibrator?.cancel()
        vibrator = null
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    override fun onDestroy() {
        handler.removeCallbacks(autoStop)
        releaseMedia()
        ringingId = NONE
        super.onDestroy()
    }

    private fun buildNotification(alarm: Alarm): Notification {
        ensureChannel(this)
        val fullScreen = PendingIntent.getActivity(
            this, 10, ringIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val isTimer = alarm.id == AlarmScheduler.TIMER_ID
        val builder = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(if (isTimer) "Minuteur" else "Alarme ${alarm.timeText}")
            .setContentText(alarm.label.ifBlank { "Réveil" })
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
        if (!isTimer) {
            builder.addAction(Notification.Action.Builder(null, "Répéter ($SNOOZE_LABEL)", serviceIntent(this, ACTION_SNOOZE, 11)).build())
        }
        return builder
            .addAction(Notification.Action.Builder(null, "Arrêter", serviceIntent(this, ACTION_STOP, 12)).build())
            .build()
    }

    companion object {
        const val ACTION_RING = "fr.eink.horlogemeteo.RING"
        const val ACTION_STOP = "fr.eink.horlogemeteo.STOP"
        const val ACTION_SNOOZE = "fr.eink.horlogemeteo.SNOOZE"
        const val ACTION_FINISHED = "fr.eink.horlogemeteo.ALARM_FINISHED"
        private const val CHANNEL = "alarms"
        private const val NOTIF_ID = 7
        private const val AUTO_STOP_MS = 10 * 60_000L
        private val SNOOZE_LABEL = "${AlarmScheduler.SNOOZE_MINUTES} min"

        /** Valeur de [ringingId] quand rien ne sonne. */
        const val NONE = Int.MIN_VALUE

        /** Alarme en train de sonner ([NONE] = aucune, [AlarmScheduler.TIMER_ID] = minuteur). */
        @Volatile var ringingId: Int = NONE
            private set
        @Volatile var ringingLabel: String = ""
            private set

        fun ensureChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            if (nm.getNotificationChannel(CHANNEL) == null) {
                nm.createNotificationChannel(
                    NotificationChannel(CHANNEL, "Alarmes", NotificationManager.IMPORTANCE_HIGH).apply {
                        setSound(null, null) // le son est joué par le service
                        enableVibration(false)
                        lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    },
                )
            }
        }

        fun ringIntent(context: Context) = Intent(context, AlarmRingActivity::class.java)

        fun serviceIntent(context: Context, action: String, code: Int): PendingIntent =
            PendingIntent.getService(
                context, code, Intent(context, AlarmService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        fun send(context: Context, action: String) {
            context.startService(Intent(context, AlarmService::class.java).setAction(action))
        }

        /** Joue un son d'alarme (fichier choisi, sinon son par défaut). Utilisé aussi pour l'essai. */
        fun startSound(context: Context, uri: Uri?, loop: Boolean): MediaPlayer? {
            val candidates = listOfNotNull(
                uri,
                RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
            )
            for (u in candidates) {
                val mp = MediaPlayer()
                try {
                    mp.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build(),
                    )
                    mp.setDataSource(context, u)
                    mp.isLooping = loop
                    mp.prepare()
                    mp.start()
                    return mp
                } catch (e: Exception) {
                    mp.release()
                }
            }
            return null
        }
    }
}
