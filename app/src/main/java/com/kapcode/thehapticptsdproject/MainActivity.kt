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
import android.os.Build
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kapcode.thehapticptsdproject.ui.theme.TheHapticPTSDProjectTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

class MainActivity : ComponentActivity() {

    private lateinit var squeezeDetector: SqueezeDetector

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
        BeatDetector.init()
        
        isSqueezeEnabled = SettingsManager.isSqueezeEnabled
        isShakeEnabled = SettingsManager.isShakeEnabled
        internalShakeThreshold = SettingsManager.internalShakeThreshold

        enableEdgeToEdge()

        HapticManager.updateIntensity(SettingsManager.intensity)
        HapticManager.updateBpm(SettingsManager.bpm)
        HapticManager.updateSessionDuration(SettingsManager.sessionDurationSeconds)

        // Local detector for calibration UI
        squeezeDetector = SqueezeDetector {}
        squeezeDetector.setSqueezeThreshold(SettingsManager.squeezeThreshold.toDouble())

        setContent {
            TheHapticPTSDProjectTheme {
                MainScreen(
                    squeezeDetector = squeezeDetector,
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
    isSqueezeEnabled: Boolean,
    onSqueezeToggle: (Boolean) -> Unit,
    isShakeEnabled: Boolean,
    onShakeToggle: (Boolean) -> Unit,
    shakeSensitivityValue: Float,
    onShakeSensitivityChange: (Float) -> Unit,
    onToggleDetection: (Boolean) -> Unit
) {
    var modeState by remember { mutableStateOf(ModeState(activeModes = emptySet())) }
    val context = LocalContext.current

    LaunchedEffect(modeState.activeModes, isSqueezeEnabled, isShakeEnabled) {
        val isActiveHeartbeatActive = PTSDMode.ActiveHeartbeat in modeState.activeModes
        val isBBPlayerActive = PTSDMode.BBPlayer in modeState.activeModes
        
        onToggleDetection(isActiveHeartbeatActive || isBBPlayerActive)
        
        val intent = Intent(context, HapticService::class.java).apply {
            action = HapticService.ACTION_UPDATE_MODES
            putExtra(HapticService.EXTRA_BB_PLAYER_ACTIVE, isBBPlayerActive)
            putExtra(HapticService.EXTRA_HEARTBEAT_ACTIVE, isActiveHeartbeatActive)
        }
        if (isActiveHeartbeatActive || isBBPlayerActive) {
            context.startService(intent)
        }
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
            HapticVisualizerCard()
            Spacer(modifier = Modifier.height(16.dp))
            BeatPlayerCard()
            Spacer(modifier = Modifier.height(16.dp))
            SqueezeDetectorCard(squeezeDetector, isSqueezeEnabled, onSqueezeToggle)
            Spacer(modifier = Modifier.height(16.dp))
            ShakeDetectorCard(isShakeEnabled, onShakeToggle, shakeSensitivityValue, onShakeSensitivityChange)
            Spacer(modifier = Modifier.height(16.dp))
            HapticControlCard()
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

@Composable
fun HapticVisualizerCard() {
    val hState by HapticManager.state.collectAsState()
    
    SectionCard(title = "Live Haptic Output") {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Phone (Outside)
            VisualizerIcon(
                onRes = R.drawable.phone_left_on,
                offRes = R.drawable.phone_left_off,
                intensity = hState.phoneLeftIntensity,
                label = "Phone L"
            )

            // Left Controller (Inside)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VisualizerIcon(
                    onRes = R.drawable.controller_left_on,
                    offRes = R.drawable.controller_left_off,
                    intensity = hState.controllerLeftTopIntensity,
                    size = 32.dp
                )
                VisualizerIcon(
                    onRes = R.drawable.controller_left_on,
                    offRes = R.drawable.controller_left_off,
                    intensity = hState.controllerLeftBottomIntensity,
                    size = 32.dp
                )
                Text("Ctrl L", style = MaterialTheme.typography.labelSmall)
            }

            // Right Controller (Inside)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VisualizerIcon(
                    onRes = R.drawable.controller_right_on,
                    offRes = R.drawable.controller_right_off,
                    intensity = hState.controllerRightTopIntensity,
                    size = 32.dp
                )
                VisualizerIcon(
                    onRes = R.drawable.controller_right_on,
                    offRes = R.drawable.controller_right_off,
                    intensity = hState.controllerRightBottomIntensity,
                    size = 32.dp
                )
                Text("Ctrl R", style = MaterialTheme.typography.labelSmall)
            }

            // Right Phone (Outside)
            VisualizerIcon(
                onRes = R.drawable.phone_right_on,
                offRes = R.drawable.phone_right_off,
                intensity = hState.phoneRightIntensity,
                label = "Phone R"
            )
        }
    }
}

