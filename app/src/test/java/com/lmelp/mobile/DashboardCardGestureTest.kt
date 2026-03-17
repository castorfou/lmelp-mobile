package com.lmelp.mobile

import com.lmelp.mobile.ui.home.GestureAction
import com.lmelp.mobile.ui.home.resolveGestureAction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests pour la logique de résolution du geste sur DashboardCard.
 *
 * Un tap pur (doigt levé sans déplacement) doit déclencher onClick,
 * pas être ignoré. Bug #66 : detectHorizontalDragGestures ignorait les taps.
 *
 * Règles :
 *  - totalDragX == 0f  → TAP (doigt levé sans mouvement)
 *  - |totalDragX| < threshold → TAP (micro-mouvement, traité comme tap)
 *  - totalDragX < -threshold → SWIPE_LEFT (page suivante)
 *  - totalDragX > +threshold → SWIPE_RIGHT (page précédente)
 */
class DashboardCardGestureTest {

    private val threshold = 60f

    // --- Tap pur ---

    @Test
    fun `tap pur sans mouvement declenche TAP`() {
        assertEquals(GestureAction.TAP, resolveGestureAction(0f, threshold))
    }

    // --- Micro-mouvements traités comme tap ---

    @Test
    fun `micro-mouvement positif sous le seuil declenche TAP`() {
        assertEquals(GestureAction.TAP, resolveGestureAction(30f, threshold))
    }

    @Test
    fun `micro-mouvement negatif sous le seuil declenche TAP`() {
        assertEquals(GestureAction.TAP, resolveGestureAction(-30f, threshold))
    }

    @Test
    fun `mouvement exactement au seuil declenche TAP`() {
        assertEquals(GestureAction.TAP, resolveGestureAction(60f, threshold))
        assertEquals(GestureAction.TAP, resolveGestureAction(-60f, threshold))
    }

    // --- Swipe gauche (page suivante) ---

    @Test
    fun `swipe gauche au-dela du seuil declenche SWIPE_LEFT`() {
        assertEquals(GestureAction.SWIPE_LEFT, resolveGestureAction(-61f, threshold))
        assertEquals(GestureAction.SWIPE_LEFT, resolveGestureAction(-200f, threshold))
    }

    // --- Swipe droite (page précédente) ---

    @Test
    fun `swipe droite au-dela du seuil declenche SWIPE_RIGHT`() {
        assertEquals(GestureAction.SWIPE_RIGHT, resolveGestureAction(61f, threshold))
        assertEquals(GestureAction.SWIPE_RIGHT, resolveGestureAction(200f, threshold))
    }
}
