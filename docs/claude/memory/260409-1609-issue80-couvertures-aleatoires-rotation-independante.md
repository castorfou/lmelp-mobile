# Issue #80 — Couvertures aléatoires et rotation indépendante (page d'accueil)

## Problème résolu

Sur la page d'accueil, les 4 DashboardCard affichaient toujours les mêmes couvertures au démarrage (index initiaux = 0) et changeaient toutes en même temps toutes les 10s (ticker unique synchronisé).

## Fichiers modifiés

- `app/src/main/java/com/lmelp/mobile/viewmodel/HomeViewModel.kt` — seul fichier de prod modifié
- `app/src/test/java/com/lmelp/mobile/HomeViewModelTickerTest.kt` — nouveau fichier de tests

## Ce qui a changé dans HomeViewModel.kt

### Supprimé
- Constante `TICKER_INTERVAL_MS = 10 * 1000L` (ticker fixe à 10s)
- Méthode `private suspend fun startTicker()` (boucle unique synchronisée)

### Ajouté

**`randomInitialIndex(size: Int): Int`** (internal, testable)
- Retourne un index aléatoire dans `[0, size-1]`, ou 0 si vide/singleton
- Utilisé dans `loadStats()` pour initialiser les 4 index après chargement des slides

**`sampleTickerDelayMs(meanMs, stdDevMs, minMs, random): Long`** (internal, testable)
- Tire un délai selon N(µ=5000ms, σ=2000ms), tronqué à `minMs=1000ms`
- Paramètre `random: java.util.Random` injectable pour tests déterministes

**`startTicker()` → 4 coroutines indépendantes** (private, non-suspend)
- Lance 4 `viewModelScope.launch { tickerLoop(...) }` en parallèle
- Chacune gère une carte (emissions, palmares, conseils, onkindle)
- Chaque carte tire son propre délai via `sampleTickerDelayMs()`

**`tickerLoop(getSize, updateIndex): suspend`** (private)
- Boucle infinie : delay → lire taille → update index si size > 1
- Paramétrisé par lambdas pour éviter la duplication × 4

### Modification dans `loadStats()`
Après chargement des slides, les 4 index sont initialisés avec `randomInitialIndex()` :
```
emissionsIndex = randomInitialIndex(emissionsSlides.size)
palmaresIndex  = randomInitialIndex(palmaresSlides.size)
conseilsIndex  = randomInitialIndex(conseilsSlides.size)
onkindleIndex  = randomInitialIndex(onkindleSlides.size)
```

## Tests ajoutés (HomeViewModelTickerTest.kt)

- `randomInitialIndex` : vide→0, singleton→0, N éléments→index dans [0,N-1], grand N
- `sampleTickerDelayMs` : jamais < 1000ms (1000 tirages seed=42), médiane dans [3000,7000] (seed=123), déterminisme avec même seed

## Architecture résultante

- `init` → `viewModelScope.launch { loadStats() }` + `startTicker()`
- `startTicker()` est `private fun` (non-suspend), pas `private suspend fun`
- Les fonctions pures `randomInitialIndex` et `sampleTickerDelayMs` sont `internal` pour être accessibles aux tests unitaires du même module

## Effet visuel

- Démarrage : chaque carte montre une couverture différente à chaque lancement
- Rotation : les 4 cartes changent indépendamment, délai variable ~1s–11s (moy 5s, σ 2s)
