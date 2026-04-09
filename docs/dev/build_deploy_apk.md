## processus d'install

**Le processus complet**

Le parti pris est de dire : nouvelle db = nouvel apk = nouveau deploiement

Nouvelle DB peut etre cause par :

- nouvelle emission -> nouveaux livres
- potentiellement nouveau palmares (un chef d'oeuvre est apparu)
- nouvelle lecture -> nouveaux conseils possibles
- des livres sont lus et disparaissent de la liseuse, d'autres apparaissent

On fait manuellement

```bash
# 1. Export DB (rare, uniquement si nouvelle DB)
python scripts/export_mongo_to_sqlite.py --force

# 2. Build + deploy
build.sh && deploy.sh
```

## mise à jour DB sans rebuild APK (issue #81)

Quand seule la base de données change (nouvelle émission), inutile de recompiler l'APK.
Le container `lmelp-export` fait tout : export MongoDB → push ADB → restart app.

**Pré-requis :**

- Téléphone branché en USB, mode **Transfert de fichiers**
- Mode développeur activé (Paramètres → À propos du téléphone, et cliquer 7 fois de suite sur Numéro de version)
- Débogage USB activé (Paramètres → Options développeur)
- Stack docker-lmelp démarrée (`docker compose up -d`)

```bash
# Sur le laptop
adb -a start-server                          # flag -a obligatoire (écoute sur 0.0.0.0)
docker exec lmelp-export export-and-push
```

L'image `ghcr.io/castorfou/lmelp-mobile-export` est publiée automatiquement depuis ce repo (CI/CD sur `Dockerfile.export`). Le service `lmelp-export` est configuré dans le repo `castorfou/docker-lmelp` ([issue #41](https://github.com/castorfou/docker-lmelp/issues/41)).

## build apk depuis vscode

```bash
JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64 ANDROID_HOME=/home/vscode/android-sdk ./gradlew assembleDebug
```

ou depuis [[tech] creer 2 scripts, build et deploy](https://github.com/castorfou/lmelp-mobile/issues/7)

```bash
build.sh
```

## deploy apk sur telephone

**via adb**

pre-requis : `adb devices` doit afficher mon telephone (et ca marche depuis devcontainer)

Si le device ne s'affiche pas :

- un `adb kill-server` peut aider, revoquer les autorisations de debogage USB, et relancer `adb devices` (une popup d'autorisation doit arriver)
- il faut aussi que le debogage USB soit active


```bash
# liste les telephones connectes en USB
~/android-sdk/platform-tools/adb devices

# y deploie l'APK
~/android-sdk/platform-tools/adb install app/build/outputs/apk/debug/app-debug.apk
```

ou depuis [[tech] creer 2 scripts, build et deploy](https://github.com/castorfou/lmelp-mobile/issues/7)

```bash
# deploie et desinstalle avant si necesaire
deploy.sh
```

## desinstaller (INSTALL_FAILED_UPDATE_INCOMPATIBLE Existing package com.lmelp.mobile signatures do not match newer version; ignoring!)

```bash
~/android-sdk/platform-tools/adb uninstall com.lmelp.mobile
```

## regenerer la DB asset

`app/src/main/assets/lmelp.db`

le comportement viens de `scripts/.env` (precedemment on avait des parametres mais maintenant je detecte dans le fichier env les parametre a passer comme calibre-db)

> ⚠️ **Toujours inclure `--calibre-db`** lors de l'export. Sans cette option,
> `calibre_in_library = 0` et `calibre_lu = 0` pour tous les livres → filtre
> "Lus" vide dans l'app, aucun ✓ affiché. Un test CI bloque le commit si
> la base est commité sans données Calibre (voir `tests/test_lmelp_db_integrity.py`).

### commande complète (à utiliser systématiquement)

```bash
python scripts/export_mongo_to_sqlite.py --force
```

### après regénération : forcer la recopie sur le device

Room copie `lmelp.db` depuis les assets une seule fois. Si la version Room n'a
pas changé, désinstaller et réinstaller pour forcer la recopie :

```bash
adb uninstall com.lmelp.mobile
./gradlew installDebug
```

## cache couvertures (Coil)

Les images de couverture sont chargées via `url_cover` directement depuis `lmelp.db` et mises en cache par Coil dans `getExternalFilesDir/coil_image_cache` (25 MB max).

Android Backup est configuré (issue [#62](https://github.com/castorfou/lmelp-mobile/issues/62)) : le cache est sauvegardé automatiquement sur Google Drive et restauré à la réinstallation.

**En usage normal** : Android effectue des backups automatiques toutes les ~24h (device en charge + WiFi). Rien à faire manuellement.

**En dev**, avant de désinstaller, forcer le backup pour ne pas perdre les images :

```bash
ADB=/home/vscode/android-sdk/platform-tools/adb
$ADB shell "bmgr backupnow com.lmelp.mobile"
$ADB uninstall com.lmelp.mobile
./gradlew installDebug
# Les images sont restaurées automatiquement depuis Google Drive
```

### vérifier le nombre d'images dans le cache

```bash
ADB=/home/vscode/android-sdk/platform-tools/adb
$ADB shell "ls /sdcard/Android/data/com.lmelp.mobile/files/coil_image_cache/*.1 2>/dev/null | wc -l" && echo "images en cache"
```

## pour l'env python


```bash
# ajout surprise
uv add --active scikit-surprise

# apres modif de pyproject.toml pour downgrade numpy
uv sync --active --all-extras
```
