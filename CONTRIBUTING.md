# Contributing to ArtCraft Launcher

Thank you for your interest in contributing to the ArtCraft Launcher! We welcome contributions from everyone.

## How to Contribute

### Reporting Issues

If you find a bug or have a feature request, please [open an issue](https://github.com/reality-prod/artcraft-launcher-android/issues) on GitHub. When reporting an issue:

1. Check if the issue already exists
2. Provide a clear title and description
3. Include steps to reproduce
4. Add relevant labels
5. Include screenshots if applicable

### Code Contributions

We accept contributions via Pull Requests (PRs). Here's how to get started:

1. **Fork the repository** on GitHub
2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/your-username/artcraft-launcher-android.git
   cd artcraft-launcher-android
   ```
3. **Create a feature branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```
   Use a descriptive name that starts with:
   - `feature/` for new features
   - `fix/` for bug fixes
   - `docs/` for documentation updates
   - `refactor/` for code refactoring
   - `chore/` for maintenance tasks

4. **Make your changes**
5. **Run tests**:
   ```bash
   # Android tests
   ./gradlew testDebugUnitTest
   
   # Rust tests
   cd rust && cargo test
   
   # All tests
   ./gradlew test
   ```
6. **Run linting**:
   ```bash
   ./gradlew lintDebug
   ./gradlew spotlessCheck
   ```
7. **Commit your changes** with clear, atomic commits:
   ```bash
   git commit -m "Add new feature X"
   ```
   Follow [Conventional Commits](https://www.conventionalcommits.org/) format:
   - `feat: Add new feature`
   - `fix: Fix bug`
   - `docs: Update documentation`
   - `style: Fix formatting`
   - `refactor: Refactor code`
   - `test: Add tests`
   - `chore: Maintenance task`

8. **Push to your fork**:
   ```bash
   git push origin feature/your-feature-name
   ```
9. **Open a Pull Request** on GitHub with:
   - A clear title and description
   - Reference to any related issues
   - Screenshots or videos if applicable
   - Testing instructions

### Code Review Process

1. All PRs require at least one approval from a maintainer
2. CI checks must pass (build, tests, lint)
3. Code must follow the existing style and conventions
4. New features should include tests
5. Documentation should be updated for significant changes

## Development Setup

### Prerequisites

- **Java JDK 17**: Required for Android development
  ```bash
  # On macOS
  brew install openjdk@17
  
  # On Ubuntu
  sudo apt install openjdk-17-jdk
  ```

- **Android SDK 35**: Install via Android Studio or:
  ```bash
  # Install Android SDK
  sudo apt install android-sdk
  
  # Install specific packages
  sdkmanager "platforms;android-35"
  sdkmanager "build-tools;35.0.0"
  ```

- **Android NDK** (optional, for Rust bridge):
  ```bash
  # Via Android Studio
  # Or manually:
  sdkmanager ndk
  ```

- **Rust** (optional, for Rust bridge):
  ```bash
  curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
  source $HOME/.cargo/env
  
  # Add Android targets
  rustup target add aarch64-linux-android armv7-linux-androideabi
  
  # Install cargo-ndk
  cargo install cargo-ndk
  ```

### Building the Project

#### Without Rust Bridge (Recommended for quick testing)
```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Install debug APK
./gradlew installDebug
```

#### With Rust Bridge (For better performance)
```bash
# Set NDK path
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk/<version>

# Build with Rust
./gradlew assembleRelease -PbuildRust
```

### Project Structure

```
.
├── .github/                   # GitHub workflows
├── app/                       # Android application
│   ├── src/main/java/...      # Kotlin code
│   ├── src/main/res/          # Resources
│   └── build.gradle.kts       # App build config
├── rust/                      # Rust bridge
│   ├── bridge/                # JNI bridge crate
│   └── Cargo.toml             # Workspace manifest
├── .gitignore                 # Git ignore rules
├── build.gradle.kts           # Root build config
├── settings.gradle.kts        # Settings
└── README.md                  # Project documentation
```

## Code Style

### Kotlin Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use 4 spaces for indentation
- Maximum line length: 100 characters (flexible)
- Prefer `val` over `var` when possible
- Use named arguments for clarity
- Follow the existing patterns in the codebase

### Rust Style

- Follow [Rust coding conventions](https://doc.rust-lang.org/1.0.0/style/)
- Use `snake_case` for variables and functions
- Use `PascalCase` for types
- Use `SCREAMING_SNAKE_CASE` for constants
- Deny unsafe code (`#![deny(unsafe_code)]`)
- Catch panics in JNI exports

### Compose Style

- Use Material3 components
- Follow [Compose best practices](https://developer.android.com/jetpack/compose/best-practices)
- Use descriptive modifier chains
- Prefer `@Composable` functions over complex nested lambdas
- Use `remember` for state that should survive recomposition

## Testing

### Android Tests

We use JUnit 5 for Android tests. Place tests in the `test` directory:

```kotlin
// Example test
class ExampleTest {
    @Test
    fun testSomething() {
        // Test logic
    }
}
```

Run tests:
```bash
./gradlew testDebugUnitTest
```

### Rust Tests

Rust tests are in the same file as the code or in a `tests` module:

```rust
#[cfg(test)]
mod tests {
    use super::*;
    
    #[test]
    fn test_something() {
        // Test logic
    }
}
```

Run tests:
```bash
cd rust && cargo test
```

### UI Tests

We're working on adding UI tests. Currently, manual testing is recommended:

1. Run the app on an emulator or device
2. Test various scenarios:
   - App scanning and display
   - APK download and installation
   - Settings changes
   - Theme switching
   - Error cases

## Documentation

- Update the `README.md` for significant changes
- Add inline code comments for complex logic
- Use KDoc for Kotlin functions and classes
- Use rustdoc for Rust items
- Update `CHANGELOG.md` for each release

## License

By contributing to this project, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).

## Code of Conduct

We expect all contributors to follow our [Code of Conduct](CODE_OF_CONDUCT.md). Be respectful and inclusive.

## Recognition

All contributions are appreciated and will be recognized in the `CONTRIBUTORS.md` file (if we add one).

## Need Help?

If you have questions about contributing:
1. Check this document
2. Check the `README.md`
3. Open an issue on GitHub
4. Join our community discussions (if available)

Thank you for contributing to ArtCraft Launcher!
