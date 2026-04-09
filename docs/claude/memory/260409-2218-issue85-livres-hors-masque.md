# Issue #85 — Livres lus hors Masque dans Mon Palmarès et page auteur

## Problème résolu

"Mon Palmarès" n'affichait que les livres discutés au Masque et la Plume (table `palmares`).
L'issue demandait d'y ajouter les livres Calibre lus mais **non discutés au Masque**, avec :
- Affichage intégré dans Mon Palmarès (même flux de tri, sans section séparée)
- Chip "Hors Masque" pour filtrer, aligné à droite, préférence persistante
- Section "Lus hors Masque" dans la page auteur si livres correspondants

## Nouvelle table SQLite

```sql
CREATE TABLE calibre_hors_masque (
    id             TEXT PRIMARY KEY,   -- titre normalisé (slug)
    titre          TEXT NOT NULL,
    auteur_nom     TEXT,
    calibre_rating REAL,
    date_lecture   TEXT
)
```

Logique script Python : livres Calibre avec `calibre_lu=1` dont le titre normalisé n'est PAS dans `palmares`.
**193 livres** insérés après le premier export.

## Fichiers modifiés

### Script Python

**`scripts/export_mongo_to_sqlite.py`**
- Nouvelle table `calibre_hors_masque` dans `SCHEMA_SQL`
- Nouvelle fonction `build_calibre_hors_masque_table()` : charge tous les livres Calibre lus, exclut ceux déjà dans `palmares` (titre normalisé), dédoublonne par titre normalisé
- Appel dans le flux principal après `import_calibre_data()`
- `calibre_hors_masque` ajouté dans `verify_database()`
- `PRAGMA user_version = 6` (était 5)

### Couche données Android

**`app/src/main/java/com/lmelp/mobile/data/model/Entities.kt`**
- Nouvelle `CalibreHorsMasqueEntity` (`@Entity(tableName = "calibre_hors_masque")`)

**`app/src/main/java/com/lmelp/mobile/data/db/LmelpDatabase.kt`**
- `CalibreHorsMasqueEntity::class` ajouté dans `@Database(entities = [...])`
- `version = 6` (était 5)
- Nouvelle méthode abstraite `calibreHorsMasqueDao()`

**`app/src/main/java/com/lmelp/mobile/data/db/CalibreHorsMasqueDao.kt`** (nouveau)
- `getAll()` : tri note décroissante, sans note en fin
- `getAllParDate()` : tri date décroissante, sans date en fin
- `getByAuteurNom(auteurNom)` : filtre par auteur

**`app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt`**
- Nouveau `CalibreHorsMasqueUi` (id, titre, auteurNom, calibreRating, dateLecture)
- Nouveau `MonPalmaresItemUi` : wrapper unifié Masque + hors Masque — `livreId == null` signifie "hors Masque, non cliquable"
- `AuteurDetailUi` enrichi : `livresHorsMasque: List<CalibreHorsMasqueUi> = emptyList()`

**`app/src/main/java/com/lmelp/mobile/data/repository/PalmaresRepository.kt`**
- Constructeur enrichi : `horsMasqueDao: CalibreHorsMasqueDao? = null`
- `getMonPalmaresHorsMasque()` / `getMonPalmaresHorsMasqueParDate()` : livres hors Masque seuls
- `getHorsMasqueByAuteurNom()` → `List<CalibreHorsMasqueUi>`
- `getMonPalmaresUnifieParNote()` / `getMonPalmaresUnifieParDate()` : fusion + tri Kotlin côté repo

**`app/src/main/java/com/lmelp/mobile/data/repository/AuteursRepository.kt`**
- Constructeur enrichi : `horsMasqueDao: CalibreHorsMasqueDao? = null`
- `getAuteurDetail()` appelle `horsMasqueDao.getByAuteurNom(auteur.nom)` et alimente `livresHorsMasque`

**`app/src/main/java/com/lmelp/mobile/data/repository/UserPreferencesRepository.kt`** (nouveau)
- DataStore Preferences (`user_prefs`)
- Clé `show_hors_masque: Boolean` (défaut `true`)
- `showHorsMasque: Flow<Boolean>` + `setShowHorsMasque()`

### Couche ViewModel

**`app/src/main/java/com/lmelp/mobile/viewmodel/PalmaresViewModel.kt`**
- `PalmaresUiState` enrichi : `monPalmares: List<MonPalmaresItemUi>`, `showHorsMasque: Boolean = true`
- Mode PERSONNEL utilise désormais `monPalmares` (pas `palmares`)
- `init` lit la préférence DataStore avant le premier chargement
- `setShowHorsMasque()` : persiste dans DataStore + recharge
- Filtrage côté ViewModel : si `!showHorsMasque`, filtre `monPalmares` pour ne garder que `livreId != null`

### Couche UI

**`app/src/main/java/com/lmelp/mobile/ui/palmares/PalmaresScreen.kt`**
- `PalmaresScreen` accepte `userPrefsRepository: UserPreferencesRepository? = null`
- `PalmaresContent` : nouveau param `onToggleHorsMasque`
- Chip "Hors Masque" dans la rangée des filtres PERSONNEL, poussé à droite avec `Spacer(Modifier.weight(1f))`
- `MonPalmaresCard` refactorisé pour `MonPalmaresItemUi` : si `livreId == null` → pas de `clickable`, pas de `BookCoverThumbnail`

**`app/src/main/java/com/lmelp/mobile/ui/auteurs/AuteurDetailScreen.kt`**
- Si `auteur.livresHorsMasque.isNotEmpty()` : `HorizontalDivider` + label "Lus hors Masque"
- Nouveau composable `HorsMasqueCard` : titre + date + note, non cliquable

### Infrastructure

**`app/src/main/java/com/lmelp/mobile/LmelpApp.kt`**
- `palmaresRepository` : passe `database.calibreHorsMasqueDao()`
- `auteursRepository` : passe `database.calibreHorsMasqueDao()`
- `userPreferencesRepository` (nouveau lazy)

**`app/src/main/java/com/lmelp/mobile/Navigation.kt`**
- Passe `userPrefsRepository = app.userPreferencesRepository` à `PalmaresScreen`

**`gradle/libs.versions.toml`** + **`app/build.gradle.kts`**
- Ajout `androidx.datastore.preferences:1.1.1`

### Tests

**`app/src/test/java/com/lmelp/mobile/CalibreHorsMasqueRepositoryTest.kt`** (nouveau)
- `getMonPalmaresHorsMasque()` → `livreId == null`, champs mappés correctement
- `getMonPalmaresHorsMasqueParDate()` → ordre préservé
- `getHorsMasqueByAuteurNom()` → filtre correct + liste vide si pas de match

## Points clés architecture

- **Tri Kotlin côté repo** : la fusion Masque + hors Masque se fait dans `PalmaresRepository` (pas côté DAO ni UI) via `sortedWith(compareBy(...))` — gère proprement `null` pour note et date
- **`MonPalmaresItemUi.livreId == null`** : convention pour "hors Masque, non cliquable" — un seul composable card adaptatif au lieu de deux
- **DataStore** (pas Room) pour la préférence `show_hors_masque` : persistance légère, asynchrone, lue dans `init` via `first()`
- **Chip "Hors Masque" aligné à droite** via `Spacer(Modifier.weight(1f))` pour signifier visuellement qu'il s'applique aux deux modes de tri
- **`horsMasqueDao` optionnel** (`= null`) dans les repositories : les tests existants ne cassent pas

## Version Room

Room version 6 (était 5). Après regénération `lmelp.db` : `adb uninstall com.lmelp.mobile && ./gradlew installDebug` obligatoire.
