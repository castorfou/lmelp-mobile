package com.lmelp.mobile

import com.lmelp.mobile.data.model.DataUpdateState
import com.lmelp.mobile.data.model.UpdateCheckResult
import com.lmelp.mobile.data.remote.RemoteMetadata
import com.lmelp.mobile.data.repository.DataUpdateRepository
import com.lmelp.mobile.data.repository.MetadataRepository
import com.lmelp.mobile.viewmodel.AboutViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests unitaires pour la partie "mise à jour des données" de AboutViewModel (issue #118).
 * Le check silencieux (démarrage) ne doit jamais publier d'Error visible ;
 * le check manuel (bouton) doit publier une Error explicite en cas d'échec.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AboutViewModelDataUpdateTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun remoteMetadata() = RemoteMetadata(
        schemaVersion = 1,
        exportDate = "2026-08-19",
        exportDatetime = "2026-08-19T19:35:48.818072",
        exportVersion = "200",
        nbEmissions = 180,
        nbLivres = 1688,
        nbAvis = 4275,
        fileSizeBytes = 4616192L,
        sha256 = "abc",
        filename = "lmelp.db"
    )

    private suspend fun mockMetadataRepository(): MetadataRepository {
        val metadataRepository = mock<MetadataRepository>()
        whenever(metadataRepository.getDbInfo()).thenReturn(
            com.lmelp.mobile.data.model.DbInfoUi("—", "—", "—", "—", "—")
        )
        return metadataRepository
    }

    /** lastCheckResult neutre (UpToDate) par défaut, pour ne pas polluer les tests qui n'en ont pas besoin. */
    private fun mockDataUpdateRepository(): DataUpdateRepository {
        val dataUpdateRepository = mock<DataUpdateRepository>()
        whenever(dataUpdateRepository.lastCheckResult)
            .thenReturn(MutableStateFlow(UpdateCheckResult.UpToDate))
        return dataUpdateRepository
    }

    @Test
    fun `checkForUpdateSilently ne publie jamais d'Error meme si le repository echoue`() = runTest {
        val dataUpdateRepository = mockDataUpdateRepository()
        whenever(dataUpdateRepository.checkForUpdate())
            .thenReturn(UpdateCheckResult.Error("pas de réseau"))

        val viewModel = AboutViewModel(mockMetadataRepository(), dataUpdateRepository)
        viewModel.checkForUpdateSilently()
        advanceUntilIdle()

        val state = viewModel.dataUpdateState.value
        assertEquals(DataUpdateState.Idle, state)
    }

    @Test
    fun `checkForUpdateSilently publie UpdateAvailable si une MAJ est disponible`() = runTest {
        val dataUpdateRepository = mockDataUpdateRepository()
        val remote = remoteMetadata()
        whenever(dataUpdateRepository.checkForUpdate())
            .thenReturn(UpdateCheckResult.UpdateAvailable(remote))

        val viewModel = AboutViewModel(mockMetadataRepository(), dataUpdateRepository)
        viewModel.checkForUpdateSilently()
        advanceUntilIdle()

        val state = viewModel.dataUpdateState.value
        assertTrue(state is DataUpdateState.UpdateAvailable)
    }

    @Test
    fun `le ViewModel reflete immediatement lastCheckResult du repository au chargement`() = runTest {
        val dataUpdateRepository = mock<DataUpdateRepository>()
        val remote = remoteMetadata()
        whenever(dataUpdateRepository.lastCheckResult)
            .thenReturn(MutableStateFlow(UpdateCheckResult.UpdateAvailable(remote)))

        val viewModel = AboutViewModel(mockMetadataRepository(), dataUpdateRepository)
        advanceUntilIdle()

        val state = viewModel.dataUpdateState.value
        assertTrue(
            "le ViewModel doit refléter le cache sans appel manuel, était : $state",
            state is DataUpdateState.UpdateAvailable
        )
    }

    @Test
    fun `checkForUpdateManually publie Error visible si le repository echoue`() = runTest {
        val dataUpdateRepository = mockDataUpdateRepository()
        whenever(dataUpdateRepository.checkForUpdate())
            .thenReturn(UpdateCheckResult.Error("pas de réseau"))

        val viewModel = AboutViewModel(mockMetadataRepository(), dataUpdateRepository)
        viewModel.checkForUpdateManually()
        advanceUntilIdle()

        val state = viewModel.dataUpdateState.value
        assertTrue(state is DataUpdateState.Error)
    }

    @Test
    fun `applyUpdate publie Restarting avant d'appeler onSuccess pour laisser le temps d'afficher le message`() = runTest {
        val dataUpdateRepository = mockDataUpdateRepository()
        whenever(dataUpdateRepository.applyUpdate(org.mockito.kotlin.any(), org.mockito.kotlin.any()))
            .thenReturn(DataUpdateState.Success)

        val viewModel = AboutViewModel(mockMetadataRepository(), dataUpdateRepository)
        var onSuccessCalled = false
        viewModel.applyUpdate(
            targetDbFile = java.io.File("dummy.db"),
            tempDir = java.io.File("dummy_tmp"),
            onSuccess = { onSuccessCalled = true }
        )

        // Juste après le Success, avant advanceUntilIdle : l'état affiché doit être Restarting,
        // pas encore Success brut, et onSuccess ne doit pas avoir été appelé immédiatement.
        runCurrent()
        assertEquals(DataUpdateState.Restarting, viewModel.dataUpdateState.value)
        assertEquals(false, onSuccessCalled)

        advanceUntilIdle()
        assertEquals(true, onSuccessCalled)
    }
}
