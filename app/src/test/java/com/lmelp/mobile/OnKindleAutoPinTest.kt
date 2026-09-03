package com.lmelp.mobile

import com.lmelp.mobile.data.db.OnKindleAvecConseilRow
import com.lmelp.mobile.data.db.OnKindleDao
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests TDD pour l'auto-épinglage "en cours de lecture" basé sur les données Calibre/KOReader
 * (issue #131) : un livre avec enCoursLecture=true doit apparaître épinglé par défaut, sans
 * action utilisateur, tout en restant désépinglable manuellement de façon durable.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnKindleAutoPinTest {

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
        enCoursLecture: Int = 0
    ) = OnKindleAvecConseilRow(
        livreId = livreId,
        titre = titre,
        auteurNom = null,
        urlBabelio = null,
        urlCover = null,
        calibreLu = calibreLu,
        calibreRating = null,
        noteMoyenne = null,
        nbAvis = 0,
        scoreHybride = null,
        enCoursLecture = enCoursLecture
    )

    @Test
    fun `livre en cours de lecture est epingle automatiquement sans action utilisateur`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(
                fakeRow("id1", "Livre en cours", enCoursLecture = 1),
                fakeRow("id2", "Livre normal")
            )
        )
        val prefs = FakeUserPreferencesRepository()
        val repo = OnKindleRepository(dao)
        val vm = OnKindleViewModel(repo, prefs)
        advanceUntilIdle()

        val livres = vm.uiState.value.livres
        assertTrue(livres.first { it.livreId == "id1" }.isPinned)
        assertFalse(livres.first { it.livreId == "id2" }.isPinned)
    }

    @Test
    fun `desepinglage manuel d'un livre auto-epingle persiste apres rechargement`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(fakeRow("id1", "Livre en cours", enCoursLecture = 1))
        )
        val prefs = FakeUserPreferencesRepository()
        val repo = OnKindleRepository(dao)
        val vm = OnKindleViewModel(repo, prefs)
        advanceUntilIdle()

        // L'utilisateur retire l'épingle auto
        vm.togglePin("id1")
        advanceUntilIdle()
        assertFalse(vm.uiState.value.livres.first().isPinned)

        // Un nouveau ViewModel (simulateur de rechargement d'écran) doit respecter le retrait
        val vm2 = OnKindleViewModel(repo, prefs)
        advanceUntilIdle()
        assertFalse(vm2.uiState.value.livres.first().isPinned)
        assertTrue("id1" in prefs.autoPinDismissedSnapshot())
    }

    @Test
    fun `livre dismissed dont la progression redevient absente est nettoye`() = runTest {
        val dao = mock<OnKindleDao>()
        // Première charge : en cours, l'utilisateur le retire
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(fakeRow("id1", "Livre en cours", enCoursLecture = 1))
        )
        val prefs = FakeUserPreferencesRepository()
        val repo = OnKindleRepository(dao)
        val vm = OnKindleViewModel(repo, prefs)
        advanceUntilIdle()
        vm.togglePin("id1")
        advanceUntilIdle()
        assertTrue("id1" in prefs.autoPinDismissedSnapshot())

        // Nouvelle regen DB : le livre n'est plus en cours de lecture
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(fakeRow("id1", "Livre en cours", enCoursLecture = 0))
        )
        val vm2 = OnKindleViewModel(repo, prefs)
        advanceUntilIdle()

        assertFalse("id1" in prefs.autoPinDismissedSnapshot())
        assertFalse(vm2.uiState.value.livres.first().isPinned)
    }

    @Test
    fun `livre auto-epingle devenu lu est auto-desepingle et nettoye du dismissed`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(fakeRow("id1", "Livre termine", calibreLu = 1, enCoursLecture = 0))
        )
        val prefs = FakeUserPreferencesRepository()
        prefs.dismissAutoPin("id1") // était dismissed avant que le livre soit terminé
        val repo = OnKindleRepository(dao)
        val vm = OnKindleViewModel(repo, prefs)
        advanceUntilIdle()

        assertFalse(vm.uiState.value.livres.first().isPinned)
        assertFalse("id1" in prefs.autoPinDismissedSnapshot())
    }

    @Test
    fun `epinglage manuel d'un livre sans progression en cours fonctionne normalement`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(fakeRow("id1", "Livre A"), fakeRow("id2", "Livre B"))
        )
        val prefs = FakeUserPreferencesRepository()
        val repo = OnKindleRepository(dao)
        val vm = OnKindleViewModel(repo, prefs)
        advanceUntilIdle()

        vm.togglePin("id1")
        advanceUntilIdle()

        assertTrue(vm.uiState.value.livres.first { it.livreId == "id1" }.isPinned)
        assertFalse(vm.uiState.value.livres.first { it.livreId == "id2" }.isPinned)
    }

    @Test
    fun `livres auto et manuel epingles restent groupes en tete`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1)).thenReturn(
            listOf(
                fakeRow("id1", "Zorro"),
                fakeRow("id2", "Auto En Cours", enCoursLecture = 1),
                fakeRow("id3", "Non epingle")
            )
        )
        val prefs = FakeUserPreferencesRepository()
        val repo = OnKindleRepository(dao)
        val vm = OnKindleViewModel(repo, prefs)
        advanceUntilIdle()

        vm.togglePin("id1") // épinglage manuel
        advanceUntilIdle()

        val livres = vm.uiState.value.livres
        assertTrue(livres[0].isPinned)
        assertTrue(livres[1].isPinned)
        assertFalse(livres[2].isPinned)
        assertEqualsIgnoreOrder(setOf("id1", "id2"), setOf(livres[0].livreId, livres[1].livreId))
    }

    private fun assertEqualsIgnoreOrder(expected: Set<String>, actual: Set<String>) {
        assertTrue(expected == actual)
    }
}
