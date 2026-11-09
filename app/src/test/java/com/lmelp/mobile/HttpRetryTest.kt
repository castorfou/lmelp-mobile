package com.lmelp.mobile

import com.lmelp.mobile.data.remote.HttpStatusException
import com.lmelp.mobile.data.remote.ReleaseNotFoundException
import com.lmelp.mobile.data.remote.updateErrorMessage
import com.lmelp.mobile.data.remote.withRetry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * Réessais sur les erreurs passagères et messages affichés à l'utilisateur (issue #156).
 */
class HttpRetryTest {

    private val delays = mutableListOf<Long>()

    private suspend fun <T> retry(block: () -> T): T =
        withRetry(delaysMs = listOf(1000L, 3000L), sleep = { delays += it }, block = block)

    @Test
    fun `un timeout reseau est reessaye`() = runBlocking {
        var calls = 0
        val result = retry {
            calls++
            if (calls == 1) throw SocketTimeoutException("timeout")
            "ok"
        }
        assertEquals("ok", result)
        assertEquals(2, calls)
        assertEquals(listOf(1000L), delays)
    }

    @Test
    fun `pas de reessai sans reseau`() = runBlocking {
        var calls = 0
        try {
            retry<String> { calls++; throw UnknownHostException("api.github.com") }
            fail("UnknownHostException attendue")
        } catch (e: UnknownHostException) {
            // attendu
        }
        assertEquals(1, calls)
        assertTrue(delays.isEmpty())
    }

    @Test
    fun `pas de reessai sur une HTTP 500`() = runBlocking {
        var calls = 0
        try {
            retry<String> { calls++; throw HttpStatusException(500, "HTTP 500") }
            fail("HttpStatusException attendue")
        } catch (e: HttpStatusException) {
            assertEquals(500, e.code)
        }
        assertEquals(1, calls)
    }

    @Test
    fun `une 5xx donne le message momentanement indisponible sans code brut`() {
        val message = updateErrorMessage(HttpStatusException(504, "Échec du téléchargement de metadata.json : HTTP 504"))
        assertTrue(message, message.contains("momentanément indisponible"))
        assertFalse(message, message.contains("504"))
        assertFalse(message, message.startsWith("Erreur"))
    }

    @Test
    fun `un timeout ou une connexion refusee donnent aussi le message momentanement indisponible`() {
        assertTrue(updateErrorMessage(SocketTimeoutException("timeout")).contains("momentanément indisponible"))
        assertTrue(updateErrorMessage(ConnectException("refused")).contains("momentanément indisponible"))
    }

    @Test
    fun `sans reseau le message le dit`() {
        assertEquals("Pas de connexion Internet.", updateErrorMessage(UnknownHostException("api.github.com")))
    }

    @Test
    fun `une release data-v N absente donne un message neutre`() {
        assertEquals(
            "Aucune mise à jour disponible pour cette version de l'application.",
            updateErrorMessage(ReleaseNotFoundException("data-v9"))
        )
    }

    @Test
    fun `les autres erreurs gardent le message d'origine et le code HTTP`() {
        assertEquals(
            "Erreur : Échec de la requête GitHub Releases : HTTP 403",
            updateErrorMessage(HttpStatusException(403, "Échec de la requête GitHub Releases : HTTP 403"))
        )
        assertEquals("Erreur : boom", updateErrorMessage(IOException("boom")))
        assertEquals("Erreur : IllegalStateException", updateErrorMessage(IllegalStateException()))
    }
}
