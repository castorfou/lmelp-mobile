# Issue #99 — Android Auto : l'app n'apparaît pas dans la voiture

## Contexte

L'app lmelp-mobile implémentait Android Auto via la Car App Library (`androidx.car.app:app:1.4.0`), fonctionnelle sur le DHU (émulateur), mais invisible dans le lanceur Android Auto de la Renault réelle.

## Causes identifiées

### 1. Mauvaise catégorie Car App Library
`app/src/main/AndroidManifest.xml` déclarait `androidx.car.app.category.POI` (Points of Interest — réservé aux apps de navigation/cartographie). La catégorie correcte pour une app de contenu/templates génériques est **`androidx.car.app.category.IOT`**.

### 2. `minApiLevel` mal placé et mal nommé
La meta-data était dans le bloc `<service>` avec le nom `androidx.car.app.minApiLevel`. Elle doit être dans `<application>` avec le nom **`androidx.car.app.minCarApiLevel`**.

### 3. Feature `android.hardware.type.automotive` manquante
La déclaration `<uses-feature android:name="android.hardware.type.automotive" android:required="false"/>` est requise pour la distribution Android Auto via Play Store.

## Corrections apportées

**`app/src/main/AndroidManifest.xml`** :
- `POI` → `IOT` dans la catégorie du service
- Ajout de `<uses-feature android:name="android.hardware.type.automotive" android:required="false"/>`
- Déplacement de la meta-data `minCarApiLevel` du `<service>` vers `<application>`
- Renommage `minApiLevel` → `minCarApiLevel`

**`app/src/main/java/com/lmelp/mobile/ui/auto/CarScreenBuilder.kt`** :
- Exposition de la constante `CAR_APP_CATEGORY = "androidx.car.app.category.IOT"` pour la testabilité

**`app/src/test/java/com/lmelp/mobile/AndroidAutoScreenTest.kt`** :
- Ajout du test `categorie Car App est IOT et non POI` vérifiant la constante

## Manifest final correct

```xml
<uses-feature android:name="android.software.car.templates.host" android:required="false"/>
<uses-feature android:name="android.hardware.type.automotive" android:required="false"/>

<service android:name=".ui.auto.LmelpCarAppService" android:exported="true">
    <intent-filter>
        <action android:name="androidx.car.app.CarAppService"/>
        <category android:name="androidx.car.app.category.IOT"/>
    </intent-filter>
</service>

<meta-data android:name="androidx.car.app.minCarApiLevel" android:value="1"/>
<meta-data android:name="com.google.android.gms.car.application" android:resource="@xml/automotive_app_desc"/>
```

## Apprentissage clé : APK debug invisible sur hardware réel

**Les APK installés via `adb` (debug) n'apparaissent PAS dans le lanceur Android Auto embarqué** (Renault, etc.). Ce n'est pas un bug de configuration — c'est une restriction système.

Pour tester en développement → DHU (Desktop Head Unit) : accepte les APK debug.
Pour apparaître dans la vraie voiture → **publication sur le Google Play Store** obligatoire (review manuelle ~7-14 jours).

## Checklist Play Store (pour publication future)

- [x] Catégorie `IOT` dans le service
- [x] `android.hardware.type.automotive` `required="false"`
- [x] `android.software.car.templates.host` `required="false"`
- [x] `minCarApiLevel` dans `<application>`
- [x] `automotive_app_desc.xml` avec `<uses name="template"/>`
- [ ] Play Console : activer form factors Android Auto + Android Automotive OS
- [ ] Screenshots 1024×768 landscape pour la fiche Play Store
- [ ] Soumettre à la review manuelle Google (open testing ou production)

## Catégories Car App Library valides

| Catégorie | Usage |
|-----------|-------|
| `NAVIGATION` | Apps GPS/navigation |
| `POI` | Apps points d'intérêt (maps) |
| `IOT` | Apps template générales (listes, contenu) ← **notre cas** |
