package com.lmelp.mobile.ui.emissions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmelp.mobile.data.model.EmissionDetailUi
import com.lmelp.mobile.data.model.LivreUi
import com.lmelp.mobile.data.repository.EmissionsRepository
import com.lmelp.mobile.ui.components.BookCoverThumbnail
import com.lmelp.mobile.ui.components.ErrorMessage
import com.lmelp.mobile.ui.components.LoadingIndicator
import com.lmelp.mobile.ui.components.NoteBadge
import com.lmelp.mobile.viewmodel.EmissionsViewModel

private val IconProgramme: ImageVector = ImageVector.Builder(
    defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f
).apply {
    // Cercle bleu plein (r=8, cx=12, cy=12)
    path(fill = SolidColor(Color(0xFF0B5FFF))) {
        moveTo(12f, 4f)
        arcTo(8f, 8f, 0f, true, true, 11.999f, 4f)
        close()
    }
    // Cercle blanc central (r=4, cx=12, cy=12)
    path(fill = SolidColor(Color.White)) {
        moveTo(12f, 8f)
        arcTo(4f, 4f, 0f, true, true, 11.999f, 8f)
        close()
    }
}.build()

private val IconCoupDeCoeur: ImageVector = ImageVector.Builder(
    defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color(0xFFD93025))) {
        moveTo(12f, 21f)
        curveTo(12f, 21f, 4.5f, 16.5f, 2.7f, 13.9f)
        curveTo(-0.4f, 9.8f, 3f, 5f, 7.4f, 7.1f)
        curveTo(9.1f, 8f, 10f, 9.6f, 12f, 11.3f)
        curveTo(14f, 9.6f, 14.9f, 8f, 16.6f, 7.1f)
        curveTo(21f, 5f, 24.4f, 9.8f, 21.3f, 13.9f)
        curveTo(19.5f, 16.5f, 12f, 21f, 12f, 21f)
        close()
    }
}.build()

/** Supprime le suffixe RSS "Vous aimez ce podcast ?..." de la description. */
fun stripRssSuffix(description: String): String {
    val marker = "Vous aimez ce podcast"
    val idx = description.indexOf(marker)
    return if (idx >= 0) description.substring(0, idx).trimEnd() else description
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmissionDetailScreen(
    emissionId: String,
    repository: EmissionsRepository,
    onLivreClick: (String) -> Unit,
    onBack: () -> Unit
) {
    val viewModel: EmissionsViewModel = viewModel(factory = EmissionsViewModel.Factory(repository))
    val uiState by viewModel.detailState.collectAsStateWithLifecycle()

    LaunchedEffect(emissionId) {
        viewModel.loadEmissionDetail(emissionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.emission?.titre ?: "Émission",
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                },
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
            uiState.emission != null -> EmissionDetailContent(
                emission = uiState.emission!!,
                onLivreClick = onLivreClick,
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
fun EmissionDetailContent(
    emission: EmissionDetailUi,
    onLivreClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(text = formatDateLong(emission.date), style = MaterialTheme.typography.bodyMedium)

        emission.description?.let { desc ->
            val cleaned = stripRssSuffix(desc)
            if (cleaned.isNotBlank()) {
                Text(
                    text = cleaned,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        val livresProgramme = emission.livres.filter { it.section == "programme" }
            .sortedByDescending { it.noteMoyenne }
        val livresCoupDeCoeur = emission.livres.filter { it.section == "coup_de_coeur" }
            .sortedBy { it.titre }
        val autresLivres = emission.livres.filter { it.section == null }

        if (livresProgramme.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = IconProgramme,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).padding(end = 6.dp),
                    tint = Color.Unspecified
                )
                Text("Au programme", style = MaterialTheme.typography.titleMedium)
            }
            livresProgramme.forEach { livre ->
                LivreCard(livre = livre, onClick = { onLivreClick(livre.id) })
            }
        }
        if (livresCoupDeCoeur.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = IconCoupDeCoeur,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp).padding(end = 6.dp),
                    tint = Color.Unspecified
                )
                Text("Coups de cœur", style = MaterialTheme.typography.titleMedium)
            }
            livresCoupDeCoeur.forEach { livre ->
                LivreCard(livre = livre, onClick = { onLivreClick(livre.id) })
            }
        }
        if (autresLivres.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text("Livres discutés", style = MaterialTheme.typography.titleMedium)
            autresLivres.forEach { livre ->
                LivreCard(livre = livre, onClick = { onLivreClick(livre.id) })
            }
        }
    }
}

@Composable
fun LivreCard(livre: LivreUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BookCoverThumbnail(urlCover = livre.urlCover)
            Column(modifier = Modifier.weight(1f)) {
                Text(text = livre.titre, style = MaterialTheme.typography.titleSmall)
                livre.auteurNom?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            livre.noteMoyenne?.let { NoteBadge(note = it) }
        }
    }
}
