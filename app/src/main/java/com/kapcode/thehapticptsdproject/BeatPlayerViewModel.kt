@file:Suppress("unused")

package com.kapcode.thehapticptsdproject

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel for the BeatPlayerCard composable.
 *
 * This class manages the UI state and business logic for the Bilateral Beat Player feature.
 * It interacts with the `BeatDetector` singleton to control playback, load audio files,
 * manage haptic profiles, and trigger audio analysis. It also handles user interactions
 * from the UI, such as track selection, playback controls, and profile changes.
 */
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

    val triggerNext = mutableStateOf(false)
    private var pendingPlay = false

    init {
        BeatDetector.onTrackFinished = {
            if (SettingsManager.isRepeatAllEnabled) {
                triggerNext.value = true
            }
        }
    }

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
        // If a track was playing or paused, set pendingPlay to continue playback after loading the new track.
        pendingPlay = playerState.value.isPlaying || playerState.value.isPaused
        lastLoadedUri.value = null
        BeatDetector.updateSelectedTrack(uri, name)
    }

    fun onProfileSelected(profile: BeatProfile) {
        selectedProfile.value = profile
        BeatDetector.liveHapticProfile = profile
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
        val rootUri = folderUris.firstOrNull { uri.toString().startsWith(it) }?.toUri()
        
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
            val rootUri = folderUris.firstOrNull { uri.toString().startsWith(it) }?.toUri()
            if (rootUri != null) {
                // This replaces the old beats, it does not add to them.
                BeatDetector.loadMergedProfiles(context, uri, playerState.value.selectedFileName, rootUri)
                
                if (pendingPlay) {
                    pendingPlay = false
                    if (playerState.value.detectedBeats.isNotEmpty()) {
                        play(context)
                    }
                }

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
                val lastUriStr = SettingsManager.lastPlayedAudioUri
                val lastName = SettingsManager.lastPlayedAudioName
                if (lastUriStr != null && lastName != null) {
                    val lastUri = lastUriStr.toUri()
                    val root = folderUris.find { lastUriStr.startsWith(it) }
                    if (root != null) {
                        onFileSelected(lastUri, lastName)
                        return@withContext
                    }
                }

                var bestFile: AnalysisFile? = null
                for (folderUriStr in folderUris) {
                    val folderUri = folderUriStr.toUri()
                    val files = getAudioFilesWithDetails(context, folderUri)
                    val analyzedFiles = files.filter { file ->
                        BeatDetector.findExistingProfile(context, folderUri, file.name, selectedProfile.value) != null
                    }

                    if (analyzedFiles.isNotEmpty()) {
                        val sorted = analyzedFiles.sortedWith(compareBy({ it.durationMs }, { it.name }))
                        val top = sorted.first()
                        if (bestFile == null || top.durationMs < bestFile!!.durationMs) {
                            bestFile = top
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
        // Allow playback in live mode without analysis
        if (!SettingsManager.isLiveHapticsEnabled && playerState.value.detectedBeats.isEmpty()) {
            Toast.makeText(context, "Waiting for audio analysis to complete...", Toast.LENGTH_SHORT).show()
            return
        }

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

    fun pause() = BeatDetector.pausePlayback()
    fun stop() = BeatDetector.stopPlayback()
    fun skipForward(seconds: Int) = BeatDetector.skipForward(seconds)
    fun skipBackward(seconds: Int) = BeatDetector.skipBackward(seconds)

    fun nextTrack(context: Context) {
        viewModelScope.launch {
            var files = filesInExpandedFolder.value
            if (files.isEmpty()) {
                val currentUri = playerState.value.selectedFileUri ?: return@launch
                val rootUriStr = folderUris.firstOrNull { currentUri.toString().startsWith(it) }
                if (rootUriStr != null) {
                    val rootUri = rootUriStr.toUri()
                    files = withContext(Dispatchers.IO) { getAudioFiles(context, rootUri) }.sortedBy { it.first }
                    filesInExpandedFolder.value = files
                }
            }
            
            if (files.isEmpty()) return@launch
            
            val currentUri = playerState.value.selectedFileUri
            val currentIndex = files.indexOfFirst { it.second == currentUri }
            val nextIndex = (currentIndex + 1) % files.size
            val nextFile = files[nextIndex]
            
            // Set pendingPlay for repeat-all, as isPlaying will be false after the track completes.
            if(SettingsManager.isRepeatAllEnabled) pendingPlay = true
            onFileSelected(nextFile.second, nextFile.first)
        }
    }

    fun previousTrack(context: Context) {
        viewModelScope.launch {
            var files = filesInExpandedFolder.value
            if (files.isEmpty()) {
                val currentUri = playerState.value.selectedFileUri ?: return@launch
                val rootUriStr = folderUris.firstOrNull { currentUri.toString().startsWith(it) }
                if (rootUriStr != null) {
                    val rootUri = rootUriStr.toUri()
                    files = withContext(Dispatchers.IO) { getAudioFiles(context, rootUri) }.sortedBy { it.first }
                    filesInExpandedFolder.value = files
                }
            }
            
            if (files.isEmpty()) return@launch
            
            val currentUri = playerState.value.selectedFileUri
            val currentIndex = files.indexOfFirst { it.second == currentUri }
            val prevIndex = if (currentIndex <= 0) files.size - 1 else currentIndex - 1
            val prevFile = files[prevIndex]

            onFileSelected(prevFile.second, prevFile.first)
        }
    }
}
