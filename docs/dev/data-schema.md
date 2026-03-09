# Schéma SQLite — lmelp.db

Base de données SQLite embarquée dans l'APK, générée par `scripts/export_mongo_to_sqlite.py` depuis MongoDB `masque_et_la_plume`.

## Conventions

- **IDs** : Strings (conversion depuis ObjectId MongoDB)
- **Dates** : Strings ISO 8601 (`YYYY-MM-DDTHH:MM:SSZ`)
- **Notes** : REAL (1.0 à 10.0)
- **Booléens** : INTEGER (0 ou 1)
- **Durées** : INTEGER (secondes)

## Tables

### `emissions`

Émissions de Le Masque et la Plume (émissions enrichies avec critiques et avis).

```sql
CREATE TABLE emissions (
    id              TEXT PRIMARY KEY,   -- ObjectId MongoDB comme string
    episode_id      TEXT NOT NULL,      -- Référence vers episodes.id
    date            TEXT NOT NULL,      -- ISO 8601
    duree           INTEGER,            -- Durée en secondes
    animateur_id    TEXT,               -- Référence vers critiques.id (nullable)
    nb_avis         INTEGER DEFAULT 0,  -- Nombre d'avis (précalculé)
    has_summary     INTEGER DEFAULT 0,  -- A un résumé LLM (0/1)
    created_at      TEXT,
    updated_at      TEXT
);
```

### `episodes`

Épisodes radio bruts (source RadioFrance).

```sql
CREATE TABLE episodes (
    id              TEXT PRIMARY KEY,
    titre           TEXT NOT NULL,
    date            TEXT,               -- ISO 8601
    description     TEXT,
    url             TEXT,               -- URL RadioFrance
    duree           INTEGER,            -- Durée en secondes
    masked          INTEGER DEFAULT 0   -- Épisode masqué (0/1)
);
```

### `livres`

Livres discutés dans les émissions.

```sql
CREATE TABLE livres (
    id              TEXT PRIMARY KEY,
    titre           TEXT NOT NULL,
    auteur_id       TEXT,               -- Référence vers auteurs.id
    auteur_nom      TEXT,               -- Dénormalisé pour éviter jointures
    editeur         TEXT,
    url_babelio     TEXT,
    created_at      TEXT,
    updated_at      TEXT
);
```

### `auteurs`

Auteurs des livres.

```sql
CREATE TABLE auteurs (
    id              TEXT PRIMARY KEY,
    nom             TEXT NOT NULL,
    url_babelio     TEXT
);
```

### `critiques`

Les critiques et chroniqueurs de l'émission (25 personnes).

```sql
CREATE TABLE critiques (
    id              TEXT PRIMARY KEY,
    nom             TEXT NOT NULL,
    animateur       INTEGER DEFAULT 0,  -- Est animateur (0/1)
    nb_avis         INTEGER DEFAULT 0   -- Nombre total d'avis (précalculé)
);
```

### `avis`

Avis individuels des critiques sur les livres lors des émissions.

```sql
CREATE TABLE avis (
    id              TEXT PRIMARY KEY,
    emission_id     TEXT NOT NULL,      -- Référence vers emissions.id
    livre_id        TEXT NOT NULL,      -- Référence vers livres.id
    critique_id     TEXT NOT NULL,      -- Référence vers critiques.id
    note            REAL,               -- Note 1-10 (nullable)
    commentaire     TEXT,
    -- Données dénormalisées (pour éviter jointures coûteuses)
    livre_titre     TEXT,
    auteur_nom      TEXT,
    critique_nom    TEXT,
    -- Qualité de l'extraction
    match_phase     INTEGER,            -- 1=exact, 2=partial, 3=similarité, null=non-matché
    created_at      TEXT
);
```

### `emission_livres`

Table de jointure émission ↔ livres discutés.

```sql
CREATE TABLE emission_livres (
    emission_id     TEXT NOT NULL,
    livre_id        TEXT NOT NULL,
    PRIMARY KEY (emission_id, livre_id),
    FOREIGN KEY (emission_id) REFERENCES emissions(id),
    FOREIGN KEY (livre_id) REFERENCES livres(id)
);
```

### `palmares`

**Table précalculée** à l'export. Livres classés par note moyenne.

```sql
CREATE TABLE palmares (
    rank            INTEGER NOT NULL,   -- Rang (1 = meilleure note)
    livre_id        TEXT PRIMARY KEY,
    titre           TEXT NOT NULL,
    auteur_nom      TEXT,
    note_moyenne    REAL NOT NULL,      -- Moyenne des notes
    nb_avis         INTEGER NOT NULL,   -- Nombre d'avis avec note
    nb_critiques    INTEGER NOT NULL,   -- Nombre de critiques distincts
    FOREIGN KEY (livre_id) REFERENCES livres(id)
);
```

### `recommendations`

**Table précalculée** à l'export. Recommandations SVD collaborative filtering.

