# CryptMc — Low-Budget Setup Guide (Student / Gamer edition)

You don't need to pay for anything to get this running.

## What you need

| Item | Required? | Cost | Notes |
|------|-----------|------|-------|
| Android Studio | Yes | Free | Download from developer.android.com |
| ARM64 JRE zip | Yes (to actually start a server) | Free | See steps below |
| playit agent | Only for public tunnel | Free | Optional at first |
| Google OAuth client | Only for Sign-In | Free | Optional |

## 1. Get the free JRE (most important)

### Method A — Termux (easiest on phone)

1. Install **Termux** from F-Droid (recommended) or Play Store
2. Open Termux and run:
   ```bash
   pkg update && pkg install openjdk-17
   ```
3. The files are usually under `/data/data/com.termux/files/usr`
4. Zip that folder (or at least the parts containing `bin/java`) and name the zip:
   `jre-arm64.zip`
5. Place it here in the project:
   ```
   CryptMc/app/src/main/assets/jre-arm64.zip
   ```

### Method B — Download on PC (cleaner)

1. Go to Azul Zulu or BellSoft Liberica
2. Download the **linux-aarch64** JDK 17 or 21
3. Extract it
4. Zip the folder that contains `bin/java` → name it `jre-arm64.zip`
5. Put it in the same assets folder as above

## 2. (Optional) playit.gg agent for friends outside your Wi-Fi

1. Open https://github.com/playit-cloud/playit-agent/releases
2. Download the linux **arm64 / aarch64** binary
3. Rename it exactly to: `libplayit_agent.so`
4. Put it here:
   ```
   CryptMc/app/src/main/jniLibs/arm64-v8a/libplayit_agent.so
   ```

## 3. Build the app

1. Open the `CryptMc` folder in Android Studio
2. Let it sync / download dependencies
3. Build → Build APK(s)

Or in terminal:
```bash
cd CryptMc
./gradlew assembleDebug
```

APK location:
`app/build/outputs/apk/debug/app-debug.apk`

## Recommended first test (no tunnel needed)

1. Install the APK on your phone
2. Create a server
3. Accept the EULA
4. Use **Hotspot** or **Local** mode
5. Start the server
6. On another device on the same network (or connected to your hotspot), Direct Connect to the IP shown

## Tips for weak phones / low RAM

- Set max RAM to 1536 or 2048 MB
- Use Paper 1.20.x or 1.21 (lighter than heavy modpacks)
- Close other apps before starting the server
- Prefer Hotspot mode over public tunnel if your connection is bad

## Still stuck?

Open an issue on the repo or ask in the discussion. Include:
- What phone / Android version
- Exact error message from the console
- Whether you added the JRE zip or not

You got this.
