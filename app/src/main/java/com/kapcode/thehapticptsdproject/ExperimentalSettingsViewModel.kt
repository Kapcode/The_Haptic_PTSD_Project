package com.kapcode.thehapticptsdproject

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class ExperimentalSettingsViewModel : ViewModel() {
    var isExperimentalEnabled by mutableStateOf(SettingsManager.isExperimentalEnabled)
        private set

    fun onExperimentalEnabledChange(isEnabled: Boolean) {
        isExperimentalEnabled = isEnabled
        SettingsManager.isExperimentalEnabled = isEnabled
        SettingsManager.save()
    }
}
