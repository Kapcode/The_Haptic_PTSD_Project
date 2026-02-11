package com.kapcode.thehapticptsdproject

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kapcode.thehapticptsdproject.composables.AnimatedSwitch
import com.kapcode.thehapticptsdproject.composables.SectionCard
import com.kapcode.thehapticptsdproject.composables.SliderWithTick

@Composable
fun DetectorCards() {
    if (SettingsManager.isExperimentalEnabled) {
        SqueezeDetectorCard(
            isEnabled = SettingsManager.isSqueezeEnabled,
            onToggle = { SettingsManager.isSqueezeEnabled = it; SettingsManager.save() },
            isExperimental = true
        )
        Spacer(Modifier.height(16.dp))
    }

    ShakeDetectorCard(
        isEnabled = SettingsManager.isShakeEnabled,
        onToggle = { SettingsManager.isShakeEnabled = it; SettingsManager.save() },
        sensitivity = SettingsManager.internalShakeThreshold,
        onSensitivityChange = { SettingsManager.internalShakeThreshold = it; SettingsManager.save() }
    )
}

@Composable
fun SqueezeDetectorCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    viewModel: DetectorViewModel = viewModel(),
    isExperimental: Boolean = false
) {
    val state by viewModel.squeezeState.collectAsState()
    SectionCard(
        title = "Squeeze Calibration", 
        isExperimental = isExperimental,
        actions = { AnimatedSwitch(checked = isEnabled, onCheckedChange = onToggle) }
    ) {
        Text("Magnitude: ${state.liveMagnitude.toInt()} / Baseline: ${state.baselineMagnitude.toInt()}")
        SliderWithTick(
            value = state.squeezeThresholdPercent.toFloat(),
            onValueChange = { viewModel.onSqueezeThresholdChange(applySnap(it, SettingsManager.snapSqueeze)) },
            valueRange = 0.05f..0.95f,
            defaultValue = 0.5f,
            enabled = isEnabled
        )
        Button(onClick = { viewModel.recalibrateSqueezeDetector() }, enabled = isEnabled) { Text("Recalibrate") }
    }
}

@Composable
fun ShakeDetectorCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    sensitivity: Float,
    onSensitivityChange: (Float) -> Unit,
    isExperimental: Boolean = false
) {
    SectionCard(
        title = "Wrist Snap", 
        isExperimental = isExperimental,
        actions = { AnimatedSwitch(checked = isEnabled, onCheckedChange = onToggle) }
    ) {
        Text("Sensitivity: ${sensitivity.toInt()}")
        SliderWithTick(
            value = sensitivity,
            onValueChange = { onSensitivityChange(applySnap(it, SettingsManager.snapShake)) },
            valueRange = 5f..50f,
            defaultValue = 40f,
            enabled = isEnabled
        )
    }
}
