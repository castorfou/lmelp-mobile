package com.lmelp.mobile

import com.lmelp.mobile.data.db.OnKindleAvecConseilRow
import com.lmelp.mobile.data.db.OnKindleDao
import com.lmelp.mobile.data.repository.OnKindleRepository
import com.lmelp.mobile.viewmodel.OnKindleViewModel
import com.lmelp.mobile.viewmodel.TriMode
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

    private fun fakeRow(
        livreId: String,
        titre: String,
        calibreLu: Int = 0,
        noteMoyenne: Double? = null,
        nbAvis: Int = 0,
        scoreHybride: Double? = null
    ) = OnKindleAvecConseilRow(
        livreId = livreId,
        titre = titre,
        auteurNom = null,
        urlBabelio = null,
        urlCover = null,
        calibreLu = calibreLu,
        calibreRating = null,
        noteMoyenne = noteMoyenne,
        nbAvis = nbAvis,
        scoreHybride = scoreHybride
    )

    @Test
    fun `par defaut afficherLus=true afficherNonLus=true triMode=ALPHA`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(fakeRow("id1", "Aventure"), fakeRow("id2", "Zorro"))
        )
        val repo = OnKindleRepository(dao)
        val viewModel = OnKindleViewModel(repo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.afficherLus)
        assertTrue(state.afficherNonLus)
        assertEquals(TriMode.ALPHA, state.triMode)
        assertEquals(2, state.livres.size)
        assertEquals("Aventure", state.livres[0].titre)
        assertNull(state.error)
        verify(dao).getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)
    }

    @Test
    fun `setAfficherLus false affiche seulement non lus`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(fakeRow("id1", "Livre Lu", calibreLu = 1), fakeRow("id2", "Livre Non Lu"))
        )
        whenever(dao.getOnKindleAvecConseil(afficherLus = 0, afficherNonLus = 1)).thenReturn(
            listOf(fakeRow("id2", "Livre Non Lu"))
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
    fun `setTriMode NOTE_MASQUE trie par noteMoyenne decroissante`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(
                fakeRow("id1", "Moyen", noteMoyenne = 7.0, nbAvis = 3),
                fakeRow("id2", "Excellent", noteMoyenne = 9.5, nbAvis = 4),
                fakeRow("id3", "Sans note", noteMoyenne = null, nbAvis = 0)
            )
        )
        val repo = OnKindleRepository(dao)
        val viewModel = OnKindleViewModel(repo)
        advanceUntilIdle()

        viewModel.setTriMode(TriMode.NOTE_MASQUE)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TriMode.NOTE_MASQUE, state.triMode)
        assertEquals(3, state.livres.size)
        assertEquals("Excellent", state.livres[0].titre)
        assertEquals("Moyen", state.livres[1].titre)
        assertEquals("Sans note", state.livres[2].titre)
    }

    @Test
    fun `setTriMode NOTE_CONSEIL trie par scoreHybride decroissant livres sans conseil en dernier`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(
                fakeRow("id1", "Moyen conseil", scoreHybride = 6.5),
                fakeRow("id2", "Top conseil", scoreHybride = 8.0),
                fakeRow("id3", "Hors recommendations", scoreHybride = null)
            )
        )
        val repo = OnKindleRepository(dao)
        val viewModel = OnKindleViewModel(repo)
        advanceUntilIdle()

        viewModel.setTriMode(TriMode.NOTE_CONSEIL)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(TriMode.NOTE_CONSEIL, state.triMode)
        assertEquals(3, state.livres.size)
        assertEquals("Top conseil", state.livres[0].titre)
        assertEquals("Moyen conseil", state.livres[1].titre)
        assertEquals("Hors recommendations", state.livres[2].titre)
    }

    @Test
    fun `scoreHybride propage dans OnKindleUi`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(fakeRow("id1", "Avec conseil", scoreHybride = 7.8))
        )
        val repo = OnKindleRepository(dao)
        val viewModel = OnKindleViewModel(repo)
        advanceUntilIdle()

        val livre = viewModel.uiState.value.livres[0]
        assertEquals(7.8, livre.scoreHybride!!, 0.001)
    }

    @Test
    fun `tri az insensible aux accents`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(
                fakeRow("id1", "Zorro"),
                fakeRow("id2", "À prendre ou à laisser"),
                fakeRow("id3", "Aventure")
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
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(
                fakeRow("id1", "Discuté", noteMoyenne = 8.5, nbAvis = 4),
                fakeRow("id2", "Non discuté", noteMoyenne = null, nbAvis = 0)
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
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1))
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
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(fakeRow("id1", "Livre A"))
        )
        whenever(dao.getOnKindleAvecConseil(afficherLus = 0, afficherNonLus = 0)).thenReturn(emptyList())
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
