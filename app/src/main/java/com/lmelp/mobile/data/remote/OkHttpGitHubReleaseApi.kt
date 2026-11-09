package com.lmelp.mobile.data.remote

import com.lmelp.mobile.data.update.SqliteSchemaVersion
import kotlinx.coroutines.delay
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Implémentation OkHttp de GitHubReleaseApi (issue #118).
 * Consomme l'API GitHub Releases pour résoudre les download URLs des assets
 * publiés sur la release data-v{N}, où N est le PRAGMA user_version du fichier
 * DB local (lmelp.db + metadata.json). Le tag est résolu dynamiquement à partir
 * du schéma Room local, jamais un tag fixe : ainsi le fichier téléchargé est
 * toujours au même schéma que l'app qui le demande (issue #132).
 *
 * Chaque requête est réessayée sur les erreurs passagères de GitHub (502/503/504,
 * coupure réseau), jamais sur une 404 (issue #156, voir [withRetry]).
 */
class OkHttpGitHubReleaseApi(
    private val localDbFile: File,
    private val apiBaseUrl: String =
        "https://api.github.com/repos/castorfou/lmelp-mobile/releases/tags",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build(),
    private val retryDelaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
    private val sleep: suspend (Long) -> Unit = { delay(it) }
) : GitHubReleaseApi {

    private fun releaseTag(): String = "data-v${SqliteSchemaVersion.read(localDbFile)}"

    private suspend fun <T> retrying(block: () -> T): T =
        withRetry(delaysMs = retryDelaysMs, sleep = sleep, block = block)

    private suspend fun fetchAssetUrls(): Map<String, String> {
        val tag = releaseTag()
        val request = Request.Builder().url("$apiBaseUrl/$tag").build()
        return retrying { fetchAssetUrls(request, tag) }
    }

    private fun fetchAssetUrls(request: Request, tag: String): Map<String, String> =
        client.newCall(request).execute().use { response ->
            if (response.code == 404) throw ReleaseNotFoundException(tag)
            if (!response.isSuccessful) {
                throw HttpStatusException(
                    response.code, "Échec de la requête GitHub Releases : HTTP ${response.code}"
                )
            }
            val body = response.body?.string() ?: throw IOException("Réponse vide de GitHub Releases")
            val assets = org.json.JSONObject(body).getJSONArray("assets")
            (0 until assets.length()).associate { i ->
                val asset = assets.getJSONObject(i)
                asset.getString("name") to asset.getString("browser_download_url")
            }
        }

    override suspend fun fetchMetadata(): RemoteMetadata {
        val assetUrls = fetchAssetUrls()
        val metadataUrl = assetUrls["metadata.json"]
            ?: throw IOException("metadata.json absent des assets de la release")
        val request = Request.Builder().url(metadataUrl).build()
        return retrying {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw HttpStatusException(
                        response.code, "Échec du téléchargement de metadata.json : HTTP ${response.code}"
                    )
                }
                val body = response.body?.string() ?: throw IOException("metadata.json vide")
                RemoteMetadata.parse(body)
            }
        }
    }

    override suspend fun downloadDatabase(destination: File) {
        val assetUrls = fetchAssetUrls()
        val dbUrl = assetUrls["lmelp.db"]
            ?: throw IOException("lmelp.db absent des assets de la release")
        val request = Request.Builder().url(dbUrl).build()
        // Une coupure en cours de copie relance le téléchargement complet : outputStream()
        // réécrit destination depuis le début.
        retrying {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw HttpStatusException(
                        response.code, "Échec du téléchargement de lmelp.db : HTTP ${response.code}"
                    )
                }
                val body = response.body ?: throw IOException("lmelp.db vide")
                destination.outputStream().use { output ->
                    body.byteStream().copyTo(output)
                }
            }
        }
    }
}
