# Changelog

All notable changes to the ArtCraft Launcher will be documented in this file.

## [Unreleased]
- Initial open-source release
- Added comprehensive CI/CD workflows
- Enhanced Rust bridge with APK metadata support
- Added custom splash screen with animations
- Added settings screen with theme selection
- Added Lottie animation support
- Added custom loading animations and transitions

## [v2.0.0] - 2025-11-07

### Added
- **GitHub Workflows**: Complete CI/CD pipeline with:
  - Build and release workflow with auto-publish to reality-prod
  - Rust tests workflow with coverage
  - Android lint and format workflow
  - Clippy linting and security audits

- **Rust Bridge Enhancements**:
  - APK metadata extraction (package name, version, SDK versions)
  - APK validation (magic bytes, signature, structure)
  - ABI detection from APK filename
  - ABI compatibility checking
  - Improved error handling and logging

- **UI Enhancements**:
  - Custom splash screen with animated ArtCraft logo
  - Settings screen with:
    - Theme selection (Light/Dark/System)
    - Update checking preferences
    - Notification preferences
    - Rust Bridge toggle
    - Cache management
    - Bridge information
  - Custom install screens with animated progress
  - Lottie animation support for download animations
  - Custom loading indicators (bouncing dots, circular progress)
  - Shimmer effects for loading states
  - Smooth transitions between screens

- **New Dependencies**:
  - `com.airbnb.android:lottie-compose:6.4.0` - Lottie animation support
  - `io.coil-kt:coil-compose:2.6.0` - Image loading
  - `com.google.accompanist:accompanist-navigation-animation:0.34.0` - Navigation animations

### Changed
- Updated Rust bridge version to 0.2.0
- Updated Gradle to 8.7.3
- Updated Compose BOM to 2024.12.01
- Updated Kotlin to 2.0.21
- Added material icons extended
- Improved error messages and user feedback

### Fixed
- Better error handling in download and install flows
- Improved fallback to Kotlin implementations when Rust bridge is unavailable

## [v1.0.0] - 2025-10-28

### Added
- Initial ArtCraft Launcher release
- Basic app scanning and display
- APK download and installation
- SHA-256 verification
- Version comparison
- ABI detection and selection
- Rust bridge with basic functionality

[Unreleased]: https://github.com/reality-prod/artcraft-launcher-android/compare/v2.0.0...HEAD
[v2.0.0]: https://github.com/reality-prod/artcraft-launcher-android/compare/v1.0.0...v2.0.0
[v1.0.0]: https://github.com/reality-prod/artcraft-launcher-android/releases/tag/v1.0.0
