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

/** Calcule le nouvel index après navigation (modulo, sans effet si liste vide ou singleton). */
internal fun nextSlideIndex(currentIndex: Int, size: Int): Int =
    if (size <= 1) 0 else (currentIndex + 1) % size

internal fun prevSlideIndex(currentIndex: Int, size: Int): Int =
    if (size <= 1) 0 else (currentIndex - 1 + size) % size

/** Retourne un index initial aléatoire dans [0, size-1], ou 0 si vide/singleton. */
internal fun randomInitialIndex(size: Int): Int =
    if (size <= 1) 0 else (0 until size).random()

/**
 * Tire un délai selon une distribution normale N(meanMs, stdDevMs), tronquée à minMs.
 * Le paramètre [random] est injectable pour faciliter les tests déterministes.
 */
internal fun sampleTickerDelayMs(
    meanMs: Long = 5_000L,
    stdDevMs: Long = 2_000L,
    minMs: Long = 1_000L,
    random: java.util.Random = java.util.Random()
): Long {
    val sample = meanMs + (random.nextGaussian() * stdDevMs).toLong()
    return maxOf(sample, minMs)
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val nbEmissions: String = "",
    val exportDate: String = "",
    val derniereEmission: DerniereEmissionUi? = null,
    val emissionsSlides: List<SlideItem> = emptyList(),
    val palmaresSlides: List<SlideItem> = emptyList(),
    val conseilsSlides: List<SlideItem> = emptyList(),
    val onkindleSlides: List<SlideItem> = emptyList(),
    val emissionsIndex: Int = 0,
    val palmaresIndex: Int = 0,
    val conseilsIndex: Int = 0,
    val onkindleIndex: Int = 0,
    val error: String? = null
)

class HomeViewModel(private val repository: HomeRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadStats() }
        startTicker()
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
            val onkindleSlides = repository.getOnKindleSlides()

            _uiState.update {
                it.copy(
                    isLoading = false,
                    nbEmissions = nbEmissions,
                    exportDate = exportDate,
                    derniereEmission = derniereEmission,
                    emissionsSlides = emissionsSlides,
                    palmaresSlides = palmaresSlides,
                    conseilsSlides = conseilsSlides,
                    onkindleSlides = onkindleSlides,
                    emissionsIndex = randomInitialIndex(emissionsSlides.size),
                    palmaresIndex  = randomInitialIndex(palmaresSlides.size),
                    conseilsIndex  = randomInitialIndex(conseilsSlides.size),
                    onkindleIndex  = randomInitialIndex(onkindleSlides.size),
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            _uiState.update { it.copy(isLoading = false, error = e.message) }
        }
    }

    /** Lance 4 coroutines indépendantes, chacune avec son propre délai stochastique N(5s, 2s). */
    private fun startTicker() {
        viewModelScope.launch {
            tickerLoop(
                getSize = { state -> state.emissionsSlides.size },
                updateIndex = { state, idx -> state.copy(emissionsIndex = idx) }
            )
        }
        viewModelScope.launch {
            tickerLoop(
                getSize = { state -> state.palmaresSlides.size },
                updateIndex = { state, idx -> state.copy(palmaresIndex = idx) }
            )
        }
        viewModelScope.launch {
            tickerLoop(
                getSize = { state -> state.conseilsSlides.size },
                updateIndex = { state, idx -> state.copy(conseilsIndex = idx) }
            )
        }
        viewModelScope.launch {
            tickerLoop(
                getSize = { state -> state.onkindleSlides.size },
                updateIndex = { state, idx -> state.copy(onkindleIndex = idx) }
            )
        }
    }

    private suspend fun tickerLoop(
        getSize: (HomeUiState) -> Int,
        updateIndex: (HomeUiState, Int) -> HomeUiState
    ) {
        while (true) {
            delay(sampleTickerDelayMs())
            val size = getSize(_uiState.value)
            if (size > 1) {
                _uiState.update { state -> updateIndex(state, (0 until size).random()) }
            }
        }
    }

    fun nextEmissionsSlide() {
        _uiState.update { it.copy(emissionsIndex = nextSlideIndex(it.emissionsIndex, it.emissionsSlides.size)) }
    }

    fun prevEmissionsSlide() {
        _uiState.update { it.copy(emissionsIndex = prevSlideIndex(it.emissionsIndex, it.emissionsSlides.size)) }
    }

    fun nextPalmaresSlide() {
        _uiState.update { it.copy(palmaresIndex = nextSlideIndex(it.palmaresIndex, it.palmaresSlides.size)) }
    }

    fun prevPalmaresSlide() {
        _uiState.update { it.copy(palmaresIndex = prevSlideIndex(it.palmaresIndex, it.palmaresSlides.size)) }
    }

    fun nextConseilsSlide() {
        _uiState.update { it.copy(conseilsIndex = nextSlideIndex(it.conseilsIndex, it.conseilsSlides.size)) }
    }

    fun prevConseilsSlide() {
        _uiState.update { it.copy(conseilsIndex = prevSlideIndex(it.conseilsIndex, it.conseilsSlides.size)) }
    }

    fun nextOnkindleSlide() {
        _uiState.update { it.copy(onkindleIndex = nextSlideIndex(it.onkindleIndex, it.onkindleSlides.size)) }
    }

    fun prevOnkindleSlide() {
        _uiState.update { it.copy(onkindleIndex = prevSlideIndex(it.onkindleIndex, it.onkindleSlides.size)) }
    }

    class Factory(private val repository: HomeRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(repository) as T
        }
    }
}
