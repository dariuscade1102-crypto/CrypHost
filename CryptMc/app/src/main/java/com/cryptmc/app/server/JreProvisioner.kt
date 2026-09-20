package com.cryptmc.app.server

import android.content.Context
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Android has no system `java` binary. PaperMC/Fabric server jars need a real
 * JVM, so we ship a statically-linked ARM JRE (Termux's `openjdk-17` package,
 * or a Liberica/Zulu "Alpine-like" musl/glibc ARM build both work) as an
 * asset, unpack it to app-private storage on first run, and invoke its
 * `bin/java` directly via ProcessBuilder.
 *
 * This class does NOT bundle the JRE itself — that's a ~180MB binary you
 * need to fetch once and drop into app/src/main/assets/jre-arm64.zip.
 * See README.md "Sourcing the embedded JRE" for exact steps and licenses.
 */
class JreProvisioner(private val context: Context) {

    private val jreDir: File
        get() = File(context.filesDir, "jre")

    val javaBinary: File
        get() = File(jreDir, "bin/java")

    fun isProvisioned(): Boolean = javaBinary.exists() && javaBinary.canExecute()

    /**
     * Unpacks the bundled JRE zip (matched to the device ABI) into app-private
     * storage and marks the java binary executable. Safe to call repeatedly;
     * no-ops if already provisioned.
     */
    fun provisionIfNeeded(onProgress: (String) -> Unit = {}) {
        if (isProvisioned()) return

        // Order matters: prefer 64-bit native ABIs over 32-bit or emulated
        // ones. Some x86 tablets/Chromebooks report arm ABIs in this list
        // purely for compatibility-layer translation (libhoudini/ARC++),
        // which won't work for a real JVM — so x86_64 is checked ahead of
        // armeabi-v7a to avoid picking an ABI Android can't natively execute.
        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" }
            ?: android.os.Build.SUPPORTED_ABIS.firstOrNull { it == "x86_64" }
            ?: android.os.Build.SUPPORTED_ABIS.firstOrNull { it == "armeabi-v7a" }
            ?: error("Unsupported ABI: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")

        val assetName = when (abi) {
            "arm64-v8a" -> "jre-arm64.zip"
            "x86_64" -> "jre-x86_64.zip"
            else -> "jre-armv7.zip"
        }

        onProgress("Unpacking embedded Java runtime ($abi)...")
        jreDir.mkdirs()

        if (!context.assets.list("").orEmpty().contains(assetName)) {
            error("Missing $assetName. Add an ABI-matched JRE ZIP to app/src/main/assets; see assets/README.md")
        }

        context.assets.open(assetName).use { input ->
            ZipInputStream(input).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val outFile = File(jreDir, entry.name)
                    if (entry.isDirectory) {
                        outFile.mkdirs()
                    } else {
                        outFile.parentFile?.mkdirs()
                        outFile.outputStream().use { out -> zip.copyTo(out) }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        if (!javaBinary.setExecutable(true, false)) {
            error("Failed to mark JRE binary executable — check filesystem mount options")
        }
        onProgress("Java runtime ready.")
    }
}
