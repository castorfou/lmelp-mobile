# Identité visuelle — lmelp-mobile

Guide de référence pour les développements UI futurs.

## Inspiration

S'inspirer du site officiel **Le Masque et la Plume** (Radio France) sans le copier :
- [Page officielle](https://www.radiofrance.fr/franceinter/podcasts/le-masque-et-la-plume)
- Image de référence : `ui/sketchs/400x400_sc_le-masque-et-la-plume-rm.jpg`

## Palette de couleurs

### Bandeau hero (HomeScreen)

| Rôle | Couleur | Hex |
|------|---------|-----|
| Fond haut | Bleu nuit profond | `#12192C` |
| Fond bas (gradient) | Bleu nuit clair | `#1E2D4A` |
| Texte principal | Blanc | `#FFFFFF` |
| Texte secondaire | Blanc 75% | `#FFFFFF` à 0.75 alpha |

### Tuiles de navigation

Trois couleurs issues de l'image officielle du podcast :

| Nom | Hex | Source | Usage |
|-----|-----|--------|-------|
| Bleu | `#1565C0` | Bleu institutionnel | Émissions |
| Bordeaux | `#A10127` | Carré France Inter | Conseils, Critiques |
| Vert | `#00897B` | Fond derrière Rebecca Manzoni | Palmarès, Recherche |

### Règle d'adjacence des tuiles

Deux tuiles adjacentes ne doivent jamais avoir la même couleur (théorème des 4 couleurs — 3 suffisent ici).

Disposition actuelle validée :

```
┌─────────────────┬──────────────┐
│                 │  Palmarès    │
│   Émissions     │  (vert)      │
│   (bleu)        ├──────────────┤
│                 │  Conseils    │
│                 │  (bordeaux)  │
├──────────┬──────┴──────────────┤
│ Critiques│     Recherche       │
│(bordeaux)│     (vert)          │
└──────────┴─────────────────────┘
```

### Fond des screens

| Zone | Couleur |
|------|---------|
| HomeScreen — hero (status bar incluse) | Dégradé `#12192C` → `#1E2D4A` |
| HomeScreen — grille de navigation | Blanc (`Color.White`) |
| Autres screens — TopAppBar + status bar | Couleur de la tuile correspondante |
| Autres screens — contenu sous le bandeau | Blanc (`Color.White`) |

## Implémentation Compose — status bar colorée par écran

### Règle principale

Le `Scaffold` racine dans `MainActivity` doit avoir `contentWindowInsets = WindowInsets(0)` pour ne pas consommer les insets avant les screens imbriqués.

Chaque screen secondaire utilise :
```kotlin
Scaffold(
    contentWindowInsets = WindowInsets(0),
    topBar = {
        TopAppBar(
            colors = TopAppBarDefaults.topAppBarColors(containerColor = LmelpXxx),
            windowInsets = WindowInsets.statusBars  // colore la status bar
        )
    }
)
```

### HomeScreen — hero continu derrière la status bar

Box extérieure avec background (couvre status bar), Box intérieure avec `statusBarsPadding()` (décale le contenu) :
```kotlin
Box(modifier = modifier.background(Brush.verticalGradient(...))) {
    Box(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(16.dp)) {
        // contenu
    }
}
```

### Prérequis

- `enableEdgeToEdge()` dans `MainActivity.onCreate()`
- PAS de `android:statusBarColor` fixe dans `themes.xml`

## Typographie

- Titre principal : `FontWeight.Bold`, `20.sp`, blanc
- Sous-titre : `11.sp`, blanc à 0.75 alpha, `maxLines = 1`
- Labels tuiles : `FontWeight.Medium`, `13.sp`, blanc, `TextAlign.Center`

## Image de marque

- Fichier : `app/src/main/res/drawable/masque_et_la_plume.jpg`
- Source : Radio France (usage personnel uniquement — demander autorisation avant publication sur Play Store)
- Affichage : `110.dp` x `110.dp`, `ContentScale.Fit`

## Structure de la HomeScreen

- **Pas de bottom nav** sur la HomeScreen — c'est le hub de navigation
- **Bottom nav sur toutes les autres pages** (y compris Critiques et About)
- **Engrenage** (Settings icon) en haut à droite du hero → AboutScreen
- **Tuiles asymétriques** : Émissions grande (weight 2f), autres normales

## Icônes utilisées (Material Icons core)

| Section | Icône |
|---------|-------|
| Accueil | `Icons.Default.Home` |
| Émissions | `Icons.AutoMirrored.Filled.List` |
| Palmarès | `Icons.Default.Star` |
| Conseils | `Icons.Default.Person` |
| Recherche | `Icons.Default.Search` |
| Critiques | `Icons.AutoMirrored.Filled.List` |
| Paramètres | `Icons.Default.Settings` |

Note : seuls les icônes du module `material-icons-core` sont disponibles (pas `material-icons-extended`).
