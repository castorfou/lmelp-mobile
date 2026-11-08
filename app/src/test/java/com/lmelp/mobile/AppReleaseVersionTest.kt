package com.lmelp.mobile

import com.lmelp.mobile.data.model.AppRelease
import com.lmelp.mobile.data.update.appVersionCodeFromTag
import com.lmelp.mobile.data.update.isDevVersion
import com.lmelp.mobile.data.update.selectLatestAppRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comparaison de versions de l'appli (issue #162) : le versionCode d'une release est
 * recalculé depuis son tag avec la même formule que buildSrc/AppVersion.kt (issue #161).
 */
class AppReleaseVersionTest {

    @Test
    fun `un tag vX_Y_Z donne major*10000 + minor*100 + patch`() {
        assertEquals(10203, appVersionCodeFromTag("v1.2.3"))
        assertEquals(100, appVersionCodeFromTag("v0.1.0"))
        assertEquals(129999, appVersionCodeFromTag("v12.99.99"))
    }

    @Test
    fun `les tags hors release d'appli ne donnent pas de version`() {
        assertNull(appVersionCodeFromTag("data-v9"))
        assertNull(appVersionCodeFromTag("data-latest"))
        assertNull(appVersionCodeFromTag("v0.0.0-test"))
        assertNull(appVersionCodeFromTag("v1.2.3-rc.1"))
        assertNull(appVersionCodeFromTag("1.2.3"))
        assertNull(appVersionCodeFromTag("v1.2"))
        assertNull(appVersionCodeFromTag("v1.100.0"))
        assertNull(appVersionCodeFromTag("v1.0.100"))
    }

    @Test
    fun `une version de dev est reconnue, debug compris`() {
        assertTrue(isDevVersion("0.0.0-dev+abc1234"))
        assertTrue(isDevVersion("0.0.0-dev+abc1234-debug"))
        assertFalse(isDevVersion("1.2.3"))
        assertFalse(isDevVersion("0.0.1-debug"))
    }

    private fun release(
        tag: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
        apkUrl: String? = "https://example.org/$tag.apk"
    ) = AppRelease(
        tag = tag,
        prerelease = prerelease,
        draft = draft,
        apkUrl = apkUrl,
        apkSha256 = null,
        htmlUrl = "https://github.com/castorfou/lmelp-mobile/releases/tag/$tag"
    )

    @Test
    fun `selectLatestAppRelease garde la release v la plus recente au-dessus de la version installee`() {
        val releases = listOf(release("v1.0.0"), release("v1.2.0"), release("v1.1.5"))
        assertEquals("v1.2.0", selectLatestAppRelease(releases, installedVersionCode = 10000)?.tag)
    }

    @Test
    fun `selectLatestAppRelease ignore pre-releases, brouillons, data-v et releases sans APK`() {
        val releases = listOf(
            release("data-v9"),
            release("v2.0.0", prerelease = true),
            release("v3.0.0", draft = true),
            release("v4.0.0", apkUrl = null),
            release("v0.0.0-test"),
            release("v1.1.0")
        )
        assertEquals("v1.1.0", selectLatestAppRelease(releases, installedVersionCode = 10000)?.tag)
    }

    @Test
    fun `selectLatestAppRelease ne signale rien si la version installee est a jour ou plus recente`() {
        val releases = listOf(release("v1.0.0"), release("v1.2.0"))
        assertNull(selectLatestAppRelease(releases, installedVersionCode = 10200))
        assertNull(selectLatestAppRelease(releases, installedVersionCode = 10300))
        assertNull(selectLatestAppRelease(emptyList(), installedVersionCode = 1))
    }

    @Test
    fun `AppRelease expose versionName et versionCode depuis le tag`() {
        val r = release("v1.2.3")
        assertEquals("1.2.3", r.versionName)
        assertEquals(10203, r.versionCode)
    }
}
