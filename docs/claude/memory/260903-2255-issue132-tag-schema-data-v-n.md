# Issue #132 — Publier/télécharger les données par tag de schéma (data-v{N})

## Contexte

Découvert pendant l'implémentation de l'issue #131 : le mécanisme de mise à jour des données à distance (`DataUpdateRepository.applyUpdate()`, issue #118) télécharge `lmelp.db` depuis la GitHub Release fixe `data-latest`, vérifie son SHA-256, puis remplace le fichier local — sans jamais vérifier la compatibilité de schéma Room (`PRAGMA user_version`). Si l'app tournait sur un ancien schéma pendant que le pipeline avait déjà republié au nouveau schéma sous ce tag unique, le fichier téléchargé remplaçait la base locale sans vérification, et au redémarrage `fallbackToDestructiveMigration()` s'activait **silencieusement** — perte de toutes les données locales, sans message d'erreur.

## Décision de design (proposée par l'utilisateur, retenue après discussion)

Plutôt qu'un garde-fou qui détecterait l'incompatibilité *après* téléchargement (approche initialement envisagée : comparer `PRAGMA user_version` du fichier téléchargé au fichier local, refuser si différent), l'approche retenue élimine le risque **en amont** : publier/télécharger par **tag dérivé du schéma courant**, `data-v{ROOM_VERSION}` (ex. `data-v7`, puis `data-v8`), au lieu du tag fixe unique `data-latest`.

- Le pipeline NAS (`scripts/docker_export_and_publish_release.sh`) publie toujours vers le tag correspondant au `ROOM_VERSION` courant du script d'export — sans historique conservé (un seul tag actif par schéma, pas de rétention `data-v7`/`data-v8` en parallèle).
- Côté app, chaque build télécharge toujours le tag correspondant à son propre `PRAGMA user_version` local.
- Une app restée sur un ancien schéma après que le pipeline soit passé au tag suivant ne trouve simplement plus de release à son tag (404 GitHub) → pas de mise à jour proposée, jusqu'à mise à jour de l'app elle-même. Aucune perte de données silencieuse possible : le fichier téléchargé est *par construction* toujours au même schéma que l'app qui le demande.

## Implémentation

### Lecture de `PRAGMA user_version` sans dépendance Android/JDBC

Nouveau `app/src/main/java/com/lmelp/mobile/data/update/SqliteSchemaVersion.kt` : lit `PRAGMA user_version` directement depuis le **header binaire standard du fichier SQLite**, octets 60-63 (4 bytes big-endian) — vérifié manuellement (`sqlite3 ... "PRAGMA user_version"` vs lecture binaire des mêmes octets, même valeur). Pas de dépendance `android.database.sqlite.SQLiteDatabase` (non testable en JVM pur sans Robolectric, absent du projet) ni driver JDBC (absent). Fonctionne identiquement en prod Android et en tests JVM.

**Piège rencontré en TDD** : le magic header SQLite standard est `"SQLite format 3\0"` — **16 octets se terminant par un byte nul**, pas `"SQLite format 3 "` (espace final) comme on pourrait le deviner. Vérifié avec `python3 -c "open('lmelp.db','rb').read(16)"` → `b'SQLite format 3\x00'`. Une comparaison de chaîne naïve avec un espace en trop décale tout et fait toujours échouer la validation du header, même sur un fichier valide.

### Résolution dynamique du tag côté app

