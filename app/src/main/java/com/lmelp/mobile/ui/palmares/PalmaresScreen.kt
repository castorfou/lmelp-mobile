package com.lmelp.mobile.ui.palmares

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import com.lmelp.mobile.data.model.PalmaresUi
import com.lmelp.mobile.data.repository.PalmaresRepository
import com.lmelp.mobile.ui.components.BookCoverThumbnail
import com.lmelp.mobile.ui.components.EmptyState
import com.lmelp.mobile.ui.components.ErrorMessage
import com.lmelp.mobile.ui.components.LoadingIndicator
import com.lmelp.mobile.ui.components.NoteBadge
import com.lmelp.mobile.viewmodel.PalmaresUiState
import com.lmelp.mobile.viewmodel.PalmaresViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PalmaresScreen(
    repository: PalmaresRepository,
    onLivreClick: (String) -> Unit
) {
    val viewModel: PalmaresViewModel = viewModel(factory = PalmaresViewModel.Factory(repository))
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
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
fun PalmaresContent(
    uiState: PalmaresUiState,
    onLivreClick: (String) -> Unit,
    onToggleLus: () -> Unit,
    onToggleNonLus: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
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
        }
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null -> ErrorMessage(uiState.error)
            uiState.palmares.isEmpty() -> EmptyState("Palmarès vide")
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
