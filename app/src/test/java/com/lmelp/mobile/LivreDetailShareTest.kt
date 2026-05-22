package com.lmelp.mobile

import com.lmelp.mobile.data.db.AvisAvecEmissionRow
import com.lmelp.mobile.data.db.LivreAvecCalibreRow
import com.lmelp.mobile.data.db.LivresDao
import com.lmelp.mobile.data.repository.LivresRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LivreDetailShareTest {

    private fun makeLivreAvecCalibreRow(urlBabelio: String? = null) = LivreAvecCalibreRow(
        id = "l1",
        titre = "Mon Livre",
        auteurId = null,
        auteurNom = "Auteur Test",
        editeur = null,
        urlBabelio = urlBabelio,
        urlCover = null,
        createdAt = null,
        updatedAt = null
    )

    @Test
    fun `urlBabelio est propagee depuis LivreAvecCalibreRow vers LivreDetailUi`() = runTest {
        val dao = mock<LivresDao>()
        whenever(dao.getLivreAvecCalibreById(any())).thenReturn(
            makeLivreAvecCalibreRow(urlBabelio = "https://www.babelio.com/livres/Test/123456")
        )
        whenever(dao.getAvisAvecEmissionByLivre(any())).thenReturn(emptyList<AvisAvecEmissionRow>())

        val repo = LivresRepository(dao)
        val result = repo.getLivreDetail("l1")

        assertEquals("https://www.babelio.com/livres/Test/123456", result!!.urlBabelio)
    }

    @Test
    fun `urlBabelio est null dans LivreDetailUi quand le livre na pas d url babelio`() = runTest {
        val dao = mock<LivresDao>()
        whenever(dao.getLivreAvecCalibreById(any())).thenReturn(
            makeLivreAvecCalibreRow(urlBabelio = null)
        )
        whenever(dao.getAvisAvecEmissionByLivre(any())).thenReturn(emptyList<AvisAvecEmissionRow>())

        val repo = LivresRepository(dao)
        val result = repo.getLivreDetail("l1")

        assertNull(result!!.urlBabelio)
    }
}
