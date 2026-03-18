package com.lmelp.mobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lmelp.mobile.data.model.PalmaresUi
import com.lmelp.mobile.data.repository.PalmaresRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PalmaresMode { CRITIQUES, PERSONNEL }
enum class MonPalmaresTriMode { NOTE_PERSO, DATE_LECTURE }

data class PalmaresUiState(
    val isLoading: Boolean = false,
    val palmares: List<PalmaresUi> = emptyList(),
    val error: String? = null,
    val afficherLus: Boolean = false,
    val afficherNonLus: Boolean = true,
    val palmaresMode: PalmaresMode = PalmaresMode.CRITIQUES,
    val monPalmaresTriMode: MonPalmaresTriMode = MonPalmaresTriMode.NOTE_PERSO
)

class PalmaresViewModel(private val repository: PalmaresRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(PalmaresUiState())
    val uiState: StateFlow<PalmaresUiState> = _uiState.asStateFlow()

    init {
        loadPalmares()
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
        _uiState.update { it.copy(monPalmaresTriMode = tri) }
        loadPalmares()
    }

    private fun loadPalmares() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val state = _uiState.value
                val palmares = when (state.palmaresMode) {
                    PalmaresMode.CRITIQUES -> repository.getPalmaresFiltres(
                        afficherLus = state.afficherLus,
                        afficherNonLus = state.afficherNonLus
                    )
                    PalmaresMode.PERSONNEL -> when (state.monPalmaresTriMode) {
                        MonPalmaresTriMode.NOTE_PERSO -> repository.getMonPalmares()
                        MonPalmaresTriMode.DATE_LECTURE -> repository.getMonPalmaresParDate()
                    }
                }
                _uiState.update { it.copy(isLoading = false, palmares = palmares) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    class Factory(private val repository: PalmaresRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return PalmaresViewModel(repository) as T
        }
    }
}
