package com.lmelp.mobile

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.lmelp.mobile.data.db.LmelpDatabase
import com.lmelp.mobile.data.repository.AuteursRepository
import com.lmelp.mobile.data.repository.CritiquesRepository
import com.lmelp.mobile.data.repository.EmissionsRepository
import com.lmelp.mobile.data.repository.HomeRepository
import com.lmelp.mobile.data.repository.LivresRepository
import com.lmelp.mobile.data.repository.MetadataRepository
import com.lmelp.mobile.data.repository.OnKindleRepository
import com.lmelp.mobile.data.repository.PalmaresRepository
import com.lmelp.mobile.data.repository.RecommendationsRepository
import com.lmelp.mobile.data.repository.SearchRepository
import okio.Path.Companion.toOkioPath

class LmelpApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Cache des images dans getExternalFilesDir() → /sdcard/Android/data/com.lmelp.mobile/files/
        // Ce répertoire survit aux désinstallations/réinstallations et aux mises à jour de lmelp.db.
        // Contrairement à filesDir et cacheDir (tous deux effacés à la désinstallation).
        // Fallback sur filesDir si le stockage externe est indisponible (device sans SD).
        val persistentCacheDir = (getExternalFilesDir(null) ?: filesDir)
            .resolve("coil_image_cache")
        val imageLoader = ImageLoader.Builder(this)
            .components { add(OkHttpNetworkFetcherFactory()) }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(this, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(persistentCacheDir.toOkioPath())
                    .maxSizeBytes(50L * 1024 * 1024) // 50 MB
                    .build()
            }
            .build()
        SingletonImageLoader.setSafe { imageLoader }
    }

    val database by lazy { LmelpDatabase.getInstance(this) }

    val emissionsRepository by lazy {
        EmissionsRepository(
            database.emissionsDao(),
            database.episodesDao(),
            database.livresDao(),
            database.avisCritiquesDao()
        )
    }
    val livresRepository by lazy { LivresRepository(database.livresDao()) }
    val critiquesRepository by lazy { CritiquesRepository(database.critiquesDao()) }
    val palmaresRepository by lazy { PalmaresRepository(database.palmaresDao()) }
    val recommendationsRepository by lazy { RecommendationsRepository(database.recommendationsDao()) }
    val searchRepository by lazy { SearchRepository(database.searchDao()) }
    val metadataRepository by lazy { MetadataRepository(database.metadataDao()) }
    val auteursRepository by lazy { AuteursRepository(database.auteursDao()) }
    val onKindleRepository by lazy { OnKindleRepository(database.onKindleDao()) }
    val homeRepository by lazy {
        HomeRepository(
            database.metadataDao(),
            database.emissionsDao(),
            database.palmaresDao(),
            database.livresDao(),
            database.recommendationsDao(),
            database.onKindleDao(),
            context = this
        )
    }
}
