package com.lmelp.mobile

import com.lmelp.mobile.data.remote.OkHttpGitHubReleaseApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tests unitaires pour OkHttpGitHubReleaseApi (issue #118), via MockWebServer
 * pour ne jamais faire de vrai appel réseau dans les tests.
 */
class OkHttpGitHubReleaseApiTest {

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

    private val releaseTagsResponse = """
        {
          "assets": [
            { "name": "lmelp.db", "browser_download_url": "%s/download/lmelp.db" },
            { "name": "metadata.json", "browser_download_url": "%s/download/metadata.json" }
          ]
        }
    """.trimIndent()

    private val metadataJson = """
        {
          "schema_version": 1,
          "export_date": "2026-08-19",
          "export_datetime": "2026-08-19T19:35:48.818072",
          "export_version": "1787168148",
          "nb_emissions": 180,
          "nb_livres": 1688,
          "nb_avis": 4275,
          "file_size_bytes": 4616192,
          "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
          "filename": "lmelp.db"
        }
    """.trimIndent()

    @Test
    fun `fetchMetadata resout le browser_download_url de metadata json et parse son contenu`() = runBlocking {
        val baseUrl = server.url("").toString().removeSuffix("/")
        server.enqueue(MockResponse().setBody(releaseTagsResponse.format(baseUrl, baseUrl)))
        server.enqueue(MockResponse().setBody(metadataJson))

        val api = OkHttpGitHubReleaseApi(releaseApiBaseUrl = server.url("/repos/castorfou/lmelp-mobile/releases/tags/data-latest").toString())
        val metadata = api.fetchMetadata()

        assertEquals("1787168148", metadata.exportVersion)
        assertEquals(180, metadata.nbEmissions)
    }

    @Test
    fun `downloadDatabase ecrit le corps de la reponse dans le fichier destination`() = runBlocking {
        val baseUrl = server.url("").toString().removeSuffix("/")
        server.enqueue(MockResponse().setBody(releaseTagsResponse.format(baseUrl, baseUrl)))
        server.enqueue(MockResponse().setBody("contenu binaire de la base"))

        val api = OkHttpGitHubReleaseApi(releaseApiBaseUrl = server.url("/repos/castorfou/lmelp-mobile/releases/tags/data-latest").toString())
        val destination = tmpFolder.newFile("downloaded.db")
        api.downloadDatabase(destination)

        assertEquals("contenu binaire de la base", destination.readText())
    }
}
