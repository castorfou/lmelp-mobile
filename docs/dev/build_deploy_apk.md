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

# 2. Fetch couvertures manquantes (rare, uniquement si nouvelle DB)
python scripts/check_covers.py --fetch --no-open

# 3. Build + deploy
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

> ⚠️ **Toujours inclure `--calibre-db`** lors de l'export. Sans cette option,
> `calibre_in_library = 0` et `calibre_lu = 0` pour tous les livres → filtre
> "Lus" vide dans l'app, aucun ✓ affiché. Un test CI bloque le commit si
> la base est commité sans données Calibre (voir `tests/test_lmelp_db_integrity.py`).

### commande complète (à utiliser systématiquement)

```bash
python scripts/export_mongo_to_sqlite.py \
  --mongo-uri mongodb://localhost:27018 \
  --output app/src/main/assets/lmelp.db \
  --force \
  --calibre-db "/home/vscode/Calibre Library/metadata.db"
```

### avec filtre bibliothèque virtuelle Calibre (optionnel)

```bash
python scripts/export_mongo_to_sqlite.py \
  --mongo-uri mongodb://localhost:27018 \
  --output app/src/main/assets/lmelp.db \
  --force \
  --calibre-db "/home/vscode/Calibre Library/metadata.db" \
  --calibre-virtual-library guillaume
```

### après regénération : forcer la recopie sur le device

Room copie `lmelp.db` depuis les assets une seule fois. Si la version Room n'a
pas changé, désinstaller et réinstaller pour forcer la recopie :

```bash
adb uninstall com.lmelp.mobile
./gradlew installDebug
```

## cache couvertures babelio

un script pour avoir des infos sur le cache

```bash
./scripts/inspect_cover_cache.sh
```

un script pour verifier la coherence des images

```bash
python scripts/check_covers.py
```

### nombre d'images dans le cache

```bash
adb shell cat /sdcard/Android/data/com.lmelp.mobile/files/couvertures_cache.json | python3 -c "import sys,json; d=json.load(sys.stdin); total=len(d); ok=sum(1 for v in d.values() if v); print(f'Total: {total} | Avec image: {ok} | Vides (pas de couverture Babelio): {total-ok}')"
```

### vider le cache des couvertures babelio

```bash
adb shell rm /sdcard/Android/data/com.lmelp.mobile/files/couvertures_cache.json
```
