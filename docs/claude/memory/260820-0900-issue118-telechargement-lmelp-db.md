# Issue #118 — Téléchargement/remplacement de lmelp.db depuis l'app

Implémentation TDD complète de la contrepartie côté app de l'ADR
[0001-separation-maj-appli-donnees.md](../dev/adr/0001-separation-maj-appli-donnees.md)
(issue #116) : vérification, téléchargement et remplacement de la base SQLite
locale depuis la GitHub Release `data-latest` du repo `castorfou/lmelp-mobile`.

Branche `118-download-lmelp-db-from-app`. Travail non encore commité au moment
de la rédaction de cette note (commits à suivre juste après).

## Architecture mise en place

Nouveau package `data/remote/` :
- `RemoteMetadata.kt` — parsing JSON pur du `metadata.json` publié à côté de
  `lmelp.db` sur la release (`org.json`, pas de lib de sérialisation dédiée).
- `GitHubReleaseApi.kt` — interface abstraite (`fetchMetadata`,
  `downloadDatabase`), pour mocker le réseau dans les tests.
- `OkHttpGitHubReleaseApi.kt` — implémentation réelle. Résout
  `https://api.github.com/repos/castorfou/lmelp-mobile/releases/tags/data-latest`,
  extrait les `browser_download_url` de `lmelp.db` et `metadata.json`.

Nouveau package `data/update/` :
- `Sha256Verifier.kt` — hash SHA-256 (JDK `MessageDigest`) pour vérifier
  l'intégrité du fichier téléchargé avant remplacement.
- `DatabaseFileReplacer.kt` — remplacement physique du fichier, avec
  suppression préalable de `lmelp.db-shm`/`lmelp.db-wal` (bug WAL connu,
  issue #101 : sinon SQLite rollback avec `no such table: search_index`).
- `ProcessRestarter.kt` — ferme le process après une MAJ réussie.

`data/repository/DataUpdateRepository.kt` — orchestrateur :
`checkForUpdate()` (compare `db_metadata.version` local vs
`RemoteMetadata.exportVersion` distant — jamais `PRAGMA user_version`, cf.
issue #102), `checkForUpdateAndCache()` (résultat mis en cache dans un
`StateFlow` `lastCheckResult`, lu passivement par les ViewModels sans
redéclencher d'appel réseau), `applyUpdate()` (download + vérif SHA-256 +
remplacement). Tous les appels réseau tournent sous `withContext(Dispatchers.IO)`.

`data/model/UiModels.kt` — sealed classes `UpdateCheckResult`
(`UpToDate`/`UpdateAvailable`/`Error`) et `DataUpdateState`
(`Idle`/`Checking`/`UpdateAvailable`/`Downloading`/`Verifying`/`Replacing`/
`Restarting`/`Success`/`Error`).

`MetadataRepository.kt` — ajout de `getLocalVersion()` (lit
`db_metadata.version`, déjà exposé via `getDbInfo()` mais pas isolément).

## Intégration UI

- `AboutViewModel.kt` — `dataUpdateState: StateFlow<DataUpdateState>`,
  `checkForUpdateSilently()` (n'expose jamais d'`Error`, offline-first),
  `checkForUpdateManually()` (bouton, affiche l'erreur), `applyUpdate()`.
  Observe aussi `dataUpdateRepository.lastCheckResult` dans son `init` pour
  refléter immédiatement le check silencieux déjà fait au démarrage de l'app.
- `AboutScreen.kt` — section "Mise à jour des données" avec bouton dont le
  libellé varie selon `DataUpdateState` (`formatUpdateStateLabel`).
- `HomeViewModel.kt` — `dataUpdateRepository` optionnel, expose
  `hasUpdateAvailable: Boolean` dans `HomeUiState` (fonction pure
  `hasUpdateAvailable(UpdateCheckResult)` testée isolément).
- `HomeScreen.kt` — petit point vert en overlay sur l'icône réglages si
  `hasUpdateAvailable` (demande utilisateur ajoutée en cours de route, pas
  dans le plan initial).
- `LmelpApp.kt` — check silencieux lancé dans `onCreate()` via un
  `CoroutineScope(SupervisorJob() + Dispatchers.IO)` applicatif.
- `Navigation.kt` — câblage `dataUpdateRepository`, `targetDbFile =
  app.getDatabasePath("lmelp.db")`, `tempDir = app.cacheDir`.

## Dépendances ajoutées

`gradle/libs.versions.toml` + `app/build.gradle.kts` : OkHttp 4.12.0 explicite
(alignée sur la version déjà tirée transitivement par `coil-network-okhttp`,
vérifié via `./gradlew :app:dependencies` avant de figer la version — évite un
conflit de résolution), `okhttp3.mockwebserver` en test, `org.json` déplacé de
`testImplementation` à `implementation`.

## Bugs découverts en test manuel réel (device Pixel 9 Pro, Android 17/API 37)

Le plan initial et les 261 tests unitaires passaient tous, mais deux bugs
n'étaient visibles qu'en conditions réelles sur device :

1. **`NetworkOnMainThreadException` silencieuse** — `viewModelScope.launch { }`
   tourne sur `Dispatchers.Main.immediate` par défaut ; l'appel OkHttp
   synchrone (`client.newCall(request).execute()`) plantait dès le premier
   appel réseau avec une exception au `message` `null`, d'où "Erreur
   inconnue" affiché côté UI. **Aucun test unitaire ne pouvait détecter ça**
   car les mocks contournent l'appel réseau réel. Fix : englober
   `checkForUpdate()`/`applyUpdate()` dans `withContext(Dispatchers.IO)`
   dans `DataUpdateRepository.kt`. Fallback de message d'erreur amélioré
   (`e.javaClass.simpleName` au lieu d'un texte générique) pour faciliter
   le diagnostic futur.

2. **Auto-relance de l'app après MAJ impossible sur Android 12+** — le plan
   initial prévoyait `AlarmManager.set(...)` + `PendingIntent.getActivity(...)`
   pour relancer automatiquement l'Activity après avoir tué le process
   (`Runtime.getRuntime().exit(0)`). Confirmé en test manuel sur Android
   17/API 37 : l'app se ferme mais **ne se relance jamais seule** — bloqué
   par les restrictions de lancement d'activité en arrière-plan
   (durcies au fil des versions Android récentes, spécifiquement pour
   empêcher ce genre de pattern). Décision : **abandonner l'auto-relance**,
   `ProcessRestarter.closeApp()` se contente de `Runtime.getRuntime().exit(0)`
   après un délai de 2s (`RESTART_DELAY_MS`) pendant lequel `AboutScreen`
   affiche "Mise à jour appliquée ! Veuillez rouvrir l'application." —
   l'utilisateur rouvre lui-même depuis le launcher.

## Décision de conception non prévue dans le plan initial

Recharger l'état Room "à chaud" sans tuer le process (alternative envisagée
pour contourner le problème #2) a été explicitement écartée : les 12
repositories de `LmelpApp.kt` capturent leur DAO à la construction via
`by lazy`, et les rendre reconstructibles aurait nécessité de refactorer
`LmelpApp` + tous les `Factory` de ViewModel — jugé disproportionné par
rapport au scope de cette issue, avec risque de régression sur les 11 autres
écrans de l'app. Le kill-process reste la stratégie retenue pour garantir un
état Room propre (StateFlow des repositories, connexions DAO), cohérent avec
la logique déjà validée côté script bash legacy (`am force-stop`/`am start`
dans `scripts/docker_export_and_push.sh`).

## Convention de test découverte/appliquée

`HomeViewModel` a un ticker interne (`startTicker()`) qui lance des boucles
`while(true) { delay(...) }` dans son `init` — **jamais instancier
`HomeViewModel` directement dans un test avec `runTest`/`advanceUntilIdle()`**,
ça bloque indéfiniment (le ticker infini empêche `advanceUntilIdle()` de
converger). C'est pourquoi `HomeViewModelTickerTest.kt` préexistant ne teste
que des fonctions pures extraites (`randomInitialIndex`, `sampleTickerDelayMs`)
plutôt que le ViewModel complet. Le nouveau `hasUpdateAvailable(UpdateCheckResult):
Boolean` (`HomeViewModel.kt`) suit ce même pattern : fonction pure testée
isolément dans `HomeViewModelUpdateBadgeTest.kt`, jamais testée via une
instance `HomeViewModel` réelle.

## Fichiers de test créés

`RemoteMetadataTest.kt`, `Sha256VerifierTest.kt`, `DatabaseFileReplacerTest.kt`,
`DataUpdateRepositoryTest.kt`, `DataUpdateRepositoryApplyUpdateTest.kt`,
`DataUpdateRepositoryCacheTest.kt`, `OkHttpGitHubReleaseApiTest.kt` (via
`MockWebServer`), `AboutViewModelDataUpdateTest.kt`, `UpdateStateFormatterTest.kt`,
`HomeViewModelUpdateBadgeTest.kt` — pattern mockito-kotlin +
`kotlinx-coroutines-test` (`StandardTestDispatcher`/`runTest`/`advanceUntilIdle`/
`runCurrent`), cohérent avec les conventions déjà en place dans le projet
(`app/src/test/java/com/lmelp/mobile/HomeRepositoryTest.kt`,
`app/src/test/java/com/lmelp/mobile/PalmaresViewModelTest.kt`).

## Validation réelle contre `data-latest`

Testé en conditions réelles contre la release `data-latest`
(`castorfou/lmelp-mobile`), alimentée en production le 2026-08-19 (180
émissions, 1688 livres, 4275 avis) : désinstallation/réinstallation de l'app
pour repartir de l'asset embarqué obsolète (179 émissions, export
2026-08-16), puis flux complet check → badge → téléchargement → vérification
SHA-256 → remplacement → fermeture avec message → réouverture manuelle →
données à jour confirmées visibles (180 émissions).
