# Issue #84 — Badge Calibre (lu + note) sur fiche livre, page auteur, page émission

## Problème résolu

Les données Calibre (`calibre_lu`, `calibre_in_library`, `calibre_rating`) existaient dans la table `palmares` mais ne remontaient pas dans les écrans fiche livre, page auteur et page émission. Seul le palmarès les affichait.

## Fichiers modifiés

### Couche données (DAOs)

**`app/src/main/java/com/lmelp/mobile/data/db/LivresDao.kt`**
- Nouvelle data class `LivreAvecCalibreRow` : contient tous les champs de `LivreEntity` + `calibre_in_library`, `calibre_lu`, `calibre_rating` (LEFT JOIN palmares)
- Nouvelle méthode `getLivreAvecCalibreById(id)` → remplace `getLivreById` dans `LivresRepository`
- Nouvelle méthode `getLivresAvecCalibreByEmission(emissionId)` → remplace `getLivresByEmission` dans `EmissionsRepository`

**`app/src/main/java/com/lmelp/mobile/data/db/AuteursDao.kt`**
- `LivreParAuteurRow` enrichi : ajout de `calibreInLibrary`, `calibreLu`, `calibreRating` (défaut 0/null)
- `getLivresParAuteur` : requête SQL enrichie avec `COALESCE(p.calibre_in_library, 0)`, `COALESCE(p.calibre_lu, 0)`, `p.calibre_rating`

### Couche données (modèles UI)

**`app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt`**
- `LivreDetailUi` : ajout `calibreInLibrary: Boolean = false`, `calibreLu: Boolean = false`, `calibreRating: Double? = null`
- `LivreUi` : mêmes ajouts
- `LivreParAuteurUi` : mêmes ajouts

### Couche données (repositories)

**`app/src/main/java/com/lmelp/mobile/data/repository/LivresRepository.kt`**
- Utilise `getLivreAvecCalibreById` au lieu de `getLivreById`
- Mappe `calibreInLibrary = livre.calibreInLibrary == 1`, `calibreLu = livre.calibreLu == 1`, `calibreRating = livre.calibreRating`

**`app/src/main/java/com/lmelp/mobile/data/repository/AuteursRepository.kt`**
- Mappe les 3 champs calibre de `LivreParAuteurRow` vers `LivreParAuteurUi`

**`app/src/main/java/com/lmelp/mobile/data/repository/EmissionsRepository.kt`**
- Utilise `getLivresAvecCalibreByEmission` au lieu de `getLivresByEmission`
- Mappe les 3 champs calibre vers `LivreUi`

### Couche UI

**`app/src/main/java/com/lmelp/mobile/ui/components/CommonComponents.kt`**
- Nouveau composable réutilisable `CalibreBadge(calibreInLibrary, calibreLu, calibreRating, modifier)`
- N'affiche rien si `!calibreInLibrary`
- ✓ vert (#2E7D32) si lu, ◯ gris sinon, et `X/10` en dessous si noté

**`app/src/main/java/com/lmelp/mobile/ui/emissions/LivreDetailScreen.kt`**
- `CalibreBadge` ajouté dans l'en-tête (colonne à droite, sous la `NoteBadge` note critique)

**`app/src/main/java/com/lmelp/mobile/ui/auteurs/AuteurDetailScreen.kt`**
- `CalibreBadge` ajouté dans `LivreParAuteurCard` (colonne à droite, sous `NoteBadge`)

**`app/src/main/java/com/lmelp/mobile/ui/emissions/EmissionDetailScreen.kt`**
- `CalibreBadge` ajouté dans `LivreCard` (colonne à droite, sous `NoteBadge`)

### Tests

**`app/src/test/java/com/lmelp/mobile/CalibreBadgeRepositoryTest.kt`** (nouveau)
- `LivresRepository` : calibreLu/calibreInLibrary/calibreRating correctement mappés (3 cas : lu, non lu, dans bibliothèque mais non lu)
- `AuteursRepository` : propagation calibre dans `LivreParAuteurUi`

**`app/src/test/java/com/lmelp/mobile/LivresRepositoryTest.kt`** (mis à jour)
- Remplace `getLivreById` par `getLivreAvecCalibreById` dans les mocks

**`app/src/test/java/com/lmelp/mobile/UrlCoverPropagationTest.kt`** (mis à jour)
- Remplace `getLivresByEmission` par `getLivresAvecCalibreByEmission` dans les mocks

## Pattern de conversion Int → Boolean

Les champs SQLite `calibre_in_library` et `calibre_lu` sont des INTEGER (0/1). La conversion se fait dans les repositories :
```
calibreInLibrary = row.calibreInLibrary == 1
calibreLu = row.calibreLu == 1
```
Les data classes Room utilisent `Int` avec `defaultValue = "0"`.

## Points clés architecture

- La table `palmares` est la source des données Calibre (pas `livres`)
- Tous les JOINs sont `LEFT JOIN palmares` → un livre sans entrée palmares a calibre=false/null
- `CalibreBadge` est le seul composable UI à créer — réutilisé sur 3 écrans
- Le palmarès avait déjà son propre code inline (non refactorisé, pour ne pas toucher du code stable)
