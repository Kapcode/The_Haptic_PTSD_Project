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
    - **State Management**: Uses Compose `mutableStateOf` with delegates for real-time reactivity.
    - **Device Assignments**: Routings are persisted as JSON strings using `kotlinx.serialization`.

## Core Hardware Integration

### Acoustic Squeeze Detection (`SqueezeDetector.kt`)
- **Mechanism**: On-device sonar. Emits a 20kHz (inaudible) sine wave via `AudioTrack` and monitors the environment via `AudioRecord`.
- **Processing**: Real-time FFT analysis using `JTransforms`.
- **Architecture**: Managed via `SqueezeManager` singleton to coordinate between the UI and Foreground Service.

### Haptic Feedback Engine (`HapticManager.kt`)
- **Architecture**: Singleton `object` coordinating feedback across Phone and External Controllers.
- **Application Haptics**: Dedicated tactile signatures for UI interactions (snapping, toggling, clicking) defined in `ApplicationHapticEffects.kt`.
- **Multi-Assignment**: Supports routing multiple `BeatProfile` transients to any combination of the 6 available haptic points.
- **Live Visualizer State**: Tracks independent colors and intensities for each motor based on the most recent trigger.

## UI & Accessibility

### Dynamic Typography
- **Customization**: Independent sliders for Heading, Regular, Card Title, Button, and Notation font sizes.
- **Mapping**: Settings are mapped directly to Material 3 typography styles (`titleLarge`, `bodyMedium`, `labelLarge`, etc.).
- **Safety**: Minimum font size bounds are enforced in `SettingsManager` to prevent unreadable UI states. The "Hold to Reset" button uses a fixed size for emergency recovery.

### Settings & Experimental Features
- **Organization**: Centralized settings in a `ModalNavigationDrawer`.
- **Sandboxing**: Development-stage features (like Squeeze Detection) are hidden unless the "Experimental Features" switch is enabled in the drawer.
- **ViewModels**: Features like Experimental Settings are managed via dedicated ViewModels to decouple UI state from global managers.

## Bilateral Beat Player & Stimulation

### Audio Analysis & Beat Detection
- **Mechanism**: Two-pass adaptive transient detection.
- **Sync Logic**: Uses `currentPos - SettingsManager.hapticSyncOffsetMs` to allow precision alignment (±2000ms).
- **Advanced Controls**: Implemented Repeat (One/All), Playback Speed, and Volume Boost.

## Current Known Issues / Notes
- **Foreground Service**: Requires `FOREGROUND_SERVICE_SPECIAL_USE` and `POST_NOTIFICATIONS` permissions.
- **WakeLock**: Prevents system sleep during active therapeutic sessions.
