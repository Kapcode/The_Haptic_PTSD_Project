package com.kapcode.thehapticptsdproject

import android.app.Application
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel

class ModesViewModel(application: Application) : AndroidViewModel(application) {
    val modeState = mutableStateOf(ModeState(activeModes = emptySet()))

    fun onModeToggled(mode: PTSDMode) {
        val currentModes = modeState.value.activeModes.toMutableSet()
        val isActivating = mode !in currentModes
        if (isActivating) {
            currentModes.add(mode)
            Logger.info("UI: Activated mode '${mode.name}'")
        } else {
            currentModes.remove(mode)
            Logger.info("UI: Deactivated mode '${mode.name}'")
        }
        modeState.value = modeState.value.copy(activeModes = currentModes)
        updateHapticService()
    }

    fun resetModes() {
        modeState.value = ModeState(activeModes = emptySet())
        updateHapticService()
        Logger.info("UI: All modes reset to default.")
    }

    private fun updateHapticService() {
        val intent = Intent(getApplication(), HapticService::class.java).apply {
            action = HapticService.ACTION_UPDATE_MODES
            putExtra(HapticService.EXTRA_HEARTBEAT_ACTIVE, PTSDMode.ActiveHeartbeat in modeState.value.activeModes)
            putExtra(HapticService.EXTRA_BB_PLAYER_ACTIVE, PTSDMode.BBPlayer in modeState.value.activeModes)
        }
        getApplication<Application>().startService(intent)
        Logger.info("UI: Sent mode update to HapticService.")
    }
}
