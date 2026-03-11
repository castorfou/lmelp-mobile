# Issue #37 — Amélioration liste et détail Émissions

## Résumé des travaux

Amélioration complète de l'écran Émissions (liste + détail) avec TDD, migration de schéma Room, et ajout d'icônes SVG fidèles au back-office.

## Modifications fichiers

### Script export
- `scripts/export_mongo_to_sqlite.py` : ajout colonne `section TEXT` dans `CREATE TABLE avis`, ajout `a.get("section")` dans le tuple d'export (12 `?` au lieu de 11), `PRAGMA user_version = 2` (était 1)

### Base de données
- `app/src/main/assets/lmelp.db` : regénérée avec MongoDB port 27018, `section` peuplée avec valeurs `"programme"` et `"coup_de_coeur"` (underscore, pas espace)

### Modèle Room
- `app/src/main/java/com/lmelp/mobile/data/model/Entities.kt` : ajout `val section: String?` à `AvisEntity`
- `app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt` : ajout `noteMoyenne: Double? = null` et `section: String? = null` à `LivreUi`
- `app/src/main/java/com/lmelp/mobile/data/db/LmelpDatabase.kt` : `version = 2` (était 1)

### DAO & Repository
- `app/src/main/java/com/lmelp/mobile/data/db/LivresDao.kt` : nouvelle data class `LivreNoteSection` (non-Entity) + requête `getNotesParLivreForEmission` (AVG note + section GROUP BY livre)
- `app/src/main/java/com/lmelp/mobile/data/repository/EmissionsRepository.kt` : jointure `notesMap` pour peupler `noteMoyenne` et `section` dans `LivreUi`

### UI
- `app/src/main/java/com/lmelp/mobile/ui/emissions/EmissionFormatUtils.kt` (CRÉÉ) : `formatDateLong()` ISO → "01 mars 2026"
- `app/src/main/java/com/lmelp/mobile/ui/emissions/EmissionsScreen.kt` : date longue avec année en gras (`buildAnnotatedString` + `SpanStyle(fontWeight = FontWeight.Bold)`), suppression `nbAvis`
- `app/src/main/java/com/lmelp/mobile/ui/emissions/EmissionDetailScreen.kt` : icônes SVG programme/cœur, sections séparées, tri, description sans suffixe RSS, `NoteBadge` par livre

### Tests
- `app/src/test/java/com/lmelp/mobile/EmissionFormatTest.kt` (CRÉÉ) : 12 tests TDD pour `formatDateLong` (un par mois)

### Documentation
- `CLAUDE.md` : section "⚠️ Erreur fréquente : Room cannot verify the data integrity"

## Points saillants techniques

### Icônes SVG dans Compose
Utiliser `ImageVector.Builder` + `path { moveTo/arcTo/curveTo/close() }` :
```kotlin
private val IconProgramme: ImageVector = ImageVector.Builder(
    defaultWidth = 24.dp, defaultHeight = 24.dp,
    viewportWidth = 24f, viewportHeight = 24f
).apply {
    path(fill = SolidColor(Color(0xFF0B5FFF))) {
        moveTo(12f, 4f)
        arcTo(8f, 8f, 0f, true, true, 11.999f, 4f)
        close()
    }
    path(fill = SolidColor(Color.White)) {
        moveTo(12f, 8f)
        arcTo(4f, 4f, 0f, true, true, 11.999f, 8f)
        close()
    }
}.build()
```
- `addOval()` n'existe PAS dans le DSL `path {}` de Compose — utiliser `arcTo` avec point de départ légèrement décalé (11.999f) pour forcer le sweep complet
- Toujours `tint = Color.Unspecified` sur l'`Icon` pour préserver les couleurs SVG

### Room version sync — piège récurrent
Deux fichiers DOIVENT être synchronisés à chaque changement de `@Entity` :
1. `app/.../data/db/LmelpDatabase.kt` → `@Database(version = N)`
2. `scripts/export_mongo_to_sqlite.py` → `PRAGMA user_version = N`

Symptômes si désynchronisés :
- `version < PRAGMA` : "Room cannot verify the data integrity"
- `version > PRAGMA` : Room voit "downgrade", traite l'asset comme incompatible → **données vides silencieuses** (écran "Aucune émission")

Après fix : **désinstaller l'app** sur le device avant réinstall (Room ne recopie l'asset que si la DB n'existe pas encore).

### Valeurs section MongoDB
Les valeurs réelles sont `"programme"` et `"coup_de_coeur"` (underscore). Pas `"coup de coeur"` avec espace.

### Suppression suffixe RSS
Pattern : `"Vous aimez ce podcast ? Pour écouter tous les autres épisodes..."` précédé de `\n\n`. Suppression via `indexOf("Vous aimez ce podcast")`.

### Tri livres
- Programme : `sortedByDescending { it.noteMoyenne }` (null en dernier automatiquement)
- Coups de cœur : `sortedBy { it.titre }` (alphabétique)

## Sources des icônes
Reproduites fidèlement depuis `back-office-lmelp/frontend/src/components/AvisTable.vue` (SVG inline dans le HTML Vue).
