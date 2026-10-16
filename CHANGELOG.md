# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [2.0.0] - Unreleased

"Any File, Any Code": sessions are read from any document, and the camera reads every
common code format.

### Added
- Codes are read from PDF, images, text lists and CSV, several files at once, with
  progress per file and page. Code kinds can be unticked before a session is created.
- Every barcode format ML Kit supports. Fields are parsed for GS1 (Chestny ZNAK, EU FMD
  and other traceability systems), GS1 Digital Link, German IFA PPN and HIBC; expiry and
  batch are shown under each code and in the scan banner.
- Sessions that collect codes without a document; adding a document later turns them
  into checking sessions.
- Answers say how long ago a repeated code was scanned.
- Viewfinder with a smoothed outline around the accepted code, tap to focus, pinch to
  zoom, a torch, and an optional scan-on-tap mode.
- Manual entry that searches the session's codes.
- Bin for removed codes, kept for a week.
- Search, sort and swipe actions in code lists; a scrollbar in long lists.
- Session files: share a session and merge its marks on another phone.
- Automatic backup of sessions to a folder of the user's choice.
- Settings for theme, language (switched in the app), clock, vibration, sound and what
  is shown under codes.
- Dark theme, landscape layout, English and Russian.
- Privacy policy page.

### Changed
- Sessions are ordered by when they were last opened, and show their progress.
- Screens use view models with dependency injection (Hilt); scan decisions moved out of
  the screen into tested classes.
- The screen may sleep after five minutes without a code in view.
- Licensed under Apache 2.0.

### Fixed
- With two codes in the viewfinder the answer jumped between them and the phone buzzed
  several times a second; each code is now answered once while it stays in view.
- Misreads of linear barcodes from a tilted label: a new linear code needs several equal
  reads.
- A code held in front of the camera is answered once instead of repeatedly.
- The last mark is no longer lost when leaving the scan screen right after a scan, and a
  failed write takes the shown answer back.
- Rotation no longer blacks out the camera.

### Removed
- Internet and network state permissions pulled in by ML Kit telemetry.
- The session database is excluded from Android cloud backup, which could restore it
  corrupted; the folder backup replaces it.

## [1.2.1] - 2025-09-02

### Changed
- English localisation of the whole interface.
- Code cleanup.

## [1.2.0] - 2025-08-22

### Added
- Deleting scanned and remaining codes from a session.

## [1.1.2] - 2025-08-21

### Added
- App icon.

## [1.1.1] - 2025-08-20

### Fixed
- QR scanning on devices where it did not work.

## [1.1.0] - 2025-08-20

### Added
- Visual feedback for every scan.

[2.0.0]: https://github.com/LeonovAndreww/QR-Checker/compare/v1.2.1...HEAD
[1.2.1]: https://github.com/LeonovAndreww/QR-Checker/compare/v1.2.0...v1.2.1
[1.2.0]: https://github.com/LeonovAndreww/QR-Checker/compare/v1.1.2...v1.2.0
[1.1.2]: https://github.com/LeonovAndreww/QR-Checker/compare/v1.1.1...v1.1.2
[1.1.1]: https://github.com/LeonovAndreww/QR-Checker/compare/v1.1.0...v1.1.1
[1.1.0]: https://github.com/LeonovAndreww/QR-Checker/releases/tag/v1.1.0
