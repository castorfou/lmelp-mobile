package com.lmelp.mobile

import com.lmelp.mobile.data.model.AppRelease
import com.lmelp.mobile.data.update.APP_PREVIEW_TAG
import com.lmelp.mobile.data.update.appVersionCodeFromPreviewName
import com.lmelp.mobile.data.update.appVersionCodeFromTag
import com.lmelp.mobile.data.update.isDevVersion
import com.lmelp.mobile.data.update.isPreviewVersion
import com.lmelp.mobile.data.update.selectLatestAppRelease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comparaison de versions de l'appli (issue #162) : le versionCode d'une release est
 * recalculé depuis son tag avec la même formule que buildSrc/AppVersion.kt (issue #161),
 * `(major*10000 + minor*100 + patch)*1000 + N` depuis les préversions (issue #173).
 */
class AppReleaseVersionTest {

    @Test
    fun `un tag vX_Y_Z donne (major*10000 + minor*100 + patch)*1000`() {
        assertEquals(10_203_000, appVersionCodeFromTag("v1.2.3"))
        assertEquals(100_000, appVersionCodeFromTag("v0.1.0"))
        assertEquals(129_999_000, appVersionCodeFromTag("v12.99.99"))
    }

    @Test
    fun `exemples de l'issue 173, identiques a buildSrc AppVersion`() {
        assertEquals(10_200_000, appVersionCodeFromTag("v1.2.0"))
        assertEquals(10_200_003, appVersionCodeFromPreviewName("1.2.0+3"))
        assertEquals(10_201_000, appVersionCodeFromTag("v1.2.1"))
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
        assertNull(appVersionCodeFromTag("app-preview"))
        assertNull("une préversion ne se lit pas dans un tag", appVersionCodeFromTag("v1.2.0+3"))
    }

    @Test
    fun `nom de preversion X_Y_Z+N, N au plus 999`() {
        assertEquals(10_200_999, appVersionCodeFromPreviewName("1.2.0+999"))
        assertNull(appVersionCodeFromPreviewName("1.2.0+1000"))
        assertNull(appVersionCodeFromPreviewName("1.2.0"))
        assertNull(appVersionCodeFromPreviewName("v1.2.0+3"))
        assertNull(appVersionCodeFromPreviewName("1.2.0+abc"))
        assertNull(appVersionCodeFromPreviewName("1.100.0+3"))
        assertNull(appVersionCodeFromPreviewName(""))
    }

    @Test
    fun `une version de dev est reconnue, debug compris`() {
        assertTrue(isDevVersion("0.0.0-dev+abc1234"))
        assertTrue(isDevVersion("0.0.0-dev+abc1234-debug"))
        assertFalse(isDevVersion("1.2.3"))
        assertFalse(isDevVersion("0.0.1-debug"))
    }

    @Test
    fun `une preversion n'est pas une version de dev`() {
        assertFalse(isDevVersion("1.2.0+3"))
        assertFalse(isDevVersion("1.2.0+3-debug"))
    }

    @Test
    fun `une preversion installee est reconnue, debug compris`() {
        assertTrue(isPreviewVersion("1.2.0+3"))
        assertTrue(isPreviewVersion("1.2.0+3-debug"))
        assertFalse(isPreviewVersion("1.2.0"))
        assertFalse(isPreviewVersion("1.2.0-debug"))
        assertFalse(isPreviewVersion("0.0.0-dev+abc1234"))
        assertFalse(isPreviewVersion("0.0.0-dev+1234567"))
    }

    private fun release(
        tag: String,
        prerelease: Boolean = false,
        draft: Boolean = false,
        apkUrl: String? = "https://example.org/$tag.apk",
        name: String = ""
    ) = AppRelease(
        tag = tag,
        name = name,
        prerelease = prerelease,
        draft = draft,
        apkUrl = apkUrl,
        apkSha256 = null,
        htmlUrl = "https://github.com/castorfou/lmelp-mobile/releases/tag/$tag"
    )

    private fun preview(name: String) = release(APP_PREVIEW_TAG, prerelease = true, name = name)

    @Test
    fun `selectLatestAppRelease garde la release v la plus recente au-dessus de la version installee`() {
        val releases = listOf(release("v1.0.0"), release("v1.2.0"), release("v1.1.5"))
        assertEquals("v1.2.0", selectLatestAppRelease(releases, installedVersionCode = 10_000_000)?.tag)
    }

    @Test
    fun `selectLatestAppRelease ignore pre-releases, brouillons, data-v et releases sans APK`() {
        val releases = listOf(
            release("data-v9"),
            release("v2.0.0", prerelease = true),
            release("v3.0.0", draft = true),
            release("v4.0.0", apkUrl = null),
            release("v0.0.0-test", prerelease = true),
            release("v1.1.0")
        )
        assertEquals("v1.1.0", selectLatestAppRelease(releases, installedVersionCode = 10_000_000)?.tag)
    }

    @Test
    fun `selectLatestAppRelease ne signale rien si la version installee est a jour ou plus recente`() {
        val releases = listOf(release("v1.0.0"), release("v1.2.0"))
        assertNull(selectLatestAppRelease(releases, installedVersionCode = 10_200_000))
        assertNull(selectLatestAppRelease(releases, installedVersionCode = 10_300_000))
        assertNull(selectLatestAppRelease(emptyList(), installedVersionCode = 1))
    }

    @Test
    fun `selectLatestAppRelease propose la preversion app-preview au-dessus de la version installee`() {
        val releases = listOf(release("v1.2.0"), preview("1.2.0+3"), release("v0.0.0-test", prerelease = true))
        assertEquals(APP_PREVIEW_TAG, selectLatestAppRelease(releases, installedVersionCode = 10_200_000)?.tag)
    }

    @Test
    fun `selectLatestAppRelease prefere la version suivante a une preversion plus ancienne`() {
        val releases = listOf(preview("1.2.0+3"), release("v1.2.1"))
        assertEquals("v1.2.1", selectLatestAppRelease(releases, installedVersionCode = 10_200_003)?.tag)
        assertEquals("v1.2.1", selectLatestAppRelease(releases, installedVersionCode = 10_200_000)?.tag)
    }

    @Test
    fun `selectLatestAppRelease ignore une app-preview sans APK, en brouillon ou au nom illisible`() {
        val releases = listOf(
            release(APP_PREVIEW_TAG, prerelease = true, name = "1.2.0+3", apkUrl = null),
            release(APP_PREVIEW_TAG, prerelease = true, name = "1.2.0+4", draft = true),
            preview("lmelp-mobile preview")
        )
        assertNull(selectLatestAppRelease(releases, installedVersionCode = 10_200_000))
    }

    @Test
    fun `AppRelease expose versionName et versionCode depuis le tag`() {
        val r = release("v1.2.3")
        assertEquals("1.2.3", r.versionName)
        assertEquals(10_203_000, r.versionCode)
        assertFalse(r.isPreview)
    }

    @Test
    fun `AppRelease app-preview lit sa version dans le nom de la release`() {
        val r = preview("1.2.0+3")
        assertTrue(r.isPreview)
        assertEquals("1.2.0+3", r.versionName)
        assertEquals(10_200_003, r.versionCode)
    }
}
