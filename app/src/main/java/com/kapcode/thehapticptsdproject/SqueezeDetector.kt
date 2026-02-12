@file:Suppress("RedundantSuspendModifier")

package com.kapcode.thehapticptsdproject

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.jtransforms.fft.DoubleFFT_1D
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sin
import kotlin.math.sqrt

data class SqueezeDetectorState(
    val liveMagnitude: Double = 0.0,
    val baselineMagnitude: Double = 0.0,
    val squeezeThresholdPercent: Double = 0.50, // 50%
    val isCalibrating: Boolean = false
)

object SqueezeManager {
    private var onSqueezeAction: (() -> Unit)? = null

    val detector = SqueezeDetector {
        onSqueezeAction?.invoke()
    }

    fun setOnSqueezeListener(action: () -> Unit) {
        onSqueezeAction = action
    }
}

class SqueezeDetector(
    private val onSqueezeDetected: () -> Unit
) {
    private var isRunning = AtomicBoolean(false)
    private var shouldRecalibrate = AtomicBoolean(false)
    
    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var processingJob: Job? = null
    private var toneJob: Job? = null

    private val _state = MutableStateFlow(SqueezeDetectorState())
    val state = _state.asStateFlow()

    private val sampleRate = 44100
    private val frequency = 20000.0 // 20kHz sonar tone
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    companion object {
        private const val CALIBRATION_DURATION_MS = 1000L
        private const val TRIGGER_DELAY_MS = 200L
    }

    @SuppressLint("DefaultLocale")
    fun setSqueezeThreshold(percent: Double) {
        _state.value = _state.value.copy(squeezeThresholdPercent = percent)
        Logger.info("Squeeze threshold updated to ${String.format("%.0f", percent * 100)}%")
    }

    fun recalibrate() {
        if (isRunning.get()) {
            if (shouldRecalibrate.compareAndSet(false, true)) {
                Logger.info("Recalibration requested...")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning.getAndSet(true)) return
        Logger.info("SqueezeDetector starting sonar...")

        try {
            setupAudio()
            audioRecord?.startRecording()
            audioTrack?.play()
            
            startToneGeneration()
            startProcessingLoop()
            
            // Trigger initial calibration
            shouldRecalibrate.set(true)
        } catch (e: Exception) {
            Logger.error("ERROR starting detector: ${e.message}")
            stop()
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        Logger.info("Stopping SqueezeDetector hardware...")

        // 1. Unblock native calls by stopping hardware
        try {
            audioRecord?.stop()
            audioTrack?.stop()
        } catch (e: Exception) {
            // Ignore errors during stop
        }

        // 2. Wait for background loops to exit
        runBlocking {
            processingJob?.cancelAndJoin()
            toneJob?.cancelAndJoin()
        }

        // 3. Clean up resources
        try {
            audioRecord?.release()
            audioTrack?.release()
            audioRecord = null
            audioTrack = null
            _state.value = _state.value.copy(liveMagnitude = 0.0, baselineMagnitude = 0.0, isCalibrating = false)
            Logger.info("SqueezeDetector fully stopped.")
        } catch (e: Exception) {
            Logger.error("ERROR releasing audio: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupAudio() {
        audioTrack = AudioTrack.Builder()
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )
    }

    private fun startToneGeneration() {
        toneJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ShortArray(bufferSize)
            var angle = 0.0
            try {
                while (isRunning.get()) {
                    for (i in buffer.indices) {
                        val sinValue = sin(angle)
                        buffer[i] = (sinValue * Short.MAX_VALUE).toInt().toShort()
                        angle += 2 * Math.PI * frequency / sampleRate
                    }
                    val track = audioTrack ?: break
                    if (isRunning.get()) {
                        track.write(buffer, 0, buffer.size)
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Logger.error("Sonar tone error: ${e.message}")
                }
            }
        }
    }

    private fun startProcessingLoop() {
        processingJob = CoroutineScope(Dispatchers.IO).launch {
            val audioData = ShortArray(bufferSize)
            val fft = DoubleFFT_1D(bufferSize.toLong())
            val targetBin = (frequency * bufferSize / sampleRate).toInt()

            try {
                while (isRunning.get()) {
                    // Check if we need to recalibrate
                    if (shouldRecalibrate.get()) {
                        performCalibration(fft, audioData, targetBin)
                        shouldRecalibrate.set(false)
                    }

                    val record = audioRecord ?: break
                    val readSize = record.read(audioData, 0, bufferSize)
                    
                    if (readSize > 0 && isRunning.get()) {
                        val currentMagnitude = getMagnitudeAtBin(fft, audioData, targetBin)
                        _state.value = _state.value.copy(liveMagnitude = currentMagnitude)

                        val baseline = _state.value.baselineMagnitude
                        val threshold = _state.value.squeezeThresholdPercent
                        
                        if (baseline > 0 && currentMagnitude < (baseline * threshold)) {
                            onSqueezeDetected()
                            delay(TRIGGER_DELAY_MS)
                        }
                    } else if (readSize <= 0) {
                        break // Hardware stopped or error
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Logger.error("Detection loop error: ${e.message}")
                }
            } finally {
                _state.value = _state.value.copy(isCalibrating = false)
            }
        }
    }

    private suspend fun performCalibration(fft: DoubleFFT_1D, audioData: ShortArray, targetBin: Int) {
        Logger.info("Starting calibration baseline measurement...")
        _state.value = _state.value.copy(isCalibrating = true)
        
        val readings = mutableListOf<Double>()
        val endTime = System.currentTimeMillis() + CALIBRATION_DURATION_MS
        
        while (System.currentTimeMillis() < endTime && isRunning.get()) {
            val record = audioRecord ?: break
            val readSize = record.read(audioData, 0, bufferSize)
            if (readSize > 0) {
                val mag = getMagnitudeAtBin(fft, audioData, targetBin)
                readings.add(mag)
                _state.value = _state.value.copy(liveMagnitude = mag)
            }
        }
        
        if (readings.isNotEmpty()) {
            val avg = readings.average()
            _state.value = _state.value.copy(baselineMagnitude = avg, isCalibrating = false)
            Logger.info("Calibration complete. New baseline: ${avg.toInt()}")
        } else {
            Logger.error("Calibration failed: No signal detected.")
            _state.value = _state.value.copy(isCalibrating = false)
        }
    }

    private fun getMagnitudeAtBin(fft: DoubleFFT_1D, audioData: ShortArray, bin: Int): Double {
        val doubleData = DoubleArray(audioData.size)
        for (i in audioData.indices) {
            doubleData[i] = audioData[i].toDouble()
        }
        fft.realForward(doubleData)
        
        // JTransforms realForward layout for N even: [Re(0), Re(N/2), Re(1), Im(1), ...]
        val real: Double
        val imag: Double
        if (bin == 0) {
            real = doubleData[0]
            imag = 0.0
        } else {
            real = doubleData[2 * bin]
            imag = doubleData[2 * bin + 1]
        }
        return sqrt(real * real + imag * imag)
    }
}
