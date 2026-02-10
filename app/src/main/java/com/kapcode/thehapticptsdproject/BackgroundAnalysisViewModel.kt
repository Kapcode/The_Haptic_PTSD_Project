package com.kapcode.thehapticptsdproject

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BackgroundAnalysisViewModel : ViewModel() {
    var allFiles by mutableStateOf<List<AnalysisFile>>(emptyList())
        private set
    
    var selectedFileUris by mutableStateOf(setOf<Uri>())
    
    var selectedProfiles by mutableStateOf(BeatProfile.entries.toSet())
    
    var isScanning by mutableStateOf(true)
        private set
        
    var analyzedMap by mutableStateOf<Map<Pair<Uri, BeatProfile>, Boolean>>(emptyMap())
        private set

    fun loadFiles(context: Context, folderUris: Set<String>) {
        isScanning = true
        viewModelScope.launch {
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
                    // Auto-select files that are NOT fully analyzed for all profiles
                    selectedFileUris = files.filter { file ->
                        !BeatProfile.entries.all { profile -> statusMap[file.uri to profile] == true }
                    }.map { it.uri }.toSet()
                    isScanning = false
                }
            }
        }
    }

    fun toggleFileSelection(uri: Uri) {
        selectedFileUris = if (uri in selectedFileUris) {
            selectedFileUris - uri
        } else {
            selectedFileUris + uri
        }
    }

    fun toggleProfileSelection(profile: BeatProfile) {
        selectedProfiles = if (profile in selectedProfiles) {
            selectedProfiles - profile
        } else {
            selectedProfiles + profile
        }
    }

    fun selectAll() {
        selectedFileUris = allFiles.map { it.uri }.toSet()
    }

    fun selectNone() {
        selectedFileUris = emptySet()
    }

    fun selectSkipLong() {
        selectedFileUris = allFiles.filter { it.durationMs < 300000 }.map { it.uri }.toSet()
    }
}
