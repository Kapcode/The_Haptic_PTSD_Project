package com.kapcode.thehapticptsdproject

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HapticState(
    val intensity: Float = 0.5f,
    val bpm: Int = 60,
    val sessionDurationSeconds: Int = 120, // 2 minutes default
    val isHeartbeatRunning: Boolean = false,
    val remainingSeconds: Int = 0,
    val isTestRunning: Boolean = false,
    val testPulseCount: Int = 0
)

object HapticManager {
    private val _state = MutableStateFlow(HapticState())
    val state = _state.asStateFlow()

    private var sessionJob: Job? = null
    private var testJob: Job? = null
    private var vibrator: Vibrator? = null

    fun init(context: Context) {
        if (vibrator != null) return
        vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
            vibratorManager.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    fun updateIntensity(intensity: Float) {
        _state.value = _state.value.copy(intensity = intensity.coerceIn(0f, 1f))
    }

    fun updateBpm(bpm: Int) {
        _state.value = _state.value.copy(bpm = bpm.coerceIn(30, 200))
    }

    fun updateSessionDuration(seconds: Int) {
        val current = _state.value
        val newDuration = seconds.coerceIn(10, 600)
        if (current.isHeartbeatRunning) {
            _state.value = current.copy(
                sessionDurationSeconds = newDuration,
                remainingSeconds = newDuration
            )
            Logger.debug("Session timer updated live to ${newDuration}s")
        } else {
            _state.value = current.copy(sessionDurationSeconds = newDuration)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun startHeartbeatSession() {
        if (_state.value.isHeartbeatRunning) {
            _state.value = _state.value.copy(remainingSeconds = _state.value.sessionDurationSeconds)
            Logger.info("Heartbeat session extended.")
            return
        }

        _state.value = _state.value.copy(
            isHeartbeatRunning = true,
            remainingSeconds = _state.value.sessionDurationSeconds,
            isTestRunning = false
        )

        sessionJob?.cancel()
        sessionJob = CoroutineScope(Dispatchers.Default).launch {
            Logger.info("Soothing heartbeat session started for ${_state.value.sessionDurationSeconds}s.")
            
            val pulseJob = launch {
                while (_state.value.isHeartbeatRunning) {
                    playHeartbeatPulse()
                    val interval = 60000L / _state.value.bpm
                    delay(interval)
                }
            }

            while (_state.value.remainingSeconds > 0 && _state.value.isHeartbeatRunning) {
                delay(1000)
                if (_state.value.remainingSeconds > 0) {
                    _state.value = _state.value.copy(remainingSeconds = _state.value.remainingSeconds - 1)
                }
            }

            stopHeartbeatSession()
        }
    }

    fun stopHeartbeatSession() {
        if (!_state.value.isHeartbeatRunning) return
        _state.value = _state.value.copy(isHeartbeatRunning = false, remainingSeconds = 0)
        sessionJob?.cancel()
        vibrator?.cancel()
        Logger.info("Heartbeat session finished.")
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun testPulseSequence() {
        if (_state.value.isTestRunning || _state.value.isHeartbeatRunning) return
        
        testJob?.cancel()
        testJob = CoroutineScope(Dispatchers.Default).launch {
            _state.value = _state.value.copy(isTestRunning = true, testPulseCount = 0)
            repeat(3) { i ->
                _state.value = _state.value.copy(testPulseCount = i + 1)
                playHeartbeatPulse()
                val interval = 60000L / _state.value.bpm
                delay(interval)
            }
            _state.value = _state.value.copy(isTestRunning = false)
        }
    }

    /**
     * Plays a generic single pulse. Useful for beat-synchronized playback
     * where the "lub-dub" heartbeat pattern might not be appropriate.
     */
    @RequiresApi(Build.VERSION_CODES.O)
    fun playPulse(intensityOverride: Float? = null, durationMs: Long = 100L) {
        val intensity = intensityOverride ?: _state.value.intensity
        val strength = (255 * intensity).toInt().coerceIn(1, 255)
        
        vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, strength))
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun playSingleHeartbeat() {
        CoroutineScope(Dispatchers.Default).launch {
            playHeartbeatPulse()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun playHeartbeatPulse() {
        val intensity = _state.value.intensity
        val strength1 = (255 * intensity).toInt().coerceIn(1, 255)
        val strength2 = (180 * intensity).toInt().coerceIn(1, 255)

        // Lub-dub pattern
        vibrator?.vibrate(VibrationEffect.createOneShot(50, strength1))
        delay(150)
        vibrator?.vibrate(VibrationEffect.createOneShot(60, strength2))
    }
}
