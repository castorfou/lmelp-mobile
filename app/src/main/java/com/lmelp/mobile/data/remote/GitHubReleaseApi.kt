package com.lmelp.mobile.data.remote

import java.io.File

/**
 * Contrat d'accès à la GitHub Release de données lmelp, taguée data-v{N} selon le schéma
 * Room local (issue #118, tag dynamique issue #132).
 * Permet de mocker le réseau dans les tests du repository sans dépendre d'OkHttp.
 */
interface GitHubReleaseApi {
    suspend fun fetchMetadata(): RemoteMetadata
    suspend fun downloadDatabase(destination: File)
}
