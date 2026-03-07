package com.lmelp.mobile.ui.emissions

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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lmelp.mobile.ui.theme.LmelpBleu
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmelp.mobile.data.model.EmissionUi
import com.lmelp.mobile.data.repository.EmissionsRepository
import com.lmelp.mobile.ui.components.EmptyState
import com.lmelp.mobile.ui.components.ErrorMessage
import com.lmelp.mobile.ui.components.LoadingIndicator
import com.lmelp.mobile.viewmodel.EmissionsUiState
import com.lmelp.mobile.viewmodel.EmissionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmissionsScreen(
    repository: EmissionsRepository,
    onEmissionClick: (String) -> Unit
) {
    val viewModel: EmissionsViewModel = viewModel(factory = EmissionsViewModel.Factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Émissions", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LmelpBleu)
            )
        }
    ) { padding ->
        EmissionsContent(
            uiState = uiState,
            onEmissionClick = onEmissionClick,
            modifier = Modifier.padding(padding)
        )
    }
}

@Composable
fun EmissionsContent(
    uiState: EmissionsUiState,
    onEmissionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        uiState.isLoading -> LoadingIndicator(modifier)
        uiState.error != null -> ErrorMessage(uiState.error, modifier)
        uiState.emissions.isEmpty() -> EmptyState("Aucune émission", modifier)
        else -> LazyColumn(modifier = modifier) {
            items(uiState.emissions, key = { it.id }) { emission ->
                EmissionCard(emission = emission, onClick = { onEmissionClick(emission.id) })
            }
        }
    }
}

@Composable
fun EmissionCard(emission: EmissionUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = emission.titre, style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = emission.date.take(10), style = MaterialTheme.typography.bodySmall)
                Text(text = "${emission.nbAvis} avis", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
