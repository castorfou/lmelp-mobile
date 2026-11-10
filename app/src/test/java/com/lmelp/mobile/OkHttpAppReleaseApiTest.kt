package com.lmelp.mobile

import com.lmelp.mobile.data.remote.HttpStatusException
import com.lmelp.mobile.data.remote.OkHttpAppReleaseApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException

/**
 * OkHttpAppReleaseApi (issue #162) : liste des releases GitHub, via MockWebServer.
 * La liste mélange releases d'appli (v*) et de données (data-v*) : le filtrage est fait
 * par selectLatestAppRelease, l'API se contente de tout parser fidèlement.
 */
class OkHttpAppReleaseApiTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private lateinit var server: MockWebServer

    @Before
    fun setup() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun teardown() {
        server.shutdown()
    }

    private val releasesJson = """
        [
          {
            "tag_name": "data-v9", "draft": false, "prerelease": false,
            "html_url": "https://github.com/castorfou/lmelp-mobile/releases/tag/data-v9",
            "assets": [
              { "name": "lmelp.db", "browser_download_url": "https://example.org/lmelp.db" },
              { "name": "metadata.json", "browser_download_url": "https://example.org/metadata.json" }
            ]
          },
          {
            "tag_name": "v1.2.0", "draft": false, "prerelease": false,
            "html_url": "https://github.com/castorfou/lmelp-mobile/releases/tag/v1.2.0",
            "assets": [
              {
                "name": "lmelp-mobile-v1.2.0.apk",
                "browser_download_url": "https://example.org/lmelp-mobile-v1.2.0.apk",
                "digest": "sha256:abcdef0123"
              }
            ]
          },
          {
            "tag_name": "v0.0.0-test", "draft": false, "prerelease": true,
            "html_url": "https://github.com/castorfou/lmelp-mobile/releases/tag/v0.0.0-test",
            "assets": [
              { "name": "lmelp-mobile-v0.0.0-test.apk", "browser_download_url": "https://example.org/t.apk", "digest": null }
            ]
          }
        ]
    """.trimIndent()

    @Test
    fun `fetchReleases parse tag, statut, APK et empreinte de chaque release`() = runBlocking {
        server.enqueue(MockResponse().setBody(releasesJson))
        val api = OkHttpAppReleaseApi(releasesUrl = server.url("/repos/castorfou/lmelp-mobile/releases").toString())

        val releases = api.fetchReleases()

        assertEquals(listOf("data-v9", "v1.2.0", "v0.0.0-test"), releases.map { it.tag })
        val data = releases[0]
        assertNull("une release de données n'a pas d'APK", data.apkUrl)
        val app = releases[1]
        assertEquals("https://example.org/lmelp-mobile-v1.2.0.apk", app.apkUrl)
        assertEquals("abcdef0123", app.apkSha256)
        assertFalse(app.prerelease)
        val test = releases[2]
        assertTrue(test.prerelease)
        assertNull("digest null → pas d'empreinte", test.apkSha256)
    }

    @Test
    fun `fetchReleases interroge la liste des releases et non releases-latest`() = runBlocking {
        server.enqueue(MockResponse().setBody("[]"))
        val api = OkHttpAppReleaseApi(releasesUrl = server.url("/repos/castorfou/lmelp-mobile/releases").toString())

        api.fetchReleases()

        val path = server.takeRequest().path!!
        assertTrue("path=$path", path.startsWith("/repos/castorfou/lmelp-mobile/releases?"))
    }

    @Test(expected = IOException::class)
    fun `fetchReleases leve une IOException sur une erreur HTTP (ex quota GitHub)`() {
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(403))
            val api = OkHttpAppReleaseApi(releasesUrl = server.url("/releases").toString())
            api.fetchReleases()
        }
    }

    @Test
    fun `downloadApk ecrit le corps de la reponse dans le fichier destination`() = runBlocking {
        server.enqueue(MockResponse().setBody("contenu de l'APK"))
        val api = OkHttpAppReleaseApi(releasesUrl = server.url("/releases").toString())
        val destination = tmpFolder.newFile("app.apk")

        api.downloadApk(server.url("/download/app.apk").toString(), destination)

        assertEquals("contenu de l'APK", destination.readText())
    }

    // --- Réessais sur les erreurs passagères de GitHub (issue #168, même règle que #156) ---

    private val recordedDelays = mutableListOf<Long>()

    private fun retryingApi() = OkHttpAppReleaseApi(
        releasesUrl = server.url("/releases").toString(),
        retryDelaysMs = listOf(1000L, 3000L),
        sleep = { recordedDelays += it }
    )

    @Test
    fun `une 504 passagere suivie d'une 200 donne un check APK reussi`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(504))
        server.enqueue(MockResponse().setBody(releasesJson))

        val releases = retryingApi().fetchReleases()

        assertEquals(3, releases.size)
        assertEquals(2, server.requestCount)
        assertEquals(listOf(1000L), recordedDelays)
    }

    @Test
    fun `trois 504 consecutives levent HttpStatusException 504 apres 3 tentatives`() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(504)) }

        try {
            retryingApi().fetchReleases()
            fail("HttpStatusException attendue")
        } catch (e: HttpStatusException) {
            assertEquals(504, e.code)
        }
        assertEquals(3, server.requestCount)
        assertEquals(listOf(1000L, 3000L), recordedDelays)
    }

    @Test
    fun `une 404 sur la liste des releases n'est pas reessayee`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        try {
            retryingApi().fetchReleases()
            fail("HttpStatusException attendue")
        } catch (e: HttpStatusException) {
            assertEquals(404, e.code)
        }
        assertEquals(1, server.requestCount)
        assertTrue(recordedDelays.isEmpty())
    }

    @Test
    fun `le telechargement de l'APK est reessaye sur une 503 passagere`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody("contenu de l'APK"))
        val destination = tmpFolder.newFile("retry.apk")

        retryingApi().downloadApk(server.url("/download/app.apk").toString(), destination)

        assertEquals("contenu de l'APK", destination.readText())
        assertEquals(2, server.requestCount)
        assertEquals(listOf(1000L), recordedDelays)
    }

    @Test
    fun `trois 502 sur le telechargement de l'APK levent HttpStatusException 502`() = runBlocking {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(502)) }
        val destination = tmpFolder.newFile("fail.apk")

        try {
            retryingApi().downloadApk(server.url("/download/app.apk").toString(), destination)
            fail("HttpStatusException attendue")
        } catch (e: HttpStatusException) {
            assertEquals(502, e.code)
        }
        assertEquals(3, server.requestCount)
    }
}
