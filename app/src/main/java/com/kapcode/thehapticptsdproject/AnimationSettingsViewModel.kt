package com.kapcode.thehapticptsdproject

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel

class AnimationSettingsViewModel : ViewModel() {
    var isDitheringEnabled by mutableStateOf(true)
    var ditheringIntensity by mutableFloatStateOf(0.3f)//changed from mutableStateOf to mutableFloatStateOf
    var ditheringSpeed by mutableFloatStateOf(0.3f)// this too
}
