package com.lmelp.mobile.data.remote

import com.lmelp.mobile.data.model.AppRelease
import java.io.File

/**
 * Accès aux releases GitHub du dépôt pour détecter une nouvelle version de l'appli (issue #162).
 * Interface pour pouvoir remplacer le réseau dans les tests du repository.
 */
interface AppReleaseApi {
    /** Toutes les releases récentes, appli et données mélangées, sans filtrage. */
    suspend fun fetchReleases(): List<AppRelease>
    suspend fun downloadApk(url: String, destination: File)
}
