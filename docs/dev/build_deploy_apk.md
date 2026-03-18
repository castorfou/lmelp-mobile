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

si le device ne s'affiche pas, un `adb kill-server` peut aider


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

Les images de couverture sont chargées via `url_cover` directement depuis `lmelp.db` et mises en cache par Coil dans `getExternalFilesDir/coil_image_cache` (50 MB max).

> ⚠️ Ce cache est effacé lors d'une désinstallation (comportement Android 11+). Une issue [#62](https://github.com/castorfou/lmelp-mobile/issues/62) est ouverte pour implémenter Android Backup.

### nombre d'images dans le cache

```bash
ADB=/home/vscode/android-sdk/platform-tools/adb
$ADB shell "ls /sdcard/Android/data/com.lmelp.mobile/files/coil_image_cache/*.1 2>/dev/null | wc -l" && echo "images en cache"
```

### faire le backup dans le cloud

```bash
ADB=/home/vscode/android-sdk/platform-tools/adb
$ADB shell "bmgr backupnow com.lmelp.mobile" 2>&1
```

## pour l'env python


```bash
# ajout surprise
uv add --active scikit-surprise

# apres modif de pyproject.toml pour downgrade numpy
uv sync --active --all-extras
```
