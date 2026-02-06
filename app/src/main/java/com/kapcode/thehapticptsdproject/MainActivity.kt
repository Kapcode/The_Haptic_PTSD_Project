package com.kapcode.thehapticptsdproject

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.kapcode.thehapticptsdproject.ui.theme.TheHapticPTSDProjectTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private lateinit var hapticManager: HapticManager
    private lateinit var squeezeDetector: SqueezeDetector
    private lateinit var beatDetector: BeatDetector

    // State for switches and sensitivity (initialized from SettingsManager)
    private var isSqueezeEnabled by mutableStateOf(false)
    private var isShakeEnabled by mutableStateOf(false)
    private var internalShakeThreshold by mutableStateOf(15f)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Logger.info("Permissions granted.")
                handleDetectionStateChange(isModeActiveInUI())
            } else {
                Logger.error("Permissions denied.")
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SettingsManager.init(this)
        HapticManager.init(this)
        beatDetector = BeatDetector(this)
        
        isSqueezeEnabled = SettingsManager.isSqueezeEnabled
        isShakeEnabled = SettingsManager.isShakeEnabled
        internalShakeThreshold = SettingsManager.internalShakeThreshold

        enableEdgeToEdge()

        hapticManager = HapticManager
        hapticManager.updateIntensity(SettingsManager.intensity)
        hapticManager.updateBpm(SettingsManager.bpm)
        hapticManager.updateSessionDuration(SettingsManager.sessionDurationSeconds)

        // Local detector for calibration UI
        squeezeDetector = SqueezeDetector {}
        squeezeDetector.setSqueezeThreshold(SettingsManager.squeezeThreshold.toDouble())

        setContent {
            TheHapticPTSDProjectTheme {
                MainScreen(
                    squeezeDetector = squeezeDetector,
                    hapticManager = hapticManager,
                    beatDetector = beatDetector,
                    isSqueezeEnabled = isSqueezeEnabled,
                    onSqueezeToggle = { 
                        isSqueezeEnabled = it
                        SettingsManager.isSqueezeEnabled = it
                        handleDetectionStateChange(isModeActiveInUI())
                    },
                    isShakeEnabled = isShakeEnabled,
                    onShakeToggle = { 
                        isShakeEnabled = it
                        SettingsManager.isShakeEnabled = it
                        handleDetectionStateChange(isModeActiveInUI())
                    },
                    shakeSensitivityValue = 55f - internalShakeThreshold,
                    onShakeSensitivityChange = { uiValue -> 
                        val newThreshold = 55f - uiValue
                        internalShakeThreshold = newThreshold
                        SettingsManager.internalShakeThreshold = newThreshold
                        handleDetectionStateChange(isModeActiveInUI())
                    },
                    onToggleDetection = { isActive -> handleDetectionStateChange(isActive) }
                )
            }
        }
        
        checkPermissions()
    }

    private var _isModeActiveHack = false
    private fun isModeActiveInUI(): Boolean = _isModeActiveHack

    private fun checkPermissions() {
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (missing.isNotEmpty()) {
            requestPermissionLauncher.launch(missing[0])
        }
    }

    private fun handleDetectionStateChange(isModeActive: Boolean) {
        _isModeActiveHack = isModeActive
        val intent = Intent(this, HapticService::class.java)
        if (isModeActive) {
            intent.action = HapticService.ACTION_START
            intent.putExtra(HapticService.EXTRA_SQUEEZE_ENABLED, isSqueezeEnabled)
            intent.putExtra(HapticService.EXTRA_SHAKE_ENABLED, isShakeEnabled)
            intent.putExtra(HapticService.EXTRA_SHAKE_THRESHOLD, internalShakeThreshold)
            ContextCompat.startForegroundService(this, intent)
            
            if (isSqueezeEnabled) squeezeDetector.start() else squeezeDetector.stop()
        } else {
            intent.action = HapticService.ACTION_STOP
            stopService(intent)
            squeezeDetector.stop()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        squeezeDetector.stop()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    squeezeDetector: SqueezeDetector,
    hapticManager: HapticManager,
    beatDetector: BeatDetector,
    isSqueezeEnabled: Boolean,
    onSqueezeToggle: (Boolean) -> Unit,
    isShakeEnabled: Boolean,
    onShakeToggle: (Boolean) -> Unit,
    shakeSensitivityValue: Float,
    onShakeSensitivityChange: (Float) -> Unit,
    onToggleDetection: (Boolean) -> Unit
) {
    var modeState by remember { mutableStateOf(ModeState(activeModes = emptySet())) }

    LaunchedEffect(modeState.activeModes, isSqueezeEnabled, isShakeEnabled) {
        val isActiveHeartbeatActive = PTSDMode.ActiveHeartbeat in modeState.activeModes
        onToggleDetection(isActiveHeartbeatActive)
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("The Haptic PTSD Project") },
                navigationIcon = {
                    IconButton(onClick = { /* TODO: Open drawer */ }) {
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
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            ModesSection(
                activeModes = modeState.activeModes,
                onModeToggled = { mode ->
                    val currentModes = modeState.activeModes.toMutableSet()
                    if (mode in currentModes) {
                        currentModes.remove(mode)
                        Logger.info("${mode.name} deactivated.")
                    } else {
                        currentModes.add(mode)
                        Logger.info("${mode.name} activated.")
                    }
                    modeState = modeState.copy(activeModes = currentModes)
                }
            )
            Spacer(modifier = Modifier.height(16.dp))
            BeatPlayerCard(beatDetector)
            Spacer(modifier = Modifier.height(16.dp))
            SqueezeDetectorCard(squeezeDetector, isSqueezeEnabled, onSqueezeToggle)
            Spacer(modifier = Modifier.height(16.dp))
            ShakeDetectorCard(isShakeEnabled, onShakeToggle, shakeSensitivityValue, onShakeSensitivityChange)
            Spacer(modifier = Modifier.height(16.dp))
            HapticControlCard(hapticManager)
            Spacer(modifier = Modifier.height(16.dp))
            MediaFoldersCard()
            Spacer(modifier = Modifier.height(16.dp))
            SectionCard(title = "Alarm Settings") {
                Text("Configure triggers and wake-up alerts.")
            }
            Spacer(modifier = Modifier.height(16.dp))
            LoggerCard()
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BeatPlayerCard(beatDetector: BeatDetector) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerState by beatDetector.playerState.collectAsState()
    
    var selectedProfile by remember { mutableStateOf(BeatProfile.AMPLITUDE) }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var selectedFileName by remember { mutableStateOf("No file selected") }
    var analysisProgress by remember { mutableStateOf(0f) }
    var isAnalyzing by remember { mutableStateOf(false) }
    var detectedBeats by remember { mutableStateOf<List<DetectedBeat>>(emptyList()) }

    // List of audio files in authorized folders
    var audioFiles by remember { mutableStateOf<List<Pair<String, Uri>>>(emptyList()) }
    var expandedFiles by remember { mutableStateOf(false) }
    var expandedProfiles by remember { mutableStateOf(false) }

    val folderUris = remember { SettingsManager.authorizedFolderUris }

    // Refresh file list when folders change
    LaunchedEffect(folderUris) {
        val files = mutableListOf<Pair<String, Uri>>()
        folderUris.forEach { uriString ->
            val rootUri = Uri.parse(uriString)
            try {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    rootUri,
                    DocumentsContract.getTreeDocumentId(rootUri)
                )
                context.contentResolver.query(
                    childrenUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE),
                    null, null, null
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val name = cursor.getString(0)
                        val id = cursor.getString(1)
                        val mime = cursor.getString(2)
                        if (mime.startsWith("audio/")) {
                            files.add(name to DocumentsContract.buildDocumentUriUsingTree(rootUri, id))
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.error("Access error for $uriString: ${e.message}")
            }
        }
        audioFiles = files
    }

    // Auto-load profile when track or profile changes
    LaunchedEffect(selectedFileUri, selectedProfile) {
        val uri = selectedFileUri
        if (uri != null) {
            val rootUri = folderUris.firstOrNull { uri.toString().startsWith(it) }?.let { Uri.parse(it) }
            if (rootUri != null) {
                val existingProfileUri = beatDetector.findExistingProfile(rootUri, selectedFileName, selectedProfile)
                if (existingProfileUri != null) {
                    val beats = beatDetector.loadProfile(existingProfileUri)
                    if (beats != null) {
                        detectedBeats = beats
                        Logger.info("Profile loaded automatically.")
                    }
                } else {
                    detectedBeats = emptyList()
                }
            }
        }
    }

    SectionCard(title = "Bilateral Beat Player") {
        Column {
            // Track Selector
            Box {
                OutlinedButton(onClick = { expandedFiles = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedFileName)
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = expandedFiles, onDismissRequest = { expandedFiles = false }) {
                    if (audioFiles.isEmpty()) {
                        DropdownMenuItem(text = { Text("No audio files found") }, onClick = { expandedFiles = false })
                    }
                    audioFiles.forEach { (name, uri) ->
                        DropdownMenuItem(
                            text = { Text(name) },
                            onClick = {
                                selectedFileUri = uri
                                selectedFileName = name
                                expandedFiles = false
                                detectedBeats = emptyList()
                                Logger.info("Selected track: $name")
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Profile Selector
            Box {
                OutlinedButton(onClick = { expandedProfiles = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Profile: ${selectedProfile.name}")
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = expandedProfiles, onDismissRequest = { expandedProfiles = false }) {
                    BeatProfile.entries.forEach { profile ->
                        DropdownMenuItem(
                            text = { Text(profile.name) },
                            onClick = {
                                selectedProfile = profile
                                expandedProfiles = false
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (isAnalyzing) {
                Text("Analyzing beats: ${(analysisProgress * 100).toInt()}%")
                LinearProgressIndicator(progress = { analysisProgress }, modifier = Modifier.fillMaxWidth())
            } else if (detectedBeats.isEmpty()) {
                Button(
                    onClick = {
                        val uri = selectedFileUri
                        if (uri != null) {
                            val rootUri = folderUris.firstOrNull { uri.toString().startsWith(it) }?.let { Uri.parse(it) }
                            if (rootUri != null) {
                                isAnalyzing = true
                                scope.launch {
                                    val result = beatDetector.analyzeAudioUri(uri, selectedProfile) { progress ->
                                        analysisProgress = progress
                                    }
                                    if (result.isNotEmpty()) {
                                        beatDetector.saveProfile(rootUri, selectedFileName, selectedProfile, result)
                                        detectedBeats = result
                                    }
                                    isAnalyzing = false
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedFileUri != null && !playerState.isPlaying
                ) {
                    Text("Analyze Audio")
                }
            }

            if (detectedBeats.isNotEmpty() || playerState.isPlaying) {
                if (playerState.isPlaying) {
                    val progress = if (playerState.totalDurationMs > 0) playerState.currentTimestampMs.toFloat() / playerState.totalDurationMs.toFloat() else 0f
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Playing: ${(playerState.currentTimestampMs/1000)}s / ${(playerState.totalDurationMs/1000)}s", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { beatDetector.stopPlayback() },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Text("Stop Playback")
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${detectedBeats.size} beats ready", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        
                        Button(
                            onClick = { 
                                val uri = selectedFileUri
                                if (uri != null) beatDetector.playSynchronized(uri, detectedBeats)
                            },
                            modifier = Modifier.combinedClickable(
                                onClick = { 
                                    val uri = selectedFileUri
                                    if (uri != null) beatDetector.playSynchronized(uri, detectedBeats)
                                },
                                onLongClick = {
                                    val uri = selectedFileUri
                                    val rootUri = folderUris.firstOrNull { uri.toString().startsWith(it) }?.let { Uri.parse(it) }
                                    if (uri != null && rootUri != null) {
                                        isAnalyzing = true
                                        scope.launch {
                                            val result = beatDetector.analyzeAudioUri(uri, selectedProfile) { progress ->
                                                analysisProgress = progress
                                            }
                                            if (result.isNotEmpty()) {
                                                beatDetector.saveProfile(rootUri, selectedFileName, selectedProfile, result)
                                                detectedBeats = result
                                            }
                                            isAnalyzing = false
                                        }
                                    }
                                }
                            ).weight(1f).padding(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Text("Play (Hold to Refresh)")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaFoldersCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    var folderUris by remember { mutableStateOf(SettingsManager.authorizedFolderUris) }
    
    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(it, flags)
            
            val newUris = folderUris.toMutableSet()
            newUris.add(it.toString())
            folderUris = newUris
            SettingsManager.authorizedFolderUris = newUris
            Logger.info("Added authorized folder: ${it.path}")
        }
    }

    SectionCard(
        title = "Media Folders",
        actions = {
            IconButton(onClick = { openFolderLauncher.launch(null) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Folder")
            }
        }
    ) {
        Column {
            if (folderUris.isEmpty()) {
                Text("No folders authorized. Add a folder to scan for audio files.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            } else {
                folderUris.forEach { uriString ->
                    val uri = Uri.parse(uriString)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = uri.path?.substringAfterLast(":") ?: "Folder",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = {
                            val newUris = folderUris.toMutableSet()
                            newUris.remove(uriString)
                            folderUris = newUris
                            SettingsManager.authorizedFolderUris = newUris
                            Logger.info("Removed folder access.")
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SqueezeDetectorCard(detector: SqueezeDetector, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    val state by detector.state.collectAsState()

    SectionCard(
        title = "Squeeze Calibration",
        actions = {
            Switch(checked = isEnabled, onCheckedChange = onToggle)
        }
    ) {
        Column {
            Text("Live Magnitude: ${state.liveMagnitude.roundToInt()}")
            Text("Baseline: ${state.baselineMagnitude.roundToInt()}")
            Spacer(modifier = Modifier.height(8.dp))
            val sensitivity = (state.squeezeThresholdPercent * 100).roundToInt()
            Text("Sensitivity: $sensitivity%")
            Slider(
                value = state.squeezeThresholdPercent.toFloat(),
                onValueChange = { 
                    val snappedValue = (Math.round(it * 20) / 20f).coerceIn(0.05f, 0.95f)
                    detector.setSqueezeThreshold(snappedValue.toDouble())
                    SettingsManager.squeezeThreshold = snappedValue
                },
                valueRange = 0.05f..0.95f,
                steps = 17,
                enabled = isEnabled
            )
            Spacer(modifier = Modifier.height(8.dp))
            Button(onClick = { detector.recalibrate() }, enabled = isEnabled) {
                Text("Recalibrate")
            }
        }
    }
}

@Composable
fun ShakeDetectorCard(isEnabled: Boolean, onToggle: (Boolean) -> Unit, sensitivity: Float, onSensitivityChange: (Float) -> Unit) {
    SectionCard(
        title = "Wrist Snap Detection",
        actions = {
            Switch(checked = isEnabled, onCheckedChange = onToggle)
        }
    ) {
        Column {
            val displaySensitivity = sensitivity.roundToInt()
            Text("Snap Sensitivity: $displaySensitivity")
            Slider(
                value = sensitivity,
                onValueChange = {
                    val snappedValue = Math.round(it / 5f) * 5f
                    onSensitivityChange(snappedValue)
                },
                valueRange = 5f..50f,
                steps = 8,
                enabled = isEnabled
            )
            Text(
                text = "Tuned for restless wrist flips. Higher = more sensitive.",
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

@Composable
fun HapticControlCard(hapticManager: HapticManager) {
    val state by hapticManager.state.collectAsState()

    SectionCard(title = "Haptic Heartbeat") {
        Column {
            AnimatedVisibility(visible = state.isHeartbeatRunning || state.isTestRunning) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    if (state.isTestRunning) {
                        Text(
                            text = "Test Sequence: ${state.testPulseCount}/3...",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    } else {
                        Text(
                            text = "Session Active: ${state.remainingSeconds}s",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { if (state.isTestRunning) state.testPulseCount.toFloat() / 3f else state.remainingSeconds.toFloat() / state.sessionDurationSeconds.toFloat() },
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                    )
                }
            }

            val intensityPercent = (state.intensity * 100).roundToInt()
            Text("Intensity: $intensityPercent%")
            Slider(
                value = state.intensity,
                onValueChange = { 
                    val snappedValue = (Math.round(it * 20) / 20f).coerceIn(0f, 1f)
                    hapticManager.updateIntensity(snappedValue)
                    SettingsManager.intensity = snappedValue
                },
                valueRange = 0f..1f,
                steps = 19,
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            Text("Heartbeat BPM: ${state.bpm}")
            Slider(
                value = state.bpm.toFloat(),
                onValueChange = { 
                    val snappedValue = Math.round(it / 5f) * 5f
                    val newBpm = snappedValue.toInt()
                    hapticManager.updateBpm(newBpm)
                    SettingsManager.bpm = newBpm
                },
                valueRange = 30f..200f,
                steps = 33,
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text("Session Duration: ${state.sessionDurationSeconds}s")
            Slider(
                value = state.sessionDurationSeconds.toFloat(),
                onValueChange = { 
                    val snappedValue = Math.round(it / 50f) * 50f
                    val newDuration = snappedValue.toInt()
                    hapticManager.updateSessionDuration(newDuration)
                    SettingsManager.sessionDurationSeconds = newDuration
                },
                valueRange = 50f..600f,
                steps = 10,
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { hapticManager.testPulseSequence() }, enabled = !state.isHeartbeatRunning && !state.isTestRunning) {
                    Text("Test Sequence")
                }
                
                if (state.isHeartbeatRunning) {
                    Button(onClick = { hapticManager.stopHeartbeatSession() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Stop Session")
                    }
                } else {
                    Button(onClick = { hapticManager.startHeartbeatSession() }, enabled = !state.isTestRunning) {
                        Text("Start Session")
                    }
                }
            }
        }
    }
}

@Composable
fun ModesSection(
    activeModes: Set<PTSDMode>,
    onModeToggled: (PTSDMode) -> Unit
) {
    val modes = listOf(PTSDMode.ActiveHeartbeat, PTSDMode.SleepAssistance, PTSDMode.GroundingMode)

    SectionCard(title = "Modes") {
        Column {
            modes.forEach { mode ->
                ModeItem(
                    mode = mode,
                    isSelected = mode in activeModes,
                    onClick = { onModeToggled(mode) }
                )
            }
        }
    }
}

@Composable
fun ModeItem(mode: PTSDMode, isSelected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 4.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = mode.icon,
                contentDescription = mode.name,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.size(16.dp))
            Column {
                Text(text = mode.name, style = MaterialTheme.typography.titleMedium)
                Text(text = mode.description, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun LoggerCard() {
    val logHistory by Logger.logHistory.collectAsState()
    var selectedLogLevel by remember { mutableStateOf(LogLevel.DEBUG) }

    val filteredLogs = remember(logHistory, selectedLogLevel) {
        logHistory.filter { it.level.ordinal >= selectedLogLevel.ordinal }
    }

    SectionCard(
        title = "Logging",
        actions = {
            Row {
                LogLevel.entries.forEach { level ->
                    TextButton(
                        onClick = { selectedLogLevel = level },
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = if (selectedLogLevel == level) MaterialTheme.colorScheme.primary else Color.Gray
                        )
                    ) {
                        Text(level.name)
                    }
                }
            }
        }
    ) {
        Column {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
            ) {
                items(filteredLogs.reversed()) { log ->
                    val color = when (log.level) {
                        LogLevel.INFO -> Color.Unspecified
                        LogLevel.DEBUG -> Color.Gray
                        LogLevel.ERROR -> MaterialTheme.colorScheme.error
                    }
                    Text(text = log.toString(), style = MaterialTheme.typography.bodySmall, color = color)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    Logger.clear()
                    Logger.info("Logs cleared.")
                },
                modifier = Modifier.align(Alignment.End)
            ) {
                Text("Clear Logs")
            }
        }
    }
}

@Composable
fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    actions()
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            content()
        }
    }
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    TheHapticPTSDProjectTheme {
        val fakeDetector = SqueezeDetector {}
        MainScreen(
            squeezeDetector = fakeDetector,
            hapticManager = HapticManager,
            beatDetector = BeatDetector(LocalContext.current),
            isSqueezeEnabled = true,
            onSqueezeToggle = {},
            isShakeEnabled = true,
            onShakeToggle = {},
            shakeSensitivityValue = 30f,
            onShakeSensitivityChange = {},
            onToggleDetection = {}
        )
    }
}
