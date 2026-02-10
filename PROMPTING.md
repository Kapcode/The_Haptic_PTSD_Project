# AI Prompting Guide for The Haptic PTSD Project

This guide provides tips and context for developers using AI assistants (like Gemini) to understand and modify this project.

## Project Overview

The core purpose of this app is to provide therapeutic haptic feedback based on user-initiated triggers (squeeze, shake) or in sync with audio playback.

- **Heartbeat Mode**: A rhythmic pulse for grounding.
- **Bilateral Beat Player**: Synchronizes haptic vibrations with beats detected in an audio file.

All background processing happens in `HapticService.kt`, which is a foreground service.

## Key Architectural Components

When making a request, providing context by referencing these key files can significantly improve the AI's accuracy.

- **`BeatDetector.kt`**: The singleton responsible for audio analysis (beat detection), `MediaPlayer` management, and playback state.
  - *Good for prompts like: "Modify the beat detection algorithm to be more sensitive to bass frequencies."*

- **`HapticManager.kt`**: The singleton that manages all haptic output. It controls vibration intensity, duration, and routing to different devices.
  - *Good for prompts like: "Change the 'heartbeat' effect to be a double pulse."*

- **`HapticService.kt`**: The Android `Service` that keeps the app alive in the background. It manages the wake lock, listens to sensors, and updates the persistent notification.
  - *Good for prompts like: "Add a new button to the media notification that does X."*

- **`BeatPlayerViewModel.kt`**: The MVVM ViewModel for the main player UI. It connects UI actions (like pressing 'play') to the `BeatDetector`.
  - *Good for prompts like: "Add a new slider to the player UI that controls master volume."*

- **`SettingsManager.kt`**: A singleton that manages all persistent settings using `SharedPreferences`.
  - *Good for prompts like: "Add a new setting to control the default playback speed."*

- **`SqueezeDetector.kt`**: Handles the sonar-based squeeze detection logic.
  - *Good for prompts like: "Adjust the frequency of the sonar sine wave."*

## Example Prompts

### Good (Specific and Contextual)
*   "In `BeatPlayerCard.kt`, I want to change the 'Repeat' button. Instead of a single toggle, make it cycle through three states: OFF, REPEAT_ALL, and REPEAT_ONE. Update the icon and tint accordingly. The state should be saved in `SettingsManager.kt`."
*   "In `BeatDetector.kt`, when a track finishes playing, I want it to automatically play the next track if `isRepeatAllEnabled` is true. You'll need to use the `onTrackFinished` callback in `BeatPlayerViewModel.kt` to handle this."
*   "Add a file-level KDoc comment to `BeatDetector.kt` explaining its role in the project."

### Bad (Vague or Ambiguous)
*   "Fix the player."
*   "The music isn't working."
*   "Make the haptics better."

By providing clear, file-specific context, you help the AI understand exactly where and how to apply the requested changes.
