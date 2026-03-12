---
name: Issue #47 — Fiche auteur
description: Création de la fiche auteur accessible depuis la fiche livre et la recherche
type: project
---

# Issue #47 — Fiche auteur : créer la fiche, accessible depuis un livre ou la recherche

## Problème

Pas de fiche auteur dans l'application. L'issue demandait :

1. Créer `AuteurDetailScreen` : nom auteur + liste des livres (note moyenne + date dernière émission), livres triés par date émission décroissante, cliquables → fiche livre
2. Modifier `LivreDetailScreen` : auteurNom cliquable → fiche auteur (seulement si `auteurId` non null)
3. Modifier `SearchScreen` / `Navigation` : résultat de type "auteur" cliquable → fiche auteur

## Architecture de la solution

### Nouveau DAO

`app/src/main/java/com/lmelp/mobile/data/db/AuteursDao.kt`

- `LivreParAuteurRow` : data class avec `livreId`, `titre`, `noteMoyenne` (LEFT JOIN palmares), `derniereEmissionDate` (MAX(em.date) via LEFT JOIN avis → emissions)
- Query SQL avec double LEFT JOIN :

```sql
SELECT l.id as livre_id, l.titre,
       p.note_moyenne,
       MAX(em.date) as derniere_emission_date
FROM livres l
LEFT JOIN palmares p ON p.livre_id = l.id
LEFT JOIN avis a ON a.livre_id = l.id
LEFT JOIN emissions em ON em.id = a.emission_id
WHERE l.auteur_id = :auteurId
GROUP BY l.id
```

LEFT JOIN car tous les livres n'ont pas de note dans palmares (seuil ≥ 2 avis), et certains peuvent n'avoir aucune émission.

### Nouveaux modèles UI

`app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt`

- `LivreParAuteurUi` : `livreId`, `titre`, `noteMoyenne: Double?`, `derniereEmissionDate: String?`
- `AuteurDetailUi` : `id`, `nom`, `livres: List<LivreParAuteurUi>`
- `LivreDetailUi` : ajout de `auteurId: String?` (nouveau champ) pour permettre la navigation vers la fiche auteur

### Logique de tri dans le Repository

`app/src/main/java/com/lmelp/mobile/data/repository/AuteursRepository.kt`

- Tri par `derniereEmissionDate` décroissant (les `null` passent en dernier via `sortedByDescending { it.derniereEmissionDate ?: "" }`)

### `LivresRepository` mis à jour

`app/src/main/java/com/lmelp/mobile/data/repository/LivresRepository.kt`

- Propage `livre.auteurId` dans le `LivreDetailUi` retourné

### ViewModel

`app/src/main/java/com/lmelp/mobile/viewmodel/AuteurDetailViewModel.kt`

Pattern identique aux autres ViewModels : `AuteurDetailUiState(isLoading, auteur, error)` + `Factory`.

### UI

`app/src/main/java/com/lmelp/mobile/ui/auteurs/AuteurDetailScreen.kt`

- `AuteurDetailScreen` : Scaffold + TopAppBar (nom auteur) + `LazyColumn` de `LivreParAuteurCard`
- `LivreParAuteurCard` : Row avec titre + date émission (à gauche) et `NoteBadge` (à droite), cliquable

### LivreDetailScreen mis à jour

`app/src/main/java/com/lmelp/mobile/ui/emissions/LivreDetailScreen.kt`

- Ajout paramètre `onAuteurClick: (String) -> Unit = {}`
- `auteurNom` affiché en `colorScheme.primary` et cliquable si `auteurId != null`, sinon texte normal non cliquable

### Navigation

`app/src/main/java/com/lmelp/mobile/Navigation.kt`

- Ajout `Routes.AUTEUR_DETAIL = "auteur/{auteurId}"` + `Routes.auteurDetail(id)`
- Nouveau composable `AUTEUR_DETAIL` avec `navArgument("auteurId")`
- `LIVRE_DETAIL` : passage de `onAuteurClick = { navController.navigate(Routes.auteurDetail(it)) }`
- `SEARCH` : ajout `"auteur" -> navController.navigate(Routes.auteurDetail(id))` dans le `when`

### Infrastructure

- `app/src/main/java/com/lmelp/mobile/data/db/LmelpDatabase.kt` : expose `abstract fun auteursDao(): AuteursDao`
- `app/src/main/java/com/lmelp/mobile/LmelpApp.kt` : expose `val auteursRepository by lazy { AuteursRepository(database.auteursDao()) }`

## Tests TDD

`app/src/test/java/com/lmelp/mobile/AuteursRepositoryTest.kt`

4 tests unitaires :
- `getAuteurDetail retourne null si auteur introuvable`
- `livres tries par date emission decroissante`
- `livres sans date emission apparaissent en dernier`
- `noteMoyenne null si livre sans avis dans palmares`

## Pattern LEFT JOIN palmares

La table `palmares` ne contient que les livres avec ≥ 2 avis. Pour afficher tous les livres d'un auteur (y compris ceux sans note), utiliser `LEFT JOIN palmares` → `noteMoyenne` sera `null` pour les livres non présents dans palmares.
