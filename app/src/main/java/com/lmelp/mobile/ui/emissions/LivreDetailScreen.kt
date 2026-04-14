package com.lmelp.mobile.ui.emissions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmelp.mobile.data.model.AvisParEmissionUi
import com.lmelp.mobile.data.model.AvisUi
import com.lmelp.mobile.data.model.LivreDetailUi
import com.lmelp.mobile.data.repository.LivresRepository
import com.lmelp.mobile.ui.components.CalibreBadge
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
    onEmissionClick: (String) -> Unit = {},
    onAuteurClick: (String) -> Unit = {},
    onCritiqueClick: (String) -> Unit = {}
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
                onAuteurClick = onAuteurClick,
                onCritiqueClick = onCritiqueClick,
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
    onAuteurClick: (String) -> Unit = {},
    onCritiqueClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showCoverFullscreen by remember { mutableStateOf(false) }

    if (showCoverFullscreen && livre.urlCover != null) {
        Dialog(
            onDismissRequest = { showCoverFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable { showCoverFullscreen = false },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = livre.urlCover,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    LazyColumn(modifier = modifier) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top
            ) {
                livre.urlCover?.let { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(80.dp)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showCoverFullscreen = true }
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    livre.auteurNom?.let { nom ->
                        val auteurId = livre.auteurId
                        Text(
                            text = nom,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (auteurId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = if (auteurId != null) {
                                Modifier.clickable { onAuteurClick(auteurId) }
                            } else {
                                Modifier
                            }
                        )
                    }
                    livre.editeur?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (livre.calibreLu && livre.dateLecture != null) {
                        val dateTxt = buildString {
                            append("Lu le ${formatDateLong(livre.dateLecture)}")
                            livre.joursLecture?.let { append(" (${it}j)") }
                        }
                        Text(
                            text = dateTxt,
                            style = MaterialTheme.typography.labelSmall,
                            color = androidx.compose.ui.graphics.Color(0xFF2E7D32),
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    livre.noteMoyenne?.let {
                        NoteBadge(note = it, fontSize = 32.sp)
                    }
                    CalibreBadge(
                        calibreInLibrary = livre.calibreInLibrary,
                        calibreLu = livre.calibreLu,
                        calibreRating = livre.calibreRating,
                        modifier = Modifier.padding(top = 4.dp)
                    )
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
                AvisCard(avis = avis, onCritiqueClick = onCritiqueClick)
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
fun AvisCard(avis: AvisUi, onCritiqueClick: (String) -> Unit = {}) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row {
                val critiqueId = avis.critiqueId
                Text(
                    text = avis.critiqueNom ?: "Critique",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (critiqueId != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (critiqueId != null) Modifier.clickable { onCritiqueClick(critiqueId) }
                            else Modifier
                        )
                )
                avis.note?.let { NoteBadge(note = it) }
            }
            avis.commentaire?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}
