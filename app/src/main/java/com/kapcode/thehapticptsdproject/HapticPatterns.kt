package com.kapcode.thehapticptsdproject

data class VibrationStep(
    val durationMs: Long,
    val amplitude: Int, // 0-255
    val delayMs: Long = 0
)

data class HapticPattern(
    val steps: List<VibrationStep>,
    val name: String = "Custom Pattern"
)

object HapticPatterns {
    val Heartbeat = HapticPattern(
        name = "Heartbeat",
        steps = listOf(
            VibrationStep(durationMs = 50, amplitude = 255),
            VibrationStep(durationMs = 60, amplitude = 180, delayMs = 80)
        )
    )

    val TestPulse = HapticPattern(
        name = "TestPulse",
        steps = listOf(
            VibrationStep(durationMs = 100, amplitude = 200)
        )
    )
    // Add more patterns here in the future
}
