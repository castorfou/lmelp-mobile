package com.lmelp.mobile.ui.emissions

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmelp.mobile.data.model.AvisParEmissionUi
import com.lmelp.mobile.data.model.AvisUi
import com.lmelp.mobile.data.model.LivreDetailUi
import com.lmelp.mobile.data.repository.LivresRepository
import com.lmelp.mobile.ui.components.EmptyState
import com.lmelp.mobile.ui.components.ErrorMessage
import com.lmelp.mobile.ui.components.LoadingIndicator
import com.lmelp.mobile.ui.components.NoteBadge
import com.lmelp.mobile.viewmodel.LivreDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LivreDetailScreen(
    livreId: String,
    repository: LivresRepository,
    onBack: () -> Unit,
    onEmissionClick: (String) -> Unit = {}
) {
    val viewModel: LivreDetailViewModel = viewModel(
        factory = LivreDetailViewModel.Factory(repository, livreId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.livre?.titre ?: "Livre") },
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
            uiState.livre != null -> LivreDetailContent(
                livre = uiState.livre!!,
                onEmissionClick = onEmissionClick,
                modifier = Modifier.padding(padding)
            )
            else -> EmptyState("Livre introuvable", Modifier.padding(padding))
        }
    }
}

@Composable
fun LivreDetailContent(
    livre: LivreDetailUi,
    onEmissionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    livre.auteurNom?.let {
                        Text(it, style = MaterialTheme.typography.bodyLarge)
                    }
                    livre.editeur?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                livre.noteMoyenne?.let {
                    NoteBadge(note = it, fontSize = 32.sp, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
        if (livre.avisParEmission.isNotEmpty()) {
            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
        livre.avisParEmission.forEach { groupe ->
            item(key = "header_${groupe.emissionId}") {
                EmissionGroupHeader(
                    groupe = groupe,
                    onClick = { onEmissionClick(groupe.emissionId) }
                )
            }
            items(groupe.avis, key = { it.id }) { avis ->
                AvisCard(avis = avis)
            }
        }
    }
}

@Composable
fun EmissionGroupHeader(groupe: AvisParEmissionUi, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = groupe.emissionTitre ?: groupe.emissionId,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        groupe.emissionDate?.let {
            Text(
                text = formatDateLong(it),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun AvisCard(avis: AvisUi) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                Text(
                    text = avis.critiqueNom ?: "Critique",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f)
                )
                avis.note?.let { NoteBadge(note = it) }
            }
            avis.commentaire?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
