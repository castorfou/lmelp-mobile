import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Règle tag `vX.Y.Z` → versionName / versionCode de l'APK (issue #161), étendue aux
 * préversions `X.Y.Z+N` (issue #173) : versionCode = (major*10000 + minor*100 + patch)*1000 + N.
 */
class AppVersionTest {

    @Test
    fun `tag simple`() {
        assertEquals(AppVersion("1.2.3", 10_203_000), AppVersion.fromTag("1.2.3"))
    }

    @Test
    fun `le prefixe v est retire du versionName`() {
        assertEquals(AppVersion("1.2.3", 10_203_000), AppVersion.fromTag("v1.2.3"))
    }

    @Test
    fun `exemples de l'issue 173`() {
        assertEquals(10_200_000, AppVersion.fromTag("v1.2.0").code)
        assertEquals(AppVersion("1.2.0+3", 10_200_003), AppVersion.fromTag("1.2.0+3"))
        assertEquals(10_201_000, AppVersion.fromTag("v1.2.1").code)
    }

    @Test
    fun `preversion N jusqu'a 999, au-dela refuse`() {
        assertEquals(10_200_999, AppVersion.fromTag("1.2.0+999").code)
        assertThrows(IllegalArgumentException::class.java) { AppVersion.fromTag("1.2.0+1000") }
    }

    @Test
    fun `pre-release garde son suffixe, code calcule sur X Y Z`() {
        assertEquals(AppVersion("0.0.0-test", 1), AppVersion.fromTag("v0.0.0-test"))
        assertEquals(AppVersion("1.4.0-rc.1", 10_400_000), AppVersion.fromTag("v1.4.0-rc.1"))
    }

    @Test
    fun `versionCode strictement croissant entre versions et preversions successives`() {
        val ordre = listOf(
            "v0.1.0", "v1.2.0", "1.2.0+1", "1.2.0+999", "v1.2.1", "v1.2.3", "v1.2.99",
            "v1.3.0", "v1.99.99", "v2.0.0", "v10.0.0"
        )
        val codes = ordre.map { AppVersion.fromTag(it).code }
        codes.zipWithNext().forEach { (a, b) -> assertTrue("$codes non croissant", b > a) }
    }

    @Test
    fun `nouveau versionCode toujours superieur a l'ancien de la 1 2 0 installee`() {
        // Ancienne formule (#161) : 1.2.0 → 10200
        assertTrue(AppVersion.fromTag("v1.2.0").code > 10_200)
    }

    @Test
    fun `v0 0 0 donne le versionCode minimal 1`() {
        assertEquals(1, AppVersion.fromTag("v0.0.0").code)
    }

    @Test
    fun `formats invalides refuses`() {
        listOf("", "1.2", "abc", "1.2.3.4", "v1.2.x", "1.2.3+build", "1.2.3+", "1.2.3+-1").forEach { tag ->
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
        assertEquals(2_100_000_000, AppVersion.fromTag("210.0.0").code)
        assertThrows(IllegalArgumentException::class.java) { AppVersion.fromTag("210.0.0+1") }
        assertThrows(IllegalArgumentException::class.java) { AppVersion.fromTag("211.0.0") }
    }

    @Test
    fun `build de dev reconnaissable comme non publie`() {
        assertEquals(AppVersion("0.0.0-dev+abc1234", 1), AppVersion.dev("abc1234"))
    }

    @Test
    fun `resolve utilise la propriete si fournie, sinon dev`() {
        assertEquals(AppVersion("1.2.3", 10_203_000), AppVersion.resolve("1.2.3", "abc1234"))
        assertEquals(AppVersion("1.2.0+3", 10_200_003), AppVersion.resolve("1.2.0+3", "abc1234"))
        assertEquals(AppVersion.dev("abc1234"), AppVersion.resolve(null, "abc1234"))
        assertEquals(AppVersion.dev("abc1234"), AppVersion.resolve("  ", "abc1234"))
    }
}
