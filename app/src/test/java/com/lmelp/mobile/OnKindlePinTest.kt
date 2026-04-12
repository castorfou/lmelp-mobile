package com.lmelp.mobile

import com.lmelp.mobile.data.db.OnKindleAvecConseilRow
import com.lmelp.mobile.data.db.OnKindleDao
import com.lmelp.mobile.data.repository.OnKindleRepository
import com.lmelp.mobile.data.repository.UserPreferencesRepository
import com.lmelp.mobile.viewmodel.OnKindleViewModel
import com.lmelp.mobile.viewmodel.TriMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Stub en mémoire de UserPreferencesRepository pour les tests unitaires.
 * Évite la dépendance à Context/DataStore dans les tests JVM.
 */
class FakeUserPreferencesRepository : UserPreferencesRepository.PinnedReadingStorage {
    private val _pinned = MutableStateFlow<Set<String>>(emptySet())
    override val pinnedReading: Flow<Set<String>> = _pinned

    override suspend fun togglePinnedReading(livreId: String) {
        val current = _pinned.value
        _pinned.value = if (livreId in current) current - livreId else current + livreId
    }

    override suspend fun removePinned(livreId: String) {
        _pinned.value = _pinned.value - livreId
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class OnKindlePinTest {

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

    private suspend fun makeViewModel(
        dao: OnKindleDao,
        prefs: FakeUserPreferencesRepository = FakeUserPreferencesRepository()
    ): OnKindleViewModel {
        val repo = OnKindleRepository(dao)
        return OnKindleViewModel(repo, prefs)
    }

    @Test
    fun `par defaut aucun livre epingle`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(fakeRow("id1", "Livre A"), fakeRow("id2", "Livre B"))
        )
        val vm = makeViewModel(dao)
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue(state.pinnedBookIds.isEmpty())
        assertTrue(state.livres.all { !it.isPinned })
    }

    @Test
    fun `togglePin epingle un livre`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(fakeRow("id1", "Livre A"), fakeRow("id2", "Livre B"))
        )
        val vm = makeViewModel(dao)
        advanceUntilIdle()

        vm.togglePin("id1")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertTrue("id1" in state.pinnedBookIds)
        assertTrue(state.livres.first { it.livreId == "id1" }.isPinned)
        assertFalse(state.livres.first { it.livreId == "id2" }.isPinned)
    }

    @Test
    fun `togglePin deux fois desepingle`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(fakeRow("id1", "Livre A"))
        )
        val vm = makeViewModel(dao)
        advanceUntilIdle()

        vm.togglePin("id1")
        advanceUntilIdle()
        vm.togglePin("id1")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertFalse("id1" in state.pinnedBookIds)
        assertFalse(state.livres.first().isPinned)
    }

    @Test
    fun `livres epingles apparaissent en tete independamment du tri NOTE_MASQUE`() = runTest {
        val dao = mock<OnKindleDao>()
        // id2 a une note plus haute mais id1 est épinglé → doit être en tête
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(
                fakeRow("id1", "Note Basse", noteMoyenne = 5.0, nbAvis = 2),
                fakeRow("id2", "Note Haute", noteMoyenne = 9.0, nbAvis = 3)
            )
        )
        val vm = makeViewModel(dao)
        advanceUntilIdle()

        vm.togglePin("id1")
        vm.setTriMode(TriMode.NOTE_MASQUE)
        advanceUntilIdle()

        val livres = vm.uiState.value.livres
        assertEquals(2, livres.size)
        assertEquals("id1", livres[0].livreId)
        assertTrue(livres[0].isPinned)
        assertEquals("id2", livres[1].livreId)
    }

    @Test
    fun `plusieurs livres epingles suivent le tri interne ALPHA`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(
                fakeRow("id1", "Zorro"),
                fakeRow("id2", "Aventure"),
                fakeRow("id3", "Non epingle")
            )
        )
        val vm = makeViewModel(dao)
        advanceUntilIdle()

        vm.togglePin("id1")
        vm.togglePin("id2")
        advanceUntilIdle()

        val livres = vm.uiState.value.livres
        assertEquals(3, livres.size)
        // Les deux épinglés sont en tête, triés alphabétiquement entre eux
        assertTrue(livres[0].isPinned)
        assertTrue(livres[1].isPinned)
        assertEquals("Aventure", livres[0].titre)
        assertEquals("Zorro", livres[1].titre)
        // Le non-épinglé est en dernier
        assertFalse(livres[2].isPinned)
        assertEquals("Non epingle", livres[2].titre)
    }

    @Test
    fun `isPinned propage dans OnKindleUi`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(fakeRow("id1", "Livre A"), fakeRow("id2", "Livre B"))
        )
        val prefs = FakeUserPreferencesRepository()
        prefs.togglePinnedReading("id1")
        val repo = OnKindleRepository(dao)
        val vm = OnKindleViewModel(repo, prefs)
        advanceUntilIdle()

        val livres = vm.uiState.value.livres
        assertTrue(livres.first { it.livreId == "id1" }.isPinned)
        assertFalse(livres.first { it.livreId == "id2" }.isPinned)
    }

    @Test
    fun `livre lu dans calibre est automatiquement desepingle au chargement`() = runTest {
        val dao = mock<OnKindleDao>()
        // id1 est épinglé mais il devient lu (calibreLu=1)
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(fakeRow("id1", "Livre Lu", calibreLu = 1), fakeRow("id2", "Livre Non Lu"))
        )
        val prefs = FakeUserPreferencesRepository()
        prefs.togglePinnedReading("id1") // on épingle id1 au préalable
        val repo = OnKindleRepository(dao)
        val vm = OnKindleViewModel(repo, prefs)
        advanceUntilIdle()

        // id1 doit avoir été désépinglé automatiquement car calibreLu=true
        val state = vm.uiState.value
        assertFalse("id1" in state.pinnedBookIds)
        assertFalse(state.livres.first { it.livreId == "id1" }.isPinned)
    }
}
