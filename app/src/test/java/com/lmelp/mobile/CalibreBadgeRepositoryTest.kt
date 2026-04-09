package com.lmelp.mobile

import com.lmelp.mobile.data.db.AvisAvecEmissionRow
import com.lmelp.mobile.data.db.AuteursDao
import com.lmelp.mobile.data.db.LivreAvecCalibreRow
import com.lmelp.mobile.data.db.LivreParAuteurRow
import com.lmelp.mobile.data.db.LivresDao
import com.lmelp.mobile.data.model.AvisEntity
import com.lmelp.mobile.data.model.AuteurEntity
import com.lmelp.mobile.data.repository.AuteursRepository
import com.lmelp.mobile.data.repository.LivresRepository
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
 * Tests TDD pour la propagation des champs Calibre (calibre_lu, calibre_in_library, calibre_rating)
 * dans les repositories liés aux livres (issue #84).
 */
class CalibreBadgeRepositoryTest {

    // --- Helpers ---

    private fun makeAvisRow(emissionId: String, livreId: String) = AvisAvecEmissionRow(
        avis = AvisEntity(
            id = "a1", emissionId = emissionId, livreId = livreId,
            critiqueId = "c1", note = 7.0, commentaire = null,
            livreTitre = null, auteurNom = null, critiqueNom = "Critique",
            matchPhase = null, section = null, createdAt = null
        ),
        emissionTitre = "Emission test",
        emissionDate = "2024-01-01"
    )

    private fun makeLivreAvecCalibre(
        id: String,
        calibreInLibrary: Int = 0,
        calibreLu: Int = 0,
        calibreRating: Double? = null
    ) = LivreAvecCalibreRow(
        id = id,
        titre = "Titre $id",
        auteurId = null,
        auteurNom = "Auteur",
        editeur = null,
        urlBabelio = null,
        urlCover = null,
        createdAt = null,
        updatedAt = null,
        calibreInLibrary = calibreInLibrary,
        calibreLu = calibreLu,
        calibreRating = calibreRating
    )

    private fun makeAuteurEntity(id: String) = AuteurEntity(id = id, nom = "Auteur", urlBabelio = null)

    private fun makeLivreParAuteurRow(
        livreId: String,
        calibreInLibrary: Int = 0,
        calibreLu: Int = 0,
        calibreRating: Double? = null
    ) = LivreParAuteurRow(
        livreId = livreId,
        titre = "Titre $livreId",
        noteMoyenne = null,
        derniereEmissionDate = "2024-01-01",
        calibreInLibrary = calibreInLibrary,
        calibreLu = calibreLu,
        calibreRating = calibreRating
    )

    // --- LivresRepository ---

    @Test
    fun `LivreDetailUi calibre fields true when livre lu dans Calibre`() = runTest {
        val dao = mock<LivresDao>()
        whenever(dao.getLivreAvecCalibreById(any())).thenReturn(
            makeLivreAvecCalibre("l1", calibreInLibrary = 1, calibreLu = 1, calibreRating = 8.0)
        )
        whenever(dao.getAvisAvecEmissionByLivre(any())).thenReturn(listOf(makeAvisRow("e1", "l1")))

        val repo = LivresRepository(dao)
        val result = repo.getLivreDetail("l1")!!

        assertTrue(result.calibreInLibrary)
        assertTrue(result.calibreLu)
        assertEquals(8.0, result.calibreRating!!, 0.001)
    }

    @Test
    fun `LivreDetailUi calibre fields false when livre not in Calibre`() = runTest {
        val dao = mock<LivresDao>()
        whenever(dao.getLivreAvecCalibreById(any())).thenReturn(
            makeLivreAvecCalibre("l1", calibreInLibrary = 0, calibreLu = 0, calibreRating = null)
        )
        whenever(dao.getAvisAvecEmissionByLivre(any())).thenReturn(emptyList())

        val repo = LivresRepository(dao)
        val result = repo.getLivreDetail("l1")!!

        assertFalse(result.calibreInLibrary)
        assertFalse(result.calibreLu)
        assertNull(result.calibreRating)
    }

    @Test
    fun `LivreDetailUi calibreInLibrary true but calibreLu false when in library not read`() = runTest {
        val dao = mock<LivresDao>()
        whenever(dao.getLivreAvecCalibreById(any())).thenReturn(
            makeLivreAvecCalibre("l1", calibreInLibrary = 1, calibreLu = 0, calibreRating = null)
        )
        whenever(dao.getAvisAvecEmissionByLivre(any())).thenReturn(emptyList())

        val repo = LivresRepository(dao)
        val result = repo.getLivreDetail("l1")!!

        assertTrue(result.calibreInLibrary)
        assertFalse(result.calibreLu)
        assertNull(result.calibreRating)
    }

    @Test
    fun `LivreDetailUi returns null when livre not found`() = runTest {
        val dao = mock<LivresDao>()
        whenever(dao.getLivreAvecCalibreById(any())).thenReturn(null)

        val repo = LivresRepository(dao)
        val result = repo.getLivreDetail("inexistant")

        assertNull(result)
    }

    // --- AuteursRepository ---

    @Test
    fun `LivreParAuteurUi calibreLu true when livre lu dans Calibre`() = runTest {
        val dao = mock<AuteursDao>()
        whenever(dao.getAuteurById(any())).thenReturn(makeAuteurEntity("a1"))
        whenever(dao.getLivresParAuteur(any())).thenReturn(
            listOf(makeLivreParAuteurRow("l1", calibreInLibrary = 1, calibreLu = 1, calibreRating = 7.5))
        )

        val repo = AuteursRepository(dao)
        val result = repo.getAuteurDetail("a1")!!

        val livre = result.livres[0]
        assertTrue(livre.calibreInLibrary)
        assertTrue(livre.calibreLu)
        assertEquals(7.5, livre.calibreRating!!, 0.001)
    }

    @Test
    fun `LivreParAuteurUi calibre fields false when not in Calibre`() = runTest {
        val dao = mock<AuteursDao>()
        whenever(dao.getAuteurById(any())).thenReturn(makeAuteurEntity("a1"))
        whenever(dao.getLivresParAuteur(any())).thenReturn(
            listOf(makeLivreParAuteurRow("l1", calibreInLibrary = 0, calibreLu = 0))
        )

        val repo = AuteursRepository(dao)
        val result = repo.getAuteurDetail("a1")!!

        val livre = result.livres[0]
        assertFalse(livre.calibreInLibrary)
        assertFalse(livre.calibreLu)
        assertNull(livre.calibreRating)
    }
}
