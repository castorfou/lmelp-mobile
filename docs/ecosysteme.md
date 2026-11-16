# Écosystème lmelp

Vue d'ensemble des briques qui alimentent l'application lmelp-mobile : qui alimente qui, et **ce qui déclenche chaque flux**. Les détails propres à chaque brique sont dans la documentation de [back-office-lmelp](https://castorfou.github.io/back-office-lmelp/) et de docker-lmelp ; le parcours d'un nouvel épisode, pas à pas, est dans [Mise à jour d'un épisode](user/mise_a_jour_episode.md).

## Schéma synoptique

```mermaid
flowchart LR
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
        CI["CI docker-publish<br/>images ghcr.io"]
        REL["Release data-v{N}<br/>lmelp.db + metadata.json"]
    end

    subgraph PHONE["Téléphone"]
        MOB["app lmelp-mobile"]
        PWA["PWA lmelp"]
    end

    RSS -->|"nouvel épisode publié"| AUTO
    AUTO -->|"notification"| NTFY
    AUTO -->|"POST /api/rss/sync<br/>puis /api/pgx/transcription/start"| BACK
    RF -->|"téléchargement audio : rss/sync<br/>ou manuel, page Ajouter une émission"| BACK
    BACK <-->|"ssh/scp : envoi audio, retour transcription<br/>réessai toutes les heures si PGX injoignable"| PGX
    BACK -->|"manuel : Génération Avis Critiques"| AZ
    FF -->|"manuel : copie du cookie<br/>valable ~5 min"| BACK
    BACK <-->|"manuel : Valider avec Babelio, Liaison Babelio,<br/>couvertures ; 1 requête / BABELIO_FAIR_SEC,<br/>empreinte Firefox"| BAB
    BACK <-->|"chaque action"| MONGO
    BACK -->|"chaque étape traitée"| NTFY
    CAL -->|"Synology Drive<br/>continu, quelques secondes"| DRIVE
    DRIVE -->|"lecture seule ;<br/>relue dès que metadata.db change"| BACK
    DRIVE -->|"synchronisation nocturne<br/>00:05"| CWA
    MONGO -->|"toutes les heures"| EXPORT
    DRIVE -->|"toutes les heures<br/>livres lus"| EXPORT
    EXPORT -->|"si les données ont changé :<br/>republie data-v{N}"| REL
    EXPORT -->|"nouvelles données publiées<br/>ou échec"| NTFY
    REL -->|"détection au lancement (point vert) ;<br/>téléchargement manuel :<br/>À propos, Vérifier les mises à jour"| MOB
    NTFY -->|"notifications"| PHONE
    PWA -->|"navigation"| BACK
    CI -->|"push sur main"| WT
    WT -->|"recrée les conteneurs<br/>avec les nouvelles images"| BACK
```

## Déclencheurs

| Flux | Déclencheur |
|---|---|
| RSS → Automatisch → back-office (téléchargement audio, transcription) | publication d'un nouvel épisode dans le flux RSS |
| Ajout d'une émission absente du RSS | manuel, page **Ajouter une émission** |
| Transcription PGX | lancée par Automatisch ou à la main ; réessai **toutes les heures** si PGX est injoignable |
| Avis critiques (LLM) | manuel, page **Génération Avis Critiques** |
| Babelio | manuel uniquement (aucune requête à l'ouverture des pages) ; cookie copié depuis Firefox **connecté** à Babelio, valable ~5 min |
| Calibre laptop → copie Drive | **continu** (Synology Drive, quelques secondes) |
| Copie Drive → back-office | lecture directe, relue dès que `metadata.db` change |
| Copie Drive → Calibre-Web-Automated | **chaque nuit à 00:05** (CWA seulement) |
| MongoDB + Calibre → `lmelp-export` → release `data-v{N}` | **toutes les heures** (`PUBLISH_INTERVAL`), publication seulement si le contenu a changé ; immédiat avec `docker exec lmelp-export export-and-publish-release` |
| Release → app mobile | détection au lancement de l'app ; téléchargement **manuel** (À propos → Vérifier les mises à jour) |
| Nouvelle version de code | push sur `main` → CI → images ghcr.io → Watchtower recrée les conteneurs |
| Sauvegarde MongoDB | **hebdomadaire** (anacron dans `lmelp-mongo`) |

!!! note "La release `data-v{N}` est republiée sur place"
    `N` est la version du schéma de la base (`PRAGMA user_version`, version Room), pas un numéro de publication. Chaque export qui publie remplace les fichiers de la même release `data-v{N}` ; le numéro ne change que lorsque le schéma change (voir [ADR 0001](dev/adr/0001-separation-maj-appli-donnees.md)). La date affichée par GitHub pour la release reste donc celle de sa création : la date réelle de la dernière publication est `export_datetime` dans `metadata.json`.
