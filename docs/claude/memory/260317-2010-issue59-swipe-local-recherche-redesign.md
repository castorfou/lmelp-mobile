# Issue #59 — Swipe local par bloc + redesign bloc Recherche

## Contexte

Refonte de la navigation par swipe sur l'écran d'accueil (`HomeScreen`) :
- Supprimer le swipe global (qui naviguait entre écrans depuis l'accueil)
- Activer des swipes **locaux** dans chaque bloc (émissions, palmarès, conseils, liseuse) pour naviguer entre les couvertures de livres
- Redesign du bloc Recherche : bandeau vert + fond blanc + mini SearchBar

## Fichiers modifiés

- `app/src/main/java/com/lmelp/mobile/MainActivity.kt`
- `app/src/main/java/com/lmelp/mobile/ui/home/HomeScreen.kt`
- `app/src/main/java/com/lmelp/mobile/viewmodel/HomeViewModel.kt`
- `app/src/test/java/com/lmelp/mobile/HomeViewModelSlideNavigationTest.kt` (nouveau)

## Ce qui a été fait

### 1. Swipe global désactivé sur HOME (`MainActivity.kt:136`)
```kotlin
if (currentRoute == Routes.HOME) return@pointerInput
```
Le `pointerInput` global qui gérait la navigation entre écrans retourne immédiatement sur l'écran d'accueil.

### 2. Logique de navigation extraite en fonctions pures (`HomeViewModel.kt`)
```kotlin
internal fun nextSlideIndex(currentIndex: Int, size: Int): Int =
    if (size <= 1) 0 else (currentIndex + 1) % size

internal fun prevSlideIndex(currentIndex: Int, size: Int): Int =
    if (size <= 1) 0 else (currentIndex - 1 + size) % size
```
Ces fonctions `internal` sont testables sans coroutines ni ViewModel.

8 fonctions publiques ajoutées dans `HomeViewModel` : `nextEmissionsSlide`, `prevEmissionsSlide`, `nextPalmaresSlide`, `prevPalmaresSlide`, `nextConseilsSlide`, `prevConseilsSlide`, `nextOnkindleSlide`, `prevOnkindleSlide`.

### 3. Swipe local dans `DashboardCard` (`HomeScreen.kt`)
- Nouveau paramètre `slideDirection: Int` (state local, -1 = gauche, +1 = droite)
- `detectHorizontalDragGestures` met à jour `slideDirection` avant d'invoquer le callback
- `AnimatedContent` utilise `slideDirection` pour animer dans le bon sens :
  - Swipe gauche → nouvelle cover arrive par la droite
  - Swipe droite → nouvelle cover arrive par la gauche
- Tap court (< seuil 60dp) → `onClick()` (navigation vers l'écran)

### 4. Bloc Recherche redesigné (`HomeScreen.kt`)
Remplacé `DashboardCard` par un `Card` Material3 standard :
- Bordure verte (`LmelpVert`, 1.5dp)
- Bandeau vert en haut avec "Recherche" aligné à gauche
- Fond blanc en dessous avec mini SearchBar grisée (`Color.Black.copy(alpha = 0.08f)`)

## Piège TDD : `HomeViewModel` ne peut pas être testé directement

`HomeViewModel.__init__` lance `startTicker()` (boucle `while(true)` avec `delay`).
Même avec `StandardTestDispatcher` ou `UnconfinedTestDispatcher`, `runTest` attend
la fin de tous les jobs → timeout/blocage infini.

**Solution** : extraire la logique pure en fonctions `internal` et tester celles-ci
directement dans `HomeViewModelSlideNavigationTest`, sans jamais instancier `HomeViewModel`.

## Tests

8 tests unitaires dans `HomeViewModelSlideNavigationTest` — passent en < 1s sans blocage.
