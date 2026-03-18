# Issue #64 — Afficher les couvertures dans les listes de livres

**Date** : 2026-03-18
**Branche** : `64-afficher-les-couvertures-dans-les-listes-de-livres`
**Commit** : `d0cb662`

## Contexte

Les couvertures de livres (`url_cover`) étaient stockées dans la table SQLite `livres`
mais n'étaient pas propagées jusqu'à l'UI. La donnée était perdue dès la couche DAO/repository.

## Architecture de la solution

### 1. Propagation du champ `urlCover` dans les modèles UI

`app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt` — ajout de `urlCover: String? = null` dans :
- `LivreUi`
- `PalmaresUi`
- `RecommendationUi`
- `SearchResultUi`
- `OnKindleUi`

### 2. Nouvelles data classes DAO avec JOIN

Pour **Palmarès** (`PalmaresDao`) : nouvelle data class `PalmaresFiltreAvecUrlRow` + méthode
`getPalmaresFiltresAvecUrl()` avec `LEFT JOIN livres l ON p.livre_id = l.id`.

Pour **Recommandations** (`RecommendationsDao`) : nouvelle data class `RecommandationNonLueAvecUrlRow`
+ méthode `getRecommandationsNonLuesAvecUrl()` avec `LEFT JOIN livres l ON r.livre_id = l.id`.

> **Règle Room** : une data class non-`@Entity` peut représenter le résultat d'un JOIN.
> Annoter avec `@ColumnInfo(name = "url_cover")` si le nom SQL diffère du champ Kotlin.

### 3. SearchRepository — FTS5 + LEFT JOIN

`app/src/main/java/com/lmelp/mobile/data/repository/SearchRepository.kt` :
```kotlin
SELECT si.type, si.ref_id AS refId, si.content,
    si.display_content AS displayContent,
    l.url_cover AS url_cover
FROM search_index si
LEFT JOIN livres l ON (si.type = 'livre' AND l.id = si.ref_id)
WHERE si.search_index MATCH ? AND si.type IN ('livre', 'auteur', 'critique')
LIMIT 50
```

### 4. Composable réutilisable `BookCoverThumbnail`

`app/src/main/java/com/lmelp/mobile/ui/components/CommonComponents.kt` :
- Taille par défaut : 48×72dp
- `RoundedCornerShape(4.dp)` + `ContentScale.Crop`
- Si `urlCover == null` : Spacer de même taille (préserve l'alignement)

### 5. Intégration dans 5 écrans

| Écran | Fichier | Composable Card |
|-------|---------|-----------------|
| Détail émission | `EmissionDetailScreen.kt` | `LivreCard` |
| Palmarès | `PalmaresScreen.kt` | `PalmaresCard` |
| Conseils (recommandations) | `RecommendationsScreen.kt` | `RecommendationCard` |
| Recherche | `SearchScreen.kt` | `SearchResultItem` (type livre uniquement) |
| Sur ma liseuse | `OnKindleScreen.kt` | `OnKindleCard` |

## TDD

Tests dans `app/src/test/java/com/lmelp/mobile/UrlCoverPropagationTest.kt` :
- `urlCover est propagée depuis LivreEntity vers LivreUi dans EmissionDetail` (RED→GREEN)
- `urlCover est null dans LivreUi si LivreEntity na pas de cover` (RED→GREEN)

`PalmaresViewModelTest.kt` mis à jour pour utiliser `getPalmaresFiltresAvecUrl` et
`PalmaresFiltreAvecUrlRow` (après refacto DAO).

## Pièges rencontrés

- **`@ColumnInfo(name = "url_cover")`** : obligatoire dans la data class DAO si le champ
  SQL snake_case diffère du champ Kotlin camelCase.
- **`SearchScreen` imports manquants** : `Row` et `Alignment` à ajouter lors de la restructuration
  en Row pour afficher la miniature.
- **`RecommendationCard` Alignment** : utiliser `verticalAlignment = androidx.compose.ui.Alignment.CenterVertically`
  explicitement si `Alignment` n'est pas importé directement.
- **`PalmaresViewModelTest`** : après changement de méthode DAO, mettre à jour TOUS les `whenever()`
  et `verify()` qui référencent l'ancienne méthode.
