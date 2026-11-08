# ArtCraft Launcher - Changes Summary

## Overview
This document summarizes all the enhancements made to the ArtCraft Launcher Android app, including new features, bug fixes, and infrastructure improvements.

---

## ✅ Fixed Issues

### Rust Bridge Compilation Errors
**File**: `rust/bridge/src/apk_metadata.rs`
- Fixed error conversion issue with `?` operator in `validate_apk()` function
- Properly handled `std::io::Error` to `String` conversions using `map_err`

### Kotlin Compilation Errors

**File**: `app/src/main/java/app/artcraft/launcher/ui/SettingsScreen.kt`
- Added missing import: `import androidx.compose.ui.draw.rotate`
- Fixed unresolved reference error on line 394

**File**: `app/src/main/java/app/artcraft/launcher/ui/SplashScreen.kt`
- Added missing imports: `import androidx.compose.foundation.layout.Row`
- Added missing imports: `import androidx.compose.material3.CircularProgressIndicator`
- Fixed `checkmarkScale` to `checkmarkScale.value` on line 166

---

## 🆕 New Features Added

### 1. Enhanced Rust Bridge (`rust/bridge/`)

#### New Module: `apk_utils.rs`
Added comprehensive APK utility functions:
- `extract_package_name_from_apk()` - Extract package name from APK file
- `extract_version_code_from_apk_name()` - Extract version code from filename
- `extract_version_name_from_apk_name()` - Extract version name from filename
- `get_apk_hash()` - Compute SHA-256 hash of APK file
- `is_split_apk()` - Check if APK is a split APK
- `get_supported_abis_from_apk_name()` - Get all supported ABIs from filename
- `format_apk_size()` - Format file size in human-readable format

#### Updated: `lib.rs`
- Added new JNI bindings for all `apk_utils` functions
- Total of 6 new native methods exposed to Kotlin

#### Updated: `Cargo.toml`
- Added `apk_utils` feature flag
- Updated description to reflect new capabilities

### 2. Kotlin Fallback Implementations

**File**: `app/src/main/java/app/artcraft/launcher/bridge/NativeCore.kt`
- Added Kotlin fallback implementations for all new Rust functions
- Ensures app works even without Rust bridge compiled
- New methods:
  - `extractPackageName()`
  - `extractVersionCode()`
  - `extractVersionName()`
  - `getApkHash()`
  - `isSplitApk()`
  - `getSupportedAbis()`

### 3. Custom Animations

**File**: `app/src/main/java/app/artcraft/launcher/ui/Animations.kt`
Reusable animation components:
- `BouncySpringSpec` - Bouncy spring animation
- `FastSnapSpec` - Fast snap animation
- `SmoothEaseInOutSpec` - Smooth ease in/out
- `QuickFadeSpec` - Quick fade animation
- `Modifier.pulse()` - Pulsing effect for loading states
- `Modifier.shimmer()` - Shimmer effect for content loading
- `Modifier.shake()` - Shake animation for errors/attention
- `Modifier.rotateInfinitely()` - Continuous rotation animation
- `ArtCraftCircularProgress` - Custom circular progress indicator
- `DotLoadingIndicator` - Three bouncing dots loading indicator
- `SlideFadeVisibility` - Slide and fade transition
- `ScaleFadeTransition` - Scale and fade transition
- `fastOutSlowInEasing()` - Custom easing function

### 4. Custom Install Screens

**File**: `app/src/main/java/app/artcraft/launcher/ui/SplashScreen.kt`
- `SplashScreen()` - Animated splash screen with:
  - Gradient background
  - Animated ArtCraft logo with scale and rotation
  - Loading progress indicator
  - Pulsing tagline animation
- `InstallScreen()` - Custom install screen with:
  - Circular progress indicator
  - Download speed display
  - File size and downloaded bytes
  - Animated download icon
  - Progress bar with statistics
- `InstallSuccessScreen()` - Success animation with:
  - Animated checkmark
  - Bouncy success animation
  - Tap to dismiss

### 5. Settings Screen

