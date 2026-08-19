package com.lmelp.mobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lmelp.mobile.data.model.DbInfoUi
import com.lmelp.mobile.data.repository.MetadataRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AboutUiState(
    val isLoading: Boolean = false,
    val dbInfo: DbInfoUi? = null,
    val error: String? = null
)

class AboutViewModel(private val repository: MetadataRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(AboutUiState())
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

    init {
        loadDbInfo()
    }

    private fun loadDbInfo() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val dbInfo = repository.getDbInfo()
                _uiState.update { it.copy(isLoading = false, dbInfo = dbInfo) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    class Factory(private val repository: MetadataRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AboutViewModel(repository) as T
        }
    }
}
