# Issue #143 — Grandes couvertures dans les listes d'œuvres

## Besoin

Dans les listes de livres, la couverture était une vignette de 48×72dp
(`BookCoverThumbnail`) dans une carte avec 12dp de marge intérieure. L'utilisateur
voulait le rendu de la carte Émission (refonte #139) : couverture **pleine
hauteur, collée au bord gauche** et clippée par les coins arrondis de la carte,
mais **sans** le fond flouté. La carte Material actuelle est conservée (fond,
textes, badges à droite). Le rang `#xx` disparaît dans Palmarès et Conseils.

## Périmètre retenu (validé avec l'utilisateur)

| Écran | Composable | Changement |
|---|---|---|
| Sur ma liseuse | `OnKindleCard` | nouvelle carte |
| Palmarès | `PalmaresCard` | nouvelle carte, `#rank` retiré |
| Palmarès, Mes lectures | `MonPalmaresCard` | nouvelle carte (placeholder pour les livres hors Masque) |
| Conseils | `RecommendationCard` | nouvelle carte, `#displayRank` retiré |
| Critique, Coups de cœur | `CoupDeCoeurCard` | nouvelle carte, couverture ajoutée côté données |
| Recherche | `SearchResultItem` | nouvelle carte si `type == "livre"`, autres types inchangés |
| Auteur, livres discutés au Masque | `LivreParAuteurCard` | nouvelle carte, couverture ajoutée côté données (cas oublié dans l'issue) |

Volontairement **inchangés** :
- `LivreCard` (détail d'une émission) : l'utilisateur considère que ce n'est pas
  une liste d'œuvres. `BookCoverThumbnail` est donc conservé pour elle.
- `HorsMasqueCard` (section « Lus hors Masque » de la page auteur, ex. Proust,
  *Du côté de chez Swann*) : ces livres viennent uniquement de Calibre (table
  `calibre_hors_masque`) et n'ont pas d'`url_cover`.

## Suite : issue #145

[#145](https://github.com/castorfou/lmelp-mobile/issues/145) est une issue à
part (qui mentionne #143, sans être une sous-issue, à la demande de
l'utilisateur). Elle porte sur les vignettes Calibre des livres hors Masque.
Calibre a une `cover.jpg` pour 997 livres sur 1000, alors que l'ISBN ne figure
que pour environ 1/3 d'entre eux. La piste : vignette en BLOB à l'export. Elle
implique un bump de `ROOM_VERSION` et la publication de `data-vN`, et le
container NAS doit monter le dossier Calibre complet, pas seulement
`metadata.db`.

## Implémentation

### Composant partagé

`app/src/main/java/com/lmelp/mobile/ui/components/CommonComponents.kt:98-160` :
- `BookListCardDefaults` : `CardHeight = 112.dp`, soit la hauteur de la carte
  Émission (120dp moins 2×4dp de padding vertical), et `CoverWidth = 75.dp`
  (ratio 2:3).
- `BookListCard(urlCover, modifier, content: RowScope.() -> Unit)` : une `Card`
  de hauteur fixe contenant un `Row`.
  - La couverture est un `AsyncImage` en `ContentScale.Crop`, sans padding ni
    arrondi propre : c'est la `Card` qui clippe.
  - Le contenu est placé dans un `Row(weight 1f, padding horizontal 12dp,
    spacedBy 10dp, CenterVertically)`.
  - Sans couverture, un bloc `surfaceVariant` avec l'icône
    `Icons.AutoMirrored.Filled.MenuBook` garde les textes alignés d'une carte à
    l'autre.
- L'appelant fournit le padding externe et le clic via `modifier`.
  `OnKindleCard` garde ainsi son `combinedClickable` (appui long vers la
  feuille « En cours de lecture »).

Hauteur fixe oblige, tous les titres passent en `maxLines = 2` et les auteurs en
`maxLines = 1`, avec `TextOverflow.Ellipsis`. Pour la recherche, le texte d'un
livre est limité à `maxLines = 3`.

### Couvertures manquantes côté données

Aucun bump de version Room : ce sont des projections de requête, pas des
`@Entity`.
- `app/src/main/java/com/lmelp/mobile/data/db/CritiquesDao.kt:16,31,34` :
  `LEFT JOIN livres l ON l.id = a.livre_id` et `l.url_cover as urlCover` dans
  `getAvisByCritique`. `AvisParCritiqueRow.urlCover` prend `null` par défaut.
- `app/src/main/java/com/lmelp/mobile/data/db/AuteursDao.kt:17,33` : ajout de
  `l.url_cover` dans `getLivresParAuteur`.
- `urlCover` est ajouté à `AvisParCritiqueUi` et `LivreParAuteurUi`
  (`app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt`), et propagé par
  `CritiquesRepository.kt:47` et `AuteursRepository.kt:29`.

### Écrans

- Appels à `BookListCard` :
  - `ui/onkindle/OnKindleScreen.kt:221`
  - `ui/palmares/PalmaresScreen.kt:192,233`
  - `ui/recommendations/RecommendationsScreen.kt:91`
  - `ui/critiques/CritiqueDetailScreen.kt:245`
  - `ui/auteurs/AuteurDetailScreen.kt:107`
  - `ui/search/SearchScreen.kt:120`
- `RecommendationCard` perd son paramètre `displayRank`, et `itemsIndexed`
  redevient `items` (`RecommendationsScreen.kt:82,90`).
- `SearchScreen.kt` : un `if/else` choisit `BookListCard` pour les livres et
  l'ancienne `Card` pour les autres types. Le texte commun est factorisé dans le
  composable privé `SearchResultText`.

## Tests (TDD)

Les tests RED ont d'abord échoué à la compilation, puis sont passés au vert :
- `CritiquesRepositoryTest` : la couverture est propagée dans `coupsDeCoeur`, et
  un `urlCover` null reste null.
- `AuteursRepositoryTest` : la couverture est propagée dans `livres`.
- `BookListCardDefaultsTest` (nouveau) : la carte fait 112dp comme la carte
  Émission, et la couverture respecte le ratio 2:3 à 1dp près.

Bilan : 287 tests JVM verts ; lint, `assembleDebug` et hooks pre-commit OK (y
compris la cohérence de version Room). Le projet n'a ni androidTest ni
Robolectric, donc le rendu Compose a été validé visuellement sur Pixel 9 Pro
(`installDebug` : mise à jour sur place, la base complète est conservée).

## Pièges rencontrés

- En nettoyant automatiquement les imports inutilisés par recherche du nom
  simple, on retire à tort `androidx.compose.runtime.getValue`/`setValue` :
  ils servent aux délégations `by remember`/`by collectAsStateWithLifecycle()`
  sans que leur nom apparaisse dans le code.
- Les hauteurs de carte fixes imposent de borner `maxLines` sur les textes,
  sinon un titre long déborde ou se retrouve coupé net.
