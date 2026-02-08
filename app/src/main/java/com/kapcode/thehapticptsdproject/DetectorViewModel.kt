package com.kapcode.thehapticptsdproject

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

class DetectorViewModel(private val squeezeDetector: SqueezeDetector) : ViewModel() {

    val squeezeState: StateFlow<SqueezeDetectorState> = squeezeDetector.state

    fun onSqueezeToggle(enabled: Boolean) {
        SettingsManager.isSqueezeEnabled = enabled
    }

    fun onSqueezeThresholdChange(threshold: Float) {
        squeezeDetector.setSqueezeThreshold(threshold.toDouble())
        SettingsManager.squeezeThreshold = threshold
    }

    fun recalibrateSqueezeDetector() {
        squeezeDetector.recalibrate()
    }

    fun onShakeToggle(enabled: Boolean) {
        SettingsManager.isShakeEnabled = enabled
    }

    fun onShakeSensitivityChange(sensitivity: Float) {
        val newThreshold = 55f - sensitivity
        SettingsManager.internalShakeThreshold = newThreshold
    }
}
