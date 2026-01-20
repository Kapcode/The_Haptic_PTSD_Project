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
import kotlin.math.sqrt

data class SqueezeDetectorState(
    val liveMagnitude: Double = 0.0,
    val baselineMagnitude: Double = 0.0,
    val squeezeThresholdPercent: Double = 0.50 // 50%
)

class SqueezeDetector(
    private val onSqueezeDetected: () -> Unit
) {
    private var isRunning = AtomicBoolean(false)
    private lateinit var audioRecord: AudioRecord
    private lateinit var audioTrack: AudioTrack
    private var processingJob: Job? = null
    private var toneJob: Job? = null

    private val _state = MutableStateFlow(SqueezeDetectorState())
    val state = _state.asStateFlow()

    private val sampleRate = 44100
    private val frequency = 20000.0 // 20kHz
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    companion object {
        private const val CALIBRATION_DURATION_MS = 1000L
        private const val TRIGGER_DELAY_MS = 200L
    }

    fun setSqueezeThreshold(percent: Double) {
        _state.value = _state.value.copy(squeezeThresholdPercent = percent)
        Logger.info("Squeeze threshold updated to ${percent * 100}%")
    }

    fun recalibrate() {
        if (isRunning.get()) {
            startProcessingLoop(forceRecalibrate = true)
        }
    }

    @SuppressLint("MissingPermission")
    fun start() {
        if (isRunning.getAndSet(true)) return
        Logger.info("SqueezeDetector starting...")

        try {
            setupAudio()
            audioRecord.startRecording()
            audioTrack.play()
            Logger.info("Audio hardware started.")
            startToneGeneration()
            startProcessingLoop(forceRecalibrate = true)
        } catch (e: Exception) {
            Logger.error("ERROR starting detector: ${e.message}")
            isRunning.set(false)
        }
    }

    fun stop() {
        if (!isRunning.getAndSet(false)) return
        Logger.info("Stopping SqueezeDetector...")

        // Stop the hardware first to unblock any read/write calls
        try {
            audioRecord.stop()
            audioTrack.stop()
        } catch (e: Exception) {
            Logger.error("ERROR stopping audio hardware: ${e.message}")
        }

        // Now, wait for the coroutines to finish cleanly
        runBlocking {
            try {
                processingJob?.join()
                toneJob?.join()
            } catch (e: Exception) {
                Logger.error("Error joining jobs: ${e.message}")
            }
        }

        // Finally, release the resources
        try {
            audioRecord.release()
            audioTrack.release()
            Logger.info("SqueezeDetector stopped successfully.")
        } catch (e: Exception) {
            Logger.error("ERROR releasing detector hardware: ${e.message}")
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

        audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
    }

    private fun startToneGeneration() {
        toneJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val buffer = ShortArray(bufferSize)
                var angle = 0.0
                while (isRunning.get()) {
                    for (i in buffer.indices) {
                        val sinValue = Math.sin(angle)
                        buffer[i] = (sinValue * Short.MAX_VALUE).toInt().toShort()
                        angle += 2 * Math.PI * frequency / sampleRate
                    }
                    audioTrack.write(buffer, 0, buffer.size)
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Logger.error("ERROR in tone generation: ${e.message}")
                }
            }
        }
    }

    private fun startProcessingLoop(forceRecalibrate: Boolean = false) {
        processingJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val audioData = ShortArray(bufferSize)
                val fft = DoubleFFT_1D(bufferSize.toLong())
                val targetBin = (frequency * bufferSize / sampleRate).toInt()

                if (forceRecalibrate) {
                    Logger.info("Calibrating...")
                    val calibrationReadings = mutableListOf<Double>()
                    val calibrationEndTime = System.currentTimeMillis() + CALIBRATION_DURATION_MS
                    while (isRunning.get() && System.currentTimeMillis() < calibrationEndTime) {
                        val readSize = audioRecord.read(audioData, 0, bufferSize)
                        if (readSize > 0) {
                            val magnitude = getMagnitudeAtBin(fft, audioData, targetBin)
                            _state.value = _state.value.copy(liveMagnitude = magnitude)
                            calibrationReadings.add(magnitude)
                        }
                    }
                    val newBaseline = if (calibrationReadings.isNotEmpty()) calibrationReadings.average() else 0.0
                    _state.value = _state.value.copy(baselineMagnitude = newBaseline)

                    if (newBaseline > 0) {
                        Logger.info("Calibration complete. Baseline: $newBaseline")
                    } else {
                        Logger.error("Calibration FAILED: No audio readings.")
                        stop()
                        return@launch
                    }
                }

                // Detection phase
                while (isRunning.get()) {
                    val readSize = audioRecord.read(audioData, 0, bufferSize)
                    if (readSize > 0) {
                        val currentMagnitude = getMagnitudeAtBin(fft, audioData, targetBin)
                        _state.value = _state.value.copy(liveMagnitude = currentMagnitude)

                        val baseline = _state.value.baselineMagnitude
                        val threshold = _state.value.squeezeThresholdPercent
                        if (baseline > 0 && currentMagnitude < (baseline * threshold)) {
                            onSqueezeDetected()
                            delay(TRIGGER_DELAY_MS)
                        }
                    } else {
                        // readSize is <= 0, which means the track has been stopped.
                        // Break the loop to allow the job to finish.
                        break
                    }
                }
            } catch (e: Exception) {
                if (e !is kotlinx.coroutines.CancellationException) {
                    Logger.error("ERROR in processing loop: ${e.message}")
                }
            }
        }
    }

    private fun getMagnitudeAtBin(fft: DoubleFFT_1D, audioData: ShortArray, bin: Int): Double {
        val doubleData = audioData.map { it.toDouble() }.toDoubleArray()
        fft.realForward(doubleData)
        val real = doubleData[2 * bin]
        val imag = doubleData[2 * bin + 1]
        return sqrt(real * real + imag * imag)
    }
}
