# Issue #52 — Page "Sur ma liseuse" (OnKindle)

## Résumé

Création d'une page dédiée listant les livres tagués `onkindle` dans Calibre (liseuse Kindle),
avec filtres Lus/Non lus, tri alphabétique ou par note, et notes du Masque et la Plume.
La page est accessible depuis la Home (tuile "Liseuse" remplaçant "Critiques"), pas depuis la NavBar.

---

## Fichiers créés

- `app/src/main/java/com/lmelp/mobile/data/db/OnKindleDao.kt` — DAO Room avec `getOnKindleFiltres` + `getTopOnKindleAvecUrl`
- `app/src/main/java/com/lmelp/mobile/data/repository/OnKindleRepository.kt` — tri Kotlin via `java.text.Collator(Locale.FRENCH, PRIMARY)` pour accents
- `app/src/main/java/com/lmelp/mobile/viewmodel/OnKindleViewModel.kt` — `OnKindleUiState` avec `afficherLus`, `afficherNonLus`, `triParNote`
- `app/src/main/java/com/lmelp/mobile/ui/onkindle/OnKindleScreen.kt` — 4 FilterChips + badge rouge compteur + `OnKindleCard`
- `app/src/test/java/com/lmelp/mobile/OnKindleViewModelTest.kt` — 7 tests ViewModel
- `tests/test_build_onkindle_table.py` — 7 tests Python (fallback avis + filtre virtual library)
- `tests/test_normalize_title.py` — 5 tests pour `_normalize_title`
- `scripts/.env.example` — template de configuration locale

## Fichiers modifiés

- `app/src/main/java/com/lmelp/mobile/data/model/Entities.kt` — ajout `OnKindleEntity`
- `app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt` — ajout `OnKindleUi`
- `app/src/main/java/com/lmelp/mobile/data/db/LmelpDatabase.kt` — version 2→3, ajout `OnKindleEntity` + `onKindleDao()`
- `app/src/main/java/com/lmelp/mobile/data/repository/HomeRepository.kt` — ajout `getOnKindleSlides()`
- `app/src/main/java/com/lmelp/mobile/viewmodel/HomeViewModel.kt` — ajout `onkindleSlides` + ticker
- `app/src/main/java/com/lmelp/mobile/ui/home/HomeScreen.kt` — tuile "Liseuse" remplace "Critiques"
- `app/src/main/java/com/lmelp/mobile/Navigation.kt` — route `onkindle`
- `app/src/main/java/com/lmelp/mobile/LmelpApp.kt` — `onKindleRepository`
- `scripts/export_mongo_to_sqlite.py` — `build_onkindle_table()` + filtre virtual library + `.env`

---

## Points techniques clés

### Tri alphabétique avec accents
Le tri SQLite `ORDER BY lower(titre)` ne gère pas les accents (À prendre → fin de liste).
**Solution** : tri en Kotlin via `java.text.Collator.getInstance(Locale.FRENCH)` avec `strength = Collator.PRIMARY`.
Voir `OnKindleRepository.kt`.

### Livres non dans palmares mais avec avis
Des livres "coups de cœur" (nb_avis < 2) n'apparaissaient pas dans `palmares` mais ont des `avis`.
**Solution** : dans `build_onkindle_table()`, construire un `avis_index` (livre_id → moyenne+count)
comme fallback quand le livre n'est pas dans `palmares_index`.

### Normalisation de titres — apostrophe typographique
`_normalize_title()` ne gérait pas `'` (U+2019, apostrophe typographique de Calibre).
**Solution** : remplacer U+2018/U+2019 par `'` avant normalisation NFKD.
Impacte "Frapper l'épopée", "Les guerriers de l'hiver", "L'Éternité n'est pas de trop".

### Auteur depuis Calibre
Les livres non dans `livres` (non discutés au Masque) n'avaient pas d'auteur.
**Solution** : dans la requête Calibre, `LEFT JOIN books_authors_link + authors`, puis fallback
`if auteur_nom is None and calibre_auteur: auteur_nom = calibre_auteur`.

### Filtre virtual library Calibre
`build_onkindle_table` accepte maintenant `virtual_library_tag` (ex: `guillaume`).
Filtre SQL : `JOIN books_tags_link btl_ok ... AND t_ok.name = 'onkindle'` + second JOIN sur le tag vlib.
Résultat : 28 livres (guillaume) au lieu de 30 (tous utilisateurs).

### Configuration via .env
Paramètres lus depuis `scripts/.env` via `python-dotenv` + `envvar=` dans les options Click :
- `LMELP_MONGO_URI`, `LMELP_OUTPUT`, `LMELP_CALIBRE_DB`, `LMELP_CALIBRE_VIRTUAL_LIBRARY`
- Commande simplifiée : `python scripts/export_mongo_to_sqlite.py --force`

### Room version 2→3
Ajout de `OnKindleEntity` → incrément obligatoire dans `LmelpDatabase.kt` (version=3)
ET dans le script Python (`PRAGMA user_version = 3`). Sinon : crash "Room cannot verify data integrity".

### Badge compteur
`Box` rouge avec `uiState.livres.size` à droite des FilterChips, mis à jour en temps réel.

---

## Pièges rencontrés

- **Page vide au premier test** : DB non régénérée après ajout de la table `onkindle` → désinstaller/réinstaller l'app
- **`adb uninstall` obligatoire** à chaque changement de version Room (fallbackToDestructiveMigration ne suffit pas si la DB assets est plus récente)
- **`_make_calibre_db` dans les tests** : doit inclure `books_authors_link` et `authors` (ajout lors du fix auteur Calibre)
- **Hook pre-commit** : fichiers non stagés (ex: `ui/sketchs/#53.excalidraw`) bloquent le commit si le hook les modifie → les stager aussi

---

## Tests

- 18 tests Python : `test_normalize_title.py` (5) + `test_build_onkindle_table.py` (7 avis fallback + 3 virtual library + anciens = 7+3+5=18 total en comptant ceux de `test_lmelp_db_integrity`)
- 7 tests Kotlin ViewModel : `OnKindleViewModelTest.kt`
- Build Android : OK
