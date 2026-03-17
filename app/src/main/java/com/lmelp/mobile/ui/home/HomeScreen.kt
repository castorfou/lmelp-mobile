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
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
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
        onNextEmissions = viewModel::nextEmissionsSlide,
        onPrevEmissions = viewModel::prevEmissionsSlide,
        onNextPalmares = viewModel::nextPalmaresSlide,
        onPrevPalmares = viewModel::prevPalmaresSlide,
        onNextConseils = viewModel::nextConseilsSlide,
        onPrevConseils = viewModel::prevConseilsSlide,
        onNextOnkindle = viewModel::nextOnkindleSlide,
        onPrevOnkindle = viewModel::prevOnkindleSlide,
        modifier = modifier
    )
}

@Composable
fun HomeContent(
    uiState: HomeUiState,
    onNavigate: (String) -> Unit,
    onSettingsClick: () -> Unit,
    onNextEmissions: () -> Unit = {},
    onPrevEmissions: () -> Unit = {},
    onNextPalmares: () -> Unit = {},
    onPrevPalmares: () -> Unit = {},
    onNextConseils: () -> Unit = {},
    onPrevConseils: () -> Unit = {},
    onNextOnkindle: () -> Unit = {},
    onPrevOnkindle: () -> Unit = {},
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
            onNextEmissions = onNextEmissions,
            onPrevEmissions = onPrevEmissions,
            onNextPalmares = onNextPalmares,
            onPrevPalmares = onPrevPalmares,
            onNextConseils = onNextConseils,
            onPrevConseils = onPrevConseils,
            onNextOnkindle = onNextOnkindle,
            onPrevOnkindle = onPrevOnkindle,
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

private val SWIPE_THRESHOLD_DP = 60.dp

/**
 * Carte de dashboard avec fond animé (rotation de couvertures via AnimatedContent) ou couleur unie.
 * La direction du swipe (gauche=-1, droite=+1) détermine le sens de l'animation de slide.
 * onSwipeLeft/onSwipeRight permettent de naviguer entre les livres par swipe local.
 */
@Composable
fun DashboardCard(
    onClick: () -> Unit,
    backgroundColor: Color,
    currentSlide: SlideItem? = null,
    onSwipeLeft: (() -> Unit)? = null,
    onSwipeRight: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var totalDragX by remember { mutableFloatStateOf(0f) }
    // direction: -1 = slide vient de droite (swipe gauche = page suivante)
    //            +1 = slide vient de gauche (swipe droite = page précédente)
    var slideDirection by remember { mutableIntStateOf(-1) }

    val swipeModifier = if (onSwipeLeft != null || onSwipeRight != null) {
        Modifier.pointerInput(Unit) {
            val thresholdPx = SWIPE_THRESHOLD_DP.toPx()
            detectHorizontalDragGestures(
                onDragStart = { totalDragX = 0f },
                onHorizontalDrag = { change, dragAmount ->
                    change.consume()
                    totalDragX += dragAmount
                },
                onDragEnd = {
                    when {
                        totalDragX < -thresholdPx -> {
                            slideDirection = -1
                            onSwipeLeft?.invoke()
                        }
                        totalDragX > thresholdPx -> {
                            slideDirection = 1
                            onSwipeRight?.invoke()
                        }
                        else -> onClick()
                    }
                    totalDragX = 0f
                },
                onDragCancel = { totalDragX = 0f }
            )
        }
    } else {
        Modifier.clickable(onClick = onClick)
    }

    Card(
        modifier = modifier.then(swipeModifier),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = currentSlide?.livreId to currentSlide?.urlCouverture,
                transitionSpec = {
                    val dir = slideDirection
                    (slideInHorizontally(tween(500)) { if (dir < 0) it else -it } togetherWith
                     slideOutHorizontally(tween(500)) { if (dir < 0) -it else it })
                        .using(SizeTransform(clip = true))
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
    onNextEmissions: () -> Unit = {},
    onPrevEmissions: () -> Unit = {},
    onNextPalmares: () -> Unit = {},
    onPrevPalmares: () -> Unit = {},
    onNextConseils: () -> Unit = {},
    onPrevConseils: () -> Unit = {},
    onNextOnkindle: () -> Unit = {},
    onPrevOnkindle: () -> Unit = {},
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
                onSwipeLeft = onNextEmissions,
                onSwipeRight = onPrevEmissions,
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
                    onSwipeLeft = onNextPalmares,
                    onSwipeRight = onPrevPalmares,
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
                    onSwipeLeft = onNextConseils,
                    onSwipeRight = onPrevConseils,
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
                onSwipeLeft = onNextOnkindle,
                onSwipeRight = onPrevOnkindle,
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

            // Bloc Recherche : bandeau vert en haut avec titre, fond blanc dessous avec mini SearchBar
            Card(
                onClick = { onNavigate("search") },
                modifier = Modifier
                    .weight(1.5f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.5.dp, LmelpVert)
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Bandeau vert avec titre "Recherche" en haut à gauche
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(LmelpVert)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "Recherche",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                    // Zone blanche avec mini SearchBar
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    color = Color.Black.copy(alpha = 0.08f),
                                    shape = RoundedCornerShape(24.dp)
                                )
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = Color.Black.copy(alpha = 0.45f),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Livres, auteurs...",
                                color = Color.Black.copy(alpha = 0.45f),
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}
