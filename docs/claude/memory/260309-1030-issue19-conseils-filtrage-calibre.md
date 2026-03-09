# Issue #19 — Conseils : filtrage livres non lus + renommage

Date : 2026-03-09
Branche : `19-conseils-si-linfo-livres-luspas-lu-est-dispo-nafficher-que-les-livres-pas-lus-dans-les-conseils`

## Comportement

- Titre de l'écran renommé "Recommandations" → **"Conseils"**
- La liste des conseils n'affiche que les livres **non encore lus** :
  - Livres absents de Calibre (`calibre_in_library = 0`) → affichés
  - Livres dans Calibre mais non lus (`calibre_in_library = 1, calibre_lu = 0`) → affichés
  - Livres dans Calibre **et déjà lus** (`calibre_in_library = 1, calibre_lu = 1`) → **masqués**

## Architecture

Le champ `calibre_lu` n'existe PAS dans la table `recommendations` — il est uniquement dans `palmares`.
Solution : LEFT JOIN `recommendations ⟕ palmares` sur `livre_id`.

## Fichiers modifiés

### `app/src/main/java/com/lmelp/mobile/data/model/Entities.kt`

Nouvelle data class `RecommendationAvecCalibreEntity` (pas une `@Entity` Room, juste un POJO résultat de requête) :
```kotlin
data class RecommendationAvecCalibreEntity(
    val rank: Int,
    @ColumnInfo(name = "livre_id") val livreId: String,
    val titre: String,
    @ColumnInfo(name = "auteur_nom") val auteurNom: String?,
    @ColumnInfo(name = "score_hybride") val scoreHybride: Double,
    @ColumnInfo(name = "svd_predict") val svdPredict: Double?,
    @ColumnInfo(name = "masque_mean") val masqueMean: Double?,
    @ColumnInfo(name = "masque_count") val masqueCount: Int?,
    @ColumnInfo(name = "calibre_in_library") val calibreInLibrary: Int = 0,
    @ColumnInfo(name = "calibre_lu") val calibreLu: Int = 0
)
```

### `app/src/main/java/com/lmelp/mobile/data/db/RecommendationsDao.kt`

Nouvelle méthode `getRecommandationsNonLues()` :
```sql
SELECT r.*,
       COALESCE(p.calibre_in_library, 0) AS calibre_in_library,
       COALESCE(p.calibre_lu, 0) AS calibre_lu
FROM recommendations r
LEFT JOIN palmares p ON r.livre_id = p.livre_id
WHERE COALESCE(p.calibre_in_library, 0) = 0
   OR COALESCE(p.calibre_lu, 0) = 0
ORDER BY r.rank ASC
```

### `app/src/main/java/com/lmelp/mobile/data/repository/RecommendationsRepository.kt`

`getAllRecommendations()` appelle désormais `getRecommandationsNonLues()` (pas `getAllRecommendations()` du DAO).

### `app/src/main/java/com/lmelp/mobile/ui/recommendations/RecommendationsScreen.kt`

- Titre TopAppBar : `"Recommandations"` → `"Conseils"`
- EmptyState : `"Aucune recommandation"` → `"Aucun conseil disponible"`

## Tests

`app/src/test/java/com/lmelp/mobile/RecommendationsFilterTest.kt` — 6 tests unitaires purs (JUnit, pas Android) :
- livre absent de Calibre → affiché
- livre dans Calibre non lu → affiché
- livre dans Calibre et lu → masqué
- filtre sur liste mixte (3/4 affichés)
- aucun livre Calibre → liste inchangée
- tous lus → liste vide

## Points clés

- `RecommendationAvecCalibreEntity` ne porte PAS `@Entity` → Room l'utilise uniquement comme POJO résultat
- `COALESCE(..., 0)` nécessaire car LEFT JOIN peut retourner NULL pour les colonnes palmares
- La table `recommendations` ne contient PAS `calibre_lu` — la jointure avec `palmares` est la seule option sans modifier le schéma SQLite
