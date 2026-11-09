package com.lmelp.mobile

import com.lmelp.mobile.ui.about.HistoriqueCommit
import com.lmelp.mobile.ui.about.formatContenuVersionHeader
import com.lmelp.mobile.ui.about.groupHistoriqueByVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regroupement de l'historique par version de l'appli (issue #170, vocabulaire de CONTEXT.md). */
class HistoriqueGroupingTest {

    // Historique réel au moment de l'issue, du plus récent au plus ancien
    private val historique = listOf(
        HistoriqueCommit("b80f3c7", "Réessais 5xx #156", "09/11/26"),
        HistoriqueCommit("ab74d65", "Gradle 9.9.0 #158", "09/11/26", versionName = "1.1.0"),
        HistoriqueCommit("20a35cb", "Nouvelle version de l'appli #162", "08/11/26", versionName = "1.0.0"),
        HistoriqueCommit("d024c3e", "Version depuis le tag #161", "08/11/26", versionName = "0.9.0"),
        HistoriqueCommit("ef51dc6", "APK debug et release #160", "08/11/26"),
        HistoriqueCommit("1370b61", "Node 24 #159", "08/11/26")
    )

    @Test
    fun `une version contient son commit tague et les commits en dessous jusqu au tag precedent`() {
        val sections = groupHistoriqueByVersion(historique, installedVersionName = "1.1.0")

        assertEquals(listOf(null, "1.1.0", "1.0.0", "0.9.0"), sections.map { it.versionName })
        assertEquals(listOf("b80f3c7"), sections[0].commits.map { it.hash })
        assertEquals(listOf("ab74d65"), sections[1].commits.map { it.hash })
        assertEquals(listOf("20a35cb"), sections[2].commits.map { it.hash })
        assertEquals(listOf("d024c3e", "ef51dc6", "1370b61"), sections[3].commits.map { it.hash })
    }

    @Test
    fun `la date d une version est celle de son commit tague`() {
        val sections = groupHistoriqueByVersion(historique, installedVersionName = "1.1.0")
        assertNull(sections[0].date)
        assertEquals("09/11/26", sections[1].date)
        assertEquals("08/11/26", sections[3].date)
    }

    @Test
    fun `seule la version installee est marquee`() {
        val sections = groupHistoriqueByVersion(historique, installedVersionName = "1.0.0")
        assertEquals(listOf(false, false, true, false), sections.map { it.installed })
    }

    @Test
    fun `un APK debug ne marque aucune version installee`() {
        val sections = groupHistoriqueByVersion(historique, installedVersionName = "0.0.0-dev+b80f3c7")
        assertTrue(sections.none { it.installed })
    }

    @Test
    fun `pas de section Pas encore publie quand le dernier commit est tague`() {
        val sections = groupHistoriqueByVersion(historique.drop(1), installedVersionName = "1.1.0")
        assertEquals("1.1.0", sections.first().versionName)
        assertFalse(sections.any { it.versionName == null })
    }

    @Test
    fun `sans aucun tag tout est Pas encore publie`() {
        val sansTag = historique.map { it.copy(versionName = null) }
        val sections = groupHistoriqueByVersion(sansTag, installedVersionName = "0.0.0-dev+b80f3c7")
        assertEquals(1, sections.size)
        assertNull(sections[0].versionName)
        assertEquals(6, sections[0].commits.size)
    }

    @Test
    fun `libelles des en-tetes`() {
        val sections = groupHistoriqueByVersion(historique, installedVersionName = "1.1.0")
        assertEquals("Pas encore publié", formatContenuVersionHeader(sections[0]))
        assertEquals("Version 1.1.0 · 09/11/26", formatContenuVersionHeader(sections[1]))
    }

    @Test
    fun `historique vide donne aucune section`() {
        assertTrue(groupHistoriqueByVersion(emptyList(), installedVersionName = "1.1.0").isEmpty())
    }
}
