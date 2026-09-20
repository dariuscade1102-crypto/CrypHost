package com.cryptmc.app.network

import com.cryptmc.app.data.ServerLoader
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File

/**
 * The in-app "pick a version, download it" flow the README flagged as
 * missing for requirement #3. Covers Paper (build API), Fabric (meta API +
 * installer jar), and Vanilla (Mojang manifest). NeoForge/Purpur/Bedrock
 * stay sideload-only — see the class docs on ServerJarApi.kt for why.
 *
 * Wire this into SoftwareTab.kt's version picker: call [resolveDownloadUrl],
 * show the result, then [downloadJar] with a progress callback for the UI.
 */
class ServerJarDownloader(
    private val okHttpClient: OkHttpClient = OkHttpClient()
) {
    private val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()

    private fun <T> retrofitFor(baseUrl: String, service: Class<T>): T =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(service)

    private val paperApi by lazy { retrofitFor("https://api.papermc.io/", PaperApi::class.java) }
    private val fabricApi by lazy { retrofitFor("https://meta.fabricmc.net/", FabricMetaApi::class.java) }
    private val mojangApi by lazy { retrofitFor("https://piston-meta.mojang.com/", MojangManifestApi::class.java) }

    sealed class Resolved(open val fileName: String, open val downloadUrl: String) {
        data class Paper(val build: Int, override val fileName: String, override val downloadUrl: String) :
            Resolved(fileName, downloadUrl)
        data class Fabric(val loaderVersion: String, val installerVersion: String, override val downloadUrl: String) :
            Resolved("fabric-server-launch.jar", downloadUrl)
        data class Vanilla(override val fileName: String, override val downloadUrl: String, val sha1: String?) :
            Resolved(fileName, downloadUrl)
    }

    /** Picks the newest stable build/loader for [minecraftVersion] and returns a ready-to-download URL. */
    suspend fun resolveDownloadUrl(loader: ServerLoader, minecraftVersion: String): Result<Resolved> =
        withContext(Dispatchers.IO) {
            runCatching {
                when (loader) {
                    ServerLoader.PAPER -> {
                        val builds = paperApi.builds(minecraftVersion).builds
                        val latest = builds.maxOrNull() ?: error("No Paper builds for $minecraftVersion")
                        val fileName = "paper-$minecraftVersion-$latest.jar"
                        val url = "https://api.papermc.io/v2/projects/paper/versions/$minecraftVersion/builds/$latest/downloads/$fileName"
                        Resolved.Paper(latest, fileName, url)
                    }
                    ServerLoader.FABRIC -> {
                        val loaderVersion = fabricApi.loaderVersions(minecraftVersion)
                            .firstOrNull { it.loader.stable }?.loader?.version
                            ?: error("No stable Fabric loader for $minecraftVersion")
                        val installerVersion = fabricApi.installerVersions()
                            .firstOrNull { it.stable }?.version
                            ?: error("No stable Fabric installer found")
                        val url = "https://meta.fabricmc.net/v2/versions/loader/$minecraftVersion/$loaderVersion/$installerVersion/server/jar"
                        Resolved.Fabric(loaderVersion, installerVersion, url)
                    }
                    ServerLoader.VANILLA -> {
                        val entry = mojangApi.manifest().versions.firstOrNull { it.id == minecraftVersion }
                            ?: error("Unknown Minecraft version $minecraftVersion")
                        val meta = fetchJson(entry.url, MojangVersionMeta::class.java)
                        val server = meta.downloads.server ?: error("No server.jar published for $minecraftVersion")
                        // Mojang's manifest hands back a sha1 for exactly
                        // this reason — it was being fetched into
                        // MojangDownloadEntry and then never looked at again.
                        Resolved.Vanilla("server.jar", server.url, server.sha1)
                    }
                    else -> error(
                        "${loader.displayName} has no download API — user must sideload the jar via " +
                            "Storage Access Framework (see README, requirement #3)."
                    )
                }
            }
        }

    /** Streams the resolved jar to `<workingDir>/<fileName>`, reporting 0f..1f progress. */
    suspend fun downloadJar(
        resolved: Resolved,
        destinationDir: File,
        onProgress: (Float) -> Unit = {}
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            destinationDir.mkdirs()
            val outFile = File(destinationDir, resolved.fileName)
            val request = Request.Builder().url(resolved.downloadUrl).build()
            val digest = java.security.MessageDigest.getInstance("SHA-1")
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
                val body = response.body ?: error("Empty response body")
                val total = body.contentLength().takeIf { it > 0 }
                var written = 0L
                body.byteStream().use { input ->
                    outFile.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            output.write(buffer, 0, read)
                            digest.update(buffer, 0, read)
                            written += read
                            total?.let { onProgress(written.toFloat() / it) }
                        }
                    }
                }
            }
            // Mojang publishes a sha1 for the vanilla server.jar specifically
            // so a corrupted/tampered download can be caught before it's
            // ever handed to `java -jar`. This was fetched (MojangDownloadEntry.sha1)
            // and then discarded — nothing ever compared it against what
            // actually landed on disk.
            if (resolved is Resolved.Vanilla && resolved.sha1 != null) {
                val actual = digest.digest().joinToString("") { "%02x".format(it) }
                if (!actual.equals(resolved.sha1, ignoreCase = true)) {
                    outFile.delete()
                    error("Downloaded server.jar failed checksum verification (expected ${resolved.sha1}, got $actual)")
                }
            }
            outFile
        }
    }

    private fun <T> fetchJson(url: String, clazz: Class<T>): T {
        val request = Request.Builder().url(url).build()
        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: error("Empty response for $url")
            return moshi.adapter(clazz).fromJson(body) ?: error("Could not parse response from $url")
        }
    }
}
