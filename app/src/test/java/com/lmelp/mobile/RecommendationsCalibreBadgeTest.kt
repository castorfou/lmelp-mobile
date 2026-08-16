package com.lmelp.mobile

import com.lmelp.mobile.data.db.RecommandationNonLueAvecUrlRow
import com.lmelp.mobile.data.db.RecommendationsDao
import com.lmelp.mobile.data.repository.RecommendationsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests TDD pour la propagation des champs Calibre (calibre_in_library, calibre_lu, calibre_rating)
 * dans RecommendationsRepository, pour l'affichage du badge Calibre sur la page Conseils (issue #113).
 */
class RecommendationsCalibreBadgeTest {

    private fun makeRow(
        livreId: String,
        calibreInLibrary: Int = 0,
        calibreLu: Int = 0
    ) = RecommandationNonLueAvecUrlRow(
        rank = 1,
        livreId = livreId,
        titre = "Titre $livreId",
        auteurNom = "Auteur",
        scoreHybride = 0.9,
        masqueMean = null,
        calibreInLibrary = calibreInLibrary,
        calibreLu = calibreLu,
        urlCover = null
    )

    @Test
    fun `RecommendationUi calibreInLibrary true et calibreLu false quand livre present non lu`() = runTest {
        val dao = mock<RecommendationsDao>()
        whenever(dao.getRecommandationsNonLuesAvecUrl()).thenReturn(
            listOf(makeRow("l1", calibreInLibrary = 1, calibreLu = 0))
        )

        val repo = RecommendationsRepository(dao)
        val result = repo.getAllRecommendations()

        assertTrue(result[0].calibreInLibrary)
        assertFalse(result[0].calibreLu)
    }

    @Test
    fun `RecommendationUi calibre fields false quand livre absent de Calibre`() = runTest {
        val dao = mock<RecommendationsDao>()
        whenever(dao.getRecommandationsNonLuesAvecUrl()).thenReturn(
            listOf(makeRow("l1", calibreInLibrary = 0, calibreLu = 0))
        )

        val repo = RecommendationsRepository(dao)
        val result = repo.getAllRecommendations()

        assertFalse(result[0].calibreInLibrary)
        assertFalse(result[0].calibreLu)
    }

    @Test
    fun `RecommendationUi calibreRating est toujours null`() = runTest {
        val dao = mock<RecommendationsDao>()
        whenever(dao.getRecommandationsNonLuesAvecUrl()).thenReturn(
            listOf(makeRow("l1", calibreInLibrary = 1, calibreLu = 0))
        )

        val repo = RecommendationsRepository(dao)
        val result = repo.getAllRecommendations()

        assertNull(result[0].calibreRating)
    }
}
