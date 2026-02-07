# The Haptic PTSD Project

The Haptic PTSD Project is an innovative Android application designed to provide immediate, non-invasive support for individuals managing PTSD and sleep-related distress. By combining tactile grounding techniques with advanced on-device sensing, the app offers a digital "anchor" during moments of anxiety or restlessness.

## Core Purpose

The application aims to provide physiological regulation through:
- **Active Heartbeat Mode:** Immediate grounding triggered by either a physical squeeze or a sharp "wrist snap" movement.
- **Bilateral Beat Player:** Immersive, synchronized haptics that follow the rhythm of your own audio tracks, using multiple analysis profiles (Bass, Guitar, Amplitude).
- **Background Operation:** Designed to work while the app is out of focus or the screen is off, providing continuous support as you drift off to sleep.
- **Privacy-Centric Monitoring:** Continuous sensing of triggers using local, private analysis that never sends data to the cloud.

## Key Features

### Active Heartbeat (Squeeze & Snap)
A primary therapeutic feature where the user activates a comforting, rhythmic "heartbeat" vibration by squeezing their phone or using a sharp wrist movement. This mimics the calming effect of human touch or pulse-syncing, helping to break cycles of panic or distress even when lying in bed.

### Bilateral Beat Player
Analyze any audio file from your library to generate a custom haptic profile. The app uses advanced signal processing to "feel" the music, providing synchronized tactile feedback that matches the intensity and duration of detected transients. Supports automatic caching for instant re-play.

### Intelligent Sleep Assistance (Planned)
Specialized, rhythmic haptic sessions designed to assist in falling asleep and maintaining sleep quality. The app is evolving to detect signs of tremors or night terrors and respond with gentle tactile interventions.

### Persistent Customization & Calibration
The app includes precision calibration tools for both acoustic pressure detection and motion sensitivity. All your preferences—intensity, BPM, and detection thresholds—are automatically saved and remembered.

### Privacy First
Built for the most sensitive contexts, all detection and analysis happen locally on the device. No audio or sensor data is recorded permanently or sent to external servers, ensuring total user confidentiality.

## Information for Treatment Experts
This project explores the intersection of **Haptic Grounding Therapy** and **Mobile Biosensing**. By providing a consistent, rhythmic tactile stimulus, the app targets the "window of tolerance," aiming to assist users in returning from hyper-arousal to a regulated state without the need for external intervention.

---

## Tech Stack
- **UI:** Jetpack Compose (Material 3)
- **Logic:** Kotlin / Coroutines
- **Sensing:** Acoustic Pressure Detection (sonar-based) & Linear Acceleration (gravity-independent motion)
- **Background:** Android Foreground Service with WakeLock
- **Signal Processing:** JTransforms (FFT)
- **Persistence**: JSON-based haptic profiles

## Requirements
- Android 11 (API 30) or higher.
- Physical device required for haptic and sensor functionality.

## Future Exploration
- Bilateral Stimulation: Support for dual-device connectivity via Bluetooth to provide alternating pulses on each arm.

## Ideas
- **Picture-in-Picture (PiP) Haptic Visualizer:** A real-time visualizer that shows the current intensity of the haptic engine. This would provide clear feedback on when the app is generating vibrations. It should support multiple devices, each with 1-2 motors, to give a comprehensive view of the haptic output.
