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
5.  **LLM Settings**: Configuration for the Large Language Model integration (API keys, model selection, prompt parameters).

## Components

### MainActivity
- **Role:** Main entry point and UI container.
- **Configuration:** Locked to `sensorPortrait` in `AndroidManifest.xml`.
- **UI Layout:** Uses `Scaffold` with a `TopAppBar` and a scrollable `Column`.

### Detection.kt
- Handles gyroscope and sensor data processing to detect PTSD-related symptoms (e.g., night terrors, restlessness).
- Implements FFT analysis using `JTransforms`.

### Haptic.kt
- Manages `VibratorManager` and `VibrationEffect` to provide tactile feedback.

### Alarm.kt
- Manages alarm scheduling and triggers based on detection events.

### Logger.kt
- Handles internal application logging for debugging and user history.

### LLM_Manager.kt
- Bridges to `LLMEdgeManager` from the `llmedge` library for local text generation and audio transcription.

## Development Guidelines
- Follow Material 3 design principles.
- Use `enableEdgeToEdge()` for modern Android UI.
- All screen-specific logic should be decoupled from `MainActivity` where possible using Composable functions for each section.
