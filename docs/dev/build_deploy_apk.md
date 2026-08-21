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

> ⚠️ **Ce mécanisme ADB reste valable pour le déploiement APK en dev/debug**, mais n'est plus la cible pour la seule mise à jour des données : voir l'ADR [0001 — Séparer mise à jour appli / mise à jour données](adr/0001-separation-maj-appli-donnees.md) ([issue #116](https://github.com/castorfou/lmelp-mobile/issues/116)), qui décrit le passage à une publication `lmelp.db` en GitHub Release téléchargeable en HTTP, sans ADB.

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
- **si `lsusb`/le gestionnaire de périphériques voit bien le téléphone mais que `adb devices` reste vide malgré `adb kill-server && adb -a start-server`** : redémarrer le téléphone résout souvent le problème (vécu régulièrement, cause exacte non identifiée côté adb/udev) — à essayer avant de creuser plus loin.


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

⚠️ **Depuis l'ADR [0002](adr/0002-distribution-app-mini-db-embarquee.md) (issue #119), cet asset committé est volontairement un extrait minimal (3 émissions), pas la base complète.** La vraie base est téléchargée par l'app elle-même au lancement (voir plus haut, issue #118). Ne jamais committer une base complète ici — voir plus bas.

### régénérer une base complète locale (pour dev/debug, PAS pour l'asset committé)

le comportement viens de `scripts/.env` (precedemment on avait des parametres mais maintenant je detecte dans le fichier env les parametre a passer comme calibre-db)

> ⚠️ **Toujours inclure `--calibre-db`** lors de l'export. Sans cette option,
> `calibre_in_library = 0` et `calibre_lu = 0` pour tous les livres → filtre
> "Lus" vide dans l'app, aucun ✓ affiché.

```bash
python scripts/export_mongo_to_sqlite.py --force --output /tmp/lmelp_complete.db
```

Pour faire tourner l'app localement contre cette base complète (recherche full-text sur du vrai contenu, recommandations SVD, etc.) sans la committer, remplacer manuellement le fichier sur le device après install (voir "Mise à jour DB sur le téléphone" dans `CLAUDE.md`), ou pointer les tests Python dessus via `LMELP_DB_PATH=/tmp/lmelp_complete.db pytest`.

### régénérer l'extrait minimal committé (`app/src/main/assets/lmelp.db`)

```bash
python scripts/build_mini_db.py --source /tmp/lmelp_complete.db --output app/src/main/assets/lmelp.db --nb-emissions 3
```

⚠️ **Ne jamais faire `python scripts/export_mongo_to_sqlite.py --force --output app/src/main/assets/lmelp.db` directement** — ça produirait une base complète et casserait `TestTailleMinimale` dans `tests/test_lmelp_db_integrity.py`. Toujours passer par `build_mini_db.py`.

### après regénération : forcer la recopie sur le device

Room copie `lmelp.db` depuis les assets une seule fois. Si la version Room n'a
pas changé, désinstaller et réinstaller pour forcer la recopie — `deploy.sh`
seul (qui fait `adb install -r`) **ne suffit pas**, il faut désinstaller
d'abord (voir issue #112 dans `CLAUDE.md`) :

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
