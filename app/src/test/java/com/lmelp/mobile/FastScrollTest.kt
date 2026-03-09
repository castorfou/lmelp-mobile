package com.lmelp.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests unitaires pour la logique du fast scroll dans l'écran Émissions.
 *
 * Logique testée :
 * - calcul de monthIndices (premier index de chaque mois dans la liste)
 * - formatage "2025-11" → "Nov. 2025"
 */
class FastScrollTest {

    // ---- Logique de groupement par mois ----

    /**
     * Simule le calcul de monthIndices depuis une liste de dates ISO (YYYY-MM-DD).
     * Retourne une liste de paires (yearMonth, firstIndex).
     */
    private fun buildMonthIndices(dates: List<String>): List<Pair<String, Int>> =
        dates.mapIndexedNotNull { index, date ->
            val ym = date.take(7)
            if (index == 0 || date.take(7) != dates[index - 1].take(7))
                ym to index
            else
                null
        }

    @Test
    fun `liste vide retourne indices vides`() {
        assertEquals(emptyList<Pair<String, Int>>(), buildMonthIndices(emptyList()))
    }

    @Test
    fun `un seul item retourne un seul mois`() {
        val result = buildMonthIndices(listOf("2025-11-15"))
        assertEquals(listOf("2025-11" to 0), result)
    }

    @Test
    fun `plusieurs items dans le meme mois retourne un seul indice`() {
        val dates = listOf("2025-11-15", "2025-11-10", "2025-11-01")
        val result = buildMonthIndices(dates)
        assertEquals(listOf("2025-11" to 0), result)
    }

    @Test
    fun `deux mois differents retournent deux indices`() {
        val dates = listOf("2025-11-15", "2025-11-01", "2025-10-20", "2025-10-05")
        val result = buildMonthIndices(dates)
        assertEquals(
            listOf("2025-11" to 0, "2025-10" to 2),
            result
        )
    }

    @Test
    fun `trois mois retournent trois indices au bon premier item`() {
        val dates = listOf(
            "2025-11-15",
            "2025-10-20", "2025-10-10",
            "2025-09-30", "2025-09-15", "2025-09-01"
        )
        val result = buildMonthIndices(dates)
        assertEquals(
            listOf("2025-11" to 0, "2025-10" to 1, "2025-09" to 3),
            result
        )
    }

    @Test
    fun `chaque item dans son propre mois retourne tous les indices`() {
        val dates = listOf("2025-03-01", "2025-02-15", "2025-01-10")
        val result = buildMonthIndices(dates)
        assertEquals(
            listOf("2025-03" to 0, "2025-02" to 1, "2025-01" to 2),
            result
        )
    }

    // ---- Formatage du libellé mois/an ----

    /**
     * Formate "YYYY-MM" en libellé localisé court (français).
     * Ex: "2025-11" → "Nov. 2025"
     */
    private fun formatYearMonth(yearMonth: String): String {
        val parts = yearMonth.split("-")
        val year = parts[0].toInt()
        val month = parts[1].toInt()
        val mois = listOf(
            "Jan.", "Fév.", "Mar.", "Avr.", "Mai", "Juin",
            "Juil.", "Août", "Sep.", "Oct.", "Nov.", "Déc."
        )
        return "${mois[month - 1]} $year"
    }

    @Test
    fun `novembre 2025 affiche Nov 2025`() {
        assertEquals("Nov. 2025", formatYearMonth("2025-11"))
    }

    @Test
    fun `janvier 2024 affiche Jan 2024`() {
        assertEquals("Jan. 2024", formatYearMonth("2024-01"))
    }

    @Test
    fun `decembre 2023 affiche Déc 2023`() {
        assertEquals("Déc. 2023", formatYearMonth("2023-12"))
    }

    @Test
    fun `mai 2025 affiche Mai 2025`() {
        assertEquals("Mai 2025", formatYearMonth("2025-05"))
    }

    @Test
    fun `juin 2025 affiche Juin 2025`() {
        assertEquals("Juin 2025", formatYearMonth("2025-06"))
    }

    @Test
    fun `juillet 2025 affiche Juil 2025`() {
        assertEquals("Juil. 2025", formatYearMonth("2025-07"))
    }

    // ---- Calcul du segment de drag ----

    /**
     * Calcule l'index de mois cible en fonction de la position de drag (0f..1f)
     * et du nombre total de mois.
     */
    private fun dragPositionToMonthIndex(position: Float, totalMonths: Int): Int {
        if (totalMonths == 0) return 0
        return (position * totalMonths).toInt().coerceIn(0, totalMonths - 1)
    }

    @Test
    fun `drag a 0 retourne le premier mois`() {
        assertEquals(0, dragPositionToMonthIndex(0f, 12))
    }

    @Test
    fun `drag a 1 retourne le dernier mois`() {
        assertEquals(11, dragPositionToMonthIndex(1f, 12))
    }

    @Test
    fun `drag au milieu retourne le mois central`() {
        assertEquals(6, dragPositionToMonthIndex(0.5f, 12))
    }

    @Test
    fun `drag avec un seul mois retourne 0`() {
        assertEquals(0, dragPositionToMonthIndex(0.9f, 1))
    }
}
