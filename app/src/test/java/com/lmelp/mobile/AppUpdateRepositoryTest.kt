package com.lmelp.mobile

import com.lmelp.mobile.data.model.AppRelease
import com.lmelp.mobile.data.model.AppUpdateCheckResult
import com.lmelp.mobile.data.remote.AppReleaseApi
import com.lmelp.mobile.data.remote.HttpStatusException
import com.lmelp.mobile.data.repository.AppUpdateRepository
import com.lmelp.mobile.data.update.Sha256Verifier
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * AppUpdateRepository (issue #162) : détection et téléchargement d'une nouvelle version de l'appli.
 * Fake d'AppReleaseApi plutôt que mock, pour compter les appels réseau.
 */
class AppUpdateRepositoryTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private class FakeApi(
        private val releases: List<AppRelease> = emptyList(),
        private val failure: Exception? = null,
        private val apkContent: String = "apk"
    ) : AppReleaseApi {
        var fetchCalls = 0
        override suspend fun fetchReleases(): List<AppRelease> {
            fetchCalls++
            failure?.let { throw it }
            return releases
        }

        override suspend fun downloadApk(url: String, destination: File) {
            destination.writeText(apkContent)
        }
    }

    private fun release(tag: String, sha256: String? = null) = AppRelease(
        tag = tag,
        prerelease = false,
        draft = false,
        apkUrl = "https://example.org/lmelp-mobile-$tag.apk",
        apkSha256 = sha256,
        htmlUrl = "https://github.com/castorfou/lmelp-mobile/releases/tag/$tag"
    )

    @Test
    fun `une version de dev ne verifie jamais et n'appelle pas le reseau`() = runBlocking {
        val api = FakeApi(releases = listOf(release("v9.0.0")))
        val repo = AppUpdateRepository(api, installedVersionCode = 1, installedVersionName = "0.0.0-dev+abc-debug")

        assertEquals(AppUpdateCheckResult.UpToDate, repo.checkForUpdate())
        assertEquals(0, api.fetchCalls)
    }

    @Test
    fun `une release v plus recente est signalee`() = runBlocking {
        val api = FakeApi(releases = listOf(release("data-v9"), release("v1.3.0"), release("v1.2.0")))
        val repo = AppUpdateRepository(api, installedVersionCode = 10200, installedVersionName = "1.2.0")

        val result = repo.checkForUpdate()

        assertTrue(result is AppUpdateCheckResult.UpdateAvailable)
        assertEquals("v1.3.0", (result as AppUpdateCheckResult.UpdateAvailable).release.tag)
    }

    @Test
    fun `aucune release plus recente donne UpToDate`() = runBlocking {
        val repo = AppUpdateRepository(
            FakeApi(releases = listOf(release("v1.2.0"))),
            installedVersionCode = 10200,
            installedVersionName = "1.2.0"
        )
        assertEquals(AppUpdateCheckResult.UpToDate, repo.checkForUpdate())
    }

    @Test
    fun `une erreur reseau donne Error sans propager l'exception`() = runBlocking {
        val repo = AppUpdateRepository(
            FakeApi(failure = RuntimeException("pas de réseau")),
            installedVersionCode = 10200,
            installedVersionName = "1.2.0"
        )
        val result = repo.checkForUpdate()
        assertEquals(AppUpdateCheckResult.Error("Erreur : pas de réseau"), result)
    }

    @Test
    fun `GitHub indisponible donne un message clair sans code HTTP brut`() = runBlocking {
        val repo = AppUpdateRepository(
            FakeApi(failure = HttpStatusException(504, "Échec de la requête GitHub Releases : HTTP 504")),
            installedVersionCode = 10200,
            installedVersionName = "1.2.0"
        )
        val result = repo.checkForUpdate() as AppUpdateCheckResult.Error
        assertTrue(result.message, result.message.contains("momentanément indisponible"))
        assertFalse(result.message, result.message.contains("HTTP 504"))
    }

    @Test
    fun `checkForUpdateAndCache met le resultat a disposition dans lastCheckResult`() = runBlocking {
        val repo = AppUpdateRepository(
            FakeApi(releases = listOf(release("v1.3.0"))),
            installedVersionCode = 10200,
            installedVersionName = "1.2.0"
        )
        repo.checkForUpdateAndCache()
        assertTrue(repo.lastCheckResult.value is AppUpdateCheckResult.UpdateAvailable)
    }

    @Test
    fun `downloadApk ecrit l'APK dans le dossier updates sous le nom de la release`() = runBlocking {
        val repo = AppUpdateRepository(FakeApi(apkContent = "binaire"), 10200, "1.2.0")
        val dir = tmpFolder.newFolder("cache")

        val file = repo.downloadApk(release("v1.3.0"), dir)

        assertEquals(File(File(dir, "updates"), "lmelp-mobile-v1.3.0.apk"), file)
        assertEquals("binaire", file.readText())
    }

    @Test
    fun `downloadApk accepte un APK dont l'empreinte correspond`() = runBlocking {
        val reference = tmpFolder.newFile("ref").apply { writeText("binaire") }
        val repo = AppUpdateRepository(FakeApi(apkContent = "binaire"), 10200, "1.2.0")

        val file = repo.downloadApk(release("v1.3.0", sha256 = Sha256Verifier.sha256(reference)), tmpFolder.newFolder())

        assertTrue(file.exists())
    }

    @Test
    fun `downloadApk rejette et supprime un APK dont l'empreinte ne correspond pas`() {
        val repo = AppUpdateRepository(FakeApi(apkContent = "corrompu"), 10200, "1.2.0")
        val dir = tmpFolder.newFolder()
        val error = runCatching {
            runBlocking { repo.downloadApk(release("v1.3.0", sha256 = "0".repeat(64)), dir) }
        }.exceptionOrNull()

        assertTrue("une exception est attendue", error != null)
        assertFalse(File(File(dir, "updates"), "lmelp-mobile-v1.3.0.apk").exists())
    }
}
