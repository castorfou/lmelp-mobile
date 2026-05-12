package com.lmelp.mobile

import com.lmelp.mobile.data.db.CalibreHorsMasqueDao
import com.lmelp.mobile.data.db.MonPalmaresRow
import com.lmelp.mobile.data.db.PalmaresDao
import com.lmelp.mobile.data.model.CalibreHorsMasqueEntity
import com.lmelp.mobile.data.model.MonPalmaresItemUi
import com.lmelp.mobile.data.repository.PalmaresRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Tests TDD pour l'affichage de la note du Masque dans Mon Palmarès (issue #107).
 *
 * La note du Masque (noteMoyenne) doit être propagée depuis MonPalmaresRow
 * vers MonPalmaresItemUi pour être affichée dans MonPalmaresCard.
 * Les livres hors Masque n'ont pas de note Masque (noteMoyenne = null).
 */
class MonPalmaresNoteMasqueTest {

    private fun makeMonPalmaresRow(
        livreId: String,
        noteMoyenne: Double = 7.5,
        calibreRating: Double? = null,
        dateLecture: String? = null
    ) = MonPalmaresRow(
        livreId = livreId,
        titre = "Titre $livreId",
        auteurNom = null,
        noteMoyenne = noteMoyenne,
        nbAvis = 3,
        nbCritiques = 3,
        calibreRating = calibreRating,
        urlCover = null,
        dateLecture = dateLecture
    )

    private fun makeHorsMasque(id: String, calibreRating: Double? = null) =
        CalibreHorsMasqueEntity(
            id = id,
            titre = "HM $id",
            auteurNom = null,
            calibreRating = calibreRating,
            dateLecture = null
        )

    @Test
    fun `noteMoyenne propagee depuis MonPalmaresRow vers MonPalmaresItemUi`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        whenever(palmaresDao.getMonPalmares()).thenReturn(
            listOf(makeMonPalmaresRow("livre1", noteMoyenne = 8.2, calibreRating = 9.0))
        )
        val repo = PalmaresRepository(palmaresDao)

        val result = repo.getMonPalmaresUnifieParNote()

        assertEquals(1, result.size)
        assertEquals(8.2, result[0].noteMoyenne!!, 0.01)
    }

    @Test
    fun `livre hors Masque a noteMoyenne null`() = runTest {
        val palmaresDao = mock<PalmaresDao>()
        val horsMasqueDao = mock<CalibreHorsMasqueDao>()
        whenever(palmaresDao.getMonPalmares()).thenReturn(emptyList())
        whenever(horsMasqueDao.getAll()).thenReturn(
            listOf(makeHorsMasque("hm1", calibreRating = 8.0))
        )
        val repo = PalmaresRepository(palmaresDao, horsMasqueDao)

        val result = repo.getMonPalmaresUnifieParNote()

        assertEquals(1, result.size)
        assertNull("Un livre hors Masque n'a pas de note Masque", result[0].noteMoyenne)
    }

    @Test
    fun `MonPalmaresItemUi a noteMoyenne null par defaut`() {
        val item = MonPalmaresItemUi(
            id = "x",
            titre = "Test",
            auteurNom = null,
            calibreRating = null,
            dateLecture = null
        )
        assertNull(item.noteMoyenne)
    }
}
