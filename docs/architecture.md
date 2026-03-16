# Architecture — lmelp-mobile

## Vue d'ensemble

```
┌─────────────────────────────────────────────────────────────────┐
│  ORDINATEUR DE DÉVELOPPEMENT                                    │
│                                                                 │
│  MongoDB (masque_et_la_plume)                                   │
│    ├── emissions (173)                                          │
│    ├── episodes (227)                                           │
│    ├── livres (1615)                                            │
│    ├── auteurs (1114)                                           │
│    ├── critiques (25)                                           │
│    └── avis (4100+)                                             │
│         │                                                       │
│         ▼                                                       │
│  scripts/export_mongo_to_sqlite.py                              │
│    ├── Exporte les collections                                  │
│    ├── Précalcule palmarès + recommandations SVD                │
│    ├── Construit index FTS5 (recherche full-text)               │
│    ├── Croise avec Calibre (onkindle, lu/non lu, rating)        │
│    └── Écrit lmelp.db (SQLite)                                  │
│         │                                                       │
│         ▼                                                       │
│  app/src/main/assets/lmelp.db   (~5-10 MB)                     │
│                                                                 │
│  ./gradlew assembleRelease  (ou GitHub Actions)                 │
└─────────────────────────────────────────────────────────────────┘
                         │
                         ▼  (APK publié sur GitHub Releases)
┌─────────────────────────────────────────────────────────────────┐
│  TÉLÉPHONE ANDROID                                              │
│                                                                 │
│  Installation APK                                               │
│         │                                                       │
│         ▼  (1ère ouverture)                                     │
│  Room copie assets/lmelp.db → /data/data/.../databases/         │
│         │                                                       │
│         ▼                                                       │
│  Application Android (100% offline sauf couvertures)           │
│    ├── Émissions                                                │
│    ├── Palmarès                                                 │
│    ├── Critiques                                                │
│    ├── Recherche (FTS5)                                         │
│    ├── Recommandations                                          │
│    └── Sur ma liseuse (OnKindle)                                │
│                                                                 │
│  Couvertures (Coil)                                             │
│    ├── url_cover lue directement depuis lmelp.db                │
│    ├── Images téléchargées 1 seule fois (Babelio/Amazon CDN)    │
│    └── Disk cache : getExternalFilesDir/coil_image_cache        │
└─────────────────────────────────────────────────────────────────┘
```

## Architecture Android — MVVM

```
┌─────────────────────────────────────────────────────────────────┐
│  UI Layer (Jetpack Compose)                                     │
│                                                                 │
│  EmissionsScreen ──→ EmissionsContent ──→ EmissionCard         │
│  PalmaresScreen  ──→ PalmaresContent  ──→ LivreRankCard        │
│  CritiquesScreen ──→ CritiquesContent ──→ CritiqueCard         │
│  SearchScreen    ──→ SearchContent    ──→ SearchResultItem     │
│  RecommendScreen ──→ RecommendContent ──→ RecommendCard        │
│  OnKindleScreen  ──→ OnKindleContent  ──→ OnKindleCard         │
└───────────────────────────┬─────────────────────────────────────┘
                            │ collectAsStateWithLifecycle()
                            │ StateFlow<UiState>
┌───────────────────────────▼─────────────────────────────────────┐
│  ViewModel Layer                                                │
│                                                                 │
│  EmissionsViewModel   PalmaresViewModel   SearchViewModel       │
│  CritiquesViewModel   RecommendViewModel  OnKindleViewModel     │
│                                                                 │
│  Responsabilités :                                              │
│    - Exposer StateFlow<XxxUiState>                              │
│    - Gérer les événements UI (filtres, recherche, navigation)   │
│    - Appeler les repositories via viewModelScope                │
└───────────────────────────┬─────────────────────────────────────┘
                            │ suspend functions
┌───────────────────────────▼─────────────────────────────────────┐
│  Data Layer                                                     │
│                                                                 │
│  EmissionsRepository  LivresRepository  CritiquesRepository     │
│  SearchRepository     RecommendRepository  OnKindleRepository   │
│                                                                 │
│  Responsabilités :                                              │
│    - Source unique de vérité                                    │
│    - Mapping Entity → UI model                                  │
│    - Coordination des DAOs                                      │
└───────────────────────────┬─────────────────────────────────────┘
                            │ Room DAOs
┌───────────────────────────▼─────────────────────────────────────┐
│  Database Layer (Room)                                          │
│                                                                 │
│  LmelpDatabase (RoomDatabase)                                   │
│    ├── EmissionsDao                                             │
│    ├── LivresDao                                                │
│    ├── CritiquesDao                                             │
│    ├── AvisDao                                                  │
│    ├── OnKindleDao                                              │
│    └── SearchDao (FTS5)                                         │
│                                                                 │
│  SQLite : app/src/main/assets/lmelp.db                          │
│    └── Copié dans /data/data/.../databases/ au 1er lancement   │
└─────────────────────────────────────────────────────────────────┘
```

