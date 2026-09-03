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
| `pinned_reading` | `Set<String>` | IDs des livres épinglés manuellement "en cours de lecture" dans Sur ma liseuse |
| `auto_pin_dismissed` | `Set<String>` | IDs des livres dont l'épinglage automatique (`onkindle.en_cours_lecture`) a été explicitement refusé par l'utilisateur (issue #131) |

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

## Cas d'usage : auto-épinglage "en cours de lecture" (issue #131)

Extension du pattern ci-dessus pour épingler automatiquement un livre selon les données
Calibre/KOReader (`onkindle.en_cours_lecture`, voir [data-schema.md](data-schema.md)), sans
action de l'utilisateur, tout en gardant le contrôle manuel :

1. **Ensemble effectif des épinglés** calculé dans `loadOnKindle()` :
   `pinnedIds (manuel) ∪ { livres avec enCoursLecture=true et non présents dans autoPinDismissed }`
2. **Retrait d'un livre auto-épinglé** : `togglePin(livreId)` distingue deux cas — si le livre
   est dans `pinnedBookIds` (épingle manuelle), toggle classique via `togglePinnedReading()` ;
   sinon, s'il est épinglé uniquement via l'auto-pin (`enCoursLecture=true`), l'appel route vers
   `dismissAutoPin(livreId)` (ajout à `auto_pin_dismissed`, pas un toggle réversible en un clic —
   c'est une confirmation de retrait durable).
3. **Nettoyage de `auto_pin_dismissed`** : au chargement, un livre dismissed dont
   `enCoursLecture` est redevenu `false` ou dont `calibreLu` est passé à `true` est retiré de
   `auto_pin_dismissed` via `clearAutoPinDismissed()` — le refus n'a plus lieu d'être une fois
   la condition source disparue.
4. **Non-régression** : l'épinglage manuel d'un livre sans `enCoursLecture` continue de
   fonctionner à l'identique (issue #75), et les deux catégories d'épinglés restent groupées en
   tête de liste.

Voir `app/src/test/java/com/lmelp/mobile/OnKindleAutoPinTest.kt` pour les cas de test complets.

## Référence

- [Jetpack DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore)
- `app/src/test/java/com/lmelp/mobile/OnKindlePinTest.kt` — exemple complet de tests avec fake
- `app/src/test/java/com/lmelp/mobile/OnKindleAutoPinTest.kt` — tests de l'auto-épinglage (issue #131)
