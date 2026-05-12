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
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Vérifie que les clés utilisées par le LaunchedEffect de scroll reset changent
 * effectivement quand l'utilisateur bascule entre filtres ou modes de tri.
 *
 * Contexte : issue #103 — après changement de filtre, la liste doit toujours
 * repartir du début (position 0) pour que les livres épinglés soient visibles.
 * Le LaunchedEffect dans OnKindleContent est keyed sur (afficherLus, afficherNonLus, triMode).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OnKindleScrollResetTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun fakeRow(livreId: String, titre: String, calibreLu: Int = 0) =
        OnKindleAvecConseilRow(
            livreId = livreId,
            titre = titre,
            auteurNom = null,
            urlBabelio = null,
            urlCover = null,
            calibreLu = calibreLu,
            calibreRating = null,
            noteMoyenne = null,
            nbAvis = 0,
            scoreHybride = null
        )

    @Test
    fun `changer afficherLus modifie la cle de scroll reset dans uiState`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1))
            .thenReturn(listOf(fakeRow("id1", "Livre A"), fakeRow("id2", "Livre B Lu", calibreLu = 1)))
        whenever(dao.getOnKindleAvecConseil(afficherLus = 0, afficherNonLus = 1))
            .thenReturn(listOf(fakeRow("id1", "Livre A")))
        val repo = OnKindleRepository(dao)
        val vm = OnKindleViewModel(repo)
        advanceUntilIdle()

        val afficherLusAvant = vm.uiState.value.afficherLus

        vm.setAfficherLus(false)
        advanceUntilIdle()

        val afficherLusApres = vm.uiState.value.afficherLus
        assertNotEquals(
            "afficherLus doit changer pour déclencher le LaunchedEffect de scroll reset",
            afficherLusAvant, afficherLusApres
        )
        assertEquals(false, afficherLusApres)
    }

    @Test
    fun `changer afficherNonLus modifie la cle de scroll reset dans uiState`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1))
            .thenReturn(listOf(fakeRow("id1", "Livre A")))
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 0))
            .thenReturn(listOf(fakeRow("id2", "Livre Lu", calibreLu = 1)))
        val repo = OnKindleRepository(dao)
        val vm = OnKindleViewModel(repo)
        advanceUntilIdle()

        val afficherNonLusAvant = vm.uiState.value.afficherNonLus

        vm.setAfficherNonLus(false)
        advanceUntilIdle()

        val afficherNonLusApres = vm.uiState.value.afficherNonLus
        assertNotEquals(
            "afficherNonLus doit changer pour déclencher le LaunchedEffect de scroll reset",
            afficherNonLusAvant, afficherNonLusApres
        )
        assertEquals(false, afficherNonLusApres)
    }

    @Test
    fun `changer triMode modifie la cle de scroll reset dans uiState`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1))
            .thenReturn(listOf(fakeRow("id1", "Livre A"), fakeRow("id2", "Livre B")))
        val repo = OnKindleRepository(dao)
        val vm = OnKindleViewModel(repo)
        advanceUntilIdle()

        val triModeAvant = vm.uiState.value.triMode
        assertEquals(TriMode.ALPHA, triModeAvant)

        vm.setTriMode(TriMode.NOTE_MASQUE)
        advanceUntilIdle()

        val triModeApres = vm.uiState.value.triMode
        assertNotEquals(
            "triMode doit changer pour déclencher le LaunchedEffect de scroll reset",
            triModeAvant, triModeApres
        )
        assertEquals(TriMode.NOTE_MASQUE, triModeApres)
    }

    @Test
    fun `changer de filtre apres scroll reaffiche les livres epingles depuis le debut`() = runTest {
        // Ce test vérifie que les livres épinglés sont toujours en position 0 après
        // un changement de filtre — ils ne doivent jamais être hors-écran au-dessus.
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(afficherLus = 1, afficherNonLus = 1))
            .thenReturn(
                listOf(
                    fakeRow("pinned", "Épinglé"),
                    fakeRow("id2", "Livre B"),
                    fakeRow("id3", "Livre C"),
                    fakeRow("id4", "Livre D Lu", calibreLu = 1)
                )
            )
        whenever(dao.getOnKindleAvecConseil(afficherLus = 0, afficherNonLus = 1))
            .thenReturn(
                listOf(
                    fakeRow("pinned", "Épinglé"),
                    fakeRow("id2", "Livre B"),
                    fakeRow("id3", "Livre C")
                )
            )
        val prefs = FakeUserPreferencesRepository()
        prefs.togglePinnedReading("pinned")
        val repo = OnKindleRepository(dao)
        val vm = OnKindleViewModel(repo, prefs)
        advanceUntilIdle()

        // Vérifier que l'épinglé est bien en position 0 initialement
        assertEquals("pinned", vm.uiState.value.livres[0].livreId)
        assertEquals(true, vm.uiState.value.livres[0].isPinned)

        // Simuler un changement de filtre (comme si l'utilisateur avait scrollé puis changé)
        vm.setAfficherLus(false)
        advanceUntilIdle()

        // Après changement de filtre, l'épinglé doit toujours être en position 0
        assertEquals(
            "Le livre épinglé doit rester en position 0 après changement de filtre",
            "pinned", vm.uiState.value.livres[0].livreId
        )
        assertEquals(true, vm.uiState.value.livres[0].isPinned)
        // Et le LaunchedEffect (keyed sur afficherLus) doit déclencher scrollToItem(0) côté UI
        assertEquals(false, vm.uiState.value.afficherLus)
    }
}
