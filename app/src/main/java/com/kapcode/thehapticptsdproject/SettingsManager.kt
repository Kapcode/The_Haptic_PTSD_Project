package com.kapcode.thehapticptsdproject

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class VisualizerType {
    VERTICAL_BARS,
    CHANNEL_INTENSITY,
    WAVEFORM
}

enum class HapticDevice(val label: String) {
    PHONE_LEFT("Phone L"),
    PHONE_RIGHT("Phone R"),
    CTRL_LEFT_TOP("Ctrl LT"),
    CTRL_LEFT_BOTTOM("Ctrl LB"),
    CTRL_RIGHT_TOP("Ctrl RT"),
    CTRL_RIGHT_BOTTOM("Ctrl RB")
}

object SettingsManager {
    private const val PREFS_NAME = "haptic_ptsd_prefs"
    private lateinit var prefs: SharedPreferences

    // Squeeze Settings
    var isSqueezeEnabled by mutableStateOf(false)
    var squeezeThreshold by mutableStateOf(0.50f)

    // Shake Settings
    var isShakeEnabled by mutableStateOf(true)
    var internalShakeThreshold by mutableStateOf(15f)

    // Haptic Settings
    var intensity by mutableStateOf(0.5f)
    var bpm by mutableStateOf(60)
    var sessionDurationSeconds by mutableStateOf(120)
    var hapticLeadInMs by mutableStateOf(10)
    var hapticLeadOutMs by mutableStateOf(10)
    var hapticSyncOffsetMs by mutableStateOf(-1500)

    // Beat Player Settings
    var beatMaxIntensity by mutableStateOf(1.0f)
    var mediaVolume by mutableStateOf(1.0f)
    var lastPlayedAudioUri by mutableStateOf<String?>(null)
    var lastPlayedAudioName by mutableStateOf<String?>(null)
    var showOffsetSlider by mutableStateOf(false)
    var seekbarTimeWindowMinutes by mutableStateOf(5)
    var seekThumbWidth by mutableStateOf(12f)
    var seekThumbHeight by mutableStateOf(48f)
    var seekThumbAlpha by mutableStateOf(0.85f)
    
    var isRepeatEnabled by mutableStateOf(false)
    var isRepeatAllEnabled by mutableStateOf(false)
    var volumeBoost by mutableStateOf(1.0f)
    var playbackSpeed by mutableStateOf(1.0f)

    // Device Assignments: Device -> Set of Profiles
    var deviceAssignments by mutableStateOf<Map<HapticDevice, Set<BeatProfile>>>(
        HapticDevice.entries.associateWith { setOf(BeatProfile.AMPLITUDE) }
    )

    // Visualizer Settings (Independent Layers)
    var isBarsEnabled by mutableStateOf(true)
    var isChannelIntensityEnabled by mutableStateOf(false)
    var isWaveformEnabled by mutableStateOf(false)

    var gainAmplitude by mutableStateOf(12f)
    var gainBass by mutableStateOf(2.5f)
    var gainDrum by mutableStateOf(2.2f)
    var gainGuitar by mutableStateOf(1.8f)
    var gainHighs by mutableStateOf(1.5f)
    var visualizerTriggeredAlpha by mutableStateOf(0.1f)
    var minIconAlpha by mutableStateOf(0.2f)
    var latchDurationMs by mutableStateOf(200)
    var invertVisualizerAlpha by mutableStateOf(false)
    var triggerThresholdAmplitude by mutableStateOf(0.38f)
    var triggerThresholdBass by mutableStateOf(0.48f)
    var triggerThresholdDrum by mutableStateOf(0.48f)
    var triggerThresholdGuitar by mutableStateOf(0.48f)
    var scaleVibrationVisualizerX by mutableStateOf(1.0f)
    var scaleVibrationVisualizerY by mutableStateOf(1.0f)
    var scaleAudioVisualizerX by mutableStateOf(1.0f)
    var scaleAudioVisualizerY by mutableStateOf(1.0f)


    // Experimental Switch
    var isExperimentalEnabled by mutableStateOf(false)

