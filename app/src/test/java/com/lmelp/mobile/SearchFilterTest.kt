package com.lmelp.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests pour le filtrage des types de résultats de recherche.
 *
 * Seuls les types autorisés sont retournés : 'livre', 'auteur', 'critique'.
 * Les 'emission' et autres types sont exclus.
 */
class SearchFilterTest {

    private val typesAutorises = setOf("livre", "auteur", "critique")

    data class FakeResult(val type: String, val content: String)

    private fun filtrerResultats(resultats: List<FakeResult>): List<FakeResult> =
        resultats.filter { it.type in typesAutorises }

    // --- Types inclus ---

    @Test
    fun `livre est inclus dans les résultats`() {
        val resultats = listOf(FakeResult("livre", "Le Grand Meaulnes"))
        assertEquals(1, filtrerResultats(resultats).size)
    }

    @Test
    fun `auteur est inclus dans les résultats`() {
        val resultats = listOf(FakeResult("auteur", "Alain-Fournier"))
        assertEquals(1, filtrerResultats(resultats).size)
    }

    @Test
    fun `critique est inclus dans les résultats`() {
        val resultats = listOf(FakeResult("critique", "Jérôme Garcin"))
        assertEquals(1, filtrerResultats(resultats).size)
    }

    // --- Types exclus ---

    @Test
    fun `emission est exclu des résultats`() {
        val resultats = listOf(FakeResult("emission", "Émission du 01/01/2024"))
        assertTrue(filtrerResultats(resultats).isEmpty())
    }

    @Test
    fun `type inconnu est exclu des résultats`() {
        val resultats = listOf(FakeResult("editeur", "Gallimard"))
        assertTrue(filtrerResultats(resultats).isEmpty())
    }

    // --- Filtrage d'une liste mixte ---

    @Test
    fun `filtre une liste mixte et ne garde que les types autorisés`() {
        val resultats = listOf(
            FakeResult("livre",    "Le Petit Prince"),
            FakeResult("emission", "Émission 2024"),
            FakeResult("auteur",   "Saint-Exupéry"),
            FakeResult("critique", "Jérôme Garcin"),
            FakeResult("editeur",  "Gallimard"),
        )
        val filtrés = filtrerResultats(resultats)
        assertEquals(3, filtrés.size)
        assertTrue(filtrés.none { it.type == "emission" })
        assertTrue(filtrés.none { it.type == "editeur" })
    }

    @Test
    fun `liste sans émissions est inchangée`() {
        val resultats = listOf(
            FakeResult("livre",    "Candide"),
            FakeResult("auteur",   "Voltaire"),
            FakeResult("critique", "Nelly Kaprièlian"),
        )
        assertEquals(3, filtrerResultats(resultats).size)
    }

    @Test
    fun `liste vide retourne liste vide`() {
        assertTrue(filtrerResultats(emptyList()).isEmpty())
    }
}
