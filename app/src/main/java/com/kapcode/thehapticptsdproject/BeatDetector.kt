package com.kapcode.thehapticptsdproject

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.annotation.RequiresApi
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jtransforms.fft.DoubleFFT_1D
import java.io.InputStream
import java.nio.charset.Charset
import java.util.Scanner
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
    val isAnalyzing: Boolean = false,
    val analysisProgress: Float = 0f,
    val currentTimestampMs: Long = 0,
    val totalDurationMs: Long = 0,
    val nextBeatIndex: Int = 0,
    val selectedFileUri: Uri? = null,
    val selectedFileName: String = "No file selected",
    val detectedBeats: List<DetectedBeat> = emptyList(),
    val masterIntensity: Float = 1.0f,
    val mediaVolume: Float = 1.0f
)

object BeatDetector {

    private val _playerState = MutableStateFlow(BeatPlayerState())
    val playerState = _playerState.asStateFlow()

    private var mediaPlayer: MediaPlayer? = null
    private var playbackJob: Job? = null
    
    private var lastGuitarEnergy = 0.0
    private var lastBassEnergy = 0.0
    private var lastDrumEnergy = 0.0

    fun init() {
        _playerState.value = _playerState.value.copy(
            masterIntensity = SettingsManager.beatMaxIntensity,
            mediaVolume = SettingsManager.mediaVolume
        )
    }

    fun updateMasterIntensity(intensity: Float) {
        _playerState.value = _playerState.value.copy(masterIntensity = intensity)
        SettingsManager.beatMaxIntensity = intensity
    }
    
    fun updateMediaVolume(volume: Float) {
        val coercedVolume = volume.coerceIn(0f, 1f)
        _playerState.value = _playerState.value.copy(mediaVolume = coercedVolume)
        mediaPlayer?.setVolume(coercedVolume, coercedVolume)
        SettingsManager.mediaVolume = coercedVolume
    }