## Navigation

```
MainActivity
    └── NavHost
         ├── HomeScreen (liste des sections)
         │    ├── → EmissionsScreen
         │    │    └── → EmissionDetailScreen(emissionId)
         │    │         └── → LivreDetailScreen(livreId)
         │    ├── → PalmaresScreen
         │    │    └── → LivreDetailScreen(livreId)
         │    ├── → CritiquesScreen
         │    │    └── → CritiqueDetailScreen(critiqueId)
         │    ├── → SearchScreen
         │    │    └── → [EmissionDetail | LivreDetail | CritiqueDetail]
         │    ├── → RecommendationsScreen
         │    │    └── → LivreDetailScreen(livreId)
         │    └── → OnKindleScreen  (accessible depuis Home uniquement, pas la NavBar)
         │         └── → LivreDetailScreen(livreId)  (si discuté au Masque)
         └── AboutScreen (version db, date export)
```

## Flux de mise à jour des données

### Flux manuel (V1)

```
1. Développeur lance : python scripts/export_mongo_to_sqlite.py
2. lmelp.db généré dans app/src/main/assets/
3. git tag v1.x.x && git push origin v1.x.x
4. GitHub Actions :
   a. Checkout du code (avec lmelp.db committé)
   b. Build APK signé
   c. Crée GitHub Release avec l'APK
5. Utilisateur télécharge l'APK depuis GitHub Releases
6. Installe sur Android (sideload ou store interne)
```

### Flux semi-automatisé possible (V2)

```
GitHub Actions (sur tag) :
1. Export MongoDB via secret MONGO_URI
2. lmelp.db généré dans le runner CI
3. Build APK avec la db fraîche
4. Publish GitHub Release
```

## Précalcul côté export

Pour éviter des calculs lourds sur mobile, le script Python précalcule :

### Palmarès
```sql
-- Calculé à l'export, stocké dans la table palmares
SELECT livre_id, AVG(note) as note_moyenne, COUNT(*) as nb_avis
FROM avis
WHERE note IS NOT NULL
GROUP BY livre_id
ORDER BY note_moyenne DESC
```

### Recommandations SVD
- Matrice critiques × livres construite depuis les `avis`
- SVD (Singular Value Decomposition) via numpy/scipy
- Top N recommandations précalculées et stockées dans `recommendations`
- Reproductible depuis `back-office-lmelp` (même algorithme)

### Index FTS5
```sql
-- Construit à l'export pour recherche instantanée
INSERT INTO search_index(type, ref_id, content) VALUES
  ('emission', id, titre || ' ' || description),
  ('livre', id, titre || ' ' || auteur_nom || ' ' || editeur),
  ('critique', id, nom),
  ('auteur', id, nom);
```

## Taille estimée

| Composant | Taille estimée |
|-----------|---------------|
| APK sans données | ~5 MB |
| lmelp.db (SQLite) | ~8-12 MB |
| APK total | ~13-17 MB |
| Empreinte installée | ~25-30 MB |

## Dépendances principales

### Android (Kotlin)
```kotlin
// build.gradle.kts (app)
dependencies {
    implementation("androidx.room:room-runtime:2.6.x")
    implementation("androidx.room:room-ktx:2.6.x")
    ksp("androidx.room:room-compiler:2.6.x")

    implementation("androidx.compose.ui:ui:1.6.x")
    implementation("androidx.compose.material3:material3:1.2.x")
    implementation("androidx.navigation:navigation-compose:2.7.x")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.x")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.x")
}
```

### Python (script export)
```toml
# pyproject.toml
dependencies = [
    "pymongo>=4.6",
    "numpy>=1.26",
    "scipy>=1.12",
    "click>=8.1",        # CLI arguments
    "python-dotenv>=1.0",# Configuration via .env
]
```
