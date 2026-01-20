# Technical Project Notes - The Haptic PTSD Project

This document contains technical details, architectural decisions, and development notes for contributors.

## Project Structure & Architecture

- **Minimum SDK:** 30 (Required by `llmedge` library).
- **Target SDK:** 36.
- **UI Framework:** Jetpack Compose with Material 3.
- **Language:** Kotlin.
- **Navigation:** Single-activity architecture (`MainActivity`).

## Key Dependencies

- **LLM & STT (Local AI):** `llmedge-release.aar` (Local AAR)
    - Provides native JNI wrappers for llama.cpp (GGUF inference) and whisper.cpp (STT).
    - Requires `androidx.documentfile` and `com.google.code.gson`.
- **Signal Processing:** `com.github.wendykierp:JTransforms`
    - Used for FFT analysis of sensor data to detect specific tremor frequencies (4Hz–12Hz).

## Main Screen Sections

The `MainActivity` features a vertically scrollable layout divided into several functional modules:

1.  **Detection Section**: Controls and status for motion/gyroscope sensing logic.
2.  **Haptics Section**: Configuration for the vibration motor, including intensity and patterns.
3.  **Alarm Settings**: Configuration for trigger-based alerts or wake-up functionality.
4.  **Logging Settings**: Controls for the internal logger, including verbosity and storage.
5.  **LLM Settings**: Configuration for the Large Language Model integration.

## Planned User-Facing Modes

- **Squeeze Heartbeat**: Uses acoustic squeeze detection (sub-audible frequencies via speaker/mic) to trigger a heartbeat haptic pulse.
- **Sleep Assistance**: Rhythmic haptic patterns to aid sleep onset.
- **Grounding Mode**: Rhythmic patterns for anxiety management.

## Components & Technical Implementations

### Squeeze Detection
- **Mechanism**: Emitting a low-frequency (sub-audible) tone from the speaker and monitoring changes in the microphone input to detect pressure/squeeze on the device body.

### Detection.kt
- Handles gyroscope and sensor data processing to detect PTSD-related symptoms.
- Implements FFT analysis using `JTransforms`.

### Haptic.kt
- Manages `VibratorManager` and `VibrationEffect`.

### LLM_Manager.kt
- Bridges to `LLMEdgeManager` for local text and audio processing.

## Development Guidelines
- Follow Material 3 design principles.
- Use `enableEdgeToEdge()` for modern Android UI.
- All screen-specific logic should be decoupled from `MainActivity`.
