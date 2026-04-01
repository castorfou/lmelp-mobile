# CLAUDE.md — lmelp-mobile

Guide pour Claude Code lors du développement de l'application Android **lmelp-mobile**.

## Vue d'ensemble du projet

Application Android **offline-first** pour consulter le contenu de Le Masque et la Plume.

- **Langage** : Kotlin
- **UI** : Jetpack Compose
- **Base de données** : Room (SQLite embarqué dans l'APK)
- **Architecture** : MVVM (Model-View-ViewModel)
- **Script d'export** : Python 3.11+ (MongoDB → SQLite)

## Structure du projet

```
├── app/src/main/
│   ├── assets/lmelp.db              # Base SQLite embarquée (NE PAS éditer manuellement)
│   └── java/com/lmelp/mobile/
│       ├── data/
│       │   ├── db/                  # Room Database, DAOs
│       │   ├── model/               # Entities Room + data classes
│       │   └── repository/          # Repositories (source unique de vérité)
│       ├── ui/
│       │   ├── theme/               # Couleurs, typographie, thème
│       │   ├── components/          # Composables réutilisables
│       │   ├── emissions/           # Écran Émissions
│       │   ├── palmares/            # Écran Palmarès
│       │   ├── critiques/           # Écran Critiques
│       │   ├── search/              # Écran Recherche
│       │   └── recommendations/    # Écran Recommandations
│       ├── viewmodel/               # ViewModels par feature
│       └── MainActivity.kt
├── scripts/
│   └── export_mongo_to_sqlite.py
├── docs/
│   ├── architecture.md
│   ├── data-schema.md
│   └── ci-cd.md
└── pyproject.toml
```

## Environnement local

- **adb** : `/home/vscode/android-sdk/platform-tools/adb` (pas dans le PATH par défaut)
- **MongoDB** : port **27018** (pas 27017)
- **Calibre** : `/home/vscode/Calibre Library/metadata.db`, virtual library `guillaume`
- **sqlite3** : disponible via le venv Python — toujours activer avant usage :
  ```bash
  source /workspaces/lmelp-mobile/.venv/bin/activate
  sqlite3 app/src/main/assets/lmelp.db "..."
  ```

## Commandes essentielles

### Android

```bash
# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease

# Installer sur device/émulateur connecté
./gradlew installDebug

# Lancer les tests
./gradlew test
./gradlew connectedAndroidTest

# Lint
./gradlew lint
```

### Script Python (export données)

```bash
# Installer dépendances
uv pip install -e .

# ✅ Export complet MongoDB → SQLite — les paramètres sont lus depuis scripts/.env
python scripts/export_mongo_to_sqlite.py --force

# Vérifier l'intégrité de la base générée
python scripts/export_mongo_to_sqlite.py --verify app/src/main/assets/lmelp.db

# Linting Python
ruff check scripts/
ruff format scripts/
```

### Configuration via scripts/.env

Les paramètres d'environnement local sont stockés dans `scripts/.env` (ignoré par git).
Copier `scripts/.env.example` et adapter :

```bash
cp scripts/.env.example scripts/.env
# Éditer scripts/.env avec les valeurs locales
```

Variables disponibles :
- `LMELP_MONGO_URI` — URI MongoDB (défaut: `mongodb://localhost:27017`)
- `LMELP_OUTPUT` — chemin de sortie SQLite (défaut: `lmelp.db`)
- `LMELP_CALIBRE_DB` — chemin vers `metadata.db` de Calibre
- `LMELP_CALIBRE_VIRTUAL_LIBRARY` — tag de la virtual library Calibre (ex: `guillaume`)

### ⚠️ Règle critique : toujours avoir `LMELP_CALIBRE_DB` configuré

**Ne jamais regénérer `lmelp.db` sans Calibre configuré.**

Sans Calibre, `calibre_in_library = 0` et `calibre_lu = 0` pour tous les
livres → filtre "Lus" vide dans l'app, aucun ✓ affiché (voir issue #42).

Un test de non-régression dans `tests/test_lmelp_db_integrity.py` bloque la CI
si `lmelp.db` est commité sans données Calibre.

### Après regénération de lmelp.db

Room met en cache la base locale au premier lancement. Si la version Room n'a
pas changé, **désinstaller et réinstaller** l'app pour forcer la recopie :

```bash
adb uninstall com.lmelp.mobile
./gradlew installDebug
```

### Mise à jour DB sur le téléphone sans recompiler l'APK (issue #81)

Pour pousser une nouvelle base directement sur le téléphone (sans rebuild APK) :

```bash
# Pré-requis : téléphone en USB, mode Transfert de fichiers, débogage USB activé
adb -a start-server          # flag -a obligatoire : écoute sur 0.0.0.0 (pas 127.0.0.1)
docker exec lmelp-export export-and-push
```

Le container `lmelp-export` tourne en daemon avec la stack `docker-lmelp`. Il :
- Se connecte à MongoDB via le réseau `lmelp-network`
- Exporte la base avec les données Calibre
- La pousse sur le téléphone via le daemon ADB du laptop (`host-gateway:5037`)
- Redémarre l'app

Le service `lmelp-export` est défini dans le `docker-compose.yml` du repo `castorfou/docker-lmelp`.

## Architecture MVVM

```
UI (Composables)
    ↕ observe StateFlow
ViewModel
    ↕ suspend functions
Repository
    ↕ coroutines
Room DAO
    ↕
SQLite (lmelp.db)
```

**Règles :**
- Les Composables n'accèdent JAMAIS directement aux DAOs
- Les ViewModels exposent `StateFlow<UiState>` (pas LiveData)
- Les Repositories sont les seuls à parler aux DAOs
- Toute opération base de données dans une coroutine (`viewModelScope`)

## Patterns Kotlin/Compose à suivre

### ViewModel pattern

```kotlin
// ✅ CORRECT
data class EmissionsUiState(
    val isLoading: Boolean = false,
    val emissions: List<EmissionUi> = emptyList(),
    val error: String? = null
)

class EmissionsViewModel(private val repository: EmissionsRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(EmissionsUiState())
    val uiState: StateFlow<EmissionsUiState> = _uiState.asStateFlow()

    init {
        loadEmissions()
    }

    private fun loadEmissions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val emissions = repository.getAllEmissions()
                _uiState.update { it.copy(isLoading = false, emissions = emissions) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }
}
```

### Composable pattern

```kotlin
// ✅ CORRECT - Séparer Screen (avec ViewModel) et Content (sans ViewModel)
@Composable
fun EmissionsScreen(
    viewModel: EmissionsViewModel = viewModel(),
    onEmissionClick: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    EmissionsContent(uiState = uiState, onEmissionClick = onEmissionClick)
}

@Composable
fun EmissionsContent(
    uiState: EmissionsUiState,
    onEmissionClick: (String) -> Unit
) {
    when {
        uiState.isLoading -> LoadingIndicator()
        uiState.error != null -> ErrorMessage(uiState.error)
        else -> EmissionsList(uiState.emissions, onEmissionClick)
    }
}
```

### Room DAO pattern

```kotlin
// ✅ CORRECT - Toujours suspend ou Flow
@Dao
interface EmissionsDao {
    @Query("SELECT * FROM emissions ORDER BY date DESC")
    suspend fun getAllEmissions(): List<EmissionEntity>

    @Query("SELECT * FROM emissions WHERE id = :id")
    suspend fun getEmissionById(id: String): EmissionEntity?

    // Flow pour données réactives si nécessaire
    @Query("SELECT * FROM emissions ORDER BY date DESC")
    fun observeAllEmissions(): Flow<List<EmissionEntity>>
}
```

## Base SQLite embarquée — règles critiques

### Initialisation au premier lancement

```kotlin
// ✅ CORRECT - Copier l'asset vers data/ au premier lancement
val db = Room.databaseBuilder(context, LmelpDatabase::class.java, "lmelp.db")
    .createFromAsset("lmelp.db")  // Copie depuis assets/ si absent
    .fallbackToDestructiveMigration()  // En dev : rebuild si schema change
    .build()
```

### Versioning de la base

- Chaque export SQLite incrémente `PRAGMA user_version` dans le fichier
- L'appli affiche la date du dernier export dans les paramètres
- En cas de mismatch de version : `fallbackToDestructiveMigration()` acceptable (données readonly)

### ⚠️ Erreur fréquente : "Room cannot verify the data integrity"

**Symptôme :** Au lancement, l'app affiche `Erreur : Room cannot verify the data integrity. Looks like you've changed schema but forgot to update the version number.`

**Cause :** Ajout/suppression/modification d'un champ dans une `@Entity` sans incrémenter la version Room.

**Résolution :** Deux fichiers à synchroniser :

1. `app/src/main/java/com/lmelp/mobile/data/db/LmelpDatabase.kt` — incrémenter `version` :
```kotlin
@Database(..., version = 2, ...)  // incrémenter
```

2. `scripts/export_mongo_to_sqlite.py` — même numéro dans `PRAGMA user_version` :
```python
cur.execute("PRAGMA user_version = 2")  # même valeur
```

Puis regénérer `lmelp.db` via le script d'export. `fallbackToDestructiveMigration()` gère la migration automatiquement (données readonly = OK).

## Schéma SQLite

Voir [docs/data-schema.md](docs/data-schema.md) pour le schéma complet.

**Tables principales :**
- `emissions` — émissions avec date, durée
- `episodes` — épisodes liés aux émissions
- `livres` — livres avec titre, auteur, éditeur
- `auteurs` — auteurs
- `critiques` — les 25 critiques
- `avis` — avis individuels (note 1-10, commentaire)
- `palmares` — vue précalculée (note moyenne par livre)
- `recommendations` — recommandations SVD précalculées

**Données précalculées à l'export :**
- Palmarès (moyenne des notes par livre)
- Recommandations SVD (coûteuses à calculer sur mobile)
- Noms des auteurs/critiques dénormalisés dans `avis` pour éviter les jointures

## Recherche full-text

SQLite FTS5 pour la recherche :

```sql
-- Table virtuelle FTS5
CREATE VIRTUAL TABLE search_index USING fts5(
    type,        -- 'emission' | 'livre' | 'auteur' | 'critique'
    ref_id,      -- ID de l'entité référencée
    content,     -- Texte indexé
    tokenize = 'unicode61 remove_diacritics 2'  -- Insensible aux accents
);
```

## Conventions de nommage

| Contexte | Convention | Exemple |
|----------|-----------|---------|
| Fichiers Kotlin | PascalCase | `EmissionsViewModel.kt` |
| Composables | PascalCase | `EmissionCard` |
| ViewModels | `XxxViewModel` | `EmissionsViewModel` |
| DAOs | `XxxDao` | `EmissionsDao` |
| Entities Room | `XxxEntity` | `EmissionEntity` |
| UI models | `XxxUi` | `EmissionUi` |
| Repositories | `XxxRepository` | `EmissionsRepository` |
| Packages | lowercase | `com.lmelp.mobile.ui.emissions` |

## Tests

```kotlin
// Tests ViewModel (JUnit + coroutines)
// Tests DAO (in-memory Room database)
// Tests Composables (Compose UI testing)

// Exemple DAO test
@RunWith(AndroidJUnit4::class)
class EmissionsDaoTest {
    private lateinit var db: LmelpDatabase
    private lateinit var dao: EmissionsDao

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            LmelpDatabase::class.java
        ).build()
        dao = db.emissionsDao()
    }

    @After
    fun teardown() { db.close() }
}
```

## Script Python d'export

Le script `scripts/export_mongo_to_sqlite.py` :

- Se connecte à MongoDB `masque_et_la_plume`
- Exporte les collections nécessaires vers SQLite
- Précalcule le palmarès et les recommandations SVD
- Construit l'index FTS5 pour la recherche
- Écrit `PRAGMA user_version` avec timestamp Unix

**Linting Python :**
- Ruff (format + check)
- MyPy pour le type checking
- Configuration dans `pyproject.toml`

## CI/CD — GitHub Actions

Workflow `release.yml` déclenché sur tag `v*.*.*` :
1. Export MongoDB → SQLite (si secret `MONGO_URI` configuré)
2. Build APK signé
3. Création GitHub Release avec l'APK en asset

Voir [docs/ci-cd.md](docs/ci-cd.md) pour la configuration complète.

## Anti-patterns à éviter

```kotlin
// ❌ Ne jamais faire d'opération DB sur le main thread
val emissions = dao.getAllEmissions()  // Crash sur Android !

// ❌ Ne jamais exposer des Entity Room directement à l'UI
data class EmissionEntity(...)  // Pas dans les Composables

// ❌ Ne jamais hardcoder le chemin de la base
val db = SQLiteDatabase.openDatabase("/data/data/.../lmelp.db", ...)

// ❌ Ne pas utiliser LiveData (préférer StateFlow)
val emissions: LiveData<List<...>> = ...
```

## Ressources

- [back-office-lmelp](https://github.com/castorfou/back-office-lmelp) — source des données
- [Jetpack Compose docs](https://developer.android.com/jetpack/compose)
- [Room docs](https://developer.android.com/training/data-storage/room)
- [Architecture Guide Android](https://developer.android.com/topic/architecture)
