# Issue #127 — Désynchro app mobile / back-office sur le Philippe Jaenada

## Symptôme rapporté

Le livre "L'Inconnue du quai de Javel" (Philippe Jaenada) apparaissait dans l'app mobile sans couverture, alors qu'il avait bien été traité par le Masque et la Plume et disposait d'une `url_cover` correcte côté back-office depuis plusieurs jours. L'utilisateur avait par ailleurs l'impression que des « mises à jour de la base » étaient détectées par l'app quasiment tous les matins, avant même l'apparition de ce problème.

## Investigation (MCP MongoDB en lecture seule, connexion `preconfigured` déclarée dans `.mcp.json` vers `mongodb://nas923:27018/masque_et_la_plume`)

- Le livre existe bien en base avec sa `url_cover`, créé/mis à jour le 2026-08-23 13:57 — après la dernière release GitHub `data-latest` publiée (2026-08-23 13:45).
- Aucune donnée MongoDB n'a changé depuis le 2026-08-23 18:11.
- Logs anacron du NAS (`lmelp-export`, repo `docker-lmelp`) : le job `publish-data-release` se déclenche bien chaque jour, mais échoue (`exit status 1`) systématiquement depuis le 24/08 :
  ```
  sqlite3.IntegrityError: FOREIGN KEY constraint failed
    File "export_mongo_to_sqlite.py", line 406, in export_avis
  ```
- Cause racine trouvée via requêtes MongoDB (`$lookup` livres/avis) : 5 documents `avis` référencent un `livre_oid` qui n'existe plus dans `livres` (2 `_id` fantômes, pour "L'Affaire Alaska Sanders" et "La Promesse", qui existent par ailleurs sous d'autres `_id`). Origine confirmée par l'utilisateur : une ré-extraction des avis sur l'émission du 23/08 a créé des doublons de livres, résolus via la fonction de gestion des doublons de l'app web côté back-office — mais les `avis` déjà créés avec l'ancien `livre_oid` n'ont pas été repointés vers le survivant.
- `export_avis` (`scripts/export_mongo_to_sqlite.py`) insère tous les avis en un seul `cur.executemany(...)` avec `PRAGMA foreign_keys = ON` : 5 lignes invalides suffisaient à faire échouer tout le batch, bloquant la publication de `lmelp.db` (et donc toute fraîcheur de données côté app mobile) pendant 4 jours.

## Fix appliqué (TDD)

`scripts/export_mongo_to_sqlite.py` — `export_avis` (~ligne 360) : avant l'insertion, charge les IDs valides de `emissions`, `livres`, `critiques` déjà présents dans le cursor SQLite (ces tables sont exportées avant `avis` dans `main()`), puis filtre chaque avis dont `emission_oid`/`livre_oid`/`critique_oid` ne matche pas un ID valide — loggé en `WARNING` avec l'id de l'avis et les 3 OID, puis ignoré (`continue`), sans bloquer le reste du batch.

Tests ajoutés : `tests/test_export_avis_orphelins.py` (5 tests, mock `mongo_db.avis.find` via `unittest.mock.MagicMock`, DB SQLite en mémoire avec FK actives reproduisant le schéma `emissions`/`livres`/`critiques`/`avis`) :
- avis avec `livre_oid` orphelin → ignoré sans planter
- avis valide → inséré normalement (non-régression)
- avis orphelin + avis valide dans le même batch → l'orphelin est ignoré, le valide est bien inséré
- avis avec `emission_oid` orphelin → ignoré
- avis avec `critique_oid` orphelin → ignoré

Suite complète : 75 passed, 5 skipped (nécessitent une vraie DB de prod via `LMELP_DB_PATH`), aucune régression. `ruff check`/`ruff format` propres. `mypy` : mêmes 8 erreurs préexistantes qu'avant le fix (lignes décalées), aucune nouvelle introduite par ce changement.

## Découverte annexe : `version` de `db_metadata` est naïve (issue de suivi #128)

En creusant la remarque de l'utilisateur (« impression que des MAJ étaient dispo tous les matins »), confirmation dans `write_metadata()` (`scripts/export_mongo_to_sqlite.py:1372`) : `version = int(time.time())` — un timestamp pris au moment de l'exécution du script, **pas** une empreinte du contenu réel exporté. Comme l'anacron republie chaque jour inconditionnellement (`--force`, pas de comparaison au run précédent) et que `gh release upload --clobber` écrase systématiquement, `version` avance chaque jour même si le contenu de la base est strictement identique à la veille. Côté app, `DataUpdateRepository.checkForUpdate()` compare `remoteVersion > localVersion` — presque toujours vrai. Donc l'app signalait bien une « MAJ disponible » quasiment tous les matins, réelle nouvelle donnée ou pas. Pas corrigé dans cette itération (hors scope du crash bloquant) : voir issue de suivi [castorfou/lmelp-mobile#128](https://github.com/castorfou/lmelp-mobile/issues/128), piste proposée = baser `version` sur le contenu réel (max `updated_at` des tables sources, ou hash) plutôt que l'heure d'export.

## Issue de suivi côté back-office (hors périmètre lmelp-mobile)

Ouverte [castorfou/back-office-lmelp#271](https://github.com/castorfou/back-office-lmelp/issues/271) pour traiter la cause amont : éviter de laisser des avis orphelins lors de la régénération d'avis/fusion de doublons de livres ; ajouter une page de nettoyage des avis orphelins sur le modèle de la gestion des doublons existante ; ajouter une tuile stats affichant le nombre d'avis orphelins (masquée si 0, cf. back-office-lmelp#212).

## Méthode qui a bien fonctionné

- Le MCP MongoDB (lecture seule, préconfiguré dans `.mcp.json`) a été décisif pour confirmer/infirmer les hypothèses sur des vraies données de prod plutôt que deviner depuis le code seul — nécessite un redémarrage de session Claude Code après ajout du serveur dans `.mcp.json` pour que les tools `mcp__MongoDB__*` apparaissent.
- Alterner hypothèse → vérification factuelle (Mongo, logs anacron fournis par l'utilisateur) a permis d'écarter plusieurs fausses pistes (régression de release, bug de comparaison de version côté app, mismatch de normalisation de titre Calibre) avant de trouver la vraie cause.
