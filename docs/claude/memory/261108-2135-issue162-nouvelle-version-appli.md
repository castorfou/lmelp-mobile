# Issue #162 — Signaler (et installer) une nouvelle version de l'appli

## Contexte

L'app savait détecter et appliquer une MAJ des **données** (`data-v{N}`, `DataUpdateRepository`, issue #118) mais pas une nouvelle version de l'**APK** publiée par `release.yml` sur un tag `vX.Y.Z` (asset `lmelp-mobile-vX.Y.Z.apk`). Dépendait de l'issue #161 (versionCode dérivé du tag).

## Décisions (prises avec l'utilisateur)

- **Check GitHub + téléchargement et installation de l'APK depuis l'app** (et pas seulement l'ouverture de la page de release). Google Play a été discuté (compte 25 $, test fermé 12 testeurs / 14 jours pour la production, AAB, Play App Signing incompatible avec les APK déjà signés par notre keystore, politique de confidentialité, contenu tiers) → hors scope, éventuelle issue séparée.
- **Pas de check pour une version de dev** (`versionName` contenant `-dev`, c.-à-d. l'APK debug normal, versionCode 1) : sinon toute release paraîtrait plus récente. Garde volontairement sur `-dev` et non sur `BuildConfig.DEBUG` : `./gradlew installDebug -PversionName=0.0.1` donne un debug « non dev » qui permet de tester le parcours complet.

## Pièges découverts

- **`releases/latest` est inutilisable** : GitHub marque les releases de données `data-v9` comme « Latest ». On liste `GET /repos/castorfou/lmelp-mobile/releases?per_page=30` et on filtre côté app.
- GitHub fournit désormais un champ `digest` (`sha256:…`) par asset : utilisé pour vérifier l'APK téléchargé quand il est présent (vérifié sur la release `v0.9.0`).
- La formule du versionCode (`major*10000 + minor*100 + patch`) est **dupliquée** entre `buildSrc/src/main/kotlin/AppVersion.kt` et l'app : buildSrc n'existe qu'au build.
- `v0.0.1` → versionCode 1 (même valeur qu'une version de dev, mais non-dev par son nom).
- **Historique vide dans l'APK de release** (constaté sur `v0.9.0`) : `gitChangelog` (`app/build.gradle.kts:30`) fait `git log -30` au build, mais `actions/checkout` clone par défaut avec `fetch-depth: 1` → un seul commit. Correctif : `fetch-depth: 0` dans `.github/workflows/release.yml`, verrouillé par `tests/test_github_workflows.py::test_release_checkout_fetches_full_history`.
- `adb exec-out screencap` peut renvoyer un fichier vide sur ce téléphone : passer par `adb shell screencap -p /sdcard/s.png` puis `adb pull`.

## Ce qui a été fait

1. **Domaine** — `app/src/main/java/com/lmelp/mobile/data/update/AppReleaseVersion.kt` :
   - `appVersionCodeFromTag(tag)` : regex stricte `^v(\d+)\.(\d+)\.(\d+)$` (exclut `v0.0.0-test`, `v1.2.3-rc.1`, `data-v9`, `1.2.3`), minor/patch ≤ 99.
   - `isDevVersion(versionName)`, `selectLatestAppRelease(releases, installedVersionCode)` (exclut draft, pré-release, sans APK, tag non conforme ; garde le plus grand code strictement supérieur).
2. **Modèles** — `app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt` : `AppRelease` (tag, prerelease, draft, apkUrl, apkSha256, htmlUrl ; `versionName`/`versionCode` dérivés), `AppUpdateCheckResult`, `AppUpdateState` (Idle, UpdateAvailable, Downloading, NeedsPermission, Installing, Error — chacun garde la release pour réessayer).
3. **Réseau** — `app/src/main/java/com/lmelp/mobile/data/remote/AppReleaseApi.kt` (interface) et `OkHttpAppReleaseApi.kt` (liste des releases, premier asset `*.apk`, `digest`, téléchargement).
4. **Repository** — `app/src/main/java/com/lmelp/mobile/data/repository/AppUpdateRepository.kt` : `checkForUpdate()` sous `Dispatchers.IO` (dev → UpToDate sans réseau), `lastCheckResult` + `checkForUpdateAndCache()`, `downloadApk(release, cacheDir)` → `cacheDir/updates/lmelp-mobile-vX.Y.Z.apk` (purge des anciens APK, SHA-256 via `Sha256Verifier` si `digest`).
5. **Installation** — `app/src/main/java/com/lmelp/mobile/data/update/ApkInstaller.kt` : si `!canRequestPackageInstalls()` → `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES` et `NeedsPermission` ; sinon `ACTION_VIEW` sur URI `FileProvider` (`${applicationId}.fileprovider`). Manifeste : `REQUEST_INSTALL_PACKAGES` + `<provider>` ; `app/src/main/res/xml/file_paths.xml` (`<cache-path name="updates" path="updates/"/>`).
6. **ViewModels** — `AboutViewModel` (paramètre optionnel `appUpdateRepository`) : `appUpdateState`, reflet du check silencieux, le bouton « Vérifier les mises à jour » vérifie aussi l'appli (erreurs du check appli non affichées), `installAppUpdate(downloadDir, install)`. `HomeViewModel` : badge réglages = MAJ données **ou** appli (`hasUpdateAvailable(data, app)` + `combine`).
7. **UI** — `app/src/main/java/com/lmelp/mobile/ui/about/AboutScreen.kt` : bloc « Application » (visible seulement si une version est disponible), `formatAppUpdateTitle` / `formatAppUpdateButtonLabel`. `LmelpApp.kt` : `appUpdateRepository` + check silencieux au lancement. `Navigation.kt` : branchement d'`ApkInstaller`.

## Tests

`app/src/test/java/com/lmelp/mobile/` : `AppReleaseVersionTest` (7), `OkHttpAppReleaseApiTest` (4, MockWebServer), `AppUpdateRepositoryTest` (8, fake API comptant les appels), `AboutViewModelAppUpdateTest` (7), `AppUpdateStateFormatterTest` (4). Lint : aucun nouvel avertissement.

## Validation sur device

Release `v0.9.0` publiée depuis `main` (sans la feature) ; debug `-PversionName=0.0.1` installé : À propos affiche « Nouvelle version 0.9.0 disponible », téléchargement + installateur Android lancés (installation de la release à côté du debug). La 1.0.0 sera publiée après le merge de cette issue.
