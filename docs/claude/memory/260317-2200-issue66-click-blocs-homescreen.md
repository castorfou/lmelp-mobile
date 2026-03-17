# Issue #66 — Fix click sur blocs émissions/palmarès/conseils + ajustements UI bloc Recherche

## Contexte

Suite au PR #59 (swipe local par bloc), les taps sur les blocs de la HomeScreen ne naviguaient plus vers les écrans correspondants.

## Cause racine

`DashboardCard` utilisait `detectHorizontalDragGestures` pour gérer swipe + fallback tap. Problème : ce gesture detector **ne détecte que les drags** — un tap pur (touch down + up sans mouvement) n'appelle jamais `onDragEnd`, donc `else -> onClick()` n'était jamais exécuté.

## Fix TDD

### Fonction pure extraite (testable)

Dans `app/src/main/java/com/lmelp/mobile/ui/home/HomeScreen.kt` :

```kotlin
enum class GestureAction { TAP, SWIPE_LEFT, SWIPE_RIGHT }

internal fun resolveGestureAction(totalDragX: Float, thresholdPx: Float): GestureAction = when {
    totalDragX < -thresholdPx -> GestureAction.SWIPE_LEFT
    totalDragX > thresholdPx -> GestureAction.SWIPE_RIGHT
    else -> GestureAction.TAP
}
```

9 tests unitaires dans `app/src/test/java/com/lmelp/mobile/DashboardCardGestureTest.kt` couvrent : tap pur (0f), micro-mouvements (< 60dp = TAP), swipe gauche/droite (> 60dp).

### Remplacement du gesture detector

`detectHorizontalDragGestures` → `awaitEachGesture` + `awaitFirstDown` + `horizontalDrag` :

- `awaitFirstDown` : capture le touch down (tap ET drag)
- `horizontalDrag` : accumule le déplacement jusqu'au relâchement (retourne immédiatement si pas de drag = tap pur)
- `resolveGestureAction` : décide TAP / SWIPE_LEFT / SWIPE_RIGHT

**Clé** : `horizontalDrag` retourne immédiatement si le doigt est levé sans bouger → `totalDragX` reste 0f → `resolveGestureAction` retourne `TAP` → `onClick()` est appelé.

## Ajustements UI bloc Recherche (HomeScreen)

- Fond : `Color.White` → `Color(0xFFF7F7F7)` (gris très léger ~10%)
- Bordure verte : 1.5dp → 6dp
- Mini barre de recherche : centrée verticalement → collée à 8dp sous la zone verte (`Arrangement.Top` + `padding(top = 8.dp)`)

## Ajustements UI écran Recherche (SearchScreen)

- `SearchBar` : ajout de `windowInsets = WindowInsets(0)` pour supprimer le padding interne Material3
- `vertical = 8.dp` conservé pour espacer proprement sous la TopAppBar

## Piège à retenir

`detectHorizontalDragGestures` ignore les taps purs. Quand on veut gérer **à la fois taps et swipes** sur un même composable, utiliser `awaitEachGesture` + `awaitFirstDown` + `horizontalDrag`.

## Fichiers modifiés

- `app/src/main/java/com/lmelp/mobile/ui/home/HomeScreen.kt`
- `app/src/main/java/com/lmelp/mobile/ui/search/SearchScreen.kt`
- `app/src/test/java/com/lmelp/mobile/DashboardCardGestureTest.kt` (nouveau)