    // Snap Settings
    var snapIntensity by mutableStateOf(0f)
    var snapBpm by mutableStateOf(0f)
    var snapDuration by mutableStateOf(0f)
    var snapVolume by mutableStateOf(0f)
    var snapBeatMaxIntensity by mutableStateOf(0f)
    var snapSqueeze by mutableStateOf(0f)
    var snapShake by mutableStateOf(0f)
    var snapGain by mutableStateOf(0f)
    var snapTriggeredAlpha by mutableStateOf(0f)
    var snapIconAlpha by mutableStateOf(0f)
    var snapSyncOffset by mutableStateOf(0f)
    var snapLatchDuration by mutableStateOf(0f)
    var snapTriggerThreshold by mutableStateOf(0f)
    var snapScaleVibration by mutableStateOf(0f)
    var snapScaleAudio by mutableStateOf(0f)


    // Logging
    var logToLogcat by mutableStateOf(false)

    // Authorized Media Folders
    var authorizedFolderUris by mutableStateOf<Set<String>>(emptySet())

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        
        isSqueezeEnabled = prefs.getBoolean("squeeze_enabled", false)
        squeezeThreshold = prefs.getFloat("squeeze_threshold", 0.50f)
        isShakeEnabled = prefs.getBoolean("shake_enabled", true)
        internalShakeThreshold = prefs.getFloat("shake_threshold_internal", 15f)
        intensity = prefs.getFloat("haptic_intensity", 0.5f)
        bpm = prefs.getInt("haptic_bpm", 60)
        sessionDurationSeconds = prefs.getInt("haptic_session_duration", 120)
        hapticLeadInMs = prefs.getInt("haptic_lead_in", 10)
        hapticLeadOutMs = prefs.getInt("haptic_lead_out", 10)
        hapticSyncOffsetMs = prefs.getInt("haptic_sync_offset", -1500)
        beatMaxIntensity = prefs.getFloat("beat_max_intensity", 1.0f)
        mediaVolume = prefs.getFloat("media_volume", 1.0f)
        lastPlayedAudioUri = prefs.getString("last_played_audio_uri", null)
        lastPlayedAudioName = prefs.getString("last_played_audio_name", null)
        showOffsetSlider = prefs.getBoolean("show_offset_slider", false)
        seekbarTimeWindowMinutes = prefs.getInt("seekbar_time_window", 5)
        seekThumbWidth = prefs.getFloat("seek_thumb_width", 12f)
        seekThumbHeight = prefs.getFloat("seek_thumb_height", 48f)
        seekThumbAlpha = prefs.getFloat("seek_thumb_alpha", 0.85f).coerceAtLeast(0.40f)
        
        isRepeatEnabled = prefs.getBoolean("is_repeat_enabled", false)
        isRepeatAllEnabled = prefs.getBoolean("is_repeat_all_enabled", false)
        volumeBoost = prefs.getFloat("volume_boost", 1.0f)
        playbackSpeed = prefs.getFloat("playback_speed", 1.0f)

        val assignmentsJson = prefs.getString("device_assignments", null)
        if (assignmentsJson != null) {
            try {
                deviceAssignments = Json.decodeFromString(assignmentsJson)
            } catch (e: Exception) {
                // Fallback to default
            }
        }

        isBarsEnabled = prefs.getBoolean("is_bars_enabled", true)
        isChannelIntensityEnabled = prefs.getBoolean("is_channel_intensity_enabled", false)
        isWaveformEnabled = prefs.getBoolean("is_waveform_enabled", false)

        gainAmplitude = prefs.getFloat("gain_amplitude", 12f)
        gainBass = prefs.getFloat("gain_bass", 2.5f)
        gainDrum = prefs.getFloat("gain_drum", 2.2f)
        gainGuitar = prefs.getFloat("gain_guitar", 1.8f)
        gainHighs = prefs.getFloat("gain_highs", 1.5f)
        visualizerTriggeredAlpha = prefs.getFloat("visualizer_triggered_alpha", 0.1f)
        minIconAlpha = prefs.getFloat("min_icon_alpha", 0.2f)
        latchDurationMs = prefs.getInt("latch_duration", 200)
        invertVisualizerAlpha = prefs.getBoolean("invert_visualizer_alpha", false)
        triggerThresholdAmplitude = prefs.getFloat("trigger_threshold_amplitude", 0.38f)
        triggerThresholdBass = prefs.getFloat("trigger_threshold_bass", 0.48f)
        triggerThresholdDrum = prefs.getFloat("trigger_threshold_drum", 0.48f)
        triggerThresholdGuitar = prefs.getFloat("trigger_threshold_guitar", 0.48f)
        scaleVibrationVisualizerX = prefs.getFloat("scale_vibration_visualizer_x", 1.0f)
        scaleVibrationVisualizerY = prefs.getFloat("scale_vibration_visualizer_y", 1.0f)
        scaleAudioVisualizerX = prefs.getFloat("scale_audio_visualizer_x", 1.0f)
        scaleAudioVisualizerY = prefs.getFloat("scale_audio_visualizer_y", 1.0f)
        
