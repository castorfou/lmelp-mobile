package com.lmelp.mobile.data.remote

import kotlinx.coroutines.delay
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/** Réponse HTTP non 2xx de GitHub, avec son code (issue #156). */
open class HttpStatusException(val code: Int, message: String) : IOException(message)

/**
 * Aucune release `data-v{N}` pour le schéma Room local (404 sur `releases/tags/data-v{N}`).
 * Comportement voulu après un changement de schéma (issue #132) : jamais réessayé.
 */
class ReleaseNotFoundException(tag: String) :
    HttpStatusException(404, "Aucune release $tag sur GitHub")

/** Délais entre les tentatives : 3 tentatives au plus (1 s puis 3 s d'attente). */
val DEFAULT_RETRY_DELAYS_MS: List<Long> = listOf(1_000L, 3_000L)

private val TRANSIENT_HTTP_CODES = setOf(502, 503, 504)

/**
 * Erreur passagère qui mérite un nouvel essai : 502/503/504 de GitHub, ou coupure
 * réseau (timeout, connexion interrompue). Pas sans réseau du tout
 * ([UnknownHostException]) : quelques secondes d'attente n'y changent rien.
 */
private fun isTransient(e: Throwable): Boolean = when (e) {
    is HttpStatusException -> e.code in TRANSIENT_HTTP_CODES
    is UnknownHostException -> false
    is IOException -> true
    else -> false
}

/**
 * Exécute [block], et le relance après chaque délai de [delaysMs] tant qu'il échoue
 * sur une erreur passagère. [sleep] est injectable pour que les tests n'attendent pas.
 * L'appelant reste responsable du dispatcher (Dispatchers.IO pour du réseau).
 */
suspend fun <T> withRetry(
    delaysMs: List<Long> = DEFAULT_RETRY_DELAYS_MS,
    sleep: suspend (Long) -> Unit = { delay(it) },
    block: () -> T
): T {
    for (waitMs in delaysMs) {
        try {
            return block()
        } catch (e: Exception) {
            if (!isTransient(e)) throw e
        }
        sleep(waitMs)
    }
    return block()
}

/**
 * Message affiché sur l'écran À propos quand la vérification ou l'application d'une
 * mise à jour de données échoue (issue #156). Seul le cas générique garde le préfixe
 * « Erreur : » et le message technique d'origine.
 */
fun updateErrorMessage(e: Throwable): String = when {
    e is ReleaseNotFoundException ->
        "Aucune mise à jour disponible pour cette version de l'application."
    e is UnknownHostException -> "Pas de connexion Internet."
    (e is HttpStatusException && e.code in 500..599) ||
        e is SocketTimeoutException || e is ConnectException ->
        "Le serveur de mises à jour (GitHub) est momentanément indisponible. Réessayez plus tard."
    else -> "Erreur : ${e.message ?: e.javaClass.simpleName}"
}
