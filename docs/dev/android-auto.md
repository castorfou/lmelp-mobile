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

## Tester avec le Desktop Head Unit (DHU)

L'AVD Automotive ci-dessus teste **Android Automotive OS** (Android installé dans la voiture). Ce n'est pas **Android Auto** (le téléphone projeté sur l'écran de la voiture). Pour Android Auto, on utilise le DHU, qui se connecte au vrai téléphone. Il tourne dans le devcontainer, même sans écran.

**Côté téléphone (une fois)** : application Android Auto, appuyer 10 fois sur **Version** (mode développeur), puis menu ⋮ → **Démarrer le serveur de l'unité principale**.

**Côté devcontainer** :

```bash
yes | ~/android-sdk/cmdline-tools/latest/bin/sdkmanager "extras;google;auto"
sudo apt-get install -y xvfb imagemagick xdotool libc++1 libc++abi1
Xvfb :99 -screen 0 1920x1080x24 &
adb forward tcp:5277 tcp:5277
# Le DHU quitte dès que son entrée standard se ferme : le brancher sur un fifo gardé ouvert
mkfifo /tmp/dhu.in && (sleep infinity > /tmp/dhu.in &)
cd ~/android-sdk/extras/google/auto && DISPLAY=:99 ./desktop-head-unit < /tmp/dhu.in &
DISPLAY=:99 import -window root dhu.png               # capture d'écran
DISPLAY=:99 xdotool mousemove 603 740 click 1         # ouvre le lanceur d'applis
adb logcat -d | grep "CarApp.H.Tem: Error"            # erreurs côté Android Auto
```

⚠️ Le devcontainer partage le réseau du laptop : un `adb` lancé dans le conteneur alors qu'aucun serveur n'écoute sur 5037 démarre son propre serveur, et le `adb -a start-server` du laptop ne peut plus démarrer (`adb kill-server` dans le conteneur pour libérer le port).

## `minCarApiLevel` : dans `<application>`, sous ce nom exact (issue #99)

```xml
<application ...>
    <meta-data android:name="androidx.car.app.minCarApiLevel" android:value="1"/>
</application>
```

`AppInfo.create()` (Car App Library) ne lit cette clé que dans les meta-data de `<application>`. Absente (ancienne déclaration `androidx.car.app.minApiLevel` dans `<service>`), Android Auto **affiche** l'appli dans son lanceur mais refuse de l'**ouvrir** : « Le Masque et la Plume has encountered an unexpected error », avec dans logcat `Min API level not declared in manifest (androidx.car.app.minCarApiLevel)`. `CarAppManifestTest` bloque la régression.

Constaté avec le DHU (Android Auto 18.1, Pixel 9 Pro) : l'appli apparaît dans le lanceur avec la catégorie `POI`, installée hors Play Store, sans cocher « Sources inconnues ». Le DHU ne dit rien du filtrage en voiture réelle, qui reste à vérifier.

## Notes

- La recherche vocale n'est pas disponible dans la Car App Library (SearchTemplate = clavier uniquement, à l'arrêt)
- Pour tester en voiture réelle : brancher le téléphone avec l'APK installé à une voiture Android Auto compatible
- `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` est utilisé — acceptable pour usage privé hors Play Store
