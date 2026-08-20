package com.lmelp.mobile.data.remote

import java.io.File

/**
 * Contrat d'accès à la GitHub Release data-latest (issue #118).
 * Permet de mocker le réseau dans les tests du repository sans dépendre d'OkHttp.
 */
interface GitHubReleaseApi {
    suspend fun fetchMetadata(): RemoteMetadata
    suspend fun downloadDatabase(destination: File)
}
