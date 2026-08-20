package com.lmelp.mobile.data.remote

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Implémentation OkHttp de GitHubReleaseApi (issue #118).
 * Consomme l'API GitHub Releases pour résoudre les download URLs des assets
 * publiés sur la release data-latest (lmelp.db + metadata.json).
 */
class OkHttpGitHubReleaseApi(
    private val releaseApiBaseUrl: String =
        "https://api.github.com/repos/castorfou/lmelp-mobile/releases/tags/data-latest",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) : GitHubReleaseApi {

    private fun fetchAssetUrls(): Map<String, String> {
        val request = Request.Builder().url(releaseApiBaseUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Échec de la requête GitHub Releases : HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Réponse vide de GitHub Releases")
            val assets = org.json.JSONObject(body).getJSONArray("assets")
            return (0 until assets.length()).associate { i ->
                val asset = assets.getJSONObject(i)
                asset.getString("name") to asset.getString("browser_download_url")
            }
        }
    }

    override suspend fun fetchMetadata(): RemoteMetadata {
        val assetUrls = fetchAssetUrls()
        val metadataUrl = assetUrls["metadata.json"]
            ?: throw IOException("metadata.json absent des assets de la release")
        val request = Request.Builder().url(metadataUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Échec du téléchargement de metadata.json : HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("metadata.json vide")
            return RemoteMetadata.parse(body)
        }
    }

    override suspend fun downloadDatabase(destination: File) {
        val assetUrls = fetchAssetUrls()
        val dbUrl = assetUrls["lmelp.db"]
            ?: throw IOException("lmelp.db absent des assets de la release")
        val request = Request.Builder().url(dbUrl).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Échec du téléchargement de lmelp.db : HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("lmelp.db vide")
            destination.outputStream().use { output ->
                body.byteStream().copyTo(output)
            }
        }
    }
}
