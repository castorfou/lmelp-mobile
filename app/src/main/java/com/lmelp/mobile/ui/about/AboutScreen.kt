package com.lmelp.mobile.ui.about

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Commit de l'historique ; [versionName] = version de l'appli taguée sur ce commit, s'il y en a une. */
data class ChangelogEntry(
    val hash: String,
    val message: String,
    val date: String,
    val versionName: String? = null
)

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
    // Message déjà rédigé pour l'utilisateur par updateErrorMessage (issue #156)
    is DataUpdateState.Error -> state.message
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

/** Asset généré au build par la tâche `generateChangelog` (app/build.gradle.kts). */
const val CHANGELOG_ASSET = "changelog.txt"

/** Séparateur de champs de l'historique généré au build (U+001F, absent des sujets de commit). */
const val CHANGELOG_FIELD_SEPARATOR = "\u001F"

/**
 * Parse l'historique généré au build (issue #170) : une ligne par commit,
 * `hash␟sujet␟date␟refs` où refs est le `%D` de git log (ex. "tag: v1.1.0, tag: data-v9").
 */
fun parseChangelog(raw: String): List<ChangelogEntry> =
    raw.lines()
        .filter { it.isNotBlank() }
        .mapNotNull { line ->
            val parts = line.split(CHANGELOG_FIELD_SEPARATOR)
            if (parts.size < 3) return@mapNotNull null
            ChangelogEntry(
                hash = parts[0],
                message = parts[1],
                date = parts[2],
                versionName = parts.getOrNull(3)?.let(::versionNameFromRefs)
            )
        }

/**
 * Bloc de l'historique : une version de l'appli et son contenu, ou les commits
 * « Pas encore publié » quand [versionName] est null (issue #170, voir CONTEXT.md).
 */
data class ChangelogSection(
    val versionName: String?,
    val date: String?,
    val installed: Boolean,
    val entries: List<ChangelogEntry>
)

/**
 * Regroupe l'historique (du plus récent au plus ancien) par version de l'appli : chaque version
 * contient son commit tagué et les commits en dessous, jusqu'au tag précédent. Les commits
 * au-dessus du dernier tag forment la section « Pas encore publié ».
 */
fun groupChangelogByVersion(
    entries: List<ChangelogEntry>,
    installedVersionName: String
): List<ChangelogSection> {
    val sections = mutableListOf<ChangelogSection>()
    for (entry in entries) {
        val current = sections.lastOrNull()
        if (current == null || entry.versionName != null) {
            sections += ChangelogSection(
                versionName = entry.versionName,
                date = entry.versionName?.let { entry.date },
                installed = entry.versionName == installedVersionName,
                entries = listOf(entry)
            )
        } else {
            sections[sections.lastIndex] = current.copy(entries = current.entries + entry)
        }
    }
    return sections
}

/** En-tête d'une section de l'historique : « Version X.Y.Z · jj/mm/aa » ou « Pas encore publié ». */
fun formatChangelogSectionHeader(section: ChangelogSection): String =
    if (section.versionName == null) "Pas encore publié"
    else "Version ${section.versionName} · ${section.date}"

private val STABLE_TAG = Regex("""v(\d+)\.(\d+)\.(\d+)""")

/**
 * Version de l'appli portée par les refs d'un commit : seuls les tags stables `vX.Y.Z` comptent
 * (ni `data-*`, ni pré-release), la plus grande l'emporte s'il y en a plusieurs.
 */
private fun versionNameFromRefs(refs: String): String? =
    refs.split(",")
        .mapNotNull { STABLE_TAG.matchEntire(it.trim().removePrefix("tag: ")) }
        .map { match -> match.groupValues.drop(1).map(String::toInt) }
        .maxWithOrNull(compareBy<List<Int>>({ it[0] }, { it[1] }, { it[2] }))
        ?.joinToString(".")

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
    val context = LocalContext.current
    // Historique généré au build dans un asset (issue #170) : pas de plafond de 64 Ko comme BuildConfig
    val changelog by produceState(initialValue = "") {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open(CHANGELOG_ASSET).bufferedReader().use { it.readText() }
            }.getOrDefault("")
        }
    }
    AboutContent(
        versionName = BuildConfig.VERSION_NAME,
        gitCommit = BuildConfig.GIT_COMMIT,
        buildDate = BuildConfig.BUILD_DATE,
        changelog = changelog,
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
    val sections = remember(changelog, versionName) {
        groupChangelogByVersion(parseChangelog(changelog), installedVersionName = versionName)
    }

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

        if (sections.isNotEmpty()) {
            item {
                Text(
                    text = "Historique",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            sections.forEach { section ->
                item(key = "section-${section.versionName}") {
                    ChangelogSectionHeader(section)
                }
                items(section.entries, key = { it.hash }) { entry ->
                    ChangelogRow(entry)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ChangelogSectionHeader(section: ChangelogSection) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = formatChangelogSectionHeader(section),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        if (section.installed) {
            Text(
                text = "installée",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.primaryContainer,
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.primary)
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
