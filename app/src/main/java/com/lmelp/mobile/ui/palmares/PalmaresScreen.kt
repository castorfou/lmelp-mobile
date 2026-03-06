package com.lmelp.mobile.ui.palmares

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmelp.mobile.data.model.PalmaresUi
import com.lmelp.mobile.data.repository.PalmaresRepository
import com.lmelp.mobile.ui.components.EmptyState
import com.lmelp.mobile.ui.components.ErrorMessage
import com.lmelp.mobile.ui.components.LoadingIndicator
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
        topBar = { TopAppBar(title = { Text("Palmarès") }) }
    ) { padding ->
        PalmaresContent(
            uiState = uiState,
            onLivreClick = onLivreClick,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
fun PalmaresContent(
    uiState: PalmaresUiState,
    onLivreClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        uiState.isLoading -> LoadingIndicator(modifier)
        uiState.error != null -> ErrorMessage(uiState.error, modifier)
        uiState.palmares.isEmpty() -> EmptyState("Palmarès vide", modifier)
        else -> LazyColumn(modifier = modifier) {
            items(uiState.palmares, key = { it.livreId }) { item ->
                PalmaresCard(item = item, onClick = { onLivreClick(item.livreId) })
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
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "#${item.rank}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = item.titre, style = MaterialTheme.typography.titleSmall)
                item.auteurNom?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
            Column {
                Text(
                    text = String.format("%.2f", item.noteMoyenne),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${item.nbAvis} avis",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
