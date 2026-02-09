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
Analyze any audio file from your library to generate a custom haptic profile. The app uses advanced signal processing to "feel" the music, providing synchronized tactile feedback that matches the intensity and duration of detected transients. Supports automatic caching for instant re-play.

### Advanced Audio Visualizer
A high-resolution, 32-band frequency visualizer available in-app and via persistent notification. It features color-coded ranges that correspond to detection profiles:
- **Cyan**: Overall Amplitude
- **Tan**: Bass Range
- **Orange**: Drum Range
- **Grass Green**: Guitar Range
Includes profile-specific icons beneath the frequency bands to indicate which ranges are currently being monitored.

### Smooth, Calming Animations
All visual feedback—including frequency bars, haptic icons, and progress indicators—uses non-bouncy, low-stiffness spring animations. This ensures that visual transitions are fluid and glide between states, avoiding abrupt flashing or jarring movements.

### Intelligent Auto-Selection
The app automatically prepares your therapeutic environment by choosing the best available audio track. It prioritizes previously analyzed files (choosing the shortest ones for quick sessions) and remembers your last played track, ensuring support is always one tap away.

### Notification Media Controls
Full media playback controls are integrated directly into the persistent notification. This allows you to play, pause, stop, and skip forward or backward by 5 or 30 seconds without needing to open the app. The controls are synchronized with the in-app player.

### Persistent Customization & Calibration
The app includes precision calibration tools for both acoustic pressure detection and motion sensitivity. All preferences—intensity, BPM, lead-in/out timing, and detection thresholds—are automatically saved.

### Privacy First
Built for the most sensitive contexts, all detection and analysis happen locally on the device. No audio or sensor data is recorded permanently or sent to external servers, ensuring total user confidentiality.

---

## Tech Stack
- **UI:** Jetpack Compose (Material 3)
- **Logic:** Kotlin / Coroutines / ViewModels
- **Sensing:** Acoustic Pressure Detection (sonar-based) & Linear Acceleration (gravity-independent motion)
- **Background:** Android Foreground Service with WakeLock
- **Signal Processing:** JTransforms (FFT) / Adaptive Transient Detection
- **Persistence**: JSON-based haptic profiles

## Requirements
- Android 11 (API 30) or higher.
- Physical device required for haptic and sensor functionality.
