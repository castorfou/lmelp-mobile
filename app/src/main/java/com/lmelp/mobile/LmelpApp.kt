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
import com.lmelp.mobile.data.repository.UserPreferencesRepository
import okio.Path.Companion.toOkioPath

class LmelpApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Cache des images dans getExternalFilesDir() → /sdcard/Android/data/com.lmelp.mobile/files/
        // Ce répertoire est effacé par Android 11+ lors d'une désinstallation (protection vie privée).
        // La sauvegarde via Android Backup (backup_rules.xml / data_extraction_rules.xml) permet de
        // restaurer le cache depuis Google Drive lors d'une réinstallation ou d'un changement de device.
        // Limite Google Drive : 25 MB → maxSizeBytes aligné sur cette limite.
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
                    .maxSizeBytes(25L * 1024 * 1024) // 25 MB (limite Google Drive pour Android Backup)
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
    val palmaresRepository by lazy { PalmaresRepository(database.palmaresDao(), database.calibreHorsMasqueDao()) }
    val recommendationsRepository by lazy { RecommendationsRepository(database.recommendationsDao()) }
    val searchRepository by lazy { SearchRepository(database.searchDao()) }
    val metadataRepository by lazy { MetadataRepository(database.metadataDao()) }
    val auteursRepository by lazy { AuteursRepository(database.auteursDao(), database.calibreHorsMasqueDao()) }
    val onKindleRepository by lazy { OnKindleRepository(database.onKindleDao()) }
    val userPreferencesRepository by lazy { UserPreferencesRepository(this) }
    val homeRepository by lazy {
        HomeRepository(
            database.metadataDao(),
            database.emissionsDao(),
            database.palmaresDao(),
            database.livresDao(),
            database.recommendationsDao(),
            database.onKindleDao()
        )
    }
}
