# Issue #68 — Palmarès : ajouter Mon palmarès

## Contexte

Ajout d'une bascule dans l'écran Palmarès entre deux modes :
- **Palmarès critiques** : classement par note moyenne des critiques (existant)
- **Mon palmarès** : livres lus personnellement, triés par note perso ou date de lecture

## Extraction des dates de lecture depuis Calibre

### Découverte des custom columns Calibre

Calibre possède 3 custom columns :
- `custom_column_1` : `read` (booléen — lu ou pas)
- `custom_column_2` : `paper` (booléen — version papier possédée)
- `custom_column_3` : `text` / Commentaire — contient les dates de lecture au format `DD/MM/YYYY`

Le champ Commentaire peut contenir du texte libre + une date, ex : `"Xavier, noel 2026"` ou `"01/08/2025"`.

### Regex d'extraction

```python
_DATE_LECTURE_RE = re.compile(r"\b(\d{2})/(\d{2})/(\d{4})\b")

def extract_date_lecture(commentaire: str | None) -> str | None:
    if not commentaire:
        return None
    m = _DATE_LECTURE_RE.search(str(commentaire))
    if m:
        day, month, year = m.group(1), m.group(2), m.group(3)
        return f"{year}-{month}-{day}"
    return None
```

Convertit `DD/MM/YYYY` → `YYYY-MM-DD` (format ISO pour tri correct).

### Localisation dans la DB Calibre

Dans `custom_column_3`, la valeur est dans la table `custom_column_3` avec JOIN sur `books`. Le script utilise `label='text'` pour trouver `comment_col_id`, puis extrait via :

```sql
SELECT bc.value FROM custom_column_{id} bc
JOIN books b ON bc.book = b.id
WHERE b.id = ?
```

## Modification du schéma SQLite

### Nouvelle colonne `date_lecture TEXT` dans `palmares`

Ajoutée dans `scripts/export_mongo_to_sqlite.py` dans le CREATE TABLE palmares.

**Bump de version** : 4 → 5
- `PRAGMA user_version = 5` dans le script export
- `version = 5` dans `app/src/main/java/com/lmelp/mobile/data/db/LmelpDatabase.kt`

⚠️ Après bump de version, il faut désinstaller + réinstaller l'app (Room détecte le schema change via `fallbackToDestructiveMigration()`).

## Fichiers modifiés

### Script Python
- `scripts/export_mongo_to_sqlite.py` :
  - Ajout `import re` et `_DATE_LECTURE_RE`
  - Nouvelle fonction `extract_date_lecture()`
  - `palmares` table : colonne `date_lecture TEXT`
  - `import_calibre_data()` : trouve `comment_col_id` via `label='text'`, extrait date, l'ajoute au UPDATE palmares
  - `PRAGMA user_version = 5`
- `tests/test_date_lecture_extraction.py` : 8 tests TDD (tous GREEN)

### Android — Données

- `app/src/main/java/com/lmelp/mobile/data/model/Entities.kt` :
  - `PalmaresEntity` : ajout `@ColumnInfo(name = "date_lecture") val dateLecture: String? = null`

- `app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt` :
  - `PalmaresUi` : ajout `val dateLecture: String? = null`

- `app/src/main/java/com/lmelp/mobile/data/db/PalmaresDao.kt` :
  - `PalmaresFiltreAvecUrlRow` : ajout `date_lecture`
  - Nouvelle data class `MonPalmaresRow` (livreId, titre, auteurNom, noteMoyenne, nbAvis, nbCritiques, calibreRating, urlCover, dateLecture)
  - `getPalmaresFiltresAvecUrl()` : ajout `p.date_lecture` dans SELECT
  - `getMonPalmares()` : WHERE calibre_lu=1, ORDER BY calibre_rating DESC (nulls last), titre ASC
  - `getMonPalmaresParDate()` : WHERE calibre_lu=1, ORDER BY date_lecture DESC (nulls last), titre ASC

- `app/src/main/java/com/lmelp/mobile/data/repository/PalmaresRepository.kt` :
  - `getMonPalmares()` et `getMonPalmaresParDate()` methods
  - `PalmaresFiltreAvecUrlRow.toUi()` : ajout `dateLecture`
  - `MonPalmaresRow.toUi()` : rank=0, calibreInLibrary=true, calibreLu=true

### Android — ViewModel

- `app/src/main/java/com/lmelp/mobile/viewmodel/PalmaresViewModel.kt` :
  - `enum class PalmaresMode { CRITIQUES, PERSONNEL }`
  - `enum class MonPalmaresTriMode { NOTE_PERSO, DATE_LECTURE }`
  - `PalmaresUiState` : ajout `palmaresMode`, `monPalmaresTriMode`
  - `setPalmaresMode()`, `setMonPalmaresTriMode()`
  - `loadPalmares()` : routing selon mode

### Android — UI

- `app/src/main/java/com/lmelp/mobile/ui/palmares/PalmaresScreen.kt` :
  - `SingleChoiceSegmentedButtonRow` (Material3) pour bascule Critiques / Mon palmarès
  - Chips conditionnels : `[Non lus] [Lus]` en mode CRITIQUES, `[Note ↓] [Date lecture ↓]` en mode PERSONNEL
  - Nouveau `MonPalmaresCard` : cover + titre + auteur + "Lu le DD/MM/YYYY" + calibreRating/10

## Résultat

- 20 livres du palmarès ont une date de lecture extraite
- Valeurs non-date dans Commentaire (ex: "Xavier, noel 2026") → ignorées (NULL)
- 40 tests Python + 26 tests Kotlin passent

## Points d'attention

- `custom_column_3` contient du texte libre, pas uniquement des dates → regex robuste
- Le format dans Calibre est `DD/MM/YYYY`, stocké en ISO `YYYY-MM-DD` pour tri SQLite correct
- Après toute regénération de `lmelp.db` avec nouveau schéma : désinstaller + réinstaller l'app
