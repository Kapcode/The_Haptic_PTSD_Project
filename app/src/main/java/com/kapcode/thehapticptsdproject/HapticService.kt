package com.kapcode.thehapticptsdproject

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import kotlin.math.sqrt
import com.kapcode.thehapticptsdproject.Logger

/**
 * A foreground service responsible for managing all background operations.
 *
 * This service handles:
 * - Listening for sensor events (squeeze, shake) to trigger therapeutic modes.
 * - Holding a `PARTIAL_WAKE_LOCK` to ensure operations continue when the screen is off.
 * - Managing the persistent notification, which displays real-time state and media controls.
 * - Receiving intents to control the `BeatDetector` (play, pause, skip, stop).
 * - Collecting state from `HapticManager` and `BeatDetector` to keep the notification UI updated.
 */
class HapticService : Service(), SensorEventListener {

    private val squeezeDetector = SqueezeManager.detector
    private lateinit var sensorManager: SensorManager
    private var motionSensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var visualizerRefreshJob: Job? = null
    private var vibrator: Vibrator? = null

    private var isSqueezeEnabled = false
    private var isShakeEnabled = false
    private var internalShakeThreshold = 15f
    
    private var isBBPlayerModeActive = false
    private var isHeartbeatModeActive = false

    companion object {
        private const val CHANNEL_ID = "HapticServiceChannel"
        private const val NOTIFICATION_ID = 1
        
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        const val ACTION_PAUSE = "ACTION_PAUSE"
        const val ACTION_PLAY = "ACTION_PLAY"
        const val ACTION_UPDATE_MODES = "ACTION_UPDATE_MODES"
        const val ACTION_VIBRATE = "ACTION_VIBRATE"
        const val ACTION_CANCEL_VIBRATION = "ACTION_CANCEL_VIBRATION"
        
        const val ACTION_SKIP_BWD_30 = "ACTION_SKIP_BWD_30"
        const val ACTION_SKIP_BWD_5 = "ACTION_SKIP_BWD_5"
        const val ACTION_SKIP_FWD_5 = "ACTION_SKIP_FWD_5"
        const val ACTION_SKIP_FWD_30 = "ACTION_SKIP_FWD_30"
        
        const val EXTRA_SQUEEZE_ENABLED = "EXTRA_SQUEEZE_ENABLED"
        const val EXTRA_SHAKE_ENABLED = "EXTRA_SHAKE_ENABLED"
        const val EXTRA_SHAKE_THRESHOLD = "EXTRA_SHAKE_THRESHOLD"
        const val EXTRA_BB_PLAYER_ACTIVE = "EXTRA_BB_PLAYER_ACTIVE"
        const val EXTRA_HEARTBEAT_ACTIVE = "EXTRA_HEARTBEAT_ACTIVE"
        const val EXTRA_DURATION = "EXTRA_DURATION"
        const val EXTRA_AMPLITUDE = "EXTRA_AMPLITUDE"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        SettingsManager.init(this)
        HapticManager.init(this)

        val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
        vibrator = vibratorManager.defaultVibrator

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        SqueezeManager.setOnSqueezeListener {
            triggerHaptics("Squeeze")
        }
        squeezeDetector.setSqueezeThreshold(SettingsManager.squeezeThreshold.toDouble())

        createNotificationChannel()

        serviceScope.launch {
            combine(HapticManager.state, BeatDetector.playerState) { hState, bState ->
                hState to bState
            }.collect { (hState, bState) ->
                updateNotification(hState, bState)
                
                val anyActive = hState.isHeartbeatRunning || bState.isPlaying || hState.isTestRunning || 
                                hState.phoneLeftIntensity > 0 || hState.phoneRightIntensity > 0 ||
                                hState.controllerLeftTopIntensity > 0 || hState.controllerLeftBottomIntensity > 0 ||
                                hState.controllerRightTopIntensity > 0 || hState.controllerRightBottomIntensity > 0 ||
                                hState.visualizerData.any { it > 0 }

                if (anyActive && visualizerRefreshJob == null) {
                    startVisualizerRefreshLoop()
                } else if (!anyActive) {
                    visualizerRefreshJob?.cancel()
                    visualizerRefreshJob = null
                }
            }
        }
    }

