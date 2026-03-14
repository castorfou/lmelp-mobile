# Issue #54 + #57 — Cache couvertures persistant + couvertures incorrectes

## Contexte

- **Issue #54** : maintenir un cache persistant des couvertures entre réinstallations
- **Issue #57** (fusionnée dans #54) : couvertures incorrectes affichées (mauvais livre)

## Architecture finale

### Flux complet
1. **PC** (`check_covers.py --fetch`) → scrape Babelio → écrit `app/src/main/assets/couvertures_cache.json`
2. **App au démarrage** → `HomeRepository.loadCache()` lit le JSON → passe URLs à Coil
3. **Coil** → télécharge les images (une seule fois) → disk cache `getExternalFilesDir/coil_image_cache`
4. **Lancements suivants** → Coil sert depuis disk cache → zéro réseau

### Priorité de lecture du cache dans l'app
1. `getExternalFilesDir/couvertures_cache.json` (fichier externe, survit aux réinstallations)
2. `assets/couvertures_cache.json` (embarqué dans l'APK, fallback premier lancement)

## Fichiers modifiés

### Android

**`app/src/main/java/com/lmelp/mobile/data/repository/HomeRepository.kt`**
- `CACHE_VERSION = 2` + `CACHE_VERSION_KEY = "_version"` — purge automatique cache obsolète
- `loadCache()` : charge depuis fichier externe OU assets/ en fallback
- `parseCacheJson()` : accepte URLs CVT_ Babelio ET `m.media-amazon.com` (CDN stable)
- `persistCache()` : écrit `_version` dans le JSON
- `babelioMutex` + délai 3s minimum entre requêtes Babelio depuis le device
- `fetchCouvertureBabelio(urlBabelio, titreLivre)` — paramètre `titreLivre` ajouté
- `fetchFromBabelio()` : OkHttp (remplace HttpURLConnection), vérifie redirect ID, valide titre page, ne cache que CVT_
- `pageTitleMatchesLivre()` : normalise accents, mots ≥4 chars (fallback ≥2 chars si titre très court type "Eva")
- `getCachedCouverture()` : `internal` (pour les tests)

**`app/src/main/java/com/lmelp/mobile/viewmodel/HomeViewModel.kt`**
- `fetchCouvertureBabelio(slide.urlBabelio, slide.titre)` — passage du titre

**`app/build.gradle.kts`**
- `testImplementation("org.json:json:20231013")` — JSONObject disponible en tests JVM

**`app/src/main/assets/couvertures_cache.json`**
- Nouveau fichier, embarqué dans l'APK, 41 entrées (22 CVT_ Babelio + 19 Amazon)

### Tests

**`app/src/test/java/com/lmelp/mobile/HomeRepositoryTest.kt`** (créé de zéro)
- `buildRepository(assetsJson)` — mock AssetManager pour tester le fallback assets/
- Tests `extractCouvertureUrl` (og:image priorité, fallbacks, Amazon, Babelio absolu)
- Tests `pageTitleMatchesLivre` (accents, casse, mots courts, titres très courts)
- Tests cache versioning (obsolète ignoré + supprimé, bonne version acceptée)
- Tests fallback assets/ (utilisé si pas de fichier externe, priorité fichier externe)
- Tests Amazon : `m.media-amazon.com` accepté, `ecx.images-amazon.com` rejeté

### Scripts

**`scripts/check_covers.py`** (nouveau)
- Génère rapport HTML des couvertures attendues vs cache
- `--fetch` : scrape Babelio depuis le PC (pas bloqué), écrit `assets/couvertures_cache.json`, pousse via adb
- `--force` : re-fetche même les entrées en cache
- `--push` : force le push adb
- Charge `scripts/.env` via dotenv (`BABELIO_COOKIE`)
- Headers Firefox complets (User-Agent Firefox Linux, Cookie jstsToken, gzip sans br)
- Décode ISO-8859-1 (charset Babelio) après décompression gzip
- Accepte CVT_ ET Amazon (contrairement à l'app qui filtre CVT_-only pour persistance)
- Délai 3–4s + jitter aléatoire entre requêtes

**`scripts/inspect_cover_cache.sh`** (nouveau)
- Inspecte état du cache sur le device via adb
- Section 1 : SharedPreferences legacy (purgé)
- Section 2 : `couvertures_cache.json` sur le device
- Section 3 : disk cache Coil (`getExternalFilesDir/coil_image_cache`)

**`scripts/.env.example`**
- `BABELIO_COOKIE` ajouté

## Pièges rencontrés

### Babelio bloque le device Android
- `UnknownHostException` après la 1ère requête depuis l'app → IP device bloquée
- Solution : ne jamais fetcher depuis le device, toujours depuis le PC via `check_covers.py --fetch`
- Le cookie `jstsToken` est nécessaire (captcha première visite)

### Encoding Babelio
- Babelio répond en **ISO-8859-1**, pas UTF-8 → titres corrompus sans détection charset
- Fix : lire `Content-Type: charset=` depuis les headers HTTP
- Babelio répond en **Brotli** (`br`) si demandé → urllib ne décompresse pas br
- Fix : retirer `br` de `Accept-Encoding`, garder `gzip, deflate` uniquement

### JSONObject en tests JVM
- `org.json` est une lib Android, pas disponible en JVM → tests silencieux
- Fix : `testImplementation("org.json:json:20231013")`

### OkHttp vs HttpURLConnection
- `HttpURLConnection` → `FileNotFoundException` (HTTP 4xx) sur toutes les URLs Babelio
- Fix : migrer vers OkHttp (déjà dépendance via `coil-network-okhttp`)

### Titres très courts
- "Eva" (3 chars), "Le nom sur le mur" (tous mots ≤3 chars) → `words >= 4` = liste vide → match = False
- Fix : fallback sur mots ≥2 chars si aucun mot ≥4

### URLs Amazon dans le cache
- `ecx.images-amazon.com` = ancien CDN instable → rejeté
- `m.media-amazon.com` = CDN actuel stable → accepté (dans parseCacheJson ET dans le script)

## Workflow mise à jour DB

```bash
# Rare (1x toutes les 2-3 semaines)
python scripts/export_mongo_to_sqlite.py --force
python scripts/check_covers.py --fetch --no-open
./gradlew installDebug

# Dev quotidien (inchangé)
./gradlew installDebug
```

## Variables d'environnement

`scripts/.env` (ignoré git) :
- `BABELIO_COOKIE` — cookie Firefox complet avec `jstsToken` (expire périodiquement)
- Si 403 sur `--fetch` : recopier depuis Firefox F12 → Console → `document.cookie` + jstsToken depuis Réseau

## adb

`/home/vscode/android-sdk/platform-tools/adb` (pas dans le PATH par défaut)
