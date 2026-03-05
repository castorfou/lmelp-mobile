package com.lmelp.mobile.data.db

import androidx.room.Dao
import androidx.room.Query

data class SearchRow(val type: String, val refId: String, val content: String)

@Dao
interface SearchDao {

    @Query("""
        SELECT type, ref_id AS refId, content
        FROM search_index
        WHERE search_index MATCH :query
        LIMIT 50
    """)
    suspend fun search(query: String): List<SearchRow>
}
