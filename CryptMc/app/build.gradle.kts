plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.cryptmc.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cryptmc.app"
        minSdk = 26          // needed for reliable foreground service types
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"

        // ARM covers phones and most tablets; x86_64 covers x86 tablets and
        // Chromebooks running in tablet mode. Each ABI needs its own
        // embedded JRE zip and playit-agent binary — see README.
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    // Release signing reads from gradle.properties (or matching env vars),
    // never hardcoded here — see keystore/generate-release-keystore.sh and
    // gradle.properties.example at the repo root for how to set these up.
    // If they're absent (e.g. a debug-only checkout), release builds fall
    // back to the debug key so `./gradlew assembleRelease` still works;
    // that output is NOT suitable for Play Store upload.
    val storeFilePath = findProperty("CRYPTMC_RELEASE_STORE_FILE") as String?
    signingConfigs {
        if (storeFilePath != null) {
            create("release") {
                storeFile = rootProject.file(storeFilePath)
                storePassword = findProperty("CRYPTMC_RELEASE_STORE_PASSWORD") as String?
                keyAlias = findProperty("CRYPTMC_RELEASE_KEY_ALIAS") as String?
                keyPassword = findProperty("CRYPTMC_RELEASE_KEY_PASSWORD") as String?
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = if (storeFilePath != null) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    // The embedded JRE and playit-agent binaries live here (see README for
    // where to source them). They are NOT checked into this scaffold because
    // they're large per-ABI binaries you need to fetch yourself.
    sourceSets {
        getByName("main") {
            jniLibs.srcDirs("src/main/jniLibs")
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.lifecycle:lifecycle-service:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")

    // Compose
    implementation(platform("androidx.compose:compose-bom:2024.06.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3:material3-window-size-class:1.2.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // Networking (Modrinth API)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    ksp("com.squareup.moshi:moshi-kotlin-codegen:1.15.1")

    // Background work / process supervision
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    // Google Sign-In (Credential Manager is the current API — the old
    // GoogleSignInClient/One Tap APIs are deprecated as of 2024).
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // Encrypted local storage for RCON/admin passwords — backed by the
    // Android Keystore, never plaintext SharedPreferences.
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // On-device AI ("downloadable" mode, alongside the Anthropic API mode
    // above) — runs a downloaded .task model bundle fully locally, no
    // network/key at inference time. See ai/OnDeviceAiEngine.kt.
    implementation("com.google.mediapipe:tasks-genai:0.10.14")
}
