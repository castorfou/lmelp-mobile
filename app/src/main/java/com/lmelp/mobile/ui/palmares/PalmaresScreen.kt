package com.lmelp.mobile.ui.palmares

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lmelp.mobile.ui.theme.LmelpVert
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmelp.mobile.data.model.MonPalmaresItemUi
import com.lmelp.mobile.data.model.PalmaresUi
import com.lmelp.mobile.data.repository.PalmaresRepository
import com.lmelp.mobile.data.repository.UserPreferencesRepository
import com.lmelp.mobile.ui.components.BookCoverThumbnail
import com.lmelp.mobile.ui.components.EmptyState
import com.lmelp.mobile.ui.components.ErrorMessage
import com.lmelp.mobile.ui.components.LoadingIndicator
import com.lmelp.mobile.ui.components.NoteBadge
import com.lmelp.mobile.viewmodel.MonPalmaresTriMode
import com.lmelp.mobile.viewmodel.PalmaresMode
import com.lmelp.mobile.viewmodel.PalmaresUiState
import com.lmelp.mobile.viewmodel.PalmaresViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalmaresScreen(
    repository: PalmaresRepository,
    userPrefsRepository: UserPreferencesRepository? = null,
    onLivreClick: (String) -> Unit
) {
    val viewModel: PalmaresViewModel = viewModel(factory = PalmaresViewModel.Factory(repository, userPrefsRepository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Palmarès", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LmelpVert),
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { padding ->
        PalmaresContent(
            uiState = uiState,
            onLivreClick = onLivreClick,
            onToggleLus = { viewModel.setAfficherLus(!uiState.afficherLus) },
            onToggleNonLus = { viewModel.setAfficherNonLus(!uiState.afficherNonLus) },
            onSetPalmaresMode = { viewModel.setPalmaresMode(it) },
            onSetMonPalmaresTriMode = { viewModel.setMonPalmaresTriMode(it) },
            onToggleHorsMasque = { viewModel.setShowHorsMasque(!uiState.showHorsMasque) },
            modifier = Modifier.padding(padding)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalmaresContent(
    uiState: PalmaresUiState,
    onLivreClick: (String) -> Unit,
    onToggleLus: () -> Unit,
    onToggleNonLus: () -> Unit,
    onSetPalmaresMode: (PalmaresMode) -> Unit,
    onSetMonPalmaresTriMode: (MonPalmaresTriMode) -> Unit,
    onToggleHorsMasque: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Bascule Palmarès critiques / Mon palmarès
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            SegmentedButton(
                selected = uiState.palmaresMode == PalmaresMode.CRITIQUES,
                onClick = { onSetPalmaresMode(PalmaresMode.CRITIQUES) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                label = { Text("Palmarès critiques") }
            )
            SegmentedButton(
                selected = uiState.palmaresMode == PalmaresMode.PERSONNEL,
                onClick = { onSetPalmaresMode(PalmaresMode.PERSONNEL) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                label = { Text("Mon palmarès") }
            )
        }

        // Filtres (mode CRITIQUES) ou tri (mode PERSONNEL)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.palmaresMode == PalmaresMode.CRITIQUES) {
                FilterChip(
                    selected = uiState.afficherNonLus,
                    onClick = onToggleNonLus,
                    label = { Text("Non lus") }
                )
                FilterChip(
                    selected = uiState.afficherLus,
                    onClick = onToggleLus,
                    label = { Text("Lus") }
                )
            } else {
                FilterChip(
                    selected = uiState.monPalmaresTriMode == MonPalmaresTriMode.NOTE_PERSO,
                    onClick = { onSetMonPalmaresTriMode(MonPalmaresTriMode.NOTE_PERSO) },
                    label = { Text("Note ↓") }
                )
                FilterChip(
                    selected = uiState.monPalmaresTriMode == MonPalmaresTriMode.DATE_LECTURE,
                    onClick = { onSetMonPalmaresTriMode(MonPalmaresTriMode.DATE_LECTURE) },
                    label = { Text("Date lecture ↓") }
                )
                FilterChip(
                    selected = uiState.monPalmaresTriMode == MonPalmaresTriMode.VITESSE_ASC ||
                               uiState.monPalmaresTriMode == MonPalmaresTriMode.VITESSE_DESC,
                    onClick = { onSetMonPalmaresTriMode(MonPalmaresTriMode.VITESSE_ASC) },
                    label = {
                        Text(
                            if (uiState.monPalmaresTriMode == MonPalmaresTriMode.VITESSE_DESC)
                                "Vitesse ↑" else "Vitesse ↓"
                        )
                    }
                )
                Spacer(modifier = Modifier.weight(1f))
                FilterChip(
                    selected = uiState.showHorsMasque,
                    onClick = onToggleHorsMasque,
                    label = { Text("Hors Masque") }
                )
            }
        }

        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null -> ErrorMessage(uiState.error)
            uiState.palmaresMode == PalmaresMode.PERSONNEL && uiState.monPalmares.isEmpty() ->
                EmptyState("Aucun livre lu")
            uiState.palmaresMode == PalmaresMode.CRITIQUES && uiState.palmares.isEmpty() ->
                EmptyState("Palmarès vide")
            uiState.palmaresMode == PalmaresMode.PERSONNEL -> LazyColumn {
                items(uiState.monPalmares, key = { it.id }) { item ->
                    MonPalmaresCard(item = item, onLivreClick = onLivreClick)
                }
            }
            else -> LazyColumn {
                items(uiState.palmares, key = { it.livreId }) { item ->
                    PalmaresCard(item = item, onClick = { onLivreClick(item.livreId) })
                }
            }
        }
    }
}

@Composable
fun PalmaresCard(item: PalmaresUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "#${item.rank}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            BookCoverThumbnail(urlCover = item.urlCover)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.titre, style = MaterialTheme.typography.titleSmall)
                item.auteurNom?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            if (item.calibreInLibrary) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = if (item.calibreLu) "✓" else "◯",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (item.calibreLu) Color(0xFF2E7D32) else Color.Gray
                    )
                    if (item.calibreLu) {
                        item.calibreRating?.let {
                            Text(
                                text = "${it.toInt()}/10",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF2E7D32)
                            )
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                NoteBadge(note = item.noteMoyenne)
                Text(
                    text = "${item.nbAvis} avis",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
fun MonPalmaresCard(item: MonPalmaresItemUi, onLivreClick: (String) -> Unit) {
    val isClickable = item.livreId != null
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(
                if (isClickable) Modifier.clickable { onLivreClick(item.livreId!!) }
                else Modifier
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isClickable) {
                BookCoverThumbnail(urlCover = item.urlCover)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.titre, style = MaterialTheme.typography.titleSmall)
                item.auteurNom?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                item.dateLecture?.let {
                    Text(
                        text = "Lu le ${it.substring(8, 10)}/${it.substring(5, 7)}/${it.substring(0, 4)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                item.joursLecture?.let {
                    Text(
                        text = "${it}j",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
                item.calibreRating?.let {
                    Text(
                        text = "${it.toInt()}/10",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color(0xFF2E7D32)
                    )
                }
            }
        }
    }
}
