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
    - **State Management**: Refactored to use Compose `mutableStateOf` with delegates for real-time reactivity across the UI.
    - **Methods**: Includes centralized `save()` and `resetToDefaults()` methods for predictable configuration management.

## Core Hardware Integration

### Acoustic Squeeze Detection (`SqueezeDetector.kt`)
- **Mechanism**: On-device sonar. Emits a 20kHz (inaudible) sine wave via `AudioTrack` and monitors the environment via `AudioRecord`.
- **Processing**: Real-time FFT analysis using `JTransforms`.
- **Concurrency**: `AtomicBoolean` for running state and `AtomicBoolean` for signaling recalibration to avoid race conditions.
- **Calibration**: Dynamic baseline calculation + user-adjustable sensitivity threshold (5% - 95% with 5% steps).
- **Safe Shutdown**: Hardware `stop()` calls precede coroutine `cancelAndJoin()` to prevent `SIGABRT` native crashes.
- **Visibility**: Hidden behind the "Experimental Features" switch in Settings to prevent accidental calibration changes during normal use.

### Motion Detection (`MainActivity.kt` & `HapticService.kt`)
- **Sensor**: `Sensor.TYPE_LINEAR_ACCELERATION`.
- **Tuning**: Optimized for sudden "wrist snaps" or "flaps" by stripping out gravity components.
- **Sensitivity**: User-adjustable mapping where internal thresholds range from high (gentle) to low (deliberate).

### Haptic Feedback Engine (`HapticManager.kt`)
- **Pattern**: "Lub-dub" heartbeat simulation.
- **Architecture**: Singleton `object` to ensure unified state between the UI and Background Service.
- **Live Visualizer State**: Tracks real-time intensity for virtual motors (Phones L/R, Controllers L/R).
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
- **Background Processing**: `AnalysisService.kt` uses a `Channel`-based task processor to handle queued analysis tasks efficiently, moving away from sequential looping.

### Visual Feedback & Visualizer Logic
- **Layers**: Supports multiple overlapping visualizer layers (Vertical Bars, Channel Intensity, and Waveform) that can be toggled independently.
- **Threshold Lines**: Horizontal lines in the vertical bars visualizer signify the exact haptic trigger points for each profile range (0.4 for Amplitude, 0.5 for others).
- **Profile Icons**: Icons at the base of the visualizer dim when their corresponding frequency range is triggered.
- **Active Feedback (Wobble)**: The icon for the currently selected profile performs a rapid rotation/scale animation ("wobble") whenever a haptic pulse is triggered, providing direct visual confirmation of active therapeutic stimulation.
- **Haptic Sync**: Icon dimming is synchronized with both raw audio thresholds and live haptic events to ensure visual continuity even if audio analysis is slightly out of sync.

### Intelligent Auto-Selection & Persistence
- **Auto-Selection**: The `BeatPlayerViewModel` automatically selects a track on startup. It prioritizes analyzed files (choosing the shortest for convenience) and remembers the last played track as a fallback.
- **Missing Profiles**: Automatically queues analysis if a user selects a track/profile combination that hasn't been generated yet.
- **Persistence**: Last played URI and Name are persisted in `SettingsManager`.

### File Tree Selection UI
- **Implementation**: Lazy-loading folder/file browser using SAF (Storage Access Framework) URIs.
- **Permissions**: Improved folder permission handling in `MediaFoldersViewModel`.
- **Auto-loading**: Automatically scans for and loads existing haptic profiles (.json) when a track or profile type is selected.

### Notification Controls
- **Implementation**: Media controls (play, pause, stop, skip) are implemented in the `HapticService` notification.
- **Synchronization**: The notification controls are synchronized with the in-app player via `BeatDetector`.

## UI/UX Design

### Responsive Controls
- **Slider Snapping**: Configurable snapping increments for intensity, volume, and sensitivity controls to ensure precise user adjustments.
- **Smoothing**: All visual feedback uses non-bouncy, low-stiffness spring animations for a calming effect.

## Recent Updates - Visualizer & Settings Overhaul

- **Feature:** Added extensive visualizer customization options in Settings.
    - Sliders now available for icon trigger thresholds (Amplitude, Bass, Drum, Guitar).
    - Added an option to invert icon alpha (bright on trigger).
    - Visualizer now displays a secondary (red) line to indicate the icon trigger threshold.
- **Feature:** Overhauled the "Slider Snapping" section in Settings.
    - Grouped snapping options into "Whole Numbers" and "Decimals" for clarity.
    - Added snapping controls for all new sliders.
- **UI/UX:** All sliders throughout the app now display a tick mark indicating their default value.
- **Fix:** Resolved a bug where the Amplitude icon would not trigger correctly when the visualizer bar passed its threshold.
- **Fix:** Addressed animation glitches and inconsistencies that occurred on pausing and resuming the media player.
- **Fix:** Improved responsiveness of all visualizer animations by adjusting animation stiffness.
- **Refactor:** Clarified descriptions for settings to improve user understanding.

## Future Features & Ideas

### Multi-device Support
- **Goal**: Synchronizing haptic output across multiple phones or controllers for unified bilateral stimulation.

## Current Known Issues / Notes
- **Foreground Service**: Requires `FOREGROUND_SERVICE_SPECIAL_USE` for analysis and microphone permissions for squeeze detection.
- **WakeLock**: Prevents system sleep during active therapeutic sessions.
