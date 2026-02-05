# The Haptic PTSD Project

The Haptic PTSD Project is an innovative Android application designed to provide immediate, non-invasive support for individuals managing PTSD and sleep-related distress. By combining tactile grounding techniques with advanced on-device sensing, the app offers a digital "anchor" during moments of anxiety or restlessness.

## Core Purpose

The application aims to provide physiological regulation through:
- **Active Heartbeat Mode:** Immediate grounding triggered by either a physical squeeze or a sharp "wrist snap" movement.
- **Background Operation:** Designed to work while the app is out of focus or the screen is off, providing continuous support as you drift off to sleep.
- **Rhythmic Regulation:** High-fidelity haptic patterns, such as a simulated "heartbeat," designed to synchronize with the user's autonomic nervous system to lower heart rate and promote calm.
- **Privacy-Centric Monitoring:** Continuous sensing of triggers using local, private analysis that never sends data to the cloud.

## Key Features

### Active Heartbeat (Squeeze & Snap)
A primary therapeutic feature where the user activates a comforting, rhythmic "heartbeat" vibration by squeezing their phone or using a sharp wrist movement. This mimics the calming effect of human touch or pulse-syncing, helping to break cycles of panic or distress even when lying in bed.

### Intelligent Sleep Assistance (Planned)
Specialized, rhythmic haptic sessions designed to assist in falling asleep and maintaining sleep quality. The app is evolving to detect signs of tremors or night terrors and respond with gentle tactile interventions.

### Persistent Customization & Calibration
The app includes precision calibration tools for both acoustic squeeze detection and motion sensitivity. All your preferences—intensity, BPM, and detection thresholds—are automatically saved and remembered.

### Privacy First
Built for the most sensitive contexts, the app uses local AI (LLM and Speech-to-Text) to process triggers. No audio or sensor data leaves the device, ensuring total user confidentiality.

## Information for Treatment Experts
This project explores the intersection of **Haptic Grounding Therapy** and **Mobile Biosensing**. By providing a consistent, rhythmic tactile stimulus (the "heartbeat"), the app targets the "window of tolerance," aiming to assist users in returning from hyper-arousal to a regulated state without the need for external intervention.

---

## Tech Stack
- **UI:** Jetpack Compose (Material 3)
- **Logic:** Kotlin / Coroutines
- **Sensing:** Acoustic Pressure Detection (sonar-based) & Linear Acceleration (gravity-independent motion)
- **Background:** Android Foreground Service with WakeLock
- **AI Engine:** LLMEdge (Local Llama & Whisper)
- **Signal Processing:** JTransforms

## Requirements
- Android 11 (API 30) or higher.
- Physical device required for haptic and sensor functionality.



IDEAS- for modes
use beat detection, possibly mdi files, max amplitude stereo left and right for Bilateral Stimulation. (2 phones needed (BT))

2 phones connected ideal for Bilateral Stimulation, one on each arm. connected with BT