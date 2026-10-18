# QR Checker
[![CI](https://github.com/LeonovAndreww/QR-Checker/actions/workflows/ci.yml/badge.svg)](https://github.com/LeonovAndreww/QR-Checker/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/LeonovAndreww/QR-Checker)](https://github.com/LeonovAndreww/QR-Checker/releases)
[![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?style=flat&logo=android)](https://www.android.com/)
[![GitHub Stars](https://img.shields.io/github/stars/LeonovAndreww/QR-Checker)](https://github.com/LeonovAndreww/QR-Checker/stargazers)
[![License](https://img.shields.io/badge/License-Apache%202.0-D22128?style=flat)](LICENSE)

Android app for checking codes against a list: load the list from a document, scan with
the camera and see what is already scanned, what came up twice and what is missing.

Typical uses:
- receiving a delivery against a waybill or a Chestny ZNAK export;
- checking a shipment before it leaves;
- ticket control at the entrance: a ticket used twice or not on the list shows up at once;
- collecting codes without a document, with a warning on repeats.

## Screenshots

<p align="center">
  <img src="docs/screenshots/scan-ok.jpg" width="240" alt="Scanned successfully"/>
  <img src="docs/screenshots/scan-repeat.jpg" width="240" alt="Already scanned"/>
  <img src="docs/screenshots/scan-foreign.jpg" width="240" alt="Not in this session"/>
</p>
<p align="center">
  <img src="docs/screenshots/sessions.jpg" width="240" alt="Sessions with their progress"/>
  <img src="docs/screenshots/reading-files.jpg" width="240" alt="Reading codes from the picked files"/>
  <img src="docs/screenshots/code-list.jpg" width="240" alt="Searchable list of remaining codes"/>
</p>

## What it does

A session is a list of codes and the marks on it. The list is read from PDF, images, text
or CSV, several files at once. For every scanned code the app answers: scanned, already
scanned and how long ago, or not in this session.

Data Matrix, QR and most barcode formats are read. A retail label often has a product
barcode next to the Data Matrix, so each code keeps its format and unwanted kinds can be
unticked before the session is created. For GS1 codes expiry and batch are shown.

The scanned and remaining lists can be searched, sorted and exported to CSV. A session can
be sent to another phone as a file, and the marks are merged there. Sessions are backed up
to a folder of your choice and survive reinstalling the app. Removed codes go to a bin for
a week.

If a code doesn't scan, it can be entered by hand: part of the number is enough to find it
in the list.

Sessions without a document record every new code and warn about repeats. If a document is
added later, the session starts checking against it.

### Supported codes

| Kind | What is read |
| --- | --- |
| GS1 element strings | GTIN, serial, batch, expiry and the rest, in any field order, with or without separators |
| Chestny ZNAK (Russia) | GS1 with the (91)/(92) or (93) crypto tail; the list shows the (01)(21) part printed on the box |
| EU FMD, İTS, SNCM | Medicine packs: GTIN, serial, batch and expiry |
| GS1 Digital Link | `https://…/01/<gtin>/10/<batch>?17=<expiry>`, checked by the GTIN check digit |
| IFA PPN (Germany) | ISO/IEC 15434 envelope with ASC MH10 identifiers |
| HIBC LIC | Primary and secondary data per ANSI/HIBC 2.6, mod 43 check character |
| EAN / UPC | By length and check digit |
| Anything else | QR with a link or text is matched as is |

## Architecture

One activity, Jetpack Compose, one ViewModel per screen.

```
ui/<screen>/   screen and its ViewModel
data/          Room database, SessionRepository, backup to a folder
codes/         parsing: GS1, HIBC, PPN, Digital Link, CSV lists
files/         reading PDF and images, session files, CSV export
scan/          what to answer when a code is scanned
settings/      app settings and language
di/            Hilt module
```

Each ViewModel keeps the screen state in a `StateFlow`; the screen only draws it.
Dependencies come through Hilt.

Code parsing and scan logic don't touch Android classes and are tested with plain JUnit.
The database and the scan ViewModel are tested with Robolectric on an in-memory Room
database. CI builds the app, runs the tests and lint on every push.

## Technologies used
[![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org/) 
[![Jetpack Compose](https://img.shields.io/badge/Jetpack%20Compose-4285F4?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/jetpack/compose)
[![Material Design 3](https://img.shields.io/badge/Material%20Design%203-FF6F00?style=for-the-badge&logo=google&logoColor=white)](https://m3.material.io/)
[![Room](https://img.shields.io/badge/Room-00ACC1?style=for-the-badge&logo=sqlite&logoColor=white)](https://developer.android.com/training/data-storage/room)
[![CameraX](https://img.shields.io/badge/CameraX-5D9CEC?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com/training/camerax)
[![ML Kit](https://img.shields.io/badge/ML%20Kit-4285F4?style=for-the-badge&logo=google&logoColor=white)](https://developers.google.com/ml-kit/vision/barcode-scanning)
[![Hilt](https://img.shields.io/badge/Hilt-2196F3?style=for-the-badge&logo=android&logoColor=white)](https://dagger.dev/hilt/)

The barcode model is bundled into the package, so the app does not need Google Play
services to be installed.

## Permissions

- **Camera** - required to scan codes in real time.
- **Vibrate** - provides haptic feedback for each scan result.

The app asks for nothing else. Internet access is explicitly removed from the manifest.

## Privacy

- The app works fully locally - sessions, codes and marks stay on the device and are
  **not** sent to any server.
- What is stored:
  - Sessions: the code lists, which of them are scanned and when.
  - App settings, and the folder picked for backups.
  - Temporary files while a document is being read, and the CSV files you export.
- The session database is left out of Android cloud backup - use the app's own backup to
  a folder instead.
- How to delete data:
  - Delete a session in the app, or empty the bin on its edit screen.
  - Uninstalling the app deletes all of its local data. Files written to your backup
    folder and exported CSVs stay where they are.
- Third-party telemetry:
  - There is no analytics/telemetry enabled.

The full policy is published at
[leonovandreww.github.io/QR-Checker/privacy.html](https://leonovandreww.github.io/QR-Checker/privacy.html).

## Installation
### Option A - Download APK
1. Go to [Releases](https://github.com/LeonovAndreww/QR-Checker/releases).
2. Download the latest 'qr-checker.apk' on your device.
3. Enable "Install from unknown sources" if needed.
4. Install the application.

### Option B - Build from source
```bash
git clone https://github.com/LeonovAndreww/QR-Checker.git
cd QR-Checker
./gradlew assembleDebug

./gradlew installDebug
```
Run the same checks as CI:
```bash
./gradlew assembleDebug testDebugUnitTest lintDebug
```

Alternatively, open the project in [Android Studio](https://developer.android.com) and run on an emulator or device.

Release builds are signed from `keystore.properties` next to the project - copy
`keystore.properties.example` and fill it in. Without that file `assembleRelease` still
works and simply leaves the APK unsigned, so a fresh clone builds.

Requires Android 7.0 (API 24) or newer.

## License

Licensed under the [Apache License 2.0](LICENSE). You may use, modify and
redistribute the code, including commercially, as long as the copyright notice and the
license are kept and changed files are marked as changed.

## Contact
Issues and feature requests are tracked via GitHub Issues:
- [Create new issue](https://github.com/LeonovAndreww/QR-Checker/issues)
