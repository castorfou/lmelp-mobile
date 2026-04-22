package com.lmelp.mobile

import androidx.sqlite.db.SupportSQLiteQuery
import com.lmelp.mobile.data.db.SearchDao
import com.lmelp.mobile.data.db.SearchRow
import com.lmelp.mobile.data.repository.SearchRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class SearchRepositoryTest {

    @Test
    fun `search returns empty list for blank query`() = runTest {
        val dao = mock<SearchDao>()
        val repo = SearchRepository(dao)
        val result = repo.search("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `search returns empty list for single char query`() = runTest {
        val dao = mock<SearchDao>()
        val repo = SearchRepository(dao)
        val result = repo.search("a")
        assertTrue(result.isEmpty())
    }

    /**
     * Reproduit le bug #96 : la requête SQL utilise `si.search_index MATCH ?`
     * ce qui est invalide — `search_index` est le nom de la table, pas une colonne.
     * La syntaxe FTS4 correcte est `search_index MATCH ?`.
     *
     * RED : ce test échoue avant le fix.
     */
    @Test
    fun `la requete SQL utilise search_index MATCH sans prefixe de colonne`() = runTest {
        val dao = mock<SearchDao>()
        whenever(dao.searchRaw(any())).thenReturn(emptyList())
        val repo = SearchRepository(dao)

        repo.search("hamlet")

        val captor = argumentCaptor<SupportSQLiteQuery>()
        verify(dao).searchRaw(captor.capture())
        val sql = captor.firstValue.sql

        assertFalse(
            "La requête SQL ne doit PAS contenir 'si.search_index MATCH' (colonne invalide FTS4), mais contient : $sql",
            sql.contains("si.search_index MATCH", ignoreCase = true)
        )
        assertTrue(
            "La requête SQL doit contenir 'search_index MATCH' (sans préfixe de colonne), mais est : $sql",
            sql.contains("search_index MATCH", ignoreCase = true)
        )
    }

    @Test
    fun `search mappe displayContent comme content dans SearchResultUi`() = runTest {
        val dao = mock<SearchDao>()
        val fakeRow = SearchRow(
            type = "livre",
            refId = "abc123",
            content = "hamlet contenu indexé",
            displayContent = "Hamlet (affichage)",
            urlCover = null
        )
        whenever(dao.searchRaw(any())).thenReturn(listOf(fakeRow))
        val repo = SearchRepository(dao)

        val results = repo.search("hamlet")

        assertEquals(1, results.size)
        assertEquals("Hamlet (affichage)", results[0].content)
    }
}
