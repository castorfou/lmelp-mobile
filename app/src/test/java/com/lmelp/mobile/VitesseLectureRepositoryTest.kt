package com.lmelp.mobile

import com.lmelp.mobile.data.db.CalibreHorsMasqueDao
import com.lmelp.mobile.data.db.MonPalmaresRow
import com.lmelp.mobile.data.db.PalmaresDao
import com.lmelp.mobile.data.model.CalibreHorsMasqueEntity
import com.lmelp.mobile.data.repository.PalmaresRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests TDD pour la vitesse de lecture dans Mon Palmarès (issue #93).
 *
 * La vitesse = nb de jours entre deux lectures consécutives (ordre chronologique).
 * Le 1er livre lu n'a pas de vitesse calculable → exclu.
 * Calcul sur TOUS les livres (Masque + hors Masque), filtre affichage séparé.
 */
class VitesseLectureRepositoryTest {

    private fun makeMonPalmaresRow(
        livreId: String,
        titre: String = "Titre $livreId",
        dateLecture: String? = null,
        calibreRating: Double? = null
    ) = MonPalmaresRow(
        livreId = livreId,
        titre = titre,
        auteurNom = null,
        noteMoyenne = 7.0,
        nbAvis = 2,
        nbCritiques = 2,
        calibreRating = calibreRating,
        urlCover = null,
        dateLecture = dateLecture
    )

    private fun makeHorsMasque(
        id: String,
        titre: String = "HM $id",
        dateLecture: String? = null,
        calibreRating: Double? = null
    ) = CalibreHorsMasqueEntity(
        id = id,
        titre = titre,
        auteurNom = null,
        calibreRating = calibreRating,
        dateLecture = dateLecture
    )

    // --- Test 1 : calcul de base sur deux livres consécutifs ---

    @Test
    fun `calcul_vitesse_deux_livres_chronologiques`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        whenever(palmaresDao.getMonPalmares()).thenReturn(
            listOf(
                makeMonPalmaresRow("A", dateLecture = "2024-01-01"),
                makeMonPalmaresRow("B", dateLecture = "2024-01-15")
            )
        )
        val repo = PalmaresRepository(palmaresDao)

        val result = repo.getMonPalmaresUnifieParVitesse(ascendant = true)

        // Livre A = 1er → exclu. Livre B = 14 jours après A
        assertEquals(1, result.size)
        assertEquals("B", result[0].id)
        assertEquals(14, result[0].joursLecture)
    }

    // --- Test 2 : le 1er livre chronologique est exclu ---

    @Test
    fun `premier_livre_exclu_pas_de_vitesse`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        whenever(palmaresDao.getMonPalmares()).thenReturn(
            listOf(
                makeMonPalmaresRow("A", dateLecture = "2024-03-01"),
                makeMonPalmaresRow("B", dateLecture = "2024-04-01"),
                makeMonPalmaresRow("C", dateLecture = "2024-05-01")
            )
        )
        val repo = PalmaresRepository(palmaresDao)

        val result = repo.getMonPalmaresUnifieParVitesse(ascendant = true)

        // A est exclu. B et C ont une vitesse.
        assertEquals(2, result.size)
        val ids = result.map { it.id }
        assertTrue("A ne doit pas apparaître", "A" !in ids)
        assertTrue("B doit apparaître", "B" in ids)
        assertTrue("C doit apparaître", "C" in ids)
    }

    // --- Test 3 : un livre hors Masque participe au calcul ---

    @Test
    fun `calcul_inclut_hors_masque_pour_calcul`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        val horsMasqueDao = mock<CalibreHorsMasqueDao>()
        // Livre Masque lu le 01/01
        whenever(palmaresDao.getMonPalmares()).thenReturn(
            listOf(makeMonPalmaresRow("masque1", dateLecture = "2024-01-01"))
        )
        // Livre hors Masque lu 10 jours plus tard
        whenever(horsMasqueDao.getAll()).thenReturn(
            listOf(makeHorsMasque("hm1", dateLecture = "2024-01-11"))
        )
        val repo = PalmaresRepository(palmaresDao, horsMasqueDao)

        val result = repo.getMonPalmaresUnifieParVitesse(ascendant = true)

        // masque1 = 1er → exclu. hm1 = 10 jours après masque1
        assertEquals(1, result.size)
        assertEquals("hm1", result[0].id)
        assertEquals(10, result[0].joursLecture)
    }

    // --- Test 4 : livres sans date_lecture exclus ---

    @Test
    fun `livres_sans_date_exclus`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        whenever(palmaresDao.getMonPalmares()).thenReturn(
            listOf(
                makeMonPalmaresRow("A", dateLecture = "2024-01-01"),
                makeMonPalmaresRow("B", dateLecture = "2024-01-10"),
                makeMonPalmaresRow("sansDate", dateLecture = null)
            )
        )
        val repo = PalmaresRepository(palmaresDao)

        val result = repo.getMonPalmaresUnifieParVitesse(ascendant = true)

        // A exclu (1er), B inclus (9j), sansDate exclu (pas de date)
        assertEquals(1, result.size)
        assertEquals("B", result[0].id)
        assertEquals(9, result[0].joursLecture)
    }

    // --- Test 5 : tri ascendant → rapides en premier ---

    @Test
    fun `tri_ascendant_rapides_en_premier`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        whenever(palmaresDao.getMonPalmares()).thenReturn(
            listOf(
                makeMonPalmaresRow("A", dateLecture = "2024-01-01"),
                makeMonPalmaresRow("B", dateLecture = "2024-01-21"),  // 20 jours
                makeMonPalmaresRow("C", dateLecture = "2024-01-26")   // 5 jours
            )
        )
        val repo = PalmaresRepository(palmaresDao)

        val result = repo.getMonPalmaresUnifieParVitesse(ascendant = true)

        // A exclu. C (5j) avant B (20j)
        assertEquals(2, result.size)
        assertEquals("C", result[0].id)
        assertEquals(5, result[0].joursLecture)
        assertEquals("B", result[1].id)
        assertEquals(20, result[1].joursLecture)
    }

    // --- Test 6 : tri descendant → lents en premier ---

    @Test
    fun `tri_descendant_lents_en_premier`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        whenever(palmaresDao.getMonPalmares()).thenReturn(
            listOf(
                makeMonPalmaresRow("A", dateLecture = "2024-01-01"),
                makeMonPalmaresRow("B", dateLecture = "2024-01-21"),  // 20 jours
                makeMonPalmaresRow("C", dateLecture = "2024-01-26")   // 5 jours
            )
        )
        val repo = PalmaresRepository(palmaresDao)

        val result = repo.getMonPalmaresUnifieParVitesse(ascendant = false)

        // A exclu. B (20j) avant C (5j)
        assertEquals(2, result.size)
        assertEquals("B", result[0].id)
        assertEquals(20, result[0].joursLecture)
        assertEquals("C", result[1].id)
        assertEquals(5, result[1].joursLecture)
    }

    // --- Test 7 : liste vide si moins de 2 livres avec date ---

    @Test
    fun `moins_de_deux_livres_avec_date_retourne_liste_vide`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        whenever(palmaresDao.getMonPalmares()).thenReturn(
            listOf(makeMonPalmaresRow("A", dateLecture = "2024-01-01"))
        )
        val repo = PalmaresRepository(palmaresDao)

        val result = repo.getMonPalmaresUnifieParVitesse(ascendant = true)

        assertTrue("Avec 1 seul livre, la liste doit être vide", result.isEmpty())
    }

    // --- Test 8 : joursLecture est null par défaut (hors mode vitesse) ---

    @Test
    fun `joursLecture_null_dans_autres_modes_de_tri`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        whenever(palmaresDao.getMonPalmares()).thenReturn(
            listOf(makeMonPalmaresRow("A", dateLecture = "2024-01-01"))
        )
        val repo = PalmaresRepository(palmaresDao)

        val result = repo.getMonPalmaresUnifieParNote()

        assertEquals(1, result.size)
        assertNull("joursLecture doit être null hors mode vitesse", result[0].joursLecture)
    }
}
