package com.kapcode.thehapticptsdproject

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jtransforms.fft.DoubleFFT_1D
import java.nio.charset.Charset
import java.util.Scanner
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

/**
 * Core singleton for managing audio playback, haptic profile analysis, and synchronization.
 *
 * This object is responsible for:
 * - Decoding audio files to analyze for rhythmic transients (beats).
 * - Generating and saving `.haptic.json` profiles based on different algorithms (e.g., AMPLITUDE, BASS).
 * - Managing the `MediaPlayer` instance for audio playback.
 * - Exposing a `StateFlow` of the `BeatPlayerState` for UI observation.
 * - Triggering haptic events in sync with audio playback by coordinating with `HapticManager`.
 */
enum class BeatProfile {
    AMPLITUDE, DRUM, BASS, GUITAR
}

@Serializable
data class DetectedBeat(
    val timestampMs: Long,
    val intensity: Float,
    val durationMs: Int,
    val channel: Int, // 0: Left, 1: Right, 2: Both (Legacy)
    val profile: BeatProfile = BeatProfile.AMPLITUDE
)

@Serializable
data class HapticProfile(
    val audioFileName: String,
    val profileType: String,
    val beats: List<DetectedBeat>
)

data class BeatPlayerState(
    val isPlaying: Boolean = false,
    val isPaused: Boolean = false,
    val isAnalyzing: Boolean = false,
    val analysisProgress: Float = 0f,
    val analysisFileIndex: Int = 0,
    val analysisTotalFiles: Int = 0,
    val analysisTaskProgress: String = "",
    val analysisFileDuration: String = "",
    val analysisTotalRemainingMs: Long = 0,
    val currentTimestampMs: Long = 0,
    val totalDurationMs: Long = 0,
    val nextBeatIndex: Int = 0,
    val selectedFileUri: Uri? = null,
    val selectedFileName: String = "No file selected",
    val detectedBeats: List<DetectedBeat> = emptyList(),
    val masterIntensity: Float = 1.0f,
    val mediaVolume: Float = 1.0f
)

internal data class BeatCandidate(
    val timestampMs: Long,
    val energy: Double,
    val intensity: Float,
    val durationMs: Int
)

object BeatDetector {

    private val _playerState = MutableStateFlow(BeatPlayerState())
    val playerState = _playerState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private var playbackJob: Job? = null
    private var analysisJob: Job? = null
    private val analysisLock = AtomicBoolean(false)
    
    var onTrackFinished: (() -> Unit)? = null
    
    private const val ENERGY_HISTORY_SIZE = 43
    private val lastTriggerTimes = mutableMapOf<BeatProfile, Long>()
    private var lastLiveHapticTime: Long = 0
    var liveHapticProfile: BeatProfile = BeatProfile.AMPLITUDE

    fun init() {
        _playerState.update { it.copy(
            masterIntensity = SettingsManager.beatMaxIntensity,
            mediaVolume = SettingsManager.mediaVolume
        ) }
    }

    fun updateMasterIntensity(intensity: Float) {
        _playerState.update { it.copy(masterIntensity = intensity) }
        SettingsManager.beatMaxIntensity = intensity
    }
    
    fun updateMediaVolume(volume: Float) {
        val coercedVolume = volume.coerceIn(0f, 1f)
        _playerState.update { it.copy(mediaVolume = coercedVolume) }
        syncPlaybackSettings()
        SettingsManager.mediaVolume = coercedVolume
    }

    fun syncPlaybackSettings() {
        mediaPlayer?.let { mp ->
            val vol = (_playerState.value.mediaVolume * SettingsManager.volumeBoost).coerceIn(0f, 1f)
            mp.setVolume(vol, vol)
            try {
                val params = mp.playbackParams
                params.speed = SettingsManager.playbackSpeed
                mp.playbackParams = params
            } catch (e: Exception) {
            }
        }
    }

    fun updateSelectedTrack(uri: Uri?, name: String) {
        stopPlayback()
        _playerState.update { it.copy(
            selectedFileUri = uri,
            selectedFileName = name,
            nextBeatIndex = 0,
            currentTimestampMs = 0,
            totalDurationMs = 0
        ) }
    }

