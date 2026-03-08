# Issue #15 — Bleu nuit comme couleur de fond + thème visuel par écran

Date : 2026-03-08
Branche : `15-couleur-de-fond-utiliser-le-bleu-nuit-pour-la-couleur-de-fond-de-lappli`
Commits principaux : `8364cf6`, `8fc3e86`

## Objectif

Utiliser le bleu nuit (#12192C) comme couleur de fond principale de l'app, avec :
- HomeScreen : bandeau hero bleu nuit continu derrière la status bar, fond blanc pour la grille de navigation
- Autres screens (Émissions, Palmarès, Critiques, Conseils, Recherche) : bandeau coloré selon la couleur de la tuile correspondante sur HomeScreen, status bar de la même couleur, fond blanc sous le bandeau

## Couleurs définies dans Theme.kt

```kotlin
val LmelpNightBlue = Color(0xFF12192C)     // Hero HomeScreen
val LmelpNightBlueEnd = Color(0xFF1E2D4A)  // Fin du dégradé hero
val LmelpBleu = Color(0xFF1565C0)          // Émissions
val LmelpBordeaux = Color(0xFFA10127)      // Critiques, Conseils
val LmelpVert = Color(0xFF00897B)          // Palmarès, Recherche
```

Mode clair : `background = Color.White`, `surface = Color.White`, `onBackground/onSurface = Color(0xFF1A1A1A)`

## Pattern pour chaque screen (Émissions, Palmarès, Critiques, Recommandations, Recherche)

```kotlin
Scaffold(
    contentWindowInsets = WindowInsets(0),
    topBar = {
        TopAppBar(
            title = { Text("Titre", color = Color.White) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = LmelpXxx),
            windowInsets = WindowInsets.statusBars  // ← clé : colore la status bar
        )
    }
) { padding -> ... }
```

**Imports nécessaires :**
- `androidx.compose.foundation.layout.WindowInsets`
- `androidx.compose.foundation.layout.statusBars`

## Pattern HomeScreen (HeroSection)

Le fond night blue doit s'étendre derrière la status bar. Technique : Box extérieure avec background, Box intérieure avec statusBarsPadding() + padding pour le contenu.

```kotlin
Box(
    modifier = modifier.background(Brush.verticalGradient(...LmelpNightBlue, LmelpNightBlueEnd...))
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()   // décale le contenu, pas le fond
            .padding(16.dp)
    ) {
        // IconButton settings + Row avec image/titre
    }
}
```

**Import nécessaire :** `androidx.compose.foundation.layout.statusBarsPadding`

## MainActivity — clé critique

Sans `contentWindowInsets = WindowInsets(0)` sur le Scaffold racine, les WindowInsets de status bar sont consommés avant d'arriver aux screens imbriqués → la status bar reste blanche même avec `windowInsets = WindowInsets.statusBars` sur le TopAppBar.

```kotlin
// MainActivity.kt
Scaffold(
    contentWindowInsets = WindowInsets(0),  // ← INDISPENSABLE
    bottomBar = { ... }
) { innerPadding -> ... }
```

## themes.xml

Supprimer `android:statusBarColor` pour laisser Compose gérer dynamiquement.
Garder `android:navigationBarColor` (barre de navigation en bas).

```xml
<style name="Theme.LmelpMobile" parent="android:Theme.Material.Light.NoActionBar">
    <item name="android:navigationBarColor">@color/night_blue</item>
</style>
```

## Fichiers modifiés

- `app/src/main/java/com/lmelp/mobile/ui/theme/Theme.kt`
- `app/src/main/java/com/lmelp/mobile/MainActivity.kt`
- `app/src/main/java/com/lmelp/mobile/ui/home/HomeScreen.kt`
- `app/src/main/java/com/lmelp/mobile/ui/emissions/EmissionsScreen.kt`
- `app/src/main/java/com/lmelp/mobile/ui/palmares/PalmaresScreen.kt`
- `app/src/main/java/com/lmelp/mobile/ui/critiques/CritiquesScreen.kt`
- `app/src/main/java/com/lmelp/mobile/ui/recommendations/RecommendationsScreen.kt`
- `app/src/main/java/com/lmelp/mobile/ui/search/SearchScreen.kt`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/values/colors.xml` (nouveau)

## Erreurs à éviter

- Ne pas mettre `statusBarColor` fixe dans `themes.xml` — ça écrase la couleur dynamique de Compose
- Ne pas oublier `contentWindowInsets = WindowInsets(0)` sur le Scaffold racine dans MainActivity
- Ne pas mettre `statusBarsPadding()` sur le Box extérieur (fond) de HeroSection — seulement sur le Box intérieur (contenu)
- `enableEdgeToEdge()` doit être présent dans MainActivity.onCreate() pour que Compose puisse peindre derrière la status bar
