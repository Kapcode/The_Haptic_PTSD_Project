package com.kapcode.thehapticptsdproject

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.kapcode.thehapticptsdproject.composables.SectionCard
import com.kapcode.thehapticptsdproject.composables.SliderWithTick
import java.util.Locale

@Composable
fun LiveHapticSettingsCard() {
    SectionCard(title = "Live Haptic Fine-Tuning") {
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                "Adjust the cooldown period (in milliseconds) for each haptic profile when in 'Live' mode. A shorter cooldown means more frequent haptics.",
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(modifier = Modifier.height(16.dp))

            Text("Amplitude Cooldown: ${SettingsManager.liveCooldownAmplitudeMs}ms", style = MaterialTheme.typography.bodySmall)
            SliderWithTick(
                value = SettingsManager.liveCooldownAmplitudeMs.toFloat(),
                onValueChange = { SettingsManager.liveCooldownAmplitudeMs = it.toInt(); SettingsManager.save() },
                valueRange = 50f..1000f,
                defaultValue = 200f
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text("Bass Cooldown: ${SettingsManager.liveCooldownBassMs}ms", style = MaterialTheme.typography.bodySmall)
            SliderWithTick(
                value = SettingsManager.liveCooldownBassMs.toFloat(),
                onValueChange = { SettingsManager.liveCooldownBassMs = it.toInt(); SettingsManager.save() },
                valueRange = 100f..2000f,
                defaultValue = 400f
            )
            Spacer(modifier = Modifier.height(8.dp))

            Text("Drum Cooldown: ${SettingsManager.liveCooldownDrumMs}ms", style = MaterialTheme.typography.bodySmall)
            SliderWithTick(
                value = SettingsManager.liveCooldownDrumMs.toFloat(),
                onValueChange = { SettingsManager.liveCooldownDrumMs = it.toInt(); SettingsManager.save() },
                valueRange = 50f..1000f,
                defaultValue = 150f
            )
            Spacer(modifier = Modifier.height(8.dp))
            
            Text("Guitar Cooldown: ${SettingsManager.liveCooldownGuitarMs}ms", style = MaterialTheme.typography.bodySmall)
            SliderWithTick(
                value = SettingsManager.liveCooldownGuitarMs.toFloat(),
                onValueChange = { SettingsManager.liveCooldownGuitarMs = it.toInt(); SettingsManager.save() },
                valueRange = 50f..1000f,
                defaultValue = 200f
            )
        }
    }
}
