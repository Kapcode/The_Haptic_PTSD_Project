package com.kapcode.thehapticptsdproject

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BeatPlayerViewModel : ViewModel() {
    val playerState = BeatDetector.playerState
    val selectedProfile = mutableStateOf(BeatProfile.AMPLITUDE)
    val folderUris: Set<String>
        get() = SettingsManager.authorizedFolderUris
    val expandedFolderUri = mutableStateOf<Uri?>(null)
    val filesInExpandedFolder = mutableStateOf<List<Pair<String, Uri>>>(emptyList())
    val showAnalysisDialog = mutableStateOf(false)

    val lastLoadedUri = mutableStateOf<Uri?>(null)
    val lastLoadedProfile = mutableStateOf<BeatProfile?>(null)

    fun onFolderExpanded(uri: Uri) {
        expandedFolderUri.value = if (expandedFolderUri.value == uri) null else uri
    }

    fun loadFilesInFolder(context: Context, uri: Uri?) {
        if (uri == null) {
            filesInExpandedFolder.value = emptyList()
            return
        }
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val files = getAudioFiles(context, uri)
                filesInExpandedFolder.value = files.sortedBy { it.first }
            }
        }
    }

    fun onFileSelected(uri: Uri, name: String) {
        BeatDetector.updateSelectedTrack(uri, name)
    }

    fun onProfileSelected(profile: BeatProfile) {
        selectedProfile.value = profile
    }

    fun onBatchAnalyzeClicked() {
        showAnalysisDialog.value = true
    }

    fun onDismissAnalysisDialog() {
        showAnalysisDialog.value = false
    }

    fun startBatchAnalysis(context: Context, files: List<Triple<Uri, String, Uri>>, profiles: Set<BeatProfile>) {
        val intent = Intent(context, AnalysisService::class.java).apply {
            putStringArrayListExtra(AnalysisService.EXTRA_FILE_URIS, ArrayList(files.map { it.first.toString() }))
            putStringArrayListExtra(AnalysisService.EXTRA_FILE_NAMES, ArrayList(files.map { it.second }))
            putStringArrayListExtra(AnalysisService.EXTRA_PARENT_URIS, ArrayList(files.map { it.third.toString() }))
            putStringArrayListExtra(AnalysisService.EXTRA_PROFILES, ArrayList(profiles.map { it.name }))
        }
        (context as? MainActivity)?.runWithNotificationPermission {
            ContextCompat.startForegroundService(context, intent)
        }
        showAnalysisDialog.value = false
    }

    fun analyzeSelectedAudio(context: Context) {
        val uri = playerState.value.selectedFileUri
        val name = playerState.value.selectedFileName
        val rootUri = folderUris.firstOrNull { uri.toString().startsWith(it) }?.let { Uri.parse(it) }
        
        if (uri != null && rootUri != null) {
            val intent = Intent(context, AnalysisService::class.java).apply {
                putStringArrayListExtra(AnalysisService.EXTRA_FILE_URIS, arrayListOf(uri.toString()))
                putStringArrayListExtra(AnalysisService.EXTRA_FILE_NAMES, arrayListOf(name))
                putStringArrayListExtra(AnalysisService.EXTRA_PARENT_URIS, arrayListOf(rootUri.toString()))
                putStringArrayListExtra(AnalysisService.EXTRA_PROFILES, arrayListOf(selectedProfile.value.name))
            }
            (context as? MainActivity)?.runWithNotificationPermission {
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }

    fun loadProfileForSelectedTrack(context: Context) {
        val uri = playerState.value.selectedFileUri
        val profile = selectedProfile.value
        
        if (uri != null && (uri != lastLoadedUri.value || profile != lastLoadedProfile.value)) {
            val rootUri = folderUris.firstOrNull { uri.toString().startsWith(it) }?.let { Uri.parse(it) }
            if (rootUri != null) {
                // Load merged profiles for all 4 profiles if they exist
                BeatDetector.loadMergedProfiles(context, uri, playerState.value.selectedFileName, rootUri)
                
                // Check if the current selected profile is missing
                val existingProfileUri = BeatDetector.findExistingProfile(context, rootUri, playerState.value.selectedFileName, profile)
                if (existingProfileUri == null) {
                    Toast.makeText(context, "No haptic profile for ${profile.name}. Queuing analysis...", Toast.LENGTH_SHORT).show()
                    analyzeSelectedAudio(context)
                }
                
                lastLoadedUri.value = uri
                lastLoadedProfile.value = profile
            }
        }
    }

    fun autoSelectTrack(context: Context) {
        if (playerState.value.selectedFileUri != null) return

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // 1. Try last played
                val lastUriStr = SettingsManager.lastPlayedAudioUri
                val lastName = SettingsManager.lastPlayedAudioName
                if (lastUriStr != null && lastName != null) {
                    val lastUri = Uri.parse(lastUriStr)
                    // Check if it belongs to an authorized folder
                    val root = folderUris.find { lastUriStr.startsWith(it) }
                    if (root != null) {
                        onFileSelected(lastUri, lastName)
                        return@withContext
                    }
                }

                // 2. Look for analyzed files, prefer shortest, then alphabetical
                var bestFile: AnalysisFile? = null
                var bestFolderUri: Uri? = null

                for (folderUriStr in folderUris) {
                    val folderUri = Uri.parse(folderUriStr)
                    val files = getAudioFilesWithDetails(context, folderUri)
                    
                    val analyzedFiles = files.filter { file ->
                        BeatDetector.findExistingProfile(context, folderUri, file.name, selectedProfile.value) != null
                    }

                    if (analyzedFiles.isNotEmpty()) {
                        // Sort by duration (shortest first), then name
                        val sorted = analyzedFiles.sortedWith(compareBy({ it.durationMs }, { it.name }))
                        val top = sorted.first()
                        
                        if (bestFile == null || top.durationMs < bestFile!!.durationMs) {
                            bestFile = top
                            bestFolderUri = folderUri
                        } else if (top.durationMs == bestFile!!.durationMs && top.name < bestFile!!.name) {
                            bestFile = top
                            bestFolderUri = folderUri
                        }
                    }
                }

                bestFile?.let {
                    onFileSelected(it.uri, it.name)
                }
            }
        }
    }

    fun play(context: Context) {
        if (playerState.value.detectedBeats.isEmpty()) {
            Toast.makeText(context, "Waiting for audio analysis to complete...", Toast.LENGTH_SHORT).show()
            return
        }

        // Ensure the service is running
        val intent = Intent(context, HapticService::class.java).apply {
            action = HapticService.ACTION_START
        }
        (context as? MainActivity)?.runWithNotificationPermission {
            ContextCompat.startForegroundService(context, intent)
        }

        if (playerState.value.isPaused) {
            BeatDetector.resumePlayback()
        } else {
            BeatDetector.playSynchronized(context)
        }
    }

    fun pause() {
        BeatDetector.pausePlayback()
    }

    fun stop() {
        BeatDetector.stopPlayback()
    }

    fun skipForward(seconds: Int) {
        BeatDetector.skipForward(seconds)
    }

    fun skipBackward(seconds: Int) {
        BeatDetector.skipBackward(seconds)
    }
}
