package com.lmelp.mobile

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests pour shouldShowLabel — masque les labels de la barre de navigation en mode paysage.
 *
 * Comportement attendu (issue #14) :
 *   portrait  (isLandscape = false) → afficher les labels (true)
 *   paysage   (isLandscape = true)  → masquer les labels (false)
 */
class NavigationLabelTest {

    // --- Mode portrait ---

    @Test
    fun `en portrait les labels sont affichés`() {
        assertTrue(shouldShowLabel(isLandscape = false))
    }

    // --- Mode paysage ---

    @Test
    fun `en paysage les labels sont masqués`() {
        assertFalse(shouldShowLabel(isLandscape = true))
    }
}
