import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Règle tag `vX.Y.Z` → versionName / versionCode de l'APK (issue #161). */
class AppVersionTest {

    @Test
    fun `tag simple`() {
        assertEquals(AppVersion("1.2.3", 10203), AppVersion.fromTag("1.2.3"))
    }

    @Test
    fun `le prefixe v est retire du versionName`() {
        assertEquals(AppVersion("1.2.3", 10203), AppVersion.fromTag("v1.2.3"))
    }

    @Test
    fun `pre-release garde son suffixe, code calcule sur X Y Z`() {
        assertEquals(AppVersion("0.0.0-test", 1), AppVersion.fromTag("v0.0.0-test"))
        assertEquals(AppVersion("1.4.0-rc.1", 10400), AppVersion.fromTag("v1.4.0-rc.1"))
    }

    @Test
    fun `versionCode strictement croissant entre versions successives`() {
        val ordre = listOf("v0.1.0", "v1.2.2", "v1.2.3", "v1.2.99", "v1.3.0", "v1.99.99", "v2.0.0", "v10.0.0")
        val codes = ordre.map { AppVersion.fromTag(it).code }
        codes.zipWithNext().forEach { (a, b) -> assertTrue("$codes non croissant", b > a) }
    }

    @Test
    fun `v0 0 0 donne le versionCode minimal 1`() {
        assertEquals(1, AppVersion.fromTag("v0.0.0").code)
    }

    @Test
    fun `formats invalides refuses`() {
        listOf("", "1.2", "abc", "1.2.3.4", "v1.2.x", "1.2.3+build").forEach { tag ->
            assertThrows("« $tag » devrait être refusé", IllegalArgumentException::class.java) {
                AppVersion.fromTag(tag)
            }
        }
    }

    @Test
    fun `minor ou patch superieur a 99 refuse`() {
        assertThrows(IllegalArgumentException::class.java) { AppVersion.fromTag("1.100.0") }
        assertThrows(IllegalArgumentException::class.java) { AppVersion.fromTag("1.0.100") }
    }

    @Test
    fun `versionCode au-dela de la limite Android refuse`() {
        assertThrows(IllegalArgumentException::class.java) { AppVersion.fromTag("210001.0.0") }
    }

    @Test
    fun `build de dev reconnaissable comme non publie`() {
        assertEquals(AppVersion("0.0.0-dev+abc1234", 1), AppVersion.dev("abc1234"))
    }

    @Test
    fun `resolve utilise la propriete si fournie, sinon dev`() {
        assertEquals(AppVersion("1.2.3", 10203), AppVersion.resolve("1.2.3", "abc1234"))
        assertEquals(AppVersion.dev("abc1234"), AppVersion.resolve(null, "abc1234"))
        assertEquals(AppVersion.dev("abc1234"), AppVersion.resolve("  ", "abc1234"))
    }
}
