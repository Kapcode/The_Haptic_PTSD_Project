# The Haptic PTSD Project

The Haptic PTSD Project is an Android application designed to assist individuals struggling with PTSD and sleep quality issues. By leveraging the physical capabilities of modern smartphones, the app provides non-invasive support through tactile feedback, local AI analysis, and motion sensing.

## Purpose

The primary goal of this application is to improve sleep quality and provide relief from PTSD symptoms using:
- **Haptic Feedback:** Utilizing the cellphone's vibration motor to provide grounding and calming sensations.
- **Sensor-Based Detection:** Monitoring movement and orientation using the gyroscope to detect signs of restlessness or night terrors.
- **Local AI Analysis:** Utilizing on-device Large Language Models (LLM) and Speech-to-Text (STT) for privacy-preserving analysis of sleep patterns and vocal triggers.

## Features (Planned/Current)

- **Tremor Detection:** Real-time FFT analysis of sensor data to identify PTSD-related physical tremors.
- **Vocal Trigger Response:** Local speech transcription to detect distress signals during sleep.
- **Intelligent Grounding:** Adaptive haptic patterns triggered by detected episodes.
- **Privacy First:** All AI processing happens locally on your device.

## Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose (Material 3)
- **Local AI:** LLMEdge (Llama.cpp & Whisper.cpp wrappers)
- **Signal Processing:** JTransforms (FFT)
- **Hardware Integration:** Android Haptics and Sensor APIs

## Getting Started

1. Clone the repository.
2. Open the project in Android Studio.
3. **Requirement:** Ensure `app/libs/llmedge-release.aar` is present in the project.
4. Build and run on an Android device (Android 11 / API 30 or higher).
