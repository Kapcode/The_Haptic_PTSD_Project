package com.kapcode.thehapticptsdproject

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kapcode.thehapticptsdproject.composables.AnimatedSlider
import com.kapcode.thehapticptsdproject.composables.AnimatedSwitch
import com.kapcode.thehapticptsdproject.composables.AnimationSettingsCard
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
fun SnapSettingRow(label: String, value: Float, isPercentage: Boolean, defaultValue: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        val displayValue = if (isPercentage) "${(value * 100).toInt()}%" else String.format(Locale.US, "%.2f", value)
        Text("$label: $displayValue", style = MaterialTheme.typography.bodySmall)
        SliderWithTick(
            value = value,
            onValueChange = onValueChange,
            valueRange = if (isPercentage) 0f..0.2f else 0f..5f,
            defaultValue = defaultValue,
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

            MaterialTheme(
                colorScheme = darkColorScheme(primary = profileColor),
                typography = Typography(
                    bodyMedium = TextStyle(fontSize = SettingsManager.fontSizeRegular.sp),
                    titleMedium = TextStyle(fontSize = SettingsManager.fontSizeCardTitle.sp),
                    labelLarge = TextStyle(fontSize = SettingsManager.fontSizeButton.sp, fontWeight = FontWeight.Bold),
                    bodySmall = TextStyle(fontSize = SettingsManager.fontSizeCardNotation.sp),
                    titleLarge = TextStyle(fontSize = SettingsManager.fontSizeCardHeading.sp, fontWeight = FontWeight.Bold)
                )
            ) {
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreenWithDrawer(playerVm: BeatPlayerViewModel) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset to Defaults") },
            text = { Text("Are you sure you want to reset all settings to their default values? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        ApplicationHapticEffects.onResetTriggered()
                        SettingsManager.resetToDefaults()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset", style = TextStyle(fontWeight = FontWeight.Bold))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(320.dp)
                        .padding(vertical = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(Modifier.height(16.dp))

                    ExperimentalSettingsCard()
                    Spacer(Modifier.height(16.dp))

                    AccessibilitySettingsCard()
                    Spacer(Modifier.height(16.dp))

                    LiveHapticSettingsCard()
                    Spacer(Modifier.height(16.dp))

                    SectionCard(title = "Player Settings") {
                        Column {
                            Text("Seekbar Time Window: ${SettingsManager.seekbarTimeWindowMinutes}m", style = MaterialTheme.typography.bodySmall)
                            SliderWithTick(
                                value = SettingsManager.seekbarTimeWindowMinutes.toFloat(),
                                onValueChange = { 
                                    SettingsManager.seekbarTimeWindowMinutes = it.toInt()
                                    SettingsManager.save()
                                },
                                valueRange = 1f..60f,
                                defaultValue = 5f
                            )
                            
                            Spacer(Modifier.height(16.dp))
                            Text("Seek Thumb Customization", style = MaterialTheme.typography.titleMedium)
                            Text("Width: ${SettingsManager.seekThumbWidth.toInt()}dp", style = MaterialTheme.typography.bodySmall)
                            SliderWithTick(
                                value = SettingsManager.seekThumbWidth,
                                onValueChange = { SettingsManager.seekThumbWidth = it; SettingsManager.save() },
                                valueRange = 2f..40f,
                                defaultValue = 12f
                            )
                            Text("Height: ${SettingsManager.seekThumbHeight.toInt()}dp", style = MaterialTheme.typography.bodySmall)
                            SliderWithTick(
                                value = SettingsManager.seekThumbHeight,
                                onValueChange = { SettingsManager.seekThumbHeight = it; SettingsManager.save() },
                                valueRange = 10f..100f,
                                defaultValue = 48f
                            )
                            Text("Alpha: ${(SettingsManager.seekThumbAlpha * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                            SliderWithTick(
                                value = SettingsManager.seekThumbAlpha,
                                onValueChange = { 
                                    val newAlpha = it.coerceAtLeast(0.40f)
                                    SettingsManager.seekThumbAlpha = newAlpha
                                    SettingsManager.save()
                                },
                                valueRange = 0.40f..1f,
                                defaultValue = 0.85f
                            )

                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { SettingsManager.showOffsetSlider = !SettingsManager.showOffsetSlider; SettingsManager.save() }) {
                                AnimatedSwitch(checked = SettingsManager.showOffsetSlider, onCheckedChange = { SettingsManager.showOffsetSlider = it; SettingsManager.save() })
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
                                valueRange = -2000f..2000f,
                                defaultValue = -1500f
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    
                    AnimationSettingsCard()

                    Spacer(Modifier.height(16.dp))

                    SectionCard(title = "Visualizer Customization") {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { SettingsManager.isBarsEnabled = !SettingsManager.isBarsEnabled; SettingsManager.save() }) {
                                AnimatedSwitch(checked = SettingsManager.isBarsEnabled, onCheckedChange = { SettingsManager.isBarsEnabled = it; SettingsManager.save() })
                                Text("Vertical Bars (Frequencies)")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { SettingsManager.isChannelIntensityEnabled = !SettingsManager.isChannelIntensityEnabled; SettingsManager.save() }) {
                                AnimatedSwitch(checked = SettingsManager.isChannelIntensityEnabled, onCheckedChange = { SettingsManager.isChannelIntensityEnabled = it; SettingsManager.save() })
                                Text("Channel Intensity (Left/Right)")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { SettingsManager.isWaveformEnabled = !SettingsManager.isWaveformEnabled; SettingsManager.save() }) {
                                AnimatedSwitch(checked = SettingsManager.isWaveformEnabled, onCheckedChange = { SettingsManager.isWaveformEnabled = it; SettingsManager.save() })
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
                                AnimatedSwitch(checked = SettingsManager.invertVisualizerAlpha, onCheckedChange = { SettingsManager.invertVisualizerAlpha = it; SettingsManager.save() })
                                Text("Invert Icon Alpha (Bright when triggered)")
                            }
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
                            SnapSettingRow("BPM Snap (Units)", SettingsManager.snapBpm, false, 0f) {
                                SettingsManager.snapBpm = it; SettingsManager.save()
                            }
                            SnapSettingRow("Duration Snap (Units)", SettingsManager.snapDuration, false, 0f) {
                                SettingsManager.snapDuration = it; SettingsManager.save()
                            }
                            SnapSettingRow("Sync Offset Snap (ms)", SettingsManager.snapSyncOffset, false, 0f) {
                                SettingsManager.snapSyncOffset = it; SettingsManager.save()
                            }

                            Spacer(Modifier.height(16.dp))
                            Text("Decimals (Percentages)", style = MaterialTheme.typography.titleMedium)
                            SnapSettingRow("Intensity Snap (%)", SettingsManager.snapIntensity, true, 0f) {
                                SettingsManager.snapIntensity = it; SettingsManager.save()
                            }
                            SnapSettingRow("Media Volume Snap (%)", SettingsManager.snapVolume, true, 0f) {
                                SettingsManager.snapVolume = it; SettingsManager.save()
                            }
                            SnapSettingRow("BB Max Intensity Snap (%)", SettingsManager.snapBeatMaxIntensity, true, 0f) {
                                SettingsManager.snapBeatMaxIntensity = it; SettingsManager.save()
                            }
                            SnapSettingRow("Squeeze Sensitivity Snap (%)", SettingsManager.snapSqueeze, true, 0f) {
                                SettingsManager.snapSqueeze = it; SettingsManager.save()
                            }
                            SnapSettingRow("Triggered Alpha Snap (%)", SettingsManager.snapTriggeredAlpha, true, 0f) {
                                SettingsManager.snapTriggeredAlpha = it; SettingsManager.save()
                            }
                            SnapSettingRow("Icon Alpha Snap (%)", SettingsManager.snapIconAlpha, true, 0f) {
                                SettingsManager.snapIconAlpha = it; SettingsManager.save()
                            }
                            SnapSettingRow("Default Value Snap (%)", SettingsManager.snapDefaultValue, true, 0.05f) {
                                SettingsManager.snapDefaultValue = it; SettingsManager.save()
                            }

                            Spacer(Modifier.height(16.dp))
                            Text("Decimals (Units)", style = MaterialTheme.typography.titleMedium)
                            SnapSettingRow("Shake Sensitivity Snap (Units)", SettingsManager.snapShake, false, 0f) {
                                SettingsManager.snapShake = it; SettingsManager.save()
                            }
                            SnapSettingRow("Gain Snap (Units)", SettingsManager.snapGain, false, 0f) {
                                SettingsManager.snapGain = it; SettingsManager.save()
                            }
                            SnapSettingRow("Trigger Threshold Snap (Units)", SettingsManager.snapTriggerThreshold, false, 0f) {
                                SettingsManager.snapTriggerThreshold = it; SettingsManager.save()
                            }
                            SnapSettingRow("Vibration Scale Snap (Units)", SettingsManager.snapScaleVibration, false, 0f) {
                                SettingsManager.snapScaleVibration = it; SettingsManager.save()
                            }
                            SnapSettingRow("Audio Scale Snap (Units)", SettingsManager.snapScaleAudio, false, 0f) {
                                SettingsManager.snapScaleAudio = it; SettingsManager.save()
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))
                    
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .combinedClickable(
                                onClick = {
                                    ApplicationHapticEffects.onButtonClick()
                                    Toast.makeText(context, "Hold to reset all settings", Toast.LENGTH_SHORT).show()
                                },
                                onLongClick = {
                                    ApplicationHapticEffects.onLongPress()
                                    showResetDialog = true
                                }
                            ),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                            Spacer(Modifier.width(8.dp))
                            // Fixed text size for reset button to ensure it stays legible
                            Text("Hold to Reset All Settings", color = MaterialTheme.colorScheme.error, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold))
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                CenterAlignedTopAppBar(
                    title = { Text("The Haptic PTSD Project") },
                    navigationIcon = {
                        IconButton(onClick = { 
                            ApplicationHapticEffects.onButtonClick()
                            scope.launch { drawerState.open() } 
                        }) {
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
    val context = LocalContext.current
    val modesViewModel: ModesViewModel = viewModel(factory = ModesViewModelFactory(context.applicationContext))
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(8.dp))

        ModesSection(viewModel = modesViewModel)
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
    }
}

@Composable
fun ExperimentalSettingsCard(viewModel: ExperimentalSettingsViewModel = viewModel()) {
    SectionCard(
        title = "Experimental Features",
        isExperimental = true,
        actions = {
            AnimatedSwitch(
                checked = viewModel.isExperimentalEnabled,
                onCheckedChange = { viewModel.onExperimentalEnabledChange(it) }
            )
        }
    ) {
        Text("Unlock features currently in development, such as Squeeze Detection via on-device sonar. These may be unstable.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
fun AccessibilitySettingsCard() {
    SectionCard(title = "Accessibility") {
        Column {
            Text("Font Sizes", style = MaterialTheme.typography.titleMedium)
            
            FontSizeSettingRow("Heading Font Size", SettingsManager.fontSizeCardHeading, 22f) {
                SettingsManager.fontSizeCardHeading = it; SettingsManager.save()
            }
            FontSizeSettingRow("Regular Font Size", SettingsManager.fontSizeRegular, 14f) {
                SettingsManager.fontSizeRegular = it; SettingsManager.save()
            }
            FontSizeSettingRow("Card Title Font Size", SettingsManager.fontSizeCardTitle, 18f) {
                SettingsManager.fontSizeCardTitle = it; SettingsManager.save()
            }
            FontSizeSettingRow("Button Text Size", SettingsManager.fontSizeButton, 14f) {
                SettingsManager.fontSizeButton = it; SettingsManager.save()
            }
            FontSizeSettingRow("Card Notation Font Size", SettingsManager.fontSizeCardNotation, 12f) {
                SettingsManager.fontSizeCardNotation = it; SettingsManager.save()
            }
        }
    }
}

@Composable
fun FontSizeSettingRow(label: String, value: Float, defaultValue: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("$label: ${value.toInt()}sp", style = MaterialTheme.typography.bodySmall)
        SliderWithTick(
            value = value,
            onValueChange = { onValueChange(it) },
            valueRange = 8f..32f,
            defaultValue = defaultValue,
            modifier = Modifier.height(24.dp)
        )
    }
}
