# Issue #14 — Masquer les labels en mode paysage (barre de navigation)

## Problème
En mode paysage (landscape), les labels des onglets de la `NavigationBar` ("Émissions", "Palmarès", "Conseils", "Recherche") étaient coupés faute de place verticale.

## Solution
Masquer les labels (afficher uniquement les icônes) en mode paysage.

## Fichiers créés/modifiés
- `app/src/main/java/com/lmelp/mobile/NavigationUtils.kt` — fonction pure `shouldShowLabel(isLandscape: Boolean): Boolean`
- `app/src/main/java/com/lmelp/mobile/MainActivity.kt` — détection orientation + label conditionnel
- `app/src/test/java/com/lmelp/mobile/NavigationLabelTest.kt` — tests TDD (2 tests)

## Détails d'implémentation

### NavigationUtils.kt
```kotlin
fun shouldShowLabel(isLandscape: Boolean): Boolean = !isLandscape
```

### MainActivity.kt — détection orientation
```kotlin
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration

val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
```

### MainActivity.kt — label conditionnel dans NavigationBarItem
```kotlin
label = if (shouldShowLabel(isLandscape)) { { Text(item.label) } } else null
```

## Pattern TDD appliqué
1. Test RED écrit en premier → compilation échoue (fonction inexistante)
2. Implémentation de `shouldShowLabel` → tests passent (GREEN)
3. Lint + build debug → OK

## Pattern de test (JUnit 4 pur, sans Android runner)
Identique à `NoteColorTest.kt` — tests sur logique pure sans dépendance Compose/Android.
