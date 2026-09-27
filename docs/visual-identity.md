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

### Teintes « vives » (dégradés de bandeau, issue #138)

Chaque couleur dominante a une variante plus claire/vive, utilisée en fin de dégradé sur la TopAppBar de l'écran correspondant (la barre de navigation du bas n'utilise plus ces teintes depuis l'issue #142, voir [Barre de navigation du bas](#barre-de-navigation-du-bas)) :

| Nom | Hex | Couleur dominante associée |
|-----|-----|------------------------------|
| `LmelpBleuVif` | `#1E88E5` | Bleu (`LmelpBleu`) |
| `LmelpBordeauxVif` | `#D32F4B` | Bordeaux (`LmelpBordeaux`) |
| `LmelpVertVif` | `#26A69A` | Vert (`LmelpVert`) |

Définies dans `app/src/main/java/com/lmelp/mobile/ui/theme/Theme.kt`.

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
| Autres screens — TopAppBar + status bar | Dégradé vertical, couleur de la tuile → variante « vive » (issue #138) |
| Autres screens — contenu sous le bandeau | Blanc (`Color.White`) |
| Bottom nav — fond | Blanc, fin séparateur en haut (issue #142) |
| Bottom nav — onglet sélectionné | Pastille pastel + icône foncée dans la teinte de l'écran (issue #142) |

## Implémentation Compose — status bar colorée par écran

### Règle principale

Le `Scaffold` racine dans `MainActivity` doit avoir `contentWindowInsets = WindowInsets(0)` pour ne pas consommer les insets avant les screens imbriqués.

Chaque screen secondaire utilise un dégradé vertical (couleur dominante → variante « vive », issue #138) plutôt qu'une couleur unie :
```kotlin
Scaffold(
    contentWindowInsets = WindowInsets(0),
    topBar = {
        TopAppBar(
            title = { Text("Titre", color = Color.White) },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            windowInsets = WindowInsets.statusBars,  // colore la status bar
            modifier = Modifier.background(
                Brush.verticalGradient(colors = listOf(LmelpXxx, LmelpXxxVif))
            )
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

## Barre de navigation du bas

Inspirée de celle de WhatsApp (issue #142) :

- **Icône contour au repos, pleine quand l'onglet est sélectionné.**
- **Onglet sélectionné** : une pastille très claire et une icône foncée, toutes deux dans la teinte de l'écran.
- **Label** : noir, en gras quand l'onglet est sélectionné.
- **Fond** : blanc, avec un fin séparateur en haut.

| Onglet | Icône (repos → sélectionné) | Pastille | Icône sélectionnée |
|--------|-----------------------------|----------|--------------------|
| Émissions | `Outlined.MicNone` → `Filled.Mic` | `#C8DFF9` | `#103D70` |
| Palmarès | `Outlined.StarOutline` → `Filled.Star` | `#C8F9F4` | `#107066` |
| Conseils | `Outlined.Lightbulb` → `Filled.Lightbulb` | `#F9C8D4` | `#701027` |
| Recherche | `Outlined.Search` → `LoupePleine` | `#C8F9F4` | `#107066` |

L'onglet Accueil figure dans la liste, mais la barre est masquée sur la HomeScreen.

Les deux couleurs sont dérivées de la couleur dominante de l'onglet (`LmelpBleu`, `LmelpVert`, `LmelpBordeaux`) :

- `navIndicatorColor` = `Color.hsl(teinte, 0.8, 0.88)` ;
- `navSelectedIconColor` = `Color.hsl(teinte, 0.75, 0.25)`.

Ces deux fonctions sont dans `ui/theme/Theme.kt`, et la barre dans `LmelpBottomBar.kt`.

!!! warning "Pas de `lerp` vers blanc ou noir"
    `androidx.compose.ui.graphics.lerp` interpole dans l'espace Oklab, ce qui désature la couleur. Une pastille obtenue par `lerp(accent, White, 0.85)` paraît grise, et une icône obtenue par `lerp(accent, Black, 0.55)` paraît noire. Constaté sur device.

!!! warning "`Icons.Outlined.Mic` est dessiné plein"
    Il est identique à `Filled.Mic`. L'icône creuse est `Outlined.MicNone`. Pour vérifier qu'une paire plein/contour en est vraiment une, le test `BottomNavStyleTest` compare le nombre de sous-tracés : une icône contour en a un de plus, le contour intérieur du creux.

**`LoupePleine`** : `Filled.Search` et `Outlined.Search` sont identiques, avec un verre creux. La loupe sélectionnée est donc dessinée à la main :

- verre plein ;
- petit reflet en croissant dans l'angle haut-gauche, percé dans le verre par remplissage pair-impair (`PathFillType.EvenOdd`).

## Icône de lancement

![Icône de lancement](img/screenshot_lmelp-mobile_icon.png)

Le masque et la plume de l'icône lmelp historique, avec deux changements pour ne pas confondre l'app avec la PWA back-office « BO LMELP », dont l'icône a un fond rose et une base de données (issue #141) :

- le **fond bleu nuit** du bandeau d'accueil, en dégradé `#12192C` → `#1E2D4A` ;
- un **smartphone** (cadre `#E3E8F0`, écran bleu Émissions `#1565C0`) à la place de la base de données.

C'est une **icône adaptive** : le launcher la découpe à sa forme (cercle, squircle…), et elle remplit toute la pastille. Le motif tient dans la safe zone (disque central de 66 dp sur 108), donc aucun masque ne le rogne. Un calque monochrome, dont les détails sombres (yeux, bouche, écran) sont évidés, sert aux icônes thémées d'Android 13 et plus.

| Ressource | Rôle |
|-----------|------|
| `mipmap-anydpi/ic_launcher.xml`, `ic_launcher_round.xml` | Déclaration de l'icône adaptive |
| `drawable/ic_launcher_background.xml` | Fond : dégradé bleu nuit |
| `mipmap-*dpi/ic_launcher_foreground.png` | Premier plan : masque, plume et smartphone |
| `mipmap-*dpi/ic_launcher_monochrome.png` | Icône thémée |
| `mipmap-*dpi/ic_launcher.png`, `ic_launcher_round.png` | Icônes legacy |

### Régénérer l'icône

Toutes ces ressources sont produites par un script ; ne jamais éditer les PNG à la main :

```bash
python scripts/generate_launcher_icon.py
./gradlew clean assembleDebug   # clean : prise en compte des nouveaux dossiers de ressources
```

Le script part de `scripts/icon/source_lmelp_green.png`, l'icône lmelp verte du repo back-office-lmelp (`frontend/public/gimp_favicon/favicon.png`). Il détoure son fond vert et retire le liseré rose hérité de la version BO. Il ajoute ensuite le smartphone, recentre le motif dans la safe zone et exporte toutes les densités. Les couleurs et la position du téléphone sont des constantes en tête du script. `tests/test_launcher_icon.py` vérifie les ressources committées : fond bleu nuit et non rose, tailles, XML adaptive.

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