**File**: `app/src/main/java/app/artcraft/launcher/ui/SettingsScreen.kt`
Complete settings UI with:
- **Appearance Section**:
  - Theme selection (Light, Dark, System) with radio buttons
  - Smooth expandable categories
- **Updates Section**:
  - Auto-check for updates toggle
  - Notify new releases toggle
  - Manual check for updates
- **Storage Section**:
  - Cache size display
  - Clear cache button with confirmation dialog
  - Progress indicator during clearing
- **About Section**:
  - Version information
  - Source code links
  - Disclaimer

### 6. Theme System

**File**: `app/src/main/java/app/artcraft/launcher/ui/Theme.kt`
- Added theme preference system
- Light and Dark color schemes
- Medium and High contrast variants
- `ArtCraftTheme` composable for theme switching

---

## 📦 GitHub Workflows

### 1. Enhanced Build and Release (`build-and-release.yml`)
- Auto-publish to `reality-prod/artcraft-launcher-android` on tag push
- Improved PR description with build info
- Better error handling
- Updated secret reference from `GITHUB_TOKEN_REALITY_PROD` to `REALITY_PROD_PAT`

### 2. New: Nightly Build Workflow (`nightly-build.yml`)
- Automated daily builds at 2 AM UTC
- Creates pre-release with nightly tag
- Includes Rust bridge
- APK artifacts retained for 30 days
- Manual trigger support

### 3. New: Manual Release Workflow (`manual-release.yml`)
- Trigger via workflow_dispatch
- Custom version input
- Optional release message
- Toggle for reality-prod publishing
- Flexible release process

### 4. Rust Tests (`rust-tests.yml`)
- Unit tests for all Rust code
- Clippy linting with warnings as errors
- Security audit with cargo-audit
- Coverage reporting via Codecov (optional)

### 5. Android Lint (`android-lint.yml`)
- Android lint checks
- Spotless formatting verification
- KTLint code style checks

### 6. New: CI/CD Documentation (`CI_CD.md`)
Comprehensive documentation covering:
- Workflow descriptions and triggers
- Setup instructions
- Required secrets
- Local building and testing
- Versioning guidelines
- Publishing process
- Troubleshooting guide

---

## 🔧 Build System Improvements

### Gradle Configuration
**File**: `app/build.gradle.kts`
- Added Lottie animation support: `com.airbnb.android:lottie-compose:6.4.0`
- Added Coil image loading: `io.coil-kt:coil-compose:2.6.0`
- Added Accompanist utilities: `com.google.accompanist:accompanist-navigation-animation:0.34.0`

### Rust Configuration
**File**: `rust/bridge/Cargo.toml`
- Version bumped to `0.2.0`
- Performance optimizations:
  - `opt-level = "s"`
  - `lto = true`
  - `strip = true`
  - `codegen-units = 1`
  - `panic = "abort"`

---

## 📁 Files Modified

### Rust
- `rust/bridge/src/lib.rs` - Added new module declarations and JNI bindings
- `rust/bridge/src/apk_metadata.rs` - Fixed compilation errors
- `rust/bridge/src/apk_utils.rs` - **NEW** - Enhanced APK utilities
- `rust/bridge/Cargo.toml` - Updated dependencies and features

### Kotlin
- `app/src/main/java/app/artcraft/launcher/bridge/NativeCore.kt` - Added new native methods and fallbacks
- `app/src/main/java/app/artcraft/launcher/ui/SplashScreen.kt` - Fixed imports, added install screens
- `app/src/main/java/app/artcraft/launcher/ui/SettingsScreen.kt` - Fixed imports
- `app/src/main/java/app/artcraft/launcher/ui/Animations.kt` - **NEW** - Animation utilities
- `app/src/main/java/app/artcraft/launcher/ui/Theme.kt` - Theme system
- `app/src/main/java/app/artcraft/launcher/ui/LauncherScreen.kt` - Unchanged (already had install screen integration)

### Gradle
- `app/build.gradle.kts` - Added new dependencies

