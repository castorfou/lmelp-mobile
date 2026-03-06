package com.lmelp.mobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lmelp.mobile.data.model.CritiqueUi
import com.lmelp.mobile.data.repository.CritiquesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CritiquesUiState(
    val isLoading: Boolean = false,
    val critiques: List<CritiqueUi> = emptyList(),
    val error: String? = null
)

class CritiquesViewModel(private val repository: CritiquesRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(CritiquesUiState())
    val uiState: StateFlow<CritiquesUiState> = _uiState.asStateFlow()

    init {
        loadCritiques()
    }

    private fun loadCritiques() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val critiques = repository.getAllCritiques()
                _uiState.update { it.copy(isLoading = false, critiques = critiques) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    class Factory(private val repository: CritiquesRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return CritiquesViewModel(repository) as T
        }
    }
}
