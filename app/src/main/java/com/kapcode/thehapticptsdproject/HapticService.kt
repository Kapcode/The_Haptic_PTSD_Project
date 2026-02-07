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
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.math.sqrt

class HapticService : Service(), SensorEventListener {

    private lateinit var squeezeDetector: SqueezeDetector
    private lateinit var sensorManager: SensorManager
    private var motionSensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

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
        const val ACTION_UPDATE_MODES = "ACTION_UPDATE_MODES"
        
        const val EXTRA_SQUEEZE_ENABLED = "EXTRA_SQUEEZE_ENABLED"
        const val EXTRA_SHAKE_ENABLED = "EXTRA_SHAKE_ENABLED"
        const val EXTRA_SHAKE_THRESHOLD = "EXTRA_SHAKE_THRESHOLD"
        const val EXTRA_BB_PLAYER_ACTIVE = "EXTRA_BB_PLAYER_ACTIVE"
        const val EXTRA_HEARTBEAT_ACTIVE = "EXTRA_HEARTBEAT_ACTIVE"
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate() {
        super.onCreate()
        HapticManager.init(this)
        SettingsManager.init(this)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        squeezeDetector = SqueezeDetector {
            triggerHaptics("Squeeze")
        }
        squeezeDetector.setSqueezeThreshold(SettingsManager.squeezeThreshold.toDouble())

        createNotificationChannel()

        serviceScope.launch {
            HapticManager.state.collect { state ->
                updateNotification(state)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isSqueezeEnabled = intent.getBooleanExtra(EXTRA_SQUEEZE_ENABLED, false)
                isShakeEnabled = intent.getBooleanExtra(EXTRA_SHAKE_ENABLED, false)
                internalShakeThreshold = intent.getFloatExtra(EXTRA_SHAKE_THRESHOLD, 15f)
                
                acquireWakeLock()
                startDetection()
                updateNotification(HapticManager.state.value)
            }
            ACTION_UPDATE_MODES -> {
                isBBPlayerModeActive = intent.getBooleanExtra(EXTRA_BB_PLAYER_ACTIVE, false)
                isHeartbeatModeActive = intent.getBooleanExtra(EXTRA_HEARTBEAT_ACTIVE, false)
                updateNotification(HapticManager.state.value)
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun updateNotification(state: HapticState) {
        val playerState = BeatDetector.playerState.value
        
        val title = when {
            state.isHeartbeatRunning -> "Heartbeat Active"
            playerState.isPlaying -> "Bilateral Beats Playing"
            else -> "Haptic PTSD Monitoring"
        }
        
        val text = when {
            state.isHeartbeatRunning -> "Soothe Session: ${state.remainingSeconds}s remaining (${state.bpm} BPM)"
            playerState.isPlaying -> "Sync Playing: ${playerState.currentTimestampMs/1000}s / ${playerState.totalDurationMs/1000}s"
            else -> {
                val modes = mutableListOf<String>()
                if (isHeartbeatModeActive) modes.add("Heartbeat")
                if (isBBPlayerModeActive) modes.add("BB Player")
                "Active Modes: ${if (modes.isEmpty()) "Standby" else modes.joinToString(", ")}"
            }
        }

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Haptic PTSD Monitoring",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
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
