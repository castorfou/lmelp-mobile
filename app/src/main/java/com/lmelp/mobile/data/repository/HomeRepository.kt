package com.lmelp.mobile.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.lmelp.mobile.data.db.DerniereEmissionRow
import com.lmelp.mobile.data.db.EmissionsDao
import com.lmelp.mobile.data.db.LivresDao
import com.lmelp.mobile.data.db.MetadataDao
import com.lmelp.mobile.data.db.PalmaresDao
import com.lmelp.mobile.data.db.RecommendationsDao
import com.lmelp.mobile.data.model.SlideItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val PREFS_NAME = "home_cache"
private const val KEY_COUVERTURE_PREFIX = "couverture_"

class HomeRepository(
    private val metadataDao: MetadataDao,
    private val emissionsDao: EmissionsDao,
    private val palmaresDao: PalmaresDao,
    private val livresDao: LivresDao,
    private val recommendationsDao: RecommendationsDao,
    private val context: Context
) {

    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
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

    private fun getCachedCouverture(urlBabelio: String?): String? {
        if (urlBabelio == null) return null
        val cacheKey = KEY_COUVERTURE_PREFIX + urlBabelio.hashCode()
        return prefs.getString(cacheKey, null)?.ifEmpty { null }
    }

    /**
     * Récupère l'URL de couverture pour un livre depuis sa page Babelio.
     * Résultat mis en cache dans SharedPreferences pour éviter les requêtes répétées.
     */
    suspend fun fetchCouvertureBabelio(urlBabelio: String): String? {
        val cacheKey = KEY_COUVERTURE_PREFIX + urlBabelio.hashCode()
        val cached = prefs.getString(cacheKey, null)
        if (cached != null) return cached.ifEmpty { null }

        return try {
            val html = withContext(Dispatchers.IO) {
                val conn = java.net.URL(urlBabelio).openConnection()
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android 14; Mobile)")
                conn.inputStream.bufferedReader().readText()
            }
            val imageUrl = extractCouvertureUrl(html)
            prefs.edit().putString(cacheKey, imageUrl ?: "").apply()
            imageUrl
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Extrait l'URL de couverture depuis le HTML d'une page Babelio.
     */
    internal fun extractCouvertureUrl(html: String): String? {
        val babelioPattern = Regex("""src=['"](/couv/CVT_[^'"]+)['"]""")
        val babelioMatch = babelioPattern.find(html)
        if (babelioMatch != null) {
            return "https://www.babelio.com${babelioMatch.groupValues[1]}"
        }

        val amazonPattern = Regex("""src=['"]( ?https?://m\.media-amazon\.com/images/[^'"]+)['"]""")
        val amazonMatch = amazonPattern.find(html)
        if (amazonMatch != null) {
            return amazonMatch.groupValues[1].trim()
        }

        val babelioAbsPattern = Regex("""src=['"]( ?https?://www\.babelio\.com/couv/[^'"]+)['"]""")
        val babelioAbsMatch = babelioAbsPattern.find(html)
        if (babelioAbsMatch != null) {
            return babelioAbsMatch.groupValues[1].trim()
        }

        return null
    }
}
