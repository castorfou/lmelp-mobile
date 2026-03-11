# Issue #44 — Recherche : cacher le clavier au lancement de la recherche

## Problème

Quand l'utilisateur tape une requête dans la SearchBar et valide avec la touche "Rechercher" du clavier Android, le clavier restait affiché et masquait les résultats de recherche.

## Cause

Dans `app/src/main/java/com/lmelp/mobile/ui/search/SearchScreen.kt`, le callback `onSearch` de la `SearchBar` était vide (`onSearch = {}`), donc aucune action n'était déclenchée lors de la validation.

## Fix

Utilisation de `LocalSoftwareKeyboardController` (API Compose standard) pour cacher le clavier dans `onSearch`.

**Fichier modifié** : `app/src/main/java/com/lmelp/mobile/ui/search/SearchScreen.kt`

Ajout de l'import :
```kotlin
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
```

Dans `SearchContent` :
```kotlin
val keyboardController = LocalSoftwareKeyboardController.current
// ...
onSearch = { keyboardController?.hide() },
```

## Pattern retenu

`LocalSoftwareKeyboardController.current` est la méthode recommandée dans Jetpack Compose pour cacher le clavier programmatiquement. Elle doit être appelée au niveau Composable (pas dans un lambda), puis utilisée dans les callbacks.

## Tests

Changement purement UI — pas de logique ViewModel testable unitairement. Vérification par build (`./gradlew assembleDebug`) et test manuel.
