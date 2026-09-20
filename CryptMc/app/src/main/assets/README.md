# CryptMc runtime assets

The dashboard APK can build without these files, but starting a Minecraft server requires an ABI-matched embedded Java runtime:

- `jre-arm64.zip` for `arm64-v8a`
- `jre-armv7.zip` for `armeabi-v7a`
- `jre-x86_64.zip` for `x86_64`

Each ZIP must contain `bin/java` at its root. Do not download arbitrary binaries into this directory; obtain a redistributable JRE build whose license permits embedding. The app reports a clear missing-asset message if the selected runtime is not present.
