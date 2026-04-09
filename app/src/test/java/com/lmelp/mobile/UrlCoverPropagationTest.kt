package com.lmelp.mobile

import com.lmelp.mobile.data.db.LivreAvecCalibreRow
import com.lmelp.mobile.data.db.LivresDao
import com.lmelp.mobile.data.repository.EmissionsRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import com.lmelp.mobile.data.db.EmissionsDao
import com.lmelp.mobile.data.db.EpisodesDao
import com.lmelp.mobile.data.db.AvisCritiquesDao
import com.lmelp.mobile.data.model.EmissionEntity
import com.lmelp.mobile.data.model.EpisodeEntity

/**
 * Tests RED → vérifie que urlCover est propagé depuis LivreAvecCalibreRow vers LivreUi
 * dans EmissionsRepository.
 */
class UrlCoverPropagationTest {

    private fun makeLivreAvecCalibreRow(id: String, urlCover: String? = null) = LivreAvecCalibreRow(
        id = id,
        titre = "Titre $id",
        auteurId = null,
        auteurNom = "Auteur",
        editeur = null,
        urlBabelio = null,
        urlCover = urlCover,
        createdAt = null,
        updatedAt = null
    )

    private fun makeEmissionEntity(id: String) = EmissionEntity(
        id = id,
        episodeId = "ep1",
        date = "2024-01-01",
        duree = 60,
        animateurId = null,
        nbAvis = 3,
        hasSummary = 0,
        createdAt = null,
        updatedAt = null
    )

    private fun makeEpisodeEntity() = EpisodeEntity(
        id = "ep1",
        titre = "Episode Test",
        date = null,
        description = null,
        url = null,
        duree = null
    )

    @Test
    fun `urlCover est propagee depuis LivreEntity vers LivreUi dans EmissionDetail`() = runTest {
        val emissionsDao = mock<EmissionsDao>()
        val episodesDao = mock<EpisodesDao>()
        val livresDao = mock<LivresDao>()
        val avisCritiquesDao = mock<AvisCritiquesDao>()

        whenever(emissionsDao.getEmissionById(any())).thenReturn(makeEmissionEntity("e1"))
        whenever(episodesDao.getEpisodeById(any())).thenReturn(makeEpisodeEntity())
        whenever(livresDao.getNotesParLivreForEmission(any())).thenReturn(emptyList())
        whenever(livresDao.getLivresAvecCalibreByEmission(any())).thenReturn(
            listOf(makeLivreAvecCalibreRow("l1", urlCover = "https://example.com/cover.jpg"))
        )
        whenever(avisCritiquesDao.getByEmissionId(any())).thenReturn(null)

        val repo = EmissionsRepository(emissionsDao, episodesDao, livresDao, avisCritiquesDao)
        val result = repo.getEmissionDetail("e1")

        assertEquals("https://example.com/cover.jpg", result!!.livres[0].urlCover)
    }

    @Test
    fun `urlCover est null dans LivreUi si LivreEntity na pas de cover`() = runTest {
        val emissionsDao = mock<EmissionsDao>()
        val episodesDao = mock<EpisodesDao>()
        val livresDao = mock<LivresDao>()
        val avisCritiquesDao = mock<AvisCritiquesDao>()

        whenever(emissionsDao.getEmissionById(any())).thenReturn(makeEmissionEntity("e1"))
        whenever(episodesDao.getEpisodeById(any())).thenReturn(makeEpisodeEntity())
        whenever(livresDao.getNotesParLivreForEmission(any())).thenReturn(emptyList())
        whenever(livresDao.getLivresAvecCalibreByEmission(any())).thenReturn(
            listOf(makeLivreAvecCalibreRow("l1", urlCover = null))
        )
        whenever(avisCritiquesDao.getByEmissionId(any())).thenReturn(null)

        val repo = EmissionsRepository(emissionsDao, episodesDao, livresDao, avisCritiquesDao)
        val result = repo.getEmissionDetail("e1")

        assertNull(result!!.livres[0].urlCover)
    }
}
