package com.lmelp.mobile.data.repository

import androidx.sqlite.db.SimpleSQLiteQuery
import com.lmelp.mobile.data.db.SearchDao
import com.lmelp.mobile.data.model.SearchResultUi

class SearchRepository(
    private val searchDao: SearchDao
) {

    private fun stripAccents(text: String): String {
        val nfd = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
        return nfd.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
    }

    suspend fun search(query: String): List<SearchResultUi> {
        if (query.isBlank() || query.length < 2) return emptyList()
        val ftsQuery = stripAccents(query).trim().split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
        val sql = SimpleSQLiteQuery(
            "SELECT type, ref_id AS refId, content, display_content AS displayContent FROM search_index WHERE search_index MATCH ? AND type IN ('livre', 'auteur', 'critique') LIMIT 50",
            arrayOf(ftsQuery)
        )
        return searchDao.searchRaw(sql).map {
            SearchResultUi(type = it.type, refId = it.refId, content = it.displayContent)
        }
    }
}
