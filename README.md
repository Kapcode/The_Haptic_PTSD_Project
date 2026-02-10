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
- **Automatic Analysis**: Missing a profile for a track? The app automatically queues it for background analysis as soon as it's selected.
- **Live Profile Switching**: Switch between Bass, Drum, Guitar, and Amplitude profiles seamlessly during playback. The haptics update instantly to match the new profile.
- **Sync Offset Adjustment**: Precision-tune the timing between audio and haptics to account for different hardware latencies.
- **Seamless Track Changes**: Selecting a new track now automatically stops the old one and resets the UI, ensuring a clean and predictable transition.

### Multi-Layer Audio Visualizer
A high-resolution, 32-band frequency visualizer available in-app and via persistent notification. It supports independent, overlapping layers that can be toggled in settings:
- **Vertical Bars (Live HZ)**: Detailed frequency-specific feedback with subtle threshold lines.
- **Channel Intensity (L/R)**: Real-time volume levels for left and right channels.
- **Waveform**: A fluid background representation of the overall audio energy.
- **Dynamic Theming**: The app's primary color updates live to match the color of the selected haptic profile (Cyan, Tan, Orange, or Green).
- **Dual Threshold Lines**: The visualizer displays both the bar's trigger threshold (White) and the icon's animation threshold (Red), providing a clear visual distinction.

### Smooth, Calming Animations
All visual feedback—including frequency bars, haptic icons, and progress indicators—uses non-bouncy, medium-stiffness spring animations for a more responsive feel. Active profiles now feature a circular "glow" effect when triggered, providing a clear visual link between the sound and the haptic pulse.

### Settings Drawer & Workspace Management
Configuration settings are now neatly tucked away in a side navigation drawer, keeping the main workspace focused on therapeutic monitoring and playback. This includes visualizer scaling, gain controls, and snapping increments.

### Intelligent Auto-Selection & Management
The app automatically prepares your therapeutic environment by choosing the best available audio track on startup. It prioritizes previously analyzed files and remembers your last played track. 

### Notification Media Controls
Full media playback controls are integrated directly into the persistent notification. This allows you to play, pause, stop, and skip forward or backward without needing to open the app.

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