`OkHttpGitHubReleaseApi` (`app/src/main/java/com/lmelp/mobile/data/remote/OkHttpGitHubReleaseApi.kt`) prend maintenant un `localDbFile: File` en constructeur (au lieu d'une URL `data-latest` en dur) et construit `data-v{SqliteSchemaVersion.read(localDbFile)}` à chaque requête. Instancié dans `LmelpApp.kt` avec `getDatabasePath("lmelp.db")` — cohérent avec le pattern déjà utilisé pour `targetDbFile` ailleurs dans le flux `applyUpdate` (`Navigation.kt`).

### Publication dynamique côté pipeline

`export_mongo_to_sqlite.py` : nouvelle option `--print-room-version` (flag Click, pattern cohérent avec `--verify`) qui imprime `ROOM_VERSION` sur stdout et sort sans connexion MongoDB/Calibre — permet de résoudre le tag en pur shell : `ROOM_VERSION=$(python3 .../export_mongo_to_sqlite.py --print-room-version)`.

`scripts/docker_export_and_publish_release.sh` : `RELEASE_TAG` par défaut passe de `data-latest` à `data-v${ROOM_VERSION}`. Le comportement legacy (variable `RELEASE_TAG` explicite pour tester sur un tag jetable) reste inchangé.

### Piège identifié et évité : `schema_version` de `metadata.json`

`RemoteMetadata.schemaVersion` / `generate_data_release_metadata.py:SCHEMA_VERSION` existe déjà côté modèle distant mais **n'a jamais été maintenu** (`SCHEMA_VERSION = 1` fixe depuis sa création) — ce champ décrit le format JSON de `metadata.json`, pas le schéma SQLite Room. Piste explorée puis explicitement écartée : ne pas réutiliser ce champ pour la vérification de compatibilité, source de confusion garantie (deux notions de "version de schéma" complètement différentes portant des noms proches).

## Tests (TDD RED→GREEN)

- `app/src/test/java/com/lmelp/mobile/SqliteSchemaVersionTest.kt` (nouveau) : 5 cas — lecture correcte, deux fichiers avec versions différentes, fichier absent, fichier trop court, magic header invalide.
- `app/src/test/java/com/lmelp/mobile/OkHttpGitHubReleaseApiTest.kt` (existant, réécrit) : construit un fichier DB local factice (mini-header SQLite avec `user_version` connu) et vérifie via `server.takeRequest().path` que l'URL réellement appelée par MockWebServer porte le tag `data-v{N}` attendu.
- `tests/test_export_cli_print_room_version.py` (nouveau, Python) : `--print-room-version` via `click.testing.CliRunner`, vérifie l'exit code et la sortie exacte, sans nécessiter MongoDB.

## Test manuel en conditions réelles (device physique)

Procédure suivie pour valider de bout en bout, avec un bug de manipulation découvert et corrigé en cours de route :

1. Génération d'une base complète locale au schéma v8 (`export_mongo_to_sqlite.py --force`), génération de `metadata.json` (`generate_data_release_metadata.py`).
2. Publication d'une release GitHub `data-v8` via `gh release create` + `gh release upload`.
3. **Bug rencontré** : premier upload avec `gh release upload data-v8 lmelp_complete.db#lmelp.db` — le suffixe `#lmelp.db` après `gh release upload` définit le **label** affiché dans l'UI GitHub, pas le `name` de l'asset (utilisé par `OkHttpGitHubReleaseApi.fetchAssetUrls()` qui recherche `asset.getString("name")`). Résultat observé sur device : "Erreur : lmelp.db absent des assets de la release" — l'asset existait bien mais sous le nom `lmelp_complete.db`. **Correction** : renommer le fichier local en `lmelp.db` avant `gh release upload` (le `name` de l'asset suit le nom de fichier local, pas le label). Piège à réutiliser pour toute future publication manuelle de release avec des assets `gh release upload local_file.ext#Label affiché`.
4. Après correction : build + install de l'app (mini-DB embarquée v8) via `scripts/build.sh`/`scripts/deploy.sh`, déclenchement de "Vérifier les mises à jour" → "Mettre à jour" dans l'écran À propos → succès confirmé par l'utilisateur, résolution correcte du tag `data-v8`, téléchargement, remplacement.
5. Décision : la release `data-v8` de test est conservée (pas supprimée) — elle sera naturellement rafraîchie par le prochain export réel du pipeline NAS une fois cette PR mergée et l'image `ghcr.io/castorfou/lmelp-mobile-export` reconstruite.

## Fichiers modifiés

- `app/src/main/java/com/lmelp/mobile/data/update/SqliteSchemaVersion.kt` (nouveau)
- `app/src/main/java/com/lmelp/mobile/data/remote/OkHttpGitHubReleaseApi.kt`
- `app/src/main/java/com/lmelp/mobile/data/remote/GitHubReleaseApi.kt` (doc)
- `app/src/main/java/com/lmelp/mobile/data/remote/RemoteMetadata.kt` (doc)
- `app/src/main/java/com/lmelp/mobile/data/repository/DataUpdateRepository.kt` (doc)
- `app/src/main/java/com/lmelp/mobile/LmelpApp.kt`
- `app/src/test/java/com/lmelp/mobile/SqliteSchemaVersionTest.kt` (nouveau)
- `app/src/test/java/com/lmelp/mobile/OkHttpGitHubReleaseApiTest.kt`
- `app/src/test/java/com/lmelp/mobile/RemoteMetadataTest.kt` (doc)
- `scripts/export_mongo_to_sqlite.py` (`--print-room-version`)
- `scripts/docker_export_and_publish_release.sh` (`RELEASE_TAG` dynamique)
- `scripts/generate_data_release_metadata.py` (doc)
- `tests/test_export_cli_print_room_version.py` (nouveau)
- `tests/test_generate_data_release_metadata.py` (doc)
- `docs/dev/adr/0001-separation-maj-appli-donnees.md` (nouvelle section)
- `CLAUDE.md`, `docs/architecture.md`, `docs/user/mise_a_jour_episode.md`
