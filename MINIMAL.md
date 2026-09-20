# CryptMc — Minimal / Low-Budget Setup Guide

This branch is meant for students and low-budget developers who just want something that **compiles and runs** without hunting huge binaries first.

## What this minimal version prioritizes

- Compiles cleanly in Android Studio
- Local server hosting (no tunnel required at first)
- Live console
- Hotspot mode (phone becomes the Wi-Fi)
- Clear error messages instead of hard crashes when JRE / playit agent are missing

## Step-by-step to get a working APK (free)

### 1. Open the project
1. Clone or download this repo
2. Open the `CryptMc` folder in **Android Studio**
3. Let it download the Gradle wrapper + dependencies (this can take a few minutes the first time)

### 2. Get a free ARM64 JRE (required for the server to actually start)

**Easiest free option (Termux method):**

1. Install Termux from F-Droid (not Play Store)
2. In Termux run:
   ```bash
   pkg update && pkg install openjdk-17
   ```
3. The Java files live in something like:
   `/data/data/com.termux/files/usr`
4. Zip the whole `usr` folder (or at least the part that contains `bin/java`) and name it `jre-arm64.zip`
5. Put it here:
   ```
   CryptMc/app/src/main/assets/jre-arm64.zip
   ```

**Alternative (cleaner):**
- Download Azul Zulu or BellSoft Liberica JDK for **linux-aarch64**
- Extract it and zip the folder that contains `bin/java`
- Same destination as above

### 3. (Optional) playit.gg agent for public tunnel

Only needed if you want friends to join from outside your Wi-Fi.

1. Go to: https://github.com/playit-cloud/playit-agent/releases
2. Download the **linux-arm64** (or aarch64) binary
3. Rename it to `libplayit_agent.so`
4. Put it in:
   ```
   CryptMc/app/src/main/jniLibs/arm64-v8a/libplayit_agent.so
   ```

### 4. Build

In Android Studio:
- Click **Build → Build Bundle(s) / APK(s) → Build APK(s)**

Or from terminal:
```bash
./gradlew assembleDebug
```

The APK will be in:
`app/build/outputs/apk/debug/`

## What still needs work later

- Full AI Assistant (needs Anthropic key or MediaPipe model)
- Cross-device collaborator invites
- Desktop companion feature parity
- Proper Play Store release signing

## Tips for low-end phones

- Start with 1024–2048 MB RAM max
- Use Paper instead of heavy modded loaders
- Prefer Hotspot or Local mode over tunnel if your phone is weak

Good luck. You got this.
