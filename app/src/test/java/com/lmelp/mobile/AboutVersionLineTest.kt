package com.lmelp.mobile

import com.lmelp.mobile.ui.about.formatVersionLine
import org.junit.Assert.assertEquals
import org.junit.Test

/** Ligne de version de l'écran À propos (issue #161). */
class AboutVersionLineTest {

    @Test
    fun `version publiee, commit et date de build`() {
        assertEquals(
            "Version 1.2.3 · abc1234 (08/11/26)",
            formatVersionLine("1.2.3", "abc1234", "08/11/26")
        )
    }

    @Test
    fun `build de dev affiche tel quel`() {
        assertEquals(
            "Version 0.0.0-dev+abc1234-debug · abc1234 (08/11/26)",
            formatVersionLine("0.0.0-dev+abc1234-debug", "abc1234", "08/11/26")
        )
    }

    @Test
    fun `preversion signalee comme telle (issue 173)`() {
        assertEquals(
            "Version 1.2.0+3 (préversion) · abc1234 (08/11/26)",
            formatVersionLine("1.2.0+3", "abc1234", "08/11/26")
        )
    }
}
