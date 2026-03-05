package com.lmelp.mobile.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lmelp.mobile.data.model.EmissionDetailUi
import com.lmelp.mobile.data.model.EmissionUi
import com.lmelp.mobile.data.repository.EmissionsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EmissionsUiState(
    val isLoading: Boolean = false,
    val emissions: List<EmissionUi> = emptyList(),
    val error: String? = null
)

data class EmissionDetailUiState(
    val isLoading: Boolean = false,
    val emission: EmissionDetailUi? = null,
    val error: String? = null
)

class EmissionsViewModel(private val repository: EmissionsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(EmissionsUiState())
    val uiState: StateFlow<EmissionsUiState> = _uiState.asStateFlow()

    private val _detailState = MutableStateFlow(EmissionDetailUiState())
    val detailState: StateFlow<EmissionDetailUiState> = _detailState.asStateFlow()

    init {
        loadEmissions()
    }

    private fun loadEmissions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                Log.d("EmissionsVM", "Loading emissions...")
                val emissions = repository.getAllEmissions()
                Log.d("EmissionsVM", "Loaded ${emissions.size} emissions")
                _uiState.update { it.copy(isLoading = false, emissions = emissions) }
            } catch (e: Exception) {
                Log.e("EmissionsVM", "Error loading emissions: ${e.message}", e)
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun loadEmissionDetail(emissionId: String) {
        viewModelScope.launch {
            _detailState.update { it.copy(isLoading = true) }
            try {
                val detail = repository.getEmissionDetail(emissionId)
                _detailState.update { it.copy(isLoading = false, emission = detail) }
            } catch (e: Exception) {
                _detailState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    class Factory(private val repository: EmissionsRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return EmissionsViewModel(repository) as T
        }
    }
}
