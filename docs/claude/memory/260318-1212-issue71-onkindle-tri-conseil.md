# Issue #71 — Sur ma liseuse : ajouter un tri conseil

**Date** : 2026-03-18
**Branche** : `71-sur-ma-liseuse-ajouter-un-tri-conseil`

## Contexte

L'écran "Sur ma liseuse" (OnKindle) disposait de 2 options de tri :
- `a→z` (alphabétique)
- `Note ↓` (par `note_moyenne` = note Masque)

L'issue demandait d'ajouter un 3ème tri : **Note conseil ↓** (par `score_hybride` de la table `recommendations`).

## Solution — remplacement du booléen par un enum TriMode

### Enum `TriMode`

`app/src/main/java/com/lmelp/mobile/viewmodel/OnKindleViewModel.kt` :
```kotlin
enum class TriMode { ALPHA, NOTE_MASQUE, NOTE_CONSEIL }
```
Remplace le booléen `triParNote: Boolean` dans `OnKindleUiState`.
Méthode `setTriParNote(Boolean)` → `setTriMode(TriMode)`.

### Nouveau LEFT JOIN au niveau DAO

`app/src/main/java/com/lmelp/mobile/data/db/OnKindleDao.kt` — nouvelle data class de projection (pas `@Entity`, pas de migration Room) :
```kotlin
data class OnKindleAvecConseilRow(
    @ColumnInfo(name = "livre_id")      val livreId: String,
    // ... tous les champs de onkindle ...
    @ColumnInfo(name = "score_hybride") val scoreHybride: Double?  // null si absent de recommendations
)
```

Requête avec LEFT JOIN :
```sql
SELECT ok.*, r.score_hybride
FROM onkindle ok
LEFT JOIN recommendations r ON r.livre_id = ok.livre_id
WHERE (:afficherLus = 1 AND ok.calibre_lu = 1) OR (:afficherNonLus = 1 AND ok.calibre_lu = 0)
```

Les livres absents de `recommendations` (ex: livres Calibre hors LMELP) ont `scoreHybride = null`
et sont triés en dernier dans le mode NOTE_CONSEIL (même pattern que `noteMoyenne = null` en NOTE_MASQUE).

### Repository — 3 branches de tri

`app/src/main/java/com/lmelp/mobile/data/repository/OnKindleRepository.kt` :
```kotlin
when (triMode) {
    TriMode.ALPHA       -> rows.sortedWith(Comparator { a, b -> collator.compare(a.titre, b.titre) })
    TriMode.NOTE_MASQUE -> rows.sortedWith(compareBy { if (it.noteMoyenne == null) 1 else 0 }
                              .thenByDescending { it.noteMoyenne ?: 0.0 }.thenWith(collator) { it.titre })
    TriMode.NOTE_CONSEIL -> rows.sortedWith(compareBy { if (it.scoreHybride == null) 1 else 0 }
                              .thenByDescending { it.scoreHybride ?: 0.0 }.thenWith(collator) { it.titre })
}
```

### OnKindleUi — nouveau champ

`app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt` :
```kotlin
val scoreHybride: Double? = null   // ajouté dans OnKindleUi
```

### UI — 2 lignes de chips

`app/src/main/java/com/lmelp/mobile/ui/onkindle/OnKindleScreen.kt` :
- Ligne 1 : `[Non lus] [Lus]`
- Ligne 2 : `[a→z] [Note masque ↓] [Note conseil ↓]    [count]`

Signature `OnKindleContent` : `onToggleTriParNote: () -> Unit` → `onSetTriMode: (TriMode) -> Unit`.

## TDD

`app/src/test/java/com/lmelp/mobile/OnKindleViewModelTest.kt` — tests mis à jour :
- `fakeEntity(OnKindleEntity)` → `fakeRow(OnKindleAvecConseilRow)` avec param `scoreHybride: Double? = null`
- Tous les `dao.getOnKindleFiltres(...)` → `dao.getOnKindleAvecConseil(...)`
- Tous les `state.triParNote` → `state.triMode == TriMode.ALPHA/NOTE_MASQUE/NOTE_CONSEIL`
- Nouveaux tests : `setTriMode NOTE_MASQUE`, `setTriMode NOTE_CONSEIL`, `scoreHybride propage dans OnKindleUi`

## Pas de migration Room

`OnKindleAvecConseilRow` est une data class de projection (comme `PalmaresFiltreAvecUrlRow`),
pas une `@Entity`. Aucun changement de schéma, aucun incrément de version.

## Donnée dans la DB

19 livres en commun entre `onkindle` et `recommendations` (vérifiable via
`SELECT COUNT(*) FROM onkindle ok JOIN recommendations r ON ok.livre_id = r.livre_id`).

## Note technique sur sqlite3

Pour accéder à la DB depuis le terminal :
```bash
source /workspaces/lmelp-mobile/.venv/bin/activate
sqlite3 app/src/main/assets/lmelp.db "..."
```
(documenté dans CLAUDE.md section "Environnement local")
