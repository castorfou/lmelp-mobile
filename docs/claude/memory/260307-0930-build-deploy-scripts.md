# Scripts build et deploy APK — Issue #7

**Date** : 2026-03-07
**Branch** : `7-tech-creer-2-scripts-build-et-deploy`
**Commit** : `2851a17`

## Ce qui a été fait

### Scripts shell accessibles depuis n'importe où

Deux scripts créés dans `scripts/` et ajoutés au PATH via `.bashrc`/`.zshrc` :

- `scripts/build.sh` : remonte l'arborescence pour trouver `gradlew`, configure `JAVA_HOME`/`ANDROID_HOME`, lance `assembleDebug`
- `scripts/deploy.sh` : vérifie device ADB connecté, détecte `INSTALL_FAILED_UPDATE_INCOMPATIBLE` et désinstalle automatiquement avant réinstall

**Pattern clé pour trouver la racine du projet depuis n'importe où :**
```bash
find_project_root() {
    local dir="$PWD"
    while [[ "$dir" != "/" ]]; do
        if [[ -f "$dir/gradlew" ]]; then echo "$dir"; return 0; fi
        dir="$(dirname "$dir")"
    done
}
```

### PATH persistant

Ajouté dans `.devcontainer/postCreateCommand.sh` (section `setup_android_dev`) pour que le PATH soit permanent au prochain devcontainer. Aussi ajouté manuellement dans `.bashrc` et `.zshrc` pour la session courante :

```
export PATH="/workspaces/lmelp-mobile/scripts:$PATH"
```

**Attention** : le heredoc dans `postCreateCommand.sh` utilise `ENVEOF` sans guillemets (pas `'ENVEOF'`) pour permettre l'interpolation de `$PROJECT_PATH`, avec `\$ANDROID_HOME` échappé pour être écrit littéralement.

### Version info dans l'APK (BuildConfig)

Dans `app/build.gradle.kts` : injection du commit git et de la date de build.

```kotlin
val gitCommit: String by lazy {
    ProcessBuilder("git", "rev-parse", "--short", "HEAD")
        .directory(rootDir).redirectErrorStream(true).start()
        .inputStream.bufferedReader().readLine()?.trim() ?: "unknown"
}
val buildDate: String by lazy { SimpleDateFormat("dd/MM/yy").format(Date()) }
// Dans defaultConfig :
buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")
```

Aussi activé `buildConfig = true` dans `buildFeatures`.

Résultat dans `BuildConfig.java` :
```java
public static final String BUILD_DATE = "07/03/26";
public static final String GIT_COMMIT = "1a4a515";
```

Format affiché : `v. 1a4a515 (07/03/26)` — inspiré de backoffice-frontend.

### AboutScreen (prête, non visible)

`app/src/main/java/com/lmelp/mobile/ui/about/AboutScreen.kt` créé mais **non raccordé à la navigation** — sera intégré lors du futur redesign de la page d'accueil / page technique.

Utilise `BuildConfig.GIT_COMMIT` et `BuildConfig.BUILD_DATE`.

## Commandes de build

```bash
# Depuis la racine du projet (commande directe)
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/vscode/android-sdk ./gradlew assembleDebug

# Via script (depuis n'importe où après source ~/.bashrc)
build.sh
deploy.sh
```

## Docs mises à jour

`docs/dev/build_deploy_apk.md` mis à jour par l'utilisateur pour référencer les nouveaux scripts.
