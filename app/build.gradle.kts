plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "app.artcraft.launcher"
    compileSdk = 35

    defaultConfig {
        // Own id (not under artcraft.app, a domain nobody here owns). Namespace stays app.artcraft.launcher.
        applicationId = "io.github.realityprod.artcraftlauncher"
        minSdk = 26
        targetSdk = 35
        // CI passes the tag: -PappVersion=0.2.0 -PappVersionCode=20099
        versionCode = providers.gradleProperty("appVersionCode").map(String::toInt).getOrElse(1)
        versionName = providers.gradleProperty("appVersion").getOrElse("0.1.0-dev")
    }

    // One APK per CPU type: the launcher's own release picks the right one by name.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
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
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
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
