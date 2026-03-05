package com.lmelp.mobile.data.db

import androidx.room.Dao
import androidx.room.Query
import com.lmelp.mobile.data.model.EpisodeEntity

@Dao
interface EpisodesDao {

    @Query("SELECT * FROM episodes WHERE id = :id")
    suspend fun getEpisodeById(id: String): EpisodeEntity?
}
