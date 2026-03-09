# Issue #27 — Corrections affichage Palmarès

Date : 2026-03-09
Branche : `27-palmares-corrections-affichage`

## Comportement

Deux corrections dans `app/src/main/java/com/lmelp/mobile/ui/palmares/PalmaresScreen.kt` :

### 1. Note Calibre affichée uniquement si lu

Avant : `calibreRating` ("4/10") s'affichait pour tous les livres possédés dans Calibre, qu'ils soient lus ou non. Cela créait de la confusion car le "4/10" semble être une note Masque.

Après : la note Calibre n'est affichée que si `calibreLu = true`. Si non lu, seul le symbole ◯ apparaît.

```kotlin
if (item.calibreLu) {
    item.calibreRating?.let {
        Text(text = "${it.toInt()}/10", ...)
    }
}
```

### 2. Ordre des colonnes inversé

Avant : `#rank | titre/auteur | NoteBadge+nbAvis | ✓/◯+calibreRating`
Après : `#rank | titre/auteur | ✓/◯+calibreRating | NoteBadge+nbAvis`

Le badge de note Masque est maintenant à **l'extrême droite**, conformément à la demande (issue #27).

## Fichier modifié

- `app/src/main/java/com/lmelp/mobile/ui/palmares/PalmaresScreen.kt` — composable `PalmaresCard`

## Points clés

- Pas de modification de modèle ou DAO — purement UI
- `calibreRating` reste dans `PalmaresUi` et `PalmaresEntity` (données toujours disponibles si besoin futur)
- Pas de nouveaux tests unitaires nécessaires (logique d'affichage pure)
