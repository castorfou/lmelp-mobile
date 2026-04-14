package com.lmelp.mobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lmelp.mobile.data.model.MonPalmaresItemUi
import com.lmelp.mobile.data.model.PalmaresUi
import com.lmelp.mobile.data.repository.PalmaresRepository
import com.lmelp.mobile.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PalmaresMode { CRITIQUES, PERSONNEL }
enum class MonPalmaresTriMode { NOTE_PERSO, DATE_LECTURE, VITESSE_ASC, VITESSE_DESC }

data class PalmaresUiState(
    val isLoading: Boolean = false,
    val palmares: List<PalmaresUi> = emptyList(),
    val monPalmares: List<MonPalmaresItemUi> = emptyList(),
    val error: String? = null,
    val afficherLus: Boolean = false,
    val afficherNonLus: Boolean = true,
    val palmaresMode: PalmaresMode = PalmaresMode.CRITIQUES,
    val monPalmaresTriMode: MonPalmaresTriMode = MonPalmaresTriMode.NOTE_PERSO,
    val showHorsMasque: Boolean = true
)

class PalmaresViewModel(
    private val repository: PalmaresRepository,
    private val userPrefsRepository: UserPreferencesRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(PalmaresUiState())
    val uiState: StateFlow<PalmaresUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val showHorsMasque = userPrefsRepository?.showHorsMasque?.first() ?: true
            _uiState.update { it.copy(showHorsMasque = showHorsMasque) }
            loadPalmares()
        }
    }

    fun setAfficherLus(afficher: Boolean) {
        _uiState.update { it.copy(afficherLus = afficher) }
        loadPalmares()
    }

    fun setAfficherNonLus(afficher: Boolean) {
        _uiState.update { it.copy(afficherNonLus = afficher) }
        loadPalmares()
    }

    fun setPalmaresMode(mode: PalmaresMode) {
        _uiState.update { it.copy(palmaresMode = mode) }
        loadPalmares()
    }

    fun setMonPalmaresTriMode(tri: MonPalmaresTriMode) {
        val current = _uiState.value.monPalmaresTriMode
        val newMode = when {
            tri == MonPalmaresTriMode.VITESSE_ASC && current == MonPalmaresTriMode.VITESSE_ASC -> MonPalmaresTriMode.VITESSE_DESC
            tri == MonPalmaresTriMode.VITESSE_ASC && current == MonPalmaresTriMode.VITESSE_DESC -> MonPalmaresTriMode.VITESSE_ASC
            else -> tri
        }
        _uiState.update { it.copy(monPalmaresTriMode = newMode) }
        loadPalmares()
    }

    fun setShowHorsMasque(show: Boolean) {
        _uiState.update { it.copy(showHorsMasque = show) }
        viewModelScope.launch {
            userPrefsRepository?.setShowHorsMasque(show)
        }
        loadPalmares()
    }

    private fun loadPalmares() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val state = _uiState.value
                when (state.palmaresMode) {
                    PalmaresMode.CRITIQUES -> {
                        val palmares = repository.getPalmaresFiltres(
                            afficherLus = state.afficherLus,
                            afficherNonLus = state.afficherNonLus
                        )
                        _uiState.update { it.copy(isLoading = false, palmares = palmares) }
                    }
                    PalmaresMode.PERSONNEL -> {
                        val allItems = when (state.monPalmaresTriMode) {
                            MonPalmaresTriMode.NOTE_PERSO -> repository.getMonPalmaresUnifieParNote()
                            MonPalmaresTriMode.DATE_LECTURE -> repository.getMonPalmaresUnifieParDate()
                            MonPalmaresTriMode.VITESSE_ASC -> repository.getMonPalmaresUnifieParVitesse(ascendant = true)
                            MonPalmaresTriMode.VITESSE_DESC -> repository.getMonPalmaresUnifieParVitesse(ascendant = false)
                        }
                        val monPalmares = if (state.showHorsMasque) allItems
                            else allItems.filter { it.livreId != null }
                        _uiState.update { it.copy(isLoading = false, monPalmares = monPalmares) }
                    }
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    class Factory(
        private val repository: PalmaresRepository,
        private val userPrefsRepository: UserPreferencesRepository? = null
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PalmaresViewModel(repository, userPrefsRepository) as T
        }
    }
}
