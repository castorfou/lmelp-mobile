package com.lmelp.mobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lmelp.mobile.data.model.AuteurDetailUi
import com.lmelp.mobile.data.repository.AuteursRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AuteurDetailUiState(
    val isLoading: Boolean = false,
    val auteur: AuteurDetailUi? = null,
    val error: String? = null
)

class AuteurDetailViewModel(
    private val repository: AuteursRepository,
    private val auteurId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuteurDetailUiState())
    val uiState: StateFlow<AuteurDetailUiState> = _uiState.asStateFlow()

    init {
        loadAuteur()
    }

    private fun loadAuteur() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val auteur = repository.getAuteurDetail(auteurId)
                _uiState.update { it.copy(isLoading = false, auteur = auteur) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    class Factory(
        private val repository: AuteursRepository,
        private val auteurId: String
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AuteurDetailViewModel(repository, auteurId) as T
        }
    }
}
