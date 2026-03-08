# Issue #17 — Navigation circulaire par swipe

Date : 2026-03-08
Branche : `17-naviguer-entre-pages-de-la-navbar-avec-mouvement-gauche-a-droite-et-droite-a-gauche`
Commit : `38dae47`

## Comportement

Swipe gauche = page suivante, swipe droite = page précédente.
Ordre circulaire : Home → Émissions → Palmarès → Conseils → Recherche → (retour Home)

## Implémentation — un seul fichier modifié : `MainActivity.kt`

`detectHorizontalDragGestures` sur le `Modifier` du `LmelpNavHost` :

```kotlin
// Liste ordonnée (circulaire, inclut Home)
val swipeRoutes = listOf(Routes.HOME, Routes.EMISSIONS, Routes.PALMARES, Routes.RECOMMENDATIONS, Routes.SEARCH)
val swipeThresholdPx = with(LocalDensity.current) { 80.dp.toPx() }

fun navigateBySwipe(direction: Int) {
    val currentIndex = swipeRoutes.indexOf(currentRoute)
    if (currentIndex == -1) return  // route hors séquence (Critiques, detail pages) → ignoré
    val targetIndex = (currentIndex - direction + swipeRoutes.size) % swipeRoutes.size
    val targetRoute = swipeRoutes[targetIndex]
    if (targetRoute == Routes.HOME) {
        navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
    } else {
        navController.navigate(targetRoute) { popUpTo(Routes.HOME) { saveState = true }; launchSingleTop = true; restoreState = true }
    }
}

modifier = Modifier.padding(innerPadding).pointerInput(currentRoute) {
    var totalDragX = 0f
    detectHorizontalDragGestures(
        onDragStart = { totalDragX = 0f },
        onHorizontalDrag = { change, dragAmount -> change.consume(); totalDragX += dragAmount },
        onDragEnd = {
            when {
                totalDragX < -swipeThresholdPx -> navigateBySwipe(-1)
                totalDragX > swipeThresholdPx -> navigateBySwipe(+1)
            }
            totalDragX = 0f
        },
        onDragCancel = { totalDragX = 0f }
    )
}
```

## Points clés

- `pointerInput(currentRoute)` : la clé `currentRoute` réinitialise le bloc à chaque changement de page → évite les accumulations de drag
- `change.consume()` : signale que le drag horizontal est traité ; compatible avec `LazyColumn` (axes orthogonaux)
- Routes hors `swipeRoutes` (ex: `critiques`, pages de détail) → `indexOf` retourne -1, swipe ignoré
- Aucune modification des screens individuels

## Tests

`app/src/test/java/com/lmelp/mobile/SwipeNavigationTest.kt` — 11 tests unitaires purs (pas Android), couvrant les 10 transitions + route inconnue.
