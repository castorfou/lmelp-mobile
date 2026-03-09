# Issue #13 — Recherche : filtrer les types de résultats

Date : 2026-03-09
Branche : `13-recherche-ne-le-faire-que-pour-les-livres-auteurs-et-avis-des-critiques`

## Comportement

La recherche FTS5 n'affiche plus les résultats de type `emission`.
Seuls les types `livre`, `auteur`, `critique` sont retournés.

## Implémentation — un seul fichier modifié côté logique

### `app/src/main/java/com/lmelp/mobile/data/repository/SearchRepository.kt`

Ajout de `AND type IN ('livre', 'auteur', 'critique')` dans la requête SQL FTS5 :

```kotlin
val sql = SimpleSQLiteQuery(
    "SELECT type, ref_id AS refId, content FROM search_index WHERE search_index MATCH ? AND type IN ('livre', 'auteur', 'critique') LIMIT 50",
    arrayOf(ftsQuery)
)
```

### `app/src/main/java/com/lmelp/mobile/ui/search/SearchScreen.kt:81`

Placeholder mis à jour : `"Rechercher un livre, auteur, émission..."` → `"Rechercher un livre, auteur, critique..."`

## Tests

`app/src/test/java/com/lmelp/mobile/SearchFilterTest.kt` — 8 tests unitaires purs (JUnit, pas Android) :
- livre / auteur / critique → inclus
- emission → exclu
- type inconnu (editeur) → exclu
- liste mixte : 3/5 gardés
- liste sans émissions → inchangée
- liste vide → vide

## Points clés

- La table FTS5 `search_index` contient les types : `emission`, `livre`, `auteur`, `critique`
- Le filtre est appliqué côté SQL (pas côté Kotlin) → efficace, pas de données inutiles chargées
- `AND type IN (...)` est compatible avec la syntaxe FTS5 SQLite (le filtre porte sur une colonne normale, pas sur le contenu indexé)
- Aucune modification du DAO ni du ViewModel
