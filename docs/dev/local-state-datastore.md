# État local utilisateur — DataStore

## Contexte

La base `lmelp.db` est **en lecture seule** : elle est générée par export MongoDB et embarquée dans l'APK (ou poussée via ADB). Elle ne peut pas stocker d'état propre à l'utilisateur (préférences, annotations, épingles).

Pour tout état local qui doit **persister entre les sessions** sans modifier la base Room, l'application utilise **Jetpack DataStore Preferences**.

## UserPreferencesRepository

`app/src/main/java/com/lmelp/mobile/data/repository/UserPreferencesRepository.kt`

Dépôt unique pour toutes les préférences utilisateur locales. Il implémente l'interface `PinnedReadingStorage` (voir ci-dessous).

### Clés DataStore actuelles

| Clé | Type | Usage |
|-----|------|-------|
| `show_hors_masque` | `Boolean` | Afficher/masquer les livres hors Masque dans Mon Palmarès |
| `pinned_reading` | `Set<String>` | IDs des livres épinglés "en cours de lecture" dans Sur ma liseuse |

### Ajouter une nouvelle préférence

```kotlin
private val MA_PREF = stringPreferencesKey("ma_pref")  // ou booleanPreferencesKey, intPreferencesKey…

val maPref: Flow<String> = context.dataStore.data
    .map { prefs -> prefs[MA_PREF] ?: "valeur_par_defaut" }

suspend fun setMaPref(value: String) {
    context.dataStore.edit { prefs -> prefs[MA_PREF] = value }
}
```

## Pattern testabilité — Interface extraite

`UserPreferencesRepository` dépend de `Context` (Android), ce qui empêche son utilisation directe dans les tests JVM purs (sans émulateur).

**Solution** : extraire une interface pour la partie à tester.

### Exemple : PinnedReadingStorage

```kotlin
// Dans UserPreferencesRepository
interface PinnedReadingStorage {
    val pinnedReading: Flow<Set<String>>
    suspend fun togglePinnedReading(livreId: String)
    suspend fun removePinned(livreId: String)
}

class UserPreferencesRepository(context: Context) : PinnedReadingStorage {
    // implémentation DataStore réelle
}
```

```kotlin
// Dans le fichier de test
class FakeUserPreferencesRepository : UserPreferencesRepository.PinnedReadingStorage {
    private val _pinned = MutableStateFlow<Set<String>>(emptySet())
    override val pinnedReading: Flow<Set<String>> = _pinned

    override suspend fun togglePinnedReading(livreId: String) {
        val current = _pinned.value
        _pinned.value = if (livreId in current) current - livreId else current + livreId
    }

    override suspend fun removePinned(livreId: String) {
        _pinned.value = _pinned.value - livreId
    }
}
```

Le ViewModel reçoit l'interface, pas la classe concrète :

```kotlin
class OnKindleViewModel(
    private val repository: OnKindleRepository,
    private val pinnedStorage: UserPreferencesRepository.PinnedReadingStorage? = null
) : ViewModel()
```

Ainsi, les tests instancient `FakeUserPreferencesRepository` sans aucune dépendance Android.

## Cas d'usage : épingles "en cours de lecture"

La fonctionnalité d'épinglage (issue #75) illustre le pattern complet :

1. **Stockage** : `stringSetPreferencesKey("pinned_reading")` dans DataStore
2. **Lecture initiale** : `init { pinnedStorage?.pinnedReading?.first() }` dans le ViewModel
3. **Auto-nettoyage** : au chargement, les livres épinglés dont `calibre_lu = true` sont automatiquement désépinglés via `removePinned()` — cela couvre la mise à jour DB via `lmelp-update-mobile`
4. **Ordre** : les épinglés sont placés en tête dans `loadOnKindle()` après annotation `isPinned`

## Référence

- [Jetpack DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore)
- `app/src/test/java/com/lmelp/mobile/OnKindlePinTest.kt` — exemple complet de tests avec fake
