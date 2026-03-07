package com.lmelp.mobile.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import com.lmelp.mobile.ui.theme.LmelpVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmelp.mobile.data.model.SearchResultUi
import com.lmelp.mobile.data.repository.SearchRepository
import com.lmelp.mobile.ui.components.EmptyState
import com.lmelp.mobile.ui.components.ErrorMessage
import com.lmelp.mobile.ui.components.LoadingIndicator
import com.lmelp.mobile.viewmodel.SearchUiState
import com.lmelp.mobile.viewmodel.SearchViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    repository: SearchRepository,
    onResultClick: (type: String, id: String) -> Unit
) {
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recherche", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LmelpVert)
            )
        }
    ) { padding ->
        SearchContent(
            uiState = uiState,
            onQueryChange = viewModel::onQueryChange,
            onResultClick = onResultClick,
            modifier = Modifier.padding(padding)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchContent(
    uiState: SearchUiState,
    onQueryChange: (String) -> Unit,
    onResultClick: (type: String, id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SearchBar(
            query = uiState.query,
            onQueryChange = onQueryChange,
            onSearch = {},
            active = false,
            onActiveChange = {},
            placeholder = { Text("Rechercher un livre, auteur, émission...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        ) {}

        when {
            uiState.isLoading -> LoadingIndicator()
            uiState.error != null -> ErrorMessage(uiState.error)
            uiState.query.isBlank() -> EmptyState("Tapez votre recherche")
            uiState.results.isEmpty() -> EmptyState("Aucun résultat pour « ${uiState.query} »")
            else -> LazyColumn {
                items(uiState.results) { result ->
                    SearchResultItem(
                        result = result,
                        onClick = { onResultClick(result.type, result.refId) }
                    )
                }
            }
        }
    }
}

@Composable
fun SearchResultItem(result: SearchResultUi, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = result.type.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Text(text = result.content.take(80), style = MaterialTheme.typography.bodyMedium)
        }
    }
}
