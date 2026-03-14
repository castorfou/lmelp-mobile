package com.lmelp.mobile

import com.lmelp.mobile.data.db.OnKindleDao
import com.lmelp.mobile.data.model.OnKindleEntity
import com.lmelp.mobile.data.repository.OnKindleRepository
import com.lmelp.mobile.viewmodel.OnKindleViewModel
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
class OnKindleViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeEntity(
        livreId: String,
        titre: String,
        calibreLu: Int = 0,
        noteMoyenne: Double? = null,
        nbAvis: Int = 0
    ) = OnKindleEntity(
        livreId = livreId,
        titre = titre,
        auteurNom = null,
        urlBabelio = null,
        calibreLu = calibreLu,
        calibreRating = null,
        noteMoyenne = noteMoyenne,
        nbAvis = nbAvis
    )

    @Test
    fun `par defaut afficherLus=true afficherNonLus=true triParNote=false`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleFiltres(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(fakeEntity("id1", "Aventure"), fakeEntity("id2", "Zorro"))
        )
        val repo = OnKindleRepository(dao)
        val viewModel = OnKindleViewModel(repo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.afficherLus)
        assertTrue(state.afficherNonLus)
        assertFalse(state.triParNote)
        assertEquals(2, state.livres.size)
        assertEquals("Aventure", state.livres[0].titre)
        assertNull(state.error)
        verify(dao).getOnKindleFiltres(afficherLus = 1, afficherNonLus = 1)
    }

    @Test
    fun `setAfficherLus false affiche seulement non lus`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleFiltres(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(fakeEntity("id1", "Livre Lu", calibreLu = 1), fakeEntity("id2", "Livre Non Lu"))
        )
        whenever(dao.getOnKindleFiltres(afficherLus = 0, afficherNonLus = 1)).thenReturn(
            listOf(fakeEntity("id2", "Livre Non Lu"))
        )
        val repo = OnKindleRepository(dao)
        val viewModel = OnKindleViewModel(repo)
        advanceUntilIdle()

        viewModel.setAfficherLus(false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.afficherLus)
        assertTrue(state.afficherNonLus)
        assertEquals(1, state.livres.size)
        assertEquals("Livre Non Lu", state.livres[0].titre)
    }

    @Test
    fun `setTriParNote true trie par note decroissante`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleFiltres(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(
                fakeEntity("id1", "Moyen", noteMoyenne = 7.0, nbAvis = 3),
                fakeEntity("id2", "Excellent", noteMoyenne = 9.5, nbAvis = 4),
                fakeEntity("id3", "Sans note", noteMoyenne = null, nbAvis = 0)
            )
        )
        val repo = OnKindleRepository(dao)
        val viewModel = OnKindleViewModel(repo)
        advanceUntilIdle()

        viewModel.setTriParNote(true)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.triParNote)
        assertEquals(3, state.livres.size)
        assertEquals("Excellent", state.livres[0].titre)
        assertEquals("Moyen", state.livres[1].titre)
        assertEquals("Sans note", state.livres[2].titre)
    }

    @Test
    fun `tri az insensible aux accents`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleFiltres(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(
                fakeEntity("id1", "Zorro"),
                fakeEntity("id2", "À prendre ou à laisser"),
                fakeEntity("id3", "Aventure")
            )
        )
        val repo = OnKindleRepository(dao)
        val viewModel = OnKindleViewModel(repo)
        advanceUntilIdle()

        val livres = viewModel.uiState.value.livres
        assertEquals("À prendre ou à laisser", livres[0].titre)
        assertEquals("Aventure", livres[1].titre)
        assertEquals("Zorro", livres[2].titre)
    }

    @Test
    fun `livre discute au masque a discusseAuMasque=true`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleFiltres(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(
                fakeEntity("id1", "Discuté", noteMoyenne = 8.5, nbAvis = 4),
                fakeEntity("id2", "Non discuté", noteMoyenne = null, nbAvis = 0)
            )
        )
        val repo = OnKindleRepository(dao)
        val viewModel = OnKindleViewModel(repo)
        advanceUntilIdle()

        val livres = viewModel.uiState.value.livres
        assertTrue(livres.first { it.titre == "Discuté" }.discusseAuMasque)
        assertFalse(livres.first { it.titre == "Non discuté" }.discusseAuMasque)
    }

    @Test
    fun `error case expose erreur dans uiState`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleFiltres(afficherLus = 1, afficherNonLus = 1))
            .thenThrow(RuntimeException("DB error"))
        val repo = OnKindleRepository(dao)
        val viewModel = OnKindleViewModel(repo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("DB error", state.error)
        assertTrue(state.livres.isEmpty())
    }

    @Test
    fun `les deux filtres off retourne liste vide`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleFiltres(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(fakeEntity("id1", "Livre A"))
        )
        whenever(dao.getOnKindleFiltres(afficherLus = 0, afficherNonLus = 0)).thenReturn(emptyList())
        val repo = OnKindleRepository(dao)
        val viewModel = OnKindleViewModel(repo)
        advanceUntilIdle()

        viewModel.setAfficherLus(false)
        viewModel.setAfficherNonLus(false)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.afficherLus)
        assertFalse(state.afficherNonLus)
        assertTrue(state.livres.isEmpty())
    }
}
