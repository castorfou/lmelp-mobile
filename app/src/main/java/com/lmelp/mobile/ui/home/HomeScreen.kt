package com.lmelp.mobile.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.lmelp.mobile.R
import com.lmelp.mobile.data.model.DerniereEmissionUi
import com.lmelp.mobile.data.model.SlideItem
import com.lmelp.mobile.data.repository.HomeRepository
import com.lmelp.mobile.ui.components.NoteBadge
import com.lmelp.mobile.ui.theme.LmelpBleu
import com.lmelp.mobile.ui.theme.LmelpBordeaux
import com.lmelp.mobile.ui.theme.LmelpNightBlue
import com.lmelp.mobile.ui.theme.LmelpNightBlueEnd
import com.lmelp.mobile.ui.theme.LmelpVert
import com.lmelp.mobile.viewmodel.HomeUiState
import com.lmelp.mobile.viewmodel.HomeViewModel

@Composable
fun HomeScreen(
    repository: HomeRepository,
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
            uiState = uiState,
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

/**
 * Carte de dashboard avec fond animé (rotation de couvertures via AnimatedContent) ou couleur unie.
 * La transition (fondu ou glissement) est choisie selon le livreId courant.
 */
@Composable
fun DashboardCard(
    onClick: () -> Unit,
    backgroundColor: Color,
    currentSlide: SlideItem? = null,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = currentSlide?.livreId to currentSlide?.urlCouverture,
                transitionSpec = {
                    val useSlide = (targetState.first?.hashCode() ?: 0) % 2 == 0
                    if (useSlide) {
                        (slideInHorizontally(tween(700)) { it } togetherWith
                         slideOutHorizontally(tween(700)) { -it })
                    } else {
                        (fadeIn(tween(700)) togetherWith fadeOut(tween(700)))
                    }.using(SizeTransform(clip = true))
                },
                modifier = Modifier.fillMaxSize()
            ) { (_, urlCouverture) ->
                if (urlCouverture != null) {
                    AsyncImage(
                        model = urlCouverture,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize())
                }
            }
            if (currentSlide?.urlCouverture != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.1f),
                                    Color.Black.copy(alpha = 0.75f)
                                )
                            )
                        )
                )
            }
            content()
        }
    }
}

@Composable
fun NavTilesGrid(
    uiState: HomeUiState,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentEmission = uiState.emissionsSlides.getOrNull(uiState.emissionsIndex)
    val currentPalmares = uiState.palmaresSlides.getOrNull(uiState.palmaresIndex)
    val currentConseils = uiState.conseilsSlides.getOrNull(uiState.conseilsIndex)
    val currentOnKindle = uiState.onkindleSlides.getOrNull(uiState.onkindleIndex)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Tuile Émissions (grande, avec image de fond animée)
            DashboardCard(
                onClick = { onNavigate("emissions") },
                backgroundColor = LmelpBleu,
                currentSlide = currentEmission,
                modifier = Modifier
                    .weight(2f)
                    .fillMaxHeight()
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    currentEmission?.noteMoyenne?.let {
                        NoteBadge(
                            note = it,
                            modifier = Modifier.align(Alignment.TopEnd)
                        )
                    }
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.List,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "Émissions",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        if (uiState.nbEmissions.isNotEmpty() && uiState.nbEmissions != "—") {
                            Text(
                                text = "${uiState.nbEmissions} émissions",
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        currentEmission?.let {
                            Text(
                                text = it.titre,
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            it.date?.let { d ->
                                Text(
                                    text = d,
                                    color = Color.White.copy(alpha = 0.55f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1
                                )
                            }
                        } ?: uiState.derniereEmission?.let {
                            Text(
                                text = it.titre,
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Colonne droite : Palmarès + Conseils
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DashboardCard(
                    onClick = { onNavigate("palmares") },
                    backgroundColor = LmelpVert,
                    currentSlide = currentPalmares,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Palmarès",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            currentPalmares?.let { slide ->
                                Text(
                                    text = slide.titre,
                                    color = Color.White.copy(alpha = 0.85f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                DashboardCard(
                    onClick = { onNavigate("recommendations") },
                    backgroundColor = LmelpBordeaux,
                    currentSlide = currentConseils,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Column {
                            Text(
                                text = "Conseils",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                            currentConseils?.let { slide ->
                                Text(
                                    text = slide.titre,
                                    color = Color.White.copy(alpha = 0.75f),
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } ?: Text(
                                text = "Pour vous",
                                color = Color.White.copy(alpha = 0.75f),
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }

        // Rangée basse : OnKindle + Recherche
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.6f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DashboardCard(
                onClick = { onNavigate("onkindle") },
                backgroundColor = LmelpBordeaux,
                currentSlide = currentOnKindle,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.List,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Column {
                        Text(
                            text = "Liseuse",
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp
                        )
                        currentOnKindle?.let { slide ->
                            Text(
                                text = slide.titre,
                                color = Color.White.copy(alpha = 0.85f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            DashboardCard(
                onClick = { onNavigate("search") },
                backgroundColor = LmelpVert,
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "Recherche",
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = "Livres, auteurs...",
                        color = Color.White.copy(alpha = 0.75f),
                        style = MaterialTheme.typography.labelSmall,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}
