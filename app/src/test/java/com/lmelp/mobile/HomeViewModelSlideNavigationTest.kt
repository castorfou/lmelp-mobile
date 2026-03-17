package com.lmelp.mobile

import com.lmelp.mobile.viewmodel.nextSlideIndex
import com.lmelp.mobile.viewmodel.prevSlideIndex
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests pour la logique de navigation dans les slides (next/prev).
 * Teste les fonctions pures nextSlideIndex/prevSlideIndex — sans coroutines ni ViewModel.
 */
class HomeViewModelSlideNavigationTest {

    // --- nextSlideIndex ---

    @Test
    fun `nextSlideIndex increments index`() {
        assertEquals(1, nextSlideIndex(0, 3))
        assertEquals(2, nextSlideIndex(1, 3))
    }

    @Test
    fun `nextSlideIndex wraps around at end`() {
        assertEquals(0, nextSlideIndex(1, 2))
        assertEquals(0, nextSlideIndex(2, 3))
    }

    @Test
    fun `nextSlideIndex returns 0 for single item`() {
        assertEquals(0, nextSlideIndex(0, 1))
    }

    @Test
    fun `nextSlideIndex returns 0 for empty list`() {
        assertEquals(0, nextSlideIndex(0, 0))
    }

    // --- prevSlideIndex ---

    @Test
    fun `prevSlideIndex decrements index`() {
        assertEquals(0, prevSlideIndex(1, 3))
        assertEquals(1, prevSlideIndex(2, 3))
    }

    @Test
    fun `prevSlideIndex wraps around at start`() {
        assertEquals(2, prevSlideIndex(0, 3))
        assertEquals(1, prevSlideIndex(0, 2))
    }

    @Test
    fun `prevSlideIndex returns 0 for single item`() {
        assertEquals(0, prevSlideIndex(0, 1))
    }

    @Test
    fun `prevSlideIndex returns 0 for empty list`() {
        assertEquals(0, prevSlideIndex(0, 0))
    }
}
