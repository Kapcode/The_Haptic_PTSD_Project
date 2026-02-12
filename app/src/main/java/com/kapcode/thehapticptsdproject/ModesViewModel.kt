package com.kapcode.thehapticptsdproject

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel

class ModesViewModel(@SuppressLint("StaticFieldLeak") private val context: Context) : ViewModel() {
    val modeState = mutableStateOf(ModeState(activeModes = emptySet()))

    private val appContext = context.applicationContext

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
        val intent = Intent(appContext, HapticService::class.java).apply {
            action = HapticService.ACTION_UPDATE_MODES
            putExtra(HapticService.EXTRA_HEARTBEAT_ACTIVE, PTSDMode.ActiveHeartbeat in modeState.value.activeModes)
            putExtra(HapticService.EXTRA_BB_PLAYER_ACTIVE, PTSDMode.BBPlayer in modeState.value.activeModes)
        }
        appContext.startService(intent)
        Logger.info("UI: Sent mode update to HapticService.")
    }
}