> **Filtrage dans l'app** : l'écran Conseils effectue un LEFT JOIN avec `palmares` pour exclure les livres déjà lus (`calibre_in_library = 1 AND calibre_lu = 1`). Voir `RecommendationsDao.getRecommandationsNonLues()`.

```sql
CREATE TABLE recommendations (
    rank            INTEGER NOT NULL,   -- Rang de recommandation
    livre_id        TEXT NOT NULL,
    titre           TEXT NOT NULL,
    auteur_nom      TEXT,
    score_hybride   REAL NOT NULL,      -- Score final (70% SVD + 30% masque_mean)
    svd_predict     REAL,               -- Prédiction SVD pure
    masque_mean     REAL,               -- Moyenne avis Masque et la Plume
    masque_count    INTEGER,            -- Nb avis Masque
    PRIMARY KEY (livre_id),
    FOREIGN KEY (livre_id) REFERENCES livres(id)
);
```

### `avis_critiques`

Résumés LLM des émissions (générés par back-office-lmelp).

```sql
CREATE TABLE avis_critiques (
    id              TEXT PRIMARY KEY,
    emission_id     TEXT NOT NULL,      -- Référence vers emissions.id (via episode)
    episode_title   TEXT,
    episode_date    TEXT,
    summary         TEXT,               -- HTML table avec les avis détaillés
    animateur       TEXT,
    critiques_json  TEXT,               -- JSON array des critiques présents
    FOREIGN KEY (emission_id) REFERENCES emissions(id)
);
```

### `search_index` (FTS5)

**Index full-text** pour la recherche. Table virtuelle SQLite FTS5.

```sql
CREATE VIRTUAL TABLE search_index USING fts5(
    type,       -- 'emission' | 'livre' | 'auteur' | 'critique'
    ref_id,     -- ID de l'entité référencée (TEXT)
    content,    -- Contenu indexé (titre + auteur + description...)
    tokenize = 'unicode61 remove_diacritics 2'
);
```

**Contenu indexé par type :**
- `emission` : titre de l'épisode + description
- `livre` : titre + auteur_nom + editeur
- `auteur` : nom
- `critique` : nom + variantes

### `db_metadata`

Métadonnées de la base (version, date d'export).

```sql
CREATE TABLE db_metadata (
    key     TEXT PRIMARY KEY,
    value   TEXT NOT NULL
);

-- Valeurs insérées à l'export :
-- ('version', '42')               -- PRAGMA user_version (timestamp Unix)
-- ('export_date', '2026-03-05')   -- Date lisible
-- ('source_db', 'masque_et_la_plume')
-- ('nb_emissions', '173')
-- ('nb_livres', '1615')
-- ('nb_avis', '4100')
```

## Index

```sql
-- Performances de navigation
CREATE INDEX idx_avis_emission ON avis(emission_id);
CREATE INDEX idx_avis_livre ON avis(livre_id);
CREATE INDEX idx_avis_critique ON avis(critique_id);
CREATE INDEX idx_emission_livres_emission ON emission_livres(emission_id);
CREATE INDEX idx_emission_livres_livre ON emission_livres(livre_id);
CREATE INDEX idx_livres_auteur ON livres(auteur_id);
CREATE INDEX idx_emissions_date ON emissions(date DESC);
CREATE INDEX idx_palmares_rank ON palmares(rank);
CREATE INDEX idx_recommendations_rank ON recommendations(rank);
```

## Correspondance MongoDB → SQLite

| Collection MongoDB | Table SQLite | Transformations |
|-------------------|--------------|-----------------|
| `emissions` | `emissions` | Calcul `nb_avis`, `has_summary` |
| `episodes` | `episodes` | Direct |
| `livres` | `livres` | Ajout `auteur_nom` (jointure auteurs) |
| `auteurs` | `auteurs` | Direct |
| `critiques` | `critiques` | Ajout `nb_avis` (précalculé) |
| `avis` | `avis` | Conversion String IDs, dénormalisation noms |
| `avis_critiques` | `avis_critiques` | Conversion `metadata_source` → colonnes |
| Calculé | `palmares` | AVG(note) GROUP BY livre |
| Calculé | `recommendations` | SVD sur matrice avis |
| Calculé | `search_index` | FTS5 sur tous les textes |
| Calculé | `emission_livres` | Jointure avis → (emission, livre) pairs |

## Taille estimée

| Table | Lignes | Taille estimée |
|-------|--------|---------------|
| emissions | 173 | ~100 KB |
| episodes | 227 | ~500 KB |
| livres | 1615 | ~300 KB |
| auteurs | 1114 | ~100 KB |
| critiques | 25 | ~5 KB |
| avis | 4100+ | ~2 MB |
| avis_critiques | 175 | ~5 MB |
| palmares | ~800 | ~100 KB |
| recommendations | ~500 | ~50 KB |
| search_index | ~3500 | ~500 KB |
| **Total** | | **~9-12 MB** |
