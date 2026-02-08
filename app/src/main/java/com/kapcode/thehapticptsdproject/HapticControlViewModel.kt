package com.kapcode.thehapticptsdproject

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

class HapticControlViewModel : ViewModel() {
    val hapticState: StateFlow<HapticState> = HapticManager.state

    fun onIntensityChange(intensity: Float) {
        HapticManager.updateIntensity(intensity)
        SettingsManager.intensity = intensity
    }

    fun onBpmChange(bpm: Int) {
        HapticManager.updateBpm(bpm)
        SettingsManager.bpm = bpm
    }

    fun onTestPulse() {
        HapticManager.testPulseSequence()
    }

    fun onStartSession() {
        HapticManager.startHeartbeatSession()
    }

    fun onStopSession() {
        HapticManager.stopHeartbeatSession()
    }
}
