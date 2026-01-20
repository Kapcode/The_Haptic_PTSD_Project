package com.kapcode.thehapticptsdproject

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
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
    val remainingSeconds: Int = 0
)

class HapticManager(private val context: Context) {
    private val _state = MutableStateFlow(HapticState())
    val state = _state.asStateFlow()

    private var sessionJob: Job? = null

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        _state.value = _state.value.copy(sessionDurationSeconds = seconds.coerceIn(10, 600))
    }

    fun startHeartbeatSession() {
        // If already running, just reset the timer (extend session)
        if (_state.value.isHeartbeatRunning) {
            _state.value = _state.value.copy(remainingSeconds = _state.value.sessionDurationSeconds)
            Logger.info("Heartbeat session extended.")
            return
        }

        _state.value = _state.value.copy(
            isHeartbeatRunning = true,
            remainingSeconds = _state.value.sessionDurationSeconds
        )

        sessionJob = CoroutineScope(Dispatchers.Default).launch {
            Logger.info("Soothing heartbeat session started for ${_state.value.sessionDurationSeconds} seconds.")
            
            // Loop for the heartbeat pulses
            val pulseJob = launch {
                while (_state.value.isHeartbeatRunning) {
                    playHeartbeatPulse()
                    val interval = 60000L / _state.value.bpm
                    delay(interval)
                }
            }

            // Countdown timer
            while (_state.value.remainingSeconds > 0 && _state.value.isHeartbeatRunning) {
                delay(1000)
                _state.value = _state.value.copy(remainingSeconds = _state.value.remainingSeconds - 1)
            }

            stopHeartbeatSession()
        }
    }

    fun stopHeartbeatSession() {
        if (!_state.value.isHeartbeatRunning) return
        _state.value = _state.value.copy(isHeartbeatRunning = false, remainingSeconds = 0)
        sessionJob?.cancel()
        vibrator.cancel()
        Logger.info("Heartbeat session finished.")
    }

    fun playSingleHeartbeat() {
        CoroutineScope(Dispatchers.Default).launch {
            playHeartbeatPulse()
        }
    }

    private suspend fun playHeartbeatPulse() {
        val intensity = _state.value.intensity
        val strength1 = (255 * intensity).toInt().coerceIn(1, 255)
        val strength2 = (180 * intensity).toInt().coerceIn(1, 255)

        // Lub-dub pattern
        vibrator.vibrate(VibrationEffect.createOneShot(50, strength1))
        delay(150)
        vibrator.vibrate(VibrationEffect.createOneShot(60, strength2))
    }
}
