package com.kapcode.thehapticptsdproject

import android.content.Context
import android.content.Intent
import android.net.Uri
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
    val folderUris = SettingsManager.authorizedFolderUrisState
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
        val rootUri = folderUris.value.firstOrNull { uri.toString().startsWith(it) }?.let { Uri.parse(it) }
        if (uri != null && rootUri != null) {
            viewModelScope.launch {
                val result = BeatDetector.analyzeAudioUri(context, uri, selectedProfile.value)
                if (result.isNotEmpty()) {
                    BeatDetector.saveProfile(context, rootUri, playerState.value.selectedFileName, selectedProfile.value, result)
                }
            }
        }
    }

    fun loadProfileForSelectedTrack(context: Context) {
        val uri = playerState.value.selectedFileUri
        if (uri != null && (uri != lastLoadedUri.value || selectedProfile.value != lastLoadedProfile.value)) {
            val rootUri = folderUris.value.firstOrNull { uri.toString().startsWith(it) }?.let { Uri.parse(it) }
            if (rootUri != null) {
                val existingProfileUri = BeatDetector.findExistingProfile(context, rootUri, playerState.value.selectedFileName, selectedProfile.value)
                BeatDetector.loadProfile(context, existingProfileUri)
                lastLoadedUri.value = uri
                lastLoadedProfile.value = selectedProfile.value
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
                    val root = folderUris.value.find { lastUriStr.startsWith(it) }
                    if (root != null) {
                        onFileSelected(lastUri, lastName)
                        return@withContext
                    }
                }

                // 2. Look for analyzed files, prefer shortest, then alphabetical
                var bestFile: AnalysisFile? = null
                var bestFolderUri: Uri? = null

                for (folderUriStr in folderUris.value) {
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
        // Ensure the service is running so the notification is visible
        val intent = Intent(context, HapticService::class.java).apply {
            action = HapticService.ACTION_START
        }
        (context as? MainActivity)?.runWithNotificationPermission {
            ContextCompat.startForegroundService(context, intent)
        }
        BeatDetector.playSynchronized(context)
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