    fun updateSelectedTrack(uri: Uri?, name: String) {
        _playerState.value = _playerState.value.copy(
            selectedFileUri = uri,
            selectedFileName = name,
            detectedBeats = emptyList()
        )
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

    fun loadProfile(context: Context, uri: Uri): List<DetectedBeat>? {
        return try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val json = Scanner(inputStream).useDelimiter("\\A").next()
                val profile = Json.decodeFromString<HapticProfile>(json)
                _playerState.value = _playerState.value.copy(detectedBeats = profile.beats)
                profile.beats
            }
        } catch (e: Exception) {
            Logger.error("Load Error: ${e.message}")
            null
        }
    }

    suspend fun analyzeAudioUri(
        context: Context,
        uri: Uri,
        profile: BeatProfile
    ): List<DetectedBeat> = withContext(Dispatchers.Default) {
        Logger.info("Starting analysis for URI: $uri")
        _playerState.value = _playerState.value.copy(
            isAnalyzing = true, 
            analysisProgress = 0f, 
            detectedBeats = emptyList()
        )

        val beats = mutableListOf<DetectedBeat>()
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null

        lastGuitarEnergy = 0.0
        lastBassEnergy = 0.0
        lastDrumEnergy = 0.0
        
        try {
            Logger.debug("Setting extractor data source...")
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                extractor.setDataSource(fd.fileDescriptor)
            } ?: run {
                Logger.error("Failed to open file descriptor for URI.")
                _playerState.value = _playerState.value.copy(isAnalyzing = false)
                return@withContext emptyList()
            }

            val trackIndex = selectAudioTrack(extractor)
            if (trackIndex < 0) {
                Logger.error("No audio track found.")
                _playerState.value = _playerState.value.copy(isAnalyzing = false)
                return@withContext emptyList()
            }

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)
            if (mime == null) {
                Logger.error("MIME type is null.")
                _playerState.value = _playerState.value.copy(isAnalyzing = false)
                return@withContext emptyList()
            }
            
            Logger.debug("Track format: $format")
            
            codec = MediaCodec.createDecoderByType(mime).also {
                Logger.debug("Codec created: ${it.name}")
                it.configure(format, null, null, 0)
                it.start()
                Logger.debug("Codec started.")
            }

            extractor.selectTrack(trackIndex)

            val info = MediaCodec.BufferInfo()
            var isExtractorDone = false
            var isDecoderDone = false
            var lastPeakTimeMs = -500L

            Logger.debug("Starting decoding loop...")
            while (!isDecoderDone) {
                if (!isActive) {
                    Logger.info("Analysis coroutine cancelled.")
                    break
                }

                if (!isExtractorDone) {
                    val inputIndex = codec.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isExtractorDone = true
                        } else {
                            _playerState.value = _playerState.value.copy(analysisProgress = extractor.sampleTime.toFloat() / durationUs.toFloat())
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, 10000)
                if (outputIndex >= 0) {
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isDecoderDone = true
                    } else {
                        val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                        val currentPcm = ShortArray(info.size / 2)
                        outputBuffer.asShortBuffer().get(currentPcm)
                        
                        val presentationTimeMs = info.presentationTimeUs / 1000
                        processPcmWindow(currentPcm, presentationTimeMs, format.getInteger(MediaFormat.KEY_SAMPLE_RATE), format.getInteger(MediaFormat.KEY_CHANNEL_COUNT), profile, lastPeakTimeMs)?.let {
                            beats.add(it)
                            lastPeakTimeMs = it.timestampMs
                        }
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
            Logger.debug("Decoding loop finished.")

        } catch (e: Exception) {
            Logger.error("Analysis Exception: ${e.javaClass.simpleName} - ${e.message}")
            beats.clear()
        } finally {
            Logger.debug("Releasing resources...")
            try {
                codec?.stop()
                codec?.release()
                Logger.debug("Codec released.")
            } catch (e: Exception) {
                Logger.error("Error releasing codec: ${e.message}")
            }
            try {
                extractor.release()
                Logger.debug("Extractor released.")
            } catch (e: Exception) {
                Logger.error("Error releasing extractor: ${e.message}")
            }
        }

        Logger.info("Analysis finished. Found ${beats.size} beats.")
        _playerState.value = _playerState.value.copy(detectedBeats = beats, isAnalyzing = false)
        return@withContext beats
    }


    fun playSynchronized(context: Context) {
        val uri = _playerState.value.selectedFileUri ?: return
        val beats = _playerState.value.detectedBeats
        if (beats.isEmpty()) return

        stopPlayback()
        
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
            start()
        }

        _playerState.value = _playerState.value.copy(
            isPlaying = true,
            totalDurationMs = mediaPlayer?.duration?.toLong() ?: 0L,
            nextBeatIndex = 0
        )

        playbackJob = CoroutineScope(Dispatchers.Default).launch {
            while (mediaPlayer?.isPlaying == true) {
                val currentPos = mediaPlayer?.currentPosition?.toLong() ?: 0L
                _playerState.value = _playerState.value.copy(currentTimestampMs = currentPos)

                val nextIndex = _playerState.value.nextBeatIndex
                if (nextIndex < beats.size) {
                    val beat = beats[nextIndex]
                    if (currentPos >= beat.timestampMs) {
                        triggerBeatHaptic(beat)
                        _playerState.value = _playerState.value.copy(nextBeatIndex = nextIndex + 1)
                    }
                }
                delay(5)
            }
            stopPlayback()
        }
    }

    private fun triggerBeatHaptic(beat: DetectedBeat) {
        val maxIntensity = _playerState.value.masterIntensity
        val scaledIntensity = (beat.intensity * maxIntensity).coerceIn(0f, 1f)
        
        HapticManager.playPulse(
            intensityOverride = scaledIntensity,
            durationMs = beat.durationMs.toLong()
        )
    }

    fun stopPlayback() {
        playbackJob?.cancel()
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        _playerState.value = _playerState.value.copy(isPlaying = false)
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

    private fun processPcmWindow(pcm: ShortArray, timestampMs: Long, sampleRate: Int, channels: Int, profile: BeatProfile, lastPeakTimeMs: Long): DetectedBeat? {
        val refractoryPeriod = when (profile) {
            BeatProfile.GUITAR -> 350L
            BeatProfile.BASS -> 400L
            BeatProfile.DRUM -> 150L
            else -> 200L
        }
        if (timestampMs - lastPeakTimeMs < refractoryPeriod) return null
        
        return when (profile) {
            BeatProfile.AMPLITUDE -> analyzePeak(pcm, timestampMs)
            BeatProfile.BASS -> analyzeBassPeak(pcm, timestampMs, sampleRate, channels)
            BeatProfile.DRUM -> analyzeDrumPeak(pcm, timestampMs, sampleRate, channels)
            BeatProfile.GUITAR -> analyzeGuitarPeak(pcm, timestampMs, sampleRate, channels)
        }
    }

    private fun analyzePeak(pcm: ShortArray, timestampMs: Long): DetectedBeat? {
        var maxAmp = 0f
        for (sample in pcm) {
            val amp = abs(sample.toInt()) / 32768f
            if (amp > maxAmp) maxAmp = amp
        }
        return if (maxAmp > 0.5f) {
            val intensity = ((maxAmp - 0.5f) / 0.5f * 0.6f + 0.4f).coerceIn(0f, 1f)
            DetectedBeat(timestampMs, intensity, 100, 2)
        } else null
    }

    private fun analyzeBassPeak(pcm: ShortArray, timestampMs: Long, sampleRate: Int, channels: Int): DetectedBeat? {
        val n = 512
        if (pcm.size < n * channels) return null
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
        val isOnset = onsetRatio > 2.0 && avgEnergy > 60.0
        lastBassEnergy = avgEnergy
        
        return if (isOnset) {
            val intensity = ((avgEnergy - 60.0) / 200.0 * 0.5 + 0.5).coerceIn(0.5, 1.0).toFloat()
            DetectedBeat(timestampMs, intensity, 350, 2)
        } else null
    }

    private fun analyzeDrumPeak(pcm: ShortArray, timestampMs: Long, sampleRate: Int, channels: Int): DetectedBeat? {
        val n = 512
        if (pcm.size < n * channels) return null
        val fft = DoubleFFT_1D(n.toLong())
        val data = DoubleArray(n * 2)
        for (i in 0 until n) {
            var sum = 0.0
            for (c in 0 until channels) sum += pcm[i * channels + c]
            data[i] = sum / channels
        }
        fft.realForward(data)

        var transientEnergy = 0.0
        val lowBin = (100 * n / sampleRate).coerceAtLeast(1)
        val highBin = (8000 * n / sampleRate).coerceAtMost(n / 2)

        for (i in lowBin..highBin) {
            val re = data[2 * i]
            val im = data[2 * i + 1]
            transientEnergy += sqrt(re * re + im * im)
        }

        val avgEnergy = transientEnergy / (highBin - lowBin + 1)
        val onsetRatio = if (lastDrumEnergy > 0) avgEnergy / lastDrumEnergy else 2.0
        lastDrumEnergy = avgEnergy

        return if (onsetRatio > 2.5 && avgEnergy > 30.0) {
            val intensity = (onsetRatio / 5.0).toFloat().coerceIn(0.6f, 1.0f)
            DetectedBeat(timestampMs, intensity, 120, 2)
        } else null
    }

    private fun analyzeGuitarPeak(pcm: ShortArray, timestampMs: Long, sampleRate: Int, channels: Int): DetectedBeat? {
        val n = 512
        if (pcm.size < n * channels) return null
        val fft = DoubleFFT_1D(n.toLong())
        val data = DoubleArray(n * 2)
        for (i in 0 until n) {
            var sum = 0.0
            for (c in 0 until channels) sum += pcm[i * channels + c]
            data[i] = sum / channels
        }
        fft.realForward(data)
        
        var guitarEnergy = 0.0
        val lowBin = (250 * n / sampleRate).coerceAtLeast(1)
        val highBin = (3500 * n / sampleRate).coerceAtMost(n / 2)
        
        for (i in lowBin..highBin) {
            val re = data[2 * i]
            val im = data[2 * i + 1]
            guitarEnergy += sqrt(re * re + im * im)
        }
        
        val avgEnergy = guitarEnergy / (highBin - lowBin + 1)
        val onsetRatio = if (lastGuitarEnergy > 0) avgEnergy / lastGuitarEnergy else 2.0
        val isOnset = onsetRatio > 1.6 && avgEnergy > 15.0
        lastGuitarEnergy = avgEnergy
        
        return if (isOnset) {
            val intensity = ((onsetRatio - 1.6) / 2.0 * 0.5 + 0.5).coerceIn(0.5, 1.0).toFloat()
            val duration = (150 + (avgEnergy * 2)).toInt().coerceAtMost(450)
            DetectedBeat(timestampMs, intensity, duration, 2)
        } else null
    }
}
