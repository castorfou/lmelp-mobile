package com.lmelp.mobile

import com.lmelp.mobile.ui.about.parseChangelog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires pour la logique de parsing du changelog BuildConfig.
 *
 * Format du changelog (tel que généré par build.gradle.kts) :
 *   "hash|message|date\\nhash|message|date\\n..."
 * Les sauts de ligne réels sont stockés comme "\\n" littéraux dans BuildConfig.
 * Exemple : "754e07e|fix: renumérotation|09/03/26\\nabc1234|feat: fast scroll|08/03/26"
 */
class ChangelogParserTest {

    // Séparateur : vrai saut de ligne (la JVM interprète \n de BuildConfig à l'exécution)
    private val sep = "\n"

    @Test
    fun `chaine vide retourne liste vide`() {
        assertTrue(parseChangelog("").isEmpty())
    }

    @Test
    fun `chaine avec espaces seulement retourne liste vide`() {
        assertTrue(parseChangelog("   ${sep}  ").isEmpty())
    }

    @Test
    fun `ligne valide est parsee correctement`() {
        val result = parseChangelog("754e07e|fix: renumérotation|09/03/26")
        assertEquals(1, result.size)
        assertEquals("754e07e", result[0].hash)
        assertEquals("fix: renumérotation", result[0].message)
        assertEquals("09/03/26", result[0].date)
    }

    @Test
    fun `plusieurs lignes valides sont toutes parsees`() {
        val raw = "abc1234|feat: fast scroll|08/03/26${sep}def5678|fix: palmares|07/03/26"
        val result = parseChangelog(raw)
        assertEquals(2, result.size)
        assertEquals("abc1234", result[0].hash)
        assertEquals("def5678", result[1].hash)
    }

    @Test
    fun `separateur vide entre entrees est ignore`() {
        val raw = "abc1234|feat: fast scroll|08/03/26${sep}${sep}def5678|fix: palmares|07/03/26"
        val result = parseChangelog(raw)
        assertEquals(2, result.size)
    }

    @Test
    fun `ligne sans separateur pipe est ignoree`() {
        val raw = "ligne sans pipe${sep}abc1234|feat: ok|08/03/26"
        val result = parseChangelog(raw)
        assertEquals(1, result.size)
        assertEquals("abc1234", result[0].hash)
    }

    @Test
    fun `message avec pipe interne ne casse pas le parsing`() {
        val raw = "abc1234|feat: foo|bar|08/03/26"
        val result = parseChangelog(raw)
        assertEquals(1, result.size)
        assertEquals("feat: foo", result[0].message)
    }

    @Test
    fun `ordre des entrees est preserve`() {
        val raw = "aaa|premier|01/01/26${sep}bbb|deuxieme|02/01/26${sep}ccc|troisieme|03/01/26"
        val result = parseChangelog(raw)
        assertEquals("aaa", result[0].hash)
        assertEquals("bbb", result[1].hash)
        assertEquals("ccc", result[2].hash)
    }
}
