package com.kapcode.thehapticptsdproject.composables

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kapcode.thehapticptsdproject.AnimationSettingsViewModel

@Composable
fun AnimationSettingsCard(vm: AnimationSettingsViewModel = viewModel()) {
    SectionCard(title = "Animation Settings") {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dithering Effect", modifier = Modifier.weight(1f))
                AnimatedSwitch(
                    checked = vm.isDitheringEnabled,
                    onCheckedChange = { vm.isDitheringEnabled = it }
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text("Intensity: ${(vm.ditheringIntensity * 100).toInt()}%")
            SliderWithTick(
                value = vm.ditheringIntensity,
                onValueChange = { vm.ditheringIntensity = it },
                valueRange = 0f..1f,
                defaultValue = 0.3f
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text("Speed: ${(vm.ditheringSpeed * 100).toInt()}%")
            SliderWithTick(
                value = vm.ditheringSpeed,
                onValueChange = { vm.ditheringSpeed = it },
                valueRange = 0f..1f,
                defaultValue = 0.3f
            )
        }
    }
}
