# Écosystème lmelp

Vue d'ensemble des briques qui alimentent l'application lmelp-mobile : qui alimente qui, et **ce qui déclenche chaque flux**. Les détails propres à chaque brique sont dans la documentation de [back-office-lmelp](https://castorfou.github.io/back-office-lmelp/) et de [docker-lmelp](https://castorfou.github.io/docker-lmelp/) ; le parcours d'un nouvel épisode, pas à pas, est dans [Mise à jour d'un épisode](user/mise_a_jour_episode.md).

## Mise à jour des données

Le schéma se lit de haut en bas, dans l'ordre où circulent les données : Radio France, Automatisch, le back-office lmelp et ses services d'enrichissement, l'export, la release GitHub, puis le téléphone. À côté, lmelp-pap (Projet d'Archivage Patrimonial) croise l'INA, la Wayback Machine, radiofrance.fr et les émissions vérifiées de lmelp pour reconstituer les émissions de 1955 à 2016. Survoler ou toucher une étiquette soulignée affiche son détail ; le bouton **Plein écran** l'agrandit.

```mermaid
flowchart TB
    subgraph RADIO["Radio France"]
        RSS["France Inter<br/>flux RSS Le Masque et la Plume"]
        RF["radiofrance.fr<br/>page et audio de l'émission"]
    end

    subgraph LAPTOP["Laptop"]
        FF["Firefox<br/>connecté à Babelio"]
        CAL["Calibre<br/>bibliothèque source"]
    end

    AUTO["Automatisch"]

    subgraph NAS["NAS Synology (Docker)"]
        BACK["lmelp backend + frontend<br/>lmelp.ascot63.synology.me"]
        DRIVE["Copie Drive<br/>Calibre Library"]
        MONGO[("MongoDB<br/>lmelp-mongo")]
        CWA["Calibre-Web-Automated"]
        EXPORT["lmelp-export"]
        PAP["lmelp-pap<br/>pap.lmelp.ascot63.synology.me"]
        AUDIOS[("Fichiers audio<br/>volume Docker du NAS")]
    end

    INA["INA<br/>catalogue.ina.fr"]
    WB["Wayback Machine<br/>web.archive.org"]

    PGX["PGX<br/>transcription audio"]
    BAB["Babelio<br/>recherche, pages livres et auteurs"]
    AZ["Azure OpenAI<br/>LLM"]

    REL["GitHub : release data-v{N}<br/>lmelp.db + metadata.json"]
    NTFY["ntfy.sh<br/>notifications"]

    subgraph PHONE["Téléphone"]
        MOB["app lmelp-mobile"]
        PWA["PWA lmelp"]
    end

    RSS -->|"nouvel épisode@@nouvel épisode publié dans le flux RSS<br/>flux interrogé toutes les 15 minutes"| AUTO
    AUTO -->|"sync + transcription@@POST /api/rss/sync<br/>puis /api/pgx/transcription/start"| BACK
    RF -->|"audio@@téléchargement audio : rss/sync<br/>ou manuel, page Ajouter une émission"| BACK
    FF -->|"cookie@@manuel : copie du cookie<br/>valable ~30 min"| BACK
    CAL -->|"Synology Drive@@Synology Drive<br/>continu, quelques secondes"| DRIVE
    DRIVE -->|"metadata.db@@lecture seule ;<br/>relue dès que metadata.db change"| BACK
    BACK <-->|"transcription@@ssh/scp : envoi audio, retour transcription<br/>réessai toutes les heures si PGX injoignable"| PGX
    BACK <-->|"validation@@manuel : Valider avec Babelio, Liaison Babelio,<br/>couvertures ; 1 requête / BABELIO_FAIR_SEC,<br/>empreinte Firefox"| BAB
    BACK -->|"avis critiques@@manuel : Génération Avis Critiques"| AZ
    BACK <-->|"lecture/écriture@@chaque action ;<br/>fiches pap_remises en lecture seule :<br/>tuile Émissions anciennes à intégrer (cache de 5 min),<br/>manuel, page Émissions anciennes, bouton Intégrer :<br/>création de l'épisode (source pap)"| MONGO
    DRIVE -->|"00:05@@synchronisation nocturne<br/>00:05 heure de Paris (tâche DSM)"| CWA
    MONGO -->|"horaire@@toutes les heures"| EXPORT
    DRIVE -->|"horaire@@toutes les heures<br/>livres lus"| EXPORT
    EXPORT -->|"si changement@@si les données ont changé :<br/>republie data-v{N}"| REL
    REL -->|"lmelp.db@@détection au lancement (point vert) ;<br/>téléchargement manuel :<br/>À propos, Vérifier les mises à jour"| MOB
    BACK -->|"interface web@@navigation depuis le téléphone"| PWA
    INA -->|"notices@@manuel : notices « Le masque et la plume »<br/>du catalogue INA (Playwright)"| PAP
    WB -->|"archives@@manuel : anciennes listes France Inter (2002-2012),<br/>fichiers audio du podcast (API CDX)"| PAP
    RF -->|"fiches@@manuel : fiches d'émission<br/>des pages podcast radiofrance.fr"| PAP
    MONGO <-->|"episodes + pap_*@@manuel : lecture seule des épisodes lmelp<br/>(collection episodes, émissions vérifiées) ;<br/>lecture/écriture des collections pap_* :<br/>maturité des enregistrements, vérification des URL des intégraux,<br/>fiches pap_remises des enregistrements mis à disposition"| PAP
    BACK <-->|"intégraux@@/volume1/docker/lmelp/audios<br/>intégraux valides des émissions ;<br/>audio d'une émission ancienne livré par pap,<br/>empreinte SHA-256 vérifiée à l'intégration"| AUDIOS
    PAP <-->|"enregistrements@@/volume1/docker/lmelp/pap/audios : enregistrements rapatriés ;<br/>manuel, bouton Mettre à disposition de lmelp :<br/>copie vers audios/année/nom du back-office (/audios-lmelp)"| AUDIOS
    PAP -->|"interface web@@réseau local uniquement :<br/>reverse proxy DSM, port 8085 du NAS"| LAPTOP
    AUTO -->|"notification@@notification d'un nouvel épisode"| NTFY
    BACK -->|"notifications@@chaque étape traitée,<br/>dont l'intégration d'une émission ancienne (préfixe pap)"| NTFY
    EXPORT -->|"publication / échec@@nouvelles données publiées ;<br/>premier échec, puis rétablissement"| NTFY
    NTFY -->|"notifications"| PHONE
```

