package com.lmelp.mobile

import android.app.Application
import com.lmelp.mobile.data.db.LmelpDatabase
import com.lmelp.mobile.data.repository.CritiquesRepository
import com.lmelp.mobile.data.repository.EmissionsRepository
import com.lmelp.mobile.data.repository.LivresRepository
import com.lmelp.mobile.data.repository.MetadataRepository
import com.lmelp.mobile.data.repository.PalmaresRepository
import com.lmelp.mobile.data.repository.RecommendationsRepository
import com.lmelp.mobile.data.repository.SearchRepository

class LmelpApp : Application() {

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
}
