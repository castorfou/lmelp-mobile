# Build / Deploy apk

Cette page couvre la boucle de développement : compiler un APK **debug** dans le devcontainer, l'installer en USB sur le téléphone et le tester, éventuellement avec une base de données locale. La publication d'une version de l'appli (APK **release** signé, tag `vX.Y.Z`) est décrite dans [Développer, livrer, mettre à jour](livrer_appli.md).

```bash
scripts/build.sh && scripts/deploy.sh
```

## L'APK debug

| | APK debug | APK release |
|---|---|---|
| Identifiant | `com.lmelp.mobile.debug` | `com.lmelp.mobile` |
| Sur le téléphone | « LMELP debug », icône sur fond orange | « Le Masque et la Plume », icône sur fond bleu nuit |
| Signé avec | la clé debug de la machine qui compile | la clé de release `lmelp-release.jks` |

Les deux apps s'installent côte à côte, chacune avec ses propres données (base téléchargée, épingles, palmarès personnel). Tout ce qui est décrit sur cette page vise l'app debug et ne touche jamais à la version release installée.

Les données ne passent pas par l'APK : l'app télécharge la base publiée sur la release `data-v{N}` depuis son écran À propos (voir l'[ADR 0001](adr/0001-separation-maj-appli-donnees.md)). L'APK n'embarque qu'un [extrait minimal](#extrait-minimal-embarque).

## Compiler

```bash
scripts/build.sh
```

`build.sh` lance `./gradlew assembleDebug` depuis la racine du projet (il peut être appelé de n'importe quel sous-dossier) et produit `app/build/outputs/apk/debug/app-debug.apk`. `JAVA_HOME` et `ANDROID_HOME` sont définis par le devcontainer ; à défaut, le script prend `/usr/lib/jvm/java-17-openjdk-amd64` et `/home/vscode/android-sdk`.

Après l'ajout ou le renommage d'un dossier de ressources (`app/src/*/res/...`), faire `./gradlew clean` avant de compiler : le build incrémental peut produire un APK sans les nouvelles ressources.

## Installer sur le téléphone

### Prérequis

- Mode développeur activé sur le téléphone : Paramètres → À propos du téléphone → appuyer 7 fois sur **Numéro de build**.
- **Débogage USB** activé : Paramètres → Options pour les développeurs.
- Téléphone branché en USB, en mode **Transfert de fichiers**, et popup « Autoriser le débogage USB » validée.

`adb devices` doit lister le téléphone. `adb` n'est pas dans le `PATH` : il se trouve dans `~/android-sdk/platform-tools/adb`.

```bash
~/android-sdk/platform-tools/adb devices
```

### deploy.sh

```bash
scripts/deploy.sh
```

`deploy.sh` :

1. vérifie que l'APK debug existe et qu'un téléphone est connecté ;
2. installe l'APK (`adb install -r` si l'app debug est déjà là, ce qui conserve ses données) ;
3. si l'app debug installée a été compilée sur une autre machine (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, signature différente), la désinstalle puis réinstalle ;
4. lance l'app debug.

Équivalent manuel :

```bash
ADB=~/android-sdk/platform-tools/adb
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
$ADB shell am start -n com.lmelp.mobile.debug/com.lmelp.mobile.MainActivity
$ADB uninstall com.lmelp.mobile.debug      # désinstaller l'app debug
```

`am start` exige le nom de classe complet `com.lmelp.mobile.MainActivity` : la forme courte `com.lmelp.mobile.debug/.MainActivity` se résout en `com.lmelp.mobile.debug.MainActivity`, qui n'existe pas.

### Le téléphone n'apparaît pas dans `adb devices`

Dans l'ordre :

1. vérifier que le mode USB est sur **Transfert de fichiers** et que le débogage USB est activé ;
2. révoquer les autorisations de débogage USB sur le téléphone, puis relancer `adb devices` : la popup d'autorisation doit réapparaître ;
3. `adb kill-server && adb -a start-server` ;
4. si `lsusb` voit le téléphone mais que `adb devices` reste vide : redémarrer le téléphone, ce qui résout le problème dans la plupart des cas.

## Tester avec une base complète

L'app debug télécharge la base publiée, comme la release. Pour tester une base qui n'est pas encore publiée (modification du script d'export, nouvelle donnée), il faut la générer localement puis la pousser sur le téléphone.

### Générer une base complète locale

Les paramètres d'export (URI MongoDB, chemin Calibre, virtual library) sont lus dans `scripts/.env` (modèle : `scripts/.env.example`).

```bash
python scripts/export_mongo_to_sqlite.py --force --output /tmp/lmelp_complete.db
```

`LMELP_CALIBRE_DB` doit être renseigné dans `scripts/.env` : sans Calibre, `calibre_in_library` et `calibre_lu` valent 0 pour tous les livres, le filtre « Lus » est vide et aucun ✓ n'est affiché. `LMELP_CALIBRE_DB` doit désigner le `metadata.db` d'une bibliothèque complète : l'export y lit aussi les couvertures des livres.

Les tests Python qui ont besoin de volume réel (recherche plein texte, recommandations SVD) utilisent cette base via `LMELP_DB_PATH` :

```bash
LMELP_DB_PATH=/tmp/lmelp_complete.db pytest
```

### Pousser une base sur le téléphone

Le conteneur `lmelp-export` (stack `docker-lmelp`) exporte la base et la pousse directement dans l'app debug d'un téléphone branché au laptop, puis relance l'app :

```bash
# Sur le laptop, téléphone branché en USB
adb -a start-server                          # -a obligatoire : le daemon écoute sur 0.0.0.0
docker exec lmelp-export export-and-push
```

La commande vise `com.lmelp.mobile.debug` (variable `APP_PACKAGE` du conteneur) : elle remplace la base avec `adb shell run-as`, qui n'est autorisé que sur une app debuggable. La version release n'est pas concernée.

L'image `ghcr.io/castorfou/lmelp-mobile-export` est construite par `.github/workflows/build-export-image.yml`. Sur `main`, le workflow appelle ensuite l'API Watchtower du NAS, qui redémarre `lmelp-export` sur la nouvelle image (secret `WATCHTOWER_TOKEN`). Le service est défini dans le dépôt [castorfou/docker-lmelp](https://github.com/castorfou/docker-lmelp).

## Extrait minimal embarqué

`app/src/main/assets/lmelp.db` est un extrait réel minimal (les 3 émissions les plus anciennes), pas la base complète (voir l'[ADR 0002](adr/0002-distribution-app-mini-db-embarquee.md)) : l'app télécharge la vraie base, et un extrait minimal rend visible toute panne de ce téléchargement.

Pour le régénérer à partir d'une base complète :

```bash
python scripts/build_mini_db.py --source /tmp/lmelp_complete.db --output app/src/main/assets/lmelp.db --nb-emissions 3
```

!!! warning "Ne jamais exporter la base complète dans les assets"
    `python scripts/export_mongo_to_sqlite.py --force --output app/src/main/assets/lmelp.db` produirait une base complète : le test `TestTailleMinimale` (`tests/test_lmelp_db_integrity.py`) échoue. Toujours passer par `build_mini_db.py`.

Room ne copie l'asset qu'à la première ouverture de la base. Après une régénération sans changement de version Room, `deploy.sh` (`adb install -r`) ne suffit pas : désinstaller l'app debug d'abord.

```bash
~/android-sdk/platform-tools/adb uninstall com.lmelp.mobile.debug
scripts/build.sh && scripts/deploy.sh
```

## Cache des couvertures (Coil)

Les couvertures sont chargées depuis `url_cover` et mises en cache par Coil dans `getExternalFilesDir()/coil_image_cache` (25 MB maximum).

Android Backup sauvegarde ce cache sur Google Drive et le restaure à la réinstallation. En usage normal, Android fait cette sauvegarde automatiquement, environ toutes les 24 h, téléphone en charge et en Wi-Fi.

En développement, forcer la sauvegarde avant de désinstaller l'app debug pour ne pas perdre les images :

```bash
ADB=~/android-sdk/platform-tools/adb
$ADB shell "bmgr backupnow com.lmelp.mobile.debug"
$ADB uninstall com.lmelp.mobile.debug
scripts/build.sh && scripts/deploy.sh      # les images sont restaurées depuis Google Drive
```

Nombre d'images en cache :

```bash
~/android-sdk/platform-tools/adb shell "ls /sdcard/Android/data/com.lmelp.mobile.debug/files/coil_image_cache/*.1 2>/dev/null | wc -l"
```

## Environnement Python

Le venv de travail est `/tmp/venv`. Les dépendances sont déclarées dans `pyproject.toml` et figées dans `uv.lock` :

```bash
uv sync --active --all-extras     # installer / mettre à jour le venv
uv lock                           # après une modification de pyproject.toml (commiter uv.lock)
```
