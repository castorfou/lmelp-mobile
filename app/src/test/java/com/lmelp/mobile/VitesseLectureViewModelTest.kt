package com.lmelp.mobile

import com.lmelp.mobile.data.db.CalibreHorsMasqueDao
import com.lmelp.mobile.data.db.MonPalmaresRow
import com.lmelp.mobile.data.db.PalmaresDao
import com.lmelp.mobile.data.model.CalibreHorsMasqueEntity
import com.lmelp.mobile.data.repository.PalmaresRepository
import com.lmelp.mobile.viewmodel.MonPalmaresTriMode
import com.lmelp.mobile.viewmodel.PalmaresMode
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests TDD pour la vitesse de lecture dans le ViewModel (issue #93).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class VitesseLectureViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeMonPalmaresRow(
        livreId: String,
        dateLecture: String? = null
    ) = MonPalmaresRow(
        livreId = livreId,
        titre = "Titre $livreId",
        auteurNom = null,
        noteMoyenne = 7.0,
        nbAvis = 2,
        nbCritiques = 2,
        calibreRating = 8.0,
        urlCover = null,
        dateLecture = dateLecture
    )

    private fun makeHorsMasque(
        id: String,
        dateLecture: String? = null
    ) = CalibreHorsMasqueEntity(
        id = id,
        titre = "HM $id",
        auteurNom = null,
        calibreRating = 7.0,
        dateLecture = dateLecture
    )

    // --- Test 1 : clic sur Vitesse → mode VITESSE_ASC ---

    @Test
    fun `clic_vitesse_passe_en_mode_VITESSE_ASC`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        whenever(palmaresDao.getMonPalmares()).thenReturn(
            listOf(
                makeMonPalmaresRow("A", dateLecture = "2024-01-01"),
                makeMonPalmaresRow("B", dateLecture = "2024-02-01")
            )
        )
        val repo = PalmaresRepository(palmaresDao)
        val viewModel = PalmaresViewModel(repo)
        advanceUntilIdle()

        viewModel.setPalmaresMode(PalmaresMode.PERSONNEL)
        advanceUntilIdle()

        viewModel.setMonPalmaresTriMode(MonPalmaresTriMode.VITESSE_ASC)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(MonPalmaresTriMode.VITESSE_ASC, state.monPalmaresTriMode)
        assertFalse(state.isLoading)
    }

    // --- Test 2 : reclique sur Vitesse → bascule en VITESSE_DESC ---

    @Test
    fun `reclique_vitesse_bascule_VITESSE_DESC`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        whenever(palmaresDao.getMonPalmares()).thenReturn(
            listOf(
                makeMonPalmaresRow("A", dateLecture = "2024-01-01"),
                makeMonPalmaresRow("B", dateLecture = "2024-02-01")
            )
        )
        val repo = PalmaresRepository(palmaresDao)
        val viewModel = PalmaresViewModel(repo)
        advanceUntilIdle()

        viewModel.setPalmaresMode(PalmaresMode.PERSONNEL)
        advanceUntilIdle()

        // 1er clic → VITESSE_ASC
        viewModel.setMonPalmaresTriMode(MonPalmaresTriMode.VITESSE_ASC)
        advanceUntilIdle()
        assertEquals(MonPalmaresTriMode.VITESSE_ASC, viewModel.uiState.value.monPalmaresTriMode)

        // 2e clic → VITESSE_DESC
        viewModel.setMonPalmaresTriMode(MonPalmaresTriMode.VITESSE_ASC)
        advanceUntilIdle()
        assertEquals(MonPalmaresTriMode.VITESSE_DESC, viewModel.uiState.value.monPalmaresTriMode)
    }

    // --- Test 3 : 3e clic sur Vitesse → revient VITESSE_ASC ---

    @Test
    fun `troisieme_clic_vitesse_revient_VITESSE_ASC`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        whenever(palmaresDao.getMonPalmares()).thenReturn(
            listOf(
                makeMonPalmaresRow("A", dateLecture = "2024-01-01"),
                makeMonPalmaresRow("B", dateLecture = "2024-02-01")
            )
        )
        val repo = PalmaresRepository(palmaresDao)
        val viewModel = PalmaresViewModel(repo)
        advanceUntilIdle()

        viewModel.setPalmaresMode(PalmaresMode.PERSONNEL)
        advanceUntilIdle()

        viewModel.setMonPalmaresTriMode(MonPalmaresTriMode.VITESSE_ASC)
        advanceUntilIdle()
        viewModel.setMonPalmaresTriMode(MonPalmaresTriMode.VITESSE_ASC)
        advanceUntilIdle()
        viewModel.setMonPalmaresTriMode(MonPalmaresTriMode.VITESSE_ASC)
        advanceUntilIdle()

        assertEquals(MonPalmaresTriMode.VITESSE_ASC, viewModel.uiState.value.monPalmaresTriMode)
    }

    // --- Test 4 : filtre hors Masque s'applique après calcul ---

    @Test
    fun `filtre_horsMasque_sApplique_apres_calcul_vitesse`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        val horsMasqueDao = mock<CalibreHorsMasqueDao>()

        // Livre Masque (1er lu → exclu du calcul)
        whenever(palmaresDao.getMonPalmares()).thenReturn(
            listOf(makeMonPalmaresRow("masque1", dateLecture = "2024-01-01"))
        )
        // Livre hors Masque (2e lu → a une vitesse calculable)
        whenever(horsMasqueDao.getAll()).thenReturn(
            listOf(makeHorsMasque("hm1", dateLecture = "2024-02-01"))
        )

        val repo = PalmaresRepository(palmaresDao, horsMasqueDao)
        val viewModel = PalmaresViewModel(repo)
        advanceUntilIdle()

        viewModel.setPalmaresMode(PalmaresMode.PERSONNEL)
        advanceUntilIdle()

        // Avec hors Masque visible
        viewModel.setMonPalmaresTriMode(MonPalmaresTriMode.VITESSE_ASC)
        advanceUntilIdle()
        val avecHorsMasque = viewModel.uiState.value.monPalmares
        assertTrue("hm1 doit apparaître quand showHorsMasque=true",
            avecHorsMasque.any { it.id == "hm1" })

        // Sans hors Masque (filter livreId != null)
        viewModel.setShowHorsMasque(false)
        advanceUntilIdle()
        val sansHorsMasque = viewModel.uiState.value.monPalmares
        assertTrue("hm1 doit disparaître quand showHorsMasque=false",
            sansHorsMasque.none { it.id == "hm1" })
    }
}
