package com.lmelp.mobile

import com.lmelp.mobile.data.db.CalibreHorsMasqueDao
import com.lmelp.mobile.data.model.CalibreHorsMasqueEntity
import com.lmelp.mobile.data.model.MonPalmaresItemUi
import com.lmelp.mobile.data.repository.PalmaresRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests TDD pour les livres hors Masque (issue #85).
 * Ces livres sont lus dans Calibre mais non discutés au Masque et la Plume.
 */
class CalibreHorsMasqueRepositoryTest {

    private fun makeHorsMasque(
        id: String,
        titre: String = "Titre $id",
        auteurNom: String? = "Auteur",
        calibreRating: Double? = null,
        dateLecture: String? = null
    ) = CalibreHorsMasqueEntity(
        id = id,
        titre = titre,
        auteurNom = auteurNom,
        calibreRating = calibreRating,
        dateLecture = dateLecture
    )

    // --- getMonPalmaresHorsMasque (tri par note) ---

    @Test
    fun `getMonPalmaresHorsMasque returns items mapped to MonPalmaresItemUi with livreId null`() = runTest {
        val horsMasqueDao = mock<CalibreHorsMasqueDao>()
        whenever(horsMasqueDao.getAll()).thenReturn(
            listOf(makeHorsMasque("h1", calibreRating = 8.0))
        )
        val repo = PalmaresRepository(mock(), horsMasqueDao)
        val result = repo.getMonPalmaresHorsMasque()

        assertEquals(1, result.size)
        assertNull("livreId doit être null pour hors Masque", result[0].livreId)
        assertEquals("h1", result[0].id)
        assertEquals(8.0, result[0].calibreRating!!, 0.001)
    }

    @Test
    fun `getMonPalmaresHorsMasque items without note have livreId null and no rating`() = runTest {
        val horsMasqueDao = mock<CalibreHorsMasqueDao>()
        whenever(horsMasqueDao.getAll()).thenReturn(
            listOf(makeHorsMasque("h1", calibreRating = null))
        )
        val repo = PalmaresRepository(mock(), horsMasqueDao)
        val result = repo.getMonPalmaresHorsMasque()

        assertEquals(1, result.size)
        assertNull(result[0].livreId)
        assertNull(result[0].calibreRating)
    }

    // --- getMonPalmaresHorsMasqueParDate (tri par date) ---

    @Test
    fun `getMonPalmaresHorsMasqueParDate returns items sorted by date desc`() = runTest {
        val horsMasqueDao = mock<CalibreHorsMasqueDao>()
        whenever(horsMasqueDao.getAllParDate()).thenReturn(
            listOf(
                makeHorsMasque("h2", dateLecture = "2024-06-01"),
                makeHorsMasque("h1", dateLecture = "2024-01-01")
            )
        )
        val repo = PalmaresRepository(mock(), horsMasqueDao)
        val result = repo.getMonPalmaresHorsMasqueParDate()

        assertEquals(2, result.size)
        assertEquals("h2", result[0].id)
        assertEquals("h1", result[1].id)
    }

    @Test
    fun `getMonPalmaresHorsMasqueParDate items without date have dateLecture null`() = runTest {
        val horsMasqueDao = mock<CalibreHorsMasqueDao>()
        whenever(horsMasqueDao.getAllParDate()).thenReturn(
            listOf(makeHorsMasque("h1", dateLecture = null))
        )
        val repo = PalmaresRepository(mock(), horsMasqueDao)
        val result = repo.getMonPalmaresHorsMasqueParDate()

        assertNull(result[0].dateLecture)
    }

    // --- getHorsMasqueByAuteurNom ---

    @Test
    fun `getHorsMasqueByAuteurNom returns only items matching auteur`() = runTest {
        val horsMasqueDao = mock<CalibreHorsMasqueDao>()
        whenever(horsMasqueDao.getByAuteurNom(any())).thenReturn(
            listOf(makeHorsMasque("h1", auteurNom = "Modiano"))
        )
        val repo = PalmaresRepository(mock(), horsMasqueDao)
        val result = repo.getHorsMasqueByAuteurNom("Modiano")

        assertEquals(1, result.size)
        assertEquals("Modiano", result[0].auteurNom)
    }

    @Test
    fun `getHorsMasqueByAuteurNom returns empty list when no match`() = runTest {
        val horsMasqueDao = mock<CalibreHorsMasqueDao>()
        whenever(horsMasqueDao.getByAuteurNom(any())).thenReturn(emptyList())
        val repo = PalmaresRepository(mock(), horsMasqueDao)
        val result = repo.getHorsMasqueByAuteurNom("Inconnu")

        assertTrue(result.isEmpty())
    }
}
