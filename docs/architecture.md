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
│    ├── Construit index FTS4 (recherche full-text)               │
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
│    ├── Recherche (FTS4)                                         │
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
│  UserPreferencesRepository  (DataStore — état local utilisateur)│
│                                                                 │
│  Responsabilités :                                              │
│    - Source unique de vérité                                    │
│    - Mapping Entity → UI model                                  │
│    - Coordination des DAOs                                      │
│    - UserPreferencesRepository : préférences hors DB (épingles) │
└───────────────────────────┬─────────────────────────────────────┘
                            │ Room DAOs + DataStore
┌───────────────────────────▼─────────────────────────────────────┐
│  Database Layer (Room)                                          │
│                                                                 │
│  LmelpDatabase (RoomDatabase)                                   │
│    ├── EmissionsDao                                             │
│    ├── LivresDao                                                │
│    ├── CritiquesDao                                             │
│    ├── AvisDao                                                  │
│    ├── OnKindleDao                                              │
│    └── SearchDao (FTS4)                                         │
│                                                                 │
│  SQLite : app/src/main/assets/lmelp.db                          │
│    └── Copié dans /data/data/.../databases/ au 1er lancement   │
│                                                                 │
│  DataStore (user_prefs) — état local persisté hors lmelp.db     │
│    ├── show_hors_masque (Boolean)                               │
│    └── pinned_reading (Set<String>) — livres épinglés           │
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

### Flux découplé données/app (V3, issues #116 + #118)

Sépare la mise à jour des données (fréquente, ~1x/semaine) de la mise à jour de l'app (rare). Voir l'ADR complet : [docs/dev/adr/0001-separation-maj-appli-donnees.md](dev/adr/0001-separation-maj-appli-donnees.md).

```
Côté serveur (NAS, container lmelp-export en daemon + anacron) :
1. scripts/docker_export_and_publish_release.sh
   ├── Export MongoDB → SQLite (avec données Calibre)
   ├── scripts/generate_data_release_metadata.py → metadata.json
   │     (export_date, export_version=timestamp, sha256, compteurs)
   └── gh release upload data-latest lmelp.db metadata.json --clobber
        (GitHub Release dédiée, distincte de la release APK vX.Y.Z)

Côté app Android :
2. Check silencieux au lancement (LmelpApp.onCreate) + bouton manuel
   dans l'écran À propos — DataUpdateRepository.checkForUpdate()
   compare db_metadata.version (local) à metadata.json.export_version
   (distant), jamais PRAGMA user_version (voir CLAUDE.md)
3. Sur action utilisateur ("Mettre à jour") :
   OkHttpGitHubReleaseApi télécharge lmelp.db (~5 Mo)
   → Sha256Verifier vérifie l'intégrité contre metadata.json.sha256
   → DatabaseFileReplacer supprime lmelp.db-shm/-wal résiduels puis
     remplace lmelp.db (bug WAL connu, issue #101)
   → ProcessRestarter ferme l'app (état Room garanti propre) ;
     l'utilisateur la rouvre lui-même (pas de relance automatique
     possible sur Android 12+, restrictions de lancement d'activité
     en arrière-plan)
```

Réseau strictement optionnel et best-effort : un échec (pas de connexion, timeout, erreur HTTP) ne bloque jamais l'usage normal de l'app en offline. Voir `app/src/main/java/com/lmelp/mobile/data/repository/DataUpdateRepository.kt`.

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

### Index FTS4
```sql
-- Construit à l'export pour recherche instantanée
-- content = texte normalisé sans accents (indexé)
-- display_content = texte original avec accents (affiché)
INSERT INTO search_index(type, ref_id, content, display_content) VALUES
  ('emission', id, normalize(titre || ' ' || description), titre || ' ' || description),
  ('livre', id, normalize(titre || ' ' || auteur_nom || ' ' || editeur), titre),
  ('critique', id, normalize(nom), nom),
  ('auteur', id, normalize(nom), nom);
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
