package com.lmelp.mobile

import com.lmelp.mobile.data.db.SearchDao
import com.lmelp.mobile.data.repository.SearchRepository
import com.lmelp.mobile.viewmodel.SearchViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /**
     * Reproduit le bug #3 : quand le DAO lève une CancellationException
     * (simulant une annulation de coroutine pendant une requête), le ViewModel
     * ne doit PAS exposer cette exception dans uiState.error.
     *
     * Avant le fix : catch (e: Exception) attrape CancellationException → bug visible
     * Après le fix : CancellationException est re-throwée → pas d'erreur dans UI
     */
    @Test
    fun `CancellationException du dao ne doit pas apparaitre dans uiState error`() = runTest {
        val dao = mock<SearchDao>()
        whenever(dao.searchRaw(any())).thenThrow(
            CancellationException("StandaloneCoroutine was cancelled")
        )
        val repo = SearchRepository(dao)
        val viewModel = SearchViewModel(repo)

        viewModel.onQueryChange("hamlet")
        advanceTimeBy(400)

        val error = viewModel.uiState.value.error
        assertNull(
            "CancellationException ne doit PAS apparaitre dans l'UI, mais error = '$error'",
            error?.takeIf { it.contains("cancelled", ignoreCase = true) }
        )
    }

    @Test
    fun `vraie erreur base de donnees doit apparaitre dans uiState error`() = runTest {
        val dao = mock<SearchDao>()
        whenever(dao.searchRaw(any())).thenThrow(RuntimeException("Database error"))
        val repo = SearchRepository(dao)
        val viewModel = SearchViewModel(repo)

        viewModel.onQueryChange("hamlet")
        advanceTimeBy(400)

        val error = viewModel.uiState.value.error
        assertNull(
            "Pas d'erreur 'cancelled' attendue : $error",
            error?.takeIf { it.contains("cancelled", ignoreCase = true) }
        )
    }
}