        isExperimentalEnabled = prefs.getBoolean("experimental_enabled", false)

        snapIntensity = prefs.getFloat("snap_intensity", 0f)
        snapBpm = prefs.getFloat("snap_bpm", 0f)
        snapDuration = prefs.getFloat("snap_duration", 0f)
        snapVolume = prefs.getFloat("snap_volume", 0f)
        snapBeatMaxIntensity = prefs.getFloat("snap_beat_max_intensity", 0f)
        snapSqueeze = prefs.getFloat("snap_squeeze", 0f)
        snapShake = prefs.getFloat("snap_shake", 0f)
        snapGain = prefs.getFloat("snap_gain", 0f)
        snapTriggeredAlpha = prefs.getFloat("snap_triggered_alpha", 0f)
        snapIconAlpha = prefs.getFloat("snap_icon_alpha", 0f)
        snapSyncOffset = prefs.getFloat("snap_sync_offset", 0f)
        snapLatchDuration = prefs.getFloat("snap_latch_duration", 0f)
        snapTriggerThreshold = prefs.getFloat("snap_trigger_threshold", 0f)
        snapScaleVibration = prefs.getFloat("snap_scale_vibration", 0f)
        snapScaleAudio = prefs.getFloat("snap_scale_audio", 0f)

        logToLogcat = prefs.getBoolean("log_to_logcat", false)
        authorizedFolderUris = prefs.getStringSet("authorized_folder_uris", emptySet()) ?: emptySet()
    }

    fun save() {
        prefs.edit().apply {
            putBoolean("squeeze_enabled", isSqueezeEnabled)
            putFloat("squeeze_threshold", squeezeThreshold)
            putBoolean("shake_enabled", isShakeEnabled)
            putFloat("shake_threshold_internal", internalShakeThreshold)
            putFloat("haptic_intensity", intensity)
            putInt("haptic_bpm", bpm)
            putInt("haptic_session_duration", sessionDurationSeconds)
            putInt("haptic_lead_in", hapticLeadInMs)
            putInt("haptic_lead_out", hapticLeadOutMs)
            putInt("haptic_sync_offset", hapticSyncOffsetMs)
            putFloat("beat_max_intensity", beatMaxIntensity)
            putFloat("media_volume", mediaVolume)
            putString("last_played_audio_uri", lastPlayedAudioUri)
            putString("last_played_audio_name", lastPlayedAudioName)
            putBoolean("show_offset_slider", showOffsetSlider)
            putInt("seekbar_time_window", seekbarTimeWindowMinutes)
            putFloat("seek_thumb_width", seekThumbWidth)
            putFloat("seek_thumb_height", seekThumbHeight)
            putFloat("seek_thumb_alpha", seekThumbAlpha)
            
            putBoolean("is_repeat_enabled", isRepeatEnabled)
            putBoolean("is_repeat_all_enabled", isRepeatAllEnabled)
            putFloat("volume_boost", volumeBoost)
            putFloat("playback_speed", playbackSpeed)

            putString("device_assignments", Json.encodeToString(deviceAssignments))

            putBoolean("is_bars_enabled", isBarsEnabled)
            putBoolean("is_channel_intensity_enabled", isChannelIntensityEnabled)
            putBoolean("is_waveform_enabled", isWaveformEnabled)

            putFloat("gain_amplitude", gainAmplitude)
            putFloat("gain_bass", gainBass)
            putFloat("gain_drum", gainDrum)
            putFloat("gain_guitar", gainGuitar)
            putFloat("gain_highs", gainHighs)
            putFloat("visualizer_triggered_alpha", visualizerTriggeredAlpha)
            putFloat("min_icon_alpha", minIconAlpha)
            putInt("latch_duration", latchDurationMs)
            putBoolean("invert_visualizer_alpha", invertVisualizerAlpha)
            putFloat("trigger_threshold_amplitude", triggerThresholdAmplitude)
            putFloat("trigger_threshold_bass", triggerThresholdBass)
            putFloat("trigger_threshold_drum", triggerThresholdDrum)
            putFloat("trigger_threshold_guitar", triggerThresholdGuitar)
            putFloat("scale_vibration_visualizer_x", scaleVibrationVisualizerX)
            putFloat("scale_vibration_visualizer_y", scaleVibrationVisualizerY)
            putFloat("scale_audio_visualizer_x", scaleAudioVisualizerX)
            putFloat("scale_audio_visualizer_y", scaleAudioVisualizerY)
            
            putBoolean("experimental_enabled", isExperimentalEnabled)

            putFloat("snap_intensity", snapIntensity)
            putFloat("snap_bpm", snapBpm)
            putFloat("snap_duration", snapDuration)
            putFloat("snap_volume", snapVolume)
            putFloat("snap_beat_max_intensity", snapBeatMaxIntensity)
            putFloat("snap_squeeze", snapSqueeze)
            putFloat("snap_shake", snapShake)
            putFloat("snap_gain", snapGain)
            putFloat("snap_triggered_alpha", snapTriggeredAlpha)
            putFloat("snap_icon_alpha", snapIconAlpha)
            putFloat("snap_sync_offset", snapSyncOffset)
            putFloat("snap_latch_duration", snapLatchDuration)
            putFloat("snap_trigger_threshold", snapTriggerThreshold)
            putFloat("snap_scale_vibration", snapScaleVibration)
            putFloat("snap_scale_audio", snapScaleAudio)
            putBoolean("log_to_logcat", logToLogcat)
            putStringSet("authorized_folder_uris", authorizedFolderUris)
            apply()
        }
    }

    fun resetToDefaults() {
        isSqueezeEnabled = false
        squeezeThreshold = 0.50f
        isShakeEnabled = true
        internalShakeThreshold = 15f
        intensity = 0.5f
        bpm = 60
        sessionDurationSeconds = 120
        hapticLeadInMs = 10
        hapticLeadOutMs = 10
        hapticSyncOffsetMs = -1500
        beatMaxIntensity = 1.0f
        mediaVolume = 1.0f
        showOffsetSlider = false
        seekbarTimeWindowMinutes = 5
        seekThumbWidth = 12f
        seekThumbHeight = 48f
        seekThumbAlpha = 0.85f
        
        isRepeatEnabled = false
        isRepeatAllEnabled = false
        volumeBoost = 1.0f
        playbackSpeed = 1.0f
        
        deviceAssignments = HapticDevice.entries.associateWith { setOf(BeatProfile.AMPLITUDE) }

        isBarsEnabled = true
        isChannelIntensityEnabled = false
        isWaveformEnabled = false

        gainAmplitude = 12f
        gainBass = 2.5f
        gainDrum = 2.2f
        gainGuitar = 1.8f
        gainHighs = 1.5f
        visualizerTriggeredAlpha = 0.1f
        minIconAlpha = 0.2f
        latchDurationMs = 200
        invertVisualizerAlpha = false
        triggerThresholdAmplitude = 0.38f
        triggerThresholdBass = 0.48f
        triggerThresholdDrum = 0.48f
        triggerThresholdGuitar = 0.48f
        scaleVibrationVisualizerX = 1.0f
        scaleVibrationVisualizerY = 1.0f
        scaleAudioVisualizerX = 1.0f
        scaleAudioVisualizerY = 1.0f
        
        isExperimentalEnabled = false
        
        snapIntensity = 0f
        snapBpm = 0f
        snapDuration = 0f
        snapVolume = 0f
        snapBeatMaxIntensity = 0f
        snapSqueeze = 0f
        snapShake = 0f
        snapGain = 0f
        snapTriggeredAlpha = 0f
        snapIconAlpha = 0f
        snapSyncOffset = 0f
        snapLatchDuration = 0f
        snapTriggerThreshold = 0f
        snapScaleVibration = 0f
        snapScaleAudio = 0f
        
        save()
    }
}
