package com.lmelp.mobile.ui.critiques

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmelp.mobile.data.model.CritiqueUi
import com.lmelp.mobile.data.repository.CritiquesRepository
import com.lmelp.mobile.ui.components.EmptyState
import com.lmelp.mobile.ui.components.ErrorMessage
import com.lmelp.mobile.ui.components.LoadingIndicator
import com.lmelp.mobile.viewmodel.CritiquesUiState
import com.lmelp.mobile.viewmodel.CritiquesViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CritiquesScreen(repository: CritiquesRepository) {
    val viewModel: CritiquesViewModel = viewModel(factory = CritiquesViewModel.Factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Critiques") }) }
    ) { padding ->
        CritiquesContent(uiState = uiState, modifier = Modifier.padding(padding))
    }
}

@Composable
fun CritiquesContent(uiState: CritiquesUiState, modifier: Modifier = Modifier) {
    when {
        uiState.isLoading -> LoadingIndicator(modifier)
        uiState.error != null -> ErrorMessage(uiState.error, modifier)
        uiState.critiques.isEmpty() -> EmptyState("Aucun critique", modifier)
        else -> LazyColumn(modifier = modifier) {
            items(uiState.critiques, key = { it.id }) { critique ->
                CritiqueCard(critique = critique)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CritiqueCard(critique: CritiqueUi) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = critique.nom, style = MaterialTheme.typography.titleSmall)
                Text(text = "${critique.nbAvis} avis", style = MaterialTheme.typography.bodySmall)
            }
            if (critique.animateur) {
                Badge { Text("animateur") }
            }
        }
    }
}
