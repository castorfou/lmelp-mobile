# Issue #185 — Écosystème : service pap (lmelp-pap) de docker-lmelp

## Contexte

docker-lmelp ajoute le service `pap`, conteneur `lmelp-pap`, image
`ghcr.io/castorfou/lmelp-pap:latest` ([castorfou/docker-lmelp#95](https://github.com/castorfou/docker-lmelp/pull/95),
issue [#94](https://github.com/castorfou/docker-lmelp/issues/94)). Le port NAS 8085 remplace 8090
([castorfou/docker-lmelp#96](https://github.com/castorfou/docker-lmelp/pull/96)). lmelp-pap est le
« Projet d'Archivage Patrimonial » : il reconstitue les émissions 1955-2016.

## Changement (doc seule)

`docs/ecosysteme.md` :

- **Mise à jour des données** : nœuds `PAP` (NAS), `INA`, `WB` (Wayback Machine) et
  `AUDIOS`, un nœud générique « Fichiers audio, volume Docker du NAS ». Flèches
  manuelles INA → pap (notices, Playwright), Wayback → pap (anciennes listes France
  Inter 2002-2012, fichiers audio du podcast via l'API CDX), RF → pap (fiches podcast),
  MONGO → pap (lecture seule de `episodes`). Flèches BACK ↔ AUDIOS
  (`/volume1/docker/lmelp/audios`) et PAP ↔ AUDIOS (`/volume1/docker/lmelp/pap/audios`).
  Flèche PAP → LAPTOP (interface web, réseau local) ;
- **Mise à jour du code** : lmelp-pap dans le nœud PUSH, `WT → PAP` ;
- **Tableau** : pap ajouté à « Nouvelle version de code » ; lignes « Sources de
  lmelp-pap », « Fichiers audio (volume Docker du NAS) » et « lmelp-pap → navigateur ».

`CLAUDE.md`, section « Écosystème lmelp » : lmelp-pap et ses sources ajoutés à la
liste des briques.

## À retenir

- **Vérifier dans le code du dépôt amont, pas seulement dans l'issue** : l'issue disait
  « Mongo pas encore lu », mais `collecter_lmelp` (`src/lmelp_pap/collecte.py`) lit
  déjà `masque_et_la_plume.episodes`. Les sources réelles sont dans
  `src/lmelp_pap/sources/` (ina, ina_catalogue, anciennes_listes, wayback_podcast,
  radiofrance, lmelp).
- **Regarder les derniers commits avant de conclure à un manque** : j'ai d'abord cru
  que `docker.yml` de lmelp-pap n'appelait pas l'API Watchtower (lecture partielle du
  fichier), et ouvert castorfou/lmelp-pap#75. L'appel venait d'être fusionné
  (#73, PR #74), et #75 a été fermée comme doublon.
- Watchtower (`castorfou/dockers`, `watchtower-docker-compose.yml`) : mode API HTTP
  (`/v1/update`) avec `WATCHTOWER_HTTP_API_PERIODIC_POLLS=true` et
  `WATCHTOWER_POLL_INTERVAL` à 1 semaine par défaut. Sans appel API, une image n'est
  donc installée qu'au sondage hebdomadaire.
- Chemins hôtes du NAS : `.env.nas.example` de docker-lmelp (`AUDIO_PATH`,
  `PAP_PATH_HOTE`).
- Pas de Chrome pour Playwright dans le devcontainer : le rendu mermaid n'a pas pu être
  vérifié automatiquement, seulement `mkdocs build --strict`.
