plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.artcraft.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.artcraft.launcher"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    // Release signing comes from env vars so no secrets live in the repo.
    // Without them, assembleRelease falls back to the debug key so the APK still installs.
    val ksPath = System.getenv("ARTCRAFT_KEYSTORE")
    signingConfigs {
        if (ksPath != null) {
            create("release") {
                storeFile = file(ksPath)
                storePassword = System.getenv("ARTCRAFT_KS_PASS")
                keyAlias = System.getenv("ARTCRAFT_KEY_ALIAS")
                keyPassword = System.getenv("ARTCRAFT_KEY_PASS")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (ksPath != null) signingConfigs.getByName("release")
                            else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    buildFeatures { compose = true }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}

dependencies {
    val bom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(bom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    
    // Lottie animation support
    implementation("com.airbnb.android:lottie-compose:6.4.0")
    
    // Coil for image loading
    implementation("io.coil-kt:coil-compose:2.6.0")
    
    // Accompanist for additional Compose utilities
    implementation("com.google.accompanist:accompanist-navigation-animation:0.34.0")
    
    debugImplementation("androidx.compose.ui:ui-tooling")
}

// Optional Rust bridge (rust/bridge). Build with:  ./gradlew assembleRelease -PbuildRust
// Needs `cargo install cargo-ndk`, the Android targets (rustup target add aarch64-linux-android
// armv7-linux-androideabi) and ANDROID_NDK_HOME. Without it the app uses the Kotlin fallback.
val buildRust by tasks.registering(Exec::class) {
    workingDir = file("../rust")
    commandLine(
        "cargo", "ndk", "-t", "arm64-v8a", "-t", "armeabi-v7a",
        "-o", file("src/main/jniLibs").absolutePath, "build", "--release",
    )
}
if (providers.gradleProperty("buildRust").isPresent) {
    tasks.named("preBuild") { dependsOn(buildRust) }
}
