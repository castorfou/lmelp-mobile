package com.lmelp.mobile

import com.lmelp.mobile.ui.about.parseChangelog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Parsing de l'historique généré au build (issue #170).
 *
 * Une ligne par commit, champs séparés par U+001F (jamais présent dans un sujet de commit) :
 *   hash␟sujet␟date␟refs
 * où refs est le `%D` de git log restreint aux tags, ex. "tag: v1.1.0, tag: data-v9".
 */
class ChangelogParserTest {

    private fun line(hash: String, message: String, date: String, refs: String = "") =
        listOf(hash, message, date, refs).joinToString("\u001F")

    @Test
    fun `chaine vide retourne liste vide`() {
        assertTrue(parseChangelog("").isEmpty())
        assertTrue(parseChangelog("   \n  ").isEmpty())
    }

    @Test
    fun `ligne sans tag est parsee sans version`() {
        val result = parseChangelog(line("754e07e", "fix: renumérotation", "09/03/26"))
        assertEquals(1, result.size)
        assertEquals("754e07e", result[0].hash)
        assertEquals("fix: renumérotation", result[0].message)
        assertEquals("09/03/26", result[0].date)
        assertNull(result[0].versionName)
    }

    @Test
    fun `sujet contenant un pipe est conserve entier`() {
        val result = parseChangelog(line("abc1234", "feat: foo | bar", "08/03/26"))
        assertEquals("feat: foo | bar", result[0].message)
    }

    @Test
    fun `tag stable donne la version du commit`() {
        val result = parseChangelog(line("ab74d65", "Gradle 9.9.0", "09/11/26", "tag: v1.1.0"))
        assertEquals("1.1.0", result[0].versionName)
    }

    @Test
    fun `tags de donnees et pre-releases ne sont pas des versions`() {
        val raw = listOf(
            line("7309ee0", "Barre WhatsApp", "27/09/26", "tag: data-v9"),
            line("aaa1111", "Test workflow", "01/11/26", "tag: v0.0.0-test"),
            line("bbb2222", "Données", "01/11/26", "tag: data-latest, tag: data-test-116")
        ).joinToString("\n")
        assertTrue(parseChangelog(raw).all { it.versionName == null })
    }

    @Test
    fun `tag stable retrouve parmi d autres tags`() {
        val result = parseChangelog(line("ccc3333", "x", "01/11/26", "tag: data-v9, tag: v1.0.0"))
        assertEquals("1.0.0", result[0].versionName)
    }

    @Test
    fun `plusieurs tags stables sur un commit donnent la plus grande version`() {
        val result = parseChangelog(line("ddd4444", "x", "01/11/26", "tag: v1.10.0, tag: v1.9.0"))
        assertEquals("1.10.0", result[0].versionName)
        val inverse = parseChangelog(line("ddd4444", "x", "01/11/26", "tag: v1.9.0, tag: v1.10.0"))
        assertEquals("1.10.0", inverse[0].versionName)
    }
}
