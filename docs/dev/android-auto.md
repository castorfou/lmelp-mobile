# Android Auto

L'app lmelp-mobile est compatible **Android Auto** via la [Car App Library](https://developer.android.com/cars/develop/car-app-library).
L'interface voiture est entièrement séparée de l'app téléphone — aucun fichier UI existant n'est modifié.

## Architecture

```
LmelpCarAppService  (CarAppService)
  └── LmelpSession  (Session)
        └── MainCarScreen  (ListTemplate — menu 5 entrées)
              ├── AccueilCarScreen        (MessageTemplate — stats)
              ├── EmissionsCarScreen      (ListTemplate — 6 dernières émissions)
              ├── PalmaresCarScreen       (ListTemplate — top 6 livres)
              ├── RecommendationsCarScreen (ListTemplate — top 6 conseils)
              └── SearchCarScreen         (SearchTemplate — à l'arrêt)
```

Tout le code est dans `app/src/main/java/com/lmelp/mobile/ui/auto/`.

Le `CarScreenBuilder` contient la logique de construction des items (Kotlin pur, testable sans Android).

## Contraintes Car App Library

- **MAX 6 items** par liste (limite sécurité conducteur)
- **SearchTemplate** accessible uniquement à l'arrêt (ParkedOnly automatique)
- Les Screens chargent les données via coroutine + `invalidate()` pour rafraîchir l'affichage

## Tester avec l'AVD Automotive

### 1. Installer Android Studio sur le laptop

```bash
sudo snap install android-studio --classic
android-studio
```

Au premier lancement, Android Studio télécharge le SDK (~1-2 Go).

### 2. Créer un AVD Automotive

1. Écran d'accueil → **More Actions → Virtual Device Manager**
2. **Create Virtual Device**
3. Form Factor : **Automotive** → choisir **"Automotive 1408p landscape"**
4. System image : **Android Automotive with Google APIs x86_64, API 35** → Download si absent
5. **Finish** → **▶ Play** pour démarrer l'AVD

### 3. Compiler l'APK (depuis le devcontainer)

```bash
./gradlew assembleDebug
# APK généré : app/build/outputs/apk/debug/app-debug.apk
```

### 4. Installer sur l'AVD (depuis le laptop)

```bash
# Vérifier que l'AVD est détecté
adb devices
# Doit afficher : emulator-5554   device

# Installer (depuis le dossier lmelp-mobile sur le laptop)
adb -s emulator-5554 install app/build/outputs/apk/debug/app-debug.apk
# Ou pour une mise à jour :
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

L'app apparaît dans la barre d'apps de l'émulateur voiture.

## Pourquoi l'app n'apparaît pas dans la voiture (APK debug)

**Les APK installés via `adb` (debug) ne sont pas visibles dans le lanceur Android Auto embarqué** (Renault, etc.). C'est une restriction système — le lanceur hardware ne reconnaît que les apps certifiées depuis le Google Play Store.

Pour tester en développement → utiliser l'AVD Automotive (voir ci-dessus).
Pour apparaître dans la vraie voiture → publier sur le Play Store (voir section ci-dessous).

## Catégorie Car App Library

La catégorie déclarée dans `AndroidManifest.xml` est **`androidx.car.app.category.IOT`** — la catégorie correcte pour une app de contenu/templates génériques.

| Catégorie | Usage |
|-----------|-------|
| `NAVIGATION` | Apps GPS/navigation |
| `POI` | Apps points d'intérêt (maps) — **ne pas utiliser pour du contenu** |
| `IOT` | Apps template générales (listes, contenu) ← **notre cas** |

## Checklist manifest Android Auto

```xml
<!-- Requis pour la distribution Android Auto -->
<uses-feature android:name="android.software.car.templates.host" android:required="false"/>
<uses-feature android:name="android.hardware.type.automotive" android:required="false"/>

<!-- Service avec la bonne catégorie -->
<service android:name=".ui.auto.LmelpCarAppService" android:exported="true">
    <intent-filter>
        <action android:name="androidx.car.app.CarAppService"/>
        <category android:name="androidx.car.app.category.IOT"/>
    </intent-filter>
</service>

<!-- Dans <application>, pas dans <service> -->
<meta-data android:name="androidx.car.app.minCarApiLevel" android:value="1"/>
<meta-data android:name="com.google.android.gms.car.application" android:resource="@xml/automotive_app_desc"/>
```

## Publication Play Store (pour apparaître dans la vraie voiture)

1. **Play Console → Test and release → Advanced settings → Form factors**
   - Ajouter **Android Auto**
   - Ajouter **Android Automotive OS** + accepter la review policy
2. Ajouter des screenshots 1024×768 landscape pour la fiche Play Store
3. Soumettre à l'open testing ou production → **review manuelle Google (~7-14 jours)**

## Notes

- La recherche vocale n'est pas disponible dans la Car App Library (SearchTemplate = clavier uniquement, à l'arrêt)
- `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` est utilisé — acceptable pour usage privé hors Play Store
