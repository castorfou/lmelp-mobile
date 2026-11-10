package com.lmelp.mobile.data.repository

import com.lmelp.mobile.data.model.AppRelease
import com.lmelp.mobile.data.model.AppUpdateCheckResult
import com.lmelp.mobile.data.remote.AppReleaseApi
import com.lmelp.mobile.data.remote.updateErrorMessage
import com.lmelp.mobile.data.update.Sha256Verifier
import com.lmelp.mobile.data.update.isDevVersion
import com.lmelp.mobile.data.update.selectLatestAppRelease
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

/**
 * Détection et téléchargement d'une nouvelle version de l'appli (issue #162), sur le modèle
 * de DataUpdateRepository. Compare le versionCode installé à celui recalculé depuis le tag
 * des releases `vX.Y.Z`. Une version de dev (`0.0.0-dev+…`, APK debug) ne vérifie jamais :
 * son versionCode vaut 1 et toute release paraîtrait plus récente.
 * Les messages d'erreur sont rédigés pour l'utilisateur par updateErrorMessage (issue #168).
 */
class AppUpdateRepository(
    private val api: AppReleaseApi,
    private val installedVersionCode: Int,
    private val installedVersionName: String
) {
    private val _lastCheckResult = MutableStateFlow<AppUpdateCheckResult>(AppUpdateCheckResult.UpToDate)

    /** Résultat du dernier check (silencieux au lancement ou manuel). */
    val lastCheckResult: StateFlow<AppUpdateCheckResult> = _lastCheckResult.asStateFlow()

    /** Check silencieux : met le résultat en cache, ne lève jamais d'exception. */
    suspend fun checkForUpdateAndCache() {
        _lastCheckResult.value = checkForUpdate()
    }

    suspend fun checkForUpdate(): AppUpdateCheckResult {
        if (isDevVersion(installedVersionName)) return AppUpdateCheckResult.UpToDate
        return withContext(Dispatchers.IO) {
            try {
                val latest = selectLatestAppRelease(api.fetchReleases(), installedVersionCode)
                if (latest != null) AppUpdateCheckResult.UpdateAvailable(latest) else AppUpdateCheckResult.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                AppUpdateCheckResult.Error(updateErrorMessage(e))
            }
        }
    }

    /**
     * Télécharge l'APK de la release dans `<cacheDir>/updates/` (dossier exposé par le
     * FileProvider) et vérifie son SHA-256 quand GitHub le fournit. Lève une exception en cas d'échec.
     */
    suspend fun downloadApk(release: AppRelease, cacheDir: File): File = withContext(Dispatchers.IO) {
        val url = release.apkUrl ?: throw IOException("Pas d'APK dans la release ${release.tag}")
        val updatesDir = File(cacheDir, UPDATES_DIR).apply { mkdirs() }
        // Un seul APK conservé : les téléchargements précédents ne servent plus.
        updatesDir.listFiles()?.forEach { it.delete() }
        val destination = File(updatesDir, "lmelp-mobile-${release.tag}.apk")
        api.downloadApk(url, destination)
        val expected = release.apkSha256
        if (expected != null && !Sha256Verifier.sha256(destination).equals(expected, ignoreCase = true)) {
            destination.delete()
            throw IOException("APK téléchargé corrompu, veuillez réessayer")
        }
        destination
    }

    companion object {
        /** Doit correspondre au `<cache-path>` de res/xml/file_paths.xml. */
        const val UPDATES_DIR = "updates"
    }
}
