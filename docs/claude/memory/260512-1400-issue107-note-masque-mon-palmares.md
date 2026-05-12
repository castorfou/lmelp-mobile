# Issue #107 — Afficher les notes du Masque dans Mon Palmarès

## Problème

Dans l'écran "Mon palmarès" (mode PERSONNEL), chaque carte livre affichait la note personnelle
Calibre (`calibreRating`) mais **pas la note moyenne du Masque** (`noteMoyenne`).
La donnée existait dans la base (`MonPalmaresRow.noteMoyenne`) mais était perdue à la conversion.

## Cause : chaîne de données cassée à trois niveaux

1. `MonPalmaresItemUi` n'avait pas de champ `noteMoyenne`
2. `MonPalmaresRow.toItemUi()` dans `PalmaresRepository` ignorait `noteMoyenne` lors du mapping
3. `MonPalmaresCard` dans `PalmaresScreen.kt` n'affichait pas la note du Masque

## Corrections (3 fichiers)

### 1. Modèle UI
`app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt`

Ajout du champ dans `MonPalmaresItemUi` :
```kotlin
val noteMoyenne: Double? = null
```
Nullable car les livres hors Masque (CalibreHorsMasque) ne figurent pas dans la table `palmares`.

### 2. Repository
`app/src/main/java/com/lmelp/mobile/data/repository/PalmaresRepository.kt`

Dans `MonPalmaresRow.toItemUi()` — ajout :
```kotlin
noteMoyenne = noteMoyenne
```
La fonction `CalibreHorsMasqueEntity.toItemUi()` est inchangée → `noteMoyenne` reste `null` pour les livres hors Masque.

### 3. Affichage
`app/src/main/java/com/lmelp/mobile/ui/palmares/PalmaresScreen.kt`

Dans `MonPalmaresCard`, dans la `Column(horizontalAlignment = Alignment.End)` existante — ajout :
```kotlin
item.noteMoyenne?.let {
    NoteBadge(note = it)
}
```
Réutilise le composable `NoteBadge` déjà utilisé dans `PalmaresCard`.

## Rendu visuel

```
[couverture] Titre                    7j
             Auteur                   8/10  ← note perso Calibre (vert)
             Lu le 12/05/2026         7.5   ← note Masque (NoteBadge coloré)
```
Les livres hors Masque n'affichent pas de `NoteBadge`.

## Tests TDD

Fichier créé : `app/src/test/java/com/lmelp/mobile/MonPalmaresNoteMasqueTest.kt`

3 tests :
1. `noteMoyenne propagée depuis MonPalmaresRow vers MonPalmaresItemUi` — vérifie le mapping repository
2. `livre hors Masque a noteMoyenne null` — vérifie `CalibreHorsMasqueEntity` mapping
3. `MonPalmaresItemUi a noteMoyenne null par defaut` — vérifie la valeur par défaut

## Pattern retenu

`MonPalmaresItemUi` regroupe livres Masque (avec `noteMoyenne`) et hors Masque (sans).
Utiliser des `Double?` nullable pour les champs propres au Masque — l'UI utilise `?.let` pour n'afficher que si présent.
