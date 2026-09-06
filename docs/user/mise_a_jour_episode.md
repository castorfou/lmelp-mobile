## Ajouter l'épisode à la base de données LMELP

!!! tip "Mise à jour de la base sans USB"
    Depuis l'implémentation de l'[issue #118](https://github.com/castorfou/lmelp-mobile/issues/118), l'application peut télécharger elle-même la dernière base de données publiée, sans branchement USB ni laptop — voir la section [Mettre à jour la base depuis l'application](#mettre-a-jour-la-base-depuis-lapplication) plus bas. La procédure ADB décrite ensuite reste utile en développement local (build debug, test avant publication de la release).

à la diffusion d'un nouvel épisode de l'émission, le début est automatisé :

> ![](img/favicon_automatisch.png) dans **automatisch** :
>
> - à la publication d'une nouvelle émission, le flux 'le masque et la plume se lance'
> - notifie l'utilisateur via **ntfy.sh**
> - et pour les épisodes de plus de 15 minutes, active l'api rest `/api/rss/sync` de backoffice-backend
>
> ![](img/favicon_lmelp-backoffice.png) dans **lmelp-backoffice** - backend `/api/rss/sync` :
> - download l'audio
> - notifie l'utilisateur via **ntfy.sh**
>
> ??? info "en cas de détection de type incorrect"
>     il y a un mécanisme de détection automatique du type d'épisodes (film/théâtre/livre) mais parfois cela disfonctionne et un épisode film ou théâtre peut être injustement détecté comme livre
>
>     dans ce cas il faut "cacher" l'épisode incorrectement détecté en allant dans lmelp-backoffice - frontend, masquer les épisodes et jouer sur la visibilité de l'épisode (passer de visible à masqué)

la suite est manuelle :

> ![](img/favicon_lmelp-frontoffice.png) depuis **lmelp-frontoffice** :
>
> - ![](img/telecharger_transcriptions.png) déclenche la transcription PGX automatisée
>
> ??? info "mode d'emploi - transcription PGX (automatisée)"
>     Le pipeline de transcription (envoi de l'audio, attente, rapatriement de la
>     transcription, intégration en base) est **entièrement automatisé** — plus besoin de
>     commandes `scp`/`ssh` manuelles.
>
>     1. Allumer PGX **manuellement** (pas de réveil à distance automatisé — Wi-Fi
>        uniquement, veille système désactivée pour la stabilité GPU).
>     2. *(Optionnel)* Depuis l'interface Streamlit lmelp, page **PGX**, vérifier la
>        disponibilité de la station (checklist : joignabilité, authentification SSH,
>        répertoires distants) avant de lancer une transcription.
>
>     Voir la [doc de référence du pipeline PGX](https://castorfou.github.io/lmelp/user/transcription-pgx/)
>     (repo `castorfou/lmelp`) pour le détail complet.
>
> ![](img/favicon_lmelp-backoffice.png) depuis **lmelp-backoffice** - frontend :
>
> - ![](img/generation_avis_critiques.png) page **Génération Avis Critiques (LLM)** : crée un summary depuis la transcription d'un épisode.
> Note: cliquer sur **Episodes sans Avis Critiques** dans la zone Informations générales dans la zone Informations générales nous améne à cette page
> - ![](img/livres_et_auteurs.png) page **Livres et Auteurs** : extrait les livres/auteurs de l'épisode sur la base du summary et se base sur les metadonnées babelio (il faut au préalable lancer un VPN système (pas browser), et valider le captcha d'accès [babelio](https://www.babelio.com/)) pour corriger les titres/auteurs/éditeurs.
> Note: cliquer sur **Avis Critiques sans Analyse** dans la zone Informations générales nous amène à cette page
> - ![](img/identification_des_critiques.png) **Identification des Critiques** : nécessaire si un critique participe pour la 1ère fois à l'émission
> - ![](img/liaison_babelio.png) **Liaison Babelio** : pour lier les oeuvres / auteurs à leurs pages babelio respectives, ainsi que le lien vers la couverture de l'oeuvre.
>  Note: cliquer sur **Livres sans lien Babelio** / **Auteurs sans lien Babelio** dans la zone Informations générales nous amène à cette page
> - ![](img/emissions.png) **Emissions** : visu du resultat de l'émission structurée : toutes les oeuvres doivent aparaitre identifiées, notées
>  Note: cliquer sur **Episodes sans Emission** dans la zone Informations générales nous amène à cette page


à l'issue de tout cela la base de données a été enrichie avec ce nouvel episode

## Mettre à jour la base depuis l'application

Une fois la base de données enrichie côté serveur (section précédente) et publiée automatiquement sur la GitHub Release dédiée aux données (tag `data-v{N}`, voir [ADR 0001](../dev/adr/0001-separation-maj-appli-donnees.md)), l'application mobile peut récupérer la nouvelle version elle-même :

1. Ouvrir l'écran **À propos** (icône ⚙️ en haut à droite de l'accueil — un point vert y apparaît automatiquement si une mise à jour a été détectée au lancement de l'app).
2. Appuyer sur **Vérifier les mises à jour** (ou directement sur **Mettre à jour** si le point vert était déjà présent).
3. L'app télécharge la base (~5 Mo), vérifie son intégrité, puis l'installe.
4. Un message **"Mise à jour appliquée ! Veuillez rouvrir l'application."** s'affiche puis l'app se ferme.
5. Rouvrir l'app depuis l'écran d'accueil du téléphone — les nouvelles données sont disponibles.

Le téléchargement nécessite une connexion internet mais reste **entièrement optionnel** : sans réseau, l'app continue de fonctionner normalement avec les données déjà en local (principe offline-first).

## Intégrer la nouvelle base de données LMELP à l'application mobile (legacy, dev-only)

Le logiciel lmelp-mobile ne change pas, seule la base de données évolue. Cette procédure ADB reste utile en développement local (test d'un build debug) mais n'est plus nécessaire en usage courant depuis la section précédente.

### Pré-requis

- adb est installé et [fonctionnel](#en-cas-de-problème-adb)
- Le téléphone est branché en USB au laptop
- Le débogage USB est activé (Paramètres → Options développeur)
- La stack docker-lmelp est démarrée (en suivant [Déploiement avec Portainer](https://castorfou.github.io/docker-lmelp/user/portainer/), les conteneurs `lmelp-mongo` et `lmelp-export` doivent tourner)

### Commande unique

```bash
lmelp-update-mobile
```

Le script `scripts/lmelp-update-mobile.sh` (à copier une fois sur le laptop dans `~/bin/`) fait tout :

1. Redémarre le daemon ADB en mode réseau (`0.0.0.0:5037`)
2. Vérifie qu'un téléphone est connecté
3. Vérifie que le container `lmelp-export` tourne
4. Lance l'export MongoDB → SQLite (avec données Calibre pour le filtre "Lus")
5. Vérifie l'intégrité de la base
6. Pousse la base sur le téléphone via ADB
7. Redémarre l'app Android

### Installation du script (une seule fois)

```bash
cp scripts/lmelp-update-mobile.sh ~/bin/lmelp-update-mobile
chmod +x ~/bin/lmelp-update-mobile
```

### Démarrer le container lmelp-export

Il y a 3 niveaux selon l'avancement de la configuration :

**Niveau 1 — image locale (dev/test)** : image buildée localement depuis le repo

```bash
# depuis le repo lmelp-mobile
docker build -f Dockerfile.export -t lmelp-mobile-export:local .
docker run -d --name lmelp-export \
  --network lmelp-stack_lmelp-network \
  --add-host host-gateway:host-gateway \
  -v "/home/guillaume/Calibre Library:/calibre:ro" \
  -e LMELP_MONGO_URI=mongodb://mongo:27017 \
  -e LMELP_CALIBRE_DB=/calibre/metadata.db \
  -e LMELP_CALIBRE_VIRTUAL_LIBRARY=guillaume \
  -e ADB_HOST=host-gateway \
  -e ADB_PORT=5037 \
  lmelp-mobile-export:local
```

**Niveau 2 — image ghcr.io (usage normal en attendant docker-lmelp#41)** : image publiée automatiquement par la CI, gérée par Watchtower

```bash
docker run -d --name lmelp-export \
  --network lmelp-stack_lmelp-network \
  --add-host host-gateway:host-gateway \
  -v "/home/guillaume/Calibre Library:/calibre:ro" \
  -e LMELP_MONGO_URI=mongodb://mongo:27017 \
  -e LMELP_CALIBRE_DB=/calibre/metadata.db \
  -e LMELP_CALIBRE_VIRTUAL_LIBRARY=guillaume \
  -e ADB_HOST=host-gateway \
  -e ADB_PORT=5037 \
  --label "com.centurylinklabs.watchtower.enable=true" \
  ghcr.io/castorfou/lmelp-mobile-export:latest
```

**Niveau 3 — intégré à la stack docker-lmelp (cible finale)** : le container démarre automatiquement avec `docker compose up -d`, voir [docker-lmelp#41](https://github.com/castorfou/docker-lmelp/issues/41).

### En cas de problème ADB

Si `adb devices` ne voit pas le téléphone :

- Vérifier que le mode USB est sur **Transfert de fichiers**
- Valider la popup "Autoriser le débogage USB" sur le téléphone
- En dernier recours : `adb kill-server && adb -a start-server`
- En tout dernier recours : redémarre le téléphone, ça a solutionné plusieurs fois le problème

Voir aussi [docs/dev/build_deploy_apk.md](../dev/build_deploy_apk.md) pour le diagnostic complet.
