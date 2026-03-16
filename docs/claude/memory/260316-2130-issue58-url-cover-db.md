# Issue #58 — url_cover depuis la DB (supprime le cache JSON Babelio)

## Résumé

Remplacement complet du système de fetch/cache Babelio par un champ `url_cover` stocké directement dans `lmelp.db`. Gain : suppression de ~400 lignes de code complexe (scraping HTML, mutex, cache JSON persistant, throttle réseau).

## Précondition

`url_cover` disponible dans MongoDB (back-office issue #238 mergée). Le champ existait déjà partiellement dans la collection MongoDB via `url_babelio`, mais `url_cover` est une URL directe vers l'image (CDN Babelio ou Amazon).

## Fichiers modifiés

### Python

`scripts/export_mongo_to_sqlite.py` :
- Ajout colonne `url_cover TEXT` dans `CREATE TABLE livres` et `CREATE TABLE onkindle`
- `export_livres()` : ajoute `livre.get("url_cover")` dans le tuple (9 colonnes au lieu de 8)
- `build_onkindle_table()` : idem pour la table onkindle
- `PRAGMA user_version = 4` (était 3)

### Kotlin — Entities

`app/src/main/java/com/lmelp/mobile/data/model/Entities.kt` :
- `LivreEntity` : ajout `@ColumnInfo(name = "url_cover") val urlCover: String?`
- `OnKindleEntity` : ajout `@ColumnInfo(name = "url_cover") val urlCover: String? = null`

### Kotlin — DAOs

`app/src/main/java/com/lmelp/mobile/data/db/EmissionsDao.kt` :
- `TopLivreEmissionRow` : ajout `@ColumnInfo(name = "url_cover") val urlCover: String?`
- Query `getTopLivreParEmission` : ajout `l.url_cover` dans le SELECT

`app/src/main/java/com/lmelp/mobile/data/db/PalmaresDao.kt` :
- `PalmaresAvecUrlRow` : ajout `@ColumnInfo(name = "url_cover") val urlCover: String?`
- Query `getTopPalmaresAvecUrl` : ajout `l.url_cover` dans le SELECT

`app/src/main/java/com/lmelp/mobile/data/db/RecommendationsDao.kt` :
- `RecommendationAvecUrlRow` : ajout `@ColumnInfo(name = "url_cover") val urlCover: String?`
- Queries `getTopRecommendationsAvecUrl` et `getTopRecommandationsNonLuesAvecUrl` : ajout `l.url_cover`

`app/src/main/java/com/lmelp/mobile/data/db/LmelpDatabase.kt` :
- `version = 3` → `version = 4`

### Kotlin — HomeRepository

`app/src/main/java/com/lmelp/mobile/data/repository/HomeRepository.kt` — réécriture majeure :
- Supprimé : tout le système cache JSON (`loadCache`, `persistCache`, `parseCacheJson`, `getCachedCouverture`, `cacheFile`, `memCache`, `fileMutex`)
- Supprimé : tout le système Babelio (`fetchCouvertureBabelio`, `fetchFromBabelio`, `babelioMutex`, `lastBabelioFetchMs`)
- Supprimé : fonctions utilitaires (`clearCouverturesCache`, `pageTitleMatchesLivre`, `extractCouvertureUrl`)
- Supprimé : paramètre `context: Context` du constructeur
- Dans `getEmissionsSlides`, `getPalmaresSlides`, `getConseilsSlides`, `getOnKindleSlides` : `urlCouverture = row.urlCover` directement

### Kotlin — HomeViewModel

`app/src/main/java/com/lmelp/mobile/viewmodel/HomeViewModel.kt` :
- Supprimé : `launchCouvertureLoad()` et tous ses appels (4 boucles `forEachIndexed`)
- Supprimé : les appels `launchCouvertureLoad` dans `startTicker()`
- Conservé : rotation des indices dans le ticker (inchangée)

### Kotlin — LmelpApp

`app/src/main/java/com/lmelp/mobile/LmelpApp.kt` :
- Supprimé : le paramètre `context = this` dans la construction de `HomeRepository`

### Tests

`app/src/test/java/com/lmelp/mobile/HomeRepositoryTest.kt` — réécriture complète :
- Supprimé : tous les tests liés au cache JSON/Babelio (`extractCouvertureUrl`, `clearCouverturesCache`, `pageTitleMatchesLivre`, version cache, fallback assets)
- Ajouté (TDD RED→GREEN) : 5 tests vérifiant que `getXxxSlides()` retourne `urlCouverture = row.urlCover` directement
- `buildRepository()` simplifié : plus de mock Context/Assets/SharedPreferences

`app/src/test/java/com/lmelp/mobile/LivresRepositoryTest.kt` :
- `makeLivreEntity()` : ajout `urlCover = null` (nouveau champ obligatoire)

### Fichiers supprimés

- `app/src/main/assets/couvertures_cache.json` (cache JSON pré-compilé, obsolète)
- `scripts/check_covers.py` (outil de diagnostic Babelio, obsolète)
- `scripts/inspect_cover_cache.sh` (outil d'inspection adb du cache, obsolète)

## DB générée

- 1601/1624 livres ont `url_cover` (~98.6%)
- 24/28 livres onkindle ont `url_cover`
- `user_version = 4`
- Taille : 4.1 MB

## Versioning Room

`version = 3` → `version = 4`. `fallbackToDestructiveMigration()` gère la migration (données readonly).

## Cache images (Coil)

Le cache Coil est dans `getExternalFilesDir()/coil_image_cache` (50 MB max) — configuré dans `LmelpApp.kt`. Ce répertoire **est effacé à la désinstallation** sur Android 11+ (comportement système, pas un bug). Une issue #62 a été créée pour implémenter Android Backup et préserver ce cache.

## Observations de test

- Sur WiFi : ban Babelio possible → seules les URLs Amazon (`m.media-amazon.com`) se téléchargent
- Sur données mobiles : tout fonctionne normalement
- Le prefetch (préchargement de tous les slides au démarrage) n'est plus fait — Coil charge uniquement les images visibles. Issue potentielle à créer si le comportement devient gênant.
