# The Haptic PTSD Project

The Haptic PTSD Project is an innovative Android application designed to provide immediate, non-invasive support for individuals managing PTSD and sleep-related distress. By combining tactile grounding techniques with advanced on-device sensing and color-coded visual feedback, the app offers a digital "anchor" during moments of anxiety or restlessness.

## Core Purpose

The application aims to provide physiological regulation through:
- **Active Heartbeat Mode:** Immediate grounding triggered by either a physical squeeze or a sharp "wrist snap" movement.
- **Bilateral Beat Player:** Immersive, synchronized haptics that follow the rhythm of your own audio tracks, using multiple analysis profiles (Bass, Drum, Guitar, Amplitude).
- **Background Operation:** Designed to work while the app is out of focus or the screen is off, providing continuous support as you drift off to sleep.
- **Privacy-Centric Monitoring:** Continuous sensing of triggers using local, private analysis that never sends data to the cloud.

## Key Features

### Active Heartbeat (Squeeze & Snap)
A primary therapeutic feature where the user activates a comforting, rhythmic "heartbeat" vibration by squeezing their phone or using a sharp wrist movement. This mimics the calming effect of human touch or pulse-syncing, helping to break cycles of panic or distress even when lying in bed.

### Bilateral Beat Player
Analyze any audio file from your library to generate a custom haptic profile. The app uses advanced signal processing to "feel" the music, providing synchronized tactile feedback that matches the intensity and duration of detected transients.
- **Advanced Controls**: Full playback control including Repeat (One/All), Next/Previous track, Playback Speed (0.5x to 2.0x), and a 1.5x Volume Boost.
- **Live Profile Switching**: Switch between Bass, Drum, Guitar, and Amplitude profiles seamlessly during playback. The haptics update instantly to match the new profile.

### Accessibility & Tactile Feedback
The app is designed to be inclusive and responsive to user needs:
- **Customizable Typography**: Adjust Heading, Regular, Title, Button, and Notation font sizes independently via a dedicated Accessibility card. Minimum bounds are enforced to ensure legibility.
- **Application Haptic Signatures**: Every interaction—snapping a slider, toggling a switch, or clicking a button—now has a distinct tactile signature, providing immediate physical confirmation of your actions.
- **Emergency Recovery**: The "Hold to Reset" button maintains a fixed, legible size regardless of other font adjustments, ensuring you can always restore your environment.

### Multi-Layer Audio Visualizer
A high-resolution, 32-band frequency visualizer available in-app and via persistent notification. It supports independent, overlapping layers that can be toggled in settings.

### Privacy First
Built for the most sensitive contexts, all detection and analysis happen locally on the device. No audio or sensor data is recorded permanently or sent to external servers, ensuring total user confidentiality.

---

## Tech Stack
- **UI:** Jetpack Compose (Material 3) with real-time dynamic theming.
- **Logic:** Kotlin / Coroutines / Channel-based Task Queueing.
- **Sensing:** Acoustic Pressure Detection (sonar-based) & Linear Acceleration.
- **Background:** Android Foreground Service with WakeLock and persistent status notifications.
- **Signal Processing:** JTransforms (FFT) / Adaptive Transient Detection.
- **Persistence**: JSON-based haptic profiles and reactive SharedPreferences.

## Requirements
- Android 11 (API 30) or higher.
- Physical device required for haptic and sensor functionality.
