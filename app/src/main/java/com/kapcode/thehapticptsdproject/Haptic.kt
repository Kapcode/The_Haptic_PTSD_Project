package com.kapcode.thehapticptsdproject

import android.content.Context
import android.content.Intent
import androidx.compose.ui.graphics.Color
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton object for managing haptic feedback and visualizer state.
 *
 * This manager is the central hub for all haptic-related operations. It is responsible for:
 * - Maintaining the `HapticState`, which includes intensities, BPM, and visualizer data.
 * - Running the "Heartbeat" therapeutic session via coroutines.
 * - Playing discrete haptic pulses for testing or specific events.
 * - Receiving beat triggers from `BeatDetector` and updating device visuals accordingly.
 * - Dispatching vibration commands to the `HapticService`.
 * - Managing a decay loop to gracefully fade out visualizer intensities for a smooth appearance.
 * - This object does not directly interact with the `Vibrator` service; it delegates all
 *   vibration commands to the `HapticService` to ensure they are executed correctly
 *   within the service's lifecycle and wake lock context.
 */
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
    val controllerRightBottomIntensity: Float = 0f,

    // Live motor colors (Precedence based on most recent profile)
    val phoneLeftColor: Color = Color.Cyan,
    val phoneRightColor: Color = Color.Cyan,
    val controllerLeftTopColor: Color = Color.Cyan,
    val controllerLeftBottomColor: Color = Color.Cyan,
    val controllerRightTopColor: Color = Color.Cyan,
    val controllerRightBottomColor: Color = Color.Cyan,

    // Currently vibrating profiles (for wobble animation)
    val activeProfiles: Set<BeatProfile> = emptySet(),

    // Live visualizer data
    val visualizerData: FloatArray = FloatArray(32) { 0f },
    val resetCounter: Int = 0,
    val pauseStartTime: Long = 0
)

object HapticManager {
    private val _state = MutableStateFlow(HapticState())
    val state = _state.asStateFlow()

    private var sessionJob: Job? = null
    private var testJob: Job? = null
    private var visualizerResetJob: Job? = null
    private lateinit var context: Context

    fun init(context: Context) {
        this.context = context.applicationContext
        _state.value = _state.value.copy(
            leadInMs = SettingsManager.hapticLeadInMs,
            leadOutMs = SettingsManager.hapticLeadOutMs
        )
        startVisualizerResetLoop()
    }
    
    fun setPauseStartTime(time: Long) {
        _state.value = _state.value.copy(pauseStartTime = time)
    }

