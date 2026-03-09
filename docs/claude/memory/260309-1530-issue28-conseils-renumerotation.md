# Issue #28 — Renumérotation des Conseils

Date : 2026-03-09
Branche : `28-conseils-renommer-les-pour-quils-se-suivent`

## Problème

Après le filtrage des livres lus (issue #19), l'écran Conseils affichait les rangs stockés en base (`rank` de la table `recommendations`), qui ne se suivent plus après filtrage (ex: #2, #5, #7…).

## Solution

Dans `app/src/main/java/com/lmelp/mobile/ui/recommendations/RecommendationsScreen.kt` :

- Remplacement de `items(...)` par `itemsIndexed(...)` dans `RecommendationsContent`
- `RecommendationCard` reçoit un nouveau paramètre `displayRank: Int` (au lieu d'utiliser `item.rank`)
- Affichage `"#$displayRank"` = `index + 1` → numérotation 1, 2, 3... consécutive

```kotlin
itemsIndexed(uiState.recommendations, key = { _, item -> item.livreId }) { index, item ->
    RecommendationCard(item = item, displayRank = index + 1, onClick = { ... })
}
```

## Points clés

- Le champ `rank` de `RecommendationUi` reste inchangé (rank SVD de la base, utile pour le tri)
- La numérotation affichée est découplée du rang de base → robuste à tout filtrage futur
- Correction purement UI, aucun changement de modèle ou DAO
