package com.lmelp.mobile.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.lmelp.mobile.data.db.DerniereEmissionRow
import com.lmelp.mobile.data.db.EmissionsDao
import com.lmelp.mobile.data.db.LivresDao
import com.lmelp.mobile.data.db.MetadataDao
import com.lmelp.mobile.data.db.OnKindleDao
import com.lmelp.mobile.data.db.PalmaresDao
import com.lmelp.mobile.data.db.RecommendationsDao
import com.lmelp.mobile.data.model.SlideItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

private const val CACHE_FILE_NAME = "couvertures_cache.json"
private const val LEGACY_PREFS_NAME = "home_cache"
private const val LEGACY_KEY_PREFIX = "couverture_"
// Incrémenter cette version pour invalider le cache JSON persistant (purge automatique au démarrage)
private const val CACHE_VERSION = 2
private const val CACHE_VERSION_KEY = "_version"

class HomeRepository(
    private val metadataDao: MetadataDao,
    private val emissionsDao: EmissionsDao,
    private val palmaresDao: PalmaresDao,
    private val livresDao: LivresDao,
    private val recommendationsDao: RecommendationsDao,
    private val onKindleDao: OnKindleDao,
    private val context: Context
) {
    // Mutex pour éviter les écritures concurrentes sur le fichier JSON
    private val fileMutex = Mutex()

    // Throttle Babelio : 1 requête à la fois, espacées d'au moins 3 secondes
    private val babelioMutex = Mutex()
    private var lastBabelioFetchMs = 0L
    private val BABELIO_MIN_INTERVAL_MS = 3_000L

    // Fichier JSON dans getExternalFilesDir() — survit aux désinstallations/réinstallations.
    // Fallback sur filesDir si le stockage externe est indisponible.
    private val cacheFile: File by lazy {
        val dir = context.getExternalFilesDir(null) ?: context.filesDir
        File(dir, CACHE_FILE_NAME)
    }

    // Cache en mémoire (clé = URL Babelio complète, valeur = URL image)
    private var memCache: HashMap<String, String>? = null

    private fun loadCache(): HashMap<String, String> {
        memCache?.let { return it }
        val map = HashMap<String, String>()
        // Charger depuis le JSON persistant — rejeter le cache si la version ne correspond pas
        if (cacheFile.exists()) {
            try {
                val json = JSONObject(cacheFile.readText())
                val version = if (json.has(CACHE_VERSION_KEY)) json.getInt(CACHE_VERSION_KEY) else 0
                if (version != CACHE_VERSION) {
                    // Cache obsolète : supprimer et repartir de zéro
                    cacheFile.delete()
                } else {
                    parseCacheJson(json, map)
                }
            } catch (_: Exception) { }
        } else {
            // Fallback : cache embarqué dans les assets (pré-rempli à la compilation)
            try {
                context.assets.open("couvertures_cache.json").use { stream ->
                    val json = JSONObject(stream.bufferedReader().readText())
                    val version = if (json.has(CACHE_VERSION_KEY)) json.getInt(CACHE_VERSION_KEY) else 0
                    if (version == CACHE_VERSION) {
                        parseCacheJson(json, map)
                    }
                }
            } catch (_: Exception) { }
        }

        // Purge one-shot de l'ancien SharedPreferences "home_cache" (clés-hash, non fiables)
        try {
            val legacyPrefs: SharedPreferences =
                context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
            if (legacyPrefs.all.isNotEmpty()) {
                legacyPrefs.edit().clear().apply()
            }
        } catch (_: Exception) { }

        memCache = map
        return map
    }

    private suspend fun persistCache(cache: HashMap<String, String>) {
        fileMutex.withLock {
            withContext(Dispatchers.IO) {
                try {
                    val json = JSONObject()
                    json.put(CACHE_VERSION_KEY, CACHE_VERSION)
                    cache.forEach { (k, v) -> json.put(k, v) }
                    cacheFile.writeText(json.toString())
                } catch (_: Exception) { }
            }
        }
    }

    suspend fun getNbEmissions(): String {
        val all = metadataDao.getAllMetadata().associate { it.key to it.value }
        return all["nb_emissions"] ?: "—"
    }

    suspend fun getExportDate(): String {
        val all = metadataDao.getAllMetadata().associate { it.key to it.value }
        return all["export_date"] ?: "—"
    }

    suspend fun getDerniereEmission(): DerniereEmissionRow? {
        return emissionsDao.getDerniereEmission()
    }

    suspend fun getEmissionsSlides(limit: Int = 10): List<SlideItem> {
        return emissionsDao.getTopLivreParEmission(limit * 3)
            .distinctBy { it.emissionId }
            .take(limit)
            .map { row ->
                SlideItem(
                    livreId = row.livreId,
                    titre = row.livreTitre,
                    sousTitre = "%.1f/10".format(row.noteMoyenne),
                    noteMoyenne = row.noteMoyenne,
                    date = formatDate(row.emissionDate),
                    urlBabelio = row.urlBabelio,
                    urlCouverture = getCachedCouverture(row.urlBabelio)
                )
            }
    }

    suspend fun getPalmaresSlides(limit: Int = 10): List<SlideItem> {
        return palmaresDao.getTopPalmaresAvecUrl(limit).map { row ->
            SlideItem(
                livreId = row.livreId,
                titre = row.titre,
                sousTitre = row.auteurNom ?: "",
                noteMoyenne = row.noteMoyenne,
                urlBabelio = row.urlBabelio,
                urlCouverture = getCachedCouverture(row.urlBabelio)
            )
        }
    }

    suspend fun getOnKindleSlides(limit: Int = 10): List<SlideItem> {
        return onKindleDao.getTopOnKindleAvecUrl(limit).map { row ->
            SlideItem(
                livreId = row.livreId,
                titre = row.titre,
                sousTitre = row.auteurNom ?: "",
                noteMoyenne = row.noteMoyenne,
                urlBabelio = row.urlBabelio,
                urlCouverture = getCachedCouverture(row.urlBabelio)
            )
        }
    }

    suspend fun getConseilsSlides(limit: Int = 10): List<SlideItem> {
        return recommendationsDao.getTopRecommandationsNonLuesAvecUrl(limit).map { row ->
            SlideItem(
                livreId = row.livreId,
                titre = row.titre,
                sousTitre = row.auteurNom ?: "",
                urlBabelio = row.urlBabelio,
                urlCouverture = getCachedCouverture(row.urlBabelio)
            )
        }
    }

    private fun formatDate(isoDate: String): String {
        return try {
            val input = java.time.OffsetDateTime.parse(isoDate)
            input.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.FRENCH))
        } catch (_: Exception) { isoDate }
    }

    private fun parseCacheJson(json: JSONObject, map: HashMap<String, String>) {
        val keys = json.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            if (k.startsWith("http")) {
                val v = json.getString(k)
                // Accepter les URLs CVT_ Babelio (stables) et Amazon (pré-fetchées depuis le PC)
                if (v.contains("/couv/CVT_", ignoreCase = true) ||
                    v.contains("media-amazon.com", ignoreCase = true)) {
                    map[k] = v
                }
            }
        }
    }

    internal fun getCachedCouverture(urlBabelio: String?): String? {
        if (urlBabelio == null) return null
        return loadCache()[urlBabelio]
    }

    /**
     * Récupère l'URL de couverture pour un livre depuis sa page Babelio.
     * Résultat mis en cache dans un fichier JSON persistant (getExternalFilesDir)
     * qui survit aux désinstallations et mises à jour de la base.
     * La clé de cache est l'URL Babelio complète (pas un hash) pour éviter les collisions.
     * Les échecs (pas d'image trouvée) ne sont PAS mis en cache → retentés au prochain lancement.
     */
    suspend fun fetchCouvertureBabelio(urlBabelio: String, titreLivre: String): String? {
        // Vérification rapide sans lock (cas majoritaire : déjà en cache)
        val cached = loadCache()[urlBabelio]
        if (cached != null) return cached

        return babelioMutex.withLock {
            // Re-vérifier le cache après acquisition du lock (un autre coroutine a peut-être fetchée)
            val cachedNow = loadCache()[urlBabelio]
            if (cachedNow != null) return cachedNow

            // Respecter le délai minimum entre deux requêtes Babelio
            val now = System.currentTimeMillis()
            val elapsed = now - lastBabelioFetchMs
            if (elapsed < BABELIO_MIN_INTERVAL_MS) {
                delay(BABELIO_MIN_INTERVAL_MS - elapsed)
            }
            lastBabelioFetchMs = System.currentTimeMillis()

            fetchFromBabelio(urlBabelio, titreLivre)
        }
    }

    private suspend fun fetchFromBabelio(urlBabelio: String, titreLivre: String): String? {
        val cache = loadCache()
        return try {
            val html = withContext(Dispatchers.IO) {
                val client = okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url(urlBabelio)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .header("Accept-Language", "fr-FR,fr;q=0.9")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) { response.close(); return@withContext null }
                // Si Babelio redirige vers un autre livre, vérifier l'ID dans l'URL finale
                val finalUrl = response.request.url.toString()
                val expectedId = urlBabelio.trimEnd('/').substringAfterLast('/')
                if (!finalUrl.contains("/$expectedId")) { response.close(); return@withContext null }
                val body = response.body?.string()
                response.close()
                body
            } ?: return null
            // Vérifier que la page correspond bien au livre attendu
            val pageTitle = Regex("""<title>(.*?)</title>""").find(html)?.groupValues?.get(1) ?: ""
            val match = pageTitleMatchesLivre(pageTitle, titreLivre)
            if (!match) return null
            val imageUrl = extractCouvertureUrl(html)
            val isCacheable = imageUrl != null
                && !imageUrl.contains("couv-defaut-grande")
                && imageUrl.contains("/couv/CVT_", ignoreCase = true)
            if (isCacheable) {
                cache[urlBabelio] = imageUrl!!
                persistCache(cache)
            }
            if (imageUrl?.contains("couv-defaut-grande") == true) null else imageUrl
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Vide le cache des URLs de couvertures (fichier JSON persistant).
     * Les images Coil restent dans leur disk cache séparé.
     */
    fun clearCouverturesCache() {
        memCache = HashMap()
        try { cacheFile.delete() } catch (_: Exception) { }
    }

    /**
     * Vérifie que le titre de la page Babelio correspond au livre attendu.
     * Babelio renvoie parfois la page d'un autre livre (CDN instable) — cette vérification
     * empêche de cacher une couverture incorrecte.
     * Stratégie : au moins un mot du titre attendu (≥4 caractères) doit apparaître dans
     * le titre de la page (insensible à la casse, accents ignorés).
     * Si tous les mots du titre sont courts (<4 chars, ex: "Eva"), on descend à ≥2 chars.
     */
    internal fun pageTitleMatchesLivre(pageTitle: String, titreLivre: String): Boolean {
        fun normalize(s: String) = s.lowercase()
            .replace(Regex("[àáâãäå]"), "a").replace(Regex("[éèêë]"), "e")
            .replace(Regex("[îï]"), "i").replace(Regex("[ôö]"), "o")
            .replace(Regex("[ùûü]"), "u").replace(Regex("[ç]"), "c")
        val normalizedPage = normalize(pageTitle)
        val words = normalize(titreLivre).split(Regex("\\s+"))
        val candidates = words.filter { it.length >= 4 }.ifEmpty { words.filter { it.length >= 2 } }
        return candidates.any { mot -> normalizedPage.contains(mot) }
    }

    /**
     * Extrait l'URL de couverture depuis le HTML d'une page Babelio.
     * Priorité : og:image (canonical, toujours la bonne couverture du livre),
     * puis fallbacks sur les patterns src= si og:image absent.
     */
    internal fun extractCouvertureUrl(html: String): String? {
        // og:image — source canonique Babelio, toujours la bonne couverture
        val ogPattern = Regex("""property=['"]og:image['"][^>]*content=['"]([^'"]+)['"]""")
        val ogMatch = ogPattern.find(html)
        if (ogMatch == null) {
            // Forme alternative : content avant property
            val ogAlt = Regex("""content=['"]([^'"]+)['"][^>]*property=['"]og:image['"]""")
            val ogAltMatch = ogAlt.find(html)
            if (ogAltMatch != null) return ogAltMatch.groupValues[1]
        } else {
            return ogMatch.groupValues[1]
        }

        // Fallback : src relatif Babelio /couv/CVT_
        val babelioPattern = Regex("""src=['"](/couv/CVT_[^'"]+)['"]""")
        val babelioMatch = babelioPattern.find(html)
        if (babelioMatch != null) {
            return "https://www.babelio.com${babelioMatch.groupValues[1]}"
        }

        // Fallback : Amazon
        val amazonPattern = Regex("""src=['"]( ?https?://m\.media-amazon\.com/images/[^'"]+)['"]""")
        val amazonMatch = amazonPattern.find(html)
        if (amazonMatch != null) {
            return amazonMatch.groupValues[1].trim()
        }

        // Fallback : Babelio absolu
        val babelioAbsPattern = Regex("""src=['"]( ?https?://www\.babelio\.com/couv/[^'"]+)['"]""")
        val babelioAbsMatch = babelioAbsPattern.find(html)
        if (babelioAbsMatch != null) {
            return babelioAbsMatch.groupValues[1].trim()
        }

        return null
    }
}
