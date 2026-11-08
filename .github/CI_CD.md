# ArtCraft Launcher CI/CD Documentation

## Overview

This repository uses GitHub Actions for continuous integration and deployment. The workflows automate building, testing, and releasing the ArtCraft Launcher Android app.

## Workflows

### 1. Build and Release (`build-and-release.yml`)

**Trigger**: Automatically runs on:
- Push to `main` branch
- Push of tags matching `v*` pattern (e.g., `v1.0.0`)
- Pull requests to `main` branch
- Manual trigger via workflow_dispatch

**Jobs**:
- **build**: Builds the APK without Rust bridge
- **build-with-rust**: Builds the APK with Rust bridge for enhanced performance
- **test**: Runs Android and Rust unit tests
- **release**: Creates a GitHub release when a tag is pushed
- **publish-to-reality-prod**: Automatically creates a PR to `reality-prod/artcraft-launcher-android` with the new APK

**Required Secrets**:
- `REALITY_PROD_PAT`: Personal Access Token with write access to `reality-prod/artcraft-launcher-android` repository

**How to Use**:
1. To create a release, push a tag:
   ```bash
   git tag v1.0.0
   git push origin v1.0.0
   ```
2. The workflow will automatically build, test, create a GitHub release, and submit a PR to reality-prod

### 2. Rust Bridge Tests (`rust-tests.yml`)

**Trigger**:
- Push to `main` branch (changes in `rust/**`)
- Pull requests to `main` branch (changes in `rust/**`)
- Daily at midnight UTC
- Manual trigger

**Jobs**:
- **test**: Runs all Rust unit tests
- **clippy**: Runs Clippy linter with warnings as errors
- **audit**: Runs security audit on Rust dependencies

**Features**:
- Caching for faster builds
- Coverage reporting via Codecov (requires `CODECOV_TOKEN` secret)

### 3. Android Lint and Format (`android-lint.yml`)

**Trigger**:
- Push to `main` branch (changes in `app/**` or `*.gradle*`)
- Pull requests to `main` branch (changes in `app/**` or `*.gradle*`)
- Manual trigger

**Jobs**:
- **lint**: Runs Android lint checks
- **format**: Checks Kotlin formatting with spotlessCheck
- **ktlint**: Runs KTLint for additional code style checks

### 4. Nightly Build (`nightly-build.yml`)

**Trigger**:
- Daily at 2 AM UTC
- Manual trigger

**Features**:
- Builds latest code from `main` branch
- Includes Rust bridge
- Creates a pre-release with nightly tag (e.g., `nightly-20241107`)
- APKs are available as artifacts for 30 days

### 5. Manual Release (`manual-release.yml`)

**Trigger**: Manual only via workflow_dispatch

**Inputs**:
- `version`: Version to release (e.g., `v2.1.0`) - **Required**
- `message`: Release message/changelog - Optional
- `publish-to-reality-prod`: Whether to publish to reality-prod - Default: `true`

**How to Use**:
1. Go to Actions tab in GitHub
2. Select "Manual Release" workflow
3. Click "Run workflow"
4. Enter version number (e.g., `v2.1.0`)
5. Optionally add release notes
6. Toggle publish-to-reality-prod if needed
7. Click "Run workflow"

## Setup Instructions

### Prerequisites

1. **Android SDK**: The workflows use Android SDK 35 with build tools 35.0.0
2. **Rust**: Stable toolchain with Android targets (`aarch64-linux-android`, `armv7-linux-androideabi`)
3. **cargo-ndk**: Required for building Rust code for Android

### Required Secrets

Create the following repository secrets in GitHub:

1. **REALITY_PROD_PAT** (Required for auto-publishing)
   - Go to Settings > Secrets > Actions
   - Create a new repository secret
   - Name: `REALITY_PROD_PAT`
   - Value: Personal Access Token with `repo` scope from a user with write access to `reality-prod/artcraft-launcher-android`
   - To create a PAT: Go to https://github.com/settings/tokens > Generate new token > Select `repo` scope

2. **CODECOV_TOKEN** (Optional for coverage reporting)
   - Create a token at https://codecov.io
   - Add as a secret named `CODECOV_TOKEN`

## Building Locally

### Prerequisites

```bash
# Install Android SDK and NDK
# Set ANDROID_NDK_HOME environment variable

# Install Rust
curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
rustup target add aarch64-linux-android armv7-linux-androideabi

# Install cargo-ndk
cargo install cargo-ndk
```

### Build Commands

```bash
# Build without Rust bridge
./gradlew assembleDebug

# Build with Rust bridge
./gradlew assembleDebug -PbuildRust

# Build release with Rust bridge
./gradlew assembleRelease -PbuildRust

# Run tests
./gradlew testDebugUnitTest

# Run lint
./gradlew lintDebug
```

## Testing Workflows Locally

Use the `act` tool to test workflows locally:

```bash
# Install act
brew install act

# Run a workflow
act -j build -W .github/workflows/build-and-release.yml
```

## Versioning

This project follows semantic versioning:
- `v1.0.0` - Major release
- `v1.1.0` - Minor release with new features
- `v1.0.1` - Patch release with bug fixes

Nightly builds use date-based versioning: `nightly-YYYYMMDD`

## Publishing to reality-prod

The automated workflow will create a PR to `reality-prod/artcraft-launcher-android` when:
1. A tag is pushed (e.g., `v1.0.0`)
2. The build and tests pass
3. The `REALITY_PROD_PAT` secret is configured

The PR will be created with:
- Branch: `auto-update-launcher-<version>`
- Title: `chore: Update ArtCraft Launcher to v<version>`
- Body: Includes release notes and changelog link

To manually merge the PR:
1. Go to `reality-prod/artcraft-launcher-android`
2. Find the PR created by github-actions[bot]
3. Review changes
4. Merge the PR

## Customization

### Environment Variables

- `GRADLE_OPTS`: Customize Gradle daemon settings
- `ANDROID_NDK_HOME`: Path to Android NDK

### Caching

Workflows use caching for:
- Gradle dependencies and caches
- Rust registry and git caches
- Build outputs

Cache keys are based on:
- OS
- Hash of relevant files (gradle, cargo, source code)

### Matrix Building

The workflows currently build for:
- `arm64-v8a` (64-bit ARM)
- `armeabi-v7a` (32-bit ARM)

To add more targets, update the `cargo ndk` command in the workflows.

## Troubleshooting

### Common Issues

1. **NDK not found**: Ensure `ANDROID_NDK_HOME` is set correctly
2. **Rust targets not installed**: Run `rustup target add aarch64-linux-android armv7-linux-androideabi`
3. **Java version mismatch**: Ensure JDK 17 is installed
4. **Permission errors**: Ensure the `REALITY_PROD_PAT` has the correct permissions

### Debugging

Add `-Dorg.gradle.debug=true` to `GRADLE_OPTS` for verbose Gradle output.

## Contact

For issues with the CI/CD workflows:
- Check the Actions tab for error logs
- Review the workflow files in `.github/workflows/`
- Ensure all required secrets are configured

## Workflow File Structure

```
.github/
├── workflows/
│   ├── build-and-release.yml    # Main CI/CD pipeline
│   ├── rust-tests.yml           # Rust testing and linting
│   ├── android-lint.yml         # Android linting and formatting
│   ├── nightly-build.yml        # Nightly builds
│   └── manual-release.yml       # Manual release workflow
└── CI_CD.md                     # This documentation
```
