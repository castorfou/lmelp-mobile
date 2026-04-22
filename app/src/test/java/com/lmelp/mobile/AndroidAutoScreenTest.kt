package com.lmelp.mobile

import com.lmelp.mobile.data.model.EmissionUi
import com.lmelp.mobile.data.model.PalmaresUi
import com.lmelp.mobile.data.model.RecommendationUi
import com.lmelp.mobile.ui.auto.CarScreenBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidAutoScreenTest {

    // --- Helpers ---

    private fun fakeEmission(id: String, titre: String, date: String = "2024-01-01") =
        EmissionUi(id = id, titre = titre, date = date, duree = 60, nbAvis = 3, hasSummary = false)

    private fun fakePalmares(rank: Int, titre: String, note: Double = 8.5) = PalmaresUi(
        rank = rank, livreId = "livre$rank", titre = titre, auteurNom = "Auteur $rank",
        noteMoyenne = note, nbAvis = 3, nbCritiques = 2
    )

    private fun fakeReco(rank: Int, titre: String) = RecommendationUi(
        rank = rank, livreId = "livre$rank", titre = titre, auteurNom = "Auteur $rank",
        scoreHybride = 0.9, masqueMean = 8.0
    )

    // --- MainCarScreen ---

    @Test
    fun `menu principal contient 5 entrees`() {
        val items = CarScreenBuilder.buildMainMenuItems()
        assertEquals(5, items.size)
    }

    @Test
    fun `menu principal contient Accueil Emissions Palmares Conseils Recherche`() {
        val items = CarScreenBuilder.buildMainMenuItems()
        val titres = items.map { it.title }
        assertTrue(titres.contains("Accueil"))
        assertTrue(titres.contains("Émissions"))
        assertTrue(titres.contains("Palmarès"))
        assertTrue(titres.contains("Conseils"))
        assertTrue(titres.contains("Recherche"))
    }

    // --- EmissionsCarScreen ---

    @Test
    fun `liste emissions limitee a 6 items`() {
        val emissions = (1..10).map { fakeEmission("e$it", "Emission $it") }
        val items = CarScreenBuilder.buildEmissionsItems(emissions)
        assertEquals(6, items.size)
    }

    @Test
    fun `liste emissions affiche le titre`() {
        val emissions = listOf(fakeEmission("e1", "Le livre du siècle"))
        val items = CarScreenBuilder.buildEmissionsItems(emissions)
        assertEquals("Le livre du siècle", items.first().title)
    }

    @Test
    fun `liste emissions affiche la date en sous-titre`() {
        val emissions = listOf(fakeEmission("e1", "Titre", date = "2024-03-15"))
        val items = CarScreenBuilder.buildEmissionsItems(emissions)
        assertTrue(items.first().text.contains("2024-03-15"))
    }

    @Test
    fun `liste emissions vide retourne liste vide`() {
        val items = CarScreenBuilder.buildEmissionsItems(emptyList())
        assertTrue(items.isEmpty())
    }

    // --- PalmaresCarScreen ---

    @Test
    fun `liste palmares limitee a 6 items`() {
        val palmares = (1..10).map { fakePalmares(it, "Livre $it") }
        val items = CarScreenBuilder.buildPalmaresItems(palmares)
        assertEquals(6, items.size)
    }

    @Test
    fun `liste palmares affiche titre et auteur`() {
        val palmares = listOf(fakePalmares(1, "Les Misérables", 9.2))
        val items = CarScreenBuilder.buildPalmaresItems(palmares)
        assertEquals("Les Misérables", items.first().title)
        assertTrue(items.first().text.contains("Auteur 1"))
    }

    @Test
    fun `liste palmares affiche la note`() {
        val palmares = listOf(fakePalmares(1, "Titre", note = 8.7))
        val items = CarScreenBuilder.buildPalmaresItems(palmares)
        assertTrue(items.first().text.contains("8.7") || items.first().text.contains("8,7"))
    }

    // --- RecommendationsCarScreen ---

    @Test
    fun `liste recommendations limitee a 6 items`() {
        val recos = (1..10).map { fakeReco(it, "Livre $it") }
        val items = CarScreenBuilder.buildRecommendationsItems(recos)
        assertEquals(6, items.size)
    }

    @Test
    fun `liste recommendations affiche titre et auteur`() {
        val recos = listOf(fakeReco(1, "Dune"))
        val items = CarScreenBuilder.buildRecommendationsItems(recos)
        assertEquals("Dune", items.first().title)
        assertTrue(items.first().text.contains("Auteur 1"))
    }

    // --- Accueil stats ---

    @Test
    fun `accueil construit le body avec nb emissions et livres`() {
        val body = CarScreenBuilder.buildAccueilBody(nbEmissions = "173", nbLivres = "1615")
        assertTrue(body.contains("173"))
        assertTrue(body.contains("1615"))
    }
}
