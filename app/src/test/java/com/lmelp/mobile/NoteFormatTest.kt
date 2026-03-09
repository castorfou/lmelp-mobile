package com.lmelp.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests pour le formatage des notes de critiques.
 *
 * Règle : les notes de critiques sont des entiers (stockés en Double).
 * Ne pas afficher ".0" — afficher "8" pas "8.0", mais "8.5" reste "8.5".
 */
class NoteFormatTest {

    private fun formatNote(note: Double): String =
        if (note == note.toLong().toDouble()) "${note.toLong()}" else "%.1f".format(note)

    // --- Entiers : pas de décimale ---

    @Test
    fun `note entière 8 affiche 8`() {
        assertEquals("8", formatNote(8.0))
    }

    @Test
    fun `note entière 10 affiche 10`() {
        assertEquals("10", formatNote(10.0))
    }

    @Test
    fun `note entière 1 affiche 1`() {
        assertEquals("1", formatNote(1.0))
    }

    @Test
    fun `note entière 0 affiche 0`() {
        assertEquals("0", formatNote(0.0))
    }

    // --- Décimales : conserver une décimale ---

    @Test
    fun `note 8_5 affiche 8_5`() {
        assertEquals("8.5", formatNote(8.5))
    }

    @Test
    fun `note 7_3 affiche 7_3`() {
        assertEquals("7.3", formatNote(7.3))
    }
}
