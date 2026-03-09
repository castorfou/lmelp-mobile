package com.lmelp.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests pour la logique de filtrage des recommandations (Conseils).
 *
 * Règle : afficher un livre si :
 *   - calibre_in_library = 0 (pas dans Calibre, on ne sait pas s'il est lu)
 *   - OU calibre_lu = 0 (dans Calibre mais non lu)
 *
 * Autrement dit : exclure uniquement les livres avec calibre_in_library = 1 ET calibre_lu = 1.
 */
class RecommendationsFilterTest {

    data class FakeReco(
        val livreId: String,
        val titre: String,
        val calibreInLibrary: Int = 0,
        val calibreLu: Int = 0
    )

    private fun shouldShowReco(r: FakeReco): Boolean =
        r.calibreInLibrary == 0 || r.calibreLu == 0

    // --- Livres à afficher ---

    @Test
    fun `livre absent de Calibre est affiché`() {
        val reco = FakeReco("1", "Titre A", calibreInLibrary = 0, calibreLu = 0)
        assertTrue(shouldShowReco(reco))
    }

    @Test
    fun `livre dans Calibre mais non lu est affiché`() {
        val reco = FakeReco("2", "Titre B", calibreInLibrary = 1, calibreLu = 0)
        assertTrue(shouldShowReco(reco))
    }

    // --- Livres à masquer ---

    @Test
    fun `livre dans Calibre et déjà lu est masqué`() {
        val reco = FakeReco("3", "Titre C", calibreInLibrary = 1, calibreLu = 1)
        assertTrue(!shouldShowReco(reco))
    }

    // --- Filtrage d'une liste ---

    @Test
    fun `filtre retire uniquement les livres lus`() {
        val recos = listOf(
            FakeReco("1", "Non lu",       calibreInLibrary = 0, calibreLu = 0),
            FakeReco("2", "Lu",           calibreInLibrary = 1, calibreLu = 1),
            FakeReco("3", "Dans calibre", calibreInLibrary = 1, calibreLu = 0),
            FakeReco("4", "Hors calibre", calibreInLibrary = 0, calibreLu = 0),
        )
        val filtered = recos.filter { shouldShowReco(it) }
        assertEquals(3, filtered.size)
        assertTrue(filtered.none { it.livreId == "2" })
    }

    @Test
    fun `si aucun livre dans Calibre la liste est inchangée`() {
        val recos = listOf(
            FakeReco("1", "A", calibreInLibrary = 0, calibreLu = 0),
            FakeReco("2", "B", calibreInLibrary = 0, calibreLu = 0),
        )
        val filtered = recos.filter { shouldShowReco(it) }
        assertEquals(2, filtered.size)
    }

    @Test
    fun `si tous les livres sont lus la liste est vide`() {
        val recos = listOf(
            FakeReco("1", "A", calibreInLibrary = 1, calibreLu = 1),
            FakeReco("2", "B", calibreInLibrary = 1, calibreLu = 1),
        )
        val filtered = recos.filter { shouldShowReco(it) }
        assertTrue(filtered.isEmpty())
    }
}
