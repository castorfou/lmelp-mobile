package com.lmelp.mobile.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmelp.mobile.BuildConfig
import com.lmelp.mobile.data.model.AppUpdateState
import com.lmelp.mobile.data.model.DataUpdateState
import com.lmelp.mobile.data.model.DbInfoUi
import com.lmelp.mobile.data.repository.AppUpdateRepository
import com.lmelp.mobile.data.repository.DataUpdateRepository
import com.lmelp.mobile.data.repository.MetadataRepository
import com.lmelp.mobile.data.update.ApkInstallOutcome
import com.lmelp.mobile.viewmodel.AboutViewModel
import java.io.File

data class ChangelogEntry(val hash: String, val message: String, val date: String)

/** Résumé lisible des infos DB pour AboutScreen (issue #116). */
fun formatDbInfoSummary(dbInfo: DbInfoUi): String =
    "Export du ${dbInfo.exportDate} — " +
        "${dbInfo.nbEmissions} émissions, ${dbInfo.nbLivres} livres, ${dbInfo.nbAvis} avis"

/** Ligne de version de l'en-tête : versionName de l'APK, commit et date de build (issue #161). */
fun formatVersionLine(versionName: String, gitCommit: String, buildDate: String): String =
    "Version $versionName · $gitCommit ($buildDate)"

/** Libellé du bouton/état de mise à jour des données (issue #118). */
fun formatUpdateStateLabel(state: DataUpdateState): String = when (state) {
    is DataUpdateState.Idle -> "Vérifier les mises à jour"
    is DataUpdateState.Checking -> "Vérification en cours…"
    is DataUpdateState.UpdateAvailable ->
        "Mise à jour disponible (export du ${state.remote.exportDate}) — Mettre à jour"
    is DataUpdateState.Downloading -> "Téléchargement en cours…"
    is DataUpdateState.Verifying -> "Vérification du fichier téléchargé…"
    is DataUpdateState.Replacing -> "Mise à jour de la base…"
    is DataUpdateState.Restarting -> "Mise à jour appliquée ! Veuillez rouvrir l'application."
    is DataUpdateState.Success -> "Base à jour"
    is DataUpdateState.Error -> "Erreur : ${state.message}"
}

/** Titre du bloc « nouvelle version de l'appli », null s'il n'y en a pas (issue #162). */
fun formatAppUpdateTitle(state: AppUpdateState): String? = when (state) {
    is AppUpdateState.Idle -> null
    is AppUpdateState.UpdateAvailable -> "Nouvelle version ${state.release.versionName} disponible"
    is AppUpdateState.Downloading -> "Nouvelle version ${state.release.versionName} disponible"
    is AppUpdateState.NeedsPermission -> "Nouvelle version ${state.release.versionName} disponible"
    is AppUpdateState.Installing -> "Nouvelle version ${state.release.versionName} disponible"
    is AppUpdateState.Error -> "Nouvelle version ${state.release.versionName} disponible"
}

/** Libellé du bouton du bloc « nouvelle version de l'appli » (issue #162). */
fun formatAppUpdateButtonLabel(state: AppUpdateState): String = when (state) {
    is AppUpdateState.Idle, is AppUpdateState.UpdateAvailable -> "Télécharger et installer"
    is AppUpdateState.Downloading -> "Téléchargement en cours…"
    is AppUpdateState.NeedsPermission -> "Autoriser LMELP à installer des applications, puis réessayer"
    is AppUpdateState.Installing -> "Relancer l'installation"
    is AppUpdateState.Error -> "Erreur : ${state.message} — réessayer"
}

fun parseChangelog(raw: String): List<ChangelogEntry> {
    if (raw.isBlank()) return emptyList()
    return raw.split("\n")
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split("|")
            if (parts.size >= 3) ChangelogEntry(parts[0], parts[1], parts[2])
            else null
        }
}

@Composable
fun AboutScreen(
    repository: MetadataRepository,
    dataUpdateRepository: DataUpdateRepository,
    targetDbFile: File,
    tempDir: File,
    onRestartRequired: () -> Unit = {},
    appUpdateRepository: AppUpdateRepository? = null,
    installApk: (File) -> ApkInstallOutcome = { ApkInstallOutcome.NeedsPermission },
    modifier: Modifier = Modifier
) {
    val viewModel: AboutViewModel = viewModel(
        factory = AboutViewModel.Factory(repository, dataUpdateRepository, appUpdateRepository)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val dataUpdateState by viewModel.dataUpdateState.collectAsStateWithLifecycle()
    val appUpdateState by viewModel.appUpdateState.collectAsStateWithLifecycle()
    AboutContent(
        versionName = BuildConfig.VERSION_NAME,
        gitCommit = BuildConfig.GIT_COMMIT,
        buildDate = BuildConfig.BUILD_DATE,
        changelog = BuildConfig.CHANGELOG,
        dbInfo = uiState.dbInfo,
        dataUpdateState = dataUpdateState,
        onCheckForUpdate = { viewModel.checkForUpdateManually() },
        onApplyUpdate = {
            viewModel.applyUpdate(targetDbFile, tempDir, onSuccess = onRestartRequired)
        },
        appUpdateState = appUpdateState,
        onInstallAppUpdate = { viewModel.installAppUpdate(tempDir, installApk) },
        modifier = modifier
    )
}

@Composable
fun AboutContent(
    versionName: String,
    gitCommit: String,
    buildDate: String,
    changelog: String,
    dbInfo: DbInfoUi? = null,
    dataUpdateState: DataUpdateState = DataUpdateState.Idle,
    onCheckForUpdate: () -> Unit = {},
    onApplyUpdate: () -> Unit = {},
    appUpdateState: AppUpdateState = AppUpdateState.Idle,
    onInstallAppUpdate: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val entries = remember(changelog) { parseChangelog(changelog) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Le Masque et la Plume",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = formatVersionLine(versionName, gitCommit, buildDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        val appUpdateTitle = formatAppUpdateTitle(appUpdateState)
        if (appUpdateTitle != null) {
            item {
                Text(
                    text = "Application",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = appUpdateTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Button(
                    onClick = onInstallAppUpdate,
                    enabled = appUpdateState !is AppUpdateState.Downloading
                ) {
                    Text(formatAppUpdateButtonLabel(appUpdateState))
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            }
        }

        if (dbInfo != null) {
            item {
                Text(
                    text = "Données",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = formatDbInfoSummary(dbInfo),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(bottom = 16.dp))
            }
        }

        item {
            val label = formatUpdateStateLabel(dataUpdateState)
            val onClick = when (dataUpdateState) {
                is DataUpdateState.UpdateAvailable -> onApplyUpdate
                is DataUpdateState.Checking, is DataUpdateState.Downloading,
                is DataUpdateState.Verifying, is DataUpdateState.Replacing -> ({})
                else -> onCheckForUpdate
            }
            val enabled = dataUpdateState !is DataUpdateState.Checking &&
                dataUpdateState !is DataUpdateState.Downloading &&
                dataUpdateState !is DataUpdateState.Verifying &&
                dataUpdateState !is DataUpdateState.Replacing
            Button(onClick = onClick, enabled = enabled) {
                Text(label)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
        }

        if (entries.isNotEmpty()) {
            item {
                Text(
                    text = "Historique",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            items(entries) { entry ->
                ChangelogRow(entry)
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun ChangelogRow(entry: ChangelogEntry) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = entry.hash,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.alignByBaseline()
        )
        Text(
            text = entry.message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier
                .weight(1f)
                .alignByBaseline()
        )
        Text(
            text = entry.date,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.alignByBaseline()
        )
    }
}
