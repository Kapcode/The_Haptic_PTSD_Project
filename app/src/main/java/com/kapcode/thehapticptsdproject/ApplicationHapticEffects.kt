package com.kapcode.thehapticptsdproject

import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.content.Context

object ApplicationHapticEffects {

    fun onSliderSnap() {
        HapticManager.playRawVibration(10, 80)
    }

    fun onSwitchToggle(enabled: Boolean) {
        if (enabled) {
            HapticManager.playRawVibration(15, 120)
        } else {
            HapticManager.playRawVibration(20, 60)
        }
    }

    fun onButtonClick() {
        HapticManager.playRawVibration(12, 100)
    }

    fun onLongPress() {
        HapticManager.playRawVibration(40, 150)
    }
    
    fun onResetTriggered() {
        HapticManager.playRawVibration(100, 200)
    }
}
