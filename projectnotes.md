# Technical Project Notes - The Haptic PTSD Project

This document contains technical details, architectural decisions, and development notes for contributors.

## Project Structure & Architecture

- **Minimum SDK:** 24.
- **Target SDK:** 36.
- **UI Framework:** Jetpack Compose with Material 3.
- **Language:** Kotlin / Coroutines.
- **Architecture:** MVVM with ViewModels (`BeatPlayerViewModel`, `ModesViewModel`, `ExperimentalSettingsViewModel`, etc.)
- **Background Logic:** Android Foreground Service (`HapticService.kt`) with `PARTIAL_WAKE_LOCK`.
- **Persistence:** `SharedPreferences` managed via `SettingsManager.kt`. 
    - **State Management**: Uses specialized Compose states (`mutableFloatStateOf`,
      `mutableIntStateOf`) to avoid primitive boxing and improve performance.
    - **Device Assignments**: Routings are persisted as JSON strings using `kotlinx.serialization`.
    - **Version Catalog**: Project dependencies are managed via `libs.versions.toml`.

## Core Hardware Integration

### Acoustic Squeeze Detection (`SqueezeDetector.kt`)
- **Mechanism**: On-device sonar. Emits a 20kHz (inaudible) sine wave via `AudioTrack` and monitors the environment via `AudioRecord`.
- **Processing**: Real-time FFT analysis using `JTransforms`.
- **Architecture**: Managed via `SqueezeManager` singleton to coordinate between the UI and Foreground Service.

### Haptic Feedback Engine (`HapticManager.kt`)
- **Architecture**: Singleton `object` coordinating feedback across Phone and External Controllers.
- **Haptic Pattern System**: Extensible haptic sequences defined in `HapticPatterns.kt`. Uses
  `VibrationStep` to define duration, amplitude, and delay, allowing for complex, multi-stage
  vibrations like the "Heartbeat".
- **Application Haptics**: Dedicated tactile signatures for UI interactions (snapping, toggling, clicking) defined in `ApplicationHapticEffects.kt`.
- **Multi-Assignment**: Supports routing multiple `BeatProfile` transients to any combination of the 6 available haptic points.
- **Live Visualizer State**: Tracks independent colors and intensities for each motor based on the most recent trigger.

## UI & Accessibility

### Animation Framework (`Animations.kt`)

- **Centralization**: All common animation specs (e.g., `bouncyAnimationSpec`) and reusable
  animation composables (e.g., `animateWobble`) are centralized.
- **Dynamic Feedback**: UI components use these centralized definitions to provide consistent visual
  feedback for haptic events and interactions.

### Component Design

- **Collapsible Cards**: The `SectionCard` component (`HelperComposables.kt`) now supports optional
  collapsibility. Primary therapeutic cards are expanded by default, while settings are collapsed to
  reduce visual clutter.
- **Animated Reordering**: The Bilateral Beat Player uses `LazyColumn` with `animateItemPlacement`
  to provide a fluid, "fidget-like" experience when reordering its control components.

### Dynamic Typography
- **Customization**: Independent sliders for Heading, Regular, Card Title, Button, and Notation font sizes.
- **Mapping**: Settings are mapped directly to Material 3 typography styles (`titleLarge`, `bodyMedium`, `labelLarge`, etc.).
- **Safety**: Minimum font size bounds are enforced in `SettingsManager` to prevent unreadable UI
  states.

## Bilateral Beat Player & Stimulation

### Audio Analysis & Beat Detection
- **Mechanism**: Two-pass adaptive transient detection.
- **Sync Logic**: Uses `currentPos - SettingsManager.hapticSyncOffsetMs` to allow precision alignment (±2000ms).
- **Advanced Controls**: Implemented Repeat (One/All), Playback Speed, and Volume Boost.

## Current Known Issues / Notes
- **Foreground Service**: Requires `FOREGROUND_SERVICE_SPECIAL_USE` and `POST_NOTIFICATIONS` permissions.
- **WakeLock**: Prevents system sleep during active therapeutic sessions.
- **FIXED**: BBPlayer card controls would disappear after a "Reset to Defaults" action.
    - **Cause**: `BeatDetector.resetPlayer()` was clearing the `selectedFileUri`, which is required
      for the controls to be visible.
    - **Solution**: Modified `resetPlayer()` to preserve the `selectedFileUri` and
      `selectedFileName` while resetting all other playback-related states.
- **FIXED**: "Start Session" button would occasionally fail after pausing a BBPlayer track.
    - **Cause**: The `HapticManager` logic was being blocked by the `BeatDetector` remaining in a
      paused state.
    - **Solution**: Added an explicit `BeatDetector.stopPlayback()` call at the beginning of
      `HapticManager.startHeartbeatSession()` to ensure a clean state for the new session.