## Mise à jour du code

Indépendante du flux de données. Un push sur `main` reconstruit les images Docker concernées par les fichiers modifiés (back-office-lmelp, `lmelp-export` de lmelp-mobile, `lmelp-mongo` de docker-lmelp, lmelp-pap), que Watchtower installe sur le NAS, et publie une préversion de l'appli si le push la modifie ; un tag `vX.Y.Z` publie une version de l'appli. L'appli détecte elle-même les deux (voir [Installer et mettre à jour l'appli](user/installer_mettre_a_jour.md)).

```mermaid
flowchart TB
    PUSH["push sur main<br/>back-office-lmelp, lmelp-mobile, docker-lmelp, lmelp-pap"]
    TAG["tag vX.Y.Z<br/>lmelp-mobile"]

    subgraph GH["GitHub"]
        CI["CI GitHub Actions<br/>images ghcr.io"]
        PREV["release app-preview<br/>APK préversion X.Y.Z+N"]
        APK["release vX.Y.Z<br/>lmelp-mobile-vX.Y.Z.apk"]
    end

    subgraph NAS["NAS Synology (Docker)"]
        WT["Watchtower"]
        BACK["lmelp backend + frontend"]
        EXPORT["lmelp-export"]
        MONGO[("MongoDB<br/>lmelp-mongo")]
        PAP["lmelp-pap"]
    end

    subgraph PHONE["Téléphone"]
        MOB["app lmelp-mobile"]
    end

    PUSH --> CI
    PUSH -->|"préversion@@app-preview.yml, si le push lmelp-mobile touche l'appli :<br/>APK release signé X.Y.Z+N<br/>(N commits depuis le dernier tag stable) ;<br/>la release app-preview est supprimée puis recréée"| PREV
    TAG -->|"version@@release.yml : APK release signé,<br/>signature vérifiée avec apksigner"| APK
    CI -->|"nouvelles images@@images publiées sur ghcr.io,<br/>puis appel de l'API Watchtower du NAS"| WT
    WT -->|"recrée@@recrée les conteneurs<br/>avec les nouvelles images"| BACK
    WT -->|"recrée@@recrée le conteneur<br/>sur la nouvelle image"| EXPORT
    WT -->|"recrée@@recrée le conteneur mongo<br/>sur la nouvelle image lmelp-mongo"| MONGO
    WT -->|"recrée@@recrée le conteneur pap<br/>sur la nouvelle image lmelp-pap"| PAP
    PREV -->|"nouvelle version@@détection au lancement de l'appli (badge) ;<br/>À propos : Télécharger et installer"| MOB
    APK -->|"nouvelle version@@détection au lancement de l'appli (badge) ;<br/>À propos : Télécharger et installer"| MOB
```

