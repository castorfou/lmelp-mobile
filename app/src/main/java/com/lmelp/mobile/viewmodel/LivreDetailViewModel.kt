package com.lmelp.mobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lmelp.mobile.data.model.LivreDetailUi
import com.lmelp.mobile.data.repository.LivresRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LivreDetailUiState(
    val isLoading: Boolean = false,
    val livre: LivreDetailUi? = null,
    val error: String? = null
)

class LivreDetailViewModel(
    private val repository: LivresRepository,
    private val livreId: String
) : ViewModel() {

    private val _uiState = MutableStateFlow(LivreDetailUiState())
    val uiState: StateFlow<LivreDetailUiState> = _uiState.asStateFlow()

    init {
        loadLivre()
    }

    private fun loadLivre() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val livre = repository.getLivreDetail(livreId)
                _uiState.update { it.copy(isLoading = false, livre = livre) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    class Factory(
        private val repository: LivresRepository,
        private val livreId: String
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return LivreDetailViewModel(repository, livreId) as T
        }
    }
}
