package com.cryptmc.app.network

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Requirement #5 (mod & plugin installer) + the automated half of
 * requirement #3 (Java/Bedrock crossplay via Geyser/Floodgate).
 */
class ModrinthRepository {

    private val api: ModrinthApi by lazy {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        Retrofit.Builder()
            .baseUrl("https://api.modrinth.com/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ModrinthApi::class.java)
    }

    suspend fun search(query: String, projectType: String, mcVersion: String): List<ModrinthHit> {
        val facets = """[["project_type:$projectType"],["versions:$mcVersion"]]"""
        return withContext(Dispatchers.IO) {
            api.search(query = query, facets = facets).hits
        }
    }

    /**
     * Downloads the primary file of the newest compatible version straight
     * into the server's plugins/ (Paper) or mods/ (Fabric) folder — the
     * "one-tap install" from requirement #5.
     */
    suspend fun installToServer(
        projectSlugOrId: String,
        mcVersion: String,
        loader: String,           // "paper", "fabric", ...
        destinationDir: File
    ): File = withContext(Dispatchers.IO) {
        val versions = api.projectVersions(
            projectSlugOrId, gameVersions = """["$mcVersion"]""", loaders = """["$loader"]"""
        )
        val version = versions.firstOrNull()
            ?: error("No version of $projectSlugOrId compatible with $loader $mcVersion")
        val file = version.files.firstOrNull { it.primary } ?: version.files.first()

        destinationDir.mkdirs()
        val outFile = File(destinationDir, file.filename)
        downloadFile(file.url, outFile)
        outFile
    }

    /**
     * Convenience wrapper for requirement #3: fetches and installs
     * GeyserMC + Floodgate together so mobile/console Bedrock players can
     * join a Java server world without the host touching any config.
     */
    suspend fun installBedrockCrossplay(mcVersion: String, pluginsDir: File) {
        installToServer("geyser", mcVersion, "paper", pluginsDir)
        installToServer("floodgate", mcVersion, "paper", pluginsDir)
        // Geyser writes its own config.yml with sane defaults (Bedrock port
        // 19132/udp) on first boot — no further host action required.
    }

    private fun downloadFile(url: String, outFile: File) {
        val client = OkHttpClient()
        val request = okhttp3.Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Download failed: HTTP ${response.code}" }
            outFile.outputStream().use { out ->
                response.body?.byteStream()?.copyTo(out)
                    ?: error("Empty response body for $url")
            }
        }
    }
}
