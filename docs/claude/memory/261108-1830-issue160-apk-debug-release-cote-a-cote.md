# Issue #160 — APK debug et release côte à côte

## Contexte

Depuis #159, l'appli a deux familles d'APK : debug (`scripts/build.sh` + `scripts/deploy.sh`, clé debug du devcontainer) et release (`release.yml` sur tag `vX.Y.Z`, clé `lmelp-release.jks`). Même identifiant `com.lmelp.mobile`, signatures différentes : passer de l'un à l'autre imposait `adb uninstall`, qui efface l'état local (DataStore, base téléchargée).

## Ce qui a été fait

1. **`app/build.gradle.kts`** : `buildTypes.debug { applicationIdSuffix = ".debug"; versionNameSuffix = "-debug" }`. Le `namespace` reste `com.lmelp.mobile` : packages Kotlin, `R`, `BuildConfig` inchangés. Aucun code Kotlin n'utilisait le nom de paquet ; les règles de backup (domaines + chemins relatifs) valent pour les deux paquets.
2. **Nom** : `app/src/debug/res/values/strings.xml` surcharge `app_name` → « LMELP debug » (choix de l'utilisateur, court pour ne pas être tronqué sous l'icône).
3. **Icône debug, fond orange** (choix de l'utilisateur) : `scripts/generate_launcher_icon.py` paramètre les couleurs de fond (`Colors`, `RELEASE_COLORS`, `DEBUG_COLORS` = `DEBUG_ORANGE` #E66A00 → `DEBUG_ORANGE_END` #B84A00 ; `build_background(size, colors)`, `build_legacy_icon(..., colors)`, `write_background`, `write_legacy_icons`) et écrit dans `app/src/debug/res` seulement `drawable/ic_launcher_background.xml` et `mipmap-*/ic_launcher{,_round}.png`. Foreground, monochrome et XML adaptive restent ceux de `main`. Régénération déterministe : les ressources de `main` ne bougent pas.
4. **`scripts/deploy.sh`** : `APP_PACKAGE="com.lmelp.mobile.debug"`, lance l'app après installation (`am start -n com.lmelp.mobile.debug/com.lmelp.mobile.MainActivity`). La désinstallation automatique sur `INSTALL_FAILED_UPDATE_INCOMPATIBLE` ne vise que la debug.
5. **`scripts/docker_export_and_push.sh`** (`export-and-push` du conteneur `lmelp-export`) : défaut `APP_PACKAGE=com.lmelp.mobile.debug` (seule app debuggable, donc `run-as`), `am start` avec le nom de classe complet.
6. **Docs** : `docs/dev/livrer_appli.md` (tableau signature avec identifiants et apparence, section « Debug et release sur le même téléphone »), `docs/dev/build_deploy_apk.md` et `docs/dev/schema-migration-workflow.md` (commandes `adb`, `bmgr`, chemins `/sdcard/Android/data/com.lmelp.mobile.debug/...`), `docs/user/installer_mettre_a_jour.md` (dépannage : deux icônes), CLAUDE.md, README.

## Tests

- `app/src/test/java/com/lmelp/mobile/DebugApplicationIdTest.kt` : en `testDebugUnitTest`, `BuildConfig.APPLICATION_ID == "com.lmelp.mobile.debug"` et le package de `BuildConfig` reste `com.lmelp.mobile`.
- `tests/test_launcher_icon.py::TestVarianteDebug` : couleurs orange, PNG legacy et fond XML debug, foreground/monochrome non dupliqués, `app_name` debug.
- `tests/test_deploy_scripts.py` : exécute réellement `scripts/deploy.sh` dans un faux projet (`gradlew` + `app/build/outputs/apk/debug/app-debug.apk`) avec un faux `adb` dans `$ANDROID_HOME/platform-tools` qui journalise ses arguments (`FAKE_ADB_LOG`) et simule `pm list packages` (`FAKE_INSTALLED`) et `install -r` (`FAKE_INSTALL_R_OUTPUT`). Vérifie installation + lancement de la debug et qu'aucun appel ne vise `com.lmelp.mobile`. Test statique sur `docker_export_and_push.sh`.

## Validation

`aapt2 dump badging` : debug `package: name='com.lmelp.mobile.debug' versionName='1.0-debug'`, label « LMELP debug » ; release inchangée (`com.lmelp.mobile`, « Le Masque et la Plume »). Test device par l'utilisateur, avec la release `v0.0.0-test` déjà installée : deux icônes, la debug se lance, la release garde ses données.

## Pièges

- **`am start -n <pkg>/.MainActivity`** : le `.` se résout sur l'**applicationId**, pas sur le namespace → `com.lmelp.mobile.debug.MainActivity` introuvable. Toujours écrire `com.lmelp.mobile.debug/com.lmelp.mobile.MainActivity`.
- **Nouveau dossier de ressources (`src/debug/res`)** : `./gradlew clean` avant de vérifier l'APK (cf. CLAUDE.md, issue #141).
- **Icônes thématiques Android 13+** (monochromes) : identiques pour les deux apps, seul le nom les distingue.
- Les deux apps ont des données séparées : la debug doit télécharger sa propre base depuis À propos.
- Modifier `scripts/docker_export_and_push.sh` déclenche `build-export-image.yml` (republication de l'image + Watchtower au merge).
