package com.lmelp.mobile

import com.lmelp.mobile.data.db.CalibreHorsMasqueDao
import com.lmelp.mobile.data.db.MonPalmaresRow
import com.lmelp.mobile.data.db.PalmaresDao
import com.lmelp.mobile.data.model.CalibreHorsMasqueEntity
import com.lmelp.mobile.data.repository.PalmaresRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests TDD pour l'affichage du nb de jours de lecture dans la fiche livre (issue #93 extension).
 *
 * getJoursLecturePourLivre(livreId) retourne le nb de jours calculé pour ce livre
 * dans la chronologie de tous les livres lus (Masque + hors Masque).
 */
class JoursLectureLivreDetailTest {

    private fun makeRow(livreId: String, dateLecture: String?) = MonPalmaresRow(
        livreId = livreId,
        titre = "Titre $livreId",
        auteurNom = null,
        noteMoyenne = 7.0,
        nbAvis = 2,
        nbCritiques = 2,
        calibreRating = null,
        urlCover = null,
        dateLecture = dateLecture
    )

    private fun makeHm(id: String, dateLecture: String?) = CalibreHorsMasqueEntity(
        id = id,
        titre = "HM $id",
        auteurNom = null,
        calibreRating = null,
        dateLecture = dateLecture
    )

    // --- Test 1 : retourne les jours corrects pour un livreId Masque ---

    @Test
    fun `getJoursLecturePourLivre_retourne_jours_livre_masque`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        whenever(palmaresDao.getMonPalmares()).thenReturn(
            listOf(
                makeRow("A", "2024-01-01"),
                makeRow("B", "2024-01-15")
            )
        )
        val repo = PalmaresRepository(palmaresDao)

        val jours = repo.getJoursLecturePourLivre("B")

        assertEquals(14, jours)
    }

    // --- Test 2 : le 1er livre lu retourne null (pas de référence) ---

    @Test
    fun `getJoursLecturePourLivre_premier_livre_retourne_null`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        whenever(palmaresDao.getMonPalmares()).thenReturn(
            listOf(
                makeRow("A", "2024-01-01"),
                makeRow("B", "2024-02-01")
            )
        )
        val repo = PalmaresRepository(palmaresDao)

        val jours = repo.getJoursLecturePourLivre("A")

        assertNull(jours)
    }

    // --- Test 3 : livre sans date_lecture retourne null ---

    @Test
    fun `getJoursLecturePourLivre_livre_sans_date_retourne_null`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        whenever(palmaresDao.getMonPalmares()).thenReturn(
            listOf(
                makeRow("A", "2024-01-01"),
                makeRow("B", null)
            )
        )
        val repo = PalmaresRepository(palmaresDao)

        val jours = repo.getJoursLecturePourLivre("B")

        assertNull(jours)
    }

    // --- Test 4 : livre non trouvé retourne null ---

    @Test
    fun `getJoursLecturePourLivre_livre_inconnu_retourne_null`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        whenever(palmaresDao.getMonPalmares()).thenReturn(
            listOf(makeRow("A", "2024-01-01"))
        )
        val repo = PalmaresRepository(palmaresDao)

        val jours = repo.getJoursLecturePourLivre("INCONNU")

        assertNull(jours)
    }

    // --- Test 5 : livre hors Masque participe au calcul d'un livre Masque ---

    @Test
    fun `getJoursLecturePourLivre_hors_masque_participe_au_calcul`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        val horsMasqueDao = mock<CalibreHorsMasqueDao>()
        // Livre hors Masque lu le 01/01
        whenever(palmaresDao.getMonPalmares()).thenReturn(
            listOf(makeRow("masque1", "2024-01-21"))
        )
        whenever(horsMasqueDao.getAll()).thenReturn(
            listOf(makeHm("hm1", "2024-01-01"))
        )
        val repo = PalmaresRepository(palmaresDao, horsMasqueDao)

        // masque1 lu 20j après hm1
        val jours = repo.getJoursLecturePourLivre("masque1")

        assertEquals(20, jours)
    }
}
