package com.kapcode.thehapticptsdproject

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HapticState(
    val intensity: Float = 0.5f,
    val bpm: Int = 60,
    val sessionDurationSeconds: Int = 120,
    val isHeartbeatRunning: Boolean = false,
    val remainingSeconds: Int = 0,
    val isTestRunning: Boolean = false,
    val testPulseCount: Int = 0,
    
    // Configurable delays
    val leadInMs: Int = 10,
    val leadOutMs: Int = 10,
    
    // Live motor intensities for visualizer (0.0 to 1.0)
    val phoneLeftIntensity: Float = 0f,
    val phoneRightIntensity: Float = 0f,
    val controllerLeftTopIntensity: Float = 0f,
    val controllerLeftBottomIntensity: Float = 0f,
    val controllerRightTopIntensity: Float = 0f,
    val controllerRightBottomIntensity: Float = 0f
)

object HapticManager {
    private val _state = MutableStateFlow(HapticState())
    val state = _state.asStateFlow()

    private var sessionJob: Job? = null
    private var testJob: Job? = null
    private var visualizerResetJob: Job? = null
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
        
        _state.value = _state.value.copy(
            leadInMs = SettingsManager.hapticLeadInMs,
            leadOutMs = SettingsManager.hapticLeadOutMs
        )
        
        startVisualizerResetLoop()
    }

    private fun startVisualizerResetLoop() {
        visualizerResetJob?.cancel()
        visualizerResetJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(50)
                val current = _state.value
                if (current.phoneLeftIntensity > 0 || current.phoneRightIntensity > 0 || 
                    current.controllerLeftTopIntensity > 0 || current.controllerLeftBottomIntensity > 0 ||
                    current.controllerRightTopIntensity > 0 || current.controllerRightBottomIntensity > 0) {
                    
                    _state.value = current.copy(
                        phoneLeftIntensity = (current.phoneLeftIntensity - 0.1f).coerceAtLeast(0f),
                        phoneRightIntensity = (current.phoneRightIntensity - 0.1f).coerceAtLeast(0f),
                        controllerLeftTopIntensity = (current.controllerLeftTopIntensity - 0.1f).coerceAtLeast(0f),
                        controllerLeftBottomIntensity = (current.controllerLeftBottomIntensity - 0.1f).coerceAtLeast(0f),
                        controllerRightTopIntensity = (current.controllerRightTopIntensity - 0.1f).coerceAtLeast(0f),
                        controllerRightBottomIntensity = (current.controllerRightBottomIntensity - 0.1f).coerceAtLeast(0f)
                    )
                }
            }
        }
    }

    fun updateIntensity(intensity: Float) {
        _state.value = _state.value.copy(intensity = intensity.coerceIn(0f, 1f))
    }

    fun updateBpm(bpm: Int) {
        _state.value = _state.value.copy(bpm = bpm.coerceIn(30, 200))
    }

    fun updateLeadIn(ms: Int) {
        _state.value = _state.value.copy(leadInMs = ms)
        SettingsManager.hapticLeadInMs = ms
    }

    fun updateLeadOut(ms: Int) {
        _state.value = _state.value.copy(leadOutMs = ms)
        SettingsManager.hapticLeadOutMs = ms
    }

    fun updateSessionDuration(seconds: Int) {
        val current = _state.value
        val newDuration = seconds.coerceIn(10, 600)
        if (current.isHeartbeatRunning) {
            _state.value = current.copy(
                sessionDurationSeconds = newDuration,
                remainingSeconds = newDuration
            )
        } else {
            _state.value = current.copy(sessionDurationSeconds = newDuration)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun startHeartbeatSession() {
        if (_state.value.isHeartbeatRunning) {
            _state.value = _state.value.copy(remainingSeconds = _state.value.sessionDurationSeconds)
            return
        }

        _state.value = _state.value.copy(
            isHeartbeatRunning = true,
            remainingSeconds = _state.value.sessionDurationSeconds,
            isTestRunning = false
        )

        sessionJob?.cancel()
        sessionJob = CoroutineScope(Dispatchers.Default).launch {
            while (_state.value.isHeartbeatRunning) {
                val start = System.currentTimeMillis()
                playHeartbeatPulse()
                val interval = 60000L / _state.value.bpm
                val elapsed = System.currentTimeMillis() - start
                delay((interval - elapsed).coerceAtLeast(0L))
            }
        }

        CoroutineScope(Dispatchers.Default).launch {
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

    @RequiresApi(Build.VERSION_CODES.O)
    fun playPulse(intensityOverride: Float? = null, durationMs: Long = 100L, channel: Int = 2) {
        val intensity = intensityOverride ?: _state.value.intensity
        val strength = (255 * intensity).toInt().coerceIn(1, 255)
        val s = _state.value
        
        CoroutineScope(Dispatchers.Default).launch {
            updateVisuals(intensity, channel)
            delay(s.leadInMs.toLong())
            vibrator?.vibrate(VibrationEffect.createOneShot(durationMs, strength))
            delay(durationMs)
            delay(s.leadOutMs.toLong())
        }
    }

    private fun updateVisuals(intensity: Float, channel: Int) {
        val current = _state.value
        _state.value = when (channel) {
            0 -> current.copy(phoneLeftIntensity = intensity)
            1 -> current.copy(phoneRightIntensity = intensity)
            else -> current.copy(phoneLeftIntensity = intensity, phoneRightIntensity = intensity)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    fun playSingleHeartbeat() {
        CoroutineScope(Dispatchers.Default).launch {
            playHeartbeatPulse()
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun playHeartbeatPulse() {
        val s = _state.value
        val intensity = s.intensity
        val strength1 = (255 * intensity).toInt().coerceIn(1, 255)
        val strength2 = (180 * intensity).toInt().coerceIn(1, 255)

        updateVisuals(intensity, 2)
        delay(s.leadInMs.toLong())
        vibrator?.vibrate(VibrationEffect.createOneShot(50, strength1))
        delay(50)
        delay(s.leadOutMs.toLong())
        
        delay(80)

        updateVisuals(intensity * 0.7f, 2)
        delay(s.leadInMs.toLong())
        vibrator?.vibrate(VibrationEffect.createOneShot(60, strength2))
        delay(60)
        delay(s.leadOutMs.toLong())
    }
    
    fun updateControllerVisuals(leftTop: Float = 0f, leftBottom: Float = 0f, rightTop: Float = 0f, rightBottom: Float = 0f) {
        val current = _state.value
        _state.value = current.copy(
            controllerLeftTopIntensity = leftTop.coerceIn(0f, 1f),
            controllerLeftBottomIntensity = leftBottom.coerceIn(0f, 1f),
            controllerRightTopIntensity = rightTop.coerceIn(0f, 1f),
            controllerRightBottomIntensity = rightBottom.coerceIn(0f, 1f)
        )
    }
}
