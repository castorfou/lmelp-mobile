package com.lmelp.mobile.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmelp.mobile.R
import com.lmelp.mobile.data.repository.MetadataRepository
import com.lmelp.mobile.ui.theme.LmelpBleu
import com.lmelp.mobile.ui.theme.LmelpBordeaux
import com.lmelp.mobile.ui.theme.LmelpNightBlue
import com.lmelp.mobile.ui.theme.LmelpNightBlueEnd
import com.lmelp.mobile.ui.theme.LmelpVert
import com.lmelp.mobile.viewmodel.HomeUiState
import com.lmelp.mobile.viewmodel.HomeViewModel

data class NavTile(
    val label: String,
    val icon: ImageVector,
    val color: Color,
    val route: String
)

@Composable
fun HomeScreen(
    repository: MetadataRepository,
    onNavigate: (String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeContent(
        uiState = uiState,
        onNavigate = onNavigate,
        onSettingsClick = onSettingsClick,
        modifier = modifier
    )
}

@Composable
fun HomeContent(
    uiState: HomeUiState,
    onNavigate: (String) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        HeroSection(
            uiState = uiState,
            onSettingsClick = onSettingsClick,
            modifier = Modifier.fillMaxWidth()
        )
        NavTilesGrid(
            onNavigate = onNavigate,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(12.dp)
        )
    }
}

@Composable
fun HeroSection(
    uiState: HomeUiState,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(LmelpNightBlue, LmelpNightBlueEnd)
                )
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(16.dp)
        ) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Paramètres",
                    tint = Color.White.copy(alpha = 0.8f)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, end = 40.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.drawable.masque_et_la_plume),
                    contentDescription = "Le Masque et la Plume",
                    modifier = Modifier
                        .size(110.dp)
                        .padding(end = 16.dp),
                    contentScale = ContentScale.Fit
                )
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "Le Masque et la Plume",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Tribune de critiques littéraires depuis 1955",
                        color = Color.White.copy(alpha = 0.75f),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
fun NavTilesGrid(
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NavTileItem(
                tile = NavTile("Émissions", Icons.AutoMirrored.Filled.List, LmelpBleu, "emissions"),
                onClick = { onNavigate("emissions") },
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NavTileItem(
                    tile = NavTile("Palmarès", Icons.Default.Star, LmelpVert, "palmares"),
                    onClick = { onNavigate("palmares") },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
                NavTileItem(
                    tile = NavTile("Conseils", Icons.Default.Person, LmelpBordeaux, "recommendations"),
                    onClick = { onNavigate("recommendations") },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            NavTileItem(
                tile = NavTile("Critiques", Icons.AutoMirrored.Filled.List, LmelpBordeaux, "critiques"),
                onClick = { onNavigate("critiques") },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            )
            NavTileItem(
                tile = NavTile("Recherche", Icons.Default.Search, LmelpVert, "search"),
                onClick = { onNavigate("search") },
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
            )
        }
    }
}

@Composable
fun NavTileItem(
    tile: NavTile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = tile.color)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                tile.icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = tile.label,
                color = Color.White,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
