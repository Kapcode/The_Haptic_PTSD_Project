package com.kapcode.thehapticptsdproject

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf

object SettingsManager {
    private const val PREFS_NAME = "haptic_ptsd_prefs"
    
    private lateinit var prefs: SharedPreferences

    private val _authorizedFolderUrisState = mutableStateOf<Set<String>>(emptySet())
    val authorizedFolderUrisState: State<Set<String>> = _authorizedFolderUrisState

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        _authorizedFolderUrisState.value = prefs.getStringSet("authorized_folder_uris", emptySet()) ?: emptySet()
    }

    // Squeeze Settings
    var isSqueezeEnabled: Boolean
        get() = prefs.getBoolean("squeeze_enabled", true)
        set(value) = prefs.edit().putBoolean("squeeze_enabled", value).apply()

    var squeezeThreshold: Float
        get() = prefs.getFloat("squeeze_threshold", 0.50f)
        set(value) = prefs.edit().putFloat("squeeze_threshold", value).apply()

    // Shake Settings
    var isShakeEnabled: Boolean
        get() = prefs.getBoolean("shake_enabled", true)
        set(value) = prefs.edit().putBoolean("shake_enabled", value).apply()

    var internalShakeThreshold: Float
        get() = prefs.getFloat("shake_threshold_internal", 15f)
        set(value) = prefs.edit().putFloat("shake_threshold_internal", value).apply()

    // Haptic Settings
    var intensity: Float
        get() = prefs.getFloat("haptic_intensity", 0.5f)
        set(value) = prefs.edit().putFloat("haptic_intensity", value).apply()

    var bpm: Int
        get() = prefs.getInt("haptic_bpm", 60)
        set(value) = prefs.edit().putInt("haptic_bpm", value).apply()

    var sessionDurationSeconds: Int
        get() = prefs.getInt("haptic_session_duration", 120)
        set(value) = prefs.edit().putInt("haptic_session_duration", value).apply()

    var hapticLeadInMs: Int
        get() = prefs.getInt("haptic_lead_in", 10)
        set(value) = prefs.edit().putInt("haptic_lead_in", value).apply()

    var hapticLeadOutMs: Int
        get() = prefs.getInt("haptic_lead_out", 10)
        set(value) = prefs.edit().putInt("haptic_lead_out", value).apply()

    // Beat Player Settings
    var beatMaxIntensity: Float
        get() = prefs.getFloat("beat_max_intensity", 1.0f)
        set(value) = prefs.edit().putFloat("beat_max_intensity", value).apply()
        
    var mediaVolume: Float
        get() = prefs.getFloat("media_volume", 1.0f)
        set(value) = prefs.edit().putFloat("media_volume", value).apply()

    // Snap Settings
    var snapIntensity: Float
        get() = prefs.getFloat("snap_intensity", 0f)
        set(value) = prefs.edit().putFloat("snap_intensity", value).apply()

    var snapBpm: Float
        get() = prefs.getFloat("snap_bpm", 0f)
        set(value) = prefs.edit().putFloat("snap_bpm", value).apply()

    var snapDuration: Float
        get() = prefs.getFloat("snap_duration", 0f)
        set(value) = prefs.edit().putFloat("snap_duration", value).apply()

    var snapVolume: Float
        get() = prefs.getFloat("snap_volume", 0.02f) // Default 2%
        set(value) = prefs.edit().putFloat("snap_volume", value).apply()

    var snapSqueeze: Float
        get() = prefs.getFloat("snap_squeeze", 0f)
        set(value) = prefs.edit().putFloat("snap_squeeze", value).apply()

    var snapShake: Float
        get() = prefs.getFloat("snap_shake", 0f)
        set(value) = prefs.edit().putFloat("snap_shake", value).apply()

    var snapBeatMaxIntensity: Float
        get() = prefs.getFloat("snap_beat_max_intensity", 0f)
        set(value) = prefs.edit().putFloat("snap_beat_max_intensity", value).apply()

    // Logging
    var logToLogcat: Boolean
        get() = prefs.getBoolean("log_to_logcat", false)
        set(value) = prefs.edit().putBoolean("log_to_logcat", value).apply()

    // Authorized Media Folders (SAF URIs)
    var authorizedFolderUris: Set<String>
        get() = _authorizedFolderUrisState.value
        set(value) {
            prefs.edit().putStringSet("authorized_folder_uris", value).apply()
            _authorizedFolderUrisState.value = value
        }
}