    private fun startVisualizerRefreshLoop() {
        visualizerRefreshJob = serviceScope.launch {
            while (isActive) {
                updateNotification(HapticManager.state.value, BeatDetector.playerState.value)
                delay(50) // High frequency for smooth animation
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isSqueezeEnabled = intent.getBooleanExtra(EXTRA_SQUEEZE_ENABLED, false)
                isShakeEnabled = intent.getBooleanExtra(EXTRA_SHAKE_ENABLED, false)
                internalShakeThreshold = intent.getFloatExtra(EXTRA_SHAKE_THRESHOLD, 15f)
                
                acquireWakeLock()
                startDetection()
                updateNotification(HapticManager.state.value, BeatDetector.playerState.value)
            }
            ACTION_UPDATE_MODES -> {
                isBBPlayerModeActive = intent.getBooleanExtra(EXTRA_BB_PLAYER_ACTIVE, false)
                isHeartbeatModeActive = intent.getBooleanExtra(EXTRA_HEARTBEAT_ACTIVE, false)
                updateNotification(HapticManager.state.value, BeatDetector.playerState.value)
            }
            ACTION_PAUSE -> {
                BeatDetector.pausePlayback()
                updateNotification(HapticManager.state.value, BeatDetector.playerState.value)
            }
            ACTION_PLAY -> {
                BeatDetector.resumePlayback()
                updateNotification(HapticManager.state.value, BeatDetector.playerState.value)
            }
            ACTION_SKIP_BWD_30 -> BeatDetector.skipBackward(30)
            ACTION_SKIP_BWD_5 -> BeatDetector.skipBackward(5)
            ACTION_SKIP_FWD_5 -> BeatDetector.skipForward(5)
            ACTION_SKIP_FWD_30 -> BeatDetector.skipForward(30)
            ACTION_STOP -> {
                if (BeatDetector.playerState.value.isPlaying || BeatDetector.playerState.value.isPaused) {
                    BeatDetector.stopPlayback()
                } else {
                    stopSelf()
                }
            }
            ACTION_VIBRATE -> {
                val duration = intent.getLongExtra(EXTRA_DURATION, 100L)
                val amplitude = intent.getIntExtra(EXTRA_AMPLITUDE, 255)
                playVibration(duration, amplitude)
            }
            ACTION_CANCEL_VIBRATION -> {
                if (vibrator?.hasVibrator() == true) {
                    vibrator?.cancel()
                }
            }
        }
        return START_STICKY
    }
    
