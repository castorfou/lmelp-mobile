# Issue #145 — Vignettes de couverture Calibre (livres hors Masque et liseuse)

## Besoin

Suite de #143 (grandes couvertures dans les listes d'œuvres). Les livres qui ne
viennent que de Calibre n'ont pas d'`url_cover` Babelio :
- section « Lus hors Masque » de la page auteur (`HorsMasqueCard`, ex. Proust,
  *Du côté de chez Swann*), restée à l'ancienne carte sans image ;
- livres hors Masque dans Mon Palmarès (`MonPalmaresCard`, bloc neutre) ;
- ajouté en cours de route à la demande de l'utilisateur : livres de la page
  **Ma liseuse** absents de la base Masque (ex. *Team Topologies*, id synthétique
  `calibre_1768`).

Calibre a un `cover.jpg` pour 997 livres sur 1000 ; l'ISBN n'est présent que
pour un tiers, d'où le choix d'embarquer la couverture Calibre plutôt qu'une
résolution par ISBN.

## Décisions validées avec l'utilisateur

- Vignette de **225 px de large**, JPEG qualité 75, `optimize=True`, sans jamais
  agrandir. Choix « même taille que les livres du Masque » : les couvertures
  Babelio mesurées font entre 194 et 250 px de large. Mesuré sur la vraie
  bibliothèque : environ 12 à 14 Ko par livre, 2,5 Mo pour les 184 livres hors
  Masque. Base complète : 7,5 Mo.
- Stockage en **BLOB** dans la table SQLite (pas de fichier à part) : colonne
  `cover` dans `calibre_hors_masque` **et** dans `onkindle`.
- Dans `onkindle`, la vignette n'est calculée **que si `url_cover` est NULL**,
  pour ne pas doublonner une couverture Babelio (2 livres sur 13 aujourd'hui).
- Le carrousel « liseuse » de l'accueil n'est pas concerné : il filtre
  `url_babelio IS NOT NULL` (`OnKindleDao.getTopOnKindleAvecUrl`).
- Container d'export du NAS (`castorfou/docker-lmelp`) : l'issue prévue n'a
  finalement pas été ouverte. Son `docker-compose.yml` monte déjà un **dossier**
  (`${CALIBRE_HOST_PATH}:/calibre:ro`, `LMELP_CALIBRE_DB=/calibre/metadata.db`),
  donc les `cover.jpg` devraient être visibles. À confirmer au premier export
  NAS : chercher le `WARNING` « Aucune cover.jpg trouvée ».

## Implémentation

### Export Python — `scripts/export_mongo_to_sqlite.py`

- `ROOM_VERSION = 9` (commentaire `v9 : ... (issue #145)`).
- `COVER_THUMBNAIL_WIDTH = 225` et `make_cover_thumbnail(cover_path) -> bytes | None`
  (Pillow) : `None` si le fichier est absent ou illisible (`OSError`/`ValueError`,
  `UnidentifiedImageError` étant une `OSError`).
- `_calibre_book_cover(calibre_library, book_path)` : renvoie `None` si
  `books.path` est vide, sinon la vignette de
  `<dossier de metadata.db>/<books.path>/cover.jpg`. La bibliothèque Calibre est
  déduite de `Path(calibre_db_path).parent` : pas de nouvelle variable d'env.
- `build_calibre_hors_masque_table` et `build_onkindle_table` lisent `b.path`
  et insèrent `cover`.
- Log `→ N livres hors Masque insérés (M avec couverture)` + `WARNING` si
  aucune couverture n'est trouvée : c'est le symptôme d'un container qui ne
  monte que `metadata.db`. L'export ne plante pas, la colonne reste NULL.
- `pyproject.toml` : `pillow>=10` en dépendance explicite (auparavant seulement
  transitive via matplotlib). `Dockerfile.export` installe depuis
  `pyproject.toml`, donc rien à changer côté image.
- `compute_content_hash` inclut déjà ces tables (via `repr(bytes)`) ; la sortie
  de Pillow est déterministe à entrée égale (vérifié), donc pas de fausse
  « mise à jour disponible » (issue #128).

### App Kotlin

- `data/model/Entities.kt` : `cover: ByteArray?` avec
  `@ColumnInfo(name = "cover", typeAffinity = ColumnInfo.BLOB)` dans
  `CalibreHorsMasqueEntity` et `OnKindleEntity`. `LmelpDatabase` en `version = 9`.
- `data/db/OnKindleDao.kt` : `OnKindleAvecConseilRow.cover` + `ok.cover` dans le
  `SELECT`.
- UI models (`data/model/UiModels.kt`) : `CalibreHorsMasqueUi.cover`,
  `MonPalmaresItemUi.coverData`, `OnKindleUi.coverData`. Mappers dans
  `PalmaresRepository`, `AuteursRepository`, `OnKindleRepository`.
- `BookListCard` (`ui/components/CommonComponents.kt`) : nouveau paramètre
  `coverData: ByteArray? = null`, modèle Coil = `urlCover ?: coverData`.
  **Coil 3 accepte `ByteArray` nativement**, aucun fetcher à ajouter.
- `HorsMasqueCard` (`ui/auteurs/AuteurDetailScreen.kt`) passe sur `BookListCard`
  (titre 2 lignes max). `MonPalmaresCard` et `OnKindleCard` passent
  `coverData`.
- Lint : pas d'alerte `ArrayInDataClass` sur les data classes contenant un
  `ByteArray` (0 erreur).

## Tests

- `tests/test_calibre_hors_masque_cover.py` : fausse bibliothèque Calibre
  **sur disque** dans `tmp_path` (`metadata.db` + dossiers de livres avec un
  vrai JPEG généré par Pillow), sans mock de `sqlite3` :
  - vignette 600×900 → 225×338, magic JPEG `FFD8` ;
  - pas d'agrandissement ;
  - fichier absent ou illisible → `None` ;
  - `cover.jpg` absent → ligne insérée avec `cover` NULL ;
  - `onkindle` : vignette pour un livre hors Masque, rien si `url_cover` Babelio ;
  - schéma (`SCHEMA_SQL`) et `ROOM_VERSION == 9`.
- `tests/test_build_onkindle_table.py` : fixtures alignées sur le vrai schéma
  (`books.path`, `onkindle.cover`). ⚠️ Le `try/except Exception` global de
  `build_onkindle_table` **avale** les erreurs : une `TypeError` (`Path / None`)
  se traduisait seulement par une table vide et des assertions `None` en
  échec, sans message clair.
- Kotlin : `CalibreHorsMasqueRepositoryTest`, `AuteursRepositoryTest`,
  nouveau `OnKindleCoverTest` (propagation de `cover` → `coverData`, avec
  `assertArrayEquals`).

## Migration de schéma v8 → v9 (procédure `docs/dev/schema-migration-workflow.md`)

1. Export complet vers `/tmp/.../lmelp.db` (fichier nommé exactement `lmelp.db`).
2. `build_mini_db.py` → `app/src/main/assets/lmelp.db` (`PRAGMA user_version` = 9 ;
   `calibre_hors_masque` y reste vide).
3. Release `data-v9` publiée manuellement (`gh release create`), puis ses assets
   remplacés par `gh release upload --clobber` après l'ajout de `onkindle.cover`.
4. ⚠️ Modifier une `@Entity` **sans** changer de version (v9 pas encore
   sortie) change le hash d'identité Room : il faut **désinstaller** l'app avant
   de réinstaller, sinon « Room cannot verify the data integrity ».

Validé sur device par l'utilisateur : page auteur (Proust), Mon Palmarès et
Ma liseuse (*Team Topologies*).
