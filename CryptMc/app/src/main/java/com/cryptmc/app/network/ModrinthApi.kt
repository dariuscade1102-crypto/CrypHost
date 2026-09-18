package com.cryptmc.app.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Thin client for the public Modrinth API (docs.modrinth.com/api-spec). */
interface ModrinthApi {

    @GET("v2/search")
    suspend fun search(
        @Query("query") query: String,
        @Query("facets") facets: String,   // e.g. [["project_type:plugin"],["versions:1.20.4"]]
        @Query("limit") limit: Int = 20
    ): SearchResponse

    @GET("v2/project/{idOrSlug}/version")
    suspend fun projectVersions(
        @Path("idOrSlug") idOrSlug: String,
        @Query("game_versions") gameVersions: String? = null,
        @Query("loaders") loaders: String? = null
    ): List<ModrinthVersion>
}

@JsonClass(generateAdapter = true)
data class SearchResponse(
    val hits: List<ModrinthHit>
)

@JsonClass(generateAdapter = true)
data class ModrinthHit(
    @Json(name = "project_id") val projectId: String,
    val slug: String,
    val title: String,
    val description: String,
    @Json(name = "project_type") val projectType: String, // "mod" | "plugin" | "modpack"
    @Json(name = "icon_url") val iconUrl: String?,
    val downloads: Int
)

@JsonClass(generateAdapter = true)
data class ModrinthVersion(
    val id: String,
    @Json(name = "version_number") val versionNumber: String,
    @Json(name = "game_versions") val gameVersions: List<String>,
    val loaders: List<String>,
    val files: List<ModrinthFile>
)

@JsonClass(generateAdapter = true)
data class ModrinthFile(
    val url: String,
    val filename: String,
    val primary: Boolean
)
