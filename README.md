# QR Checker
[![Release](https://img.shields.io/github/v/release/LeonovAndreww/QR-Checker)](https://github.com/LeonovAndreww/QR-Checker/releases)

Android application for scanning and extracting data from QR codes from camera and multi-page PDF documents.

## Screenshots

<p align="center">
  <img src="screenshots/menu.jpg" width="260" alt="Main menu" />
  <img src="screenshots/create.jpg" width="260" alt="Create Session" />
  <img src="screenshots/scan.jpg" width="260" alt="QR Scan Screen" />
</p>

## Technologies used
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/) 
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material Design 3](https://img.shields.io/badge/Material%20Design%203-FF6F00?style=for-the-badge&logo=google&logoColor=white)](https://m3.material.io/)
[![Room](https://img.shields.io/badge/Room-00ACC1?style=for-the-badge&logo=sqlite&logoColor=white)](https://developer.android.com/training/data-storage/room)
[![CameraX](https://img.shields.io/badge/CameraX-5D9CEC?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/training/camerax)
[![ZXing Embedded](https://img.shields.io/badge/ZXing%20Embedded-000000?style=for-the-badge&logo=android&logoColor=white)](https://github.com/journeyapps/zxing-android-embedded)

## Permissions

- **Camera** – required for scanning QR codes using the device camera.
- **Vibrate** – provides haptic feedback for each scan result.

## Privacy

- The app works fully locally - scanned data and history are stored on the device and are NOT sent to any server.
- What is stored:
  - Scan history (scanned text/URLs).
  - Temporary files used for PDF processing.
- How to delete data:
  - Use the in-app option "Delete session".
  - Uninstalling the app will delete all of the app's local data.
- Third-party telemetry:
  - There is no analytics/telemetry enabled.

## Installation
### Option A — Download APK
1. Go to [Releases](https://github.com/LeonovAndreww/QR-Checker/releases).
2. Download and install the latest APK on your device.

### Option B — Build from source
```bash
git clone https://github.com/LeonovAndreww/QR-Checker.git
cd QR-Checker
./gradlew assembleDebug

./gradlew installDebug
```
Or open the project in [Android Studio](https://developer.android.com) and run on an emulator or device.

## Contact
To report issues or request features, use GitHub Issues:
- [Create new issue](https://github.com/LeonovAndreww/QR-Checker/issues)
