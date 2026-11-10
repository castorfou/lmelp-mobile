package com.lmelp.mobile.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.lmelp.mobile.data.model.AppUpdateCheckResult
import com.lmelp.mobile.data.model.AppUpdateState
import com.lmelp.mobile.data.model.DataUpdateState
import com.lmelp.mobile.data.model.DbInfoUi
import com.lmelp.mobile.data.model.UpdateCheckResult
import com.lmelp.mobile.data.remote.updateErrorMessage
import com.lmelp.mobile.data.repository.AppUpdateRepository
import com.lmelp.mobile.data.repository.DataUpdateRepository
import com.lmelp.mobile.data.repository.MetadataRepository
import com.lmelp.mobile.data.update.ApkInstallOutcome
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
    private val dataUpdateRepository: DataUpdateRepository,
    private val appUpdateRepository: AppUpdateRepository? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(AboutUiState())
    val uiState: StateFlow<AboutUiState> = _uiState.asStateFlow()

    private val _dataUpdateState = MutableStateFlow<DataUpdateState>(DataUpdateState.Idle)
    val dataUpdateState: StateFlow<DataUpdateState> = _dataUpdateState.asStateFlow()

    private val _appUpdateState = MutableStateFlow<AppUpdateState>(AppUpdateState.Idle)

    /** Bloc « nouvelle version de l'appli » (issue #162). */
    val appUpdateState: StateFlow<AppUpdateState> = _appUpdateState.asStateFlow()

    init {
        loadDbInfo()
        observeLastCheckResult()
        observeLastAppCheckResult()
    }

    /** Reflète le check de version de l'appli fait au lancement (issue #162). */
    private fun observeLastAppCheckResult() {
        val repo = appUpdateRepository ?: return
        viewModelScope.launch {
            repo.lastCheckResult.collect { result -> showAppCheckResult(result) }
        }
    }

    /** N'écrase pas un téléchargement ou une installation en cours. */
    private fun showAppCheckResult(result: AppUpdateCheckResult) {
        if (result is AppUpdateCheckResult.UpdateAvailable) {
            _appUpdateState.update { current ->
                if (current is AppUpdateState.Idle || current is AppUpdateState.UpdateAvailable) {
                    AppUpdateState.UpdateAvailable(result.release)
                } else {
                    current
                }
            }
        }
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
        appUpdateRepository?.let { repo ->
            // Les erreurs du check appli ne sont pas affichées : le bouton des données s'en charge.
            viewModelScope.launch { showAppCheckResult(repo.checkForUpdate()) }
        }
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

    /**
     * Télécharge l'APK de la version disponible puis appelle [install] (ApkInstaller.install
     * en production, sur le main thread car il lance une Activity). Sans version disponible, ne fait rien.
     */
    fun installAppUpdate(downloadDir: File, install: (File) -> ApkInstallOutcome) {
        val repo = appUpdateRepository ?: return
        val release = when (val state = _appUpdateState.value) {
            is AppUpdateState.UpdateAvailable -> state.release
            is AppUpdateState.NeedsPermission -> state.release
            is AppUpdateState.Installing -> state.release
            is AppUpdateState.Error -> state.release
            is AppUpdateState.Idle, is AppUpdateState.Downloading -> return
        }
        viewModelScope.launch {
            _appUpdateState.update { AppUpdateState.Downloading(release) }
            try {
                val apk = repo.downloadApk(release, downloadDir)
                _appUpdateState.update {
                    when (install(apk)) {
                        ApkInstallOutcome.InstallerLaunched -> AppUpdateState.Installing(release)
                        ApkInstallOutcome.NeedsPermission -> AppUpdateState.NeedsPermission(release)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _appUpdateState.update { AppUpdateState.Error(release, updateErrorMessage(e)) }
            }
        }
    }

    class Factory(
        private val repository: MetadataRepository,
        private val dataUpdateRepository: DataUpdateRepository,
        private val appUpdateRepository: AppUpdateRepository? = null
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return AboutViewModel(repository, dataUpdateRepository, appUpdateRepository) as T
        }
    }
}
