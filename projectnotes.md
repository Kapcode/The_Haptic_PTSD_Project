# Technical Project Notes - The Haptic PTSD Project

This document contains technical details, architectural decisions, and development notes for contributors.

## Project Structure & Architecture

- **Minimum SDK:** 30 (Required by `llmedge` library).
- **Target SDK:** 36.
- **UI Framework:** Jetpack Compose with Material 3.
- **Language:** Kotlin / Coroutines.
- **Navigation:** Single-activity architecture (`MainActivity`).

## Core Hardware Integration

### Acoustic Squeeze Detection (`SqueezeDetector.kt`)
- **Mechanism**: On-device sonar. Emits a 20kHz (inaudible) sine wave via `AudioTrack` and monitors the environment via `AudioRecord`.
- **Logic**: Physical pressure on the device disrupts the acoustic path between speaker and microphone.
- **Processing**: Real-time FFT analysis using `JTransforms`. 
- **Calibration**: Dynamic baseline calculation + user-adjustable sensitivity threshold (1% - 99%).
- **Shutdown Sequence**: Atomic state management and explicit `stop()` calls to native hardware *before* resource release to prevent `SIGABRT` race conditions.

### Haptic Feedback Engine (`HapticManager.kt`)
- **Core Pattern**: "Lub-dub" heartbeat simulation.
- **Session Management**: Timer-based sessions (default 2 mins) with auto-stop and extension logic.
- **Parameters**: Real-time adjustment of intensity (0-100%) and frequency (30-200 BPM).
- **Concurrency**: Managed via Coroutines on `Dispatchers.Default` to ensure UI smoothness during continuous vibration patterns.

## Software Architecture

### Logging System (`Logger.kt`)
- **Levels**: `DEBUG`, `INFO`, `ERROR`.
- **State**: Exposed via `StateFlow<List<LogEntry>>`.
- **UI Features**: Reverse chronological sorting, level filtering (Ordinal-based), and visual color coding.

### Mode Management (`UserFacingModes.kt`)
- **Type**: Multi-selection Set-based state.
- **Implementation**: `Sealed Class` for mode definitions, allowing for rich metadata (icons, descriptions) alongside logical IDs.

## Native & AI Dependencies
- **llmedge-release.aar**: Local AAR providing JNI bridges for Llama (GGUF) and Whisper (STT).
- **JTransforms**: Java-based FFT library for signal processing.

## Current Known Issues / Notes
- **AppOps Warning**: `attributionTag` warning in logcat is a non-breaking system notice regarding microphone usage.
- **Native Stability**: Audio hardware must be stopped and joined before releasing to avoid system-level crashes.
