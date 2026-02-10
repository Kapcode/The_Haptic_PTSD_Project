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
    - **State Management**: Uses Compose `mutableStateOf` with delegates for real-time reactivity.
    - **Device Assignments**: Routings are persisted as JSON strings using `kotlinx.serialization`.

## Core Hardware Integration

### Acoustic Squeeze Detection (`SqueezeDetector.kt`)
- **Mechanism**: On-device sonar. Emits a 20kHz (inaudible) sine wave via `AudioTrack` and monitors the environment via `AudioRecord`.
- **Processing**: Real-time FFT analysis using `JTransforms`.
- **Architecture**: Managed via `SqueezeManager` singleton to coordinate between the UI and Foreground Service.

### Haptic Feedback Engine (`HapticManager.kt`)
- **Architecture**: Singleton `object` coordinating feedback across Phone and External Controllers.
- **Multi-Assignment**: Supports routing multiple `BeatProfile` transients to any combination of the 6 available haptic points.
- **Live Visualizer State**: Tracks independent colors and intensities for each motor based on the most recent trigger.
- **Wobble Logic**: Strictly event-driven animations that trigger only during physical haptic pulses.

## Bilateral Beat Player & Stimulation

### Audio Analysis & Beat Detection
- **Mechanism**: Two-pass adaptive transient detection.
- **Sync Logic**: Uses `currentPos - SettingsManager.hapticSyncOffsetMs` to allow precision alignment (±2000ms).
- **Completion Logic**: Automatically resets playback state and UI controls upon track finish via `OnCompletionListener`.
- **Advanced Controls**: Implemented Repeat (One/All), Playback Speed, and Volume Boost. State is managed in `SettingsManager` and applied via `syncPlaybackSettings()`.
- **Track Switching**: `updateSelectedTrack` now stops previous playback to ensure a clean state transition.

### Visual Feedback & UI Components
- **Dynamic Theming**: Real-time primary color transitions based on the active `BeatProfile`.
- **Seekbar Ticks**: Dual-layer tick system showing upcoming haptic events with a "pre-cue shadow" for enhanced anticipation.
- **Settings Drawer**: All configuration (Scaling, Gain, Snapping) is centralized in a `ModalNavigationDrawer`.

## Recent Updates - Advanced Playback Controls

- **Feature:** Implemented a three-state repeat button: Off, Repeat All, and Repeat One.
- **Feature:** Added playback speed control (0.5x to 2.0x) and a 1.5x volume boost.
- **Feature:** Implemented `nextTrack` and `previousTrack` functionality.
- **Fix:** Corrected an issue where changing tracks did not stop the previous audio, leading to overlapping playback and inconsistent UI updates. `updateSelectedTrack` now calls `stopPlayback()` to ensure a clean state.
- **Fix:** Fixed a build error related to a type mismatch (`Int` vs. `Long`) in the `seekTo` call within the `MediaPlayer.onCompletion` listener.

## Future Features & Ideas

### Multi-device Sync
- **Goal**: Synchronizing haptic output across multiple phones or controllers for unified bilateral stimulation.

## Current Known Issues / Notes
- **Foreground Service**: Requires `FOREGROUND_SERVICE_SPECIAL_USE` and `POST_NOTIFICATIONS` permissions.
- **WakeLock**: Prevents system sleep during active therapeutic sessions.
