package com.lmelp.mobile.ui.emissions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmelp.mobile.data.model.EmissionDetailUi
import com.lmelp.mobile.data.model.LivreUi
import com.lmelp.mobile.data.repository.EmissionsRepository
import com.lmelp.mobile.ui.components.ErrorMessage
import com.lmelp.mobile.ui.components.LoadingIndicator
import com.lmelp.mobile.viewmodel.EmissionsViewModel

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
                title = { Text(uiState.emission?.titre ?: "Émission") },
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
        Text(text = emission.date.take(10), style = MaterialTheme.typography.bodyMedium)
        emission.description?.let {
            Text(text = it, modifier = Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium)
        }
        if (emission.livres.isNotEmpty()) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
            Text("Livres discutés", style = MaterialTheme.typography.titleMedium)
            emission.livres.forEach { livre ->
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
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = livre.titre, style = MaterialTheme.typography.titleSmall)
            livre.auteurNom?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
