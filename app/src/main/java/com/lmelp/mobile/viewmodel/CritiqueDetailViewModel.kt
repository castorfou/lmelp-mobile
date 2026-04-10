package com.lmelp.mobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lmelp.mobile.data.model.CritiqueDetailUi
import com.lmelp.mobile.data.repository.CritiquesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CritiqueDetailUiState(
    val isLoading: Boolean = false,
    val critique: CritiqueDetailUi? = null,
    val error: String? = null
)

class CritiqueDetailViewModel(
    private val repository: CritiquesRepository,
    private val critiqueId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(CritiqueDetailUiState())
    val uiState: StateFlow<CritiqueDetailUiState> = _uiState.asStateFlow()

    init {
        loadCritique()
    }

    private fun loadCritique() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val critique = repository.getCritiqueDetail(critiqueId)
                _uiState.update { it.copy(isLoading = false, critique = critique) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    class Factory(
        private val repository: CritiquesRepository,
        private val critiqueId: String
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return CritiqueDetailViewModel(repository, critiqueId) as T
        }
    }
}
