# Issue #128 — `db_metadata.version` basée sur le contenu réel, pas sur l'heure d'export

## Symptôme

Découvert en marge de l'issue #127 : l'utilisateur observait une "mise à jour disponible" détectée par l'app mobile quasiment tous les matins, même sans nouvelle donnée réelle côté Masque et la Plume.

## Cause

`write_metadata()` (`scripts/export_mongo_to_sqlite.py`) fixait `db_metadata.version = int(time.time())` — un timestamp pris à l'exécution du script, pas une empreinte du contenu réellement exporté. L'anacron du NAS republie `lmelp.db` chaque jour inconditionnellement (`--force`, sans comparaison au run précédent), donc `version` avançait systématiquement même à contenu strictement identique. Côté app, `DataUpdateRepository.checkForUpdate()` compare `remoteVersion > localVersion` (deux `Long`) — condition presque toujours vraie, d'où le faux signal quotidien.

## Contrainte de design clé

`version` doit rester un `Long` strictement croissant — contrat déjà utilisé par `DataUpdateRepository.checkForUpdate()` (`remote.exportVersion.toLongOrNull() > localVersion`). Un hash de contenu (string) n'est pas ordonnable et ne peut donc pas remplacer directement `version` — il faut le combiner à la logique existante plutôt que la remplacer.

## Fix implémenté (TDD)

1. **`compute_content_hash(cur)`** (nouvelle fonction, `scripts/export_mongo_to_sqlite.py` ~ligne 1368) : SHA-256 du contenu métier — toutes les tables de données (`episodes`, `emissions`, `auteurs`, `livres`, `critiques`, `avis`, `emission_livres`, `avis_critiques`, `palmares`, `recommendations`, `onkindle`, `calibre_hors_masque`), chacune triée par toutes ses colonnes avant hashage pour être indépendante de l'ordre d'insertion (non déterministe côté Mongo). Exclut explicitement `db_metadata` (contient des timestamps) et `search_index` (table virtuelle FTS4 dérivée).

2. **`write_metadata(cur, previous_content_hash=None, previous_version=None)`** : calcule `content_hash` à chaque appel ; si `previous_content_hash == content_hash` et `previous_version` fourni, réutilise `previous_version` tel quel au lieu de générer un nouveau timestamp. `content_hash` est aussi persisté dans `db_metadata`.

3. **`main()`** : nouvelles options CLI `--previous-content-hash`/`--previous-version`, optionnelles (comportement inchangé si absentes — non-régression).

4. **`generate_data_release_metadata.py`** : `content_hash` exposé dans `metadata.json`, à côté de `sha256` (qui reste le hash du fichier `.db` complet, pour vérifier l'intégrité du téléchargement côté app — deux hash, deux usages distincts).

5. **`docker_export_and_publish_release.sh`** : avant l'export, télécharge le `metadata.json` de la release `data-latest` déjà publiée (`gh release download`, tolérant l'absence au tout premier run) et en extrait `content_hash`/`export_version` pour les passer au script Python. Après génération du nouveau `metadata.json`, si son `export_version` == l'ancien (donc contenu inchangé), **`gh release upload` est carrément sauté** — évite de republier ~4.5 Mo chaque jour pour rien, en plus de stabiliser `version`.

## Décision de design retenue avec l'utilisateur

Comparer au contenu de la **dernière release GitHub déjà publiée** (pas à un fichier local) — chaque run anacron écrit vers `/tmp/lmelp.db` dans un nouveau container Docker, donc pas de fichier "précédent" fiable en local entre deux runs.

## Tests ajoutés

- `tests/test_content_hash.py` (5 tests) : déterminisme, sensibilité à un ajout d'avis, sensibilité à une modification `url_cover` (cas concret de l'issue #127), insensibilité à l'ordre d'insertion, `db_metadata` exclu du hash.
- `tests/test_write_metadata_version.py` (4 tests) : non-régression sans `previous_*`, réutilisation de `version` si hash identique, nouveau timestamp si hash différent, nouveau timestamp si `previous_version` absent même avec hash identique.
- `tests/test_generate_data_release_metadata.py` : ajout d'une assertion sur `content_hash` dans le `metadata.json` généré.

Suite complète : 84 passed, 5 skipped (nécessitent une vraie DB de prod), aucune régression. `ruff check`/`ruff format` propres (binaire du hook pre-commit v0.16.3). `mypy` : mêmes 8 erreurs préexistantes qu'avant ce fix (lignes décalées), aucune nouvelle.

## Aucun changement côté app Kotlin

Le format `export_version` reste un `Long` croissant — `DataUpdateRepository.kt`/`RemoteMetadata.kt` n'ont eu besoin d'aucune modification, confirmé en lisant `checkForUpdate()` avant de concevoir le fix.

## Suite logique de [[260827-1530-issue127-avis-orphelins-export-bloque]]

Découverte pendant l'investigation de #127 (anacron bloqué par des avis orphelins) — question annexe de l'utilisateur sur les "fausses MAJ quotidiennes" observées avant ce blocage, traitée séparément dans cette issue #128.
