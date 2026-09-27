package com.lmelp.mobile

import com.lmelp.mobile.data.db.CalibreHorsMasqueDao
import com.lmelp.mobile.data.model.CalibreHorsMasqueEntity
import com.lmelp.mobile.data.model.MonPalmaresItemUi
import com.lmelp.mobile.data.repository.PalmaresRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
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
        dateLecture: String? = null,
        cover: ByteArray? = null
    ) = CalibreHorsMasqueEntity(
        id = id,
        titre = titre,
        auteurNom = auteurNom,
        calibreRating = calibreRating,
        dateLecture = dateLecture,
        cover = cover
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

    // --- Vignette de couverture Calibre (issue #145) ---

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2)

    @Test
    fun `getMonPalmaresHorsMasque propage la vignette Calibre`() = runTest {
        val horsMasqueDao = mock<CalibreHorsMasqueDao>()
        whenever(horsMasqueDao.getAll()).thenReturn(
            listOf(makeHorsMasque("h1", cover = jpeg), makeHorsMasque("h2"))
        )
        val result = PalmaresRepository(mock(), horsMasqueDao).getMonPalmaresHorsMasque()

        assertArrayEquals(jpeg, result[0].coverData)
        assertNull(result[1].coverData)
        assertNull("pas d'URL pour un livre hors Masque", result[0].urlCover)
    }

    @Test
    fun `getMonPalmaresHorsMasqueParDate propage la vignette Calibre`() = runTest {
        val horsMasqueDao = mock<CalibreHorsMasqueDao>()
        whenever(horsMasqueDao.getAllParDate()).thenReturn(listOf(makeHorsMasque("h1", cover = jpeg)))
        val result = PalmaresRepository(mock(), horsMasqueDao).getMonPalmaresHorsMasqueParDate()

        assertArrayEquals(jpeg, result[0].coverData)
    }

    @Test
    fun `getHorsMasqueByAuteurNom propage la vignette Calibre`() = runTest {
        val horsMasqueDao = mock<CalibreHorsMasqueDao>()
        whenever(horsMasqueDao.getByAuteurNom(any())).thenReturn(listOf(makeHorsMasque("h1", cover = jpeg)))
        val result = PalmaresRepository(mock(), horsMasqueDao).getHorsMasqueByAuteurNom("Auteur")

        assertArrayEquals(jpeg, result[0].cover)
    }
}
