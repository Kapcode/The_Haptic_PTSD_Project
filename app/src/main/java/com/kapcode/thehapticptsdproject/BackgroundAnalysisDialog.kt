package com.kapcode.thehapticptsdproject

import android.net.Uri
import androidx.activity.compose.LocalActivity
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
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BackgroundAnalysisDialog(
    vm: BackgroundAnalysisViewModel = viewModel(),
    onDismiss: () -> Unit,
    onStart: (List<Triple<Uri, String, Uri>>, Set<BeatProfile>) -> Unit
) {
    val context = LocalContext.current
    val activity = LocalActivity.current as? MainActivity
    val folderUris = SettingsManager.authorizedFolderUris

    LaunchedEffect(folderUris) {
        vm.loadFiles(context, folderUris)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.95f),
        title = { Text("Background Batch Analysis") },
        text = {
            if (vm.isScanning) {
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
                                selected = profile in vm.selectedProfiles,
                                onClick = { vm.toggleProfileSelection(profile) },
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
                        TextButton(onClick = { vm.selectAll() }) { Text("All", fontSize = 12.sp) }
                        TextButton(onClick = { vm.selectNone() }) { Text("None", fontSize = 12.sp) }
                        TextButton(onClick = { vm.selectSkipLong() }) { Text("Skip Long", fontSize = 12.sp) }
                    }

                    Text("Tracks (${vm.selectedFileUris.size}/${vm.allFiles.size}):", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(vm.allFiles) { file ->
                            val isDone = vm.selectedProfiles.isNotEmpty() && vm.selectedProfiles.all { vm.analyzedMap[file.uri to it] == true }
                            val isLong = file.durationMs > 300000
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier
                                .fillMaxWidth()
                                .clickable { vm.toggleFileSelection(file.uri) }
                                .padding(vertical = 4.dp)) {
                                Checkbox(checked = file.uri in vm.selectedFileUris, onCheckedChange = null)
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
                val selected = vm.allFiles.filter { it.uri in vm.selectedFileUris }.map { Triple(it.uri, it.name, it.parentUri) }
                activity?.runWithNotificationPermission {
                    onStart(selected, vm.selectedProfiles)
                }
            }, enabled = vm.selectedFileUris.isNotEmpty() && vm.selectedProfiles.isNotEmpty() && activity != null) {
                Text("Start (${vm.selectedFileUris.size * vm.selectedProfiles.size} Tasks)")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
