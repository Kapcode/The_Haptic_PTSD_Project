package com.kapcode.thehapticptsdproject

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.kapcode.thehapticptsdproject.ui.theme.TheHapticPTSDProjectTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {

    private lateinit var squeezeDetector: SqueezeDetector

    private var isSqueezeEnabled by mutableStateOf(false)
    private var isShakeEnabled by mutableStateOf(false)
    private var internalShakeThreshold by mutableStateOf(15f)

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                Logger.info("Permissions granted.")
                handleDetectionStateChange(isModeActiveInUI())
            } else {
                Logger.error("Permissions granted was false.")
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
                    IconButton(onClick = { /* Drawer toggle */ }) {
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
                    } else {
                        currentModes.add(mode)
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
            VisualizerIcon(R.drawable.phone_left_on, R.drawable.phone_left_off, hState.phoneLeftIntensity, "Phone L")
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VisualizerIcon(R.drawable.controller_left_on, R.drawable.controller_left_off, hState.controllerLeftTopIntensity, size = 32.dp)
                VisualizerIcon(R.drawable.controller_left_on, R.drawable.controller_left_off, hState.controllerLeftBottomIntensity, size = 32.dp)
                Text("Ctrl L", style = MaterialTheme.typography.labelSmall)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                VisualizerIcon(R.drawable.controller_right_on, R.drawable.controller_right_off, hState.controllerRightTopIntensity, size = 32.dp)
                VisualizerIcon(R.drawable.controller_right_on, R.drawable.controller_right_off, hState.controllerRightBottomIntensity, size = 32.dp)
                Text("Ctrl R", style = MaterialTheme.typography.labelSmall)
            }
            VisualizerIcon(R.drawable.phone_right_on, R.drawable.phone_right_off, hState.phoneRightIntensity, "Phone R")
        }
    }
}

