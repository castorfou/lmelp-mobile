package com.lmelp.mobile.data.db

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.lmelp.mobile.data.model.AvisCritiquesEntity
import com.lmelp.mobile.data.model.AvisEntity
import com.lmelp.mobile.data.model.AuteurEntity
import com.lmelp.mobile.data.model.CritiqueEntity
import com.lmelp.mobile.data.model.DbMetadataEntity
import com.lmelp.mobile.data.model.EmissionEntity
import com.lmelp.mobile.data.model.EmissionLivreEntity
import com.lmelp.mobile.data.model.EpisodeEntity
import com.lmelp.mobile.data.model.LivreEntity
import com.lmelp.mobile.data.model.PalmaresEntity
import com.lmelp.mobile.data.model.RecommendationEntity

@Database(
    entities = [
        EpisodeEntity::class,
        EmissionEntity::class,
        AuteurEntity::class,
        LivreEntity::class,
        CritiqueEntity::class,
        AvisEntity::class,
        EmissionLivreEntity::class,
        PalmaresEntity::class,
        RecommendationEntity::class,
        AvisCritiquesEntity::class,
        DbMetadataEntity::class,
    ],
    version = 2,
    exportSchema = false
)
abstract class LmelpDatabase : RoomDatabase() {

    abstract fun episodesDao(): EpisodesDao
    abstract fun emissionsDao(): EmissionsDao
    abstract fun livresDao(): LivresDao
    abstract fun critiquesDao(): CritiquesDao
    abstract fun palmaresDao(): PalmaresDao
    abstract fun recommendationsDao(): RecommendationsDao
    abstract fun searchDao(): SearchDao
    abstract fun metadataDao(): MetadataDao
    abstract fun avisCritiquesDao(): AvisCritiquesDao

    companion object {
        @Volatile
        private var INSTANCE: LmelpDatabase? = null

        fun getInstance(context: Context): LmelpDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: try {
                    Log.d("LmelpDB", "Building database instance...")
                    Room.databaseBuilder(
                        context.applicationContext,
                        LmelpDatabase::class.java,
                        "lmelp.db"
                    )
                        .createFromAsset("lmelp.db")
                        .fallbackToDestructiveMigration()
                        .build()
                        .also {
                            INSTANCE = it
                            Log.d("LmelpDB", "Database instance created successfully")
                        }
                } catch (e: Exception) {
                    Log.e("LmelpDB", "Failed to create database: ${e.message}", e)
                    throw e
                }
            }
        }
    }
}
