import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("org.jetbrains.compose")
}

/**
 * Desktop companion — Windows/macOS/Linux, packaged with `./gradlew
 * :desktop:packageDistributionForCurrentOS` (produces an .msi/.dmg/.deb via
 * jpackage, which bundles its OWN JRE — unlike the Android app, there's no
 * ARM JRE asset to source manually here; jpackage handles it).
 *
 * This is a companion, not a fork of the Android module: it runs the same
 * kind of Paper/Fabric/Vanilla server process directly (desktops already
 * have `java` findable on PATH or via JAVA_HOME, no JreProvisioner-style
 * extraction needed), and can point at the SAME `workingDir` a phone install
 * uses if that folder is reachable (e.g. synced via Syncthing, a shared
 * network drive, or literally the same machine) — see DesktopServerManager's
 * class doc for what that does and doesn't give you today.
 *
 * `data/`, `server/`, and `network/` here are intentionally NOT shared
 * source with `:app` yet — genuine code sharing needs promoting those
 * packages into a `:core` Kotlin Multiplatform module (commonMain) that
 * both `:app` (androidMain) and `:desktop` (jvmMain) depend on. That's a
 * real refactor (Android-only APIs like WorkManager/EncryptedSharedPreferences
 * in server/ServerScheduler.kt and auth/SecureCredentialStore.kt would need
 * expect/actual splits), not something to do silently as a side effect of
 * "add desktop support" — flagging it here rather than skipping it.
 */

kotlin {
    jvmToolchain(17)
}

compose.desktop {
    application {
        mainClass = "com.cryptmc.desktop.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "CryptMc"
            packageVersion = "0.1.0"
            description = "Local Minecraft server host — desktop companion"
        }
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.8.1")

    // Networking — same libraries/versions as :app for the mod/jar downloader ports.
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
}
