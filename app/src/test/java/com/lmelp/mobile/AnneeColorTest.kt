package com.lmelp.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests pour la fonction couleurAnneeLong — variation bleu clair/foncé
 * selon l'année d'émission (issue #138), pour la mosaïque de fond des cartes.
 *
 * Logique : alterne entre un bleu foncé (année paire) et un bleu clair
 * (année impaire), sans dépendance à la date de référence (mosaïque stable
 * dans le temps, indépendante de "aujourd'hui").
 *
 *   année paire   → 0xFF0D47A1 (bleu foncé)
 *   année impaire → 0xFF42A5F5 (bleu clair)
 *   date invalide → 0xFF1565C0 (LmelpBleu, fallback neutre)
 */
class AnneeColorTest {

    // Simule la logique pure de couleurAnnee (sans dépendance Compose)
    private fun couleurAnneeLong(date: String): Long {
        val annee = date.take(4).toIntOrNull() ?: return 0xFF1565C0L
        return if (annee % 2 == 0) 0xFF0D47A1L else 0xFF42A5F5L
    }

    @Test
    fun `annee paire est bleu fonce`() {
        assertEquals(0xFF0D47A1L, couleurAnneeLong("2024-03-01"))
    }

    @Test
    fun `annee impaire est bleu clair`() {
        assertEquals(0xFF42A5F5L, couleurAnneeLong("2023-03-01"))
    }

    @Test
    fun `date avec heure ISO complete est geree`() {
        assertEquals(0xFF0D47A1L, couleurAnneeLong("2024-03-01T10:59:39Z"))
    }

    @Test
    fun `date sans annee exploitable retombe sur LmelpBleu`() {
        assertEquals(0xFF1565C0L, couleurAnneeLong("xxxx-03-01"))
    }
}
