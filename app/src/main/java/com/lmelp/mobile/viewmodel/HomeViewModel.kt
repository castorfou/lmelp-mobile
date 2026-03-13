package com.lmelp.mobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lmelp.mobile.data.model.DerniereEmissionUi
import com.lmelp.mobile.data.model.SlideItem
import com.lmelp.mobile.data.repository.HomeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TICKER_INTERVAL_MS = 10 * 1000L

data class HomeUiState(
    val isLoading: Boolean = false,
    val nbEmissions: String = "",
    val exportDate: String = "",
    val derniereEmission: DerniereEmissionUi? = null,
    val emissionsSlides: List<SlideItem> = emptyList(),
    val palmaresSlides: List<SlideItem> = emptyList(),
    val conseilsSlides: List<SlideItem> = emptyList(),
    val emissionsIndex: Int = 0,
    val palmaresIndex: Int = 0,
    val conseilsIndex: Int = 0,
    val error: String? = null
)

class HomeViewModel(private val repository: HomeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadStats() }
        viewModelScope.launch { startTicker() }
    }

    private suspend fun loadStats() {
        _uiState.update { it.copy(isLoading = true) }
        try {
            val nbEmissions = repository.getNbEmissions()
            val exportDate = repository.getExportDate()
            val derniereEmission = repository.getDerniereEmission()?.let {
                if (it.titre != null && it.date != null)
                    DerniereEmissionUi(titre = it.titre, date = it.date)
                else null
            }
            val emissionsSlides = repository.getEmissionsSlides()
            val palmaresSlides = repository.getPalmaresSlides()
            val conseilsSlides = repository.getConseilsSlides()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    nbEmissions = nbEmissions,
                    exportDate = exportDate,
                    derniereEmission = derniereEmission,
                    emissionsSlides = emissionsSlides,
                    palmaresSlides = palmaresSlides,
                    conseilsSlides = conseilsSlides
                )
            }

            // Chargement des couvertures en background
            emissionsSlides.forEachIndexed { i, s ->
                launchCouvertureLoad(s, i,
                    selectList = { it.emissionsSlides },
                    updateList = { state, list -> state.copy(emissionsSlides = list) }
                )
            }
            palmaresSlides.forEachIndexed { i, s ->
                launchCouvertureLoad(s, i,
                    selectList = { it.palmaresSlides },
                    updateList = { state, list -> state.copy(palmaresSlides = list) }
                )
            }
            conseilsSlides.forEachIndexed { i, s ->
                launchCouvertureLoad(s, i,
                    selectList = { it.conseilsSlides },
                    updateList = { state, list -> state.copy(conseilsSlides = list) }
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    private fun launchCouvertureLoad(
        slide: SlideItem,
        slideIndex: Int,
        selectList: (HomeUiState) -> List<SlideItem>,
        updateList: (HomeUiState, List<SlideItem>) -> HomeUiState
    ) {
        if (slide.urlBabelio == null || slide.urlCouverture != null) return
        viewModelScope.launch {
            val url = try {
                repository.fetchCouvertureBabelio(slide.urlBabelio)
            } catch (_: Exception) { return@launch }
            if (url != null) {
                _uiState.update { state ->
                    val updated = selectList(state).toMutableList()
                    if (slideIndex < updated.size) {
                        updated[slideIndex] = updated[slideIndex].copy(urlCouverture = url)
                    }
                    updateList(state, updated)
                }
            }
        }
    }

    private suspend fun startTicker() {
        while (true) {
            delay(TICKER_INTERVAL_MS)
            _uiState.update { state ->
                state.copy(
                    emissionsIndex = if (state.emissionsSlides.size <= 1) 0
                                     else (0 until state.emissionsSlides.size).random(),
                    palmaresIndex  = if (state.palmaresSlides.size <= 1) 0
                                     else (0 until state.palmaresSlides.size).random(),
                    conseilsIndex  = if (state.conseilsSlides.size <= 1) 0
                                     else (0 until state.conseilsSlides.size).random()
                )
            }
        }
    }

    class Factory(private val repository: HomeRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository) as T
        }
    }
}
