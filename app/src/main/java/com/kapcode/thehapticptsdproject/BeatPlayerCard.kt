package com.kapcode.thehapticptsdproject

import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kapcode.thehapticptsdproject.composables.FileItem
import com.kapcode.thehapticptsdproject.composables.FolderItem
import com.kapcode.thehapticptsdproject.composables.InPlayerHapticVisualizer
import com.kapcode.thehapticptsdproject.composables.InPlayerAudioVisualizer
import com.kapcode.thehapticptsdproject.composables.SectionCard
import com.kapcode.thehapticptsdproject.composables.SliderWithTick

fun BeatProfile.getColor(): Color = when(this) {
    BeatProfile.AMPLITUDE -> Color.Cyan
    BeatProfile.BASS -> Color(0xFFD2B48C) // Tan
    BeatProfile.DRUM -> Color(0xFFFFA500) // Orange
    BeatProfile.GUITAR -> Color(0xFF4CAF50) // Grass Green
}

fun BeatProfile.getIcon(): ImageVector = when(this) {
    BeatProfile.AMPLITUDE -> Icons.Default.GraphicEq
    BeatProfile.BASS -> Icons.Default.Speaker
    BeatProfile.DRUM -> Icons.Default.Album
    BeatProfile.GUITAR -> Icons.Default.MusicNote
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeatPlayerCard(vm: BeatPlayerViewModel = viewModel()) {
    val context = LocalContext.current
    val playerState by vm.playerState.collectAsState()
    val folderUris = vm.folderUris
    val selectedProfile by vm.selectedProfile
    val expandedFolderUri by vm.expandedFolderUri
    val filesInExpandedFolder by vm.filesInExpandedFolder
    val showAnalysisDialog by vm.showAnalysisDialog
    val triggerNext by vm.triggerNext

    // Layout Management
    var componentOrder by remember { mutableStateOf(listOf("Haptic", "Audio", "Progress", "Controls1", "Controls2", "Controls3")) }
    
    // Animation for reorder button cue
    val infiniteTransition = rememberInfiniteTransition(label = "flash")
    val reorderScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(animation = tween(1000), repeatMode = RepeatMode.Reverse),
        label = "scale"
    )

    LaunchedEffect(folderUris) {
        if (folderUris.isNotEmpty()) { vm.autoSelectTrack(context) }
    }

    LaunchedEffect(expandedFolderUri) {
        vm.loadFilesInFolder(context, expandedFolderUri)
    }

    LaunchedEffect(playerState.selectedFileUri, selectedProfile) {
        if (!SettingsManager.isLiveHapticsEnabled) {
            vm.loadProfileForSelectedTrack(context)
        }
    }

    LaunchedEffect(triggerNext) {
        if (triggerNext) {
            vm.nextTrack(context)
            vm.triggerNext.value = false
        }
    }

    if (showAnalysisDialog) {
        BackgroundAnalysisDialog(
            onDismiss = { vm.onDismissAnalysisDialog() },
            onStart = { files, profiles ->
                vm.startBatchAnalysis(context, files, profiles)
            }
        )
    }

    SectionCard(
        title = "Bilateral Beat Player",
        actions = {
            IconButton(
                onClick = { componentOrder = componentOrder.drop(1) + componentOrder.take(1) },
                modifier = Modifier.scale(reorderScale)
            ) {
                Icon(Icons.Default.DragHandle, contentDescription = "Cycle Layout", tint = MaterialTheme.colorScheme.primary)
            }
        }
    ) {
        Column {
            for (uriString in folderUris) {
                val folderUri = Uri.parse(uriString)
                val isExpanded = expandedFolderUri == folderUri
                FolderItem(folderUri, isExpanded) { vm.onFolderExpanded(folderUri) }
                AnimatedVisibility(visible = isExpanded) {
                    Column(modifier = Modifier.padding(start = 16.dp)) {
                        filesInExpandedFolder.forEach { (name, uri) ->
                            val isAnalyzed = remember(name, selectedProfile) {
                                BeatDetector.findExistingProfile(context, folderUri, name, selectedProfile) != null
                            }
                            FileItem(name, isAnalyzed, playerState.selectedFileUri == uri) {
                                vm.onFileSelected(uri, name)
                            }
                        }
                    }
                }
            }

            Text("Selected: ${playerState.selectedFileName}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    var expanded by remember { mutableStateOf(false) }
                    val profileColor = selectedProfile.getColor()

                    OutlinedButton(
                        onClick = { expanded = true }, 
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = profileColor)
                    ) {
                        Icon(selectedProfile.getIcon(), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Profile: ${selectedProfile.name}")
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        BeatProfile.entries.forEach { profile ->
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(profile.getIcon(), contentDescription = null, tint = profile.getColor(), modifier = Modifier.size(18.dp))
                                        Spacer(Modifier.width(8.dp))
                                        Text(profile.name, color = profile.getColor())
                                    }
                                },
                                onClick = {
                                    vm.onProfileSelected(profile)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(
                    onClick = { vm.onBatchAnalyzeClicked() },
                    enabled = !playerState.isAnalyzing
                ) {
                    Text("Batch")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Analyzed", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                Switch(
                    checked = SettingsManager.isLiveHapticsEnabled,
                    onCheckedChange = {
                        SettingsManager.isLiveHapticsEnabled = it
                        SettingsManager.save()
                    }
                )
                Spacer(Modifier.width(8.dp))
                Text("Live", style = MaterialTheme.typography.labelMedium)
            }

            if (playerState.isAnalyzing) {
                Spacer(modifier = Modifier.height(16.dp))
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Task ${playerState.analysisTaskProgress}", style = MaterialTheme.typography.titleSmall)
                        if (playerState.analysisFileDuration.isNotEmpty()) {
                            Text("Cur: ${playerState.analysisFileDuration}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    LinearProgressIndicator(progress = { playerState.analysisProgress }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { BeatDetector.cancelAnalysis() }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) ) {
                        Text("Cancel Analysis")
                    }
                }
            }

            // Show Analyze button ONLY if in Analyzed mode and no beats are loaded for the selected track.
            if (playerState.selectedFileUri != null && playerState.detectedBeats.isEmpty() && !SettingsManager.isLiveHapticsEnabled && !playerState.isAnalyzing && !playerState.isPlaying && !playerState.isPaused) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { vm.analyzeSelectedAudio(context) },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Analyze Audio") }
            }

            // Show player controls once a file is selected.
            if (playerState.selectedFileUri != null) {
                Column {
                    componentOrder.forEach { type ->
                         Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            IconButton(onClick = { 
                                Toast.makeText(context, "Tap the pulsing Drag Handle in the top corner to cycle layout order.", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Default.DragHandle, null, tint = Color.Gray.copy(alpha = 0.5f))
                            }
                            
                            Box(modifier = Modifier.weight(1f)) {
                                when(type) {
                                    "Haptic" -> InPlayerHapticVisualizer(selectedProfile = selectedProfile)
                                    "Audio" -> InPlayerAudioVisualizer(selectedProfile = selectedProfile)
                                    "Progress" -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        val currentSecs = playerState.currentTimestampMs / 1000
                                        val totalSecs = playerState.totalDurationMs / 1000
                                        Text("${currentSecs / 60}:${(currentSecs % 60).toString().padStart(2, '0')} / ${totalSecs / 60}:${(totalSecs % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.bodySmall)

                                        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(64.dp)) {
                                            val containerWidth = maxWidth
                                            val density = LocalDensity.current
                                            val totalMs = playerState.totalDurationMs.coerceAtLeast(1L)
                                            val windowMs = SettingsManager.seekbarTimeWindowMinutes * 60 * 1000L
                                            val scaleFactor = if (totalMs > windowMs) totalMs.toFloat() / windowMs.toFloat() else 1.0f
                                            val contentWidth = containerWidth * scaleFactor
                                            val scrollState = rememberScrollState()
                                            
                                            LaunchedEffect(playerState.currentTimestampMs, scaleFactor) {
                                                if (playerState.isPlaying && scaleFactor > 1.0f) {
                                                    val contentWidthPx = with(density) { contentWidth.toPx() }
                                                    val containerWidthPx = with(density) { containerWidth.toPx() }
                                                    val progress = playerState.currentTimestampMs.toFloat() / totalMs.toFloat()
                                                    val cursorX = progress * contentWidthPx
                                                    val targetScroll = (cursorX - (containerWidthPx * 0.2f)).toInt()
                                                    val maxScroll = (contentWidthPx - containerWidthPx).toInt()
                                                    scrollState.scrollTo(targetScroll.coerceIn(0, maxScroll))
                                                }
                                            }

                                            Box(modifier = Modifier.fillMaxSize().horizontalScroll(scrollState, enabled = scaleFactor > 1.0f)) {
                                                Box(modifier = Modifier.width(contentWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                                    val assignedProfiles = remember(SettingsManager.deviceAssignments) { SettingsManager.deviceAssignments.values.flatten().toSet() }
                                                    Canvas(modifier = Modifier.fillMaxWidth().height(32.dp).padding(horizontal = 10.dp)) {
                                                        val canvasWidth = size.width
                                                        val totalDuration = playerState.totalDurationMs.toFloat().coerceAtLeast(1f)
                                                        if (!SettingsManager.isLiveHapticsEnabled) {
                                                            playerState.detectedBeats.forEach { beat ->
                                                                if (beat.profile in assignedProfiles) {
                                                                    val x = (beat.timestampMs.toFloat() / totalDuration) * canvasWidth
                                                                    val shadowWidth = 2.dp.toPx()
                                                                    drawLine(color = beat.profile.getColor().copy(alpha = 0.2f), start = androidx.compose.ui.geometry.Offset(x - shadowWidth, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = shadowWidth)
                                                                    drawLine(color = beat.profile.getColor().copy(alpha = 0.6f), start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1.dp.toPx())
                                                                }
                                                            }
                                                        }
                                                    }
                                                    Slider(
                                                        value = playerState.currentTimestampMs.toFloat(),
                                                        onValueChange = { BeatDetector.seekTo(it.toLong()) },
                                                        valueRange = 0f..playerState.totalDurationMs.toFloat().coerceAtLeast(1f),
                                                        modifier = Modifier.fillMaxWidth(),
                                                        thumb = {
                                                            Box(modifier = Modifier.width(SettingsManager.seekThumbWidth.dp).height(SettingsManager.seekThumbHeight.dp).alpha(SettingsManager.seekThumbAlpha).background(MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp)))
                                                        },
                                                        colors = SliderDefaults.colors(activeTrackColor = Color.Transparent, inactiveTrackColor = Color.Transparent)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    "Controls1" -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { vm.skipBackward(30) }) { Icon(Icons.Default.Replay30, "Skip -30s") }
                                        IconButton(onClick = { vm.skipBackward(5) }) { Icon(Icons.Default.Replay5, "Skip -5s") }
                                        if (playerState.isPlaying) { IconButton(onClick = { vm.pause() }) { Icon(Icons.Default.PauseCircle, "Pause", Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary) } }
                                        else { IconButton(onClick = { vm.play(context) }) { Icon(Icons.Default.PlayCircle, "Play", Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary) } }
                                        IconButton(onClick = { vm.skipForward(5) }) { Icon(Icons.Default.Forward5, "Skip +5s") }
                                        IconButton(onClick = { vm.skipForward(30) }) { Icon(Icons.Default.Forward30, "Skip +30s") }
                                    }
                                    "Controls2" -> Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = { vm.previousTrack(context) }) { Icon(Icons.Default.SkipPrevious, "Prev") }
                                        IconButton(onClick = {
                                            val toastText = if (!SettingsManager.isRepeatEnabled && !SettingsManager.isRepeatAllEnabled) {
                                                SettingsManager.isRepeatAllEnabled = true
                                                "Repeat All Enabled"
                                            } else if (SettingsManager.isRepeatAllEnabled) {
                                                SettingsManager.isRepeatAllEnabled = false
                                                SettingsManager.isRepeatEnabled = true
                                                "Repeat One Enabled"
                                            } else {
                                                SettingsManager.isRepeatEnabled = false
                                                SettingsManager.isRepeatAllEnabled = false
                                                "Repeat Disabled"
                                            }
                                            Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
                                            SettingsManager.save()
                                        }) { 
                                            val icon = if (SettingsManager.isRepeatEnabled) Icons.Default.RepeatOne else Icons.Default.Repeat
                                            val tint = if (SettingsManager.isRepeatEnabled || SettingsManager.isRepeatAllEnabled) MaterialTheme.colorScheme.primary else Color.Gray
                                            Icon(icon, "Repeat", tint = tint)
                                        }
                                        
                                        var speedExpanded by remember { mutableStateOf(false) }
                                        Box {
                                            TextButton(onClick = { speedExpanded = true }) {
                                                Text("${SettingsManager.playbackSpeed}x", fontSize = 12.sp)
                                            }
                                            DropdownMenu(expanded = speedExpanded, onDismissRequest = { speedExpanded = false }) {
                                                listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                                                    DropdownMenuItem(text = { Text("${speed}x") }, onClick = { 
                                                        SettingsManager.playbackSpeed = speed
                                                        SettingsManager.save()
                                                        BeatDetector.syncPlaybackSettings()
                                                        speedExpanded = false 
                                                    })
                                                }
                                            }
                                        }

                                        IconButton(onClick = { 
                                            SettingsManager.volumeBoost = if (SettingsManager.volumeBoost > 1.0f) 1.0f else 1.5f
                                            SettingsManager.save()
                                            BeatDetector.syncPlaybackSettings()
                                        }) { 
                                            Icon(Icons.Default.VolumeUp, "Boost", tint = if (SettingsManager.volumeBoost > 1.0f) MaterialTheme.colorScheme.primary else Color.Gray) 
                                        }
                                        IconButton(onClick = { vm.nextTrack(context) }) { Icon(Icons.Default.SkipNext, "Next") }
                                    }
                                    "Controls3" -> Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.VolumeUp, "Volume", modifier = Modifier.padding(end = 8.dp), tint = Color.Gray)
                                            Slider(
                                                value = SettingsManager.mediaVolume,
                                                onValueChange = { 
                                                    SettingsManager.mediaVolume = it
                                                    BeatDetector.syncPlaybackSettings()
                                                    // No need to save here, as it's a live adjustment
                                                },
                                                onValueChangeFinished = { SettingsManager.save() },
                                                valueRange = 0f..1f
                                            )
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Vibration, "Intensity", modifier = Modifier.padding(end = 8.dp), tint = Color.Gray)
                                            Slider(
                                                value = SettingsManager.beatMaxIntensity,
                                                onValueChange = { SettingsManager.beatMaxIntensity = it },
                                                onValueChangeFinished = { SettingsManager.save() },
                                                valueRange = 0f..1f
                                            )
                                        }
                                    }
                                }
                            }
                         }
                         Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}