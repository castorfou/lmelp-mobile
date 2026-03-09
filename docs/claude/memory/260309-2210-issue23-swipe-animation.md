# Issue #23 — Animation de slide lors du swipe entre onglets

Date : 2026-03-09
Branche : `23-navigation-le-swipe-gauchedroite-contient-une-animation`

## Comportement

Le swipe gauche/droite entre les onglets principaux produit désormais une animation de slide horizontal (comme les galeries Android). La page glisse dans la bonne direction selon le sens du swipe.

## Architecture

### Principe : state hoisting de la direction

La direction du swipe est stockée dans un `MutableState<Int>` dans `MainActivity` et passée en paramètre à `LmelpNavHost`. Les lambdas de transition la capturent au moment de la recomposition.

### Convention de direction

- `-1` = swipe vers la gauche (page suivante) → nouvelle page entre par la droite
- `+1` = swipe vers la droite (page précédente) → nouvelle page entre par la gauche
- `0` = navigation directe (tap) → comportement par défaut (comme `-1`)

### `app/src/main/java/com/lmelp/mobile/MainActivity.kt`

```kotlin
var swipeDirection by remember { mutableStateOf(0) }

fun navigateBySwipe(direction: Int) {
    swipeDirection = direction   // mis à jour AVANT navigate()
    val targetIndex = ...
    navController.navigate(...)
}

LmelpNavHost(swipeDirection = swipeDirection, ...)
```

### `app/src/main/java/com/lmelp/mobile/Navigation.kt`

```kotlin
fun LmelpNavHost(..., swipeDirection: Int = 0, ...) {
    val slideEnter = { slideInHorizontally { if (swipeDirection <= 0) it else -it } }
    val slideExit  = { slideOutHorizontally { if (swipeDirection <= 0) -it else it } }

    // Appliqué sur les 5 routes swipeables :
    composable(Routes.EMISSIONS,
        enterTransition = { slideEnter() },
        exitTransition = { slideExit() }
    ) { ... }
    // idem pour HOME, PALMARES, SEARCH, RECOMMENDATIONS
}
```

**Routes sans transition** (comportement par défaut) : ABOUT, EMISSION_DETAIL, LIVRE_DETAIL, CRITIQUES.

### `app/src/test/java/com/lmelp/mobile/SwipeAnimationTest.kt`

8 tests JUnit purs sur deux fonctions extraites :
- `slideEnterOffset(swipeDirection, fullWidth)` — offset d'entrée
- `slideExitOffset(swipeDirection, fullWidth)` — offset de sortie

## Points clés

- **Aucune dépendance ajoutée** — `slideInHorizontally`/`slideOutHorizontally` sont dans `androidx.compose.animation` déjà présent via le BOM
- `swipeDirection` doit être mis à jour **avant** `navController.navigate()` pour que la lambda de transition lise la bonne valeur
- Les lambdas `slideEnter`/`slideExit` sont des variables locales (non-`@Composable`) capturant `swipeDirection` — elles sont recalculées à chaque recomposition de `LmelpNavHost`
