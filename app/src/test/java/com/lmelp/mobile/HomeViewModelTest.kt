package com.lmelp.mobile

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
class HomeViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun HomeRepository.stubEmpty() {
        whenever(getNbEmissions()).thenReturn("0")
        whenever(getExportDate()).thenReturn("—")
        whenever(getDerniereEmission()).thenReturn(null)
        whenever(getEmissionsSlides()).thenReturn(emptyList())
        whenever(getPalmaresSlides()).thenReturn(emptyList())
        whenever(getConseilsSlides()).thenReturn(emptyList())
    }

    @Test
    fun `uiState loads nbEmissions and exportDate from repository`() = runTest {
        val repo = mock<HomeRepository>()
        whenever(repo.getNbEmissions()).thenReturn("350")
        whenever(repo.getExportDate()).thenReturn("2025-01-15")
        whenever(repo.getDerniereEmission()).thenReturn(null)
        whenever(repo.getEmissionsSlides()).thenReturn(emptyList())
        whenever(repo.getPalmaresSlides()).thenReturn(emptyList())
        whenever(repo.getConseilsSlides()).thenReturn(emptyList())

        val viewModel = HomeViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("350", state.nbEmissions)
        assertEquals("2025-01-15", state.exportDate)
        assertNull(state.error)
    }

    @Test
    fun `uiState exposes error when repository throws`() = runTest {
        val repo = mock<HomeRepository>()
        whenever(repo.getNbEmissions()).thenThrow(RuntimeException("DB error"))

        val viewModel = HomeViewModel(repo)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("DB error", state.error)
    }
}
