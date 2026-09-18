// Top-level build file
plugins {
    id("com.android.application") version "8.5.2" apply false
    id("org.jetbrains.kotlin.android") version "1.9.24" apply false
    id("com.google.devtools.ksp") version "1.9.24-1.0.20" apply false
    // Used by :desktop only — the JVM Kotlin plugin (not the Android one above)
    // plus JetBrains' Compose Multiplatform, which brings Compose to a plain JVM app.
    kotlin("jvm") version "1.9.24" apply false
    id("org.jetbrains.compose") version "1.6.11" apply false
}

tasks.register("clean", Delete::class) {
    delete(rootProject.buildDir)
}
