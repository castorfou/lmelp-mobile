package com.lmelp.mobile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires pour la logique de calcul d'offset d'animation de slide.
 *
 * Convention de direction :
 *   -1 = swipe vers la gauche (page suivante)
 *   +1 = swipe vers la droite (page précédente)
 *    0 = pas de swipe (navigation directe via tap)
 *
 * Logique d'animation (comme Android standard) :
 *   - Swipe gauche (-1) : nouvelle page entre par la droite (+width), ancienne sort par la gauche (-width)
 *   - Swipe droite (+1) : nouvelle page entre par la gauche (-width), ancienne sort par la droite (+width)
 */
class SwipeAnimationTest {

    /**
     * Calcule l'offset d'entrée de la nouvelle page.
     * Retourne la position initiale (en pixels) depuis laquelle la page entre.
     * Valeur positive = entre par la droite, négative = entre par la gauche.
     */
    private fun slideEnterOffset(swipeDirection: Int, fullWidth: Int): Int =
        if (swipeDirection <= 0) fullWidth else -fullWidth

    /**
     * Calcule l'offset de sortie de l'ancienne page.
     * Retourne la position finale (en pixels) vers laquelle la page sort.
     * Valeur négative = sort par la gauche, positive = sort par la droite.
     */
    private fun slideExitOffset(swipeDirection: Int, fullWidth: Int): Int =
        if (swipeDirection <= 0) -fullWidth else fullWidth

    // ---- Tests d'entrée ----

    @Test
    fun `swipe gauche - nouvelle page entre par la droite`() {
        assertEquals(100, slideEnterOffset(-1, 100))
    }

    @Test
    fun `swipe droite - nouvelle page entre par la gauche`() {
        assertEquals(-100, slideEnterOffset(+1, 100))
    }

    @Test
    fun `pas de swipe - nouvelle page entre par la droite par defaut`() {
        assertEquals(100, slideEnterOffset(0, 100))
    }

    // ---- Tests de sortie ----

    @Test
    fun `swipe gauche - ancienne page sort par la gauche`() {
        assertEquals(-100, slideExitOffset(-1, 100))
    }

    @Test
    fun `swipe droite - ancienne page sort par la droite`() {
        assertEquals(100, slideExitOffset(+1, 100))
    }

    @Test
    fun `pas de swipe - ancienne page sort par la gauche par defaut`() {
        assertEquals(-100, slideExitOffset(0, 100))
    }

    // ---- Cohérence entrée/sortie ----

    @Test
    fun `swipe gauche - entree et sortie coherentes`() {
        val width = 360
        val enter = slideEnterOffset(-1, width)
        val exit = slideExitOffset(-1, width)
        assertTrue("L'entrée doit être positive (droite)", enter > 0)
        assertTrue("La sortie doit être négative (gauche)", exit < 0)
    }

    @Test
    fun `swipe droite - entree et sortie coherentes`() {
        val width = 360
        val enter = slideEnterOffset(+1, width)
        val exit = slideExitOffset(+1, width)
        assertTrue("L'entrée doit être négative (gauche)", enter < 0)
        assertTrue("La sortie doit être positive (droite)", exit > 0)
    }
}
