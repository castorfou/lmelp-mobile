# Issue #131 — Auto-épinglage "en cours de lecture" basé sur les données Calibre/KOReader

## Contexte

L'écran **Sur ma liseuse** permettait déjà d'épingler manuellement un livre "en cours de lecture" (issue #75, `docs/claude/memory/260412-1041-issue75-epingler-livres-en-cours-lecture.md`). L'issue #131 demandait d'**automatiser** cet épinglage : un livre peut avoir `Read = false` dans Calibre (case à cocher standard, non mise à jour tant que la lecture n'est pas terminée) tout en ayant une **progression de lecture KOReader en cours** — observé sur le screenshot de l'issue et confirmé en base réelle avec le livre "L'Inconnue du quai de Javel" (`Read=false`, `ko_progfloat=0.5062`).

## Décisions de design (validées avec l'utilisateur)

- **Détection** : colonne custom Calibre `ko_progfloat` (float, progression KOReader précise) — `en_cours_lecture = 1` ssi `0 < ko_progfloat < 1` (exclut jamais-ouvert `None`/`0.0` et terminé `1.0`). La colonne `ko_status` ("KOReader Book Status") a été écartée : sur les données réelles, elle vaut `'complete'` pour TOUS les livres onkindle, même ceux clairement en cours de lecture (`ko_progfloat=0.5`) — colonne non fiable, mal alimentée par le plugin KOReader.
- **Contrôle manuel prioritaire** : l'auto-épinglage est une proposition par défaut, pas un état figé. L'utilisateur garde la main (appui long / tap punaise) comme pour l'épinglage manuel existant.
- **Persistance du retrait** : si l'utilisateur désépingle manuellement un livre auto-épinglé, ce retrait doit rester effectif au-delà d'un simple rechargement d'écran, jusqu'à ce que les données Calibre associées changent (nouvelle regen DB). D'où une nouvelle liste DataStore `auto_pin_dismissed`, distincte de `pinned_reading`.

## Implémentation

### Export Python (`scripts/export_mongo_to_sqlite.py`)

- Nouvelle colonne `onkindle.en_cours_lecture INTEGER NOT NULL DEFAULT 0` dans le DDL (`SCHEMA_SQL`).
- Dans `build_onkindle_table()` : récupération de l'id de la colonne custom `ko_progfloat` (même pattern que `ko_start`/`ko_finish`), calcul `en_cours_lecture = 1 if 0 < value < 1 else 0`.
- `ROOM_VERSION` passé de 7 à 8 (synchronisé avec `LmelpDatabase.kt`, vérifié par `_check_room_version_consistency()` et le hook pre-commit "Room version consistency").

### Android

- `OnKindleEntity` (`app/src/main/java/com/lmelp/mobile/data/model/Entities.kt`) : `enCoursLecture: Int = 0`.
- `LmelpDatabase.kt` : `version = 8`.
- `OnKindleUi` (`UiModels.kt`) : `enCoursLecture: Boolean = false`.
- `OnKindleDao.OnKindleAvecConseilRow` + requête `getOnKindleAvecConseil` : propagation de `en_cours_lecture`.
- `OnKindleRepository.toUi()` : mapping.
- `UserPreferencesRepository.PinnedReadingStorage` : nouveaux membres `autoPinDismissed: Flow<Set<String>>`, `dismissAutoPin(livreId)`, `clearAutoPinDismissed(livreId)` — nouvelle clé DataStore `stringSetPreferencesKey("auto_pin_dismissed")`.
- `OnKindleViewModel.loadOnKindle()` :
  - Ensemble effectif des épinglés = `pinnedIds (manuel) ∪ { livres avec enCoursLecture=true et non présents dans dismissedIds }`.
  - Nettoyage de `dismissedIds` pour les livres qui ne sont plus `enCoursLecture` ou sont devenus `calibreLu` (même logique que le nettoyage existant de `pinnedBookIds` sur `calibreLu=true`, issue #75).
- `OnKindleViewModel.togglePin(livreId)` : distingue épingle manuelle (`livreId in state.pinnedBookIds` → `togglePinnedReading`) d'un livre épinglé uniquement via l'auto-pin (`state.livres.any { it.livreId == livreId && it.enCoursLecture }` et pas dans `pinnedBookIds` → `dismissAutoPin`, pas un toggle réversible en un clic : c'est une confirmation de retrait durable).

### Tests (TDD RED→GREEN)

- `tests/test_build_onkindle_table.py` : classe `TestBuildOnkindleEnCoursLecture`, 5 cas (progression partielle, absente, zéro, complète, colonne custom absente). Extension du helper `_make_calibre_db()` avec un paramètre `ko_progfloat_by_id` pour injecter la table `custom_column_4` mockée.
- `app/src/test/java/com/lmelp/mobile/OnKindleAutoPinTest.kt` (nouveau fichier) : 6 cas — auto-épinglage sans action utilisateur, persistance du retrait manuel après re-création du ViewModel, nettoyage de `auto_pin_dismissed` quand `enCoursLecture` redevient faux, nettoyage combiné avec `calibreLu=true`, non-régression épinglage manuel classique, groupement en tête auto+manuel.
- `FakeUserPreferencesRepository` (déclaré dans `OnKindlePinTest.kt`) étendu avec `_autoPinDismissed` et une méthode `autoPinDismissedSnapshot()` pour lecture synchrone en test (accès direct à `.value` du `MutableStateFlow`, plus simple qu'une collecte de `Flow`).

### Documentation

- `docs/user/epingler_livres.md` : section "Épinglage automatique" + précisions sur le désépinglage d'un livre auto-épinglé.
- `docs/dev/data-schema.md` : documentation de `onkindle.en_cours_lecture` avec la logique de calcul.
- `docs/dev/local-state-datastore.md` : nouvelle section "Cas d'usage : auto-épinglage 'en cours de lecture' (issue #131)" détaillant le pattern de fusion pinned/auto-pin/dismissed.

## Piège rencontré : régénération de la mini-DB embarquée après bump de version Room

Après incrément de `ROOM_VERSION` (7→8), `tests/test_lmelp_db_integrity.py::TestVersionConsistance::test_user_version_db_egale_room_version` échoue tant que `app/src/main/assets/lmelp.db` (mini-DB, voir ADR 0002) n'est pas régénérée — son `PRAGMA user_version` reste à l'ancienne valeur. Procédure suivie (conforme à l'interdiction CLAUDE.md d'utiliser `export_mongo_to_sqlite.py --force` directement sur l'asset) :

1. Générer une base complète temporaire : `python scripts/export_mongo_to_sqlite.py --output /tmp/.../lmelp_complete.db --force` (nécessite MongoDB + `LMELP_CALIBRE_DB` configurés dans `scripts/.env`).
2. Régénérer la mini-DB depuis cette base complète : `python scripts/build_mini_db.py --source /tmp/.../lmelp_complete.db --output app/src/main/assets/lmelp.db --nb-emissions 3`.
3. Vérifier `sqlite3 app/src/main/assets/lmelp.db "PRAGMA user_version;"` → doit correspondre au nouveau `ROOM_VERSION`.

## Risque identifié et suivi créé : issue #132

En testant en conditions réelles (device physique, base complète poussée via ADB), question soulevée par l'utilisateur : le mécanisme de mise à jour de données à distance (issue #118, `DataUpdateRepository` + `DatabaseFileReplacer`) ne vérifie **jamais** `PRAGMA user_version` — il compare uniquement `db_metadata.version` (timestamp de fraîcheur). Si une nouvelle version de l'app avec un schéma Room incrémenté est installée avant que la GitHub Release `data-latest` ait été republiée avec ce nouveau schéma, `DatabaseFileReplacer.replace()` copierait un fichier à l'ancien schéma sans vérification, et au redémarrage Room déclencherait `fallbackToDestructiveMigration()` **silencieusement** → toutes les tables détruites et recréées vides, sans message d'erreur.

Issue de suivi créée : [castorfou/lmelp-mobile#132](https://github.com/castorfou/lmelp-mobile/issues/132) — ajouter une vérification de compatibilité de schéma (`PRAGMA user_version`) côté `DataUpdateRepository` avant d'appliquer une mise à jour distante, plutôt que de basculer en perte de données silencieuse.

Confirmé avec l'utilisateur : le pipeline `docker-lmelp`/NAS n'a rien à changer pour ce genre de bump de schéma — l'image `ghcr.io/castorfou/lmelp-mobile-export` est buildée automatiquement depuis ce repo (CI/CD sur `Dockerfile.export`, voir `docs/dev/build_deploy_apk.md`), donc le nouveau schéma se propage naturellement au prochain rebuild/pull de l'image après merge sur `main`.

## Test manuel effectué

Device physique connecté (`46191FDAP00831`). Procédure utilisée pour valider sans dépendre du mécanisme de mise à jour distante (qui aurait pu masquer le bug en réappliquant une base à l'ancien schéma) :

1. `adb uninstall com.lmelp.mobile` + `scripts/build.sh` + `scripts/deploy.sh` (mini-DB embarquée v8).
2. Lancement une fois pour déclencher la copie de l'asset Room (`createFromAsset`).
3. `am force-stop`, suppression des résidus `lmelp.db-shm`/`lmelp.db-wal` (bug WAL déjà documenté issue #101), remplacement de `databases/lmelp.db` par la base complète fraîchement générée (`adb push` + `run-as cp`), relance.

Résultat confirmé par l'utilisateur : l'app fonctionne, l'auto-épinglage s'affiche correctement sur l'écran Sur ma liseuse.

## Fichiers modifiés (résumé)

- `scripts/export_mongo_to_sqlite.py`
- `app/src/main/java/com/lmelp/mobile/data/db/LmelpDatabase.kt`
- `app/src/main/java/com/lmelp/mobile/data/db/OnKindleDao.kt`
- `app/src/main/java/com/lmelp/mobile/data/model/Entities.kt`
- `app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt`
- `app/src/main/java/com/lmelp/mobile/data/repository/OnKindleRepository.kt`
- `app/src/main/java/com/lmelp/mobile/data/repository/UserPreferencesRepository.kt`
- `app/src/main/java/com/lmelp/mobile/viewmodel/OnKindleViewModel.kt`
- `app/src/test/java/com/lmelp/mobile/OnKindlePinTest.kt`
- `app/src/test/java/com/lmelp/mobile/OnKindleAutoPinTest.kt` (nouveau)
- `tests/test_build_onkindle_table.py`
- `app/src/main/assets/lmelp.db` (mini-DB régénérée)
- `docs/user/epingler_livres.md`
- `docs/dev/data-schema.md`
- `docs/dev/local-state-datastore.md`
