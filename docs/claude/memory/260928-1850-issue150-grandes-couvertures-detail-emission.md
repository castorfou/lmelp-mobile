# Issue #150 — Grandes couvertures dans le détail d'une émission

## Besoin

#143 avait migré toutes les listes d'œuvres vers `BookListCard` (couverture
pleine hauteur 75×112dp collée au bord gauche), mais avait volontairement laissé
de côté `LivreCard`, la carte livre du détail d'une émission, restée sur la
petite vignette `BookCoverThumbnail` (48×72dp). L'issue #150 demande d'aligner
cette page sur #143.

## Modifications

- `app/src/main/java/com/lmelp/mobile/ui/emissions/EmissionDetailScreen.kt` —
  `LivreCard` passe par `BookListCard(urlCover = livre.urlCover, modifier =
  Modifier.padding(vertical = 4.dp).clickable(...))`. Pas de padding horizontal :
  la `Column` parente de `EmissionDetailContent` a déjà `padding(16.dp)`.
  Titre borné à 2 lignes, auteur à 1 ligne (`TextOverflow.Ellipsis`), la hauteur
  de carte étant fixe. La colonne de droite (`NoteBadge` + `CalibreBadge`) est
  inchangée et tient dans les 112dp. Pas de `coverData` : les livres d'une
  émission sont des livres du Masque, avec `url_cover` Babelio.
- `app/src/main/java/com/lmelp/mobile/ui/components/CommonComponents.kt` —
  suppression de `BookCoverThumbnail` (plus aucun appelant) et de l'import
  `Spacer` devenu inutile.
- `app/src/test/java/com/lmelp/mobile/BookListCardUsageTest.kt` — nouveau test
  garde-fou : parcourt `src/main/java/com/lmelp/mobile/ui/**/*.kt` et échoue si
  un fichier contient encore `BookCoverThumbnail(`. RED avant la migration
  (`CommonComponents.kt`, `EmissionDetailScreen.kt`), GREEN après.

## Points saillants

- Le projet n'a pas d'infra de test Compose UI en test unitaire (pas de
  Robolectric/`createComposeRule`) : pour une migration purement visuelle, le
  test RED retenu est un test de source (le répertoire de travail des tests
  unitaires Gradle est le module `app/`, d'où le chemin relatif
  `src/main/java/...`). Le rendu est validé sur device.
- Toutes les cartes livre de l'app passent désormais par `BookListCard` — il
  n'existe plus de petite vignette de couverture.

## Vérification

- `./gradlew testDebugUnitTest` : 304 tests, 0 échec.
- `./gradlew lintDebug` : 72 avertissements, identique à `main`.
- Test device validé par l'utilisateur (« c'est parfait »).
