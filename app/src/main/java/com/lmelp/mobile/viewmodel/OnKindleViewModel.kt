package com.lmelp.mobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lmelp.mobile.data.model.OnKindleUi
import com.lmelp.mobile.data.repository.OnKindleRepository
import com.lmelp.mobile.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TriMode { ALPHA, NOTE_MASQUE, NOTE_CONSEIL }

data class OnKindleUiState(
    val isLoading: Boolean = false,
    val livres: List<OnKindleUi> = emptyList(),
    val error: String? = null,
    val afficherLus: Boolean = true,
    val afficherNonLus: Boolean = true,
    val triMode: TriMode = TriMode.ALPHA,
    val pinnedBookIds: Set<String> = emptySet()
)

class OnKindleViewModel(
    private val repository: OnKindleRepository,
    private val pinnedStorage: UserPreferencesRepository.PinnedReadingStorage? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnKindleUiState())
    val uiState: StateFlow<OnKindleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val pinned = pinnedStorage?.pinnedReading?.first() ?: emptySet()
            _uiState.update { it.copy(pinnedBookIds = pinned) }
            loadOnKindle()
        }
    }

    fun setAfficherLus(afficher: Boolean) {
        _uiState.update { it.copy(afficherLus = afficher) }
        loadOnKindle()
    }

    fun setAfficherNonLus(afficher: Boolean) {
        _uiState.update { it.copy(afficherNonLus = afficher) }
        loadOnKindle()
    }

    fun setTriMode(mode: TriMode) {
        _uiState.update { it.copy(triMode = mode) }
        loadOnKindle()
    }

    fun togglePin(livreId: String) {
        viewModelScope.launch {
            pinnedStorage?.togglePinnedReading(livreId)
            val pinned = pinnedStorage?.pinnedReading?.first() ?: emptySet()
            _uiState.update { it.copy(pinnedBookIds = pinned) }
            loadOnKindle()
        }
    }

    private fun loadOnKindle() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val state = _uiState.value
                val livres = repository.getOnKindle(
                    afficherLus = state.afficherLus,
                    afficherNonLus = state.afficherNonLus,
                    triMode = state.triMode
                )

                // Auto-désépinglage : si un livre épinglé est maintenant lu dans Calibre,
                // on retire l'épingle (le livre a été terminé et la DB a été regénérée)
                val pinnedIds = state.pinnedBookIds.toMutableSet()
                livres.filter { it.calibreLu && it.livreId in pinnedIds }.forEach { livre ->
                    pinnedStorage?.removePinned(livre.livreId)
                    pinnedIds.remove(livre.livreId)
                }

                // Annoter chaque livre avec son statut épinglé
                val annotated = livres.map { it.copy(isPinned = it.livreId in pinnedIds) }

                // Livres épinglés en tête (dans leur ordre de tri), puis les autres
                val sorted = annotated.filter { it.isPinned } + annotated.filter { !it.isPinned }

                _uiState.update { it.copy(isLoading = false, livres = sorted, error = null, pinnedBookIds = pinnedIds) }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    class Factory(
        private val repository: OnKindleRepository,
        private val pinnedStorage: UserPreferencesRepository.PinnedReadingStorage? = null
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return OnKindleViewModel(repository, pinnedStorage) as T
        }
    }
}
