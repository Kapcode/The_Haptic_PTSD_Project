package com.kapcode.thehapticptsdproject

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kapcode.thehapticptsdproject.composables.SectionCard
import com.kapcode.thehapticptsdproject.composables.SliderWithTick

@Composable
fun HapticControlCard(vm: HapticControlViewModel = viewModel()) {
    val state by vm.hapticState.collectAsState()
    SectionCard(title = "Haptic Settings") {
        Text("Intensity: ${(state.intensity * 100).toInt()}%")
        SliderWithTick(
            value = state.intensity,
            onValueChange = { vm.onIntensityChange(applySnap(it, SettingsManager.snapIntensity)) },
            valueRange = 0f..1f,
            defaultValue = 0.5f
        )
        Text("BPM: ${state.bpm}")
        SliderWithTick(
            value = state.bpm.toFloat(),
            onValueChange = { vm.onBpmChange(applySnap(it, SettingsManager.snapBpm).toInt()) },
            valueRange = 40f..180f,
            defaultValue = 60f
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { vm.onTestPulse() }) { Text("Test") }
            if (state.isHeartbeatRunning) Button(
                onClick = { vm.onStopSession() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) { Text("Stop Session") }
            else Button(onClick = { vm.onStartSession() }) { Text("Start Session") }
        }
    }
}
