package com.lmelp.mobile

import com.lmelp.mobile.data.db.PalmaresDao
import com.lmelp.mobile.data.model.PalmaresEntity
import com.lmelp.mobile.data.repository.PalmaresRepository
import com.lmelp.mobile.viewmodel.PalmaresViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class PalmaresViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeEntity(rank: Int, livreId: String, titre: String) = PalmaresEntity(
        rank = rank,
        livreId = livreId,
        titre = titre,
        auteurNom = null,
        noteMoyenne = 8.0,
        nbAvis = 3,
        nbCritiques = 3,
        calibreInLibrary = 0,
        calibreLu = 0,
        calibreRating = null
    )

    @Test
    fun `par defaut afficherNonLus=true afficherLus=false`() = runTest {
        val dao = mock<PalmaresDao>()
        whenever(dao.getPalmaresFiltres(afficherLus = 0, afficherNonLus = 1)).thenReturn(
            listOf(fakeEntity(1, "id1", "Livre A"), fakeEntity(2, "id2", "Livre B"))
        )
        val repo = PalmaresRepository(dao)
        val viewModel = PalmaresViewModel(repo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.afficherLus)
        assertTrue(state.afficherNonLus)
        assertEquals(2, state.palmares.size)
        assertEquals("Livre A", state.palmares[0].titre)
        assertNull(state.error)
        verify(dao).getPalmaresFiltres(afficherLus = 0, afficherNonLus = 1)
    }

    @Test
    fun `setAfficherLus true avec afficherNonLus true affiche tout`() = runTest {
        val dao = mock<PalmaresDao>()
        whenever(dao.getPalmaresFiltres(afficherLus = 0, afficherNonLus = 1)).thenReturn(emptyList())
        whenever(dao.getPalmaresFiltres(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(fakeEntity(1, "id1", "Livre Lu"), fakeEntity(2, "id2", "Livre Non Lu"))
        )
        val repo = PalmaresRepository(dao)
        val viewModel = PalmaresViewModel(repo)
        advanceUntilIdle()

        viewModel.setAfficherLus(true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.afficherLus)
        assertTrue(state.afficherNonLus)
        assertEquals(2, state.palmares.size)
        verify(dao).getPalmaresFiltres(afficherLus = 1, afficherNonLus = 1)
    }

    @Test
    fun `les deux filtres off retourne liste vide`() = runTest {
        val dao = mock<PalmaresDao>()
        whenever(dao.getPalmaresFiltres(afficherLus = 0, afficherNonLus = 1)).thenReturn(
            listOf(fakeEntity(1, "id1", "Livre A"))
        )
        whenever(dao.getPalmaresFiltres(afficherLus = 0, afficherNonLus = 0)).thenReturn(emptyList())
        val repo = PalmaresRepository(dao)
        val viewModel = PalmaresViewModel(repo)
        advanceUntilIdle()

        viewModel.setAfficherNonLus(false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.afficherNonLus)
        assertFalse(state.afficherLus)
        assertTrue(state.palmares.isEmpty())
        verify(dao).getPalmaresFiltres(afficherLus = 0, afficherNonLus = 0)
    }

    @Test
    fun `error case repository throws expose erreur`() = runTest {
        val dao = mock<PalmaresDao>()
        whenever(dao.getPalmaresFiltres(afficherLus = 0, afficherNonLus = 1)).thenThrow(RuntimeException("DB error"))
        val repo = PalmaresRepository(dao)
        val viewModel = PalmaresViewModel(repo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("DB error", state.error)
        assertTrue(state.palmares.isEmpty())
    }
}
