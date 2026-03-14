package com.lmelp.mobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lmelp.mobile.data.model.OnKindleUi
import com.lmelp.mobile.data.repository.OnKindleRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OnKindleUiState(
    val isLoading: Boolean = false,
    val livres: List<OnKindleUi> = emptyList(),
    val error: String? = null,
    val afficherLus: Boolean = true,
    val afficherNonLus: Boolean = true,
    val triParNote: Boolean = false
)

class OnKindleViewModel(private val repository: OnKindleRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(OnKindleUiState())
    val uiState: StateFlow<OnKindleUiState> = _uiState.asStateFlow()

    init {
        loadOnKindle()
    }

    fun setAfficherLus(afficher: Boolean) {
        _uiState.update { it.copy(afficherLus = afficher) }
        loadOnKindle()
    }

    fun setAfficherNonLus(afficher: Boolean) {
        _uiState.update { it.copy(afficherNonLus = afficher) }
        loadOnKindle()
    }

    fun setTriParNote(tri: Boolean) {
        _uiState.update { it.copy(triParNote = tri) }
        loadOnKindle()
    }

    private fun loadOnKindle() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val state = _uiState.value
                val livres = repository.getOnKindle(
                    afficherLus = state.afficherLus,
                    afficherNonLus = state.afficherNonLus,
                    triParNote = state.triParNote
                )
                _uiState.update { it.copy(isLoading = false, livres = livres, error = null) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    class Factory(private val repository: OnKindleRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return OnKindleViewModel(repository) as T
        }
    }
}
