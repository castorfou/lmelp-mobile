package com.lmelp.mobile.ui.onkindle

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmelp.mobile.data.model.OnKindleUi
import com.lmelp.mobile.data.repository.OnKindleRepository
import com.lmelp.mobile.ui.components.BookCoverThumbnail
import com.lmelp.mobile.ui.components.EmptyState
import com.lmelp.mobile.ui.components.ErrorMessage
import com.lmelp.mobile.ui.components.LoadingIndicator
import com.lmelp.mobile.ui.components.NoteBadge
import com.lmelp.mobile.ui.theme.LmelpVert
import com.lmelp.mobile.viewmodel.OnKindleUiState
import com.lmelp.mobile.viewmodel.OnKindleViewModel
import com.lmelp.mobile.viewmodel.TriMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnKindleScreen(
    repository: OnKindleRepository,
    onLivreClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: OnKindleViewModel = viewModel(factory = OnKindleViewModel.Factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Sur ma liseuse", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LmelpVert),
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { padding ->
        OnKindleContent(
            uiState = uiState,
            onLivreClick = onLivreClick,
            onToggleLus = { viewModel.setAfficherLus(!uiState.afficherLus) },
            onToggleNonLus = { viewModel.setAfficherNonLus(!uiState.afficherNonLus) },
            onSetTriMode = { viewModel.setTriMode(it) },
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
fun OnKindleContent(
    uiState: OnKindleUiState,
    onLivreClick: (String) -> Unit,
    onToggleLus: () -> Unit,
    onToggleNonLus: () -> Unit,
    onSetTriMode: (TriMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = uiState.triMode == TriMode.ALPHA,
                onClick = { onSetTriMode(TriMode.ALPHA) },
                label = { Text("a→z") }
            )
            FilterChip(
                selected = uiState.triMode == TriMode.NOTE_MASQUE,
                onClick = { onSetTriMode(TriMode.NOTE_MASQUE) },
                label = { Text("Note masque ↓") }
            )
            FilterChip(
                selected = uiState.triMode == TriMode.NOTE_CONSEIL,
                onClick = { onSetTriMode(TriMode.NOTE_CONSEIL) },
                label = { Text("Note conseil ↓") }
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .background(Color(0xFFD32F2F), shape = MaterialTheme.shapes.small)
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "${uiState.livres.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }
        }
        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null -> ErrorMessage(uiState.error)
            uiState.livres.isEmpty() -> EmptyState("Aucun livre sur la liseuse")
            else -> LazyColumn {
                items(uiState.livres, key = { it.livreId }) { item ->
                    OnKindleCard(
                        item = item,
                        onClick = if (item.discusseAuMasque) {
                            { onLivreClick(item.livreId) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
fun OnKindleCard(item: OnKindleUi, onClick: (() -> Unit)?) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverThumbnail(urlCover = item.urlCover)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.titre, style = MaterialTheme.typography.titleSmall)
                item.auteurNom?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            // Statut lu + note personnelle (affiché seulement si lu)
            if (item.calibreLu) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "✓",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF2E7D32)
                    )
                    item.calibreRating?.let {
                        Text(
                            text = "${it.toInt()}/10",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
            // Note Masque — colonne de largeur fixe pour uniformiser la hauteur des cartes
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                if (item.discusseAuMasque && item.noteMoyenne != null) {
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
    }
}
