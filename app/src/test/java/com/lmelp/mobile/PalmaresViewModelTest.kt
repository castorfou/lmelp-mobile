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
    fun `par defaut afficherLus=false et palmares charges`() = runTest {
        val dao = mock<PalmaresDao>()
        whenever(dao.getPalmaresFiltres(afficherLus = false)).thenReturn(
            listOf(fakeEntity(1, "id1", "Livre A"), fakeEntity(2, "id2", "Livre B"))
        )
        val repo = PalmaresRepository(dao)
        val viewModel = PalmaresViewModel(repo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertFalse(state.afficherLus)
        assertEquals(2, state.palmares.size)
        assertEquals("Livre A", state.palmares[0].titre)
        assertNull(state.error)
        verify(dao).getPalmaresFiltres(afficherLus = false)
    }

    @Test
    fun `setAfficherLus true recharge le palmares`() = runTest {
        val dao = mock<PalmaresDao>()
        whenever(dao.getPalmaresFiltres(afficherLus = false)).thenReturn(emptyList())
        whenever(dao.getPalmaresFiltres(afficherLus = true)).thenReturn(
            listOf(fakeEntity(1, "id1", "Livre Lu"))
        )
        val repo = PalmaresRepository(dao)
        val viewModel = PalmaresViewModel(repo)
        advanceUntilIdle()

        viewModel.setAfficherLus(true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.afficherLus)
        assertEquals(1, state.palmares.size)
        assertEquals("Livre Lu", state.palmares[0].titre)
        verify(dao).getPalmaresFiltres(afficherLus = true)
    }

    @Test
    fun `error case repository throws expose erreur`() = runTest {
        val dao = mock<PalmaresDao>()
        whenever(dao.getPalmaresFiltres(afficherLus = false)).thenThrow(RuntimeException("DB error"))
        val repo = PalmaresRepository(dao)
        val viewModel = PalmaresViewModel(repo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("DB error", state.error)
        assertTrue(state.palmares.isEmpty())
    }
}
