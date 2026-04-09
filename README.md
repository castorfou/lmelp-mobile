# lmelp-mobile

![](img/screenshot_lmelp-mobile_icon.png)

Application Android pour consulter le contenu de Le Masque et la Plume (LMELP) — émission littéraire de France Inter — hors connexion.

Et obtenir des recommandations de lectures personnalisées.

![Aperçu de la page d'accueil](img/screenshot_lmelp-mobile_small.png)

## Projets associés

Les projets associés sont :

- **[lmelp](https://github.com/castorfou/lmelp)** - pour telecharger les episodes radiophoniques (podcasts) du masque
- **[back-office-lmelp](https://github.com/castorfou/back-office-lmelp)** - pour travailler le contenu de la base de données.
- **[docker-lmelp](https://github.com/castorfou/docker-lmelp)** - pour héberger l'ensemble des composants dans une stack docker.
- **[lmelp-pap](https://github.com/castorfou/lmelp-pap)** - le Projet d'Archivage Patrimoniale (1955-2016) des épisodes du Masque et la Plume.

Tous ces projets sont basés sur le template de projet [PyFoundry](https://castorfou.github.io/PyFoundry/).


## Vision

Application mobile **standalone et offline-first** permettant de consulter :
- Les émissions avec leurs critiques et avis
- Le palmarès des livres
- La liste des critiques et leurs avis
- La recherche dans toute la base
- Les recommandations personnalisées (collaborative filtering SVD)

Les données sont **embarquées dans l'APK** (base SQLite) et mises à jour via un pipeline de publication en un clic.

## Architecture

```
back-office-lmelp (MongoDB)
        │
        ▼
scripts/export_mongo_to_sqlite.py
        │
        ▼
lmelp.db  (SQLite snapshot)
        │
        ▼  (copié dans app/src/main/assets/)
Application Android (Kotlin + Jetpack Compose + Room)
        │
        ▼
GitHub Actions → APK → GitHub Releases
```

## Stack technique

| Composant      | Technologie                     |
| -------------- | ------------------------------- |
| Langage        | Kotlin                          |
| UI             | Jetpack Compose                 |
| ORM            | Room (SQLite)                   |
| Navigation     | Navigation Compose              |
| Build          | Gradle (Kotlin DSL)             |
| CI/CD          | GitHub Actions                  |
| Distribution   | GitHub Releases                 |
| Export données | Python 3.11+ (pymongo → SQLite) |

## Fonctionnalités cibles

### V1 — Consultation offline

| Écran               | Description                                                                      |
| ------------------- | -------------------------------------------------------------------------------- |
| **Émissions**       | Liste des émissions avec date, durée, statut                                     |
| **Détail émission** | Livres discutés, critiques présents, avis par livre                              |
| **Palmarès**        | Livres classés par note moyenne décroissante                                     |
| **Critiques**       | Liste des 25 critiques avec leurs avis                                           |
| **Détail critique** | Tous les avis d'un critique avec notes                                           |
| **Recherche**       | Full-text search sur titres, auteurs, critiques                                  |
| **Recommandations** | Livres recommandés par collaborative filtering SVD                               |
| **Sur ma liseuse**  | Livres Calibre tagués `onkindle`, filtrés par virtual library, avec notes Masque |

### V2 — Mise à jour des données (à définir)

- Sync Wi-Fi avec le back-office si sur le même réseau
- Ou rebuild APK + GitHub Releases automatisé

## Setup développement

### Prérequis

- Android Studio Hedgehog ou supérieur
- JDK 17+
- Python 3.11+ avec `uv` (pour le script d'export)
- Accès à une instance MongoDB `masque_et_la_plume`

### Générer la base SQLite

```bash
# Installer les dépendances Python
uv pip install -e .

# Configurer l'environnement local (une seule fois)
cp scripts/.env.example scripts/.env
# Éditer scripts/.env avec mongo URI, chemin Calibre, virtual library

# Exporter MongoDB → SQLite
python scripts/export_mongo_to_sqlite.py --force

# Vérifier le résultat
python scripts/export_mongo_to_sqlite.py --verify app/src/main/assets/lmelp.db
```
### Lancer l'appli

```bash
# Ouvrir le projet dans Android Studio
# ou build en ligne de commande :
./gradlew assembleDebug
./gradlew installDebug
```



## Source des données

Les données proviennent du projet [back-office-lmelp](https://github.com/castorfou/back-office-lmelp) :
- **227 émissions** de Le Masque et la Plume
- **1615 livres** discutés
- **1114 auteurs**
- **25 critiques**
- **4100+ avis** avec notes (1-10)
