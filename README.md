# The Haptic PTSD Project

The Haptic PTSD Project is an innovative Android application designed to provide immediate, non-invasive support for individuals managing PTSD and sleep-related distress. By combining tactile grounding techniques with advanced on-device sensing, the app offers a digital "anchor" during moments of anxiety or night terrors.

## Core Purpose

The application aims to provide physiological regulation through:
- **Immediate Grounding:** A "Squeeze-to-Soothe" mechanism that provides instant comfort when the user feels overwhelmed.
- **Rhythmic Regulation:** High-fidelity haptic patterns, such as a simulated "heartbeat," designed to synchronize with the user's autonomic nervous system to lower heart rate and promote calm.
- **Privacy-Centric Monitoring:** Continuous sensing of tremors and sleep disturbances using local, private AI analysis that never sends data to the cloud.

## Key Features

### Squeeze-to-Soothe Heartbeat
A primary therapeutic feature where the user simply squeezes their phone to activate a comforting, rhythmic "heartbeat" vibration. This mimics the calming effect of human touch or pulse-syncing, helping to break cycles of panic or distress.

### Intelligent Sleep Assistance
Specialized, rhythmic haptic sessions designed to assist in falling asleep and maintaining sleep quality. The app can be configured to detect signs of night terrors and respond with gentle tactile interventions.

### Adaptive Calibration
Because every user and every device is different, the app includes precision calibration tools to ensure detection sensitivity is perfectly tuned to the individual's needs and their specific phone hardware.

### Privacy First
Built for the most sensitive contexts, the app uses local AI (LLM and Speech-to-Text) to process triggers. No audio or sensor data leaves the device, ensuring total user confidentiality.

## Information for Treatment Experts
This project explores the intersection of **Haptic Grounding Therapy** and **Mobile Biosensing**. By providing a consistent, rhythmic tactile stimulus (the "heartbeat"), the app targets the "window of tolerance," aiming to assist users in returning from hyper-arousal to a regulated state without the need for external intervention.

---

## Tech Stack
- **UI:** Jetpack Compose (Material 3)
- **Logic:** Kotlin / Coroutines
- **Sensing:** Acoustic Pressure Detection (FFT-based)
- **AI Engine:** LLMEdge (Local Llama & Whisper)
- **Signal Processing:** JTransforms

## Requirements
- Android 11 (API 30) or higher.
- Physical device required for haptic and sensor functionality.
