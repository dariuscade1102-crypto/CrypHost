package com.cryptmc.app.server

import android.content.Context
import java.io.File
import java.util.zip.ZipInputStream

/**
 * Android has no system `java` binary. PaperMC/Fabric server jars need a real
 * JVM, so we ship a statically-linked ARM JRE as an asset, unpack it to
 * app-private storage on first run, and invoke its `bin/java` directly.
 *
 * Minimal / low-budget version: fails gracefully with a clear message
 * instead of hard-crashing if the zip is missing.
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
     *
     * Returns true if ready, false if the asset is missing (so the UI can show
     * a helpful message instead of crashing).
     */
    fun provisionIfNeeded(onProgress: (String) -> Unit = {}): Boolean {
        if (isProvisioned()) return true

        val abi = android.os.Build.SUPPORTED_ABIS.firstOrNull { it == "arm64-v8a" }
            ?: android.os.Build.SUPPORTED_ABIS.firstOrNull { it == "x86_64" }
            ?: android.os.Build.SUPPORTED_ABIS.firstOrNull { it == "armeabi-v7a" }
            ?: return false

        val assetName = when (abi) {
            "arm64-v8a" -> "jre-arm64.zip"
            "x86_64" -> "jre-x86_64.zip"
            else -> "jre-armv7.zip"
        }

        return try {
            onProgress("Unpacking embedded Java runtime ($abi)...")
            jreDir.mkdirs()

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
                onProgress("Failed to mark JRE binary executable")
                return false
            }
            onProgress("Java runtime ready.")
            true
        } catch (e: Exception) {
            onProgress("JRE asset missing or broken: ${e.message}. See MINIMAL.md for how to add it.")
            false
        }
    }
}
