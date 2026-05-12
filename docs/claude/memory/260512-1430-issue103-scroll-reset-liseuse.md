# Issue #103 — Scroll reset de la liste liseuse lors du changement de filtre

## Contexte

Écran "Sur ma liseuse" (`OnKindleScreen`) : en alternant entre les filtres (Lus/Non lus) ou les modes de tri, la liste `LazyColumn` ne repartait pas du début. Les livres épinglés (toujours en tête) pouvaient donc rester hors-écran si l'utilisateur avait scrollé vers le bas avant de changer de filtre.

## Root cause

Le `LazyColumn` dans `OnKindleContent` n'avait pas de `LazyListState` explicite. Compose conserve la position de scroll entre recompositions, même quand le contenu change complètement.

## Fix

Dans `app/src/main/java/com/lmelp/mobile/ui/onkindle/OnKindleScreen.kt`, dans le bloc `else` de `OnKindleContent` :

```kotlin
val listState = rememberLazyListState()
LaunchedEffect(uiState.afficherLus, uiState.afficherNonLus, uiState.triMode) {
    listState.scrollToItem(0)
}
LazyColumn(state = listState) { ... }
```

- `rememberLazyListState()` donne le contrôle explicite du scroll
- `LaunchedEffect` keyed sur les 3 filtres/tri déclenche `scrollToItem(0)` à chaque changement
- Pattern standard Compose pour reset de scroll sur changement de contenu

## Tests

Fichier `app/src/test/java/com/lmelp/mobile/OnKindleScrollResetTest.kt` (4 tests JVM) :
- Documentent que les clés du `LaunchedEffect` changent bien dans le ViewModel lors de chaque action filtre/tri
- La partie scroll (UI pure) est couverte par test manuel
- Test notable : vérifie que le livre épinglé reste en position 0 du ViewModel après changement de filtre

## Pattern à retenir

Pour tout `LazyColumn` dont le contenu change suite à des filtres/tri, ajouter systématiquement :
- `rememberLazyListState()`
- `LaunchedEffect(clé1, clé2, ...)` → `scrollToItem(0)`

Ceci s'applique potentiellement à d'autres écrans (Palmarès, Recommandations) si le même problème apparaît.
