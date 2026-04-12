package com.lmelp.mobile.ui.onkindle

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmelp.mobile.data.model.OnKindleUi
import com.lmelp.mobile.data.repository.OnKindleRepository
import com.lmelp.mobile.data.repository.UserPreferencesRepository
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
    userPrefsRepository: UserPreferencesRepository.PinnedReadingStorage? = null,
    onLivreClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: OnKindleViewModel = viewModel(
        factory = OnKindleViewModel.Factory(repository, userPrefsRepository)
    )
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
            onTogglePin = { viewModel.togglePin(it) },
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
    onTogglePin: (String) -> Unit,
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
            else -> {
                val pinnedLivres = uiState.livres.filter { it.isPinned }
                val nonPinnedLivres = uiState.livres.filter { !it.isPinned }
                LazyColumn {
                    items(pinnedLivres, key = { it.livreId }) { item ->
                        OnKindleCard(
                            item = item,
                            onClick = if (item.discusseAuMasque) {
                                { onLivreClick(item.livreId) }
                            } else null,
                            onTogglePin = { onTogglePin(item.livreId) }
                        )
                    }
                    if (pinnedLivres.isNotEmpty() && nonPinnedLivres.isNotEmpty()) {
                        item(key = "__separator__") {
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                    items(nonPinnedLivres, key = { it.livreId }) { item ->
                        OnKindleCard(
                            item = item,
                            onClick = if (item.discusseAuMasque) {
                                { onLivreClick(item.livreId) }
                            } else null,
                            onTogglePin = { onTogglePin(item.livreId) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OnKindleCard(
    item: OnKindleUi,
    onClick: (() -> Unit)?,
    onTogglePin: () -> Unit
) {
    var showBottomSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .combinedClickable(
                onClick = { onClick?.invoke() },
                onLongClick = { showBottomSheet = true }
            )
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
            // Zone droite : punaise (si épinglé) à gauche de la note, même ligne
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxHeight()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (item.isPinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "En cours de lecture — tap pour désépingler",
                            tint = LmelpVert,
                            modifier = Modifier
                                .size(28.dp)
                                .clickable { onTogglePin() }
                        )
                    }
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

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { showBottomSheet = false },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = item.titre,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            showBottomSheet = false
                            onTogglePin()
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = null,
                        tint = if (item.isPinned) LmelpVert else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (item.isPinned) "Retirer de 'En cours de lecture'" else "📌 En cours de lecture",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}
