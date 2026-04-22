# Issue #95 — Compatibilité Android Auto

## Contexte
Rendre l'app lmelp-mobile utilisable dans la voiture via Android Auto.
Aucun changement sur l'app téléphone — tout le code Auto est isolé.

## Architecture implémentée

### Stack technique
- Dépendance : `androidx.car.app:app:1.4.0` (ajoutée dans `gradle/libs.versions.toml` et `app/build.gradle.kts`)
- Catégorie manifest : `androidx.car.app.category.POI` (pas media — app de catalogue)
- `car_app_minApiLevel = 1`

### Chaîne d'appel
```
AndroidManifest.xml (service LmelpCarAppService)
  → LmelpCarAppService (CarAppService)
  → LmelpSession (Session)
  → MainCarScreen (Screen)
     ├── AccueilCarScreen    (MessageTemplate)
     ├── EmissionsCarScreen  (ListTemplate)
     ├── PalmaresCarScreen   (ListTemplate)
     ├── RecommendationsCarScreen (ListTemplate)
     └── SearchCarScreen     (SearchTemplate)
```

### Fichiers nouveaux
Tous dans `app/src/main/java/com/lmelp/mobile/ui/auto/` :
- `CarScreenBuilder.kt` — logique pure Kotlin testable sans Android (MAX_ITEMS=6)
- `LmelpCarAppService.kt`
- `LmelpSession.kt`
- `MainCarScreen.kt`
- `AccueilCarScreen.kt` — utilise `metadataRepository.getDbInfo()` pour nb émissions/livres
- `EmissionsCarScreen.kt`
- `PalmaresCarScreen.kt`
- `RecommendationsCarScreen.kt`
- `SearchCarScreen.kt` — SearchTemplate avec SearchCallback, min 2 chars

### Fichiers modifiés (ajouts seulement)
- `app/src/main/AndroidManifest.xml` — `<service>` + `uses-feature` + `<meta-data>`
- `app/src/main/res/xml/automotive_app_desc.xml` — `<automotiveApp><uses name="template"/>`
- `gradle/libs.versions.toml` — version `carApp = "1.4.0"` + lib `androidx-car-app`
- `app/build.gradle.kts` — `implementation(libs.androidx.car.app)`

## Pattern clé : CarScreenBuilder
Séparer la logique de construction des items (pure Kotlin) des classes Screen (Android).
Permet les tests unitaires JVM sans émulateur Android.
`MAX_ITEMS = 6` — limite sécurité conducteur imposée par la Car App Library.

## Tests
`app/src/test/java/com/lmelp/mobile/AndroidAutoScreenTest.kt` — 14 tests unitaires.
Approche TDD : CarScreenBuilder testé en isolation (mock-free, JVM only).

## Workflow de test avec AVD Automotive
1. Android Studio → More Actions → Virtual Device Manager
2. AVD : Automotive 1408p landscape, API 35 (Android 15 VanillaIceCream)
3. Builder l'APK dans le devcontainer : `./gradlew assembleDebug`
4. Installer depuis le laptop : `adb -s emulator-5554 install app/build/outputs/apk/debug/app-debug.apk`
5. L'app apparaît dans la barre d'apps de l'émulateur voiture

## Contraintes Car App Library
- MAX 6 items sans scroll dans un ListTemplate
- SearchTemplate : accessible uniquement à l'arrêt (ParkedOnly automatique)
- `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` en dev (à restreindre en prod si publication Play Store)
- Coroutines dans les Screen : utiliser `CoroutineScope(Dispatchers.Main)` + `invalidate()` après chargement

## Points d'attention futurs
- Si publication sur Play Store : vérifier la politique Google Play pour les apps Car App catégorie POI
- `HostValidator.ALLOW_ALL_HOSTS_VALIDATOR` est permissif — acceptable pour usage privé
- La recherche vocale native n'est pas disponible dans la Car App Library (SearchTemplate = clavier uniquement)
