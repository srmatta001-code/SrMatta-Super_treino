package br.srmatta.treino

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.media.session.MediaSession
import android.media.MediaMetadata
import android.media.session.PlaybackState
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.pm.ServiceInfo

/** Cronômetros persistentes: continuam ativos com o app minimizado ou tela bloqueada. */
class WorkoutTimerService : Service() {
    companion object {
        const val ACTION_START = "br.srmatta.treino.START_TIMER"
        const val ACTION_REST = "br.srmatta.treino.START_REST"
        const val ACTION_ADJUST_REST = "br.srmatta.treino.ADJUST_REST"
        const val ACTION_STOP_REST = "br.srmatta.treino.STOP_REST"
        const val ACTION_STOP = "br.srmatta.treino.STOP_TIMER"
        const val EXTRA_SESSION_START = "session_start"
        const val EXTRA_VALUE = "value"
        const val PREF_REST_END = "rest_end_elapsed"
        const val PREF_REST_DONE = "rest_done"
        private const val CHANNEL_TIMER = "workout_timer"
        private const val CHANNEL_ALERT = "workout_alert_v2"
        private const val NOTIFICATION_ID = 4810
        private const val ALERT_ID = 4811
    }

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var manager: NotificationManager
    private lateinit var mediaSession: MediaSession
    private val prefs by lazy { getSharedPreferences("srmatta", Context.MODE_PRIVATE) }
    private var sessionStart = 0L

    private val ticker = object : Runnable {
        override fun run() {
            if (sessionStart <= 0L) {
                stopSelf()
                return
            }
            checkRestAlarm()
            updateNotification()
            handler.postDelayed(this, 1000L)
        }
    }

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createChannels()
        mediaSession = MediaSession(this, "SrMattaWorkout").apply {
            setFlags(MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS)
            isActive = true
            setMetadata(MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, "SrMatta Super Treino")
                .putString(MediaMetadata.METADATA_KEY_ARTIST, "Cronômetros do treino")
                .build())
            setPlaybackState(PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_STOP)
                .setState(PlaybackState.STATE_PLAYING, 0L, 1f)
                .build())
            setCallback(object : MediaSession.Callback() {
                override fun onStop() { stopWorkout() }
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                sessionStart = intent.getLongExtra(EXTRA_SESSION_START, System.currentTimeMillis())
                prefs.edit().putLong("workout_session_start", sessionStart).apply()
                enterForeground()
                restartTicker()
            }
            ACTION_REST -> {
                val seconds = intent.getIntExtra(EXTRA_VALUE, 90).coerceAtLeast(1)
                prefs.edit().putLong(PREF_REST_END, SystemClock.elapsedRealtime() + seconds * 1000L)
                    .putBoolean(PREF_REST_DONE, false).apply()
                ensureForeground()
            }
            ACTION_ADJUST_REST -> {
                val delta = intent.getIntExtra(EXTRA_VALUE, 0)
                val old = prefs.getLong(PREF_REST_END, 0L)
                if (old > 0L) prefs.edit().putLong(PREF_REST_END,
                    (old + delta * 1000L).coerceAtLeast(SystemClock.elapsedRealtime())).apply()
                ensureForeground()
            }
            ACTION_STOP_REST -> {
                prefs.edit().putLong(PREF_REST_END, 0L).putBoolean(PREF_REST_DONE, false).apply()
                manager.cancel(ALERT_ID)
                updateNotification()
            }
            ACTION_STOP -> stopWorkout()
            else -> {
                sessionStart = prefs.getLong("workout_session_start", 0L)
                if (sessionStart > 0L) {
                    enterForeground()
                    restartTicker()
                } else stopSelf()
            }
        }
        return START_STICKY
    }

    private fun restartTicker() {
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun ensureForeground() {
        if (sessionStart <= 0L) sessionStart = prefs.getLong("workout_session_start", 0L)
        if (sessionStart > 0L) {
            enterForeground()
            restartTicker()
        }
    }

    private fun enterForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(NOTIFICATION_ID, n)
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= 26) {
            val timer = NotificationChannel(CHANNEL_TIMER, "Treino em andamento", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Cronômetros persistentes do treino"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setShowBadge(false)
            }
            val alert = NotificationChannel(CHANNEL_ALERT, "Alarme de descanso", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Aviso sonoro, vibração e popup ao terminar o descanso"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 250, 500, 250, 700)
                val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                setSound(sound, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            }
            manager.createNotificationChannel(timer)
            manager.createNotificationChannel(alert)
        }
    }

    private fun buildNotification(): Notification {
        val now = System.currentTimeMillis()
        val end = prefs.getLong(PREF_REST_END, 0L)
        val remain = if (end > SystemClock.elapsedRealtime()) ((end - SystemClock.elapsedRealtime() + 999) / 1000).toInt() else 0
        val elapsed = ((now - sessionStart) / 1000).toInt().coerceAtLeast(0)
        val line = if (remain > 0) "Descanso: ${clock(remain)}  •  Treino: ${clock(elapsed)}"
            else "Treino: ${clock(elapsed)}  •  Descanso: —"
        val open = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPending = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val stop = Intent(this, WorkoutTimerService::class.java).setAction(ACTION_STOP)
        val stopPending = PendingIntent.getService(this, 2, stop, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_TIMER) else Notification.Builder(this)
        return b.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("SrMatta Super Treino")
            .setContentText(line)
            .setSubText("Cronômetros ativos • toque para abrir")
            .setContentIntent(openPending)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setWhen(sessionStart)
            .setUsesChronometer(true)
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0))
            .addAction(Notification.Action.Builder(android.R.drawable.ic_menu_close_clear_cancel, "Encerrar", stopPending).build())
            .build()
    }

    private fun updateNotification() {
        if (sessionStart > 0L) manager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun checkRestAlarm() {
        val end = prefs.getLong(PREF_REST_END, 0L)
        if (end > 0L && SystemClock.elapsedRealtime() >= end) {
            prefs.edit().putLong(PREF_REST_END, 0L).putBoolean(PREF_REST_DONE, true).apply()
            val open = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(this, 3, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val b = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ALERT) else Notification.Builder(this)
            val alert = b.setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                .setContentTitle("Descanso concluído!")
                .setContentText("Hora da próxima série. Toque para voltar ao treino.")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setPriority(Notification.PRIORITY_MAX)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .build()
            manager.notify(ALERT_ID, alert)
            try {
                val vibrator = getSystemService(VIBRATOR_SERVICE) as Vibrator
                val pattern = longArrayOf(0, 500, 250, 500, 250, 700)
                if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
                else vibrator.vibrate(pattern, -1)
            } catch (_: Exception) { }
        }
    }

    private fun clock(value: Int): String {
        val n = value.coerceAtLeast(0)
        val h = n / 3600
        val m = (n % 3600) / 60
        val s = n % 60
        return if (h > 0) String.format("%02d:%02d:%02d", h, m, s) else String.format("%02d:%02d", m, s)
    }

    private fun stopWorkout() {
        handler.removeCallbacks(ticker)
        prefs.edit().remove("workout_session_start").putLong(PREF_REST_END, 0L).putBoolean(PREF_REST_DONE, false).apply()
        manager.cancel(ALERT_ID)
        if (::mediaSession.isInitialized) mediaSession.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_STOPPED, 0L, 0f).build())
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        handler.removeCallbacks(ticker)
        if (::mediaSession.isInitialized) {
            mediaSession.isActive = false
            mediaSession.release()
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
