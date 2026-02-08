package com.kapcode.thehapticptsdproject

import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class ModesViewModel : ViewModel() {
    val modeState = mutableStateOf(ModeState(activeModes = emptySet()))

    fun onModeToggled(mode: PTSDMode) {
        val currentModes = modeState.value.activeModes.toMutableSet()
        if (mode in currentModes) {
            currentModes.remove(mode)
        } else {
            currentModes.add(mode)
        }
        modeState.value = modeState.value.copy(activeModes = currentModes)
    }
}
