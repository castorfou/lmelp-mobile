# Issue #116 — ADR séparation MAJ appli / MAJ données

## Contexte du problème

Le processus actuel de mise à jour d'un épisode mélangeait deux préoccupations derrière une seule commande ADB (`lmelp-update-mobile` → `docker exec lmelp-export export-and-push`) : la mise à jour de l'**application** (rare) et la mise à jour des **données** (fréquente, ~1x/semaine). ADB n'est en réalité nécessaire que pour le premier cas. La migration de `docker-lmelp` vers un NAS Synology (`castorfou/docker-lmelp#47`) rend le mécanisme ADB actuel (USB + laptop + build debug via `run-as`) impraticable pour la mise à jour des données au quotidien.

Cette issue est une issue de **cadrage/architecture** : elle produit un ADR + un squelette CI minimal + un petit ajout UI, PAS l'implémentation Kotlin complète du téléchargement (réservée à une issue de suivi).

## Décisions d'architecture retenues

- **Hébergement des données** : GitHub Release asset dédié, tag `data-latest`, réutilise `softprops/action-gh-release@v2` déjà utilisé dans `release.yml` pour l'APK.
- **C'est une étape 1 pragmatique**, pas une décision finale : l'utilisateur a un plan long terme (non démarré) de projet public multi-utilisateurs (admin qui met à jour la base commune, mode anonyme web+mobile, mode utilisateur basé sur Calibre pour recommandations personnalisées) qui remplacera ce mécanisme par un vrai backend plus tard.
- **Publication automatique** : anacron embarqué dans `Dockerfile.export`, reproduisant exactement le pattern déjà en place et validé dans `mongodb.Dockerfile` du repo `docker-lmelp` (service `mongo`, utilisé pour rotation de logs + backup MongoDB). Anacron plutôt que cron classique car robuste aux coupures/redémarrages du NAS.
- **Déclencheur côté app** (conception seulement, pas implémenté) : vérification au lancement de l'app (non bloquant) + bouton manuel. Pas de WorkManager/service en arrière-plan — reste fidèle à la nature offline-first de l'app.
- **Réseau strictement optionnel/best-effort** : jamais requis pour l'usage normal.
- **Flux APK** (mise à jour de l'application elle-même) : traité comme périphérique, piste retenue = Google Play Store, mais hors scope technique ici (implique compte développeur, review, question de droits sur le contenu Le Masque et la Plume/France Inter) → issue de suivi séparée.

## Découverte technique importante : confusion `user_version` vs timestamp

`scripts/export_mongo_to_sqlite.py` écrit `PRAGMA user_version = ROOM_VERSION` (entier **fixe** = numéro de schéma Room, actuellement 7, synchronisé avec `version = N` dans `app/src/main/java/com/lmelp/mobile/data/db/LmelpDatabase.kt`) — ce n'est **pas** un timestamp. Le timestamp Unix de l'export est stocké séparément dans la table `db_metadata` (clé `version`), avec `export_date`/`export_datetime`. `CLAUDE.md` contenait une confusion à ce sujet (ligne ~159, affirmait à tort que `user_version` = timestamp), corrigée dans cette issue. Room utilise `PRAGMA user_version` pour détecter une incompatibilité de **schéma** (déclenchant `fallbackToDestructiveMigration()`), pas pour détecter une DB « plus récente » en fraîcheur de données — c'est `db_metadata.export_date`/`version` qu'il faut comparer pour ça. Point important pour la future implémentation du téléchargement côté app (issue #118).

## Fichiers livrés dans cette issue

- `docs/dev/adr/0001-separation-maj-appli-donnees.md` + `docs/dev/adr/README.md` — premier ADR du repo, nouvelle convention à poursuivre pour les futures décisions d'architecture significatives.
- `scripts/generate_data_release_metadata.py` (+ `tests/test_generate_data_release_metadata.py`, TDD RED→GREEN, 6 tests) — génère `metadata.json` (export_date, SHA-256, taille, compteurs) à partir de `lmelp.db`, pour permettre une future vérification de MAJ côté app sans télécharger tout le fichier (~4.4 Mo actuellement).
- `.github/workflows/publish-data-release.yml` — workflow `workflow_dispatch` (pas de déclenchement sur tag), régénère optionnellement `lmelp.db` depuis `MONGO_URI`, génère les métadonnées, publie sur la release `data-latest`.
- `scripts/docker_export_and_publish_release.sh` — nouvelle commande `export-and-publish-release` pour le container `lmelp-export`, publie via `gh release upload`. Nécessite `GH_TOKEN` (scope `contents:write`).
- `Dockerfile.export` — ajout d'`anacron` + `gh` CLI (installé via le repo APT officiel GitHub CLI), job quotidien `/etc/anacron.daily/publish-data-release`, entrypoint custom `/docker-entrypoint-anacron.sh` qui lance la boucle anacron en arrière-plan avant `CMD ["sleep", "infinity"]`.
- `app/src/main/java/com/lmelp/mobile/ui/about/AboutScreen.kt` + nouveau `app/src/main/java/com/lmelp/mobile/viewmodel/AboutViewModel.kt` — l'écran À propos affiche désormais une section "Données" (date d'export, nb émissions/livres/avis) via `MetadataRepository.getDbInfo()`, qui existait déjà mais n'était jusqu'ici jamais branché à aucune UI (seul `AccueilCarScreen.kt`, l'écran Android Auto, l'utilisait partiellement). `app/src/main/java/com/lmelp/mobile/Navigation.kt` mis à jour pour passer `app.metadataRepository` à `AboutScreen`.
- Test `app/src/test/java/com/lmelp/mobile/DbInfoFormatterTest.kt` (TDD RED→GREEN) — teste la fonction pure `formatDbInfoSummary()` extraite dans `AboutScreen.kt`, suivant le pattern déjà établi par `parseChangelog()`/`ChangelogParserTest.kt` (pas de `createComposeRule` dans ce repo, tests Compose = logique pure extraite et testée en JVM).
- `docs/dev/.nav.yml` — ajout de la section ADR dans la nav mkdocs (fichier `.nav.yml` géré par le plugin `awesome-nav`, pas de nav statique centralisée dans `mkdocs.yml`).
- `CLAUDE.md`, `docs/dev/build_deploy_apk.md`, `docs/user/mise_a_jour_episode.md` — mis à jour avec des renvois vers l'ADR et le nouveau mécanisme cible.

## Issues de suivi créées

1. `lmelp-mobile#118` — implémentation Kotlin du téléchargement/vérification de `lmelp.db` côté app (remplacement du fichier Room avec gestion WAL, cf. bug connu issue #101).
2. `lmelp-mobile#119` — faisabilité distribution APK (Google Play Store) et/ou ADB sans fil en dev/debug.
3. `back-office-lmelp#262` — visibilité côté serveur des versions app/DB déployées.
4. `docker-lmelp#56` — provisioning du secret `GH_TOKEN` + validation de l'anacron en conditions réelles sur le NAS.

## Point de process notable : plusieurs itérations de cadrage en mode plan

Cette issue a nécessité plusieurs allers-retours en mode plan avant validation (contrairement aux bugfixes habituels) car c'était une issue ouverte "à affiner". Points clés qui ont fait évoluer le plan initial :
- Le choix d'hébergement ne pouvait pas être tranché sans connaître le plan long terme (multi-utilisateurs) de l'utilisateur — a nécessité une question explicite avant de proposer une architecture.
- La question "comment le NAS reste synchro avec GitHub" a révélé qu'il fallait creuser l'existant (`ghcr.io/castorfou/lmelp-mobile-export`, `docker-compose.yml` de `docker-lmelp`) plutôt que d'inventer un nouveau mécanisme — l'utilisateur a explicitement demandé de vérifier s'il existait déjà un pattern anacron réutilisable (celui de `mongodb.Dockerfile`) avant de proposer une solution cron générique.
- "Réseau optionnel" nécessitait d'être clarifié : ne signifie pas qu'aucun réseau n'est jamais utilisé, mais que son absence ne dégrade jamais l'usage normal — distinction importante à bien poser dans l'ADR.

## Bug découvert pendant les tests manuels (hors scope, non traité)

En testant l'écran À propos avec le nouvel affichage, l'utilisateur a constaté que `lmelp.db` embarqué dans l'app (committé dans le repo) est périmé : 179 émissions / dernière du 02/07/2026 alors que la vraie base a 180 émissions / dernière du 02/08/2026. Confirmé comme un problème de fraîcheur de données indépendant du code de cette PR (personne n'a relancé l'export récemment) — volontairement non corrigé dans cette PR, à traiter séparément via `python scripts/export_mongo_to_sqlite.py --force`.

## Note de troubleshooting ADB (ajoutée à la doc)

Symptôme observé : le téléphone est bien détecté par `lsusb`/gestionnaire de périphériques sur l'hôte, mais `adb devices` reste vide même après `adb kill-server && adb -a start-server`. Un simple **redémarrage du téléphone** a résolu le problème (cause exacte côté adb/udev non identifiée). Ajouté à `docs/dev/build_deploy_apk.md` dans la section troubleshooting ADB existante.
