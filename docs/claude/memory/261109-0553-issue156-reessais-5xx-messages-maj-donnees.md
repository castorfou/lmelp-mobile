# Issue #156 — Mise à jour des données : réessais sur les 5xx de GitHub et messages clairs

## Contexte

Le 2026-11-08, « Vérifier les mises à jour » affichait « Erreur : Échec du téléchargement de metadata.json : HTTP 504 ». La cause était une panne de GitHub Releases (504 sur `github.com/.../releases/download/...` pour tous les dépôts testés, castorfou/PyFoundry#133), pas un bug de l'app. Mais l'app abandonnait dès la première 5xx, et le message brut ne disait pas que le problème était passager.

## Décisions (prises avec l'utilisateur)

- **404 sur `data-v{N}`** (aucune release pour le schéma local, comportement voulu de #132) : message neutre, sans préfixe « Erreur : ».
- **Périmètre : données uniquement**. La vérification d'une nouvelle version de l'APK (#162, `AppReleaseApi`) n'est pas traitée ici ; une issue de suivi est ouverte.

## Ce qui a été fait

1. **`app/src/main/java/com/lmelp/mobile/data/remote/HttpRetry.kt`** (nouveau) :
   - `open class HttpStatusException(val code: Int, message)` : `IOException` qui porte le code HTTP ;
   - `ReleaseNotFoundException(tag)` : `HttpStatusException(404)` levée sur `releases/tags/data-v{N}` ;
   - `DEFAULT_RETRY_DELAYS_MS = [1000, 3000]` (3 tentatives au plus) ;
   - `suspend fun <T> withRetry(delaysMs, sleep, block)` : réessaie sur `HttpStatusException` 502/503/504 et sur toute `IOException` réseau, **sauf** `UnknownHostException` (pas de réseau : attendre 4 s ne sert à rien). Pas de réessai sur 404, autres 4xx ni 500. `sleep` est injectable (défaut `delay`) ;
   - `fun updateErrorMessage(e: Throwable): String` : `ReleaseNotFoundException` → « Aucune mise à jour disponible pour cette version de l'application. » ; `UnknownHostException` → « Pas de connexion Internet. » ; 5xx, `SocketTimeoutException`, `ConnectException` → « Le serveur de mises à jour (GitHub) est momentanément indisponible. Réessayez plus tard. » ; sinon « Erreur : <message d'origine> » (code HTTP conservé).
2. **`OkHttpGitHubReleaseApi.kt`** : nouveaux paramètres de constructeur `retryDelaysMs` et `sleep`. Les trois requêtes (API release, `metadata.json`, `lmelp.db`) passent par `withRetry`. Les codes non 2xx lèvent `HttpStatusException`, et un 404 sur l'API lève `ReleaseNotFoundException`. La lecture du schéma local (`SqliteSchemaVersion.read`) reste **hors** de la boucle de réessai : une erreur de lecture du fichier ne doit pas être réessayée. Une coupure pendant la copie de `lmelp.db` relance tout le téléchargement (`outputStream()` réécrit le fichier depuis le début).
3. **`DataUpdateRepository.kt`** : `checkForUpdate()` et `applyUpdate()` produisent `Error(updateErrorMessage(e))`, toujours sous `Dispatchers.IO`. Le message SHA-256 devient « Erreur : fichier téléchargé corrompu, veuillez réessayer ». Le check silencieux au lancement ne change pas.
4. **`ui/about/AboutScreen.kt`** : `formatUpdateStateLabel` affiche `DataUpdateState.Error.message` tel quel. Le préfixe « Erreur : » n'est plus ajouté à l'affichage mais par `updateErrorMessage`, et uniquement pour le cas générique.

## Tests (18 nouveaux, 356 au total)

- `OkHttpGitHubReleaseApiTest` (MockWebServer + `sleep` qui enregistre les délais) : 504 puis 200 → succès, 3 requêtes, délais `[1000]` ; 3 × 504 → `HttpStatusException(504)`, 4 requêtes, délais `[1000, 3000]` ; 503 sur l'API réessayée ; 404 → `ReleaseNotFoundException`, 1 requête ; 403 non réessayée ; `downloadDatabase` réessaie une 502.
- `HttpRetryTest` : réessai sur timeout, pas sur `UnknownHostException` ni HTTP 500 ; `updateErrorMessage` pour chaque cause (le message 5xx ne contient ni « 504 » ni « Erreur »).
- `DataUpdateRepositoryTest` / `DataUpdateRepositoryApplyUpdateTest` : 504/503 → « momentanément indisponible » pour le check et pour `applyUpdate` ; release absente → message neutre.
- `UpdateStateFormatterTest` : `Error` affiché sans double préfixe.
- ⚠️ Mockito : une méthode `suspend` ne déclare pas d'exception vérifiée, donc `thenThrow(IOException)` est refusé (« Checked exception is invalid »). Utiliser `thenAnswer { throw HttpStatusException(...) }`.

## Vérifié

- `:app:testDebugUnitTest` (356), `lint`, `assembleDebug`, `pytest` (212), pre-commit.
- Device (Pixel 9 Pro) : installation, aucune `NetworkOnMainThreadException` ; vérification avec réseau, en mode avion et application validées par l'utilisateur.
