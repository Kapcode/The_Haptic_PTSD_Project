package com.kapcode.thehapticptsdproject

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kapcode.thehapticptsdproject.composables.FileItem
import com.kapcode.thehapticptsdproject.composables.FolderItem
import com.kapcode.thehapticptsdproject.composables.InPlayerHapticVisualizer
import com.kapcode.thehapticptsdproject.composables.SectionCard

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

@Composable
fun BeatPlayerCard(vm: BeatPlayerViewModel = viewModel()) {
    val context = LocalContext.current
    val playerState by vm.playerState.collectAsState()
    val folderUris = vm.folderUris
    val selectedProfile by vm.selectedProfile
    val expandedFolderUri by vm.expandedFolderUri
    val filesInExpandedFolder by vm.filesInExpandedFolder
    val showAnalysisDialog by vm.showAnalysisDialog

    LaunchedEffect(folderUris) {
        if (folderUris.isNotEmpty()) {
            vm.autoSelectTrack(context)
        }
    }

    LaunchedEffect(expandedFolderUri) {
        vm.loadFilesInFolder(context, expandedFolderUri)
    }

    LaunchedEffect(playerState.selectedFileUri, selectedProfile) {
        vm.loadProfileForSelectedTrack(context)
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
        title = "Bilateral Beat Player"
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
            Text("Max Haptic Intensity: ${(SettingsManager.beatMaxIntensity * 100).toInt()}%")
            Slider(
                value = SettingsManager.beatMaxIntensity,
                onValueChange = { 
                    SettingsManager.beatMaxIntensity = applySnap(it, SettingsManager.snapBeatMaxIntensity)
                    SettingsManager.save()
                    BeatDetector.updateMasterIntensity(SettingsManager.beatMaxIntensity)
                }
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text("Media Volume: ${(SettingsManager.mediaVolume * 100).toInt()}%")
            Slider(
                value = SettingsManager.mediaVolume,
                onValueChange = { 
                    SettingsManager.mediaVolume = applySnap(it, SettingsManager.snapVolume)
                    SettingsManager.save()
                    BeatDetector.updateMediaVolume(SettingsManager.mediaVolume)
                }
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (playerState.isAnalyzing) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Task ${playerState.analysisTaskProgress}", style = MaterialTheme.typography.titleSmall)
                        if (playerState.analysisFileDuration.isNotEmpty()) {
                            Text("Cur: ${playerState.analysisFileDuration}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    onClick = { vm.analyzeSelectedAudio(context) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = playerState.selectedFileUri != null && !playerState.isPlaying && !playerState.isPaused
                ) { Text("Analyze Audio") }
            }

            if (playerState.detectedBeats.isNotEmpty() || playerState.isPlaying || playerState.isPaused) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val currentSecs = playerState.currentTimestampMs / 1000
                    val totalSecs = playerState.totalDurationMs / 1000
                    Text("${currentSecs / 60}:${(currentSecs % 60).toString().padStart(2, '0')} / ${totalSecs / 60}:${(totalSecs % 60).toString().padStart(2, '0')}", style = MaterialTheme.typography.bodySmall)

                    Slider(
                        value = playerState.currentTimestampMs.toFloat(),
                        onValueChange = { BeatDetector.seekTo(it.toLong()) },
                        valueRange = 0f..playerState.totalDurationMs.toFloat().coerceAtLeast(1f),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { vm.skipBackward(30) }, enabled = playerState.isPlaying || playerState.isPaused) {
                            Icon(Icons.Default.FastRewind, contentDescription = "Rewind 30s")
                        }
                        IconButton(onClick = { vm.skipBackward(5) }, enabled = playerState.isPlaying || playerState.isPaused) {
                            Icon(Icons.Default.FastRewind, contentDescription = "Rewind 5s")
                        }
                        if (playerState.isPlaying) {
                            IconButton(onClick = { vm.pause() }) {
                                Icon(Icons.Default.Pause, contentDescription = "Pause")
                            }
                        } else {
                            IconButton(onClick = { vm.play(context) }, enabled = playerState.detectedBeats.isNotEmpty()) {
                                Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                            }
                        }
                        IconButton(onClick = { vm.stop() }, enabled = playerState.isPlaying || playerState.isPaused) {
                            Icon(Icons.Default.Stop, contentDescription = "Stop")
                        }
                        IconButton(onClick = { vm.skipForward(5) }, enabled = playerState.isPlaying || playerState.isPaused) {
                            Icon(Icons.Default.FastForward, contentDescription = "Forward 5s")
                        }
                        IconButton(onClick = { vm.skipForward(30) }, enabled = playerState.isPlaying || playerState.isPaused) {
                            Icon(Icons.Default.FastForward, contentDescription = "Forward 30s")
                        }
                    }

                    InPlayerHapticVisualizer()
                }
            }
        }
    }
}