    private fun startVisualizerResetLoop() {
        visualizerResetJob?.cancel()
        visualizerResetJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(50)
                val current = _state.value
                
                val phoneActive = current.phoneLeftIntensity > 0.05f || current.phoneRightIntensity > 0.05f
                val controllersActive = current.controllerLeftTopIntensity > 0.05f || current.controllerLeftBottomIntensity > 0.05f ||
                                       current.controllerRightTopIntensity > 0.05f || current.controllerRightBottomIntensity > 0.05f
                
                val anyMotorActive = phoneActive || controllersActive
                val hasVisualizerActivity = current.visualizerData.any { it > 0f }

                if (anyMotorActive || hasVisualizerActivity) {
                    val newVisualizerData = current.visualizerData.map { (it - 0.10f).coerceAtLeast(0f) }.toFloatArray()
                    
                    // Clear active profiles if NO device is vibrating
                    val newActiveProfiles = if (!anyMotorActive) {
                        emptySet<BeatProfile>()
                    } else {
                        current.activeProfiles
                    }

                    _state.value = current.copy(
                        phoneLeftIntensity = (current.phoneLeftIntensity - 0.15f).coerceAtLeast(0f),
                        phoneRightIntensity = (current.phoneRightIntensity - 0.15f).coerceAtLeast(0f),
                        controllerLeftTopIntensity = (current.controllerLeftTopIntensity - 0.15f).coerceAtLeast(0f),
                        controllerLeftBottomIntensity = (current.controllerLeftBottomIntensity - 0.15f).coerceAtLeast(0f),
                        controllerRightTopIntensity = (current.controllerRightTopIntensity - 0.15f).coerceAtLeast(0f),
                        controllerRightBottomIntensity = (current.controllerRightBottomIntensity - 0.15f).coerceAtLeast(0f),
                        visualizerData = newVisualizerData,
                        activeProfiles = newActiveProfiles
                    )
                } else if (current.activeProfiles.isNotEmpty()) {
                    _state.value = current.copy(activeProfiles = emptySet())
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

    fun startHeartbeatSession() {
        val playerState = BeatDetector.playerState.value
        if (playerState.isPlaying || playerState.isPaused) {
            return 
        }

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
        sendIntentToService(HapticService.ACTION_CANCEL_VIBRATION)
    }

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

    fun playRawVibration(durationMs: Long, amplitude: Int) {
        sendIntentToService(HapticService.ACTION_VIBRATE) {
            putExtra(HapticService.EXTRA_DURATION, durationMs)
            putExtra(HapticService.EXTRA_AMPLITUDE, amplitude)
        }
    }
    
    fun playPulse(intensityOverride: Float? = null, durationMs: Long = 100L, channel: Int = 2, profile: BeatProfile? = null) {
        val intensity = intensityOverride ?: _state.value.intensity
        val strength = (255 * intensity).toInt().coerceIn(1, 255)
        
        CoroutineScope(Dispatchers.Default).launch {
            updateVisuals(intensity, channel, profile)
            delay(state.value.leadInMs.toLong())
            playRawVibration(durationMs, strength)
            delay(durationMs)
            delay(state.value.leadOutMs.toLong())
        }
    }

    fun updateDeviceVisuals(device: HapticDevice, intensity: Float, color: Color, profile: BeatProfile) {
        val current = _state.value
        val newActiveProfiles = current.activeProfiles + profile
        _state.value = when (device) {
            HapticDevice.PHONE_LEFT -> current.copy(phoneLeftIntensity = intensity, phoneLeftColor = color, activeProfiles = newActiveProfiles)
            HapticDevice.PHONE_RIGHT -> current.copy(phoneRightIntensity = intensity, phoneRightColor = color, activeProfiles = newActiveProfiles)
            HapticDevice.CTRL_LEFT_TOP -> current.copy(controllerLeftTopIntensity = intensity, controllerLeftTopColor = color, activeProfiles = newActiveProfiles)
            HapticDevice.CTRL_LEFT_BOTTOM -> current.copy(controllerLeftBottomIntensity = intensity, controllerLeftBottomColor = color, activeProfiles = newActiveProfiles)
            HapticDevice.CTRL_RIGHT_TOP -> current.copy(controllerRightTopIntensity = intensity, controllerRightTopColor = color, activeProfiles = newActiveProfiles)
            HapticDevice.CTRL_RIGHT_BOTTOM -> current.copy(controllerRightBottomIntensity = intensity, controllerRightBottomColor = color, activeProfiles = newActiveProfiles)
        }
    }

    private fun updateVisuals(intensity: Float, channel: Int, profile: BeatProfile?) {
        val current = _state.value
        val color = profile?.getColor() ?: Color.Cyan
        val newActiveProfiles = if (profile != null) current.activeProfiles + profile else current.activeProfiles
        
        _state.value = when (channel) {
            0 -> current.copy(phoneLeftIntensity = intensity, phoneLeftColor = color, activeProfiles = newActiveProfiles)
            1 -> current.copy(phoneRightIntensity = intensity, phoneRightColor = color, activeProfiles = newActiveProfiles)
            else -> current.copy(
                phoneLeftIntensity = intensity, 
                phoneRightIntensity = intensity,
                phoneLeftColor = color,
                phoneRightColor = color,
                activeProfiles = newActiveProfiles
            )
        }
    }

    fun playSingleHeartbeat() {
        CoroutineScope(Dispatchers.Default).launch {
            playHeartbeatPulse()
        }
    }

    private suspend fun playHeartbeatPulse() {
        val s = _state.value
        val intensity = s.intensity
        val strength1 = (255 * intensity).toInt().coerceIn(1, 255)
        val strength2 = (180 * intensity).toInt().coerceIn(1, 255)

        updateVisuals(intensity, 2, BeatProfile.AMPLITUDE)
        delay(s.leadInMs.toLong())
        playRawVibration(50, strength1)
        delay(50)
        delay(s.leadOutMs.toLong())
        
        delay(80)

        updateVisuals(intensity * 0.7f, 2, BeatProfile.AMPLITUDE)
        delay(s.leadInMs.toLong())
        playRawVibration(60, strength2)
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

    fun updateVisualizer(data: FloatArray) {
        _state.value = _state.value.copy(visualizerData = data)
    }

    fun resetIconAlphas() {
        val current = _state.value
        _state.value = current.copy(
            phoneLeftIntensity = 0f,
            phoneRightIntensity = 0f,
            controllerLeftTopIntensity = 0f,
            controllerLeftBottomIntensity = 0f,
            controllerRightTopIntensity = 0f,
            controllerRightBottomIntensity = 0f,
            visualizerData = FloatArray(32) { 0f },
            activeProfiles = emptySet(),
            resetCounter = current.resetCounter + 1
        )
    }

    fun reset() {
        _state.value = HapticState()
        stopHeartbeatSession()
        testJob?.cancel()
        Logger.info("Haptic Manager state has been reset.")
    }

    private fun sendIntentToService(action: String, extras: Intent.() -> Unit = {}) {
        if (!::context.isInitialized) return
        val intent = Intent(context, HapticService::class.java).apply {
            this.action = action
            extras()
        }
        ContextCompat.startForegroundService(context, intent)
    }
}
