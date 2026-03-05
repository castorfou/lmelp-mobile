package com.lmelp.mobile.data.db

import androidx.room.Dao
import androidx.room.Query
import com.lmelp.mobile.data.model.DbMetadataEntity

@Dao
interface MetadataDao {

    @Query("SELECT * FROM db_metadata")
    suspend fun getAllMetadata(): List<DbMetadataEntity>

    @Query("SELECT value FROM db_metadata WHERE key = :key")
    suspend fun getValue(key: String): String?
}