    fun findExistingProfile(context: Context, parentUri: Uri, audioFileName: String, profile: BeatProfile): Uri? {
        val hapticFileName = "${audioFileName.substringBeforeLast(".")}_${profile.name.lowercase()}.haptic.json"
        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(parentUri)
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, rootDocId)
            
            context.contentResolver.query(
                childrenUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_DOCUMENT_ID),
                null, null, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    if (cursor.getString(0) == hapticFileName) {
                        return DocumentsContract.buildDocumentUriUsingTree(parentUri, cursor.getString(1))
                    }
                }
            }
        } catch (e: Exception) { }
        return null
    }

    fun loadMergedProfiles(context: Context, uri: Uri?, fileName: String, parentUri: Uri) {
        if (uri == null) {
            _playerState.update { it.copy(detectedBeats = emptyList(), nextBeatIndex = 0) }
            return
        }

        val allBeats = mutableListOf<DetectedBeat>()
        BeatProfile.entries.forEach { profile ->
            val profileUri = findExistingProfile(context, parentUri, fileName, profile)
            if (profileUri != null) {
                try {
                    context.contentResolver.openInputStream(profileUri)?.use { inputStream ->
                        val json = Scanner(inputStream).useDelimiter("\\A").next()
                        val hProfile = Json.decodeFromString<HapticProfile>(json)
                        // Ensure each beat has the correct profile assigned
                        allBeats.addAll(hProfile.beats.map { it.copy(profile = profile) })
                    }
                } catch (e: Exception) { }
            }
        }

        val sortedBeats = allBeats.sortedBy { it.timestampMs }
        _playerState.update { state ->
            state.copy(
                detectedBeats = sortedBeats,
                nextBeatIndex = findNextBeatIndex(sortedBeats, state.currentTimestampMs - SettingsManager.hapticSyncOffsetMs)
            )
        }
    }

    private fun findNextBeatIndex(beats: List<DetectedBeat>, currentMs: Long): Int {
        val index = beats.indexOfFirst { it.timestampMs >= currentMs }
        return if (index == -1) beats.size else index
    }

    fun setBatchProgress(current: Int, total: Int, duration: String = "", remainingMs: Long = 0) {
        _playerState.update { it.copy(
            analysisFileIndex = current,
            analysisTotalFiles = total,
            analysisTaskProgress = "$current of $total",
            analysisFileDuration = duration,
            analysisTotalRemainingMs = remainingMs
        ) }
    }

    suspend fun analyzeAudioUri(
        context: Context,
        uri: Uri,
        profile: BeatProfile
    ): List<DetectedBeat> = withContext(Dispatchers.Default) {
        if (!analysisLock.compareAndSet(false, true)) return@withContext emptyList()

        _playerState.update { it.copy(isAnalyzing = true, analysisProgress = 0f) }
        analysisJob = currentCoroutineContext()[Job]
        
        try {
            val candidates = performAnalysisPass(context, uri, profile, 0.0)
            if (candidates.isEmpty()) return@withContext emptyList()
            
            val sortedCandidates = candidates.sortedByDescending { it.energy }
            val thresholdIndex = (sortedCandidates.size * 0.2).toInt().coerceAtMost(sortedCandidates.size - 1)
            val dynamicThreshold = sortedCandidates[thresholdIndex].energy

            var finalBeats = candidates.filter { it.energy >= dynamicThreshold }
            if (finalBeats.isEmpty()) finalBeats = sortedCandidates.take((candidates.size * 0.1).toInt().coerceAtLeast(1))

            val detectedBeats = finalBeats.map { DetectedBeat(it.timestampMs, it.intensity, it.durationMs, 2, profile) }
            
            return@withContext detectedBeats
        } finally {
            _playerState.update { it.copy(isAnalyzing = false) }
            analysisLock.set(false)
            analysisJob = null
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        analysisLock.set(false)
        _playerState.update { it.copy(isAnalyzing = false) }
    }

    private suspend fun performAnalysisPass(context: Context, uri: Uri, profile: BeatProfile, energyThreshold: Double): List<BeatCandidate> = withContext(Dispatchers.Default) {
        val candidates = mutableListOf<BeatCandidate>()
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        val energyHistory = mutableListOf<Double>()
        var lastPeakTimeMs = -500L
        var lastGuitarEnergy = 0.0
        var lastBassEnergy = 0.0

        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { extractor.setDataSource(it.fileDescriptor) } ?: return@withContext emptyList()
            
            val trackIndex = selectAudioTrack(extractor)
            if (trackIndex < 0) return@withContext emptyList()

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext emptyList()
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)

            codec = MediaCodec.createDecoderByType(mime).apply {
                configure(format, null, null, 0)
                start()
            }
            extractor.selectTrack(trackIndex)

            val info = MediaCodec.BufferInfo()
            var isExtractorDone = false
            
            while (!isExtractorDone && isActive) {
                val inputIndex = codec.dequeueInputBuffer(10000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)!!
                    val sampleSize = extractor.readSampleData(inputBuffer, 0)
                    if (sampleSize < 0) {
                        codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        isExtractorDone = true
                    } else {
                        val progress = extractor.sampleTime.toFloat() / durationUs.toFloat()
                        _playerState.update { it.copy(analysisProgress = progress) }
                        codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }

                var outputIndex = codec.dequeueOutputBuffer(info, 10000)
                while (outputIndex >= 0) {
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) isExtractorDone = true
                    
                    if (info.size > 0) {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                        val pcm = ShortArray(info.size / 2)
                        outputBuffer.asShortBuffer().get(pcm)
                        
                        val result = processPcmWindow(pcm, info.presentationTimeUs / 1000, format, profile, lastPeakTimeMs, energyThreshold, energyHistory, lastGuitarEnergy, lastBassEnergy)
                        result?.candidate?.let {
                            candidates.add(it)
                            lastPeakTimeMs = it.timestampMs
                        }
                        result?.newGuitarEnergy?.let { lastGuitarEnergy = it }
                        result?.newBassEnergy?.let { lastBassEnergy = it }
                    }

                    codec.releaseOutputBuffer(outputIndex, false)
                    outputIndex = codec.dequeueOutputBuffer(info, 10000)
                }
            }
        } finally {
            codec?.stop(); codec?.release(); extractor.release()
        }
        return@withContext candidates
    }

    fun playSynchronized(context: Context) {
        val uri = _playerState.value.selectedFileUri ?: return
        if (!SettingsManager.isLiveHapticsEnabled && _playerState.value.detectedBeats.isEmpty()) {
            return
        }

        stopPlayback()
        HapticManager.stopHeartbeatSession()

        SettingsManager.lastPlayedAudioUri = uri.toString()
        SettingsManager.lastPlayedAudioName = _playerState.value.selectedFileName
        
        mediaPlayer = MediaPlayer().apply {
            setDataSource(context, uri)
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setOnCompletionListener {
                if (SettingsManager.isRepeatEnabled) {
                    seekTo(0L)
                    start()
                } else {
                    _playerState.update { it.copy(isPlaying = false, isPaused = false, currentTimestampMs = 0, nextBeatIndex = 0) }
                    visualizer?.enabled = false
                    HapticManager.resetIconAlphas()
                    onTrackFinished?.invoke()
                }
            }
            prepare()
            syncPlaybackSettings()
            setupVisualizer(audioSessionId)
            start()
        }

        _playerState.update { state ->
            state.copy(
                isPlaying = true,
                isPaused = false,
                totalDurationMs = mediaPlayer?.duration?.toLong() ?: 0L,
                nextBeatIndex = findNextBeatIndex(_playerState.value.detectedBeats, 0L - SettingsManager.hapticSyncOffsetMs)
            )
        }

        startPlaybackJob()
    }

    private fun setupVisualizer(audioSessionId: Int) {
        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        if (SettingsManager.isWaveformEnabled) {
                            waveform?.let {
                                val floatData = FloatArray(it.size)
                                for (i in it.indices) {
                                    floatData[i] = ((it[i].toInt() and 0xFF) - 128) / 128f
                                }
                                HapticManager.updateVisualizer(floatData)
                            }
                        }
                    }

                    override fun onFftDataCapture(visualizer: Visualizer?, fft: ByteArray?, samplingRate: Int) {
                        if (fft == null) return
                        
                        // Always calculate bands for visualizer UI
                        val n = fft.size / 2
                        val magnitudes = FloatArray(n)
                        for (i in 0 until n) {
                            val re = fft[i * 2].toFloat()
                            val im = fft[i * 2 + 1].toFloat()
                            magnitudes[i] = sqrt(re * re + im * im) / 128f
                        }
                        
                        val bands = FloatArray(32)
                        var totalSum = 0f
                        magnitudes.forEach { totalSum += it }
                        bands[0] = (totalSum / n) * SettingsManager.gainAmplitude // Amplitude
                        
                        var sum = 0f
                        for (j in 0 until 2) sum += magnitudes[1 + j]
                        bands[1] = (sum / 2) * SettingsManager.gainBass // Bass
                        
                        sum = 0f
                        for (j in 0 until 3) sum += magnitudes[6 + j]
                        bands[2] = (sum / 3) * SettingsManager.gainDrum // Drum
                        
                        sum = 0f
                        for (j in 0 until 4) sum += magnitudes[16 + j]
                        bands[3] = (sum / 4) * SettingsManager.gainGuitar // Guitar

                        HapticManager.updateVisualizer(bands)

                        // Live haptic trigger logic
                        if (SettingsManager.isLiveHapticsEnabled) {
                            val currentTime = System.currentTimeMillis()
                            if (currentTime - lastLiveHapticTime < 100) return // Global cooldown

                            val masterIntensity = _playerState.value.masterIntensity
                            
                            when (liveHapticProfile) {
                                BeatProfile.AMPLITUDE -> if (bands[0] > SettingsManager.triggerThresholdAmplitude) {
                                    triggerLiveHaptic(BeatProfile.AMPLITUDE, bands[0], masterIntensity)
                                }
                                BeatProfile.BASS -> if (bands[1] > SettingsManager.triggerThresholdBass) {
                                    triggerLiveHaptic(BeatProfile.BASS, bands[1], masterIntensity)
                                }
                                BeatProfile.DRUM -> if (bands[2] > SettingsManager.triggerThresholdDrum) {
                                    triggerLiveHaptic(BeatProfile.DRUM, bands[2], masterIntensity)
                                }
                                BeatProfile.GUITAR -> if (bands[3] > SettingsManager.triggerThresholdGuitar) {
                                    triggerLiveHaptic(BeatProfile.GUITAR, bands[3], masterIntensity)
                                }
                            }
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)
                enabled = true
            }
        } catch (e: Exception) { }
    }
    
    private fun triggerLiveHaptic(profile: BeatProfile, intensity: Float, masterIntensity: Float) {
        lastLiveHapticTime = System.currentTimeMillis()
        val scaledIntensity = (intensity * masterIntensity).coerceIn(0f, 1f)
        val duration = when (profile) {
            BeatProfile.BASS -> 350L
            BeatProfile.DRUM -> 120L
            else -> 100L
        }
        
        val assignedDevices = SettingsManager.deviceAssignments.filter { it.value.contains(profile) }.keys
        
        assignedDevices.forEach { device ->
            HapticManager.updateDeviceVisuals(device, scaledIntensity, profile.getColor(), profile)
            if (device == HapticDevice.PHONE_LEFT || device == HapticDevice.PHONE_RIGHT) {
                HapticManager.playRawVibration(duration, (255 * scaledIntensity).toInt().coerceIn(1, 255))
            }
        }
    }

    private fun startPlaybackJob() {
        if (SettingsManager.isLiveHapticsEnabled) {
            // In Live mode, haptics are driven by the Visualizer's FFT listener, not the pre-analyzed beats.
            // We still need a job to update the current timestamp for the UI.
            playbackJob?.cancel()
            playbackJob = CoroutineScope(Dispatchers.Default).launch {
                 while (isActive) {
                    if (mediaPlayer?.isPlaying == true) {
                        _playerState.update { it.copy(currentTimestampMs = mediaPlayer?.currentPosition?.toLong() ?: 0L) }
                    }
                    delay(50)
                }
            }
            return
        }

        playbackJob?.cancel()
        playbackJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                if (mediaPlayer?.isPlaying == true) {
                    val currentPos = mediaPlayer?.currentPosition?.toLong() ?: 0L
                    val beatsToTrigger = mutableListOf<DetectedBeat>()
                    
                    _playerState.update { state ->
                        var nextIndex = state.nextBeatIndex
                        // SYNC LOGIC: Subtract offset from currentPos.
                        val adjustedPos = currentPos - SettingsManager.hapticSyncOffsetMs
                        
                        while (nextIndex < state.detectedBeats.size && adjustedPos >= state.detectedBeats[nextIndex].timestampMs) {
                            beatsToTrigger.add(state.detectedBeats[nextIndex])
                            nextIndex++
                        }
                        
                        state.copy(currentTimestampMs = currentPos, nextBeatIndex = nextIndex)
                    }
                    
                    beatsToTrigger.forEach { triggerBeatHaptic(it) }
                }
                delay(5)
            }
        }
    }

    fun pausePlayback() {
        mediaPlayer?.pause()
        visualizer?.enabled = false
        HapticManager.resetIconAlphas()
        _playerState.update { it.copy(isPlaying = false, isPaused = true) }
        HapticManager.setPauseStartTime(System.currentTimeMillis())
    }

    fun resumePlayback() {
        mediaPlayer?.start()
        syncPlaybackSettings()
        visualizer?.enabled = true
        _playerState.update { state ->
            state.copy(
                isPlaying = true,
                isPaused = false,
                nextBeatIndex = findNextBeatIndex(state.detectedBeats, state.currentTimestampMs - SettingsManager.hapticSyncOffsetMs)
            )
        }
        HapticManager.setPauseStartTime(0)
    }

    fun seekTo(timestampMs: Long) {
        mediaPlayer?.let { mp ->
            val coercedTime = timestampMs.coerceIn(0, _playerState.value.totalDurationMs)
            mp.seekTo(coercedTime.toInt())
            _playerState.update { state ->
                state.copy(
                    currentTimestampMs = coercedTime,
                    nextBeatIndex = findNextBeatIndex(state.detectedBeats, coercedTime - SettingsManager.hapticSyncOffsetMs)
                )
            }
        }
    }

    fun skipForward(seconds: Int) = seekTo(_playerState.value.currentTimestampMs + (seconds * 1000))
    fun skipBackward(seconds: Int) = seekTo(_playerState.value.currentTimestampMs - (seconds * 1000))

    private fun triggerBeatHaptic(beat: DetectedBeat) {
        val maxIntensity = _playerState.value.masterIntensity
        val scaledIntensity = (beat.intensity * maxIntensity).coerceIn(0f, 1f)
        
        val assignedDevices = SettingsManager.deviceAssignments.filter { it.value.contains(beat.profile) }.keys
        
        assignedDevices.forEach { device ->
            HapticManager.updateDeviceVisuals(device, scaledIntensity, beat.profile.getColor(), beat.profile)
            
            if (device == HapticDevice.PHONE_LEFT || device == HapticDevice.PHONE_RIGHT) {
                HapticManager.playRawVibration(beat.durationMs.toLong(), (255 * scaledIntensity).toInt().coerceIn(1, 255))
            }
        }
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        visualizer?.enabled = false
        try { visualizer?.release() } catch (e: Exception) {}
        visualizer = null
        try { mediaPlayer?.stop() } catch (e: Exception) {}
        try { mediaPlayer?.release() } catch (e: Exception) {}
        mediaPlayer = null
        HapticManager.resetIconAlphas()
        _playerState.update { it.copy(isPlaying = false, isPaused = false, currentTimestampMs = 0, nextBeatIndex = 0) }
    }
    fun resetPlayer() {
        // Keep the currently selected file info after the reset.
        val currentUri = _playerState.value.selectedFileUri
        val currentName = _playerState.value.selectedFileName
        stopPlayback()
        _playerState.value = BeatPlayerState(
            selectedFileUri = currentUri,
            selectedFileName = currentName
        )
        Logger.info("BB Player state has been reset.")
    }
    fun saveProfile(context: Context, parentTreeUri: Uri, audioFileName: String, profile: BeatProfile, beats: List<DetectedBeat>) {
        val hapticProfile = HapticProfile(audioFileName, profile.name, beats)
        val jsonString = Json.encodeToString(hapticProfile)
        val hapticFileName = "${audioFileName.substringBeforeLast(".")}_${profile.name.lowercase()}.haptic.json"
        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(parentTreeUri)
            val parentFolderUri = DocumentsContract.buildDocumentUriUsingTree(parentTreeUri, rootDocId)
            val existingUri = findExistingProfile(context, parentTreeUri, audioFileName, profile)
            val targetUri = existingUri ?: DocumentsContract.createDocument(context.contentResolver, parentFolderUri, "application/json", hapticFileName)
            targetUri?.let { uri ->
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(jsonString.toByteArray(Charset.defaultCharset())) }
            }
        } catch (e: Exception) { }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) return i
        }
        return -1
    }
    
    private fun processPcmWindow(pcm: ShortArray, timestampMs: Long, format: MediaFormat, profile: BeatProfile, lastPeakTimeMs: Long, energyThreshold: Double, energyHistory: MutableList<Double>, lastGuitarEnergy: Double, lastBassEnergy: Double): ProcessResult? {
        val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
        val refractoryPeriod = when (profile) {
            BeatProfile.GUITAR -> 200L
            BeatProfile.BASS -> 400L
            BeatProfile.DRUM -> 150L
            else -> 200L
        }
        if (timestampMs - lastPeakTimeMs < refractoryPeriod) return null
        return when (profile) {
            BeatProfile.AMPLITUDE -> ProcessResult(analyzeAmplitude(pcm, timestampMs, channels, energyThreshold, energyHistory))
            BeatProfile.BASS -> analyzeBassPeak(pcm, timestampMs, sampleRate, channels, energyThreshold, lastBassEnergy)
            BeatProfile.DRUM -> ProcessResult(analyzeDrumPeak(pcm, timestampMs, sampleRate, channels, energyThreshold, energyHistory))
            BeatProfile.GUITAR -> analyzeGuitarPeak(pcm, timestampMs, sampleRate, channels, energyThreshold, lastGuitarEnergy)
        }
    }

    private fun analyzeAmplitude(pcm: ShortArray, timestampMs: Long, channels: Int, energyThreshold: Double, energyHistory: MutableList<Double>): BeatCandidate? {
        if (pcm.isEmpty()) return null
        var sum = 0.0
        val n = pcm.size / channels
        var samplesProcessed = 0
        for (i in 0 until n step 4) { 
            var channelSum = 0.0
            for (c in 0 until channels) channelSum += pcm[i * channels + c]
            val sample = channelSum / channels
            sum += sample * sample
            samplesProcessed++
        }
        if(samplesProcessed == 0) return null
        val currentEnergy = sqrt(sum / samplesProcessed)
        if (energyHistory.size < ENERGY_HISTORY_SIZE) {
            energyHistory.add(currentEnergy)
            return null
        }
        val averageEnergy = energyHistory.average()
        val energyRatio = if (averageEnergy > 0) currentEnergy / averageEnergy else 0.0
        energyHistory.removeAt(0)
        energyHistory.add(currentEnergy)
        if (energyRatio > 1.3 && currentEnergy > energyThreshold) {
            val intensity = ((energyRatio - 1.3) / 1.5).toFloat().coerceIn(0.4f, 1.0f)
            return BeatCandidate(timestampMs, currentEnergy, intensity, 100)
        }
        return null
    }

    private fun analyzeBassPeak(pcm: ShortArray, timestampMs: Long, sampleRate: Int, channels: Int, energyThreshold: Double, lastBassEnergy: Double): ProcessResult {
        val n = 512
        if (pcm.size < n * channels) return ProcessResult(null, newBassEnergy = lastBassEnergy)
        val fft = DoubleFFT_1D(n.toLong())
        val data = DoubleArray(n * 2)
        for (i in 0 until n) {
            var sum = 0.0
            for (c in 0 until channels) sum += pcm[i * channels + c]
            data[i] = sum / channels
        }
        fft.realForward(data)
        var bassEnergy = 0.0
        for (i in 1..10) {
            val re = data[2 * i]
            val im = data[2 * i + 1]
            bassEnergy += sqrt(re * re + im * im)
        }
        val avgEnergy = bassEnergy / 10.0
        val onsetRatio = if (lastBassEnergy > 0) avgEnergy / lastBassEnergy else 2.0
        if (onsetRatio > 2.0 && avgEnergy > energyThreshold) {
            val intensity = ((avgEnergy - 60.0) / 200.0 * 0.5 + 0.5).coerceIn(0.5, 1.0).toFloat()
            return ProcessResult(BeatCandidate(timestampMs, avgEnergy, intensity, 350), newBassEnergy = avgEnergy)
        }
        return ProcessResult(null, newBassEnergy = avgEnergy)
    }

    private fun analyzeDrumPeak(pcm: ShortArray, timestampMs: Long, sampleRate: Int, channels: Int, energyThreshold: Double, energyHistory: MutableList<Double>): BeatCandidate? {
        if (pcm.isEmpty()) return null
        var sum = 0.0
        val n = pcm.size / channels
        var samplesProcessed = 0
        for (i in 0 until n step 4) {
            var channelSum = 0.0
            for (c in 0 until channels) channelSum += pcm[i * channels + c]
            val sample = channelSum / channels
            sum += sample * sample
            samplesProcessed++
        }
        if(samplesProcessed == 0) return null
        val currentEnergy = sqrt(sum / samplesProcessed)
        if (energyHistory.size < ENERGY_HISTORY_SIZE) {
            energyHistory.add(currentEnergy)
            return null
        }
        val averageEnergy = energyHistory.average()
        val energyRatio = if (averageEnergy > 0) currentEnergy / averageEnergy else 0.0
        energyHistory.removeAt(0)
        energyHistory.add(currentEnergy)
        if (energyRatio > 1.8 && currentEnergy > energyThreshold) {
            val intensity = ((energyRatio - 1.8) / 1.5).toFloat().coerceIn(0.5f, 1.0f)
            return BeatCandidate(timestampMs, currentEnergy, intensity, 120)
        }
        return null
    }

    private fun analyzeGuitarPeak(pcm: ShortArray, timestampMs: Long, sampleRate: Int, channels: Int, energyThreshold: Double, lastGuitarEnergy: Double): ProcessResult {
        val n = 512
        if (pcm.size < n * channels) return ProcessResult(null, newGuitarEnergy = lastGuitarEnergy)
        val fft = DoubleFFT_1D(n.toLong())
        val data = DoubleArray(n * 2)
        for (i in 0 until n) {
            var sum = 0.0
            for (c in 0 until channels) sum += pcm[i * channels + c]
            data[i] = sum / channels
        }
        fft.realForward(data)
        var guitarEnergy = 0.0
        val lowBin = (700 * n / sampleRate).coerceAtLeast(1)
        val highBin = (3000 * n / sampleRate).coerceAtMost(n / 2)
        for (i in lowBin..highBin) {
            val re = data[2 * i]
            val im = data[2 * i + 1]
            guitarEnergy += sqrt(re * re + im * im)
        }
        val avgEnergy = guitarEnergy / (highBin - lowBin + 1)
        val onsetRatio = if (lastGuitarEnergy > 0) avgEnergy / lastGuitarEnergy else 1.6
        if (onsetRatio > 1.6 && avgEnergy > energyThreshold) {
            val intensity = ((onsetRatio - 1.6) / 2.0 * 0.5 + 0.5).coerceIn(0.5, 1.0).toFloat()
            val duration = (150 + (avgEnergy * 2)).toInt().coerceAtMost(450)
            return ProcessResult(BeatCandidate(timestampMs, avgEnergy, intensity, duration), newGuitarEnergy = avgEnergy)
        }
        return ProcessResult(null, newGuitarEnergy = avgEnergy)
    }

    internal data class ProcessResult(val candidate: BeatCandidate?, val newGuitarEnergy: Double? = null, val newBassEnergy: Double? = null)
}
