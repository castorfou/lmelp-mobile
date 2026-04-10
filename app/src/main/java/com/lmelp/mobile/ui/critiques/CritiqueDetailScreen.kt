package com.lmelp.mobile.ui.critiques

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Badge
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmelp.mobile.data.model.AvisParCritiqueUi
import com.lmelp.mobile.data.model.CritiqueDetailUi
import com.lmelp.mobile.data.repository.CritiquesRepository
import com.lmelp.mobile.ui.components.EmptyState
import com.lmelp.mobile.ui.components.ErrorMessage
import com.lmelp.mobile.ui.components.LoadingIndicator
import com.lmelp.mobile.ui.components.NoteBadge
import com.lmelp.mobile.ui.emissions.formatDateLong
import com.lmelp.mobile.viewmodel.CritiqueDetailViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CritiqueDetailScreen(
    critiqueId: String,
    repository: CritiquesRepository,
    onBack: () -> Unit,
    onLivreClick: (String) -> Unit = {}
) {
    val viewModel: CritiqueDetailViewModel = viewModel(
        factory = CritiqueDetailViewModel.Factory(repository, critiqueId)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.critique?.nom ?: "Critique") },
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
            uiState.critique != null -> CritiqueDetailContent(
                critique = uiState.critique!!,
                onLivreClick = onLivreClick,
                modifier = Modifier.padding(padding)
            )
            else -> EmptyState("Critique introuvable", Modifier.padding(padding))
        }
    }
}

@Composable
fun CritiqueDetailContent(
    critique: CritiqueDetailUi,
    onLivreClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        item {
            CritiqueHeader(critique = critique)
        }

        if (critique.distribution.isNotEmpty()) {
            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                NoteDistribution(distribution = critique.distribution)
            }
        }

        if (critique.coupsDeCoeur.isNotEmpty()) {
            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Text(
                    text = "Coups de cœur (${critique.coupsDeCoeur.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
            items(critique.coupsDeCoeur, key = { "cdc_${it.livreId}" }) { avis ->
                CoupDeCoeurCard(avis = avis, onClick = { onLivreClick(avis.livreId) })
            }
        } else {
            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                EmptyState("Aucun coup de cœur")
            }
        }
    }
}

@Composable
fun CritiqueHeader(critique: CritiqueDetailUi) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = critique.nom,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                if (critique.animateur) {
                    Badge { Text("animateur") }
                }
            }
            Text(
                text = "${critique.nbAvis} avis",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        critique.noteMoyenne?.let {
            NoteBadge(note = it)
        }
    }
}

private fun noteBarColor(note: Int): Color = when {
    note >= 9 -> Color(0xFF2E7D32)
    note >= 7 -> Color(0xFF558B2F)
    note >= 5 -> Color(0xFFAFB42B)
    else -> Color(0xFFC62828)
}

@Composable
fun NoteDistribution(distribution: Map<Int, Int>) {
    val maxCount = distribution.values.maxOrNull() ?: 1
    val chartHeight = 120.dp

    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            text = "Distribution des notes",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        // Barres verticales alignées en bas
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            (1..10).forEach { note ->
                val count = distribution[note] ?: 0
                val fraction = if (count > 0) count.toFloat() / maxCount.toFloat() else 0f
                val barColor = noteBarColor(note)
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // Compteur au-dessus de la barre
                    if (count > 0) {
                        Text(
                            text = "$count",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }
                    // Barre (hauteur proportionnelle)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 2.dp)
                            .height(chartHeight * fraction)
                            .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                            .background(if (count > 0) barColor else Color.Transparent)
                    )
                }
            }
        }
        // Axe X : étiquettes des notes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            (1..10).forEach { note ->
                Text(
                    text = "$note",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f).padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
fun CoupDeCoeurCard(avis: AvisParCritiqueUi, onClick: () -> Unit) {
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
                    text = avis.livreTitre ?: "Livre inconnu",
                    style = MaterialTheme.typography.bodyLarge
                )
                avis.auteurNom?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                avis.emissionDate?.let {
                    Text(
                        text = formatDateLong(it),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            avis.note?.let {
                NoteBadge(note = it)
            }
        }
    }
}
