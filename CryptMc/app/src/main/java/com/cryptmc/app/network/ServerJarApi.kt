package com.cryptmc.app.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path

/** PaperMC API (papermc.io/api) — covers Paper and Purpur's sibling API shape. */
interface PaperApi {
    @GET("v2/projects/paper/versions/{version}")
    suspend fun builds(@Path("version") version: String): PaperBuildsResponse
}

@JsonClass(generateAdapter = true)
data class PaperBuildsResponse(val builds: List<Int>)

/** Fabric meta API (meta.fabricmc.net) — installer + loader + game version matrix. */
interface FabricMetaApi {
    @GET("v2/versions/loader/{gameVersion}")
    suspend fun loaderVersions(@Path("gameVersion") gameVersion: String): List<FabricLoaderEntry>

    @GET("v2/versions/installer")
    suspend fun installerVersions(): List<FabricInstallerEntry>
}

@JsonClass(generateAdapter = true)
data class FabricLoaderEntry(
    val loader: FabricLoaderVersion
)

@JsonClass(generateAdapter = true)
data class FabricLoaderVersion(val version: String, val stable: Boolean)

@JsonClass(generateAdapter = true)
data class FabricInstallerEntry(val version: String, val stable: Boolean)

/**
 * Mojang's version manifest — used only to resolve "1.21.1" to a server.jar
 * download URL for Vanilla. NeoForge has no simple REST equivalent to Fabric's
 * meta API (their installer is a self-extracting jar); NeoForge downloads stay
 * a manual sideload via Storage Access Framework, same as Bedrock Dedicated
 * Server per the README.
 */
interface MojangManifestApi {
    @GET("mc/game/version_manifest_v2.json")
    suspend fun manifest(): MojangManifest
}

@JsonClass(generateAdapter = true)
data class MojangManifest(val versions: List<MojangManifestVersion>)

@JsonClass(generateAdapter = true)
data class MojangManifestVersion(
    val id: String,
    val url: String,
    @Json(name = "type") val type: String
)

@JsonClass(generateAdapter = true)
data class MojangVersionMeta(val downloads: MojangDownloads)

@JsonClass(generateAdapter = true)
data class MojangDownloads(val server: MojangDownloadEntry?)

@JsonClass(generateAdapter = true)
data class MojangDownloadEntry(val url: String, val sha1: String, val size: Long)
