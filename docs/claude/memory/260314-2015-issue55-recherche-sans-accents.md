# Issue #55 — Recherche insensible aux accents

## Contexte

Rechercher "aliene" ne retournait rien car la table FTS4 était indexée avec le
contenu original (accents inclus) et SQLite FTS4 sans tokenizer spécial est
sensible aux accents.

## Solution retenue : FTS4 + normalisation côté export + normalisation côté query

### Pourquoi pas FTS5 ?

Première tentative : migrer vers FTS5 avec `tokenize = 'unicode61 remove_diacritics 2'`.
FTS5 fonctionne parfaitement en Python/desktop, **mais Android embarque un SQLite
sans le module FTS5 compilé** → erreur `no such module: fts5` sur le device.

La lib `androidx.sqlite:sqlite-bundled` (qui inclut FTS5) n'existe qu'à partir
de la version 2.5.0+, et `setDriver(BundledSQLiteDriver())` n'est disponible
que sur Room 2.7+. Notre stack (Room 2.6.1) ne le supporte pas.

### Solution finale : FTS4 + double colonne

**`scripts/export_mongo_to_sqlite.py`** :
- `_strip_accents(text)` : NFD → supprime les caractères de catégorie `Mn`
  (diacritiques), conserve la casse et la ponctuation
- `search_index` DDL : 4 colonnes — `type`, `ref_id`, `content` (normalisé, indexé),
  `display_content` (original avec accents, `notindexed`)
- `build_search_index()` : insère les deux colonnes pour chaque entrée

**`app/src/main/java/com/lmelp/mobile/data/repository/SearchRepository.kt`** :
- `stripAccents(text)` : `java.text.Normalizer.Form.NFD` + suppression `\p{InCombiningDiacriticalMarks}+`
- Query normalisée avant le MATCH FTS4 → "Aliène" → "Aliene*" dans le MATCH
- `SELECT ... display_content AS displayContent ...` → affiché à l'UI

**`app/src/main/java/com/lmelp/mobile/data/db/SearchDao.kt`** :
- `SearchRow` : ajout du champ `displayContent`

**`tests/test_fts5_accent_search.py`** (nouveau) :
- Vérifie que le contenu de `search_index` est sans accents (100 premières lignes)
- Vérifie que `strip_accents("Aliene*")` retourne des résultats dans la DB de prod
- Tests unitaires FTS4 normalisé en mémoire

## Piège rencontré : FTS5 absent sur Android

`no such module: fts5` — Android compile SQLite sans FTS5 par défaut.
`sqlite-bundled` requis mais incompatible avec Room 2.6.1 (`setDriver` absent).
Fix : rester sur FTS4 et normaliser à l'export.

## Commits de la branche

- `6b92455` — tentative FTS5 (migrée puis annulée)
- `90610be` — solution finale FTS4 + normalisation double colonne
- `6568d57` — ajout sqlite-bundled dans libs.versions.toml (lib présente mais inutilisée)

## Résultat

"aliene" → trouve "Aliène" et "Un village pour aliénés tranquilles"
"helene" → trouve "Hélène Carrère" etc.
Les accents sont correctement affichés dans les résultats (via `display_content`).
