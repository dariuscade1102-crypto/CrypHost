# CryptMc debugging guide

## Build the release APK

From this directory, use the repository workflow or a local Android SDK with Java 17:

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

The local wrapper requires `gradle/wrapper/gradle-wrapper.jar`. If that file is absent, install Gradle 8.9 and run `gradle wrapper --gradle-version 8.9` once.

## Capture a launch crash

```bash
adb logcat -c
adb shell am force-stop com.cryptmc.app
adb shell monkey -p com.cryptmc.app 1
adb logcat -d -v time | grep -E "AndroidRuntime|FATAL EXCEPTION|com.cryptmc.app|CryptMc"
```

To save the complete report:

```bash
adb logcat -d -v threadtime > cryptmc-logcat.txt
```

## Runtime assets

The dashboard and release build do not require the large runtime binaries. Starting a Minecraft server requires an ABI-specific JRE ZIP in `app/src/main/assets/` containing `bin/java` at its root. playit.gg tunneling additionally requires a licensed `libplayit_agent.so` under `app/src/main/jniLibs/<abi>/`.

The app now reports the exact missing asset instead of crashing silently. See `app/src/main/assets/README.md` and `app/src/main/jniLibs/README.md`.

## Release signing

Without release properties, `assembleRelease` falls back to the debug keystore for sideload testing. For a Play Store-ready build, configure these properties in `gradle.properties` or the matching CI secrets:

```properties
CRYPTMC_RELEASE_STORE_FILE=keystore/cryptmc-release.keystore.jks
CRYPTMC_RELEASE_STORE_PASSWORD=...
CRYPTMC_RELEASE_KEY_ALIAS=cryptmc
CRYPTMC_RELEASE_KEY_PASSWORD=...
```

Never commit passwords or a private release keystore.
