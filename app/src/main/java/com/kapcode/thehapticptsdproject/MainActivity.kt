package com.kapcode.thehapticptsdproject

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kapcode.thehapticptsdproject.composables.SectionCard
import com.kapcode.thehapticptsdproject.composables.SliderWithTick
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun ScaleSettingRow(label: String, value: Float, defaultValue: Float, snapValue: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("$label: ${String.format(Locale.US, "%.1f", value)}x", style = MaterialTheme.typography.bodySmall)
        SliderWithTick(
            value = value,
            onValueChange = { onValueChange(applySnap(it, snapValue)) },
            valueRange = 0.5f..3f,
            defaultValue = defaultValue,
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
fun GainSettingRow(label: String, value: Float, defaultValue: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("$label: ${String.format(Locale.US, "%.1f", value)}", style = MaterialTheme.typography.bodySmall)
        SliderWithTick(
            value = value,
            onValueChange = { onValueChange(applySnap(it, SettingsManager.snapGain)) },
            valueRange = 0.1f..20f,
            defaultValue = defaultValue,
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
fun ThresholdSettingRow(label: String, value: Float, defaultValue: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("$label: ${(value * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
        SliderWithTick(
            value = value,
            onValueChange = { onValueChange(applySnap(it, SettingsManager.snapTriggerThreshold)) },
            valueRange = 0.01f..1f,
            defaultValue = defaultValue,
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
fun SnapSettingRow(label: String, value: Float, isPercentage: Boolean, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        val displayValue = if (isPercentage) "${(value * 100).toInt()}%" else String.format(Locale.US, "%.2f", value)
        Text("$label: $displayValue", style = MaterialTheme.typography.bodySmall)
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = if (isPercentage) 0f..0.2f else 0f..5f,
            modifier = Modifier.height(24.dp)
        )
    }
}

class MainActivity : ComponentActivity() {

    private var onPermissionGranted: (() -> Unit)? = null

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            onPermissionGranted?.invoke()
        }
        onPermissionGranted = null
    }

    fun runWithNotificationPermission(action: () -> Unit) {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isEmpty()) {
            action()
        } else {
            onPermissionGranted = action
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.init(this)
        HapticManager.init(this)
        BeatDetector.init()

        setContent {
            val playerVm: BeatPlayerViewModel = viewModel()
            val selectedProfile by playerVm.selectedProfile
            val profileColor = selectedProfile.getColor()

            MaterialTheme(colorScheme = darkColorScheme(primary = profileColor)) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    MainScreenWithDrawer(playerVm)
                }
            }
        }
        
        runWithNotificationPermission {
            // Initial permissions check
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreenWithDrawer(playerVm: BeatPlayerViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(16.dp))

                    SectionCard(title = "Visualizer Customization") {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { SettingsManager.isBarsEnabled = !SettingsManager.isBarsEnabled; SettingsManager.save() }) {
                                Checkbox(checked = SettingsManager.isBarsEnabled, onCheckedChange = { SettingsManager.isBarsEnabled = it; SettingsManager.save() })
                                Text("Vertical Bars (Frequencies)")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { SettingsManager.isChannelIntensityEnabled = !SettingsManager.isChannelIntensityEnabled; SettingsManager.save() }) {
                                Checkbox(checked = SettingsManager.isChannelIntensityEnabled, onCheckedChange = { SettingsManager.isChannelIntensityEnabled = it; SettingsManager.save() })
                                Text("Channel Intensity (Left/Right)")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { SettingsManager.isWaveformEnabled = !SettingsManager.isWaveformEnabled; SettingsManager.save() }) {
                                Checkbox(checked = SettingsManager.isWaveformEnabled, onCheckedChange = { SettingsManager.isWaveformEnabled = it; SettingsManager.save() })
                                Text("Waveform & Combo Elements")
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            Text("Visualizer Gain Control", style = MaterialTheme.typography.titleMedium)
                            GainSettingRow("Amplitude Gain", SettingsManager.gainAmplitude, 12f) { SettingsManager.gainAmplitude = it; SettingsManager.save() }
                            GainSettingRow("Bass Gain", SettingsManager.gainBass, 2.5f) { SettingsManager.gainBass = it; SettingsManager.save() }
                            GainSettingRow("Drum Gain", SettingsManager.gainDrum, 2.2f) { SettingsManager.gainDrum = it; SettingsManager.save() }
                            GainSettingRow("Guitar Gain", SettingsManager.gainGuitar, 1.8f) { SettingsManager.gainGuitar = it; SettingsManager.save() }
                            GainSettingRow("Highs Gain", SettingsManager.gainHighs, 1.5f) { SettingsManager.gainHighs = it; SettingsManager.save() }
                            
                            Spacer(Modifier.height(8.dp))
                            Text("Triggered Dimming (Alpha: ${(SettingsManager.visualizerTriggeredAlpha * 100).toInt()}% )", style = MaterialTheme.typography.bodyMedium)
                            SliderWithTick(
                                value = SettingsManager.visualizerTriggeredAlpha,
                                onValueChange = { 
                                    SettingsManager.visualizerTriggeredAlpha = applySnap(it, SettingsManager.snapTriggeredAlpha)
                                    SettingsManager.save()
                                },
                                valueRange = 0f..1f,
                                defaultValue = 0.1f
                            )

                            Spacer(Modifier.height(8.dp))
                            Text("Minimum Icon Alpha: ${(SettingsManager.minIconAlpha * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                            SliderWithTick(
                                value = SettingsManager.minIconAlpha,
                                onValueChange = { 
                                    SettingsManager.minIconAlpha = applySnap(it, SettingsManager.snapIconAlpha)
                                    SettingsManager.save()
                                },
                                valueRange = 0f..1f,
                                defaultValue = 0.2f
                            )

                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { SettingsManager.invertVisualizerAlpha = !SettingsManager.invertVisualizerAlpha; SettingsManager.save() }) {
                                Checkbox(checked = SettingsManager.invertVisualizerAlpha, onCheckedChange = { SettingsManager.invertVisualizerAlpha = it; SettingsManager.save() })
                                Text("Invert Icon Alpha (Bright when triggered)")
                            }

                            Spacer(Modifier.height(16.dp))
                            Text("Haptic/Audio Sync (Offset)", style = MaterialTheme.typography.titleMedium)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { SettingsManager.showOffsetSlider = !SettingsManager.showOffsetSlider; SettingsManager.save() }) {
                                Checkbox(checked = SettingsManager.showOffsetSlider, onCheckedChange = { SettingsManager.showOffsetSlider = it; SettingsManager.save() })
                                Text("Show offset slider in player")
                            }

                            Spacer(Modifier.height(4.dp))
                            Text("Offset: ${SettingsManager.hapticSyncOffsetMs}ms", style = MaterialTheme.typography.bodyMedium)
                            SliderWithTick(
                                value = SettingsManager.hapticSyncOffsetMs.toFloat(),
                                onValueChange = { 
                                    SettingsManager.hapticSyncOffsetMs = applySnap(it, SettingsManager.snapSyncOffset).toInt()
                                    SettingsManager.save()
                                },
                                valueRange = -200f..200f,
                                defaultValue = 60f
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    SectionCard(title = "Visualizer Trigger Thresholds") {
                        Column {
                            ThresholdSettingRow("Amplitude Icon Animation Trigger", SettingsManager.triggerThresholdAmplitude, 0.38f) {
                                SettingsManager.triggerThresholdAmplitude = it; SettingsManager.save()
                            }
                            ThresholdSettingRow("Bass Threshold", SettingsManager.triggerThresholdBass, 0.48f) {
                                SettingsManager.triggerThresholdBass = it; SettingsManager.save()
                            }
                            ThresholdSettingRow("Drum Threshold", SettingsManager.triggerThresholdDrum, 0.48f) {
                                SettingsManager.triggerThresholdDrum = it; SettingsManager.save()
                            }
                            ThresholdSettingRow("Guitar Threshold", SettingsManager.triggerThresholdGuitar, 0.48f) {
                                SettingsManager.triggerThresholdGuitar = it; SettingsManager.save()
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    SectionCard(title = "Visualizer Scaling") {
                        Column {
                            ScaleSettingRow("Vibration Visualizer Width", SettingsManager.scaleVibrationVisualizerX, 1.0f, SettingsManager.snapScaleVibration) {
                                SettingsManager.scaleVibrationVisualizerX = it; SettingsManager.save()
                            }
                            ScaleSettingRow("Vibration Visualizer Height", SettingsManager.scaleVibrationVisualizerY, 1.0f, SettingsManager.snapScaleVibration) {
                                SettingsManager.scaleVibrationVisualizerY = it; SettingsManager.save()
                            }
                            ScaleSettingRow("Audio Visualizer Width", SettingsManager.scaleAudioVisualizerX, 1.0f, SettingsManager.snapScaleAudio) {
                                SettingsManager.scaleAudioVisualizerX = it; SettingsManager.save()
                            }
                            ScaleSettingRow("Audio Visualizer Height", SettingsManager.scaleAudioVisualizerY, 1.0f, SettingsManager.snapScaleAudio) {
                                SettingsManager.scaleAudioVisualizerY = it; SettingsManager.save()
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    SectionCard(title = "Slider Snapping") {
                        Column {
                            Text("Whole Numbers", style = MaterialTheme.typography.titleMedium)
                            SnapSettingRow("BPM Snap (Units)", SettingsManager.snapBpm, isPercentage = false) {
                                SettingsManager.snapBpm = it; SettingsManager.save()
                            }
                            SnapSettingRow("Duration Snap (Units)", SettingsManager.snapDuration, isPercentage = false) {
                                SettingsManager.snapDuration = it; SettingsManager.save()
                            }
                            SnapSettingRow("Sync Offset Snap (ms)", SettingsManager.snapSyncOffset, isPercentage = false) {
                                SettingsManager.snapSyncOffset = it; SettingsManager.save()
                            }

                            Spacer(Modifier.height(16.dp))
                            Text("Decimals (Percentages)", style = MaterialTheme.typography.titleMedium)
                            SnapSettingRow("Intensity Snap (%)", SettingsManager.snapIntensity, isPercentage = true) {
                                SettingsManager.snapIntensity = it; SettingsManager.save()
                            }
                            SnapSettingRow("Media Volume Snap (%)", SettingsManager.snapVolume, isPercentage = true) {
                                SettingsManager.snapVolume = it; SettingsManager.save()
                            }
                            SnapSettingRow("BB Max Intensity Snap (%)", SettingsManager.snapBeatMaxIntensity, isPercentage = true) {
                                SettingsManager.snapBeatMaxIntensity = it; SettingsManager.save()
                            }
                            SnapSettingRow("Squeeze Sensitivity Snap (%)", SettingsManager.snapSqueeze, isPercentage = true) {
                                SettingsManager.snapSqueeze = it; SettingsManager.save()
                            }
                            SnapSettingRow("Triggered Alpha Snap (%)", SettingsManager.snapTriggeredAlpha, isPercentage = true) {
                                SettingsManager.snapTriggeredAlpha = it; SettingsManager.save()
                            }
                            SnapSettingRow("Icon Alpha Snap (%)", SettingsManager.snapIconAlpha, isPercentage = true) {
                                SettingsManager.snapIconAlpha = it; SettingsManager.save()
                            }

                            Spacer(Modifier.height(16.dp))
                            Text("Decimals (Units)", style = MaterialTheme.typography.titleMedium)
                            SnapSettingRow("Shake Sensitivity Snap (Units)", SettingsManager.snapShake, isPercentage = false) {
                                SettingsManager.snapShake = it; SettingsManager.save()
                            }
                            SnapSettingRow("Gain Snap (Units)", SettingsManager.snapGain, isPercentage = false) {
                                SettingsManager.snapGain = it; SettingsManager.save()
                            }
                            SnapSettingRow("Trigger Threshold Snap (Units)", SettingsManager.snapTriggerThreshold, isPercentage = false) {
                                SettingsManager.snapTriggerThreshold = it; SettingsManager.save()
                            }
                            SnapSettingRow("Vibration Scale Snap (Units)", SettingsManager.snapScaleVibration, isPercentage = false) {
                                SettingsManager.snapScaleVibration = it; SettingsManager.save()
                            }
                            SnapSettingRow("Audio Scale Snap (Units)", SettingsManager.snapScaleAudio, isPercentage = false) {
                                SettingsManager.snapScaleAudio = it; SettingsManager.save()
                            }
                        }
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("The Haptic PTSD Project") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Settings, contentDescription = "Settings")
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        titleContentColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        ) { padding ->
            MainScreen(modifier = Modifier.padding(padding), playerVm = playerVm)
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier, playerVm: BeatPlayerViewModel) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(8.dp))

        ModesSection()
        Spacer(Modifier.height(16.dp))

        HapticControlCard()
        Spacer(Modifier.height(16.dp))

        DetectorCards()
        Spacer(Modifier.height(16.dp))

        BeatPlayerCard(playerVm)
        Spacer(Modifier.height(16.dp))

        MediaFoldersCard()
        Spacer(Modifier.height(16.dp))

        LoggerCard()
        Spacer(Modifier.height(16.dp))

        SectionCard(
            title = "Experimental Features",
            isExperimental = true,
            actions = {
                Switch(
                    checked = SettingsManager.isExperimentalEnabled,
                    onCheckedChange = {
                        SettingsManager.isExperimentalEnabled = it
                        SettingsManager.save()
                    }
                )
            }
        ) {
            Text("Unlock features currently in development, such as Squeeze Detection via on-device sonar. These may be unstable.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        
        Spacer(Modifier.height(16.dp))
    }
}
