package com.kapcode.thehapticptsdproject

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

class DetectorViewModel : ViewModel() {

    private val squeezeDetector = SqueezeManager.detector
    val squeezeState: StateFlow<SqueezeDetectorState> = squeezeDetector.state

    fun onSqueezeToggle(enabled: Boolean) {
        SettingsManager.isSqueezeEnabled = enabled
        SettingsManager.save()
    }

    fun onSqueezeThresholdChange(threshold: Float) {
        squeezeDetector.setSqueezeThreshold(threshold.toDouble())
        SettingsManager.squeezeThreshold = threshold
        SettingsManager.save()
    }

    fun recalibrateSqueezeDetector() {
        squeezeDetector.recalibrate()
    }

    fun onShakeToggle(enabled: Boolean) {
        SettingsManager.isShakeEnabled = enabled
        SettingsManager.save()
    }

    fun onShakeSensitivityChange(sensitivity: Float) {
        SettingsManager.internalShakeThreshold = sensitivity
        SettingsManager.save()
    }
}
