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

    private var isSqueezeEnabled by mutableStateOf(false)
    private var isShakeEnabled by mutableStateOf(false)
    private var internalShakeThreshold by mutableStateOf(15f)
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

        isSqueezeEnabled = SettingsManager.isSqueezeEnabled
        isShakeEnabled = SettingsManager.isShakeEnabled
        internalShakeThreshold = SettingsManager.internalShakeThreshold

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
                    isSqueezeEnabled = isSqueezeEnabled,
                    onSqueezeToggle = {
                        isSqueezeEnabled = it
                        handleDetectionStateChange(isModeActiveInUI())
                    },
                    isShakeEnabled = isShakeEnabled,
                    onShakeToggle = {
                        isShakeEnabled = it
                        handleDetectionStateChange(isModeActiveInUI())
                    },
                    shakeSensitivityValue = 55f - internalShakeThreshold,
                    onShakeSensitivityChange = { uiValue ->
                        val newThreshold = 55f - uiValue
                        internalShakeThreshold = newThreshold
                        handleDetectionStateChange(isModeActiveInUI())
                    },
                    onToggleDetection = { isActive -> handleDetectionStateChange(isActive) },
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

    private fun handleDetectionStateChange(isModeActive: Boolean) {
        _isModeActiveHack = isModeActive
        val intent = Intent(this, HapticService::class.java)
        if (isModeActive) {
            runWithNotificationPermission {
                intent.action = HapticService.ACTION_START
                intent.putExtra(HapticService.EXTRA_SQUEEZE_ENABLED, isSqueezeEnabled)
                intent.putExtra(HapticService.EXTRA_SHAKE_ENABLED, isShakeEnabled)
                intent.putExtra(HapticService.EXTRA_SHAKE_THRESHOLD, internalShakeThreshold)
                ContextCompat.startForegroundService(this, intent)
            }
            if (isSqueezeEnabled) squeezeDetector.start() else squeezeDetector.stop()
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
    isSqueezeEnabled: Boolean,
    onSqueezeToggle: (Boolean) -> Unit,
    isShakeEnabled: Boolean,
    onShakeToggle: (Boolean) -> Unit,
    shakeSensitivityValue: Float,
    onShakeSensitivityChange: (Float) -> Unit,
    onToggleDetection: (Boolean) -> Unit,
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
                        isSqueezeEnabled,
                        onSqueezeToggle,
                        isShakeEnabled,
                        onShakeToggle,
                        shakeSensitivityValue,
                        onShakeSensitivityChange,
                        onToggleDetection,
                        detectorViewModelFactory
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
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        SectionCard(title = "Slider Snapping") {
            Column {
                Text(
                    "Specify the snapping increment for each slider. For percentage-based sliders (Intensity, Volume), 2 means 2%.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Spacer(Modifier.height(12.dp))

                SnapSettingRow(
                    "Intensity Snap (%)",
                    SettingsManager.snapIntensity,
                    isPercentage = true
                ) { SettingsManager.snapIntensity = it }
                SnapSettingRow("BPM Snap (Units)", SettingsManager.snapBpm, isPercentage = false) {
                    SettingsManager.snapBpm = it
                }
                SnapSettingRow(
                    "Duration Snap (Units)",
                    SettingsManager.snapDuration,
                    isPercentage = false
                ) { SettingsManager.snapDuration = it }
                SnapSettingRow(
                    "Media Volume Snap (%)",
                    SettingsManager.snapVolume,
                    isPercentage = true
                ) { SettingsManager.snapVolume = it }
                SnapSettingRow(
                    "BB Max Intensity Snap (%)",
                    SettingsManager.snapBeatMaxIntensity,
                    isPercentage = true
                ) { SettingsManager.snapBeatMaxIntensity = it }
                SnapSettingRow(
                    "Squeeze Sensitivity Snap (%)",
                    SettingsManager.snapSqueeze,
                    isPercentage = true
                ) { SettingsManager.snapSqueeze = it }
                SnapSettingRow(
                    "Shake Sensitivity Snap (Units)",
                    SettingsManager.snapShake,
                    isPercentage = false
                ) { SettingsManager.snapShake = it }
            }
        }
    }
}

@Composable
fun SnapSettingRow(label: String, value: Float, isPercentage: Boolean, onValueChange: (Float) -> Unit) {
    val initialDisplayValue =
        if (isPercentage) (value * 100f).roundToInt().toString() else value.toString()
    var textValue by remember { mutableStateOf(initialDisplayValue) }

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
                }
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
    isSqueezeEnabled: Boolean,
    onSqueezeToggle: (Boolean) -> Unit,
    isShakeEnabled: Boolean,
    onShakeToggle: (Boolean) -> Unit,
    shakeSensitivityValue: Float,
    onShakeSensitivityChange: (Float) -> Unit,
    onToggleDetection: (Boolean) -> Unit,
    detectorViewModelFactory: DetectorViewModelFactory
) {
    val context = LocalContext.current
    val detectorViewModel: DetectorViewModel = viewModel(factory = detectorViewModelFactory)
    val modesViewModel: ModesViewModel = viewModel()

    LaunchedEffect(modesViewModel.modeState.value.activeModes, isSqueezeEnabled, isShakeEnabled) {
        val isActiveHeartbeatActive =
            PTSDMode.ActiveHeartbeat in modesViewModel.modeState.value.activeModes
        val isBBPlayerActive = PTSDMode.BBPlayer in modesViewModel.modeState.value.activeModes

        onToggleDetection(isActiveHeartbeatActive || isBBPlayerActive)

        val intent = Intent(context, HapticService::class.java).apply {
            action = HapticService.ACTION_UPDATE_MODES
            putExtra(HapticService.EXTRA_BB_PLAYER_ACTIVE, isBBPlayerActive)
            putExtra(HapticService.EXTRA_HEARTBEAT_ACTIVE, isActiveHeartbeatActive)
        }
        if (isActiveHeartbeatActive || isBBPlayerActive) {
            (context as? MainActivity)?.runWithNotificationPermission {
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
        SqueezeDetectorCard(isSqueezeEnabled, onSqueezeToggle, viewModel = detectorViewModel)
        Spacer(modifier = Modifier.height(16.dp))
        ShakeDetectorCard(isShakeEnabled, onShakeToggle, shakeSensitivityValue, onShakeSensitivityChange)
        Spacer(modifier = Modifier.height(16.dp))
        HapticControlCard()
        Spacer(modifier = Modifier.height(16.dp))
        MediaFoldersCard()
        Spacer(modifier = Modifier.height(16.dp))
        LoggerCard()
        Spacer(modifier = Modifier.height(16.dp))
    }
}
