package com.lmelp.mobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lmelp.mobile.data.model.DataUpdateState
import com.lmelp.mobile.data.model.DbInfoUi
import com.lmelp.mobile.data.model.UpdateCheckResult
import com.lmelp.mobile.data.repository.DataUpdateRepository
import com.lmelp.mobile.data.repository.MetadataRepository
import com.lmelp.mobile.data.update.ProcessRestarter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class AboutUiState(
    val isLoading: Boolean = false,
    val dbInfo: DbInfoUi? = null,
    val error: String? = null
)

class AboutViewModel(
    private val repository: MetadataRepository,
    private val dataUpdateRepository: DataUpdateRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AboutUiState())
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

    private val _dataUpdateState = MutableStateFlow<DataUpdateState>(DataUpdateState.Idle)
    val dataUpdateState: StateFlow<DataUpdateState> = _dataUpdateState.asStateFlow()

    init {
        loadDbInfo()
        observeLastCheckResult()
    }

    /** Reflète immédiatement le résultat du check silencieux déjà fait au démarrage de l'app. */
    private fun observeLastCheckResult() {
        viewModelScope.launch {
            dataUpdateRepository.lastCheckResult.collect { result ->
                if (result is UpdateCheckResult.UpdateAvailable) {
                    _dataUpdateState.update { DataUpdateState.UpdateAvailable(result.remote) }
                }
            }
        }
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

    /** Check silencieux (démarrage) : n'expose jamais d'Error à l'UI, best-effort offline-first. */
    fun checkForUpdateSilently() {
        viewModelScope.launch {
            when (val result = dataUpdateRepository.checkForUpdate()) {
                is UpdateCheckResult.UpdateAvailable ->
                    _dataUpdateState.update { DataUpdateState.UpdateAvailable(result.remote) }
                is UpdateCheckResult.UpToDate ->
                    _dataUpdateState.update { DataUpdateState.Idle }
                is UpdateCheckResult.Error ->
                    _dataUpdateState.update { DataUpdateState.Idle }
            }
        }
    }

    /** Check manuel (bouton "Vérifier les mises à jour") : affiche une Error en cas d'échec. */
    fun checkForUpdateManually() {
        viewModelScope.launch {
            _dataUpdateState.update { DataUpdateState.Checking }
            when (val result = dataUpdateRepository.checkForUpdate()) {
                is UpdateCheckResult.UpdateAvailable ->
                    _dataUpdateState.update { DataUpdateState.UpdateAvailable(result.remote) }
                is UpdateCheckResult.UpToDate ->
                    _dataUpdateState.update { DataUpdateState.Success }
                is UpdateCheckResult.Error ->
                    _dataUpdateState.update { DataUpdateState.Error(result.message) }
            }
        }
    }

    /**
     * Déclenche le téléchargement + remplacement suite à un clic sur "Mettre à jour".
     * onSuccess est appelé uniquement si le remplacement a réussi (redémarrage du process).
     */
    fun applyUpdate(targetDbFile: File, tempDir: File, onSuccess: () -> Unit) {
        viewModelScope.launch {
            _dataUpdateState.update { DataUpdateState.Downloading }
            val result = dataUpdateRepository.applyUpdate(targetDbFile, tempDir)
            if (result is DataUpdateState.Success) {
                // Message explicite avant fermeture du process, pour éviter que
                // l'utilisateur ne relance l'app manuellement pendant la fenêtre
                // de redémarrage (issue #118, retour utilisateur).
                _dataUpdateState.update { DataUpdateState.Restarting }
                delay(ProcessRestarter.RESTART_DELAY_MS)
                onSuccess()
            } else {
                _dataUpdateState.update { result }
            }
        }
    }

    class Factory(
        private val repository: MetadataRepository,
        private val dataUpdateRepository: DataUpdateRepository
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AboutViewModel(repository, dataUpdateRepository) as T
        }
    }
}
