package com.kapcode.thehapticptsdproject

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jtransforms.fft.DoubleFFT_1D
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Scanner
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.sqrt

enum class BeatProfile {
    AMPLITUDE, DRUM, BASS, GUITAR
}

@Serializable
data class DetectedBeat(
    val timestampMs: Long,
    val intensity: Float,
    val durationMs: Int,
    val channel: Int // 0: Left, 1: Right, 2: Both
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
    val analysisTaskProgress: String = "", // e.g. "1 of 10"
    val analysisFileDuration: String = "", // e.g. "3:45"
    val analysisTotalRemainingMs: Long = 0, // Sum of remaining track durations
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

private data class PcmChunk(
    val pcm: ShortArray,
    val timestampMs: Long
)

object BeatDetector {

    private val _playerState = MutableStateFlow(BeatPlayerState())
    val playerState = _playerState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private var playbackJob: Job? = null
    private var analysisJob: Job? = null
    private val analysisLock = AtomicBoolean(false)
    
    private const val ENERGY_HISTORY_SIZE = 43 // ~1 second of audio history

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
        mediaPlayer?.setVolume(coercedVolume, coercedVolume)
        SettingsManager.mediaVolume = coercedVolume
    }

    fun updateSelectedTrack(uri: Uri?, name: String) {
        _playerState.update { it.copy(
            selectedFileUri = uri,
            selectedFileName = name,
            detectedBeats = emptyList(),
            nextBeatIndex = 0
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
        } catch (e: Exception) {
            // Ignore search errors
        }
        return null
    }

    fun loadProfile(context: Context, uri: Uri?): List<DetectedBeat>? {
        if (uri == null) {
            _playerState.update { it.copy(detectedBeats = emptyList(), nextBeatIndex = 0) }
            return null
        }
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val json = Scanner(inputStream).useDelimiter("\\A").next()
                val profile = Json.decodeFromString<HapticProfile>(json)
                
                _playerState.update { state ->
                    val newNextIndex = findNextBeatIndex(profile.beats, state.currentTimestampMs)
                    state.copy(
                        detectedBeats = profile.beats,
                        nextBeatIndex = newNextIndex
                    )
                }
                profile.beats
            }
        } catch (e: Exception) {
            Logger.error("Load Error: ${e.message}")
            null
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
        if (!analysisLock.compareAndSet(false, true)) {
            Logger.info("Analysis already in progress. Ignoring request.")
            return@withContext emptyList()
        }

        _playerState.update { it.copy(isAnalyzing = true, analysisProgress = 0f, detectedBeats = emptyList()) }
        analysisJob = currentCoroutineContext()[Job]
        
        try {
            Logger.info("Starting analysis for URI: $uri")
            val candidates = performAnalysisPass(context, uri, profile, 0.0)
            if (candidates.isEmpty()) {
                Logger.error("Pass 1: No beat candidates found.")
                return@withContext emptyList()
            }
            Logger.info("Pass 1: Found ${candidates.size} potential candidates.")
            
            val sortedCandidates = candidates.sortedByDescending { it.energy }
            val thresholdIndex = (sortedCandidates.size * 0.2).toInt().coerceAtMost(sortedCandidates.size - 1)
            val dynamicThreshold = sortedCandidates[thresholdIndex].energy
            Logger.info("Dynamic energy threshold set to: $dynamicThreshold")

            var finalBeats = candidates.filter { it.energy >= dynamicThreshold }
            Logger.info("Pass 2: Filtered down to ${finalBeats.size} beats.")

            if (finalBeats.isEmpty()) {
                val fallbackCount = (candidates.size * 0.1).toInt().coerceAtLeast(1)
                finalBeats = sortedCandidates.take(fallbackCount)
                Logger.info("Fallback triggered: Taking top $fallbackCount candidates.")
            }

            val detectedBeats = finalBeats.map { DetectedBeat(it.timestampMs, it.intensity, it.durationMs, 2) }
            
            _playerState.update { state ->
                state.copy(
                    detectedBeats = detectedBeats, 
                    analysisProgress = 1f,
                    nextBeatIndex = if (state.isPlaying) findNextBeatIndex(detectedBeats, state.currentTimestampMs) else 0
                )
            }
            Logger.info("Analysis complete. Final beat count: ${detectedBeats.size}")
            return@withContext detectedBeats
        } catch (e: CancellationException) {
            Logger.info("Analysis cancelled.")
            throw e
        } catch (e: Exception) {
            Logger.error("Analysis Error: ${e.message}")
            return@withContext emptyList()
        } finally {
            _playerState.update { it.copy(isAnalyzing = false) }
            analysisLock.set(false)
            analysisJob = null
        }
    }

    fun cancelAnalysis() {
        analysisJob?.cancel()
        analysisLock.set(false)
        _playerState.update { it.copy(isAnalyzing = false, analysisTaskProgress = "", analysisFileDuration = "", analysisTotalRemainingMs = 0) }
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
        if (_playerState.value.detectedBeats.isEmpty()) return

        stopPlayback()
        HapticManager.stopHeartbeatSession() // Ensure heartbeat is stopped

        // Save last played
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
            prepare()
            val volume = _playerState.value.mediaVolume
            setVolume(volume, volume)
            
            setupVisualizer(audioSessionId)
            
            start()
        }

        _playerState.update { it.copy(
            isPlaying = true,
            isPaused = false,
            totalDurationMs = mediaPlayer?.duration?.toLong() ?: 0L,
            nextBeatIndex = 0
        ) }

        startPlaybackJob()
    }

    private fun setupVisualizer(audioSessionId: Int) {
        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                    override fun onWaveFormDataCapture(visualizer: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                        if (SettingsManager.visualizerType == VisualizerType.WAVEFORM) {
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
                        if (SettingsManager.visualizerType == VisualizerType.VERTICAL_BARS) {
                            fft?.let {
                                val n = it.size / 2
                                val magnitudes = FloatArray(n)
                                for (i in 0 until n) {
                                    val re = it[i * 2].toFloat()
                                    val im = it[i * 2 + 1].toFloat()
                                    magnitudes[i] = sqrt(re * re + im * im) / 128f
                                }
                                
                                // Group into 32 bands with non-linear mapping to match profile ranges
                                val bands = FloatArray(32)
                                
                                // Band 0: Overall Amplitude (Full spectrum average)
                                var totalSum = 0f
                                magnitudes.forEach { totalSum += it }
                                bands[0] = (totalSum / n) * 3f
                                
                                // Bands 1-2: Bass Range (Bins 1-5)
                                for (i in 1..2) {
                                    val startBin = 1 + (i - 1) * 2
                                    var sum = 0f
                                    for (j in 0 until 2) sum += magnitudes[startBin + j]
                                    bands[i] = (sum / 2) * 2.5f
                                }
                                
                                // Bands 3-5: Drum Range (Bins 6-15)
                                for (i in 3..5) {
                                    val startBin = 6 + (i - 3) * 3
                                    var sum = 0f
                                    for (j in 0 until 3) sum += magnitudes[startBin + j]
                                    bands[i] = (sum / 3) * 2.2f
                                }
                                
                                // Bands 6-12: Guitar Range (Bins 16-40)
                                for (i in 6..12) {
                                    val startBin = 16 + (i - 6) * 4
                                    var sum = 0f
                                    for (j in 0 until 4) sum += magnitudes[startBin + j]
                                    bands[i] = (sum / 4) * 1.8f
                                }
                                
                                // Bands 13-31: Highs (Bins 41-511)
                                val remainingBins = n - 41
                                val bandSizeHigh = (remainingBins / 19).coerceAtLeast(1)
                                for (i in 13..31) {
                                    val startBin = 41 + (i - 13) * bandSizeHigh
                                    var sum = 0f
                                    for (j in 0 until bandSizeHigh) {
                                        if (startBin + j < n) sum += magnitudes[startBin + j]
                                    }
                                    bands[i] = (sum / bandSizeHigh) * 1.5f
                                }

                                HapticManager.updateVisualizer(bands)
                            }
                        } else if (SettingsManager.visualizerType == VisualizerType.CHANNEL_INTENSITY) {
                            fft?.let {
                                var sum = 0f
                                for (i in it.indices) {
                                    sum += abs(it[i].toFloat())
                                }
                                val intensity = (sum / it.size / 128f) * 3f
                                HapticManager.updateVisualizer(floatArrayOf(intensity, intensity))
                            }
                        }
                    }
                }, Visualizer.getMaxCaptureRate() / 2, true, true)
                enabled = true
            }
        } catch (e: Exception) {
            Logger.error("Visualizer Error: ${e.message}")
        }
    }

    private fun startPlaybackJob() {
        playbackJob?.cancel()
        playbackJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                if (mediaPlayer?.isPlaying == true) {
                    val currentPos = mediaPlayer?.currentPosition?.toLong() ?: 0L
                    
                    var beatToTrigger: DetectedBeat? = null
                    
                    _playerState.update { state ->
                        val nextIndex = state.nextBeatIndex
                        if (nextIndex < state.detectedBeats.size) {
                            val beat = state.detectedBeats[nextIndex]
                            if (currentPos >= beat.timestampMs) {
                                beatToTrigger = beat
                                state.copy(currentTimestampMs = currentPos, nextBeatIndex = nextIndex + 1)
                            } else {
                                state.copy(currentTimestampMs = currentPos)
                            }
                        } else {
                            state.copy(currentTimestampMs = currentPos)
                        }
                    }
                    
                    beatToTrigger?.let { triggerBeatHaptic(it) }
                }
                delay(5)
            }
        }
    }

    fun pausePlayback() {
        mediaPlayer?.pause()
        visualizer?.enabled = false
        _playerState.update { it.copy(isPlaying = false, isPaused = true) }
    }

    fun resumePlayback() {
        mediaPlayer?.start()
        visualizer?.enabled = true
        _playerState.update { state ->
            state.copy(
                isPlaying = true,
                isPaused = false,
                nextBeatIndex = findNextBeatIndex(state.detectedBeats, state.currentTimestampMs)
            )
        }
    }

    fun seekTo(timestampMs: Long) {
        mediaPlayer?.let { mp ->
            val coercedTime = timestampMs.coerceIn(0, _playerState.value.totalDurationMs)
            mp.seekTo(coercedTime.toInt())
            
            _playerState.update { state ->
                state.copy(
                    currentTimestampMs = coercedTime,
                    nextBeatIndex = findNextBeatIndex(state.detectedBeats, coercedTime)
                )
            }
        }
    }

    fun skipForward(seconds: Int) {
        seekTo(_playerState.value.currentTimestampMs + (seconds * 1000))
    }

    fun skipBackward(seconds: Int) {
        seekTo(_playerState.value.currentTimestampMs - (seconds * 1000))
    }

    private fun triggerBeatHaptic(beat: DetectedBeat) {
        val maxIntensity = _playerState.value.masterIntensity
        val scaledIntensity = (beat.intensity * maxIntensity).coerceIn(0f, 1f)
        
        HapticManager.playPulse(
            intensityOverride = scaledIntensity,
            durationMs = beat.durationMs.toLong(),
            channel = beat.channel
        )
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        visualizer?.enabled = false
        visualizer?.release()
        visualizer = null
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _playerState.update { it.copy(
            isPlaying = false, 
            isPaused = false,
            currentTimestampMs = 0,
            nextBeatIndex = 0
        ) }
    }

    fun saveProfile(context: Context, parentTreeUri: Uri, audioFileName: String, profile: BeatProfile, beats: List<DetectedBeat>) {
        val hapticProfile = HapticProfile(audioFileName, profile.name, beats)
        val jsonString = Json.encodeToString(hapticProfile)
        val hapticFileName = "${audioFileName.substringBeforeLast(".")}_${profile.name.lowercase()}.haptic.json"
        
        try {
            val rootDocId = DocumentsContract.getTreeDocumentId(parentTreeUri)
            val parentFolderUri = DocumentsContract.buildDocumentUriUsingTree(parentTreeUri, rootDocId)

            val existingUri = findExistingProfile(context, parentTreeUri, audioFileName, profile)
            val targetUri = if (existingUri != null) {
                Logger.info("Overwriting existing profile: $hapticFileName")
                existingUri
            } else {
                DocumentsContract.createDocument(
                    context.contentResolver,
                    parentFolderUri,
                    "application/json",
                    hapticFileName
                )
            }
            
            targetUri?.let { uri ->
                context.contentResolver.openOutputStream(uri, "wt")?.use { outputStream ->
                    outputStream.write(jsonString.toByteArray(Charset.defaultCharset()))
                }
                Logger.info("Haptic profile saved successfully.")
            }
        } catch (e: Exception) {
            Logger.error("Save Error: ${e.message}")
        }
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) return i
        }
        return -1
    }
    
    internal data class ProcessResult(
        val candidate: BeatCandidate?,
        val newGuitarEnergy: Double? = null,
        val newBassEnergy: Double? = null
    )

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
            for (c in 0 until channels) {
                channelSum += pcm[i * channels + c]
            }
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
            for (c in 0 until channels) {
                channelSum += pcm[i * channels + c]
            }
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
}
