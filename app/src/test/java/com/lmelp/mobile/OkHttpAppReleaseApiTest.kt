package com.lmelp.mobile

import com.lmelp.mobile.data.remote.OkHttpAppReleaseApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
}
