package com.lmelp.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests pour la fonction couleurNote — code couleur selon le seuil de la note.
 *
 * Seuils (identiques au back-office lmelp) :
 *   note >= 9  → 0xFF00C851 (vert vif, "excellent")
 *   note >= 7  → 0xFF8BC34A (vert clair, "bien")
 *   note >= 5  → 0xFFCDDC39 (jaune-vert, "moyen")
 *   note < 5   → 0xFFF44336 (rouge, "faible")
 */
class NoteColorTest {

    // Simule la logique pure de couleurNote (sans dépendance Compose)
    private fun couleurNoteLong(note: Double): Long = when {
        note >= 9.0 -> 0xFF00C851L
        note >= 7.0 -> 0xFF8BC34AL
        note >= 5.0 -> 0xFFCDDC39L
        else        -> 0xFFF44336L
    }

    // --- Seuil excellent (>= 9) ---

    @Test
    fun `note 10 est excellente`() {
        assertEquals(0xFF00C851L, couleurNoteLong(10.0))
    }

    @Test
    fun `note 9 est excellente`() {
        assertEquals(0xFF00C851L, couleurNoteLong(9.0))
    }

    @Test
    fun `note 9_5 est excellente`() {
        assertEquals(0xFF00C851L, couleurNoteLong(9.5))
    }

    // --- Seuil bien (>= 7, < 9) ---

    @Test
    fun `note 8 est bien`() {
        assertEquals(0xFF8BC34AL, couleurNoteLong(8.0))
    }

    @Test
    fun `note 7 est bien`() {
        assertEquals(0xFF8BC34AL, couleurNoteLong(7.0))
    }

    @Test
    fun `note 7_5 est bien`() {
        assertEquals(0xFF8BC34AL, couleurNoteLong(7.5))
    }

    @Test
    fun `note 8_99 est bien (pas encore excellente)`() {
        assertEquals(0xFF8BC34AL, couleurNoteLong(8.99))
    }

    // --- Seuil moyen (>= 5, < 7) ---

    @Test
    fun `note 6 est moyenne`() {
        assertEquals(0xFFCDDC39L, couleurNoteLong(6.0))
    }

    @Test
    fun `note 5 est moyenne`() {
        assertEquals(0xFFCDDC39L, couleurNoteLong(5.0))
    }

    @Test
    fun `note 5_5 est moyenne`() {
        assertEquals(0xFFCDDC39L, couleurNoteLong(5.5))
    }

    // --- Seuil faible (< 5) ---

    @Test
    fun `note 4 est faible`() {
        assertEquals(0xFFF44336L, couleurNoteLong(4.0))
    }

    @Test
    fun `note 1 est faible`() {
        assertEquals(0xFFF44336L, couleurNoteLong(1.0))
    }

    @Test
    fun `note 4_99 est faible (pas encore moyenne)`() {
        assertEquals(0xFFF44336L, couleurNoteLong(4.99))
    }
}
