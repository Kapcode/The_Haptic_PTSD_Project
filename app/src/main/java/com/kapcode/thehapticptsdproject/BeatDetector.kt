package com.kapcode.thehapticptsdproject

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jtransforms.fft.DoubleFFT_1D
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sqrt

enum class BeatProfile {
    AMPLITUDE, // Simple peak detection
    DRUM,      // Focus on high-frequency transients (Kicks/Snares)
    BASS       // Focus on low-frequency energy
}

data class DetectedBeat(
    val timestampMs: Long,
    val intensity: Float,
    val durationMs: Int,
    val channel: Int // 0 for Left, 1 for Right, 2 for Both
)

class BeatDetector(private val context: Context) {

    suspend fun analyzeAudioUri(
        uri: Uri,
        profile: BeatProfile,
        onProgress: (Float) -> Unit
    ): List<DetectedBeat> = withContext(Dispatchers.Default) {
        val beats = mutableListOf<DetectedBeat>()
        val extractor = MediaExtractor()
        
        try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
                extractor.setDataSource(fd.fileDescriptor)
            } ?: throw Exception("Could not open file descriptor")

            val trackIndex = selectAudioTrack(extractor)
            if (trackIndex < 0) throw Exception("No audio track found")

            val format = extractor.getTrackFormat(trackIndex)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: throw Exception("No MIME type")
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val durationUs = format.getLong(MediaFormat.KEY_DURATION)

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            extractor.selectTrack(trackIndex)

            val info = MediaCodec.BufferInfo()
            var isExtractorDone = false
            var isDecoderDone = false

            // Analysis parameters
            val windowSizeMs = 20L
            val windowSizeSamples = (sampleRate * windowSizeMs / 1000).toInt()
            val pcmBuffer = ShortArray(windowSizeSamples * channelCount)
            
            var lastPeakTimeMs = -500L // Min gap between beats

            while (!isDecoderDone) {
                if (!isExtractorDone) {
                    val inputIndex = codec.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            isExtractorDone = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, 10000)
                if (outputIndex >= 0) {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)!!
                    val presentationTimeMs = info.presentationTimeUs / 1000
                    
                    onProgress(presentationTimeMs.toFloat() / (durationUs / 1000).toFloat())

                    // Convert to ShortArray for analysis
                    val shortsRead = info.size / 2
                    val currentPcm = ShortArray(shortsRead)
                    outputBuffer.asShortBuffer().get(currentPcm)
                    
                    processPcmWindow(currentPcm, presentationTimeMs, sampleRate, channelCount, profile, lastPeakTimeMs)?.let {
                        beats.add(it)
                        lastPeakTimeMs = it.timestampMs
                    }

                    codec.releaseOutputBuffer(outputIndex, false)
                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        isDecoderDone = true
                    }
                }
            }

            codec.stop()
            codec.release()
            extractor.release()

        } catch (e: Exception) {
            Logger.error("Beat detection error: ${e.message}")
        }

        return@withContext beats
    }

    private fun selectAudioTrack(extractor: MediaExtractor): Int {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) return i
        }
        return -1
    }

    private fun processPcmWindow(
        pcm: ShortArray,
        timestampMs: Long,
        sampleRate: Int,
        channels: Int,
        profile: BeatProfile,
        lastPeakTimeMs: Long
    ): DetectedBeat? {
        if (timestampMs - lastPeakTimeMs < 250) return null // Refractory period

        return when (profile) {
            BeatProfile.AMPLITUDE -> analyzePeak(pcm, timestampMs, channels)
            BeatProfile.BASS -> analyzeBassPeak(pcm, timestampMs, sampleRate, channels)
            BeatProfile.DRUM -> analyzeDrumPeak(pcm, timestampMs, sampleRate, channels)
        }
    }

    private fun analyzePeak(pcm: ShortArray, timestampMs: Long, channels: Int): DetectedBeat? {
        var maxAmp = 0f
        for (sample in pcm) {
            val amp = abs(sample.toInt()) / 32768f
            if (amp > maxAmp) maxAmp = amp
        }

        return if (maxAmp > 0.6f) { // Arbitrary threshold
            DetectedBeat(timestampMs, maxAmp, 100, 2)
        } else null
    }

    private fun analyzeBassPeak(pcm: ShortArray, timestampMs: Long, sampleRate: Int, channels: Int): DetectedBeat? {
        // FFT to look for energy in 20-150Hz
        val n = nextPowerOfTwo(pcm.size / channels)
        val fft = DoubleFFT_1D(n.toLong())
        val data = DoubleArray(n * 2)
        
        // Use mono mix for bass
        for (i in 0 until (pcm.size / channels).coerceAtMost(n)) {
            var sum = 0.0
            for (c in 0 until channels) sum += pcm[i * channels + c]
            data[i] = sum / channels
        }
        
        fft.realForward(data)
        
        var bassEnergy = 0.0
        val lowBin = (20 * n / sampleRate).coerceAtLeast(0)
        val highBin = (150 * n / sampleRate).coerceAtMost(n / 2)
        
        for (i in lowBin..highBin) {
            val re = data[2 * i]
            val im = data[2 * i + 1]
            bassEnergy += sqrt(re * re + im * im)
        }
        
        val normalizedEnergy = (bassEnergy / n).toFloat()
        return if (normalizedEnergy > 50f) { // Threshold needs tuning
            // Bass gets extra duration and intensity as requested
            DetectedBeat(timestampMs, (normalizedEnergy * 1.5f).coerceAtMost(1f), 300, 2)
        } else null
    }

    private fun analyzeDrumPeak(pcm: ShortArray, timestampMs: Long, sampleRate: Int, channels: Int): DetectedBeat? {
        // High frequency transient detection (Kick/Snare mix)
        // Simplified: look for rapid amplitude increase
        var maxAmp = 0f
        for (sample in pcm) {
            val amp = abs(sample.toInt()) / 32768f
            if (amp > maxAmp) maxAmp = amp
        }
        return if (maxAmp > 0.8f) DetectedBeat(timestampMs, maxAmp, 150, 2) else null
    }

    private fun nextPowerOfTwo(n: Int): Int {
        var v = n
        v--
        v = v or (v shr 1)
        v = v or (v shr 2)
        v = v or (v shr 4)
        v = v or (v shr 8)
        v = v or (v shr 16)
        v++
        return v
    }
}
