package com.lmelp.mobile

import com.lmelp.mobile.data.db.MetadataDao
import com.lmelp.mobile.data.model.DbMetadataEntity
import com.lmelp.mobile.data.repository.MetadataRepository
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

    @Test
    fun `uiState loads nbEmissions and exportDate from repository`() = runTest {
        val dao = mock<MetadataDao>()
        whenever(dao.getAllMetadata()).thenReturn(
            listOf(
                DbMetadataEntity("export_date", "2025-01-15"),
                DbMetadataEntity("version", "42"),
                DbMetadataEntity("nb_emissions", "350"),
                DbMetadataEntity("nb_livres", "700"),
                DbMetadataEntity("nb_avis", "3500"),
            )
        )
        val repo = MetadataRepository(dao)
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
        val dao = mock<MetadataDao>()
        whenever(dao.getAllMetadata()).thenThrow(RuntimeException("DB error"))
        val repo = MetadataRepository(dao)
        val viewModel = HomeViewModel(repo)

        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals("DB error", state.error)
    }
}
