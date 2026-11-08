# ArtCraft Launcher (Android)

[![Build and Release](https://github.com/reality-prod/artcraft-launcher-android/actions/workflows/build-and-release.yml/badge.svg)](https://github.com/reality-prod/artcraft-launcher-android/actions/workflows/build-and-release.yml)
[![Android Lint](https://github.com/reality-prod/artcraft-launcher-android/actions/workflows/android-lint.yml/badge.svg)](https://github.com/reality-prod/artcraft-launcher-android/actions/workflows/android-lint.yml/badge.svg)
[![Rust Tests](https://github.com/reality-prod/artcraft-launcher-android/actions/workflows/rust-tests.yml/badge.svg)](https://github.com/reality-prod/artcraft-launcher-android/actions/workflows/rust-tests.yml/badge.svg)

Unofficial launcher for the ArtCraft Android apps. It scans GitHub, shows what exists, downloads
the right APK for your phone, and hands it to the system installer.

## Features

### Core Functionality
* **Mobile-first UI**: single column, bottom navigation (Apps / Installed / Settings / About), store-style rows with one
  thumb-reachable button, details in a bottom sheet. Desktop-only products (ArtCraft, and the desktop editions of
  PdfCraft/FilmCraft) are shown as native Kotlin mobile cards and stay "Coming soon" until an APK exists.
* **Sources scanned**: every public `*-android` repo in [`artcraft-android`](https://github.com/artcraft-android)
  (PdfCraft, FilmCraft, LightCraft today, new repos appear automatically) plus
  `artcraft-android/artcraft-android` (if it ever appears) and
  [`storytold/artcraft`](https://github.com/storytold/artcraft).
* **Coming soon**: any app whose newest release has no `.apk` (ArtCraft itself is Windows/macOS only
  for now). It flips to Install the moment a release ships an APK.
* **Icons**: `assets/app-icon/<name>-1024.png` from each repo, falling back to the org avatar.
* **ABI**: plain APK for arm64, `-armeabi-v7a` APK for 32-bit phones (matches the repos' release layout).
* **Safety**: verifies GitHub's per-asset SHA-256 when present; install goes through `PackageInstaller`
  so Android shows its normal confirmation.

### New Features (v2.0)

#### Visual Enhancements
- **Custom Splash Screen**: Animated splash screen with ArtCraft branding and smooth transitions
- **Lottie Animation Support**: Support for Lottie JSON animations throughout the app
- **Custom Loading Animations**: Bouncing dots, circular progress with arc animations, shimmer effects
- **Animated Transitions**: Smooth slide, fade, and scale transitions between screens
- **Enhanced UI**: Improved app cards, bottom sheets, and interactive elements with animations

#### Rust Bridge Enhancements
- **APK Metadata Extraction**: Extract package name, version code, version name, min/target SDK from APKs
- **APK Validation**: Validate APK files before installation (check magic bytes, signature, structure)
- **ABI Detection**: Automatic detection of APK ABI from filename
- **ABI Compatibility Check**: Verify if APK is compatible with device ABIs
- **Improved Error Handling**: Better error messages and fallback to Kotlin implementations
- **Performance Optimizations**: Optimized Rust code with LTO, strip, and panic handling

#### User Experience Improvements
- **Custom Install Screens**: Beautiful animated install screens with progress tracking
- **Download Speed Display**: Shows real-time download speed
- **File Size Progress**: Detailed progress showing downloaded/total size
- **Settings Screen**: Full configuration options:
  - Theme selection (Light/Dark/System)
  - Auto-check for updates
  - Notification preferences
  - Rust Bridge toggle
  - Cache management
  - Bridge information and version
- **Search & Filter**: Enhanced app discovery (coming soon)

#### GitHub Workflows
Complete CI/CD pipeline with:
- **Build Workflow**: Builds APK with and without Rust bridge
- **Release Workflow**: Automatic GitHub releases with APK attachments
- **Auto-publish**: Can automatically create PRs to reality-prod/artcraft-launcher-android
- **Lint Workflow**: Kotlin, Android, and KTLint checks
- **Rust Tests Workflow**: Unit tests, Clippy linting, and security audits
- **Test Coverage**: Code coverage reports with Codecov integration

## Screenshots

| Splash Screen | Apps Tab | App Details |
|--------------|----------|-------------|
| ![Splash](https://via.placeholder.com/300x600?text=Splash+Screen) | ![Apps](https://via.placeholder.com/300x600?text=Apps+Tab) | ![Details](https://via.placeholder.com/300x600?text=App+Details) |

| Install Screen | Settings | About |
|---------------|----------|-------|
| ![Install](https://via.placeholder.com/300x600?text=Install+Screen) | ![Settings](https://via.placeholder.com/300x600?text=Settings) | ![About](https://via.placeholder.com/300x600?text=About) |

## Build

### Prerequisites
- Java JDK 17
- Android SDK 35
- Android NDK (for Rust bridge)
- Rust toolchain (optional, for Rust bridge)
- cargo-ndk (optional, for Rust bridge)

### Building the App

#### Without Rust Bridge (Simpler)
```bash
# First time only, to create the wrapper
gradle wrapper --gradle-version 8.9

# Build the APK
./gradlew assembleRelease
```

The APK will be in `app/build/outputs/apk/release/`.

#### With Rust Bridge (Faster Performance)
```bash
# Install Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
rustup target add aarch64-linux-android armv7-linux-androideabi

# Install cargo-ndk
cargo install cargo-ndk

# Set NDK path
export ANDROID_NDK_HOME=/path/to/android-ndk

# Build with Rust bridge
./gradlew assembleRelease -PbuildRust
```

The Rust bridge provides native performance for:
- Asset picking (choosing the right APK for your device)
- Version comparison
- SHA-256 verification
- APK metadata extraction

### Running Tests

```bash
# Android tests
./gradlew testDebugUnitTest

# Rust tests
cd rust && cargo test

# All tests (via Gradle)
./gradlew test
```

## Configuration

### Release Signing
Set environment variables for release signing:
```bash
export ARTCRAFT_KEYSTORE=/path/to/keystore.jks
export ARTCRAFT_KS_PASS=your_keystore_password
export ARTCRAFT_KEY_ALIAS=your_key_alias
export ARTCRAFT_KEY_PASS=your_key_password
```

Without these, `assembleRelease` falls back to the debug key so the APK still installs.

### GitHub Secrets for CI/CD
For the GitHub workflows to work, you'll need to set these secrets in your repository:
- `CODECOV_TOKEN`: For code coverage reports
- `GITHUB_TOKEN_REALITY_PROD`: Personal access token with write access to reality-prod/artcraft-launcher-android

## Project Structure

```
.
├── .github/                  # GitHub workflows for CI/CD
│   └── workflows/
│       ├── build-and-release.yml  # Build and release APKs
│       ├── rust-tests.yml         # Rust unit tests
│       └── android-lint.yml       # Linting and formatting
├── app/                      # Android application
│   └── src/main/
│       ├── java/app/artcraft/launcher/
│       │   ├── bridge/           # Native bridge and install logic
│       │   ├── data/             # Catalog, repositories, models
│       │   ├── ui/               # Compose UI components
│       │   │   ├── Animations.kt      # Custom animations
│       │   │   ├── LauncherScreen.kt # Main screen
│       │   │   ├── SettingsScreen.kt # Settings
│       │   │   ├── SplashScreen.kt    # Splash screen
│       │   │   └── Theme.kt           # Theme management
│       │   └── MainActivity.kt
│       └── res/                 # Resources
│           └── raw/              # Lottie animations
│               └── download_animation.json
└── rust/                     # Rust bridge
    ├── bridge/               # JNI bridge crate
    │   ├── src/
    │   │   ├── apk_metadata.rs    # APK metadata extraction
    │   │   ├── assets.rs          # Asset picking
    │   │   ├── checksum.rs        # SHA-256 verification
    │   │   ├── lib.rs             # JNI exports
    │   │   └── version.rs         # Version comparison
    │   └── Cargo.toml
    └── Cargo.toml             # Workspace manifest
```

## Architecture

### Rust Bridge Architecture
The Rust bridge is designed to be safe and reliable:
- **No panics**: Every JNI entry point catches panics and returns error values
- **Hostile input**: All input is treated as untrusted (from network)
- **Fallback**: Kotlin implementations are provided as fallbacks
- **Safe Rust**: Uses `#![deny(unsafe_code)]` (except for JNI exports)

New functions in v2.0:
- `extractApkMetadata(path: String) -> String`: JSON with APK metadata
- `validateApk(path: String) -> String`: Empty on success, error message on failure
- `isApkCompatible(apkName: String, abisCsv: String) -> boolean`: Check ABI compatibility
- `getAbiFromApkName(apkName: String) -> String`: Extract ABI from filename

### Kotlin Fallback
All Rust functions have Kotlin fallback implementations in `NativeCore.kt`, ensuring the app works even without the Rust bridge.

## Publishing

### To GitHub
The workflow automatically creates GitHub releases when you push a tag:
```bash
# Create and push a new version tag
git tag v2.0.0
git push origin v2.0.0
```

### To reality-prod/artcraft-launcher-android
The workflow can automatically create a PR to update the launcher:
1. Push a tag to your repository
2. The workflow will automatically create a PR to reality-prod/artcraft-launcher-android
3. Review and merge the PR

## Notes
* Unauthenticated GitHub API allows 60 requests/hour per IP; a scan uses about 5. On rate limit the
  last cached list is shown with a banner.
* `QUERY_ALL_PACKAGES` is used to detect installed apps (package ids are learned from the APKs).
  Fine for GitHub/F-Droid/Obtainium distribution, not for Play.
* The launcher uses the system's `PackageInstaller` API, so Android shows its normal security
  confirmation before installing any APK.

## License
Apache License 2.0

## Contributing
Pull requests are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Make your changes
4. Run `./gradlew test` to ensure all tests pass
5. Submit a pull request

## Contact
For questions or suggestions, please open an issue on GitHub.

## Rust bridge (optional)
`rust/bridge` is a small Rust crate (JNI) that ports the pieces of
[`storytold/craft-launcher`](https://github.com/storytold/craft-launcher)'s core that a phone needs:
version ordering for release tags, picking the right APK per ABI, SHA-256 verification and
`SHA256SUMS.txt` lookup. `bridge/NativeCore.kt` loads it and falls back to Kotlin if it isn't packaged.

```
cd rust && cargo test                          # pure logic, no JVM needed
cargo install cargo-ndk && rustup target add aarch64-linux-android armv7-linux-androideabi
export ANDROID_NDK_HOME=/path/to/ndk
./gradlew assembleRelease -PbuildRust          # builds libartcraft_launcher_bridge.so into jniLibs
```
It deliberately does not depend on `artcraft-launcher-core`: upstream main has no Android platform or APK
package kind (that was in the closed PR #855), so the picker lives here. If upstream adds them, swap
`assets::pick_json` for core's `pick_asset` and keep the JNI signatures.
