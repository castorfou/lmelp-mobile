package com.lmelp.mobile

import com.lmelp.mobile.data.model.UpdateCheckResult
import com.lmelp.mobile.data.remote.GitHubReleaseApi
import com.lmelp.mobile.data.remote.RemoteMetadata
import com.lmelp.mobile.data.repository.DataUpdateRepository
import com.lmelp.mobile.data.repository.MetadataRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests pour le cache du dernier résultat de check (issue #118) : permet à
 * AboutViewModel de consulter le résultat du check silencieux fait au
 * démarrage de l'app sans redéclencher un appel réseau.
 */
class DataUpdateRepositoryCacheTest {

    private fun remoteMetadata(exportVersion: String) = RemoteMetadata(
        schemaVersion = 1,
        exportDate = "2026-08-19",
        exportDatetime = "2026-08-19T19:35:48.818072",
        exportVersion = exportVersion,
        nbEmissions = 180,
        nbLivres = 1688,
        nbAvis = 4275,
        fileSizeBytes = 4616192L,
        sha256 = "abc",
        filename = "lmelp.db"
    )

    @Test
    fun `checkForUpdateAndCache met a jour lastCheckResult`() = runBlocking {
        val api = mock<GitHubReleaseApi>()
        whenever(api.fetchMetadata()).thenReturn(remoteMetadata("200"))
        val metadataRepository = mock<MetadataRepository>()
        whenever(metadataRepository.getLocalVersion()).thenReturn("100")

        val repository = DataUpdateRepository(api, metadataRepository)
        repository.checkForUpdateAndCache()

        assertTrue(repository.lastCheckResult.value is UpdateCheckResult.UpdateAvailable)
    }
}
