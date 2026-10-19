# Changelog

All notable changes to this project are documented here. The format follows
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and versions follow
[Semantic Versioning](https://semver.org/).

## [2.0.0] - Unreleased

### Added
- Codes from PDF, images, text and CSV files, several at once.
- All barcode formats ML Kit reads. Unwanted kinds can be unticked when creating a session.
- Expiry and batch for GS1 codes (Chestny ZNAK, EU medicines and others), GS1 Digital
  Link, German PPN and HIBC.
- Sessions without a document that record new codes.
- "Already scanned" shows how long ago.
- Viewfinder, tap to focus, pinch to zoom, torch, optional scan on tap.
- Manual entry with search over the session's codes, or a code read from an image.
- A mark can be removed right from the scan banner, also on a repeat.
- The scan banner can be at the top or at the bottom of the screen.
- Bin for removed codes, emptied after a week.
- Search, sorting and swipe actions in code lists, scrollbar.
- Sharing a session as a file and merging marks from it.
- Automatic backup of sessions to a chosen folder.
- Settings: theme, language, clock format, vibration, sound, what is shown under codes.
- Dark theme, landscape layout.
- Privacy policy page.

### Changed
- Sessions are sorted by last opened and show progress.
- Screens moved to ViewModels with Hilt.
- The screen can turn off after five minutes without codes in view.
- License changed to Apache 2.0.

### Fixed
- With two codes in view the app switched between them and vibrated nonstop.
- Wrong reads of linear barcodes on tilted labels.
- The same code was answered again while still in view.
- The last mark could be lost when leaving the scan screen right after a scan.
- Black flash on rotation.

### Removed
- Internet permissions added by ML Kit.
- Session database from Android cloud backup.

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
