# Écosystème lmelp

Vue d'ensemble des briques qui alimentent l'application lmelp-mobile : qui alimente qui, et **ce qui déclenche chaque flux**. Les détails propres à chaque brique sont dans la documentation de [back-office-lmelp](https://castorfou.github.io/back-office-lmelp/) et de [docker-lmelp](https://castorfou.github.io/docker-lmelp/) ; le parcours d'un nouvel épisode, pas à pas, est dans [Mise à jour d'un épisode](user/mise_a_jour_episode.md).

## Schéma synoptique

Le schéma se lit de haut en bas, dans l'ordre où circulent les données : Radio France, Automatisch, le back-office lmelp et ses services d'enrichissement, l'export, la release GitHub, puis le téléphone. Survoler ou toucher une étiquette soulignée affiche son détail ; le bouton **Plein écran** l'agrandit.

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
    end

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
    FF -->|"cookie@@manuel : copie du cookie<br/>valable ~5 min"| BACK
    CAL -->|"Synology Drive@@Synology Drive<br/>continu, quelques secondes"| DRIVE
    DRIVE -->|"metadata.db@@lecture seule ;<br/>relue dès que metadata.db change"| BACK
    BACK <-->|"transcription@@ssh/scp : envoi audio, retour transcription<br/>réessai toutes les heures si PGX injoignable"| PGX
    BACK <-->|"validation@@manuel : Valider avec Babelio, Liaison Babelio,<br/>couvertures ; 1 requête / BABELIO_FAIR_SEC,<br/>empreinte Firefox"| BAB
    BACK -->|"avis critiques@@manuel : Génération Avis Critiques"| AZ
    BACK <-->|"lecture/écriture@@chaque action"| MONGO
    DRIVE -->|"00:05@@synchronisation nocturne<br/>00:05"| CWA
    MONGO -->|"horaire@@toutes les heures"| EXPORT
    DRIVE -->|"horaire@@toutes les heures<br/>livres lus"| EXPORT
    EXPORT -->|"si changement@@si les données ont changé :<br/>republie data-v{N}"| REL
    REL -->|"lmelp.db@@détection au lancement (point vert) ;<br/>téléchargement manuel :<br/>À propos, Vérifier les mises à jour"| MOB
    BACK -->|"interface web@@navigation depuis le téléphone"| PWA
    AUTO -->|"notification@@notification d'un nouvel épisode"| NTFY
    BACK -->|"notifications@@chaque étape traitée"| NTFY
    EXPORT -->|"publication / échec@@nouvelles données publiées ;<br/>premier échec, puis rétablissement"| NTFY
    NTFY -->|"notifications"| PHONE
```

## Mise à jour du code

Indépendante du flux de données. Chaque push sur `main` reconstruit les images Docker, que Watchtower installe sur le NAS, et publie une préversion de l'appli si le push la modifie ; un tag `vX.Y.Z` publie une version de l'appli. L'appli détecte elle-même les deux (voir [Installer et mettre à jour l'appli](user/installer_mettre_a_jour.md)).

```mermaid
flowchart TB
    PUSH["push sur main<br/>back-office-lmelp, lmelp-mobile"]
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
    end

    subgraph PHONE["Téléphone"]
        MOB["app lmelp-mobile"]
    end

    PUSH --> CI
    PUSH -->|"préversion@@app-preview.yml, si le push touche l'appli :<br/>APK release signé X.Y.Z+N<br/>(N commits depuis le dernier tag stable) ;<br/>la release app-preview est supprimée puis recréée"| PREV
    TAG -->|"version@@release.yml : APK release signé,<br/>signature vérifiée avec apksigner"| APK
    CI -->|"nouvelles images@@images publiées sur ghcr.io,<br/>puis appel de l'API Watchtower du NAS"| WT
    WT -->|"recrée@@recrée les conteneurs<br/>avec les nouvelles images"| BACK
    WT -->|"redémarre@@redémarre sur la nouvelle image"| EXPORT
    PREV -->|"nouvelle version@@détection au lancement de l'appli (badge) ;<br/>À propos : Télécharger et installer"| MOB
    APK -->|"nouvelle version@@détection au lancement de l'appli (badge) ;<br/>À propos : Télécharger et installer"| MOB
```

## Déclencheurs

Le tableau reprend chaque flux en détail.

| Flux | Déclencheur |
|---|---|
| RSS → Automatisch → back-office (`POST /api/rss/sync` puis `/api/pgx/transcription/start`) | publication d'un nouvel épisode dans le flux RSS, qu'Automatisch interroge **toutes les 15 minutes** (valeur par défaut d'un flux Automatisch, aucun intervalle configuré) |
| Ajout d'une émission absente du RSS | manuel, page **Ajouter une émission** |
| Transcription PGX (ssh/scp : envoi de l'audio, retour de la transcription) | lancée par Automatisch ou à la main ; réessai **toutes les heures** si PGX est injoignable |
| Avis critiques (LLM) | manuel, page **Génération Avis Critiques** |
| Babelio (Valider avec Babelio, Liaison Babelio, couvertures) | manuel uniquement (aucune requête à l'ouverture des pages), 1 requête par `BABELIO_FAIR_SEC`, empreinte Firefox ; cookie copié depuis Firefox **connecté** à Babelio, valable ~5 min |
| Calibre laptop → copie Drive | **continu** (Synology Drive, quelques secondes) |
| Copie Drive → back-office | lecture seule, relue dès que `metadata.db` change |
| Copie Drive → Calibre-Web-Automated | **chaque nuit à 00:05** (CWA seulement) |
| MongoDB + Calibre (livres lus) → `lmelp-export` → release `data-v{N}` | **toutes les heures** (`PUBLISH_INTERVAL`), publication seulement si le contenu a changé (la release est alors republiée sur place) ; immédiat avec `docker exec lmelp-export export-and-publish-release` |
| Release → app mobile | détection au lancement de l'app (point vert) ; téléchargement **manuel** (À propos → Vérifier les mises à jour) |
| Nouvelle version de code | push sur `main` → CI → images ghcr.io → Watchtower recrée les conteneurs (back-office, `lmelp-export`) |
| Préversion de l'appli → release `app-preview` | push sur `main` qui touche l'appli (`app-preview.yml`), APK `X.Y.Z+N` ; rien si aucun commit depuis le dernier tag |
| Version de l'appli → release `vX.Y.Z` | tag `vX.Y.Z` (`release.yml`) |
| Release de l'appli → app mobile | détection au lancement de l'app (badge) ; installation **manuelle** (À propos → Télécharger et installer) |
| Notifications ntfy | Automatisch : nouvel épisode ; back-office : chaque étape traitée ; `lmelp-export` : nouvelles données publiées, premier échec, puis rétablissement |
| Sauvegarde MongoDB | **hebdomadaire** (anacron dans `lmelp-mongo`) |

!!! note "La release `data-v{N}` est republiée sur place"
    `N` est la version du schéma de la base, pas un numéro de publication : la date réelle de la dernière publication est `export_datetime` dans `metadata.json`, pas la date de la release. Détails dans [Mise à jour d'un épisode](user/mise_a_jour_episode.md#mettre-a-jour-la-base-depuis-lapplication).
