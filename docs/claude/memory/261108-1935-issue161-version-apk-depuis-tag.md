# Issue #161 — versionName / versionCode de l'APK dérivés du tag `v*`

## Contexte

`app/build.gradle.kts` fixait `versionName = "1.0"` et `versionCode = 1` : tout APK publié par `release.yml` affichait « 1.0 », et un `versionCode` constant empêche Android (et la future détection de nouvelle version de l'appli, issue #162) de savoir qu'un APK est plus récent. Aucune release `v*` n'avait encore été publiée (seulement des `data-v*`).

## Décisions (prises avec l'utilisateur)

- Règle dans **`buildSrc`** (Kotlin, testée en JUnit), plutôt qu'un script Python appelé par `release.yml` : `release.yml` ne passe que `-PversionName=X.Y.Z`, Gradle calcule le reste.
- `versionCode = major*10000 + minor*100 + patch` (plutôt que `git rev-list --count HEAD`) : déterministe, recalculable depuis le nom d'une release pour #162. Contrainte : minor et patch ≤ 99.

## Ce qui a été fait

1. **`buildSrc/src/main/kotlin/AppVersion.kt`** : `data class AppVersion(name, code)` avec
   - `fromTag(raw)` : accepte `1.2.3`, `v1.2.3`, `v1.2.3-rc.1` (name sans `v`, suffixe de pré-release conservé, code calculé sur X.Y.Z) ; `IllegalArgumentException` si format invalide (`1.2`, `1.2.3.4`, `1.2.3+build`), minor/patch > 99, ou code > 2 100 000 000 (limite Android) ; code borné à ≥ 1 (`v0.0.0` → 1).
   - `dev(sha)` → `0.0.0-dev+<sha>`, code 1.
   - `resolve(prop, sha)` → `fromTag` si propriété non vide, sinon `dev`.
2. **`buildSrc/build.gradle.kts`** : `kotlin-dsl`, `mavenCentral()`, JUnit 4.13.2. Pas de dépendance AGP.
3. **`app/build.gradle.kts`** : `val appVersion = AppVersion.resolve(findProperty("versionName") as String?, gitCommit)` → `versionCode`/`versionName`. Le `versionNameSuffix = "-debug"` (issue #160) s'ajoute : debug local = `0.0.0-dev+<sha>-debug`.
4. **`.github/workflows/release.yml`** : extraction de la version déplacée **avant** le build ; `./gradlew assembleRelease -PversionName="${VERSION#v}"` ; garde-fou `aapt2 dump badging` qui échoue si `versionName` de l'APK ≠ tag sans `v` (même dossier build-tools que `apksigner`).
5. **`.github/workflows/ci.yml`** (job android) : étape `./gradlew -p buildSrc test` — Gradle 8 ne lance plus les tests de buildSrc pendant un build normal.
6. **Écran À propos** (`app/src/main/java/com/lmelp/mobile/ui/about/AboutScreen.kt`) : fonction pure `formatVersionLine(versionName, gitCommit, buildDate)` → « Version 1.2.3 · abc1234 (08/11/26) » ; `AboutContent` prend un paramètre `versionName` alimenté par `BuildConfig.VERSION_NAME` (remplace « v. <sha> (<date>) »).

## Tests

- `buildSrc/src/test/kotlin/AppVersionTest.kt` (10 tests) : tag simple, préfixe `v`, pré-release, suite strictement croissante (`v0.1.0` … `v1.2.99` < `v1.3.0` … `v10.0.0`), `v0.0.0` → 1, formats invalides, minor/patch 100, limite Android, `dev`, `resolve`.
- `app/src/test/java/com/lmelp/mobile/AboutVersionLineTest.kt` : `formatVersionLine`.
- `tests/test_github_workflows.py` : `release.yml` passe `-PversionName=` à `assembleRelease` et vérifie via `aapt2 dump badging` ; `build.gradle.kts` utilise `AppVersion.resolve(` et plus `"1.0"` ; `ci.yml` lance `-p buildSrc test`.

## Vérifié localement

- `assembleRelease -PversionName=1.2.3` → `versionCode='10203' versionName='1.2.3'`.
- `assembleDebug` seul → `versionCode='1' versionName='0.0.0-dev+ef51dc6-debug'`.
- `./gradlew help -PversionName=1.2` → build en échec, message « Version « 1.2 » invalide ».
- Device (utilisateur) : écran À propos affiche la version.

## Pièges

- `-PversionName` s'applique à **toutes** les variantes d'une même invocation Gradle : `assembleDebug assembleRelease -PversionName=1.2.3` donne aussi `1.2.3-debug` au debug.
- Dans un test regex sur le workflow, `"${BUILD_TOOLS}aapt2" dump badging` contient un guillemet entre `aapt2` et `dump` : chercher `aapt2"? dump badging`.
