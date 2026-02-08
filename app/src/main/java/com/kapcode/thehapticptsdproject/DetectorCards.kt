package com.kapcode.thehapticptsdproject

import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kapcode.thehapticptsdproject.composables.SectionCard

@Composable
fun SqueezeDetectorCard(
    isEnabled: Boolean,
    onToggle: (Boolean) -> Unit,
    viewModel: DetectorViewModel = viewModel()
) {
    val state by viewModel.squeezeState.collectAsState()
    SectionCard(title = "Squeeze Calibration", actions = { Switch(isEnabled, onToggle) }) {
        Text("Magnitude: ${state.liveMagnitude.toInt()} / Baseline: ${state.baselineMagnitude.toInt()}")
        Slider(
            value = state.squeezeThresholdPercent.toFloat(),
            onValueChange = { viewModel.onSqueezeThresholdChange(applySnap(it, SettingsManager.snapSqueeze)) },
            valueRange = 0.05f..0.95f,
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
    onSensitivityChange: (Float) -> Unit
) {
    SectionCard(title = "Wrist Snap", actions = { Switch(isEnabled, onToggle) }) {
        Text("Sensitivity: ${sensitivity.toInt()}")
        Slider(
            value = sensitivity,
            onValueChange = { onSensitivityChange(applySnap(it, SettingsManager.snapShake)) },
            valueRange = 5f..50f,
            enabled = isEnabled
        )
    }
}
