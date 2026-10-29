package br.srmatta.treino

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.TimeUnit

/** Mantém o descanso ativo e dispara alerta sonoro e vibração ao terminar. */
class RestTimerService : Service() {
    companion object {
        const val ACTION_START = "br.srmatta.treino.action.START_REST"
        const val ACTION_STOP = "br.srmatta.treino.action.STOP_REST"
        const val EXTRA_END_AT = "rest_end_at"
        // ID novo: o Android não atualiza as configurações de canais já criados.
        private const val CHANNEL_ID = "rest_timer_alerts_v2"
        private const val NOTIFICATION_ID = 410
        private const val FINISHED_NOTIFICATION_ID = 411
    }

    private val handler = Handler(Looper.getMainLooper())
    private var endAt = 0L
    private var alarm: Ringtone? = null
    private val ticker = object : Runnable {
        override fun run() {
            val left = endAt - System.currentTimeMillis()
            if (left <= 0L) {
                finishTimer()
            } else {
                updateNotification(left)
                handler.postDelayed(this, 1000L)
            }
        }
    }

    private val prefs by lazy { getSharedPreferences("srmatta", Context.MODE_PRIVATE) }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTimer()
            return START_NOT_STICKY
        }
        endAt = if (intent?.action == ACTION_START) {
            intent.getLongExtra(EXTRA_END_AT, 0L)
        } else {
            prefs.getLong("rest_end_wall", 0L)
        }
        if (endAt <= System.currentTimeMillis()) {
            finishTimer()
            return START_NOT_STICKY
        }
        prefs.edit().putLong("rest_end_wall", endAt).putBoolean("rest_done", false).apply()
        startForeground(NOTIFICATION_ID, buildNotification(endAt - System.currentTimeMillis(), false))
        handler.removeCallbacks(ticker)
        handler.post(ticker)
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val audio = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            val channel = NotificationChannel(
                CHANNEL_ID, "Alarme do descanso", NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Som e vibração quando o descanso entre séries terminar"
                setSound(alarmUri, audio)
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 350, 160, 350, 160, 600)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(left: Long, finished: Boolean): Notification {
        val open = Intent(this, MainActivity::class.java)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or
            (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val pending = PendingIntent.getActivity(this, 410, open, flags)
        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
                .setPriority(if (finished) Notification.PRIORITY_MAX else Notification.PRIORITY_LOW)
                .setDefaults(if (finished) Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE else 0)
        }
        return builder
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(if (finished) "Descanso concluído!" else "Descanso do treino")
            .setContentText(if (finished) "Hora da próxima série." else "Próxima série em ${formatTime(left)}")
            .setContentIntent(pending)
            .setCategory(if (finished) Notification.CATEGORY_ALARM else Notification.CATEGORY_STOPWATCH)
            .setOngoing(!finished)
            .setAutoCancel(finished)
            .setOnlyAlertOnce(!finished)
            .build()
    }

    private fun updateNotification(left: Long) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(left, false))
    }

    private fun formatTime(ms: Long): String {
        val seconds = TimeUnit.MILLISECONDS.toSeconds(ms).coerceAtLeast(0L)
        return String.format("%02d:%02d", seconds / 60L, seconds % 60L)
    }

    private fun finishTimer() {
        handler.removeCallbacks(ticker)
        prefs.edit().putLong("rest_end_wall", 0L).putBoolean("rest_done", true).apply()
        // Notificação de alta prioridade usa o canal novo com som e vibração habilitados.
        getSystemService(NotificationManager::class.java)
            .notify(FINISHED_NOTIFICATION_ID, buildNotification(0L, true))
        vibrate()
        playAlarm()
        stopForeground(true)
        stopSelf()
    }

    private fun stopTimer() {
        handler.removeCallbacks(ticker)
        alarm?.stop()
        alarm = null
        prefs.edit().putLong("rest_end_wall", 0L).putBoolean("rest_done", false).apply()
        stopForeground(true)
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun vibrate() {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(VibratorManager::class.java).defaultVibrator
            } else {
                getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (vibrator.hasVibrator()) {
                if (Build.VERSION.SDK_INT >= 26) {
                    vibrator.vibrate(VibrationEffect.createWaveform(
                        longArrayOf(0, 350, 160, 350, 160, 600), -1
                    ))
                } else {
                    vibrator.vibrate(longArrayOf(0, 350, 160, 350, 160, 600), -1)
                }
            }
        } catch (_: Exception) { }
    }

    private fun playAlarm() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            if (uri != null) {
                alarm = RingtoneManager.getRingtone(applicationContext, uri)?.apply {
                    if (Build.VERSION.SDK_INT >= 21) {
                        audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    }
                    play()
                }
                handler.postDelayed({
                    alarm?.stop()
                    alarm = null
                }, 10000L)
            }
        } catch (_: Exception) { }
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
