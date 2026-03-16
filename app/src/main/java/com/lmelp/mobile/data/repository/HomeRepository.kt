package com.lmelp.mobile.data.repository

import com.lmelp.mobile.data.db.DerniereEmissionRow
import com.lmelp.mobile.data.db.EmissionsDao
import com.lmelp.mobile.data.db.LivresDao
import com.lmelp.mobile.data.db.MetadataDao
import com.lmelp.mobile.data.db.OnKindleDao
import com.lmelp.mobile.data.db.PalmaresDao
import com.lmelp.mobile.data.db.RecommendationsDao
import com.lmelp.mobile.data.model.SlideItem

class HomeRepository(
    private val metadataDao: MetadataDao,
    private val emissionsDao: EmissionsDao,
    private val palmaresDao: PalmaresDao,
    private val livresDao: LivresDao,
    private val recommendationsDao: RecommendationsDao,
    private val onKindleDao: OnKindleDao,
) {
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
                    urlCouverture = row.urlCover
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
                urlCouverture = row.urlCover
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
                urlCouverture = row.urlCover
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
                urlCouverture = row.urlCover
            )
        }
    }

    private fun formatDate(isoDate: String): String {
        return try {
            val input = java.time.OffsetDateTime.parse(isoDate)
            input.format(java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy", java.util.Locale.FRENCH))
        } catch (_: Exception) { isoDate }
    }
}
