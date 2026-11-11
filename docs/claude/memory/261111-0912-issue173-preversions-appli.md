# Issue #173 — Préversions de l'appli (`app-preview`)

## Contexte

Une fonctionnalité mergée sur `main` n'arrivait sur le téléphone qu'au prochain tag `vX.Y.Z` posé à la main. L'APK debug n'aide pas : c'est une autre appli (`com.lmelp.mobile.debug`, #160), avec ses propres données. Une **version** reste une décision humaine ; entre deux versions, chaque push sur `main` qui touche l'appli publie une **préversion** `X.Y.Z+N`, proposée par la détection de mise à jour existante (#162).

## Décisions

- **versionCode `(major*10000 + minor*100 + patch)*1000 + N`** (N = 0 pour une version, N ≤ 999). Changement sans retour, compatible avec la 1.2.0 installée (10200 < 10 200 000). Une version passe toujours devant les préversions qui la précèdent : v1.2.0 → 10 200 000, 1.2.0+3 → 10 200 003, v1.2.1 → 10 201 000.
- **Une seule pré-release tournante, tag fixe `app-preview`**, supprimée (`--cleanup-tag`) puis recréée à chaque publication. La version `X.Y.Z+N` est dans le **titre de la release**, pas dans l'asset : GitHub réécrit le `+` des noms d'assets → APK `lmelp-mobile-preview-X.Y.Z.N.apk`.
- **`--prerelease`** : `app-preview` ne devient jamais « Latest » (réservé à `data-v{N}`).
- **N = 0 (commit tagué) → rien publié** ; pas de tag stable → rien publié.
- **Workflow séparé, étapes de signature dupliquées** depuis `release.yml` plutôt qu'une action composite : une action composite échapperait au test Node 24 (`tests/test_github_workflows.py` ne lit que `.github/workflows/*.yml`) et à Dependabot.
- **Amorçage (décision utilisateur)** : la 1.2.0 installée filtre toutes les pré-releases (code #162), elle ne verra jamais `app-preview`. Après le merge, poser **v1.3.0** ; les préversions `1.3.0+N` suivent.
- Hors périmètre : bouton `workflow_dispatch` « Publier une version », préversions depuis une branche de feature.

## Ce qui a été fait

1. **`buildSrc/src/main/kotlin/AppVersion.kt`** : regex étendue à `(?:\+(\d+))?`, `MAX_PREVIEW_COMMITS = 999`, nouvelle formule. `-PversionName=1.2.0+3` accepté ; `+build` (non numérique) toujours refusé. Limite Android : major ≤ 210 (`210.0.0` = 2 100 000 000 pile, `210.0.0+1` refusé).
2. **`app/src/main/java/com/lmelp/mobile/data/update/AppReleaseVersion.kt`** : copie de la formule (`versionCode()` privé), `APP_PREVIEW_TAG`, `appVersionCodeFromPreviewName("X.Y.Z+N")`, `isPreviewVersion()` (suffixe `-debug` toléré ; `0.0.0-dev+sha` n'en est pas une). `selectLatestAppRelease` garde `!prerelease || isPreview`.
3. **`app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt`** : `AppRelease.name` (défaut `""`), `isPreview`, `versionName`/`versionCode` lus dans `name` pour `app-preview`.
4. **`app/src/main/java/com/lmelp/mobile/data/remote/OkHttpAppReleaseApi.kt`** : parse `name`.
5. **`app/src/main/java/com/lmelp/mobile/ui/about/AboutScreen.kt`** : « Préversion X disponible » (`formatAppUpdateTitle`), « Version 1.2.0+3 (préversion) » (`formatVersionLine`). Historique (#170) inchangé : `1.2.0+3` ne correspond à aucune section, donc rien n'est « installée » et les commits restent sous « Pas encore publié ».
6. **`scripts/preview_version.sh`** : `git describe --tags --long --match 'v[0-9]*.[0-9]*.[0-9]*' --exclude '*-*'` → `X.Y.Z+N`, vide si N = 0 ou aucun tag.
7. **`.github/workflows/app-preview.yml`** : push `main` + `paths` (app, buildSrc, gradle, `*.gradle.kts`, `gradle.properties`, le script, le workflow), `concurrency: app-preview` + `cancel-in-progress`, checkout `fetch-depth: 0`, toutes les étapes après le calcul en `if: steps.version.outputs.VERSION != ''`, publication par `gh release delete/create`.

## Tests

- buildSrc `AppVersionTest` : exemples de l'issue, N ≤ 999, ordre version/préversions, ancienne valeur 10200 inférieure.
- App : `AppReleaseVersionTest` (formule identique, nom de préversion, sélection, `v0.0.0-test` ignorée), `AppUpdateRepositoryTest` (1.2.0 → préversion ; préversion installée → v1.2.1 ; à jour), `AppUpdateStateFormatterTest`, `AboutVersionLineTest`, `HistoriqueGroupingTest`, `OkHttpAppReleaseApiTest` (`name`). Codes installés des tests existants passés à `10_200_000`.
- Python : `tests/test_preview_version.py` (vrais dépôts git temporaires, tags annotés, `v0.0.0-test`/`data-v9`/`app-preview` ignorés), `tests/test_github_workflows.py` (trigger, paths sans docs, concurrency, `if:` après le calcul, signature, `--prerelease`/`--title`, asset sans `+`).

## Pièges

- PyYAML lit la clé `on:` d'un workflow comme le booléen `True` ; mypy refuse `wf.get("on", wf.get(True))` typé `dict` → `wf["on"] if "on" in wf else wf[True]`.
- Regex sur commande shell continuée par `\` : `[^\n]*(?:\\\n[^\n]*)*` s'arrête à la première ligne (le `[^\n]*` gourmand avale le `\`) ; utiliser `(?:[^\n]*\\\n)*[^\n]*`.
- Test device : `./gradlew installDebug -PversionName=1.2.0+3` → versionName `1.2.0+3-debug`, versionCode 10200003.