@Composable
fun VisualizerIcon(
    onRes: Int,
    offRes: Int,
    intensity: Float,
    label: String? = null,
    size: androidx.compose.ui.unit.Dp = 48.dp
) {
    val alpha = 0.2f + (intensity * 0.8f)
    val color = if (intensity > 0.01f) MaterialTheme.colorScheme.primary else Color.Gray
    
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Image(
            painter = painterResource(id = if (intensity > 0.01f) onRes else offRes),
            contentDescription = null,
            modifier = Modifier.size(size),
            alpha = alpha,
            colorFilter = ColorFilter.tint(color)
        )
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BeatPlayerCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerState by BeatDetector.playerState.collectAsState()
    var selectedProfile by remember { mutableStateOf(BeatProfile.AMPLITUDE) }
    val folderUris = SettingsManager.authorizedFolderUris
    var expandedFolderUri by remember { mutableStateOf<Uri?>(null) }
    var filesInExpandedFolder by remember { mutableStateOf<List<Pair<String, Uri>>>(emptyList()) }
    var showAnalysisDialog by remember { mutableStateOf(false) }

    if (showAnalysisDialog) {
        BackgroundAnalysisDialog(
            onDismiss = { showAnalysisDialog = false },
            onStart = { folders, profiles ->
                val intent = Intent(context, AnalysisService::class.java).apply {
                    putStringArrayListExtra(AnalysisService.EXTRA_FOLDER_URIS, ArrayList(folders.map { it.toString() }))
                    putStringArrayListExtra(AnalysisService.EXTRA_PROFILES, ArrayList(profiles.map { it.name }))
                }
                ContextCompat.startForegroundService(context, intent)
                showAnalysisDialog = false
            }
        )
    }

    LaunchedEffect(expandedFolderUri, selectedProfile) {
        val uri = expandedFolderUri
        if (uri != null) {
            val files = mutableListOf<Pair<String, Uri>>()
            try {
                val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                    uri,
                    DocumentsContract.getTreeDocumentId(uri)
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
                            files.add(name to DocumentsContract.buildDocumentUriUsingTree(uri, id))
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.error("Error loading files from folder: ${e.message}")
            }
            filesInExpandedFolder = files.sortedBy { it.first }
        } else {
            filesInExpandedFolder = emptyList()
        }
    }
    
    LaunchedEffect(playerState.selectedFileUri, selectedProfile) {
        val uri = playerState.selectedFileUri
        if (uri != null) {
            val rootUri = folderUris.firstOrNull { uri.toString().startsWith(it) }?.let { Uri.parse(it) }
            if (rootUri != null) {
                val existingProfileUri = BeatDetector.findExistingProfile(context, rootUri, playerState.selectedFileName, selectedProfile)
                BeatDetector.loadProfile(context, existingProfileUri)
            }
        }
    }

    SectionCard(
        title = "Bilateral Beat Player",
        actions = {
            Button(onClick = { showAnalysisDialog = true }, enabled = !playerState.isAnalyzing) {
                Text("Batch Analyze")
            }
        }
    ) {
        Column {
            if (folderUris.isEmpty()) {
                Text("No media folders authorized. Add one below.", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            folderUris.forEach { uriString ->
                val folderUri = Uri.parse(uriString)
                val isExpanded = expandedFolderUri == folderUri
                
                FolderItem(
                    folderUri = folderUri,
                    isExpanded = isExpanded,
                    onClick = {
                        expandedFolderUri = if (isExpanded) null else folderUri
                    }
                )

                AnimatedVisibility(visible = isExpanded) {
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        filesInExpandedFolder.forEach { (name, uri) ->
                            val isAnalyzed = remember(name, selectedProfile) {
                                BeatDetector.findExistingProfile(context, folderUri, name, selectedProfile) != null
                            }
                            FileItem(
                                name = name,
                                isAnalyzed = isAnalyzed,
                                isSelected = playerState.selectedFileUri == uri,
                                onClick = { BeatDetector.updateSelectedTrack(uri, name) }
                            )
                        }
                    }
                }
            }

            Text("Selected: ${playerState.selectedFileName}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            Spacer(modifier = Modifier.height(16.dp))

            Box {
                var expandedProfiles by remember { mutableStateOf(false) }
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

            Spacer(modifier = Modifier.height(8.dp))
            
            val masterIntensity = playerState.masterIntensity
            Text("Master Max Haptics: ${(masterIntensity * 100).toInt()}%")
            Slider(value = masterIntensity, onValueChange = { BeatDetector.updateMasterIntensity(it) })

            Spacer(modifier = Modifier.height(8.dp))
            
            val mediaVolume = playerState.mediaVolume
            Text("Media Volume: ${(mediaVolume * 100).toInt()}%")
            Slider(value = mediaVolume, onValueChange = { BeatDetector.updateMediaVolume(it) })

            Spacer(modifier = Modifier.height(16.dp))

            if (playerState.isAnalyzing) {
                Column {
                    Text("Analyzing beats: ${(playerState.analysisProgress * 100).toInt()}%")
                    LinearProgressIndicator(progress = { playerState.analysisProgress }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            Logger.info("Cancelling analysis...")
                            val intent = Intent(context, AnalysisService::class.java).apply {
                                action = AnalysisService.ACTION_CANCEL
                            }
                            context.startService(intent)
                            BeatDetector.cancelAnalysis()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Cancel Analysis")
                    }
                }
            } else if (playerState.detectedBeats.isEmpty()) {
                Button(
                    onClick = {
                        val uri = playerState.selectedFileUri
                        if (uri != null) {
                            val rootUri = folderUris.firstOrNull { uri.toString().startsWith(it) }?.let { Uri.parse(it) }
                            if (rootUri != null) {
                                scope.launch {
                                    val result = BeatDetector.analyzeAudioUri(context, uri, selectedProfile)
                                    if (result.isNotEmpty()) {
                                        BeatDetector.saveProfile(context, rootUri, playerState.selectedFileName, selectedProfile, result)
                                    }
                                }
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = playerState.selectedFileUri != null && !playerState.isPlaying
                ) {
                    Text("Analyze Audio")
                }
            }

            if (playerState.detectedBeats.isNotEmpty() || playerState.isPlaying) {
                if (playerState.isPlaying) {
                    val progress = if (playerState.totalDurationMs > 0) playerState.currentTimestampMs.toFloat() / playerState.totalDurationMs.toFloat() else 0f
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Playing: ${(playerState.currentTimestampMs/1000)}s / ${(playerState.totalDurationMs/1000)}s", style = MaterialTheme.typography.bodySmall)
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { BeatDetector.stopPlayback() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Stop, contentDescription = null)
                            Text("Stop Playback")
                        }
                    }
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${playerState.detectedBeats.size} beats ready", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        Card(
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp).combinedClickable(
                                onClick = { 
                                    val intent = Intent(context, HapticService::class.java).apply { action = HapticService.ACTION_START }
                                    ContextCompat.startForegroundService(context, intent)
                                    BeatDetector.playSynchronized(context) 
                                },
                                onLongClick = {
                                    val uri = playerState.selectedFileUri
                                    val rootUri = folderUris.firstOrNull { uri.toString().startsWith(it) }?.let { Uri.parse(it) }
                                    if (uri != null && rootUri != null) {
                                        scope.launch {
                                            val result = BeatDetector.analyzeAudioUri(context, uri, selectedProfile)
                                            if (result.isNotEmpty()) {
                                                BeatDetector.saveProfile(context, rootUri, playerState.selectedFileName, selectedProfile, result)
                                            }
                                        }
                                    }
                                }
                            ),
                            shape = ButtonDefaults.shape,
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary)
                        ) {
                            Row(modifier = Modifier.padding(ButtonDefaults.ContentPadding).fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null)
                                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                                Text("Play (Hold to Refresh)")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BackgroundAnalysisDialog(
    onDismiss: () -> Unit,
    onStart: (Set<Uri>, Set<BeatProfile>) -> Unit
) {
    val context = LocalContext.current
    val folderUris = SettingsManager.authorizedFolderUris.map { Uri.parse(it) }
    var selectedFolders by remember { mutableStateOf(setOf<Uri>()) }
    var selectedProfiles by remember { mutableStateOf(setOf<BeatProfile>()) }
    
    // Status maps: Folder/Profile -> IsFullyAnalyzed
    var folderStatus by remember { mutableStateOf(mapOf<Uri, Boolean>()) }
    var profileStatus by remember { mutableStateOf(mapOf<BeatProfile, Boolean>()) }
    var isScanning by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val fStatus = mutableMapOf<Uri, Boolean>()
            folderUris.forEach { uri ->
                val files = getAudioFiles(context, uri)
                if (files.isEmpty()) {
                    fStatus[uri] = true // Empty is "done"
                } else {
                    // Folder is analyzed if ALL its files have at least one profile
                    val allAnalyzed = files.all { (name, _) ->
                        BeatProfile.entries.any { profile ->
                            BeatDetector.findExistingProfile(context, uri, name, profile) != null
                        }
                    }
                    fStatus[uri] = allAnalyzed
                }
            }
            
            val pStatus = mutableMapOf<BeatProfile, Boolean>()
            // Profiles status based on all authorized files
            BeatProfile.entries.forEach { profile ->
                pStatus[profile] = folderUris.all { folderUri ->
                    getAudioFiles(context, folderUri).all { (name, _) ->
                        BeatDetector.findExistingProfile(context, folderUri, name, profile) != null
                    }
                }
            }

            withContext(Dispatchers.Main) {
                folderStatus = fStatus
                profileStatus = pStatus
                // Default selection: everything not fully analyzed
                selectedFolders = fStatus.filter { !it.value }.keys
                selectedProfiles = pStatus.filter { !it.value }.keys
                isScanning = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Background Analysis") },
        text = {
            if (isScanning) {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Column {
                    Text(
                        "⚠️ This process will take a significant amount of time depending on library size. It will run in the background via notification.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    // Legend
                    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.Center) {
                        Text("Legend: ", style = MaterialTheme.typography.labelSmall)
                        Text("Analyzed", color = Color(0xFF4CAF50), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        Text(" | ", style = MaterialTheme.typography.labelSmall)
                        Text("Missing", color = Color(0xFFE57373), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }

                    Text("Select folders to analyze:", style = MaterialTheme.typography.titleMedium)
                    folderUris.forEach { folderUri ->
                        val isAnalyzed = folderStatus[folderUri] ?: false
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFolders = if (folderUri in selectedFolders) selectedFolders - folderUri else selectedFolders + folderUri
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(checked = folderUri in selectedFolders, onCheckedChange = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = folderUri.path?.substringAfterLast(':') ?: "Folder",
                                color = if (isAnalyzed) Color(0xFF4CAF50) else Color(0xFFE57373),
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text("Select profiles to generate:", style = MaterialTheme.typography.titleMedium)
                    BeatProfile.entries.forEach { profile ->
                        val isAnalyzed = profileStatus[profile] ?: false
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedProfiles = if (profile in selectedProfiles) selectedProfiles - profile else selectedProfiles + profile
                                }
                                .padding(vertical = 2.dp)
                        ) {
                            Checkbox(checked = profile in selectedProfiles, onCheckedChange = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = profile.name,
                                color = if (isAnalyzed) Color(0xFF4CAF50) else Color(0xFFE57373),
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onStart(selectedFolders, selectedProfiles) },
                enabled = selectedFolders.isNotEmpty() && selectedProfiles.isNotEmpty()
            ) {
                Text("Start")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

// Helper to avoid duplicate code
private fun getAudioFiles(context: Context, folderUri: Uri): List<Pair<String, Uri>> {
    val files = mutableListOf<Pair<String, Uri>>()
    try {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            folderUri,
            DocumentsContract.getTreeDocumentId(folderUri)
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
                    files.add(name to DocumentsContract.buildDocumentUriUsingTree(folderUri, id))
                }
            }
        }
    } catch (e: Exception) { }
    return files
}

@Composable
fun FolderItem(folderUri: Uri, isExpanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = folderUri.path?.substringAfterLast(':') ?: "Folder",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight,
            contentDescription = "Expand or collapse folder"
        )
    }
}

@Composable
fun FileItem(name: String, isAnalyzed: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = if (isSelected) MaterialTheme.colorScheme.primary else if (isAnalyzed) Color(0xFF4CAF50) else Color(0xFFE57373),
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary else if (isAnalyzed) Color(0xFF4CAF50) else Color(0xFFE57373)
        )
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
fun HapticControlCard() {
    val state by HapticManager.state.collectAsState()
    val context = LocalContext.current
    var showAdvanced by remember { mutableStateOf(false) }

    SectionCard(title = "Haptic Settings") {
        Column {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { showAdvanced = !showAdvanced }.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Icon(
                    imageVector = if (showAdvanced) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Advanced Timing Settings", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(modifier = Modifier.padding(bottom = 16.dp)) {
                    Text(
                        "Fine-tune visualizer synchronization. Lead-in creates a pre-vibration 'glow', and Lead-out holds the highlight after the pulse ends.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("Lead-in: ${state.leadInMs}ms")
                    Slider(
                        value = state.leadInMs.toFloat(),
                        onValueChange = { HapticManager.updateLeadIn(it.toInt()) },
                        valueRange = 0f..100f,
                        steps = 10
                    )

                    Text("Lead-out: ${state.leadOutMs}ms")
                    Slider(
                        value = state.leadOutMs.toFloat(),
                        onValueChange = { HapticManager.updateLeadOut(it.toInt()) },
                        valueRange = 0f..100f,
                        steps = 10
                    )
                }
            }

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
                    HapticManager.updateIntensity(snappedValue)
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
                    HapticManager.updateBpm(newBpm)
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
                    HapticManager.updateSessionDuration(newDuration)
                    SettingsManager.sessionDurationSeconds = newDuration
                },
                valueRange = 50f..600f,
                steps = 10,
            )

            Spacer(modifier = Modifier.height(16.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Button(onClick = { HapticManager.testPulseSequence() }, enabled = !state.isHeartbeatRunning && !state.isTestRunning) {
                    Text("Test Sequence")
                }
                
                if (state.isHeartbeatRunning) {
                    Button(onClick = { HapticManager.stopHeartbeatSession() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Stop Session")
                    }
                } else {
                    Button(onClick = { 
                        val intent = Intent(context, HapticService::class.java).apply { action = HapticService.ACTION_START }
                        ContextCompat.startForegroundService(context, intent)
                        HapticManager.startHeartbeatSession() 
                    }, enabled = !state.isTestRunning) {
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
    val modes = listOf(PTSDMode.ActiveHeartbeat, PTSDMode.BBPlayer, PTSDMode.SleepAssistance, PTSDMode.GroundingMode)

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
    val context = LocalContext.current
    var logToLogcat by remember { mutableStateOf(Logger.logToLogcat) }

    val isDeveloperMode = remember {
        Settings.Secure.getInt(context.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1
    }

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
                        colors = ButtonDefaults.buttonColors(
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDeveloperMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Log to Logcat", style = MaterialTheme.typography.bodySmall)
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = logToLogcat,
                            onCheckedChange = {
                                logToLogcat = it
                                Logger.logToLogcat = it
                            }
                        )
                    }
                }
                Button(
                    onClick = {
                        Logger.clear()
                        Logger.info("Logs cleared.")
                    },
                ) {
                    Text("Clear Logs")
                }
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
                horizontalArrangement = Arrangement.SpaceBetween) {
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
