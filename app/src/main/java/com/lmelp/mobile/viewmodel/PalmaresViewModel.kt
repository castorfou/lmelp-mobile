package com.lmelp.mobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lmelp.mobile.data.model.PalmaresUi
import com.lmelp.mobile.data.repository.PalmaresRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PalmaresUiState(
    val isLoading: Boolean = false,
    val palmares: List<PalmaresUi> = emptyList(),
    val error: String? = null
)

class PalmaresViewModel(private val repository: PalmaresRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(PalmaresUiState())
    val uiState: StateFlow<PalmaresUiState> = _uiState.asStateFlow()

    init {
        loadPalmares()
    }

    private fun loadPalmares() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val palmares = repository.getAllPalmares()
                _uiState.update { it.copy(isLoading = false, palmares = palmares) }
            } catch (e: Exception) {
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
