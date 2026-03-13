package com.lmelp.mobile

import com.lmelp.mobile.data.model.SlideItem
import com.lmelp.mobile.data.repository.HomeRepository
import com.lmelp.mobile.viewmodel.HomeViewModel
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
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelExtendedTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makeSlide(id: String, titre: String, auteur: String = "") =
        SlideItem(livreId = id, titre = titre, sousTitre = auteur, urlBabelio = null, urlCouverture = null)

    private suspend fun mockRepoBase(repo: HomeRepository) {
        whenever(repo.getNbEmissions()).thenReturn("100")
        whenever(repo.getExportDate()).thenReturn("2025-01-01")
        whenever(repo.getDerniereEmission()).thenReturn(null)
        whenever(repo.getEmissionsSlides()).thenReturn(emptyList())
        whenever(repo.getPalmaresSlides()).thenReturn(emptyList())
        whenever(repo.getConseilsSlides()).thenReturn(emptyList())
    }

    @Test
    fun `emissionsSlides populated from repository`() = runTest {
        val repo = mock<HomeRepository>()
        mockRepoBase(repo)
        val slide = makeSlide("l1", "Titre émission", "Auteur")
        whenever(repo.getEmissionsSlides()).thenReturn(listOf(slide))

        val viewModel = HomeViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.emissionsSlides.size)
        assertEquals("Titre émission", state.emissionsSlides[0].titre)
    }

    @Test
    fun `palmaresSlides populated from repository`() = runTest {
        val repo = mock<HomeRepository>()
        mockRepoBase(repo)
        val slide = makeSlide("l1", "Les Misérables", "Victor Hugo")
        whenever(repo.getPalmaresSlides()).thenReturn(listOf(slide))

        val viewModel = HomeViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.palmaresSlides.size)
        assertEquals("Victor Hugo", state.palmaresSlides[0].sousTitre)
    }

    @Test
    fun `conseilsSlides populated from repository`() = runTest {
        val repo = mock<HomeRepository>()
        mockRepoBase(repo)
        val slide = makeSlide("l1", "Triste tigre", "Sinno")
        whenever(repo.getConseilsSlides()).thenReturn(listOf(slide))

        val viewModel = HomeViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.conseilsSlides.size)
        assertEquals("Triste tigre", state.conseilsSlides[0].titre)
    }

    @Test
    fun `emissionsSlides vide si repository retourne vide`() = runTest {
        val repo = mock<HomeRepository>()
        mockRepoBase(repo)

        val viewModel = HomeViewModel(repo)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.error)
        assertEquals(0, viewModel.uiState.value.emissionsSlides.size)
    }

    @Test
    fun `ticker ne change pas index si liste vide`() = runTest {
        val repo = mock<HomeRepository>()
        mockRepoBase(repo)

        val viewModel = HomeViewModel(repo)
        advanceUntilIdle()

        // Avancer de 5 minutes — ne pas appeler advanceUntilIdle() après car le ticker est infini
        testScheduler.advanceTimeBy(5 * 60 * 1000L + 1)

        assertEquals(0, viewModel.uiState.value.emissionsIndex)
        assertEquals(0, viewModel.uiState.value.palmaresIndex)
        assertEquals(0, viewModel.uiState.value.conseilsIndex)
    }

    @Test
    fun `ticker avance index quand liste non vide`() = runTest {
        val repo = mock<HomeRepository>()
        mockRepoBase(repo)
        val slides = (1..3).map { makeSlide("l$it", "Titre $it") }
        whenever(repo.getEmissionsSlides()).thenReturn(slides)

        val viewModel = HomeViewModel(repo)
        advanceUntilIdle()

        testScheduler.advanceTimeBy(5 * 60 * 1000L + 1)

        assertEquals(1, viewModel.uiState.value.emissionsIndex)
    }

    @Test
    fun `ticker boucle a zero apres le dernier element`() = runTest {
        val repo = mock<HomeRepository>()
        mockRepoBase(repo)
        val slides = (1..2).map { makeSlide("l$it", "Titre $it") }
        whenever(repo.getEmissionsSlides()).thenReturn(slides)

        val viewModel = HomeViewModel(repo)
        advanceUntilIdle()

        // 2 ticks → index revient à 0
        testScheduler.advanceTimeBy(2 * (5 * 60 * 1000L) + 1)

        assertEquals(0, viewModel.uiState.value.emissionsIndex)
    }

    @Test
    fun `couverture mise a jour dans slide apres fetch`() = runTest {
        val repo = mock<HomeRepository>()
        mockRepoBase(repo)
        val slide = SlideItem("l1", "Titre", "Auteur", urlBabelio = "https://babelio.com/l1", urlCouverture = null)
        whenever(repo.getEmissionsSlides()).thenReturn(listOf(slide))
        whenever(repo.fetchCouvertureBabelio("https://babelio.com/l1"))
            .thenReturn("https://img.example.com/cover.jpg")

        val viewModel = HomeViewModel(repo)
        advanceUntilIdle()

        assertEquals(
            "https://img.example.com/cover.jpg",
            viewModel.uiState.value.emissionsSlides[0].urlCouverture
        )
    }
}
