package com.lmelp.mobile.ui.auteurs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmelp.mobile.data.model.CalibreHorsMasqueUi
import com.lmelp.mobile.data.model.LivreParAuteurUi
import com.lmelp.mobile.data.repository.AuteursRepository
import com.lmelp.mobile.ui.components.CalibreBadge
import com.lmelp.mobile.ui.components.EmptyState
import com.lmelp.mobile.ui.components.ErrorMessage
import com.lmelp.mobile.ui.components.LoadingIndicator
import com.lmelp.mobile.ui.components.NoteBadge
import com.lmelp.mobile.ui.emissions.formatDateLong
import com.lmelp.mobile.viewmodel.AuteurDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuteurDetailScreen(
    auteurId: String,
    repository: AuteursRepository,
    onBack: () -> Unit,
    onLivreClick: (String) -> Unit
) {
    val viewModel: AuteurDetailViewModel = viewModel(
        factory = AuteurDetailViewModel.Factory(repository, auteurId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.auteur?.nom ?: "Auteur") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        when {
            uiState.isLoading -> LoadingIndicator(Modifier.padding(padding))
            uiState.error != null -> ErrorMessage(uiState.error!!, Modifier.padding(padding))
            uiState.auteur != null -> {
                val auteur = uiState.auteur!!
                if (auteur.livres.isEmpty() && auteur.livresHorsMasque.isEmpty()) {
                    EmptyState("Aucun livre trouvé", Modifier.padding(padding))
                } else {
                    LazyColumn(modifier = Modifier.padding(padding)) {
                        items(auteur.livres, key = { it.livreId }) { livre ->
                            LivreParAuteurCard(
                                livre = livre,
                                onClick = { onLivreClick(livre.livreId) }
                            )
                        }
                        if (auteur.livresHorsMasque.isNotEmpty()) {
                            item {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                                Text(
                                    text = "Lus hors Masque",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                                )
                            }
                            items(auteur.livresHorsMasque, key = { "hors_${it.id}" }) { livre ->
                                HorsMasqueCard(livre = livre)
                            }
                        }
                    }
                }
            }
            else -> EmptyState("Auteur introuvable", Modifier.padding(padding))
        }
    }
}

@Composable
fun LivreParAuteurCard(livre: LivreParAuteurUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = livre.titre,
                    style = MaterialTheme.typography.bodyLarge
                )
                livre.derniereEmissionDate?.let {
                    Text(
                        text = formatDateLong(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                livre.noteMoyenne?.let {
                    NoteBadge(note = it)
                }
                CalibreBadge(
                    calibreInLibrary = livre.calibreInLibrary,
                    calibreLu = livre.calibreLu,
                    calibreRating = livre.calibreRating,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
fun HorsMasqueCard(livre: CalibreHorsMasqueUi) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = livre.titre,
                    style = MaterialTheme.typography.bodyLarge
                )
                livre.dateLecture?.let {
                    Text(
                        text = "Lu le ${it.substring(8, 10)}/${it.substring(5, 7)}/${it.substring(0, 4)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            livre.calibreRating?.let {
                Text(
                    text = "${it.toInt()}/10",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color(0xFF2E7D32)
                )
            }
        }
    }
}
