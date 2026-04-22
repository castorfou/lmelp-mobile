# Issue #96 — Bug recherche : erreur SQL FTS4

## Contexte

La recherche plante avec une erreur SQL car la requête référence une colonne invalide dans une table virtuelle FTS4.

## Cause racine

Dans `app/src/main/java/com/lmelp/mobile/data/repository/SearchRepository.kt:27`, la requête SQL utilisait :

```sql
WHERE si.search_index MATCH ?
```

`search_index` est le **nom de la table virtuelle FTS4**, pas une colonne. La syntaxe `si.search_index` (préfixe d'alias de table + nom de table) est invalide en SQLite FTS4.

## Fix

Remplacer par :

```sql
WHERE search_index MATCH ?
```

En FTS4, le MATCH s'applique à la table directement (pas à une colonne), même quand la table est jointe via un alias.

## Point important : FTS4, pas FTS5

La table virtuelle `search_index` est créée en **FTS4** dans `scripts/export_mongo_to_sqlite.py:178` :

```sql
CREATE VIRTUAL TABLE IF NOT EXISTS search_index USING fts4(...)
```

La documentation dans `CLAUDE.md` mentionne FTS5 — c'est une erreur de documentation. La réalité du code est FTS4.

## Tests TDD ajoutés

Dans `app/src/test/java/com/lmelp/mobile/SearchRepositoryTest.kt` :

- `la requete SQL utilise search_index MATCH sans prefixe de colonne` — utilise `argumentCaptor` Mockito pour capturer la `SupportSQLiteQuery` passée au DAO et vérifier que le SQL ne contient pas `si.search_index MATCH` mais bien `search_index MATCH`
- `search mappe displayContent comme content dans SearchResultUi` — vérifie que le champ `displayContent` (texte affiché) est bien utilisé comme `content` dans le `SearchResultUi` (et non le champ `content` brut qui sert à l'index FTS)

## Pattern de test pour capturer une requête SQL

```kotlin
val captor = argumentCaptor<SupportSQLiteQuery>()
verify(dao).searchRaw(captor.capture())
val sql = captor.firstValue.sql
assertFalse(sql.contains("si.search_index MATCH", ignoreCase = true))
assertTrue(sql.contains("search_index MATCH", ignoreCase = true))
```
