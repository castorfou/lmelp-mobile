package com.lmelp.mobile

import com.lmelp.mobile.data.remote.HttpStatusException
import com.lmelp.mobile.data.remote.OkHttpGitHubReleaseApi
import com.lmelp.mobile.data.remote.ReleaseNotFoundException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests unitaires pour OkHttpGitHubReleaseApi (issue #118), via MockWebServer
 * pour ne jamais faire de vrai appel réseau dans les tests.
 *
 * Le tag de release résolu est data-v{N} où N est le PRAGMA user_version du fichier
 * DB local fourni (issue #132) — plus de tag fixe data-latest.
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

    private val magicHeaderBytes = byteArrayOf(
        'S'.code.toByte(), 'Q'.code.toByte(), 'L'.code.toByte(), 'i'.code.toByte(),
        't'.code.toByte(), 'e'.code.toByte(), ' '.code.toByte(), 'f'.code.toByte(),
        'o'.code.toByte(), 'r'.code.toByte(), 'm'.code.toByte(), 'a'.code.toByte(),
        't'.code.toByte(), ' '.code.toByte(), '3'.code.toByte(), 0
    )

    private fun localDbFile(userVersion: Int): File {
        val header = ByteArray(100)
        magicHeaderBytes.copyInto(header, 0)
        header[60] = (userVersion ushr 24 and 0xFF).toByte()
        header[61] = (userVersion ushr 16 and 0xFF).toByte()
        header[62] = (userVersion ushr 8 and 0xFF).toByte()
        header[63] = (userVersion and 0xFF).toByte()
        return tmpFolder.newFile("local_v$userVersion.db").apply { writeBytes(header) }
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
    fun `fetchMetadata resout le tag data-v suivant le user_version du fichier local`() = runBlocking {
        val baseUrl = server.url("").toString().removeSuffix("/")
        server.enqueue(MockResponse().setBody(releaseTagsResponse.format(baseUrl, baseUrl)))
        server.enqueue(MockResponse().setBody(metadataJson))

        val api = OkHttpGitHubReleaseApi(
            localDbFile = localDbFile(userVersion = 8),
            apiBaseUrl = server.url("/repos/castorfou/lmelp-mobile/releases/tags").toString()
        )
        val metadata = api.fetchMetadata()

        val request = server.takeRequest()
        assertTrue("le tag résolu doit être data-v8, path=${request.path}", request.path!!.endsWith("/tags/data-v8"))
        assertEquals("1787168148", metadata.exportVersion)
    }

    @Test
    fun `deux fichiers locaux avec des user_version differents resolvent des tags differents`() = runBlocking {
        val baseUrl = server.url("").toString().removeSuffix("/")
        server.enqueue(MockResponse().setBody(releaseTagsResponse.format(baseUrl, baseUrl)))
        server.enqueue(MockResponse().setBody(metadataJson))

        val api = OkHttpGitHubReleaseApi(
            localDbFile = localDbFile(userVersion = 7),
            apiBaseUrl = server.url("/repos/castorfou/lmelp-mobile/releases/tags").toString()
        )
        api.fetchMetadata()

        val request = server.takeRequest()
        assertTrue("le tag résolu doit être data-v7, path=${request.path}", request.path!!.endsWith("/tags/data-v7"))
    }

    @Test
    fun `downloadDatabase ecrit le corps de la reponse dans le fichier destination`() = runBlocking {
        val baseUrl = server.url("").toString().removeSuffix("/")
        server.enqueue(MockResponse().setBody(releaseTagsResponse.format(baseUrl, baseUrl)))
        server.enqueue(MockResponse().setBody("contenu binaire de la base"))

        val api = OkHttpGitHubReleaseApi(
            localDbFile = localDbFile(userVersion = 8),
            apiBaseUrl = server.url("/repos/castorfou/lmelp-mobile/releases/tags").toString()
        )
        val destination = tmpFolder.newFile("downloaded.db")
        api.downloadDatabase(destination)

        assertEquals("contenu binaire de la base", destination.readText())
    }
    // --- Réessais sur les erreurs passagères de GitHub (issue #156) ---

    private val recordedDelays = mutableListOf<Long>()

    private fun retryingApi(userVersion: Int = 8) = OkHttpGitHubReleaseApi(
        localDbFile = localDbFile(userVersion),
        apiBaseUrl = server.url("/repos/castorfou/lmelp-mobile/releases/tags").toString(),
        retryDelaysMs = listOf(1000L, 3000L),
        sleep = { recordedDelays += it }
    )

    private fun enqueueRelease() {
        val baseUrl = server.url("").toString().removeSuffix("/")
        server.enqueue(MockResponse().setBody(releaseTagsResponse.format(baseUrl, baseUrl)))
    }

    @Test
    fun `une 504 passagere sur metadata json suivie d'une 200 donne un check reussi`() = runBlocking {
        enqueueRelease()
        server.enqueue(MockResponse().setResponseCode(504))
        server.enqueue(MockResponse().setBody(metadataJson))

        val metadata = retryingApi().fetchMetadata()

        assertEquals("1787168148", metadata.exportVersion)
        assertEquals(3, server.requestCount)
        assertEquals(listOf(1000L), recordedDelays)
    }

    @Test
    fun `trois 504 consecutives levent HttpStatusException 504 apres 3 tentatives`() = runBlocking {
        enqueueRelease()
        repeat(3) { server.enqueue(MockResponse().setResponseCode(504)) }

        try {
            retryingApi().fetchMetadata()
            fail("HttpStatusException attendue")
        } catch (e: HttpStatusException) {
            assertEquals(504, e.code)
        }
        assertEquals("1 requête API + 3 tentatives sur metadata.json", 4, server.requestCount)
        assertEquals(listOf(1000L, 3000L), recordedDelays)
    }

    @Test
    fun `une 503 sur la requete API GitHub est aussi reessayee`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        enqueueRelease()
        server.enqueue(MockResponse().setBody(metadataJson))

        retryingApi().fetchMetadata()

        assertEquals(3, server.requestCount)
    }

    @Test
    fun `une 404 sur la release data-v N leve ReleaseNotFoundException sans reessai`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(404))

        try {
            retryingApi().fetchMetadata()
            fail("ReleaseNotFoundException attendue")
        } catch (e: ReleaseNotFoundException) {
            assertEquals(404, e.code)
        }
        assertEquals(1, server.requestCount)
        assertTrue(recordedDelays.isEmpty())
    }

    @Test
    fun `une autre erreur 4xx n'est pas reessayee`() = runBlocking {
        enqueueRelease()
        server.enqueue(MockResponse().setResponseCode(403))

        try {
            retryingApi().fetchMetadata()
            fail("HttpStatusException attendue")
        } catch (e: HttpStatusException) {
            assertEquals(403, e.code)
        }
        assertEquals(2, server.requestCount)
    }

    @Test
    fun `downloadDatabase reessaie une 502 sur lmelp db`() = runBlocking {
        enqueueRelease()
        server.enqueue(MockResponse().setResponseCode(502))
        server.enqueue(MockResponse().setBody("contenu binaire de la base"))

        val destination = tmpFolder.newFile("downloaded_retry.db")
        retryingApi().downloadDatabase(destination)

        assertEquals("contenu binaire de la base", destination.readText())
        assertEquals(3, server.requestCount)
    }
}
