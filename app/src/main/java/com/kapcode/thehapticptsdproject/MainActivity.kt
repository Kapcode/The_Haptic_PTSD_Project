package com.kapcode.thehapticptsdproject

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kapcode.thehapticptsdproject.composables.*
import com.kapcode.thehapticptsdproject.ui.theme.TheHapticPTSDProjectTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private lateinit var squeezeDetector: SqueezeDetector
    private var actionToRunAfterPermission: (() -> Unit)? = null

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Logger.info("Permission granted.")
                actionToRunAfterPermission?.invoke()
            } else {
                Logger.error("Permission was not granted.")
            }
            actionToRunAfterPermission = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.init(this)
        HapticManager.init(this)
        BeatDetector.init()

        enableEdgeToEdge()

        HapticManager.updateIntensity(SettingsManager.intensity)
        HapticManager.updateBpm(SettingsManager.bpm)
        HapticManager.updateSessionDuration(SettingsManager.sessionDurationSeconds)

        squeezeDetector = SqueezeDetector {}
        squeezeDetector.setSqueezeThreshold(SettingsManager.squeezeThreshold.toDouble())
        val detectorViewModelFactory = DetectorViewModelFactory(squeezeDetector)

        setContent {
            TheHapticPTSDProjectTheme {
                MainAppContainer(
                    detectorViewModelFactory = detectorViewModelFactory
                )
            }
        }

        checkPermissions()
    }

    private var _isModeActiveHack = false
    private fun isModeActiveInUI(): Boolean = _isModeActiveHack

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing[0])
        }
    }

    fun runWithNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            when {
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    action()
                }
                else -> {
                    actionToRunAfterPermission = action
                    requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
            action()
        }
    }

    fun handleDetectionStateChange(isModeActive: Boolean) {
        _isModeActiveHack = isModeActive
        val intent = Intent(this, HapticService::class.java)
        if (isModeActive) {
            val squeezeEnabled = SettingsManager.isSqueezeEnabled && SettingsManager.isExperimentalEnabled
            runWithNotificationPermission {
                intent.action = HapticService.ACTION_START
                intent.putExtra(HapticService.EXTRA_SQUEEZE_ENABLED, squeezeEnabled)
                intent.putExtra(HapticService.EXTRA_SHAKE_ENABLED, SettingsManager.isShakeEnabled)
                intent.putExtra(HapticService.EXTRA_SHAKE_THRESHOLD, SettingsManager.internalShakeThreshold)
                ContextCompat.startForegroundService(this, intent)
            }
            if (squeezeEnabled) squeezeDetector.start() else squeezeDetector.stop()
        } else {
            val playerState = BeatDetector.playerState.value
            if (!playerState.isPlaying && !playerState.isPaused) {
                intent.action = HapticService.ACTION_STOP
                stopService(intent)
                squeezeDetector.stop()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        squeezeDetector.stop()
    }
}

class DetectorViewModelFactory(private val squeezeDetector: SqueezeDetector) :
    ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DetectorViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DetectorViewModel(squeezeDetector) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}

enum class Screen { Home, Settings }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppContainer(
    detectorViewModelFactory: DetectorViewModelFactory
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var currentScreen by remember { mutableStateOf(Screen.Home) }

    BackHandler(enabled = drawerState.isOpen || currentScreen != Screen.Home) {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            currentScreen = Screen.Home
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Spacer(Modifier.height(12.dp))
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Home, null) },
                    label = { Text("Home") },
                    selected = currentScreen == Screen.Home,
                    onClick = {
                        currentScreen = Screen.Home
                        scope.launch { drawerState.close() }
                    }
                )
                NavigationDrawerItem(
                    icon = { Icon(Icons.Default.Settings, null) },
                    label = { Text("Settings") },
                    selected = currentScreen == Screen.Settings,
                    onClick = {
                        currentScreen = Screen.Settings
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(if (currentScreen == Screen.Home) "The Haptic PTSD Project" else "Settings") },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(imageVector = Icons.Default.Menu, contentDescription = "Menu")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.primary,
                    )
                )
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                if (currentScreen == Screen.Home) {
                    MainScreen(
                        detectorViewModelFactory = detectorViewModelFactory
                    )
                } else {
                    SettingsScreen()
                }
            }
        }
    }
}

