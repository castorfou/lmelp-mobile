package com.lmelp.mobile.data.repository

import com.lmelp.mobile.data.db.SearchDao
import com.lmelp.mobile.data.model.SearchResultUi

class SearchRepository(
    private val searchDao: SearchDao
) {

    suspend fun search(query: String): List<SearchResultUi> {
        if (query.isBlank() || query.length < 2) return emptyList()
        val ftsQuery = query.trim().split(" ")
            .filter { it.isNotBlank() }
            .joinToString(" ") { "$it*" }
        return searchDao.search(ftsQuery).map {
            SearchResultUi(type = it.type, refId = it.refId, content = it.content)
        }
    }
}
