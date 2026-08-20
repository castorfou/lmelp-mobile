package com.lmelp.mobile.data.repository

import com.lmelp.mobile.data.model.DataUpdateState
import com.lmelp.mobile.data.model.UpdateCheckResult
import com.lmelp.mobile.data.remote.GitHubReleaseApi
import com.lmelp.mobile.data.update.DatabaseFileReplacer
import com.lmelp.mobile.data.update.Sha256Verifier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Orchestre la vérification/téléchargement des mises à jour de données
 * depuis la GitHub Release data-latest (issue #118).
 *
 * Compare toujours db_metadata.version (timestamp Unix local) à
 * RemoteMetadata.exportVersion (même timestamp côté distant) — jamais
 * PRAGMA user_version, qui est un numéro de schéma Room fixe (issue #102).
 */
class DataUpdateRepository(
    private val api: GitHubReleaseApi,
    private val metadataRepository: MetadataRepository
) {
    private val _lastCheckResult = MutableStateFlow<UpdateCheckResult>(UpdateCheckResult.UpToDate)

    /** Résultat du dernier check (silencieux au démarrage ou manuel), pour consultation sans re-appel réseau. */
    val lastCheckResult: StateFlow<UpdateCheckResult> = _lastCheckResult.asStateFlow()

    /** Check silencieux : met le résultat en cache, ne lève jamais d'exception. */
    suspend fun checkForUpdateAndCache() {
        _lastCheckResult.value = checkForUpdate()
    }

    suspend fun checkForUpdate(): UpdateCheckResult = withContext(Dispatchers.IO) {
        try {
            val remote = api.fetchMetadata()
            val localVersion = metadataRepository.getLocalVersion()?.toLongOrNull() ?: 0L
            val remoteVersion = remote.exportVersion.toLongOrNull() ?: 0L
            if (remoteVersion > localVersion) {
                UpdateCheckResult.UpdateAvailable(remote)
            } else {
                UpdateCheckResult.UpToDate
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            UpdateCheckResult.Error(e.message ?: e.javaClass.simpleName)
        }
    }

    /**
     * Télécharge lmelp.db, vérifie son SHA-256, puis remplace targetDbFile.
     * tempDir doit être un répertoire accessible en écriture (ex. cacheDir) pour
     * le fichier téléchargé avant vérification.
     */
    suspend fun applyUpdate(targetDbFile: File, tempDir: File): DataUpdateState = withContext(Dispatchers.IO) {
        try {
            val remote = api.fetchMetadata()
            val downloadedFile = File(tempDir, "lmelp_download.db")
            api.downloadDatabase(downloadedFile)

            val actualSha256 = Sha256Verifier.sha256(downloadedFile)
            if (actualSha256 != remote.sha256) {
                downloadedFile.delete()
                return@withContext DataUpdateState.Error("Fichier téléchargé corrompu, veuillez réessayer")
            }

            DatabaseFileReplacer.replace(downloadedFile = downloadedFile, targetDbFile = targetDbFile)
            DataUpdateState.Success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DataUpdateState.Error(e.message ?: e.javaClass.simpleName)
        }
    }
}