@Composable
fun SettingsScreen() {
    var showResetDialog by remember { mutableStateOf(false) }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset to Defaults") },
            text = { Text("Are you sure you want to reset all settings to their default values? This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        SettingsManager.resetToDefaults()
                        showResetDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionCard(
            title = "Visual Feedback",
            actions = {
                TextButton(onClick = { showResetDialog = true }) {
                    Text("Reset", color = MaterialTheme.colorScheme.error)
                }
            }
        ) {
            Column {
                Text("Active Visualizer Layers", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { SettingsManager.isBarsEnabled = !SettingsManager.isBarsEnabled; SettingsManager.save() }) {
                    Checkbox(checked = SettingsManager.isBarsEnabled, onCheckedChange = { SettingsManager.isBarsEnabled = it; SettingsManager.save() })
                    Text("Vertical Bars (Live HZ)")
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { SettingsManager.isChannelIntensityEnabled = !SettingsManager.isChannelIntensityEnabled; SettingsManager.save() }) {
                    Checkbox(checked = SettingsManager.isChannelIntensityEnabled, onCheckedChange = { SettingsManager.isChannelIntensityEnabled = it; SettingsManager.save() })
                    Text("Channel Intensity (L/R)")
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
                Text(
                    "Adjust if haptics feel slightly off from the audio. Default is 30ms (haptics ahead).",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(4.dp))
                
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
                Text(
                    "Adjust the sensitivity for each LIVE visualizer icon. This does NOT affect audio analysis.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(4.dp))
                Row {
                    Text("Bar Threshold: ", style = MaterialTheme.typography.bodySmall)
                    Text("White", color = Color.White, style = MaterialTheme.typography.bodySmall)
                    Text(" / Icon Trigger: ", style = MaterialTheme.typography.bodySmall)
                    Text("Red", color = Color.Red, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(12.dp))
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

        SectionCard(title = "Slider Snapping") {
            Column {
                Text(
                    "Specify the snapping increment for each slider. Use 0 for no snapping.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))

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
            }
        }
    }
}

@Composable
fun ThresholdSettingRow(label: String, value: Float, defaultValue: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("$label: ${String.format("%.2f", value)}", style = MaterialTheme.typography.bodySmall)
        SliderWithTick(
            value = value,
            onValueChange = { onValueChange(applySnap(it, SettingsManager.snapTriggerThreshold)) },
            valueRange = 0.1f..1.0f,
            defaultValue = defaultValue,
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
fun GainSettingRow(label: String, value: Float, defaultValue: Float, onValueChange: (Float) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text("$label: ${String.format("%.1f", value)}", style = MaterialTheme.typography.bodySmall)
        SliderWithTick(
            value = value,
            onValueChange = { onValueChange(applySnap(it, SettingsManager.snapGain)) },
            valueRange = 0.1f..30f,
            defaultValue = defaultValue,
            modifier = Modifier.height(24.dp)
        )
    }
}

@Composable
fun SnapSettingRow(label: String, value: Float, isPercentage: Boolean, onValueChange: (Float) -> Unit) {
    val initialDisplayValue = if (value == 0f) "0" else {
        if (isPercentage) (value * 100f).roundToInt().toString() else {
            if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()
        }
    }
    var textValue by remember(value) { mutableStateOf(initialDisplayValue) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        OutlinedTextField(
            value = textValue,
            onValueChange = {
                textValue = it
                it.toFloatOrNull()?.let { f ->
                    val newValue = if (isPercentage) f / 100f else f
                    onValueChange(newValue)
                } ?: if (it.isEmpty()) {
                    onValueChange(0f)
                } else { /* ignore invalid input */ }
            },
            modifier = Modifier.width(100.dp),
            label = { Text("Snap Value", fontSize = 10.sp) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true
        )
    }
}

@Composable
fun MainScreen(
    detectorViewModelFactory: DetectorViewModelFactory
) {
    val context = LocalContext.current
    val detectorViewModel: DetectorViewModel = viewModel(factory = detectorViewModelFactory)
    val modesViewModel: ModesViewModel = viewModel()

    LaunchedEffect(modesViewModel.modeState.value.activeModes, SettingsManager.isSqueezeEnabled, SettingsManager.isShakeEnabled, SettingsManager.isExperimentalEnabled) {
        val isActiveHeartbeatActive =
            PTSDMode.ActiveHeartbeat in modesViewModel.modeState.value.activeModes
        val isBBPlayerActive = PTSDMode.BBPlayer in modesViewModel.modeState.value.activeModes

        val mainActivity = (context as? MainActivity)
        mainActivity?.handleDetectionStateChange(isActiveHeartbeatActive || isBBPlayerActive)

        val intent = Intent(context, HapticService::class.java).apply {
            action = HapticService.ACTION_UPDATE_MODES
            putExtra(HapticService.EXTRA_BB_PLAYER_ACTIVE, isBBPlayerActive)
            putExtra(HapticService.EXTRA_HEARTBEAT_ACTIVE, isActiveHeartbeatActive)
        }
        if (isActiveHeartbeatActive || isBBPlayerActive) {
            mainActivity?.runWithNotificationPermission {
                context.startService(intent)
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        ModesSection(viewModel = modesViewModel)
        Spacer(modifier = Modifier.height(16.dp))
        BeatPlayerCard()
        Spacer(modifier = Modifier.height(16.dp))
        
        if (SettingsManager.isExperimentalEnabled) {
            SqueezeDetectorCard(
                isEnabled = SettingsManager.isSqueezeEnabled, 
                onToggle = { SettingsManager.isSqueezeEnabled = it; SettingsManager.save() }, 
                viewModel = detectorViewModel,
                isExperimental = true
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        ShakeDetectorCard(SettingsManager.isShakeEnabled, { SettingsManager.isShakeEnabled = it; SettingsManager.save() }, 55f - SettingsManager.internalShakeThreshold, {
            SettingsManager.internalShakeThreshold = 55f - it
            SettingsManager.save()
        })
        Spacer(modifier = Modifier.height(16.dp))
        HapticControlCard()
        Spacer(modifier = Modifier.height(16.dp))
        MediaFoldersCard()
        Spacer(modifier = Modifier.height(16.dp))
        LoggerCard()
        Spacer(modifier = Modifier.height(16.dp))
    }
}
