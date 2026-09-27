# Barre de navigation du bas façon WhatsApp — Issue #142

**Date** : 2026-09-27
**Branche** : `142-changer-la-barre-de-navigation-du-bas-en-sinspirant-de-celle-de-whatsapp`
**Issue** : [#142 Changer la barre de navigation du bas en s'inspirant de celle de whatsapp](https://github.com/castorfou/lmelp-mobile/issues/142)
**Spécifications** : [commentaire de l'issue](https://github.com/castorfou/lmelp-mobile/issues/142#issuecomment-5855623815)

## Problème

La barre du bas était une `NavigationBar` Material 3 presque par défaut :

- fond `surfaceContainer`, légèrement violet ;
- même icône, que l'onglet soit sélectionné ou non ;
- indicateur de sélection dans la teinte **vive** de l'onglet (`LmelpBleuVif`…, issue #138).

L'utilisateur voulait le style de WhatsApp :

- icône **contour** au repos, **pleine** quand l'onglet est sélectionné ;
- pastille de sélection très claire, avec l'icône dans une version très foncée de la même couleur ;
- label en gras quand l'onglet est sélectionné.

## Choix validés avec l'utilisateur

- **Une couleur par onglet**, dérivée des couleurs dominantes de #138 : Émissions bleu (`LmelpBleu`), Palmarès et Recherche vert (`LmelpVert`), Conseils bordeaux (`LmelpBordeaux`). On ne prend pas une couleur unique comme WhatsApp.
- **Nouveau jeu d'icônes**, avec de vraies paires plein/contour :
  - Accueil : `Outlined.Home` → `Filled.Home` ;
  - Émissions : `Outlined.MicNone` → `Filled.Mic` ;
  - Palmarès : `Outlined.StarOutline` → `Filled.Star` ;
  - Conseils : `Outlined.Lightbulb` → `Filled.Lightbulb` ;
  - Recherche : `Outlined.Search` → `LoupePleine`, une icône dessinée à la main.
- **Label** : noir, en gras quand l'onglet est sélectionné.
- **Fond** : blanc, avec un `HorizontalDivider` de 0.5dp en haut.

## Implémentation

### Nouveau fichier `app/src/main/java/com/lmelp/mobile/LmelpBottomBar.kt`

La barre sort de `MainActivity.kt` :

- **`BottomNavItem(label, route, selectedIcon, unselectedIcon, accentColor)`**, avec `icon(selected)`. Il remplace l'ancien `BottomNavItem(label, route, icon, indicatorColor)` qui était déclaré dans `MainActivity.kt`.
- **`bottomNavItems`** : liste top-level, testable. `MainActivity` en dérive désormais `swipeRoutes = bottomNavItems.map { it.route }`, au lieu d'une liste dupliquée.
- **`LoupePleine`** : `ImageVector` construit avec `ImageVector.Builder` et `addPathNodes(...)`. C'est le contour extérieur de `Filled.Search`, sans son creux intérieur. Le reflet est un croissant (rayons 3 à 4.2 autour du centre du verre 9.5,9.5, de 195° à 255°, extrémités arrondies), percé grâce à `pathFillType = PathFillType.EvenOdd`.
- **`LmelpBottomBar(currentRoute, showLabels, onItemClick)`** :
  - `Column { HorizontalDivider ; NavigationBar(containerColor = surface, tonalElevation = 0.dp) }` ;
  - `NavigationBarItemDefaults.colors(indicatorColor, selectedIconColor, selectedTextColor/unselected* = onSurface)`.
- **Inchangé** : `shouldShowLabel` (issue #14, labels masqués en paysage), et la logique de navigation au clic, passée en lambda depuis `MainActivity`.

### Couleurs dérivées dans `app/src/main/java/com/lmelp/mobile/ui/theme/Theme.kt`

- **`navIndicatorColor(accent)`** = `Color.hsl(teinte, 0.8, 0.88)`.
- **`navSelectedIconColor(accent)`** = `Color.hsl(teinte, 0.75, 0.25)`.
- **`teinteHsl(color)`** : fonction privée qui calcule la teinte en degrés.
- **Onglet sans accent** (Accueil) : saturation 0, donc gris clair ou gris foncé.

Valeurs obtenues :

| Onglet | Pastille | Icône |
|---|---|---|
| Émissions | `#C8DFF9` | `#103D70` |
| Conseils | `#F9C8D4` | `#701027` |
| Palmarès, Recherche | `#C8F9F4` | `#107066` |

`LmelpBleuVif`, `LmelpBordeauxVif` et `LmelpVertVif` ne servent plus à la barre du bas. Ils restent utilisés pour les dégradés de TopAppBar (#138).

## Pièges rencontrés lors du test sur device (3 retours utilisateur)

1. **`lerp(accent, Color.White, x)` désature.** `androidx.compose.ui.graphics.lerp` interpole dans l'espace **Oklab**, et la chroma y est réduite en proportion. Première version : `lerp(..., White, 0.85-0.88)`. Sur le téléphone, la pastille paraissait **grise**, sans la teinte de la page. **Correction** : passer par `Color.hsl(teinte, saturation imposée, luminosité)`.
2. **`lerp(accent, Color.Black, 0.55)` donne une couleur quasi noire.** La luminance tombe à environ 0.01, et l'utilisateur croyait que la couleur de sélection n'était pas appliquée à l'icône. Le premier test exigeait lui-même `luminance < 0.05` : il encodait la mauvaise cible. **Nouvelle cible** : luminance entre 0.02 et 0.15, et saturation HSL ≥ 0.6.
3. **`Icons.Outlined.Mic` est dessiné plein**, exactement comme `Icons.Filled.Mic`. L'icône creuse est `Icons.Outlined.MicNone`. Le test « vraie paire » comparait les instances d'`ImageVector` (`assertNotEquals`) et passait donc à tort. Même comparer les `pathData` ne suffit pas : les nœuds diffèrent alors que le dessin est le même. **Critère fiable** : une icône contour a **plus de sous-tracés** (nombre de `MoveTo`) que sa version pleine, à cause du contour intérieur du creux. Mesures relevées avec une sonde : `Filled.Mic` 2, `Outlined.Mic` 2, `Outlined.MicNone` 3 ; `Filled.Home` 1, `Outlined.Home` 2 ; `Filled.Star` 1, `StarOutline` 2 ; `Filled.Lightbulb` 2, `Outlined.Lightbulb` 3.
4. **Loupe** : `Filled.Search` et `Outlined.Search` sont identiques, avec un verre creux. Aucune icône Material n'a un verre plein. Sur demande de l'utilisateur, la loupe est dessinée à la main (`LoupePleine`), avec un reflet en croissant dans l'angle haut-gauche.

## Tests

`app/src/test/java/com/lmelp/mobile/BottomNavStyleTest.kt` : 11 tests en JUnit pur. `Color.luminance()`, `Color.hsl()` et `ImageVector.root` fonctionnent en test JVM, sans Robolectric.

- `icon(true)` et `icon(false)` renvoient la bonne icône.
- Vraie paire plein/contour pour les 4 onglets hors Recherche, vérifiée par le nombre de sous-tracés.
- La loupe sélectionnée est `LoupePleine`, avec un chemin `EvenOdd` et 2 sous-tracés (verre plus reflet).
- L'ordre des onglets est celle du swipe.
- Chaque onglet a l'accent de son écran.
- Pastille : luminance > 0.6 et saturation HSL ≥ 0.6.
- Icône : luminance entre 0.02 et 0.15, et saturation ≥ 0.6.
- La teinte dominante (composante R, V ou B) est conservée.
- Contraste WCAG entre icône et pastille ≥ 4.5.
- Sans accent, les couleurs restent neutres et lisibles.

Bilan de la suite : 298 tests unitaires, tous verts. Lint : 72 avertissements, comme sur `main` ; aucun nouveau.

## Fichiers modifiés

- `app/src/main/java/com/lmelp/mobile/LmelpBottomBar.kt` (nouveau)
- `app/src/main/java/com/lmelp/mobile/MainActivity.kt` : bloc `NavigationBar` remplacé par `LmelpBottomBar(...)`, ancienne `BottomNavItem` et imports supprimés, `swipeRoutes` dérivé de `bottomNavItems`
- `app/src/main/java/com/lmelp/mobile/ui/theme/Theme.kt` : `navIndicatorColor`, `navSelectedIconColor`, `teinteHsl`
- `app/src/test/java/com/lmelp/mobile/BottomNavStyleTest.kt` (nouveau)
- `docs/visual-identity.md` : section sur la barre de navigation

## Divers

`gh issue view 142`, sans `--json`, n'affichait rien du tout et sortait avec le code 0. `gh issue view 142 --json number,title,body,comments` fonctionne.
