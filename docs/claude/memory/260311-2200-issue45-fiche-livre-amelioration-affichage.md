# Issue #45 — Fiche livre : amélioration de l'affichage

## Problème

La fiche livre affichait les avis de façon plate (liste sans regroupement). L'issue demandait :

1. Note moyenne en haut à droite en gros (badge 32sp avec la bonne couleur)
2. Avis regroupés par émission, précédés du titre de l'émission
3. En-têtes d'émission cliquables → navigation vers l'émission
4. Avis triés par ordre alphabétique de critique au sein de chaque groupe d'émission

## Architecture de la solution

### Nouveau DAO avec jointure SQL

`app/src/main/java/com/lmelp/mobile/data/db/LivresDao.kt`

Ajout de `AvisAvecEmissionRow` (`@Embedded` + deux colonnes supplémentaires) et d'une query avec double jointure `avis → emissions → episodes` pour récupérer titre et date de l'émission :

```sql
SELECT a.*, ep.titre as emission_titre, em.date as emission_date
FROM avis a
JOIN emissions em ON em.id = a.emission_id
JOIN episodes ep ON ep.id = em.episode_id
WHERE a.livre_id = :livreId
```

Le titre d'une émission = titre de l'épisode associé (table `episodes`).

### Nouveaux modèles UI

`app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt`

- `AvisParEmissionUi` : nouveau data class (emissionId, emissionTitre, emissionDate, avis: List<AvisUi>)
- `LivreDetailUi` : remplace `avis: List<AvisUi>` par `avisParEmission: List<AvisParEmissionUi>` + ajout de `noteMoyenne: Double?`

### Logique de groupement dans le Repository

`app/src/main/java/com/lmelp/mobile/data/repository/LivresRepository.kt`

- `noteMoyenne` = moyenne des notes non nulles
- Groupement par `emissionId` avec `groupBy` puis `sortedByDescending(emissionDate)`
- Dans chaque groupe : `sortedBy { it.avis.critiqueNom ?: "" }`

### UI mise à jour

`app/src/main/java/com/lmelp/mobile/ui/emissions/LivreDetailScreen.kt`

- Header avec `Row` : auteur/éditeur à gauche, `NoteBadge(fontSize = 32.sp)` à droite
- Nouveau composable `EmissionGroupHeader` : titre cliquable + date formatée
- La `LazyColumn` itère sur `livre.avisParEmission` avec `forEach` pour les headers et `items()` pour les avis

### NoteBadge enrichi

`app/src/main/java/com/lmelp/mobile/ui/components/CommonComponents.kt`

Ajout du paramètre `fontSize: TextUnit = 14.sp` à `NoteBadge` pour permettre le badge grande taille (32sp) sans briser les usages existants.

### Navigation

`app/src/main/java/com/lmelp/mobile/Navigation.kt`

Ajout de `onEmissionClick = { navController.navigate(Routes.emissionDetail(it)) }` dans le composable `LIVRE_DETAIL`.

## Tests TDD

`app/src/test/java/com/lmelp/mobile/LivresRepositoryTest.kt`

4 tests unitaires couvrant :
- `noteMoyenne` calculée correctement (notes nulles ignorées)
- `noteMoyenne` null si aucun avis n'a de note
- Groupes d'émissions triés par date DESC (plus récent en premier)
- Avis triés alphabétiquement par critique au sein d'un groupe

## Pattern Room `@Embedded` + colonnes supplémentaires

Pour une query Room qui retourne une entité + des colonnes supplémentaires, utiliser :
```kotlin
data class MyRow(
    @Embedded val entity: MyEntity,
    @ColumnInfo(name = "extra_col") val extraCol: String?
)
```
Aucun conflit de noms si les colonnes supplémentaires ont des alias SQL distincts des colonnes de l'entité.