## Déclencheurs

Le tableau reprend chaque flux en détail.

| Flux | Déclencheur |
|---|---|
| RSS → Automatisch → back-office (`POST /api/rss/sync` puis `/api/pgx/transcription/start`) | publication d'un nouvel épisode dans le flux RSS, qu'Automatisch interroge **toutes les 15 minutes** (valeur par défaut d'un flux Automatisch, aucun intervalle configuré) |
| Ajout d'une émission absente du RSS | manuel, page **Ajouter une émission** (URL de la page radiofrance.fr) |
| Intégration d'une émission ancienne livrée par lmelp-pap (fiche `pap_remises` + audio copié dans le dossier audio de lmelp) | **manuel**, émission par émission, page **Émissions anciennes** du back-office (`/emissions-anciennes`, bouton **Intégrer**), ni Automatisch ni pap ; le back-office vérifie l'empreinte SHA-256 du fichier, crée l'épisode (`source: "pap"`, `pap_remise_id`), puis lance la transcription PGX en mode `api` (réessai toutes les heures si PGX est injoignable), comme pour l'ajout depuis radiofrance.fr. pap n'écrit jamais dans `episodes`, le back-office jamais dans `pap_remises`, et il ne renomme ni ne déplace le fichier audio |
| Transcription PGX (ssh/scp : envoi de l'audio, retour de la transcription) | lancée par Automatisch ou à la main ; réessai **toutes les heures** si PGX est injoignable |
| Avis critiques (LLM) | manuel, page **Génération Avis Critiques** |
| Babelio (Valider avec Babelio, Liaison Babelio, couvertures) | manuel uniquement (aucune requête à l'ouverture des pages), 1 requête par `BABELIO_FAIR_SEC`, empreinte Firefox ; cookie copié depuis Firefox **connecté** à Babelio, valable ~30 min |
| Calibre laptop → copie Drive | **continu** (Synology Drive, quelques secondes) |
| Copie Drive → back-office | lecture seule, relue dès que `metadata.db` change |
| Copie Drive → Calibre-Web-Automated | **chaque nuit à 00:05 heure de Paris** (tâche planifiée DSM, CWA seulement) |
| MongoDB + Calibre (livres lus) → `lmelp-export` → release `data-v{N}` | **toutes les heures** (`PUBLISH_INTERVAL`), publication seulement si le contenu a changé (la release est alors republiée sur place) ; immédiat avec `docker exec lmelp-export export-and-publish-release` |
| Release → app mobile | détection au lancement de l'app (point vert) ; téléchargement **manuel** (À propos → Vérifier les mises à jour) |
| Nouvelle version de code | push sur `main` → CI → images ghcr.io → Watchtower recrée les conteneurs : back-office (back-office-lmelp), `lmelp-export` (lmelp-mobile), `mongo` sur l'image `lmelp-mongo` (docker-lmelp, `build-mongo-image.yml` : push touchant `mongodb.Dockerfile`, `scripts/` ou `config/mongod.conf`), `lmelp-pap` (lmelp-pap, `docker.yml`) |
| Préversion de l'appli → release `app-preview` | push sur `main` qui touche l'appli (`app-preview.yml`), APK `X.Y.Z+N` ; rien si aucun commit depuis le dernier tag |
| Version de l'appli → release `vX.Y.Z` | tag `vX.Y.Z` (`release.yml`) |
| Release de l'appli → app mobile | détection au lancement de l'app (badge) ; installation **manuelle** (À propos → Télécharger et installer) |
| Sources de lmelp-pap (INA, Wayback Machine, radiofrance.fr, MongoDB lmelp) | **manuel**, aucune tâche planifiée : une collecte par source (`lmelp-pap emissions`), mise en cache et pas relancée une fois terminée, sauf `--refresh` ; MongoDB en lecture seule (`masque_et_la_plume.episodes`, émissions vérifiées), écriture des arbitrages dans des collections `pap_*` à venir ([castorfou/lmelp-pap#65](https://github.com/castorfou/lmelp-pap/issues/65)) |
| Fichiers audio (volume Docker du NAS) | back-office : `/volume1/docker/lmelp/audios` (`AUDIO_PATH`), intégraux valides des émissions, écrits à chaque téléchargement audio ; lmelp-pap : `/volume1/docker/lmelp/pap/audios` (`PAP_PATH_HOTE`, monté sur `/data`, qui contient aussi `cache/`, `processed/` et `logs/`), enregistrements rapatriés, avec le dossier du back-office monté sur `/audios-lmelp` pour la mise à disposition ; `cache/` et `processed/` de pap amorcés à la main depuis le dépôt lmelp-pap |
| lmelp-pap ↔ MongoDB (collections `pap_*`) | pap reçoit `MONGO_URI=mongodb://mongo:27017` et `MONGO_DB` (`masque_et_la_plume` par défaut) : ses collections `pap_*` (maturité des enregistrements, vérification des URL des intégraux, ADR 0004 de lmelp-pap) sont dans la base `masque_et_la_plume` du service `mongo` ; vérification des URL **manuelle**, page `/liste` de pap, bouton **Vérifier les URL des intégraux** (`POST /api/verifications`) |
| lmelp-pap → back-office (mise à disposition d'un enregistrement) | **manuel**, bouton **Mettre à disposition de lmelp** de pap : copie de `pap/audios/<année>/<nom>` vers `audios/<année>/<nom>` du back-office (dossier `AUDIO_PATH`, monté sur `/audios-lmelp` dans pap et sur `/app/audios` dans le backend) et fiche dans la collection `pap_remises` ; le back-office lit `pap_remises` en lecture seule pour sa tuile d'accueil **Émissions anciennes à intégrer** (fiches dont le fichier est présent, clé `pap_remises_a_integrer_count`), recalculée à l'affichage de l'accueil dans le cache du dashboard (5 min), bouton **Actualiser** pour forcer ; une ligne `WARNING` par fichier absent dans les logs du backend signale un montage `/audios-lmelp` ↔ `/app/audios` mal réglé |
| lmelp-pap → navigateur | appli web sur le réseau local uniquement : `https://pap.lmelp.ascot63.synology.me` (reverse proxy DSM, port 8085 du NAS, 8090 dans le conteneur) |
| Notifications ntfy | Automatisch : nouvel épisode ; back-office : chaque étape traitée, dont l'intégration d'une émission ancienne (préfixe `pap`, « Nouvel épisode Le Masque et la Plume intégré — jj/mm/aa », message = titre de l'épisode) ; `lmelp-export` : nouvelles données publiées, premier échec, puis rétablissement |
| Sauvegarde MongoDB | **hebdomadaire** (anacron dans `lmelp-mongo`, horaires en UTC) |

!!! note "La release `data-v{N}` est republiée sur place"
    `N` est la version du schéma de la base, pas un numéro de publication : la date réelle de la dernière publication est `export_datetime` dans `metadata.json`, pas la date de la release. Détails dans [Mise à jour d'un épisode](user/mise_a_jour_episode.md#mettre-a-jour-la-base-depuis-lapplication).
