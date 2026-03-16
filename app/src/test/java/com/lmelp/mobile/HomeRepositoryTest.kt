package com.lmelp.mobile

import android.content.Context
import android.content.SharedPreferences
import com.lmelp.mobile.data.db.EmissionsDao
import com.lmelp.mobile.data.db.OnKindleDao
import com.lmelp.mobile.data.db.PalmaresDao
import com.lmelp.mobile.data.db.PalmaresAvecUrlRow
import com.lmelp.mobile.data.db.RecommendationsDao
import com.lmelp.mobile.data.db.RecommendationAvecUrlRow
import com.lmelp.mobile.data.db.TopLivreEmissionRow
import com.lmelp.mobile.data.model.OnKindleEntity
import com.lmelp.mobile.data.repository.HomeRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests unitaires pour HomeRepository (JVM, sans dépendance Android).
 * Vérifie que les slides utilisent url_cover depuis la DB directement.
 */
class HomeRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private fun buildRepository(
        emissionsDao: EmissionsDao = mock(),
        palmaresDao: PalmaresDao = mock(),
        recommendationsDao: RecommendationsDao = mock(),
        onKindleDao: OnKindleDao = mock(),
    ): HomeRepository {
        return HomeRepository(
            metadataDao = mock(),
            emissionsDao = emissionsDao,
            palmaresDao = palmaresDao,
            livresDao = mock(),
            recommendationsDao = recommendationsDao,
            onKindleDao = onKindleDao,
        )
    }

    // ── url_cover depuis la DB ────────────────────────────────────────────────

    @Test
    fun `getEmissionsSlides utilise urlCover de la DB directement`() = runBlocking {
        val emissionsDao = mock<EmissionsDao>()
        val coverUrl = "https://example.com/cover.jpg"
        val row = TopLivreEmissionRow(
            emissionId = "e1",
            emissionDate = "2024-01-01T00:00:00Z",
            livreId = "l1",
            livreTitre = "Mon Livre",
            urlBabelio = "https://www.babelio.com/livres/Test/123",
            urlCover = coverUrl,
            noteMoyenne = 8.0
        )
        whenever(emissionsDao.getTopLivreParEmission(any())).thenReturn(listOf(row))
        val repo = buildRepository(emissionsDao = emissionsDao)
        val slides = repo.getEmissionsSlides()
        assertEquals(coverUrl, slides.first().urlCouverture)
    }

    @Test
    fun `getEmissionsSlides urlCouverture est null si urlCover absent`() = runBlocking {
        val emissionsDao = mock<EmissionsDao>()
        val row = TopLivreEmissionRow(
            emissionId = "e1",
            emissionDate = "2024-01-01T00:00:00Z",
            livreId = "l1",
            livreTitre = "Mon Livre",
            urlBabelio = "https://www.babelio.com/livres/Test/123",
            urlCover = null,
            noteMoyenne = 8.0
        )
        whenever(emissionsDao.getTopLivreParEmission(any())).thenReturn(listOf(row))
        val repo = buildRepository(emissionsDao = emissionsDao)
        val slides = repo.getEmissionsSlides()
        assertNull(slides.first().urlCouverture)
    }

    @Test
    fun `getPalmaresSlides utilise urlCover de la DB directement`() = runBlocking {
        val palmaresDao = mock<PalmaresDao>()
        val coverUrl = "https://example.com/palmares_cover.jpg"
        val row = PalmaresAvecUrlRow(
            rank = 1,
            livreId = "l1",
            titre = "Livre Palmares",
            auteurNom = "Auteur",
            noteMoyenne = 9.0,
            urlBabelio = null,
            urlCover = coverUrl
        )
        whenever(palmaresDao.getTopPalmaresAvecUrl(any())).thenReturn(listOf(row))
        val repo = buildRepository(palmaresDao = palmaresDao)
        val slides = repo.getPalmaresSlides()
        assertEquals(coverUrl, slides.first().urlCouverture)
    }

    @Test
    fun `getConseilsSlides utilise urlCover de la DB directement`() = runBlocking {
        val recommendationsDao = mock<RecommendationsDao>()
        val coverUrl = "https://example.com/conseil_cover.jpg"
        val row = RecommendationAvecUrlRow(
            rank = 1,
            livreId = "l1",
            titre = "Livre Conseil",
            auteurNom = "Auteur",
            scoreHybride = 7.5,
            urlBabelio = null,
            urlCover = coverUrl
        )
        whenever(recommendationsDao.getTopRecommandationsNonLuesAvecUrl(any())).thenReturn(listOf(row))
        val repo = buildRepository(recommendationsDao = recommendationsDao)
        val slides = repo.getConseilsSlides()
        assertEquals(coverUrl, slides.first().urlCouverture)
    }

    @Test
    fun `getOnKindleSlides utilise urlCover de la DB directement`() = runBlocking {
        val onKindleDao = mock<OnKindleDao>()
        val coverUrl = "https://example.com/onkindle_cover.jpg"
        val entity = OnKindleEntity(
            livreId = "l1",
            titre = "Livre OnKindle",
            auteurNom = "Auteur",
            urlBabelio = null,
            urlCover = coverUrl
        )
        whenever(onKindleDao.getTopOnKindleAvecUrl(any())).thenReturn(listOf(entity))
        val repo = buildRepository(onKindleDao = onKindleDao)
        val slides = repo.getOnKindleSlides()
        assertEquals(coverUrl, slides.first().urlCouverture)
    }
}