@Composable
fun VisualizerIcon(onRes: Int, offRes: Int, intensity: Float, label: String? = null, size: androidx.compose.ui.unit.Dp = 48.dp) {
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
        if (label != null) Text(label, style = MaterialTheme.typography.labelSmall)
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
            onStart = { files, profiles ->
                val intent = Intent(context, AnalysisService::class.java).apply {
                    putStringArrayListExtra(AnalysisService.EXTRA_FILE_URIS, ArrayList(files.map { it.first.toString() }))
                    putStringArrayListExtra(AnalysisService.EXTRA_FILE_NAMES, ArrayList(files.map { it.second }))
                    putStringArrayListExtra(AnalysisService.EXTRA_PARENT_URIS, ArrayList(files.map { it.third.toString() }))
                    putStringArrayListExtra(AnalysisService.EXTRA_PROFILES, ArrayList(profiles.map { it.name }))
                }
                ContextCompat.startForegroundService(context, intent)
                showAnalysisDialog = false
            }
        )
    }

    LaunchedEffect(expandedFolderUri) {
        val uri = expandedFolderUri
        if (uri != null) {
            withContext(Dispatchers.IO) {
                val files = getAudioFiles(context, uri)
                filesInExpandedFolder = files.sortedBy { it.first }
            }
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
            folderUris.forEach { uriString ->
                val folderUri = Uri.parse(uriString)
                val isExpanded = expandedFolderUri == folderUri
                FolderItem(folderUri, isExpanded) { expandedFolderUri = if (isExpanded) null else folderUri }
                AnimatedVisibility(visible = isExpanded) {
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        filesInExpandedFolder.forEach { (name, uri) ->
                            val isAnalyzed = remember(name, selectedProfile) {
                                BeatDetector.findExistingProfile(context, folderUri, name, selectedProfile) != null
                            }
                            FileItem(name, isAnalyzed, playerState.selectedFileUri == uri) { 
                                BeatDetector.updateSelectedTrack(uri, name) 
                            }
                        }
                    }
                }
            }

            Text("Selected: ${playerState.selectedFileName}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            Spacer(modifier = Modifier.height(16.dp))

            Box {
                var expanded by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("Profile: ${selectedProfile.name}")
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    BeatProfile.values().forEach { profile ->
                        DropdownMenuItem(text = { Text(profile.name) }, onClick = {
                            selectedProfile = profile
                            expanded = false
                        })
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            val masterIntensity = playerState.masterIntensity
            Text("Max Haptic Intensity: ${(masterIntensity * 100).toInt()}%")
            Slider(value = masterIntensity, onValueChange = { BeatDetector.updateMasterIntensity(it) })

            Spacer(modifier = Modifier.height(8.dp))
            Text("Media Volume: ${(playerState.mediaVolume * 100).toInt()}%")
            Slider(value = playerState.mediaVolume, onValueChange = { BeatDetector.updateMediaVolume(it) })

            Spacer(modifier = Modifier.height(16.dp))

            if (playerState.isAnalyzing) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Task ${playerState.analysisTaskProgress}", style = MaterialTheme.typography.titleSmall)
                        if (playerState.analysisFileDuration.isNotEmpty()) {
                            Text("Cur: ${playerState.analysisFileDuration}", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                    if (playerState.analysisTotalRemainingMs > 0) {
                        val totalSecs = playerState.analysisTotalRemainingMs / 1000
                        val mins = totalSecs / 60
                        val secs = totalSecs % 60
                        Text("Total Audio Left: ${mins}m ${secs}s", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("Current File: ${(playerState.analysisProgress * 100).toInt()}%", style = MaterialTheme.typography.bodySmall)
                    LinearProgressIndicator(progress = { playerState.analysisProgress }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { BeatDetector.cancelAnalysis() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) {
                        Text("Cancel Analysis")
                    }
                }
            } else if (playerState.detectedBeats.isEmpty()) {
                Button(
                    onClick = {
                        val uri = playerState.selectedFileUri
                        val rootUri = folderUris.firstOrNull { uri.toString().startsWith(it) }?.let { Uri.parse(it) }
                        if (uri != null && rootUri != null) {
                            scope.launch {
                                val result = BeatDetector.analyzeAudioUri(context, uri, selectedProfile)
                                if (result.isNotEmpty()) BeatDetector.saveProfile(context, rootUri, playerState.selectedFileName, selectedProfile, result)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = playerState.selectedFileUri != null && !playerState.isPlaying
                ) { Text("Analyze Audio") }
            }

            if (playerState.detectedBeats.isNotEmpty() || playerState.isPlaying) {
                if (playerState.isPlaying) {
                    val progress = if (playerState.totalDurationMs > 0) playerState.currentTimestampMs.toFloat() / playerState.totalDurationMs.toFloat() else 0f
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Playing: ${(playerState.currentTimestampMs/1000)}s / ${(playerState.totalDurationMs/1000)}s")
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                        Button(onClick = { BeatDetector.stopPlayback() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error), modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Stop, contentDescription = null); Text("Stop Playback")
                        }
                    }
                } else {
                    Button(
                        onClick = { 
                            val intent = Intent(context, HapticService::class.java).apply { action = HapticService.ACTION_START }
                            ContextCompat.startForegroundService(context, intent)
                            BeatDetector.playSynchronized(context) 
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Default.PlayArrow, contentDescription = null); Text("Play Synchronized") }
                }
            }
        }
    }
}

data class AnalysisFile(val uri: Uri, val name: String, val parentUri: Uri, val durationMs: Long = 0)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackgroundAnalysisDialog(onDismiss: () -> Unit, onStart: (List<Triple<Uri, String, Uri>>, Set<BeatProfile>) -> Unit) {
    val context = LocalContext.current
    val folderUris = SettingsManager.authorizedFolderUris.map { Uri.parse(it) }
    var allFiles by remember { mutableStateOf<List<AnalysisFile>>(emptyList()) }
    var selectedFileUris by remember { mutableStateOf(setOf<Uri>()) }
    var selectedProfiles by remember { mutableStateOf(BeatProfile.values().toSet()) }
    var isScanning by remember { mutableStateOf(true) }
    var analyzedMap by remember { mutableStateOf<Map<Pair<Uri, BeatProfile>, Boolean>>(emptyMap()) }

    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val files = mutableListOf<AnalysisFile>()
            folderUris.forEach { files.addAll(getAudioFilesWithDetails(context, it)) }
            
            val statusMap = mutableMapOf<Pair<Uri, BeatProfile>, Boolean>()
            files.forEach { file ->
                BeatProfile.values().forEach { profile ->
                    statusMap[file.uri to profile] = BeatDetector.findExistingProfile(context, file.parentUri, file.name, profile) != null
                }
            }
            
            withContext(Dispatchers.Main) {
                allFiles = files
                analyzedMap = statusMap
                selectedFileUris = files.filter { file ->
                    !BeatProfile.values().all { profile -> statusMap[file.uri to profile] == true }
                }.map { it.uri }.toSet()
                isScanning = false
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = { Text("Background Batch Analysis") },
        text = {
            if (isScanning) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Column(modifier = Modifier.fillMaxHeight(0.85f)) {
                    Text("Select tracks and profiles. Red tracks are long (> 5m) and take significantly more processing time.", style = MaterialTheme.typography.bodySmall)
                    
                    Text("Profiles:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BeatProfile.values().forEach { profile ->
                            FilterChip(
                                selected = profile in selectedProfiles,
                                onClick = { selectedProfiles = if (profile in selectedProfiles) selectedProfiles - profile else selectedProfiles + profile },
                                label = { Text(profile.name) }
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Tracks (${selectedFileUris.size}/${allFiles.size}):", style = MaterialTheme.typography.titleSmall)
                        Row {
                            TextButton(onClick = { selectedFileUris = allFiles.map { it.uri }.toSet() }) { Text("All", fontSize = 10.sp) }
                            TextButton(onClick = { selectedFileUris = emptySet() }) { Text("None", fontSize = 10.sp) }
                            TextButton(onClick = { selectedFileUris = allFiles.filter { it.durationMs < 300000 }.map { it.uri }.toSet() }) { Text("Skip Long", fontSize = 10.sp) }
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(allFiles) { file ->
                            val isDone = selectedProfiles.isNotEmpty() && selectedProfiles.all { analyzedMap[file.uri to it] == true }
                            val isLong = file.durationMs > 300000
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable {
                                selectedFileUris = if (file.uri in selectedFileUris) selectedFileUris - file.uri else selectedFileUris + file.uri
                            }.padding(vertical = 4.dp)) {
                                Checkbox(checked = file.uri in selectedFileUris, onCheckedChange = null)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = file.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = if (isDone) Color(0xFF4CAF50) else if (isLong) Color.Red else Color.Unspecified
                                    )
                                    if (file.durationMs > 0) {
                                        Text("${file.durationMs/60000}m ${ (file.durationMs%60000)/1000 }s", style = MaterialTheme.typography.labelSmall, color = if (isDone) Color(0xFF4CAF50) else if (isLong) Color.Red else Color.Gray)
                                    } else {
                                        Text("Duration unknown", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                                    }
                                }
                                if (isDone) Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val selected = allFiles.filter { it.uri in selectedFileUris }.map { Triple(it.uri, it.name, it.parentUri) }
                onStart(selected, selectedProfiles)
            }, enabled = selectedFileUris.isNotEmpty() && selectedProfiles.isNotEmpty()) {
                Text("Start (${selectedFileUris.size * selectedProfiles.size} Tasks)")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun getAudioFilesWithDetails(context: Context, folderUri: Uri): List<AnalysisFile> {
    val files = mutableListOf<AnalysisFile>()
    val retriever = MediaMetadataRetriever()
    try {
        val rootDocId = DocumentsContract.getTreeDocumentId(folderUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, rootDocId)
        
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            "duration"
        )

        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(0)
                val id = cursor.getString(1)
                val mime = cursor.getString(2)
                if (mime?.startsWith("audio/") == true) {
                    var duration = 0L
                    
                    val durIdx = cursor.getColumnIndex("duration")
                    if (durIdx != -1) duration = cursor.getLong(durIdx)
                    
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(folderUri, id)

                    if (duration == 0L) {
                        try {
                            context.contentResolver.openFileDescriptor(fileUri, "r")?.use { pfd ->
                                retriever.setDataSource(pfd.fileDescriptor)
                                val durStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                                duration = durStr?.toLong() ?: 0L
                            }
                        } catch (e: Exception) { }
                    }

                    files.add(AnalysisFile(fileUri, name, folderUri, duration))
                }
            }
        }
    } catch (e: Exception) {
        Logger.error("Error fetching audio details: ${e.message}")
    } finally {
        try { retriever.release() } catch (e: Exception) {}
    }
    return files
}

private fun getAudioFiles(context: Context, folderUri: Uri): List<Pair<String, Uri>> {
    val files = mutableListOf<Pair<String, Uri>>()
    try {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(folderUri, DocumentsContract.getTreeDocumentId(folderUri))
        context.contentResolver.query(childrenUri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(2)?.startsWith("audio/") == true) {
                    files.add(cursor.getString(0) to DocumentsContract.buildDocumentUriUsingTree(folderUri, cursor.getString(1)))
                }
            }
        }
    } catch (e: Exception) {}
    return files
}

@Composable
fun LoggerCard() {
    val logHistory by Logger.logHistory.collectAsState()
    var selectedLogLevel by remember { mutableStateOf(LogLevel.DEBUG) }
    var logToLogcat by remember { mutableStateOf(Logger.logToLogcat) }
    val context = LocalContext.current
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
                LogLevel.values().forEach { level ->
                    TextButton(
                        onClick = { selectedLogLevel = level },
                        colors = ButtonDefaults.buttonColors(
                            contentColor = if (selectedLogLevel == level) MaterialTheme.colorScheme.primary else Color.Gray,
                            containerColor = Color.Transparent
                        )
                    ) {
                        Text(level.name, fontSize = 10.sp)
                    }
                }
            }
        }
    ) {
        Column {
            LazyColumn(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                items(filteredLogs.reversed()) { log ->
                    val color = when (log.level) {
                        LogLevel.ERROR -> Color.Red
                        LogLevel.DEBUG -> Color.Gray
                        else -> Color.Unspecified
                    }
                    Text(log.toString(), style = MaterialTheme.typography.bodySmall, color = color)
                }
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                if (isDeveloperMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Logcat", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = logToLogcat, onCheckedChange = { logToLogcat = it; Logger.logToLogcat = it }, modifier = Modifier.scale(0.7f))
                    }
                }
                Button(onClick = { Logger.clear() }) { Text("Clear Logs") }
            }
        }
    }
}

@Composable
fun FolderItem(folderUri: Uri, isExpanded: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (isExpanded) Icons.Default.FolderOpen else Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
        Text(folderUri.path?.substringAfterLast(':') ?: "Folder", modifier = Modifier.weight(1f).padding(start = 16.dp))
        Icon(if (isExpanded) Icons.Default.ArrowDropDown else Icons.Default.ArrowRight, null)
    }
}

@Composable
fun FileItem(name: String, isAnalyzed: Boolean, isSelected: Boolean, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp, horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.MusicNote, null, tint = if (isSelected) MaterialTheme.colorScheme.primary else if (isAnalyzed) Color(0xFF4CAF50) else Color.Red, modifier = Modifier.size(20.dp))
        Text(name, modifier = Modifier.padding(start = 16.dp), color = if (isSelected) MaterialTheme.colorScheme.primary else if (isAnalyzed) Color(0xFF4CAF50) else Color.Red)
    }
}

