package com.kapcode.thehapticptsdproject

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlin.math.sqrt

class HapticService : Service(), SensorEventListener {

    private lateinit var hapticManager: HapticManager
    private lateinit var squeezeDetector: SqueezeDetector
    private lateinit var sensorManager: SensorManager
    private var motionSensor: Sensor? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private var isSqueezeEnabled = false
    private var isShakeEnabled = false
    private var internalShakeThreshold = 15f

    companion object {
        private const val CHANNEL_ID = "HapticServiceChannel"
        private const val NOTIFICATION_ID = 1
        
        const val ACTION_START = "ACTION_START"
        const val ACTION_STOP = "ACTION_STOP"
        
        const val EXTRA_SQUEEZE_ENABLED = "EXTRA_SQUEEZE_ENABLED"
        const val EXTRA_SHAKE_ENABLED = "EXTRA_SHAKE_ENABLED"
        const val EXTRA_SHAKE_THRESHOLD = "EXTRA_SHAKE_THRESHOLD"
    }

    override fun onCreate() {
        super.onCreate()
        hapticManager = HapticManager(this)
        
        // Load persisted settings for haptics
        SettingsManager.init(this)
        hapticManager.updateIntensity(SettingsManager.intensity)
        hapticManager.updateBpm(SettingsManager.bpm)
        hapticManager.updateSessionDuration(SettingsManager.sessionDurationSeconds)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        motionSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)

        squeezeDetector = SqueezeDetector {
            triggerHeartbeat("Squeeze")
        }
        squeezeDetector.setSqueezeThreshold(SettingsManager.squeezeThreshold.toDouble())

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                isSqueezeEnabled = intent.getBooleanExtra(EXTRA_SQUEEZE_ENABLED, false)
                isShakeEnabled = intent.getBooleanExtra(EXTRA_SHAKE_ENABLED, false)
                internalShakeThreshold = intent.getFloatExtra(EXTRA_SHAKE_THRESHOLD, 15f)
                
                startForegroundService()
                acquireWakeLock()
                startDetection()
            }
            ACTION_STOP -> {
                stopSelf()
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Active Heartbeat Running")
            .setContentText("Monitoring for squeeze and wrist snaps...")
            .setSmallIcon(R.mipmap.ic_launcher) // Ensure you have an icon
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HapticProject::DetectionWakeLock")
        wakeLock?.acquire(10 * 60 * 1000L /*10 minutes max default, though START_STICKY helps*/)
    }

    private fun startDetection() {
        if (isSqueezeEnabled) {
            squeezeDetector.start()
        } else {
            squeezeDetector.stop()
        }

        if (isShakeEnabled) {
            sensorManager.registerListener(this, motionSensor, SensorManager.SENSOR_DELAY_GAME)
        } else {
            sensorManager.unregisterListener(this)
        }
    }

    private fun triggerHeartbeat(source: String) {
        Logger.info("$source detected in background! Starting soothing session.")
        hapticManager.startHeartbeatSession()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (isShakeEnabled && event?.sensor?.type == Sensor.TYPE_LINEAR_ACCELERATION) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = sqrt(x * x + y * y + z * z)
            if (magnitude > internalShakeThreshold) {
                Logger.debug("Wrist snap detected in background!")
                triggerHeartbeat("Motion")
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Haptic PTSD Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        squeezeDetector.stop()
        sensorManager.unregisterListener(this)
        hapticManager.stopHeartbeatSession()
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        Logger.info("Background service stopped.")
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
