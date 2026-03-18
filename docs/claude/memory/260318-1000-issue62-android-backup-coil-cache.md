---
name: issue62-android-backup-coil-cache
description: Android Backup pour préserver le cache Coil (couvertures) lors d'une désinstallation
type: project
---

# Issue #62 — Android Backup cache Coil

## Problème

`getExternalFilesDir()` est effacé par Android 11+ lors d'une désinstallation (protection
vie privée). Le cache Coil (`coil_image_cache`) stocké dans ce répertoire était donc perdu
à chaque désinstallation, forçant un re-téléchargement de toutes les couvertures.

Note : le commentaire dans `LmelpApp.kt` disait à tort que ce répertoire "survit aux
désinstallations" — c'était faux depuis Android 11+.

## Fix appliqué

Configuration Android Backup — 3 fichiers modifiés/créés :

**`app/src/main/res/xml/backup_rules.xml`** (nouveau, Android ≤11) :
```xml
<full-backup-content>
    <include domain="external" path="coil_image_cache" />
</full-backup-content>
```

**`app/src/main/res/xml/data_extraction_rules.xml`** (nouveau, Android 12+) :
```xml
<data-extraction-rules>
    <cloud-backup>
        <include domain="external" path="coil_image_cache" />
    </cloud-backup>
    <device-transfer>
        <include domain="external" path="coil_image_cache" />
    </device-transfer>
</data-extraction-rules>
```

**`app/src/main/AndroidManifest.xml`** : ajout de `android:dataExtractionRules` et
`android:fullBackupContent` dans `<application>`.

**`app/src/main/java/com/lmelp/mobile/LmelpApp.kt`** :
- `maxSizeBytes` réduit de 50 MB → 25 MB (limite Google Drive pour Android Backup)
- Commentaire corrigé

## Piège lint : domaine `externalFilesDir` invalide

Le domaine correct pour `getExternalFilesDir()` dans les règles de backup est `external`
(pas `externalFilesDir` comme suggéré dans l'issue). Le lint Android le signale avec
`[FullBackupContent]`.

**Why:** `externalFilesDir` n'existe pas comme valeur de domaine dans le schéma Android.
Les valeurs valides sont : `file`, `database`, `sharedpref`, `external`, `root`, etc.

## Workflow dev (documenté dans `docs/dev/build_deploy_apk.md`)

En usage normal : backup automatique toutes les ~24h (device en charge + WiFi). Rien à faire.

En dev, avant `adb uninstall` :
```bash
ADB=/home/vscode/android-sdk/platform-tools/adb
$ADB shell "bmgr backupnow com.lmelp.mobile"
$ADB uninstall com.lmelp.mobile
./gradlew installDebug
# Le cache est restauré automatiquement depuis Google Drive
```

## Test validé

- 29 images en cache avant désinstallation
- `bmgr backupnow` → backup ~1.5 MB sur Google Drive
- Désinstallation + réinstallation → 31 images restaurées ✓
