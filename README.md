# lmelp-mobile

Application Android pour consulter le contenu de **Le Masque et la Plume** (LMELP) — émission littéraire de France Inter — hors connexion.

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

Voir [docs/architecture.md](docs/architecture.md) pour les détails.

## Stack technique

| Composant | Technologie |
|-----------|-------------|
| Langage | Kotlin |
| UI | Jetpack Compose |
| ORM | Room (SQLite) |
| Navigation | Navigation Compose |
| Build | Gradle (Kotlin DSL) |
| CI/CD | GitHub Actions |
| Distribution | GitHub Releases |
| Export données | Python 3.11+ (pymongo → SQLite) |

## Fonctionnalités cibles

### V1 — Consultation offline

| Écran | Description |
|-------|-------------|
| **Émissions** | Liste des émissions avec date, durée, statut |
| **Détail émission** | Livres discutés, critiques présents, avis par livre |
| **Palmarès** | Livres classés par note moyenne décroissante |
| **Critiques** | Liste des 25 critiques avec leurs avis |
| **Détail critique** | Tous les avis d'un critique avec notes |
| **Recherche** | Full-text search sur titres, auteurs, critiques |
| **Recommandations** | Livres recommandés par collaborative filtering SVD |

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

# Exporter MongoDB → SQLite
python scripts/export_mongo_to_sqlite.py \
  --mongo-uri mongodb://localhost:27017 \
  --output app/src/main/assets/lmelp.db

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

## Publication (GitHub Releases)

```bash
# Créer un tag → déclenche GitHub Actions → build APK + release
git tag v1.0.0
git push origin v1.0.0
```

GitHub Actions va :
1. Exporter MongoDB → SQLite (si secrets configurés)
2. Builder l'APK signé
3. Publier dans GitHub Releases

Voir [docs/ci-cd.md](docs/ci-cd.md) pour la configuration.

## Structure du projet

```
├── app/                          # Application Android (Kotlin)
│   ├── src/main/
│   │   ├── assets/lmelp.db       # Base SQLite embarquée (générée)
│   │   ├── java/com/lmelp/mobile/
│   │   │   ├── data/             # Room entities, DAOs, Database
│   │   │   ├── ui/               # Composables Jetpack Compose
│   │   │   │   ├── emissions/
│   │   │   │   ├── palmares/
│   │   │   │   ├── critiques/
│   │   │   │   ├── search/
│   │   │   │   └── recommendations/
│   │   │   ├── viewmodel/        # ViewModels
│   │   │   └── MainActivity.kt
│   │   └── res/
│   └── build.gradle.kts
├── scripts/
│   └── export_mongo_to_sqlite.py # Export MongoDB → SQLite
├── docs/
│   ├── architecture.md           # Architecture détaillée
│   ├── data-schema.md            # Schéma SQLite
│   └── ci-cd.md                  # Pipeline CI/CD
├── .github/workflows/
│   └── release.yml               # Build + publish APK
├── pyproject.toml                # Dépendances Python (script export)
├── CLAUDE.md                     # Guide pour Claude Code
└── README.md
```

## Source des données

Les données proviennent du projet [back-office-lmelp](https://github.com/castorfou/back-office-lmelp) :
- **227 émissions** de Le Masque et la Plume
- **1615 livres** discutés
- **1114 auteurs**
- **25 critiques**
- **4100+ avis** avec notes (1-10)

## Licence

Usage personnel — données issues de France Inter / Le Masque et la Plume.
