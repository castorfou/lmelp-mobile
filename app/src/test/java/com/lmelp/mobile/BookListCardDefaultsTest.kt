package com.lmelp.mobile

import androidx.compose.ui.unit.dp
import com.lmelp.mobile.ui.components.BookListCardDefaults
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue #143 : les cartes des listes d'œuvres reprennent la géométrie de la carte Émission
 * (120dp de hauteur totale dont 2×4dp de padding vertical, soit une carte de 112dp),
 * avec une couverture pleine hauteur au ratio livre 2:3.
 */
class BookListCardDefaultsTest {

    @Test
    fun `la carte livre a la meme hauteur que la carte Emission`() {
        assertEquals(112.dp, BookListCardDefaults.CardHeight)
    }

    @Test
    fun `la couverture respecte le ratio 2 sur 3 a un dp pres`() {
        val largeurAttendue = BookListCardDefaults.CardHeight.value * 2f / 3f
        assertEquals(largeurAttendue, BookListCardDefaults.CoverWidth.value, 1f)
    }
}