### GitHub Actions
- `.github/workflows/build-and-release.yml` - Enhanced auto-publish
- `.github/workflows/rust-tests.yml` - Unchanged
- `.github/workflows/android-lint.yml` - Unchanged
- `.github/workflows/nightly-build.yml` - **NEW** - Nightly builds
- `.github/workflows/manual-release.yml` - **NEW** - Manual releases

### Documentation
- `.github/CI_CD.md` - **NEW** - Comprehensive CI/CD documentation
- `CHANGES_SUMMARY.md` - **NEW** - This file

---

## 🎯 Build Verification

All builds have been tested and verified:

```bash
# ✅ Kotlin compilation
./gradlew compileDebugKotlin

# ✅ Rust bridge compilation
./gradlew :app:buildRust

# ✅ Full debug build with Rust
./gradlew assembleDebug -PbuildRust

# ✅ Full release build with Rust
./gradlew assembleRelease -PbuildRust
```

**Result**: All builds successful ✅

---

## 📊 Features Summary

### Rust Bridge Functions (Total: 13)

**Core Functions** (Existing):
1. `pickApk()` - Pick APK from release JSON
2. `compareVersions()` - Compare version strings
3. `verifySha256()` - Verify file checksum
4. `findSha256()` - Find checksum in SHA256SUMS
5. `bridgeVersion()` - Get bridge version

**APK Metadata** (Existing):
6. `nativeValidateApk()` - Validate APK file
7. `nativeIsApkCompatible()` - Check ABI compatibility
8. `nativeGetAbiFromApkName()` - Get ABI from filename

**New APK Utilities**:
9. `nativeExtractPackageName()` - Extract package name
10. `nativeExtractVersionCode()` - Extract version code
11. `nativeExtractVersionName()` - Extract version name
12. `nativeGetApkHash()` - Get SHA-256 hash
13. `nativeIsSplitApk()` - Check if split APK
14. `nativeGetSupportedAbis()` - Get supported ABIs

### UI Components

**Screens**:
- Splash Screen (with animations)
- Install Screen (with progress)
- Install Success Screen (with animation)
- Settings Screen (with theme selection)

**Animation Components**:
- 4 animation specs
- 4 animated modifiers
- 2 loading indicators
- 2 transition animations
- 1 easing function

---

## 🚀 How to Use

### For Developers

1. **Building**:
   ```bash
   # Build without Rust (Kotlin fallback)
   ./gradlew assembleDebug
   
   # Build with Rust bridge (recommended)
   ./gradlew assembleDebug -PbuildRust
   ```

2. **Running Tests**:
   ```bash
   # Android tests
   ./gradlew testDebugUnitTest
   
   # Rust tests
   cd rust && cargo test
   ```

3. **Creating a Release**:
   ```bash
   # Push a tag to trigger automatic release
   git tag v1.0.0
   git push origin v1.0.0
   
   # Or use manual workflow in GitHub Actions
   ```

### For Users

The app now features:
- Custom animated splash screen
- Smooth install screens with progress tracking
- Settings screen for customization
- Automatic updates checking
- Cache management
- Theme selection (Light/Dark/System)

---

## 📝 Notes

1. **Rust Bridge**: The Rust bridge is optional. If not compiled with `-PbuildRust`, the app will use Kotlin fallback implementations for all functions.

2. **Auto-Publishing**: To enable auto-publishing to `reality-prod/artcraft-launcher-android`, you need to:
   - Create a Personal Access Token (PAT) with `repo` scope
   - Add it as a GitHub secret named `REALITY_PROD_PAT`
   - The workflow will automatically create a PR on new releases

3. **Nightly Builds**: Nightly builds are created automatically every day at 2 AM UTC. These are pre-releases and may contain experimental features.

4. **Manual Releases**: Use the manual workflow to create releases with custom version numbers and messages.

---

## 🎉 Next Steps

The ArtCraft Launcher is now ready for:
1. Testing on various Android devices
2. Integration with `reality-prod/artcraft-launcher-android`
3. User feedback and bug reporting
4. Continuous improvement through the new CI/CD pipeline

---

**Status**: ✅ All tasks completed successfully
**Last Updated**: 2026-11-07
