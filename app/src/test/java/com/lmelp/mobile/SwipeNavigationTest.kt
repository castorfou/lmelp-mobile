package com.lmelp.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests pour la logique de navigation circulaire par swipe.
 *
 * Ordre des pages : Home → Émissions → Palmarès → Conseils → Recherche → (retour Home)
 * Swipe gauche = page suivante, Swipe droite = page précédente
 */
class SwipeNavigationTest {

    private val swipeRoutes = listOf(
        Routes.HOME,
        Routes.EMISSIONS,
        Routes.PALMARES,
        Routes.RECOMMENDATIONS,
        Routes.SEARCH,
    )

    private fun swipeLeft(currentRoute: String): String? =
        getSwipeTarget(currentRoute, direction = -1)

    private fun swipeRight(currentRoute: String): String? =
        getSwipeTarget(currentRoute, direction = +1)

    private fun getSwipeTarget(currentRoute: String, direction: Int): String? {
        val currentIndex = swipeRoutes.indexOf(currentRoute)
        if (currentIndex == -1) return null
        val targetIndex = (currentIndex - direction + swipeRoutes.size) % swipeRoutes.size
        return swipeRoutes[targetIndex]
    }

    // --- Swipe gauche (page suivante) ---

    @Test
    fun `swipe gauche depuis Home va vers Emissions`() {
        assertEquals(Routes.EMISSIONS, swipeLeft(Routes.HOME))
    }

    @Test
    fun `swipe gauche depuis Emissions va vers Palmares`() {
        assertEquals(Routes.PALMARES, swipeLeft(Routes.EMISSIONS))
    }

    @Test
    fun `swipe gauche depuis Palmares va vers Conseils`() {
        assertEquals(Routes.RECOMMENDATIONS, swipeLeft(Routes.PALMARES))
    }

    @Test
    fun `swipe gauche depuis Conseils va vers Recherche`() {
        assertEquals(Routes.SEARCH, swipeLeft(Routes.RECOMMENDATIONS))
    }

    @Test
    fun `swipe gauche depuis Recherche revient a Home (circulaire)`() {
        assertEquals(Routes.HOME, swipeLeft(Routes.SEARCH))
    }

    // --- Swipe droite (page précédente) ---

    @Test
    fun `swipe droite depuis Emissions va vers Home`() {
        assertEquals(Routes.HOME, swipeRight(Routes.EMISSIONS))
    }

    @Test
    fun `swipe droite depuis Palmares va vers Emissions`() {
        assertEquals(Routes.EMISSIONS, swipeRight(Routes.PALMARES))
    }

    @Test
    fun `swipe droite depuis Conseils va vers Palmares`() {
        assertEquals(Routes.PALMARES, swipeRight(Routes.RECOMMENDATIONS))
    }

    @Test
    fun `swipe droite depuis Recherche va vers Conseils`() {
        assertEquals(Routes.RECOMMENDATIONS, swipeRight(Routes.SEARCH))
    }

    @Test
    fun `swipe droite depuis Home va vers Recherche (circulaire)`() {
        assertEquals(Routes.SEARCH, swipeRight(Routes.HOME))
    }

    // --- Route inconnue ---

    @Test
    fun `route inconnue retourne null`() {
        assertNull(swipeLeft(Routes.CRITIQUES))
        assertNull(swipeRight("unknown_route"))
    }
}
