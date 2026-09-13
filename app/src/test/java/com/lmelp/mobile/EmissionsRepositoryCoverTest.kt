package com.lmelp.mobile

import com.lmelp.mobile.data.db.AvisCritiquesDao
import com.lmelp.mobile.data.db.EmissionsDao
import com.lmelp.mobile.data.db.EpisodesDao
import com.lmelp.mobile.data.db.LivresDao
import com.lmelp.mobile.data.db.TopLivreEmissionRow
import com.lmelp.mobile.data.model.EmissionEntity
import com.lmelp.mobile.data.model.EpisodeEntity
import com.lmelp.mobile.data.repository.EmissionsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests RED → vérifie que getAllEmissions() enrichit EmissionUi avec urlCover/noteMoyenne
 * du livre le mieux noté de chaque émission (issue #138).
 */
class EmissionsRepositoryCoverTest {

    private fun makeEmissionEntity(id: String, date: String = "2024-01-01") = EmissionEntity(
        id = id, episodeId = "ep1", date = date, duree = 60,
        animateurId = null, nbAvis = 3, hasSummary = 0, createdAt = null, updatedAt = null
    )

    private fun makeEpisodeEntity() = EpisodeEntity(
        id = "ep1", titre = "Episode Test", date = null, description = null, url = null, duree = null
    )

    @Test
    fun `getAllEmissions propage urlCover et noteMoyenne du top livre`() = runTest {
        val emissionsDao = mock<EmissionsDao>()
        val episodesDao = mock<EpisodesDao>()
        val livresDao = mock<LivresDao>()
        val avisCritiquesDao = mock<AvisCritiquesDao>()
        whenever(emissionsDao.getAllEmissions()).thenReturn(listOf(makeEmissionEntity("e1")))
        whenever(episodesDao.getEpisodeById(any())).thenReturn(makeEpisodeEntity())
        whenever(emissionsDao.getTopLivreParEmission(any())).thenReturn(
            listOf(
                TopLivreEmissionRow(
                    emissionId = "e1", emissionDate = "2024-01-01",
                    livreId = "l1", livreTitre = "Livre Top",
                    urlBabelio = null, urlCover = "https://example.com/cover.jpg",
                    noteMoyenne = 8.5
                )
            )
        )

        val repo = EmissionsRepository(emissionsDao, episodesDao, livresDao, avisCritiquesDao)
        val result = repo.getAllEmissions()

        assertEquals("https://example.com/cover.jpg", result.first().urlCover)
        assertEquals(8.5, result.first().noteMoyenne)
    }

    @Test
    fun `getAllEmissions urlCover est null si aucun livre note pour l emission`() = runTest {
        val emissionsDao = mock<EmissionsDao>()
        val episodesDao = mock<EpisodesDao>()
        val livresDao = mock<LivresDao>()
        val avisCritiquesDao = mock<AvisCritiquesDao>()
        whenever(emissionsDao.getAllEmissions()).thenReturn(listOf(makeEmissionEntity("e1")))
        whenever(episodesDao.getEpisodeById(any())).thenReturn(makeEpisodeEntity())
        whenever(emissionsDao.getTopLivreParEmission(any())).thenReturn(emptyList())

        val repo = EmissionsRepository(emissionsDao, episodesDao, livresDao, avisCritiquesDao)
        val result = repo.getAllEmissions()

        assertNull(result.first().urlCover)
        assertNull(result.first().noteMoyenne)
    }

    @Test
    fun `getAllEmissions associe le bon top livre a chaque emission par emissionId`() = runTest {
        val emissionsDao = mock<EmissionsDao>()
        val episodesDao = mock<EpisodesDao>()
        val livresDao = mock<LivresDao>()
        val avisCritiquesDao = mock<AvisCritiquesDao>()
        whenever(emissionsDao.getAllEmissions()).thenReturn(
            listOf(makeEmissionEntity("e1", "2024-02-01"), makeEmissionEntity("e2", "2024-01-01"))
        )
        whenever(episodesDao.getEpisodeById(any())).thenReturn(makeEpisodeEntity())
        whenever(emissionsDao.getTopLivreParEmission(any())).thenReturn(
            listOf(
                TopLivreEmissionRow("e1", "2024-02-01", "l1", "Livre 1", null, "https://example.com/1.jpg", 9.0),
                TopLivreEmissionRow("e2", "2024-01-01", "l2", "Livre 2", null, "https://example.com/2.jpg", 6.0)
            )
        )

        val repo = EmissionsRepository(emissionsDao, episodesDao, livresDao, avisCritiquesDao)
        val result = repo.getAllEmissions().associateBy { it.id }

        assertEquals("https://example.com/1.jpg", result["e1"]!!.urlCover)
        assertEquals(9.0, result["e1"]!!.noteMoyenne)
        assertEquals("https://example.com/2.jpg", result["e2"]!!.urlCover)
        assertEquals(6.0, result["e2"]!!.noteMoyenne)
    }
}
