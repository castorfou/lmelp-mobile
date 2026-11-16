# Écosystème lmelp

Vue d'ensemble des briques qui alimentent l'application lmelp-mobile : qui alimente qui, et **ce qui déclenche chaque flux**. Les détails propres à chaque brique sont dans la documentation de [back-office-lmelp](https://castorfou.github.io/back-office-lmelp/) et de [docker-lmelp](https://castorfou.github.io/docker-lmelp/) ; le parcours d'un nouvel épisode, pas à pas, est dans [Mise à jour d'un épisode](user/mise_a_jour_episode.md).

## Schéma synoptique

```mermaid
flowchart TB
    subgraph EXT["Sources externes"]
        RSS["France Inter<br/>flux RSS Le Masque et la Plume"]
        RF["radiofrance.fr<br/>page et audio de l'émission"]
        BAB["Babelio<br/>recherche, pages livres et auteurs"]
        AZ["Azure OpenAI<br/>LLM"]
    end

    subgraph LAPTOP["Laptop"]
        FF["Firefox<br/>connecté à Babelio"]
        CAL["Calibre<br/>bibliothèque source"]
    end

    PGX["PGX<br/>transcription audio"]
    AUTO["Automatisch"]
    NTFY["ntfy.sh<br/>notifications"]

    subgraph NAS["NAS Synology (Docker)"]
        BACK["lmelp backend + frontend<br/>lmelp.ascot63.synology.me"]
        MONGO[("MongoDB<br/>lmelp-mongo")]
        DRIVE["Copie Drive<br/>Calibre Library"]
        CWA["Calibre-Web-Automated"]
        EXPORT["lmelp-export"]
        WT["Watchtower"]
    end

    subgraph GH["GitHub"]
        CI["CI GitHub Actions<br/>images ghcr.io"]
        REL["Release data-v{N}<br/>lmelp.db + metadata.json"]
    end

    subgraph PHONE["Téléphone"]
        MOB["app lmelp-mobile"]
        PWA["PWA lmelp"]
    end

    RSS -->|"nouvel épisode@@nouvel épisode publié dans le flux RSS<br/>flux interrogé toutes les 15 minutes"| AUTO
    AUTO -->|"notification@@notification d'un nouvel épisode"| NTFY
    AUTO -->|"sync + transcription@@POST /api/rss/sync<br/>puis /api/pgx/transcription/start"| BACK
    RF -->|"audio@@téléchargement audio : rss/sync<br/>ou manuel, page Ajouter une émission"| BACK
    BACK <-->|"transcription@@ssh/scp : envoi audio, retour transcription<br/>réessai toutes les heures si PGX injoignable"| PGX
    BACK -->|"avis critiques@@manuel : Génération Avis Critiques"| AZ
    FF -->|"cookie@@manuel : copie du cookie<br/>valable ~5 min"| BACK
    BACK <-->|"validation@@manuel : Valider avec Babelio, Liaison Babelio,<br/>couvertures ; 1 requête / BABELIO_FAIR_SEC,<br/>empreinte Firefox"| BAB
    BACK <-->|"lecture/écriture@@chaque action"| MONGO
    BACK -->|"notifications@@chaque étape traitée"| NTFY
    CAL -->|"Synology Drive@@Synology Drive<br/>continu, quelques secondes"| DRIVE
    DRIVE -->|"metadata.db@@lecture seule ;<br/>relue dès que metadata.db change"| BACK
    DRIVE -->|"00:05@@synchronisation nocturne<br/>00:05"| CWA
    MONGO -->|"horaire@@toutes les heures"| EXPORT
    DRIVE -->|"horaire@@toutes les heures<br/>livres lus"| EXPORT
    EXPORT -->|"si changement@@si les données ont changé :<br/>republie data-v{N}"| REL
    EXPORT -->|"publication / échec@@nouvelles données publiées ;<br/>premier échec, puis rétablissement"| NTFY
    REL -->|"lmelp.db@@détection au lancement (point vert) ;<br/>téléchargement manuel :<br/>À propos, Vérifier les mises à jour"| MOB
    NTFY -->|"notifications"| PHONE
    PWA -->|"navigation"| BACK
    CI -->|"push main@@push sur main"| WT
    WT -->|"nouvelles images@@recrée les conteneurs<br/>avec les nouvelles images"| BACK
    WT -->|"nouvelle image@@redémarre sur la nouvelle image"| EXPORT
```

## Déclencheurs

Le schéma ne garde que des étiquettes courtes. En plein écran, survoler ou toucher une étiquette affiche son détail ; le tableau reprend chaque flux.

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
| Notifications ntfy | Automatisch : nouvel épisode ; back-office : chaque étape traitée ; `lmelp-export` : nouvelles données publiées, premier échec, puis rétablissement |
| Sauvegarde MongoDB | **hebdomadaire** (anacron dans `lmelp-mongo`) |

!!! note "La release `data-v{N}` est republiée sur place"
    `N` est la version du schéma de la base, pas un numéro de publication : la date réelle de la dernière publication est `export_datetime` dans `metadata.json`, pas la date de la release. Détails dans [Mise à jour d'un épisode](user/mise_a_jour_episode.md#mettre-a-jour-la-base-depuis-lapplication).