@Composable
fun SectionCard(title: String, actions: @Composable RowScope.() -> Unit = {}, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                Row { actions() }
            }
            content()
        }
    }
}

@Composable
fun ModesSection(activeModes: Set<PTSDMode>, onModeToggled: (PTSDMode) -> Unit) {
    val modes = listOf(
        PTSDMode.ActiveHeartbeat,
        PTSDMode.BBPlayer,
        PTSDMode.SleepAssistance,
        PTSDMode.GroundingMode
    )
    SectionCard(title = "Modes") {
        Column {
            modes.forEach { mode ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onModeToggled(mode) },
                    colors = CardDefaults.cardColors(containerColor = if (mode in activeModes) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(mode.icon, null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                        Column(modifier = Modifier.padding(start = 12.dp)) {
                            Text(mode.name, style = MaterialTheme.typography.titleMedium)
                            Text(mode.description, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MediaFoldersCard() {
    val context = LocalContext.current
    var uris by remember { mutableStateOf(SettingsManager.authorizedFolderUris) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val new = uris.toMutableSet().apply { add(it.toString()) }
            uris = new; SettingsManager.authorizedFolderUris = new
        }
    }
    SectionCard(title = "Media Folders", actions = { IconButton(onClick = { launcher.launch(null) }) { Icon(Icons.Default.Add, null) } }) {
        uris.forEach { u ->
            Row(modifier = Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary)
                Text(Uri.parse(u).path?.substringAfterLast(':') ?: "Folder", modifier = Modifier.weight(1f).padding(start = 8.dp))
                IconButton(onClick = { val new = uris.toMutableSet().apply { remove(u) }; uris = new; SettingsManager.authorizedFolderUris = new }) { Icon(Icons.Default.Delete, null, tint = Color.Gray) }
            }
        }
    }
}

@Composable
fun SqueezeDetectorCard(detector: SqueezeDetector, isEnabled: Boolean, onToggle: (Boolean) -> Unit) {
    val state by detector.state.collectAsState()
    SectionCard(title = "Squeeze Calibration", actions = { Switch(isEnabled, onToggle) }) {
        Text("Magnitude: ${state.liveMagnitude.toInt()} / Baseline: ${state.baselineMagnitude.toInt()}")
        Slider(value = state.squeezeThresholdPercent.toFloat(), onValueChange = { detector.setSqueezeThreshold(it.toDouble()); SettingsManager.squeezeThreshold = it }, valueRange = 0.05f..0.95f, enabled = isEnabled)
        Button(onClick = { detector.recalibrate() }, enabled = isEnabled) { Text("Recalibrate") }
    }
}

@Composable
fun ShakeDetectorCard(isEnabled: Boolean, onToggle: (Boolean) -> Unit, sensitivity: Float, onSensitivityChange: (Float) -> Unit) {
    SectionCard(title = "Wrist Snap", actions = { Switch(isEnabled, onToggle) }) {
        Text("Sensitivity: ${sensitivity.toInt()}")
        Slider(value = sensitivity, onValueChange = onSensitivityChange, valueRange = 5f..50f, enabled = isEnabled)
    }
}

@Composable
fun HapticControlCard() {
    val state by HapticManager.state.collectAsState()
    SectionCard(title = "Haptic Settings") {
        Text("Intensity: ${(state.intensity*100).toInt()}%")
        Slider(value = state.intensity, onValueChange = { HapticManager.updateIntensity(it); SettingsManager.intensity = it })
        Text("BPM: ${state.bpm}")
        Slider(value = state.bpm.toFloat(), onValueChange = { HapticManager.updateBpm(it.toInt()); SettingsManager.bpm = it.toInt() }, valueRange = 40f..180f)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { HapticManager.testPulseSequence() }) { Text("Test") }
            if (state.isHeartbeatRunning) Button(onClick = { HapticManager.stopHeartbeatSession() }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) { Text("Stop Session") }
            else Button(onClick = { HapticManager.startHeartbeatSession() }) { Text("Start Session") }
        }
    }
}
