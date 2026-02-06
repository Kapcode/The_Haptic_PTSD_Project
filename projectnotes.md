# Technical Project Notes - The Haptic PTSD Project

This document contains technical details, architectural decisions, and development notes for contributors.

## Project Structure & Architecture

- **Minimum SDK:** 24.
- **Target SDK:** 36.
- **UI Framework:** Jetpack Compose with Material 3.
- **Language:** Kotlin / Coroutines.
- **Background Logic:** Android Foreground Service (`HapticService.kt`) with `PARTIAL_WAKE_LOCK`.
- **Persistence:** `SharedPreferences` managed via `SettingsManager.kt`.

## Core Hardware Integration

### Acoustic Squeeze Detection (`SqueezeDetector.kt`)
- **Mechanism**: On-device sonar. Emits a 20kHz (inaudible) sine wave via `AudioTrack` and monitors the environment via `AudioRecord`.
- **Processing**: Real-time FFT analysis using `JTransforms`.
- **Concurrency**: `AtomicBoolean` for running state and `AtomicBoolean` for signaling recalibration to avoid race conditions.
- **Calibration**: Dynamic baseline calculation + user-adjustable sensitivity threshold (5% - 95% with 5% steps).
- **Safe Shutdown**: Hardware `stop()` calls precede coroutine `cancelAndJoin()` to prevent `SIGABRT` native crashes.

### Motion Detection (`MainActivity.kt` & `HapticService.kt`)
- **Sensor**: `Sensor.TYPE_LINEAR_ACCELERATION`.
- **Tuning**: Optimized for sudden "wrist snaps" or "flaps" by stripping out gravity components.
- **Sensitivity**: User-adjustable mapping where internal thresholds range from high (gentle) to low (deliberate).

### Haptic Feedback Engine (`HapticManager.kt`)
- **Pattern**: "Lub-dub" heartbeat simulation.
- **Architecture**: Singleton `object` to ensure unified state between the UI and Background Service.
- **Session Management**: Timer-based sessions with auto-stop and "reset-on-trigger" extension logic.
- **Testing**: `testPulseSequence()` provides a 3-beat burst to allow immediate tuning of BPM and intensity.

## Bilateral Beat Player & Stimulation (Planned)

### Overview
A multi-device feature designed for Bilateral Stimulation (BLS) using audio-synchronized haptics across two phones connected via Bluetooth.

### Step 1: Audio Analysis & Beat Detection
- **Goal**: Analyze an audio file (e.g., MP3/WAV) to detect rhythmic peaks or transients.
- **Implementation**: Use FFT (via `JTransforms`) or amplitude thresholding on PCM data to identify "beats".
- **Separation**: Distinguish between Left and Right channel peaks for alternating stimulation.

### Step 2: Metadata Creation (.mdi files)
- **Format**: A custom `.mdi` (Motion Data Interface) file generated alongside the audio.
- **Data**: Stores timestamps and channel mapping (L/R) for each detected beat.
- **Purpose**: Low-latency lookup for haptic triggers during playback without re-analyzing the audio.

### Step 3: Bluetooth Coordination
- **Protocol**: Bluetooth Classic (SPP) or BLE for low-latency synchronization.
- **Logic**: 
    - **Primary Phone**: Plays the audio and handles its own arm's haptics (e.g., Right). Sends a small sync trigger over Bluetooth to the secondary phone.
    - **Secondary Phone**: Listens for Bluetooth triggers and fires its local haptic motor (e.g., Left).

### Step 4: Synchronized Playback
- **Mechanism**: Use a shared reference clock to ensure the audio and haptic pulses remain in phase over long sessions.

## Software Architecture

### Logging System (`Logger.kt`)
- **Levels**: `DEBUG`, `INFO`, `ERROR`.
- **Persistence**: Currently session-based (in-memory list).
- **UI Features**: Ordinal-based level filtering and reverse chronological sorting.

### Mode Management (`UserFacingModes.kt`)
- **Selection**: Set-based multi-selection.
- **Lifecycle**: Modes are reset to empty on fresh launch to ensure intentional activation of physiological interventions.

## Persistence (`SettingsManager.kt`)
- **Scope**: All hardware preferences (Intensity, BPM, Thresholds, Toggles) are persisted.
- **Exclusion**: Therapeutic Modes (Active Heartbeat, etc.) are NOT persisted for safety.

## Signal Processing Dependencies
- **JTransforms**: Java-based FFT library for signal processing.

## Current Known Issues / Notes
- **Foreground Service**: Requires `FOREGROUND_SERVICE_MICROPHONE` and `FOREGROUND_SERVICE_SPECIAL_USE` permissions on API 34+.
- **WakeLock**: Used to ensure sensor processing continues when the screen is dimmed or off during sleep monitoring.
