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

## Notes

- La recherche vocale n'est pas disponible dans la Car App Library (SearchTemplate = clavier uniquement, à l'arrêt)
- Pour tester en voiture réelle : brancher le téléphone avec l'APK installé à une voiture Android Auto compatible
- `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` est utilisé — acceptable pour usage privé hors Play Store
