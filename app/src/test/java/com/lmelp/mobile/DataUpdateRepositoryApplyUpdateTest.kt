package com.lmelp.mobile

import com.lmelp.mobile.data.model.DataUpdateState
import com.lmelp.mobile.data.remote.GitHubReleaseApi
import com.lmelp.mobile.data.remote.RemoteMetadata
import com.lmelp.mobile.data.repository.DataUpdateRepository
import com.lmelp.mobile.data.repository.MetadataRepository
import com.lmelp.mobile.data.update.Sha256Verifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests unitaires pour DataUpdateRepository.applyUpdate() (issue #118) :
 * téléchargement + vérification SHA-256 + remplacement physique du fichier.
 */
class DataUpdateRepositoryApplyUpdateTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun remoteMetadata(sha256: String, content: String) = RemoteMetadata(
        schemaVersion = 1,
        exportDate = "2026-08-19",
        exportDatetime = "2026-08-19T19:35:48.818072",
        exportVersion = "200",
        nbEmissions = 180,
        nbLivres = 1688,
        nbAvis = 4275,
        fileSizeBytes = content.length.toLong(),
        sha256 = sha256,
        filename = "lmelp.db"
    )

    @Test
    fun `applyUpdate remplace la base si le SHA-256 telecharge est correct`() = runBlocking {
        val content = "nouvelle base de données"
        val correctSha256 = Sha256Verifier.sha256(tmpFolder.newFile("ref.db").apply { writeText(content) })
        val targetDb = tmpFolder.newFile("lmelp.db").apply { writeText("ancienne base") }

        val api = mock<GitHubReleaseApi>()
        whenever(api.fetchMetadata()).thenReturn(remoteMetadata(correctSha256, content))
        doAnswer { invocation ->
            val destination = invocation.getArgument<java.io.File>(0)
            destination.writeText(content)
            null
        }.whenever(api).downloadDatabase(any())

        val metadataRepository = mock<MetadataRepository>()
        val repository = DataUpdateRepository(api, metadataRepository)

        val state = repository.applyUpdate(targetDbFile = targetDb, tempDir = tmpFolder.newFolder("tmp"))

        assertEquals(DataUpdateState.Success, state)
        assertEquals(content, targetDb.readText())
    }

    @Test
    fun `applyUpdate retourne Error et ne remplace rien si le SHA-256 est incorrect`() = runBlocking {
        val content = "contenu corrompu"
        val targetDb = tmpFolder.newFile("lmelp.db").apply { writeText("ancienne base") }

        val api = mock<GitHubReleaseApi>()
        whenever(api.fetchMetadata()).thenReturn(remoteMetadata("sha256_incorrect_attendu", content))
        doAnswer { invocation ->
            val destination = invocation.getArgument<java.io.File>(0)
            destination.writeText(content)
            null
        }.whenever(api).downloadDatabase(any())

        val metadataRepository = mock<MetadataRepository>()
        val repository = DataUpdateRepository(api, metadataRepository)

        val state = repository.applyUpdate(targetDbFile = targetDb, tempDir = tmpFolder.newFolder("tmp2"))

        assertTrue(state is DataUpdateState.Error)
        assertEquals("ancienne base", targetDb.readText())
    }

    @Test
    fun `applyUpdate retourne Error si le telechargement leve une exception`() = runBlocking {
        val targetDb = tmpFolder.newFile("lmelp.db").apply { writeText("ancienne base") }

        val api = mock<GitHubReleaseApi>()
        whenever(api.fetchMetadata()).thenReturn(remoteMetadata("peu importe", "peu importe"))
        whenever(api.downloadDatabase(any())).thenThrow(RuntimeException("timeout réseau"))

        val metadataRepository = mock<MetadataRepository>()
        val repository = DataUpdateRepository(api, metadataRepository)

        val state = repository.applyUpdate(targetDbFile = targetDb, tempDir = tmpFolder.newFolder("tmp3"))

        assertTrue(state is DataUpdateState.Error)
        assertEquals("ancienne base", targetDb.readText())
    }
}
