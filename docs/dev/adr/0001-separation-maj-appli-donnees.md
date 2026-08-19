# ADR 0001 — Séparer la mise à jour de l'application et la mise à jour des données

- Statut : Accepté
- Date : 2026-08-19
- Issue : [#116](https://github.com/castorfou/lmelp-mobile/issues/116)

## Contexte

Le processus actuel de [mise à jour d'un épisode](../../user/mise_a_jour_episode.md) mélange deux préoccupations différentes derrière une seule commande ADB (`lmelp-update-mobile` → `docker exec lmelp-export export-and-push`) :

- l'installation/mise à jour de l'**application** mobile elle-même (nouvelle fonctionnalité) — **rare**
- la mise à jour des **données** (nouvel épisode ajouté à la base) — **fréquente** (~1x/semaine)

ADB n'est en réalité nécessaire que pour le premier cas. De plus, le mécanisme actuel de remplacement de `lmelp.db` sur le téléphone dépend de `run-as`, qui n'est disponible que sur un build **debug** (`android:debuggable`) — il ne fonctionnerait jamais avec un APK release installé normalement par un utilisateur final.

Élément déclencheur : la stack `docker-lmelp` migre vers un NAS Synology ([castorfou/docker-lmelp#47](https://github.com/castorfou/docker-lmelp/issues/47)). En usage courant, il n'y aura plus de laptop branché en USB au téléphone — le mécanisme ADB actuel devient impraticable pour la mise à jour des données, qui est pourtant le cas fréquent.

## Décision

### Flux de données (`lmelp.db`) — sujet principal, traité maintenant

Publier `lmelp.db` comme **asset d'une GitHub Release dédiée aux données** (tag `data-latest`), distincte de la release APK (`vX.Y.Z`) :

- Réutilise l'infrastructure CI existante — `release.yml` publie déjà l'APK de la même manière.
- Zéro nouvelle infrastructure serveur à ce stade.
- Un asset secondaire `metadata.json` (date d'export, nombre d'émissions/livres/avis, taille, SHA-256) est publié à côté du fichier, pour permettre une future vérification "y a-t-il une mise à jour ?" sans télécharger tout le fichier (~4-5 Mo).

**Mécanisme de publication** : le container `lmelp-export` (image `ghcr.io/castorfou/lmelp-mobile-export`, buildée depuis `Dockerfile.export` dans ce repo) contient déjà `export_mongo_to_sqlite.py` + ADB et tourne en veille sur l'hôte Docker, piloté par `docker exec lmelp-export <commande>`. Une nouvelle commande `export-and-publish-release` (script `scripts/docker_export_and_publish_release.sh`) exporte, vérifie, génère `metadata.json` et publie sur GitHub via `gh release upload`.

**Automatisation** : un job **anacron** est embarqué dans `Dockerfile.export`, suivant le même pattern déjà validé dans `mongodb.Dockerfile` (repo `docker-lmelp`, service `mongo`) pour la rotation de logs et le backup MongoDB. Anacron est choisi (plutôt que cron classique) précisément parce qu'un NAS/laptop peut être éteint aux heures programmées : anacron rattrape le job au prochain démarrage au lieu de le sauter. La commande `export-and-publish-release` reste aussi invocable manuellement.

**Déclencheur côté app** (conception uniquement — implémentation dans une issue de suivi) : vérification **au lancement de l'app** (non bloquant, échec réseau silencieux) + **bouton manuel** "Vérifier les mises à jour", probablement dans l'écran À propos.

**Contrainte non négociable** : l'app reste strictement **offline-first**. Le réseau est optionnel et best-effort — son absence ou son échec ne doit jamais bloquer ou dégrader l'usage normal de l'app (consultation des données déjà en local). La permission `INTERNET` est déjà présente dans le manifest (utilisée aujourd'hui pour les couvertures de livres via Coil) ; aucun changement de permission n'est nécessaire pour ce mécanisme.

**Page technique version app/DB** (ajoutée par cette issue) : l'écran À propos (`AboutScreen.kt`), qui affiche déjà la version de l'app (commit Git, date de build) et un historique des commits, affiche désormais aussi les informations de la base locale (date d'export, nombre d'émissions/livres/avis) via `MetadataRepository.getDbInfo()` — déjà implémenté mais jusqu'ici jamais branché à l'UI. Combiné à une future vue côté serveur (voir Suivi), cela permet de comparer "ce que mon téléphone a" vs "ce qui est publié".

### Flux application (APK) — point périphérique, hors scope technique ici

Le mécanisme USB+ADB pour l'APK est lui aussi remis en cause par la migration NAS. Piste retenue comme direction future : publication sur le **Google Play Store**, qui résout nativement la distribution et la mise à jour de l'APK. Implications connues mais **non traitées dans cette issue** : compte développeur Google, processus de review, question des droits sur le contenu Le Masque et la Plume / France Inter diffusé dans l'app. Voir issue de suivi.

Le sous-sujet "ADB sans fil depuis le NAS" (pairing debug sans fil Android 11+, `adb connect`) mentionné dans l'issue initiale devient obsolète pour le flux de données, remplacé par le téléchargement HTTP direct — strictement supérieur ici (pas de dépendance USB/pairing, fonctionne avec un build release). Il reste une piste possible pour un usage dev/debug ponctuel de déploiement APK sur un device de test — traité dans l'issue de suivi dédiée à la distribution APK, pas résolu ici.

## Trajectoire court terme vs long terme

**Court terme (cette issue + issues de suivi)** : GitHub Release asset, lecture seule, pas d'authentification, une base de données commune pour tous les utilisateurs de l'app (aujourd'hui : un seul utilisateur, l'auteur).

**Long terme (projet séparé, non démarré, hors scope)** : l'auteur envisage de rendre le projet public avec :

- multi-utilisateurs
- un rôle admin qui met à jour la base commune des émissions (passées et futures)
- un mode anonyme de consultation web + mobile
- un mode utilisateur basé sur Calibre pour collecter les goûts (livres lus, notes) et permettre des recommandations personnalisées

Ce futur backend nécessitera authentification, profils utilisateurs, et très probablement un vrai service HTTP/API — pas un simple asset statique. Il **remplacera** le mécanisme décrit ici. Cette décision ne ferme pas cette porte : le choix "GitHub Release asset" est explicitement une étape 1 pragmatique et temporaire. Le format `metadata.json` reste volontairement simple (JSON plat) pour ne pas sur-investir dans un format qui sera de toute façon remplacé par une vraie API plus tard.

## Alternatives rejetées

| Alternative | Raison du rejet |
|---|---|
| NAS statique seul (fichier servi en HTTP direct par le NAS, sans GitHub) | Nécessite d'exposer le NAS à Internet (reverse proxy, TLS, nom de domaine, sécurité) — infrastructure nouvelle à maintenir alors qu'une GitHub Release est déjà gratuite et en place. Envisageable plus tard si le volume/fréquence de données dépasse les limites GitHub Release, mais prématuré aujourd'hui. |
| Service HTTP dédié tout de suite (API exposant `/lmelp.db` + endpoint de métadonnées) | Sur-ingénierie par rapport au besoin actuel (lecture seule, un seul éditeur de données). Anticipe le futur backend multi-utilisateurs sans que ses besoins réels (auth, modèle de données par utilisateur) soient encore connus — risque de travail jeté. À réévaluer quand le projet multi-utilisateurs démarrera réellement. |
| ADB sans fil depuis le NAS (pairing debug sans fil Android 11+) | Résout génériquement "pousser un fichier sans USB" mais reste fragile (stabilité du pairing dans la durée, dépendance à un serveur ADB joignable en TCP) et exige un build debug (`run-as`) — ne fonctionnerait jamais avec un APK release installé normalement. Le HTTP direct est strictement supérieur pour ce cas d'usage. |

## Conséquences

**Positives :**

- Découplage net : la mise à jour des données ne nécessite plus jamais ADB, USB, laptop ni build debug.
- Réutilisation totale de l'infrastructure CI existante (`softprops/action-gh-release` déjà utilisé dans `release.yml`).
- Chemin de migration clair vers un futur backend multi-utilisateurs, sans travail jeté.

**Négatives / risques :**

- GitHub Release asset n'a pas de mécanisme de delta/diff : chaque mise à jour télécharge le fichier complet (~4-5 Mo aujourd'hui). Acceptable au volume actuel ; à surveiller si la base grossit significativement.
- Dépendance à la disponibilité de GitHub côté téléchargement — cohérent avec le caractère offline-first (l'app fonctionne sans, le téléchargement est un best-effort).
- Le tag `data-latest` republié en continu ne conserve pas d'historique des versions de données dans les Releases GitHub — acceptable car `db_metadata`/`metadata.json` conservent `export_date`, et Git ne versionnait pas non plus les anciennes DB jusqu'ici.
- Le déclenchement automatique dépend d'un token GitHub (`GH_TOKEN`, scope `contents:write`) provisionné côté NAS — nouveau secret à créer et sécuriser (voir issue de suivi `docker-lmelp`).

## Suivi

Issues créées dans le cadre de cette décision :

1. [lmelp-mobile#118](https://github.com/castorfou/lmelp-mobile/issues/118) — implémentation Kotlin du téléchargement/vérification de `lmelp.db` côté app.
2. [lmelp-mobile#119](https://github.com/castorfou/lmelp-mobile/issues/119) — faisabilité distribution APK (Google Play Store) et/ou ADB sans fil en dev/debug.
3. [back-office-lmelp#262](https://github.com/castorfou/back-office-lmelp/issues/262) — visibilité côté serveur des versions app/DB déployées.
4. [docker-lmelp#56](https://github.com/castorfou/docker-lmelp/issues/56) — provisioning du secret `GH_TOKEN` et validation de l'anacron en conditions réelles sur le NAS.
