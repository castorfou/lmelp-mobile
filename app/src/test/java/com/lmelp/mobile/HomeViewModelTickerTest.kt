package com.lmelp.mobile

import com.lmelp.mobile.viewmodel.randomInitialIndex
import com.lmelp.mobile.viewmodel.sampleTickerDelayMs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

/**
 * Tests pour les fonctions pures du ticker aléatoire (issue #80).
 * - randomInitialIndex : index de départ aléatoire pour chaque carte
 * - sampleTickerDelayMs : délai tiré selon N(µ, σ) tronqué à minMs
 */
class HomeViewModelTickerTest {

    // --- randomInitialIndex ---

    @Test
    fun `randomInitialIndex returns 0 for empty list`() {
        assertEquals(0, randomInitialIndex(0))
    }

    @Test
    fun `randomInitialIndex returns 0 for singleton list`() {
        assertEquals(0, randomInitialIndex(1))
    }

    @Test
    fun `randomInitialIndex returns index within bounds for multiple items`() {
        repeat(100) {
            val idx = randomInitialIndex(5)
            assertTrue("Index $idx doit être dans [0, 4]", idx in 0 until 5)
        }
    }

    @Test
    fun `randomInitialIndex returns index within bounds for large list`() {
        repeat(100) {
            val idx = randomInitialIndex(100)
            assertTrue("Index $idx doit être dans [0, 99]", idx in 0 until 100)
        }
    }

    // --- sampleTickerDelayMs ---

    @Test
    fun `sampleTickerDelayMs is always at least minMs`() {
        // Utilise un Random avec seed fixe pour reproductibilité
        val rng = Random(42)
        repeat(1000) {
            val delay = sampleTickerDelayMs(meanMs = 5_000L, stdDevMs = 2_000L, minMs = 1_000L, random = rng)
            assertTrue("Délai $delay doit être >= 1000ms", delay >= 1_000L)
        }
    }

    @Test
    fun `sampleTickerDelayMs median is close to mean`() {
        // Avec 1000 tirages, la médiane doit être proche de la moyenne (5000ms)
        val rng = Random(123)
        val samples = (1..1000).map {
            sampleTickerDelayMs(meanMs = 5_000L, stdDevMs = 2_000L, minMs = 1_000L, random = rng)
        }.sorted()
        val median = samples[500]
        assertTrue("Médiane $median doit être dans [3000, 7000]", median in 3_000L..7_000L)
    }

    @Test
    fun `sampleTickerDelayMs uses provided random for determinism`() {
        // Deux appels avec le même seed doivent donner le même résultat
        val delay1 = sampleTickerDelayMs(meanMs = 5_000L, stdDevMs = 2_000L, minMs = 1_000L, random = Random(999))
        val delay2 = sampleTickerDelayMs(meanMs = 5_000L, stdDevMs = 2_000L, minMs = 1_000L, random = Random(999))
        assertEquals(delay1, delay2)
    }
}
