# Issue #168 — Vérification de l'APK : réessais sur les 5xx de GitHub et messages clairs

## Contexte

#156 a ajouté `withRetry` et `updateErrorMessage` (`app/src/main/java/com/lmelp/mobile/data/remote/HttpRetry.kt`) à la mise à jour des **données**. La vérification et le téléchargement d'une **nouvelle version de l'appli** (#162) n'en profitaient pas. Une panne de GitHub Releases (504, castorfou/PyFoundry#133) y produisait encore « Erreur : Échec du téléchargement de l'APK : HTTP 504 — réessayer ».

## Décisions

- **Pas de variante de message pour l'APK** : `updateErrorMessage` ne traite la 404 à part que pour `ReleaseNotFoundException` (`data-v{N}` absente). `OkHttpAppReleaseApi` ne lève jamais cette exception : une 404 sur `GET /releases` tombe dans le cas générique « Erreur : … HTTP 404 ». La fonction est donc réutilisée telle quelle.
- **Bouton d'erreur = message seul** : `formatAppUpdateButtonLabel(AppUpdateState.Error)` renvoie `state.message`, comme `DataUpdateState.Error`. On retire « Erreur : » (doublé) et « — réessayer » (redondant avec « Réessayez plus tard »). Le bouton reste actif, et un clic relance le téléchargement.

## Ce qui a été fait

1. **`app/src/main/java/com/lmelp/mobile/data/remote/OkHttpAppReleaseApi.kt`** : nouveaux paramètres de constructeur `retryDelaysMs = DEFAULT_RETRY_DELAYS_MS` et `sleep = { delay(it) }`, sur le modèle d'`OkHttpGitHubReleaseApi`. `fetchReleases()` et `downloadApk()` passent par `withRetry`, et les codes non 2xx lèvent `HttpStatusException(code, …)` au lieu d'une `IOException` brute. Une coupure en cours de copie relance tout le téléchargement, car `outputStream()` réécrit le fichier depuis le début.
2. **`app/src/main/java/com/lmelp/mobile/data/repository/AppUpdateRepository.kt`** : `checkForUpdate()` renvoie `AppUpdateCheckResult.Error(updateErrorMessage(e))`, toujours sous `Dispatchers.IO`.
3. **`app/src/main/java/com/lmelp/mobile/viewmodel/AboutViewModel.kt`** : `installAppUpdate()` renvoie `AppUpdateState.Error(release, updateErrorMessage(e))`.
4. **`app/src/main/java/com/lmelp/mobile/ui/about/AboutScreen.kt`** : en cas d'erreur, `formatAppUpdateButtonLabel` affiche le message tel quel.
5. **`HttpRetry.kt`** : le KDoc d'`updateErrorMessage` couvre maintenant les deux usages (données et appli) et précise la règle de la 404.

## Tests (7 nouveaux)

- `OkHttpAppReleaseApiTest` (MockWebServer, `sleep` qui enregistre les délais) :
  - 504 puis 200 sur `fetchReleases` → succès, 2 requêtes, délais `[1000]` ;
  - 3 × 504 → `HttpStatusException(504)`, 3 requêtes, délais `[1000, 3000]` ;
  - 404 → pas de réessai ;
  - `downloadApk` : 503 puis 200 → fichier écrit ; 3 × 502 → `HttpStatusException(502)`.
- `AppUpdateRepositoryTest` : 504 → « momentanément indisponible », sans « HTTP 504 ». Le test existant attend maintenant « Erreur : pas de réseau » (cas générique).
- `AboutViewModelAppUpdateTest` : un échec 504 au téléchargement donne un message clair.
- `AppUpdateStateFormatterTest` : le libellé `Error` est le message tel quel.

## Vérification sur device

`./gradlew installDebug -PversionName=0.0.1` : le check au lancement passe, sans `NetworkOnMainThreadException` dans le logcat. En mode avion, le bouton affiche « Pas de connexion Internet. ». Une fois le réseau revenu, le téléchargement et l'installation se lancent. Validé par l'utilisateur.
