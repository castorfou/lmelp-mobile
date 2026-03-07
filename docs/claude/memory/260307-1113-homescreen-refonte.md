# 260307-1113 — Refonte HomeScreen (issue #9)

## Contexte

Remplacement du lancement direct sur EmissionsScreen par une vraie HomeScreen hub de navigation avec identité visuelle Le Masque et la Plume.

## Fichiers créés

- `app/src/main/java/com/lmelp/mobile/viewmodel/HomeViewModel.kt` — ViewModel + HomeUiState
- `app/src/main/java/com/lmelp/mobile/ui/home/HomeScreen.kt` — HomeScreen + HeroSection + NavTilesGrid
- `app/src/test/java/com/lmelp/mobile/HomeViewModelTest.kt` — tests TDD
- `docs/visual-identity.md` — guide identité visuelle complet
- `app/src/main/res/drawable/masque_et_la_plume.jpg` — image podcast Radio France

## Fichiers modifiés

- `app/src/main/java/com/lmelp/mobile/Navigation.kt` — startDestination=HOME, ajout routes HOME+ABOUT, ajout `Routes.ABOUT`
- `app/src/main/java/com/lmelp/mobile/MainActivity.kt` — bottom nav 5 items, HOME exclu de la nav, popUpTo(Routes.HOME)
- `gradle.properties` — `android.suppressUnsupportedCompileSdk=35`
- `.vscode/settings.json` — PATH scripts/, exclusion .venv/site, python interpreter
- `.gitignore` — `.vscode/settings.json` retiré de gitignore

## Architecture HomeScreen

### HomeUiState
```kotlin
data class HomeUiState(
    val isLoading: Boolean = false,
    val nbEmissions: String = "",
    val exportDate: String = "",
    val error: String? = null
)
```

### Composables
- `HomeScreen(repository, onNavigate, onSettingsClick)` — instancie le ViewModel
- `HomeContent(uiState, onNavigate, onSettingsClick)` — Column(Hero + Grid)
- `HeroSection(uiState, onSettingsClick)` — bandeau bleu nuit avec image podcast
- `NavTilesGrid(onNavigate)` — grille asymétrique 2 lignes
- `NavTileItem(tile, onClick)` — Card colorée + icône + label

## Palette de couleurs validée

| Nom | Hex | Usage |
|-----|-----|-------|
| Bleu nuit hero haut | `#12192C` | Fond HeroSection gradient haut |
| Bleu nuit hero bas | `#1E2D4A` | Fond HeroSection gradient bas |
| Bleu | `#1565C0` | Émissions, Recherche |
| Bordeaux | `#A10127` | Conseils, Critiques |
| Vert | `#00897B` | Palmarès |

## Disposition tuiles (règle d'adjacence validée)

```
┌─────────────────┬──────────────┐
│                 │  Palmarès    │
│   Émissions     │  (vert)      │
│   (bleu)        ├──────────────┤
│                 │  Conseils    │
│                 │  (bordeaux)  │
├──────────┬──────┴──────────────┤
│ Critiques│     Recherche       │
│(bordeaux)│     (bleu)          │
└──────────┴─────────────────────┘
```
Poids : Émissions weight=2f, Critiques weight=1f, Recherche weight=1.5f

## Logique bottom nav

- HOME exclu de la bottom nav (`routesWithoutBottomNav = setOf(Routes.HOME)`)
- Bottom nav visible sur TOUTES les autres pages (y compris Critiques et About)
- Navigation vers HOME : `popUpTo(Routes.HOME) { inclusive = true }` (pas restoreState)
- Navigation vers autres : `popUpTo(Routes.HOME) { saveState = true }; launchSingleTop; restoreState`

## Icônes utilisées (material-icons-core uniquement)

- `Icons.AutoMirrored.Filled.List` — Émissions, Critiques (remplace `Icons.Default.List` déprécié)
- `Icons.Default.Star` — Palmarès
- `Icons.Default.Person` — Conseils
- `Icons.Default.Search` — Recherche
- `Icons.Default.Settings` — Paramètres (AboutScreen)
- `Icons.Default.Home` — Accueil (bottom nav)

## Points d'attention / leçons

- `nbEmissions` est un `String` dans `DbInfoUi` — garder `String` dans `HomeUiState` aussi
- `Icons.Default.List` est déprécié → toujours utiliser `Icons.AutoMirrored.Filled.List`
- `TextAlign` import requis : `androidx.compose.ui.text.style.TextAlign`
- L'image Radio France (`masque_et_la_plume.jpg`) est pour usage personnel uniquement — demander autorisation avant Play Store
- `android.suppressUnsupportedCompileSdk=35` dans gradle.properties pour supprimer le warning AGP (issue #10 créée pour mettre à jour AGP)
- Renommer "Recommandations" → "Conseils" dans : HomeScreen.kt, MainActivity.kt, docs/visual-identity.md
- `.vscode/settings.json` doit être commité (retiré de .gitignore) pour partager la config PATH

## HeroSection layout

Image podcast 110dp à gauche + titre/sous-titre à droite + engrenage Settings en TopEnd.
Sous-titre : `fontSize=11.sp`, `maxLines=1` pour éviter le retour à la ligne sur mobile portrait.
