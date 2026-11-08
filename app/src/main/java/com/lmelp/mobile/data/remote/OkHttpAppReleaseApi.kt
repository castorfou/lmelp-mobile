package com.lmelp.mobile.data.remote

import com.lmelp.mobile.data.model.AppRelease
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Implémentation OkHttp d'AppReleaseApi (issue #162).
 *
 * Liste `GET /releases` plutôt que `releases/latest` : les releases de données `data-v{N}`
 * sont marquées « Latest » par GitHub, `releases/latest` ne renverrait jamais une release d'appli.
 * Appel non authentifié : 60 requêtes/heure par IP, largement assez pour un check au lancement.
 */
class OkHttpAppReleaseApi(
    private val releasesUrl: String = "https://api.github.com/repos/castorfou/lmelp-mobile/releases",
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()
) : AppReleaseApi {

    override suspend fun fetchReleases(): List<AppRelease> {
        val request = Request.Builder()
            .url("$releasesUrl?per_page=30")
            .header("Accept", "application/vnd.github+json")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Échec de la requête GitHub Releases : HTTP ${response.code}")
            }
            val body = response.body?.string() ?: throw IOException("Réponse vide de GitHub Releases")
            val array = JSONArray(body)
            return (0 until array.length()).map { parseRelease(array.getJSONObject(it)) }
        }
    }

    private fun parseRelease(json: JSONObject): AppRelease {
        val assets = json.optJSONArray("assets") ?: JSONArray()
        val apk = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk") }
        val digest = apk?.takeUnless { it.isNull("digest") }?.optString("digest")
        return AppRelease(
            tag = json.getString("tag_name"),
            prerelease = json.optBoolean("prerelease", false),
            draft = json.optBoolean("draft", false),
            apkUrl = apk?.getString("browser_download_url"),
            apkSha256 = digest?.takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:"),
            htmlUrl = json.optString("html_url")
        )
    }

    override suspend fun downloadApk(url: String, destination: File) {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Échec du téléchargement de l'APK : HTTP ${response.code}")
            }
            val body = response.body ?: throw IOException("APK vide")
            destination.outputStream().use { output -> body.byteStream().copyTo(output) }
        }
    }
}
