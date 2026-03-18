package com.lmelp.mobile.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery

data class SearchRow(
    val type: String,
    val refId: String,
    val content: String,
    val displayContent: String,
    @ColumnInfo(name = "url_cover") val urlCover: String?
)

@Dao
interface SearchDao {

    @RawQuery
    suspend fun searchRaw(query: SupportSQLiteQuery): List<SearchRow>
}
