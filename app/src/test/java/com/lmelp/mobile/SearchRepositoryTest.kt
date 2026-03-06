package com.lmelp.mobile

import com.lmelp.mobile.data.db.SearchDao
import com.lmelp.mobile.data.db.SearchRow
import com.lmelp.mobile.data.repository.SearchRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class SearchRepositoryTest {

    @Test
    fun `search returns empty list for blank query`() = runTest {
        val dao = mock(SearchDao::class.java)
        val repo = SearchRepository(dao)
        val result = repo.search("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `search returns empty list for single char query`() = runTest {
        val dao = mock(SearchDao::class.java)
        val repo = SearchRepository(dao)
        val result = repo.search("a")
        assertTrue(result.isEmpty())
    }
}
