package com.lmelp.mobile

import com.lmelp.mobile.data.model.DataUpdateState
import com.lmelp.mobile.data.model.UpdateCheckResult
import com.lmelp.mobile.data.remote.GitHubReleaseApi
import com.lmelp.mobile.data.remote.RemoteMetadata
import com.lmelp.mobile.data.repository.DataUpdateRepository
import com.lmelp.mobile.data.repository.MetadataRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests unitaires pour DataUpdateRepository (issue #118).
 * La comparaison de version doit toujours se faire sur db_metadata.version
 * (timestamp Unix), jamais PRAGMA user_version (issue #102).
 */
class DataUpdateRepositoryTest {

    private fun remoteMetadata(exportVersion: String) = RemoteMetadata(
        schemaVersion = 1,
        exportDate = "2026-08-19",
        exportDatetime = "2026-08-19T19:35:48.818072",
        exportVersion = exportVersion,
        nbEmissions = 180,
        nbLivres = 1688,
        nbAvis = 4275,
        fileSizeBytes = 4616192L,
        sha256 = "0000000000000000000000000000000000000000000000000000000000000000",
        filename = "lmelp.db"
    )

    @Test
    fun `checkForUpdate retourne UpdateAvailable si la version distante est plus recente`() = runBlocking {
        val api = mock<GitHubReleaseApi>()
        whenever(api.fetchMetadata()).thenReturn(remoteMetadata(exportVersion = "200"))
        val metadataRepository = mock<MetadataRepository>()
        whenever(metadataRepository.getLocalVersion()).thenReturn("100")

        val repository = DataUpdateRepository(api, metadataRepository)
        val result = repository.checkForUpdate()

        assertTrue(result is UpdateCheckResult.UpdateAvailable)
    }

    @Test
    fun `checkForUpdate retourne UpToDate si les versions sont identiques`() = runBlocking {
        val api = mock<GitHubReleaseApi>()
        whenever(api.fetchMetadata()).thenReturn(remoteMetadata(exportVersion = "100"))
        val metadataRepository = mock<MetadataRepository>()
        whenever(metadataRepository.getLocalVersion()).thenReturn("100")

        val repository = DataUpdateRepository(api, metadataRepository)
        val result = repository.checkForUpdate()

        assertEquals(UpdateCheckResult.UpToDate, result)
    }

    @Test
    fun `checkForUpdate retourne Error si l'API leve une exception, sans jamais propager le crash`() = runBlocking {
        val api = mock<GitHubReleaseApi>()
        whenever(api.fetchMetadata()).thenThrow(RuntimeException("pas de réseau"))
        val metadataRepository = mock<MetadataRepository>()
        whenever(metadataRepository.getLocalVersion()).thenReturn("100")

        val repository = DataUpdateRepository(api, metadataRepository)
        val result = repository.checkForUpdate()

        assertTrue(result is UpdateCheckResult.Error)
    }
}
