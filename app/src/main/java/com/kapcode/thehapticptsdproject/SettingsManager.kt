package com.kapcode.thehapticptsdproject

import android.content.Context
import android.content.SharedPreferences

object SettingsManager {
    private const val PREFS_NAME = "haptic_ptsd_prefs"
    
    private lateinit var prefs: SharedPreferences

    fun init(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

    // Beat Player Settings
    var beatMaxIntensity: Float
        get() = prefs.getFloat("beat_max_intensity", 1.0f)
        set(value) = prefs.edit().putFloat("beat_max_intensity", value).apply()
        
    var mediaVolume: Float
        get() = prefs.getFloat("media_volume", 1.0f)
        set(value) = prefs.edit().putFloat("media_volume", value).apply()

    // Authorized Media Folders (SAF URIs)
    var authorizedFolderUris: Set<String>
        get() = prefs.getStringSet("authorized_folder_uris", emptySet()) ?: emptySet()
        set(value) = prefs.edit().putStringSet("authorized_folder_uris", value).apply()
}
