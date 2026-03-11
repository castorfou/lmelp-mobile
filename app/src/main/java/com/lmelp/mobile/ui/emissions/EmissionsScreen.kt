package com.lmelp.mobile.ui.emissions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lmelp.mobile.data.model.EmissionUi
import com.lmelp.mobile.data.repository.EmissionsRepository
import com.lmelp.mobile.ui.components.EmptyState
import com.lmelp.mobile.ui.components.ErrorMessage
import com.lmelp.mobile.ui.components.LoadingIndicator
import com.lmelp.mobile.ui.theme.LmelpBleu
import com.lmelp.mobile.viewmodel.EmissionsUiState
import com.lmelp.mobile.viewmodel.EmissionsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val MOIS_FR = listOf(
    "Jan.", "Fév.", "Mar.", "Avr.", "Mai", "Juin",
    "Juil.", "Août", "Sep.", "Oct.", "Nov.", "Déc."
)

private fun formatYearMonth(yearMonth: String): String {
    val parts = yearMonth.split("-")
    val year = parts[0].toInt()
    val month = parts[1].toInt()
    return "${MOIS_FR[month - 1]} $year"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmissionsScreen(
    repository: EmissionsRepository,
    onEmissionClick: (String) -> Unit
) {
    val viewModel: EmissionsViewModel = viewModel(factory = EmissionsViewModel.Factory(repository))
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        contentWindowInsets = WindowInsets(0),
        topBar = {
            TopAppBar(
                title = { Text("Émissions", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = LmelpBleu),
                windowInsets = WindowInsets.statusBars
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
        else -> EmissionsListWithFastScroll(
            emissions = uiState.emissions,
            onEmissionClick = onEmissionClick,
            modifier = modifier
        )
    }
}

@Composable
fun EmissionsListWithFastScroll(
    emissions: List<EmissionUi>,
    onEmissionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Calcul des indices de début de chaque mois
    val monthIndices: List<Pair<String, Int>> = remember(emissions) {
        emissions.mapIndexedNotNull { index, e ->
            val ym = e.date.take(7)
            if (index == 0 || e.date.take(7) != emissions[index - 1].date.take(7))
                ym to index
            else
                null
        }
    }

    // Fraction de scroll réelle (0..1) : 0 = tout en haut, 1 = tout en bas
    // Basée sur layoutInfo pour atteindre exactement 1.0 quand le dernier item est visible
    val scrollFraction by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val total = info.totalItemsCount
            if (total <= 1) return@derivedStateOf 0f
            val firstVisible = listState.firstVisibleItemIndex
            val firstItem = info.visibleItemsInfo.firstOrNull()
                ?: return@derivedStateOf 0f
            val itemH = firstItem.size.toFloat().coerceAtLeast(1f)
            val scrolled = firstVisible + listState.firstVisibleItemScrollOffset / itemH
            val visibleCount = info.visibleItemsInfo.size.toFloat().coerceAtLeast(1f)
            val maxScroll = (total - visibleCount).coerceAtLeast(1f)
            (scrolled / maxScroll).coerceIn(0f, 1f)
        }
    }

    // Mois courant selon le premier item visible (pour l'affichage de la liste)
    val currentYearMonth by remember {
        derivedStateOf {
            val firstVisible = listState.firstVisibleItemIndex
            monthIndices.lastOrNull { it.second <= firstVisible }?.first
                ?: monthIndices.firstOrNull()?.first
                ?: ""
        }
    }

    // Contrôle de la visibilité de la bulle (disparaît 2s après la fin du scroll/drag)
    var showLabel by remember { mutableStateOf(false) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var barHeightPx by remember { mutableFloatStateOf(1f) }

    // Afficher la bulle quand le scroll bouge, la masquer 2s après arrêt
    val isScrollInProgress = listState.isScrollInProgress
    LaunchedEffect(isScrollInProgress, isDragging) {
        if (isScrollInProgress || isDragging) {
            showLabel = true
        } else {
            delay(2000)
            showLabel = false
        }
    }

    // Fraction du pouce : drag = dragFraction, scroll = scrollFraction (atteint 1.0 en bas)
    val thumbFraction = if (isDragging) dragFraction else scrollFraction
    // Ref stable pour lire thumbFraction dans les lambdas pointerInput sans recréer le handler
    val thumbFractionState = rememberUpdatedState(thumbFraction)

    // Mois affiché dans la bulle : dérivé de thumbFraction → même mois que la position du pouce
    val labelToShow: String = if (monthIndices.isEmpty()) "" else {
        val idx = dragFractionToMonthIndex(thumbFraction, monthIndices.size)
        monthIndices[idx].first
    }

    // Handler partagé pour le drag (utilisé à la fois par la barre et la bulle)
    // startFraction : fraction de départ du geste (0..1 dans la barre)
    fun onDragStart(startFractionY: Float) {
        isDragging = true
        showLabel = true
        dragFraction = startFractionY.coerceIn(0f, 1f)
    }

    // dragDelta : déplacement en pixels depuis le dernier événement
    fun onDragDelta(dragDeltaPx: Float) {
        dragFraction = (dragFraction + dragDeltaPx / barHeightPx).coerceIn(0f, 1f)
        if (monthIndices.isNotEmpty()) {
            val idx = dragFractionToMonthIndex(dragFraction, monthIndices.size)
            coroutineScope.launch {
                listState.scrollToItem(monthIndices[idx].second)
            }
        }
    }

    fun onDragEnd() {
        isDragging = false
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Liste principale avec padding à droite pour ne pas passer sous la barre
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(end = 20.dp)
        ) {
            items(emissions, key = { it.id }) { emission ->
                EmissionCard(emission = emission, onClick = { onEmissionClick(emission.id) })
            }
        }

        // Barre de fast scroll à droite (rail + pouce)
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .width(20.dp)
                .onSizeChanged { barHeightPx = it.height.toFloat().coerceAtLeast(1f) }
                .pointerInput(monthIndices) {
                    detectTapGestures { offset ->
                        // Tap direct sur la barre → scroll immédiat à cet endroit
                        val fraction = (offset.y / barHeightPx).coerceIn(0f, 1f)
                        if (monthIndices.isNotEmpty()) {
                            val idx = dragFractionToMonthIndex(fraction, monthIndices.size)
                            showLabel = true
                            dragFraction = fraction
                            coroutineScope.launch {
                                listState.scrollToItem(monthIndices[idx].second)
                            }
                        }
                    }
                }
                .pointerInput(monthIndices) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            onDragStart(offset.y / barHeightPx)
                        },
                        onDragEnd = { onDragEnd() },
                        onDragCancel = { onDragEnd() },
                        onVerticalDrag = { change, dragAmount ->
                            change.consume()
                            onDragDelta(dragAmount)
                        }
                    )
                }
        ) {
            // Rail vertical
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxHeight()
                    .width(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
            )

            // Pouce sur le rail — même hauteur que la bulle (28dp)
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        val thumbH = 28.dp.roundToPx()
                        IntOffset(
                            0,
                            (thumbFraction * barHeightPx).toInt()
                                .coerceIn(0, (barHeightPx.toInt() - thumbH).coerceAtLeast(0))
                        )
                    }
                    .width(8.dp)
                    .height(28.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(LmelpBleu)
            )
        }

        // Bulle mois/an — draggable, disparaît 2s après arrêt
        // Alignée verticalement avec le centre du pouce (issue #35)
        if (showLabel && labelToShow.isNotEmpty() && monthIndices.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        // Pouce et bulle ont la même hauteur (28dp) → même bord haut = même Y
                        // Espace de 4dp entre la bulle et la barre de scroll (20dp de large)
                        val thumbHeightPx = 28.dp.roundToPx()
                        val spacePx = 4.dp.roundToPx()
                        val barWidthPx = 20.dp.roundToPx()
                        val thumbY = (thumbFraction * barHeightPx).toInt()
                            .coerceIn(0, (barHeightPx.toInt() - thumbHeightPx).coerceAtLeast(0))
                        IntOffset(x = -(barWidthPx + spacePx), y = thumbY)
                    }
                    // La bulle est une poignée de navigation : drag par delta
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { _ -> onDragStart(thumbFractionState.value) },
                            onDragEnd = { onDragEnd() },
                            onDragCancel = { onDragEnd() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                onDragDelta(dragAmount.y)
                            }
                        )
                    }
                    .clip(RoundedCornerShape(8.dp))
                    .background(LmelpBleu)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = formatYearMonth(labelToShow),
                    color = Color.White,
                    fontSize = 12.sp
                )
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
