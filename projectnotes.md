# Technical Project Notes - The Haptic PTSD Project

This document contains technical details, architectural decisions, and development notes for contributors.

## Project Structure & Architecture

- **Minimum SDK:** 24.
- **Target SDK:** 36.
- **UI Framework:** Jetpack Compose with Material 3.
- **Language:** Kotlin / Coroutines.
- **Architecture:** MVVM with ViewModels (`BeatPlayerViewModel`, `ModesViewModel`, etc.)
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
- **Live Visualizer State**: Tracks real-time intensity for four virtual motors (Phones L/R, Controllers L/R).
- **Decay Logic**: Background loop reduces motor intensity over time to create a smooth "glow" effect.
- **Adjustable Timing**: Supports user-defined "lead-in" and "lead-out" periods for both physical vibration and visual feedback.
- **Session Management**: Timer-based sessions with auto-stop and "reset-on-trigger" extension logic.
- **Testing**: `testPulseSequence()` provides a 3-beat burst to allow immediate tuning of BPM and intensity.

## Bilateral Beat Player & Stimulation

### Audio Analysis & Beat Detection
- **Mechanism**: Two-pass analysis system using adaptive transient detection.
- **Pass 1**: Scans audio to identify potential beat candidates and determine a dynamic energy threshold.
- **Pass 2**: Filters candidates based on the threshold to finalize rhythmic timestamps.
- **Optimization**: Intelligent downsampling (analyzing 1 in 4 samples) significantly reduces CPU load during profile generation.
- **Background Processing**: Batch analysis handled by `AnalysisService.kt` with persistent progress notifications.

### Intelligent Auto-Selection & Persistence
- **Auto-Selection**: The `BeatPlayerViewModel` automatically selects a track on startup. It prioritizes analyzed files (choosing the shortest for convenience) and remembers the last played track as a fallback.
- **Persistence**: Last played URI and Name are persisted in `SettingsManager`.

### File Tree Selection UI
- **Implementation**: Lazy-loading folder/file browser using SAF (Storage Access Framework) URIs.
- **Auto-loading**: Automatically scans for and loads existing haptic profiles (.json) when a track or profile type is selected.

### Notification Controls
- **Implementation**: Media controls (play, pause, stop, skip) are implemented in the `HapticService` notification.
- **Synchronization**: The notification controls are synchronized with the in-app player via `BeatDetector`.

## Future Features & Ideas

### Picture-in-Picture (PiP) Haptic Visualizer
- **Goal**: Floating visual confirmation of haptic activity while using other apps.
- **Multi-device Support**: Design for visualizing output across synchronized phones/controllers.

## Software Architecture

### Logging System (`Logger.kt`)
- **Persistence**: Persistent "Log to Logcat" preference saved in `SettingsManager`.
- **UI Features**: Filterable log history with Logcat mirroring for advanced debugging.

### Mode Management (`UserFacingModes.kt`)
- **Lifecycle**: Therapeutic modes (e.g., Active Heartbeat) are intentionally reset on app launch for safety.

### Persistence (`SettingsManager.kt`)
- **Scope**: Hardware preferences (Intensity, BPM, Timing, Thresholds) and last played media metadata are persisted.
- **Exclusion**: Active modes are NOT persisted.

## Current Known Issues / Notes
- **Foreground Service**: Requires `FOREGROUND_SERVICE_SPECIAL_USE` for analysis and microphone permissions for squeeze detection.
- **WakeLock**: Prevents system sleep during active therapeutic sessions.
