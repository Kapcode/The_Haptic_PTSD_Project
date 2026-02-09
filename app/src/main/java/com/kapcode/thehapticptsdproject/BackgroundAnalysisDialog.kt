package com.kapcode.thehapticptsdproject

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BackgroundAnalysisDialog(onDismiss: () -> Unit, onStart: (List<Triple<Uri, String, Uri>>, Set<BeatProfile>) -> Unit) {
    val context = LocalContext.current
    val folderUris = SettingsManager.authorizedFolderUris
    var allFiles by remember { mutableStateOf<List<AnalysisFile>>(emptyList()) }
    var selectedFileUris by remember { mutableStateOf(setOf<Uri>()) }
    var selectedProfiles by remember { mutableStateOf(BeatProfile.entries.toSet()) }
    var isScanning by remember { mutableStateOf(true) }
    var analyzedMap by remember { mutableStateOf<Map<Pair<Uri, BeatProfile>, Boolean>>(emptyMap()) }
    
    val activity = LocalContext.current as? MainActivity

    LaunchedEffect(folderUris) {
        withContext(Dispatchers.IO) {
            val files = mutableListOf<AnalysisFile>()
            folderUris.forEach { uriString ->
                val folderUri = Uri.parse(uriString)
                files.addAll(getAudioFilesWithDetails(context, folderUri))
            }
            
            val statusMap = mutableMapOf<Pair<Uri, BeatProfile>, Boolean>()
            files.forEach { file ->
                BeatProfile.entries.forEach { profile ->
                    statusMap[file.uri to profile] = BeatDetector.findExistingProfile(context, file.parentUri, file.name, profile) != null
                }
            }
            
            withContext(Dispatchers.Main) {
                allFiles = files
                analyzedMap = statusMap
                selectedFileUris = files.filter { file ->
                    !BeatProfile.entries.all { profile -> statusMap[file.uri to profile] == true }
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
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                Column(modifier = Modifier.fillMaxHeight(0.85f)) {
                    Text("Select tracks and profiles. Red tracks are long (> 5m) and take significantly more processing time.", style = MaterialTheme.typography.bodySmall)
                    
                    Text("Profiles:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        BeatProfile.entries.forEach { profile ->
                            FilterChip(
                                selected = profile in selectedProfiles,
                                onClick = { selectedProfiles = if (profile in selectedProfiles) selectedProfiles - profile else selectedProfiles + profile },
                                label = { Text(profile.name) }
                            )
                        }
                    }

                    Text("Selection:", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        TextButton(onClick = { selectedFileUris = allFiles.map { it.uri }.toSet() }) { Text("All", fontSize = 12.sp) }
                        TextButton(onClick = { selectedFileUris = emptySet() }) { Text("None", fontSize = 12.sp) }
                        TextButton(onClick = { selectedFileUris = allFiles.filter { it.durationMs < 300000 }.map { it.uri }.toSet() }) { Text("Skip Long", fontSize = 12.sp) }
                    }

                    Text("Tracks (${selectedFileUris.size}/${allFiles.size}):", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(allFiles) { file ->
                            val isDone = selectedProfiles.isNotEmpty() && selectedProfiles.all { analyzedMap[file.uri to it] == true }
                            val isLong = file.durationMs > 300000
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedFileUris =
                                        if (file.uri in selectedFileUris) selectedFileUris - file.uri else selectedFileUris + file.uri
                                }
                                .padding(vertical = 4.dp)) {
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
                                if (isDone) Icon(
                                    Icons.Default.CheckCircle,
                                    null,
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val selected = allFiles.filter { it.uri in selectedFileUris }.map { Triple(it.uri, it.name, it.parentUri) }
                activity?.runWithNotificationPermission {
                    onStart(selected, selectedProfiles)
                }
            }, enabled = selectedFileUris.isNotEmpty() && selectedProfiles.isNotEmpty() && activity != null) {
                Text("Start (${selectedFileUris.size * selectedProfiles.size} Tasks)")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
