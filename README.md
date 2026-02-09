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
- **Automatic Analysis**: Missing a profile for a track? The app automatically queues it for background analysis as soon as it's selected.
- **Sync Offset Adjustment**: Precision-tune the timing between audio and haptics to account for different hardware latencies.

### Multi-Layer Audio Visualizer
A high-resolution, 32-band frequency visualizer available in-app and via persistent notification. It supports independent, overlapping layers that can be toggled in settings:
- **Vertical Bars (Live HZ)**: Detailed frequency-specific feedback with subtle threshold lines.
- **Channel Intensity (L/R)**: Real-time volume levels for left and right channels.
- **Waveform**: A fluid background representation of the overall audio energy.
- **Color Coding**: Cyan (Amplitude), Tan (Bass), Orange (Drum), and Grass Green (Guitar) help identify exactly which sounds are driving your grounding feedback.
- **Dual Threshold Lines**: The visualizer now displays both the bar's trigger threshold (White) and the icon's animation threshold (Red), providing a clear visual distinction between the two.

### Smooth, Calming Animations
All visual feedback—including frequency bars, haptic icons, and progress indicators—uses non-bouncy, medium-stiffness spring animations for a more responsive feel. Active profiles now feature a "wobble" effect when triggered, providing a clear visual link between the sound and the haptic pulse.

### Intelligent Auto-Selection & Management
The app automatically prepares your therapeutic environment by choosing the best available audio track on startup. It prioritizes previously analyzed files and remembers your last played track. 

### Notification Media Controls
Full media playback controls are integrated directly into the persistent notification. This allows you to play, pause, stop, and skip forward or backward by 5 or 30 seconds without needing to open the app.

### Persistent Customization & Precision Controls
- **Slider Snapping**: All sensitive controls (Intensity, Volume, Sensitivity) feature configurable snapping increments for predictable and consistent adjustments. The snapping settings are now grouped by "Whole Numbers" and "Decimals" for clarity.
- **Visualizer Gain & Thresholds**: Independent gain and icon trigger threshold controls for each frequency range allow you to tailor the visual feedback to your specific hearing or hardware needs.
- **Inverted Alpha**: An option to invert the icon alpha, making them bright when triggered instead of dim.
- **Reset to Defaults**: Quickly revert all settings to factory configurations with a single tap.
- **Experimental Features Toggle**: Hide complex or beta tools like Squeeze Detection to maintain a focused, distraction-free interface for daily use.

### Privacy First
Built for the most sensitive contexts, all detection and analysis happen locally on the device. No audio or sensor data is recorded permanently or sent to external servers, ensuring total user confidentiality.

---

## Tech Stack
- **UI:** Jetpack Compose (Material 3) with real-time reactive state.
- **Logic:** Kotlin / Coroutines / Channel-based Task Queueing.
- **Sensing:** Acoustic Pressure Detection (sonar-based) & Linear Acceleration (gravity-independent motion).
- **Background:** Android Foreground Service with WakeLock and persistent status notifications.
- **Signal Processing:** JTransforms (FFT) / Adaptive Transient Detection.
- **Persistence**: JSON-based haptic profiles and encrypted preferences.

## Requirements
- Android 11 (API 30) or higher.
- Physical device required for haptic and sensor functionality.
