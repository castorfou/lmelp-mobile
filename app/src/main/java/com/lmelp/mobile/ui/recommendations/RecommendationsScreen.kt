package com.lmelp.mobile.ui.recommendations

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import com.lmelp.mobile.ui.theme.LmelpBordeaux
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmelp.mobile.data.model.RecommendationUi
import com.lmelp.mobile.data.repository.RecommendationsRepository
import com.lmelp.mobile.ui.components.EmptyState
import com.lmelp.mobile.ui.components.ErrorMessage
import com.lmelp.mobile.ui.components.LoadingIndicator
import com.lmelp.mobile.viewmodel.RecommendationsUiState
import com.lmelp.mobile.viewmodel.RecommendationsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationsScreen(
    repository: RecommendationsRepository,
    onLivreClick: (String) -> Unit
) {
    val viewModel: RecommendationsViewModel = viewModel(
        factory = RecommendationsViewModel.Factory(repository)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Conseils", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LmelpBordeaux),
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { padding ->
        RecommendationsContent(
            uiState = uiState,
            onLivreClick = onLivreClick,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
fun RecommendationsContent(
    uiState: RecommendationsUiState,
    onLivreClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        uiState.isLoading -> LoadingIndicator(modifier)
        uiState.error != null -> ErrorMessage(uiState.error, modifier)
        uiState.recommendations.isEmpty() -> EmptyState("Aucun conseil disponible", modifier)
        else -> LazyColumn(modifier = modifier) {
            items(uiState.recommendations, key = { it.livreId }) { item ->
                RecommendationCard(item = item, onClick = { onLivreClick(item.livreId) })
            }
        }
    }
}

@Composable
fun RecommendationCard(item: RecommendationUi, onClick: () -> Unit) {
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
            item.masqueMean?.let {
                Text(
                    text = String.format("%.1f", it),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
