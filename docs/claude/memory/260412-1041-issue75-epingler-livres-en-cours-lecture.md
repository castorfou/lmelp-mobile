# Issue #75 — Épingler des livres "en cours de lecture" (Sur ma liseuse)

## Contexte

Ajout de la fonctionnalité d'épinglage de livres dans l'écran "Sur ma liseuse" : l'utilisateur peut marquer un livre comme "en cours de lecture" via un appui long, ce qui le fait remonter en tête de liste avec une icône punaise verte.

## Architecture retenue

**Stockage local (pas de modif Room)** : les épingles sont purement un état utilisateur local, stockées dans DataStore via `UserPreferencesRepository` avec `stringSetPreferencesKey("pinned_reading")`.

**Interface `PinnedReadingStorage`** extraite dans `UserPreferencesRepository` pour permettre les tests unitaires sans `Context`/DataStore. Le `FakeUserPreferencesRepository` dans les tests implémente cette interface avec un `MutableStateFlow<Set<String>>` en mémoire.

## Fichiers modifiés

- `app/build.gradle.kts` : ajout de `"androidx.compose.material:material-icons-extended"` pour l'icône `PushPin` (via compose BOM, pas de version explicite)

- `app/src/main/java/com/lmelp/mobile/data/repository/UserPreferencesRepository.kt` :
  - Interface interne `PinnedReadingStorage` avec `pinnedReading: Flow<Set<String>>`, `togglePinnedReading()`, `removePinned()`
  - `UserPreferencesRepository` implémente `PinnedReadingStorage`
  - `stringSetPreferencesKey("pinned_reading")` pour persister les IDs épinglés

- `app/src/main/java/com/lmelp/mobile/data/model/UiModels.kt` : `isPinned: Boolean = false` ajouté à `OnKindleUi`

- `app/src/main/java/com/lmelp/mobile/viewmodel/OnKindleViewModel.kt` :
  - Second paramètre `pinnedStorage: UserPreferencesRepository.PinnedReadingStorage? = null`
  - `pinnedBookIds: Set<String>` dans `OnKindleUiState`
  - `init` charge les épingles via `pinnedStorage?.pinnedReading?.first()`
  - `togglePin(livreId)` : toggle + rechargement
  - `loadOnKindle()` : auto-désépinglage si `calibreLu = true` + annotation `isPinned` + tri (épinglés en tête)

- `app/src/main/java/com/lmelp/mobile/ui/onkindle/OnKindleScreen.kt` :
  - Paramètre `userPrefsRepository: UserPreferencesRepository.PinnedReadingStorage? = null`
  - `@OptIn(ExperimentalFoundationApi::class)` + `combinedClickable` pour appui long → bottom sheet
  - Icône `Icons.Filled.PushPin` (28dp, vert Lmelp) à gauche de la note dans la zone droite
  - `ModalBottomSheet` avec option "📌 En cours de lecture" / "Retirer de 'En cours de lecture'"
  - `LazyColumn` splitée : épinglés + `HorizontalDivider` (padding vertical 12dp) + non-épinglés

- `app/src/main/java/com/lmelp/mobile/Navigation.kt` : passage de `userPrefsRepository = app.userPreferencesRepository` à `OnKindleScreen`

## Tests

`app/src/test/java/com/lmelp/mobile/OnKindlePinTest.kt` — 7 cas :
1. Par défaut aucun livre épinglé
2. `togglePin` épingle un livre
3. `togglePin` deux fois désépingle
4. Épinglé en tête même avec tri NOTE_MASQUE défavorable
5. Plusieurs épinglés suivent le tri interne (ALPHA)
6. `isPinned` propagé dans `OnKindleUi` à partir des prefs existantes
7. Auto-désépinglage si `calibreLu = true` au chargement

## Points saillants

- **Pattern testabilité** : extraire une interface depuis `UserPreferencesRepository` (qui dépend de `Context`) permet de la mocker proprement dans les tests JVM purs sans instrumentation. `FakeUserPreferencesRepository` est déclaré directement dans le fichier de test.

- **Auto-désépinglage** : quand la DB est regénérée via `lmelp-update-mobile` et qu'un livre devient `calibreLu = true`, il est automatiquement retiré des épingles au prochain chargement. Cela couvre le cas naturel "je termine ma lecture → je regénère la DB → le livre disparaît des épingles".

- **`material-icons-extended`** : `PushPin` n'est PAS dans les icons Material de base (`material-icons-core`). Il faut impérativement la dépendance extended. Les icônes disponibles sans extended : `Home`, `Star`, `Person`, `Search`, `Settings`, `List`, `Menu`, `Close`, `Add`, `Done`, `ArrowBack`, etc.

- **Séparateur visuel** : `HorizontalDivider` avec `padding(horizontal=16.dp, vertical=12.dp)` inséré comme `item(key="__separator__")` dans la `LazyColumn` entre épinglés et non-épinglés. Clé unique en string nécessaire pour éviter les conflits d'animation.
