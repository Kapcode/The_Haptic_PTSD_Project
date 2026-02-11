package com.kapcode.thehapticptsdproject

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class AnimationSettingsViewModel : ViewModel() {
    var isDitheringEnabled by mutableStateOf(true)
    var ditheringIntensity by mutableStateOf(0.3f)
    var ditheringSpeed by mutableStateOf(0.3f)
}
