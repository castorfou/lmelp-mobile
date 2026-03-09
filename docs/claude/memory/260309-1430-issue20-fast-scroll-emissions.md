# Issue #20 — Fast scroll avec dates dans l'écran Émissions

Date : 2026-03-09
Branche : `20-emissions-mettre-un-ascenseur-rapide-a-droite-avec-les-dates-des-emissions-qui-defilent`

## Comportement

L'écran Émissions dispose d'un **ascenseur rapide à droite** similaire aux galeries photos Android :

- **Rail vertical** (4dp, gris transparent) toujours visible à droite
- **Pouce bleu** (8dp) qui suit la position courante dans la liste
- **Bulle "Nov. 2025"** qui apparaît pendant le scroll et disparaît 2s après arrêt
- La bulle est **draggable** (poignée de navigation)
- Un **tap sur la barre** positionne directement la liste au mois correspondant

## Architecture

### `app/src/main/java/com/lmelp/mobile/ui/emissions/EmissionsScreen.kt`

Nouveau composable `EmissionsListWithFastScroll` remplace le `LazyColumn` nu :

**Calcul des indices de mois** (logique pure, testée) :
```kotlin
val monthIndices: List<Pair<String, Int>> = remember(emissions) {
    emissions.mapIndexedNotNull { index, e ->
        val ym = e.date.take(7)
        if (index == 0 || e.date.take(7) != emissions[index - 1].date.take(7))
            ym to index else null
    }
}
```

**Visibilité de la bulle** (disparaît 2s après arrêt) :
```kotlin
LaunchedEffect(isScrollInProgress, isDragging) {
    if (isScrollInProgress || isDragging) showLabel = true
    else { delay(2000); showLabel = false }
}
```

**Drag par delta** (évite les problèmes de position absolue/relative) :
```kotlin
fun onDragDelta(dragDeltaPx: Float) {
    dragFraction = (dragFraction + dragDeltaPx / barHeightPx).coerceIn(0f, 1f)
    // scroll vers monthIndices[idx]
}
```

**Tap sur la barre** → `detectTapGestures` + `detectVerticalDragGestures` en deux `pointerInput` chaînés sur le même Box.

**Bulle draggable** → `detectDragGestures` (pas vertical-only) avec `dragAmount.y`.

### Formatage des dates

```kotlin
private fun formatYearMonth(yearMonth: String): String // "2025-11" → "Nov. 2025"
private val MOIS_FR = listOf("Jan.", "Fév.", "Mar.", ...)
```

## Tests

`app/src/test/java/com/lmelp/mobile/FastScrollTest.kt` — 16 tests :
- Calcul `monthIndices` (liste vide, 1 mois, N mois, items mixtes)
- Formatage "YYYY-MM" → libellé FR
- Calcul du segment de drag (position 0..1 → index mois)

## Points clés / pièges

- **Ne pas utiliser `change.position.y`** pour le drag (position absolue relative au composable → blocage au milieu). Toujours utiliser **`dragAmount`** (delta).
- La bulle utilise `detectDragGestures` (2D) plutôt que `detectVerticalDragGestures` car son `onDragStart` a besoin de capturer `labelFraction` au moment du début du geste.
- Deux `pointerInput` sur le même `Box` : le premier (`detectTapGestures`) et le second (`detectVerticalDragGestures`) coexistent bien.
- `barHeightPx` mesuré via `onSizeChanged` sur le Box de la barre.
- `derivedStateOf` pour `currentYearMonth` évite les recompositions inutiles.