    @RequiresApi(Build.VERSION_CODES.O)
    private fun playVibration(durationMs: Long, amplitude: Int) {
        if (vibrator?.hasVibrator() == true) {
            vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, amplitude))
        } else {
            //Logger.warn("Vibrator not available on this device.")
        }
    }

    private fun updateNotification(hState: HapticState, bState: BeatPlayerState) {
        val title = when {
            hState.isHeartbeatRunning -> "Heartbeat Active"
            bState.isPlaying || bState.isPaused -> "Bilateral Beats Player"
            else -> "Haptic PTSD Monitoring"
        }
        
        val text = when {
            hState.isHeartbeatRunning -> "Soothe Session: ${hState.remainingSeconds}s remaining"
            bState.isPlaying || bState.isPaused -> {
                val cur = bState.currentTimestampMs / 1000
                val tot = bState.totalDurationMs / 1000
                "Sync: ${cur/60}:${(cur%60).toString().padStart(2, '0')} / ${tot/60}:${(tot%60).toString().padStart(2, '0')}"
            }
            else -> {
                val modes = mutableListOf<String>()
                if (isHeartbeatModeActive) modes.add("Heartbeat")
                if (isBBPlayerModeActive) modes.add("BB Player")
                "Active Modes: ${if (modes.isEmpty()) "Standby" else modes.joinToString(", ")}"
            }
        }

        val remoteViews = RemoteViews(packageName, R.layout.notification_haptic_visualizer).apply {
            setTextViewText(R.id.notification_title, title)
            setTextViewText(R.id.notification_text, text)
            
            val minAlpha = 40 
            val maxAlpha = 255
            fun getAlpha(intensity: Float) = (minAlpha + (intensity * (maxAlpha - minAlpha))).toInt()

            setInt(R.id.img_phone_left, "setAlpha", getAlpha(hState.phoneLeftIntensity))
            setInt(R.id.img_phone_right, "setAlpha", getAlpha(hState.phoneRightIntensity))
            setInt(R.id.img_controller_left_top, "setAlpha", getAlpha(hState.controllerLeftTopIntensity))
            setInt(R.id.img_controller_left_bottom, "setAlpha", getAlpha(hState.controllerLeftBottomIntensity))
            setInt(R.id.img_controller_right_top, "setAlpha", getAlpha(hState.controllerRightTopIntensity))
            setInt(R.id.img_controller_right_bottom, "setAlpha", getAlpha(hState.controllerRightBottomIntensity))

            // Visualizer handling
            val barsVisible = SettingsManager.isBarsEnabled
            val intensityVisible = SettingsManager.isChannelIntensityEnabled
            val waveformVisible = SettingsManager.isWaveformEnabled

            setViewVisibility(R.id.layout_bars, if (barsVisible) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.layout_intensity, if (intensityVisible) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.layout_waveform, if (waveformVisible) View.VISIBLE else View.GONE)

            if (barsVisible) {
                val bars = arrayOf(R.id.bar1, R.id.bar2, R.id.bar3, R.id.bar4, R.id.bar5, R.id.bar6, R.id.bar7, R.id.bar8)
                for (i in bars.indices) {
                    val progress = if (i < hState.visualizerData.size) (hState.visualizerData[i] * 100).toInt() else 0
                    setProgressBar(bars[i], 100, progress.coerceIn(0, 100), false)
                }
            }
            
            if (intensityVisible) {
                val leftIntensity = if (hState.visualizerData.isNotEmpty()) hState.visualizerData[0] else 0f
                val rightIntensity = if (hState.visualizerData.size > 1) hState.visualizerData[1] else leftIntensity
                setProgressBar(R.id.progress_left, 100, (leftIntensity * 100).toInt().coerceIn(0, 100), false)
                setProgressBar(R.id.progress_right, 100, (rightIntensity * 100).toInt().coerceIn(0, 100), false)
            }
            
            if (waveformVisible) {
                val overallIntensity = hState.visualizerData.average().toFloat()
                setInt(R.id.waveform_line, "setAlpha", getAlpha(overallIntensity))
            }

            if (bState.isPlaying || bState.isPaused) {
                setViewVisibility(R.id.player_controls, View.VISIBLE)
                setProgressBar(R.id.player_progress, bState.totalDurationMs.toInt(), bState.currentTimestampMs.toInt(), false)
                
                val ppIcon = if (bState.isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
                val ppAction = if (bState.isPlaying) ACTION_PAUSE else ACTION_PLAY
                setImageViewResource(R.id.btn_play_pause, ppIcon)
                setOnClickPendingIntent(R.id.btn_play_pause, createPendingAction(ppAction))
                setImageViewResource(R.id.btn_stop, android.R.drawable.ic_menu_close_clear_cancel)
                setOnClickPendingIntent(R.id.btn_stop, createPendingAction(ACTION_STOP))
                
                setOnClickPendingIntent(R.id.btn_replay_30, createPendingAction(ACTION_SKIP_BWD_30))
                setOnClickPendingIntent(R.id.btn_replay_5, createPendingAction(ACTION_SKIP_BWD_5))
                setOnClickPendingIntent(R.id.btn_forward_5, createPendingAction(ACTION_SKIP_FWD_5))
                setOnClickPendingIntent(R.id.btn_forward_30, createPendingAction(ACTION_SKIP_FWD_30))
            } else {
                setViewVisibility(R.id.player_controls, View.GONE)
            }
        }

        val mainIntent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        val pendingIntent = PendingIntent.getActivity(this, 0, mainIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

        val notificationBuilder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setCustomContentView(remoteViews)
            .setCustomBigContentView(remoteViews)
            .setStyle(NotificationCompat.DecoratedCustomViewStyle())
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setVibrate(longArrayOf(0L)) // Explicitly disable vibration on the notification itself

        notificationBuilder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)

        startForeground(NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun createPendingAction(action: String): PendingIntent {
        val intent = Intent(this, HapticService::class.java).apply { this.action = action }
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HapticProject::DetectionWakeLock")
        }
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(2 * 60 * 60 * 1000L)
        }
    }

    private fun startDetection() {
        if (isSqueezeEnabled) squeezeDetector.start() else squeezeDetector.stop()
        if (isShakeEnabled) {
            sensorManager.registerListener(this, motionSensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            sensorManager.unregisterListener(this)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun triggerHaptics(source: String) {
        val playerState = BeatDetector.playerState.value
        if (playerState.isPlaying || playerState.isPaused) {
            return // Don't trigger any haptics if BB Player is active
        }

        if (isHeartbeatModeActive) {
            Logger.info("$source detected! Starting heartbeat.")
            HapticManager.startHeartbeatSession()
        } else if (isBBPlayerModeActive) {
            Logger.info("$source detected! Starting BB Player.")
            BeatDetector.playSynchronized(this)
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (isShakeEnabled && event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = sqrt(x * x + y * y + z * z)
            if (magnitude > internalShakeThreshold) {
                Logger.debug("Wrist snap detected!")
                triggerHaptics("Motion")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            CHANNEL_ID,
            "Haptic PTSD Monitoring",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableVibration(false)
            vibrationPattern = longArrayOf(0L)
        }
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(serviceChannel)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        squeezeDetector.stop()
        sensorManager.unregisterListener(this)
        HapticManager.stopHeartbeatSession()
        BeatDetector.stopPlayback()
        wakeLock?.let { if (it.isHeld) it.release() }
        Logger.info("Background service stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
