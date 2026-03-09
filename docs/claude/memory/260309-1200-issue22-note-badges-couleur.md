# Issue #22 — Badges colorés pour les notes

Date : 2026-03-09
Branche : `22-presenter-les-notes-avec-un-code-couleur`

## Comportement

Les notes affichées dans l'app utilisent maintenant des **badges avec fond coloré**, identiques au back-office lmelp (`frontend/src/views/Palmares.vue` et `frontend/src/components/AvisTable.vue`).

Règles de couleur :
- note >= 9 → fond `#00C851` (vert vif), texte blanc
- note >= 7 → fond `#8BC34A` (vert clair), texte blanc
- note >= 5 → fond `#CDDC39` (jaune-vert), texte `#333333` (noir, contraste faible sur jaune)
- note < 5  → fond `#F44336` (rouge), texte blanc

Les notes entières ne s'affichent pas avec `.0` : `8.0` → `"8"`, `8.5` reste `"8.5"`.

## Architecture

### `app/src/main/java/com/lmelp/mobile/ui/theme/Theme.kt`

Ajouts :
- 4 constantes couleur : `NoteExcellent`, `NoteBien`, `NoteMoyen`, `NoteFaible`
- `fun couleurNote(note: Double): Color` — retourne la couleur de fond selon les seuils
- `fun couleurTexteNote(note: Double): Color` — blanc sauf jaune-vert → noir
- `fun formatNote(note: Double): String` — supprime `.0` pour les entiers

### `app/src/main/java/com/lmelp/mobile/ui/components/CommonComponents.kt`

Nouveau composable `NoteBadge(note: Double, suffix: String = "")` :
- `Box` avec `background(couleurNote(note))` + `RoundedCornerShape(4.dp)`
- `Text` avec `couleurTexteNote(note)`, `FontWeight.Bold`, `14.sp`
- Utilise `formatNote` pour supprimer le `.0`

### Écrans utilisant `NoteBadge`

- `app/src/main/java/com/lmelp/mobile/ui/emissions/LivreDetailScreen.kt:107` — avis critiques (sans suffixe `/10`)
- `app/src/main/java/com/lmelp/mobile/ui/palmares/PalmaresScreen.kt:129` — note moyenne du palmarès (format `%.2f` remplacé par badge)
- `app/src/main/java/com/lmelp/mobile/ui/recommendations/RecommendationsScreen.kt` — masqueMean dans la liste Conseils

## Tests

- `app/src/test/java/com/lmelp/mobile/NoteColorTest.kt` — 13 tests couvrant tous les seuils et cas limites de `couleurNote`
- `app/src/test/java/com/lmelp/mobile/NoteFormatTest.kt` — 6 tests pour `formatNote` (entiers et décimales)

## Points clés

- Le composable `NoteBadge` est dans `CommonComponents.kt` → réutilisable partout
- Les fonctions `couleurNote`, `couleurTexteNote`, `formatNote` sont dans `Theme.kt` car liées à l'identité visuelle
- Le `/10` a été supprimé des avis critiques (back-office affiche seulement la note)
- La note moyenne du Palmarès utilisait `"%.2f"` avant (ex: `"8.45"`) — maintenant badge via `formatNote` qui garde les décimales si != entier
