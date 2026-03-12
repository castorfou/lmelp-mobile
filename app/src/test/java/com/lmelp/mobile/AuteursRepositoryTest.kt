package com.lmelp.mobile

import com.lmelp.mobile.data.db.AuteursDao
import com.lmelp.mobile.data.db.LivreParAuteurRow
import com.lmelp.mobile.data.model.AuteurEntity
import com.lmelp.mobile.data.repository.AuteursRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class AuteursRepositoryTest {

    private fun makeAuteurEntity(id: String, nom: String) = AuteurEntity(
        id = id,
        nom = nom,
        urlBabelio = null
    )

    private fun makeLivreParAuteurRow(
        livreId: String,
        titre: String,
        noteMoyenne: Double?,
        derniereEmissionDate: String?
    ) = LivreParAuteurRow(
        livreId = livreId,
        titre = titre,
        noteMoyenne = noteMoyenne,
        derniereEmissionDate = derniereEmissionDate
    )

    @Test
    fun `getAuteurDetail retourne null si auteur introuvable`() = runTest {
        val dao = mock<AuteursDao>()
        whenever(dao.getAuteurById(any())).thenReturn(null)

        val repo = AuteursRepository(dao)
        val result = repo.getAuteurDetail("inexistant")

        assertNull(result)
    }

    @Test
    fun `livres tries par date emission decroissante`() = runTest {
        val dao = mock<AuteursDao>()
        whenever(dao.getAuteurById(any())).thenReturn(makeAuteurEntity("a1", "Victor Hugo"))
        whenever(dao.getLivresParAuteur(any())).thenReturn(
            listOf(
                makeLivreParAuteurRow("l1", "Notre-Dame de Paris", 8.0, "2022-01-10"),
                makeLivreParAuteurRow("l2", "Les Misérables", 9.0, "2024-06-15"),
                makeLivreParAuteurRow("l3", "Ruy Blas", null, "2023-03-01"),
            )
        )

        val repo = AuteursRepository(dao)
        val result = repo.getAuteurDetail("a1")!!

        assertEquals(3, result.livres.size)
        // Plus récent en premier
        assertEquals("l2", result.livres[0].livreId)  // 2024
        assertEquals("l3", result.livres[1].livreId)  // 2023
        assertEquals("l1", result.livres[2].livreId)  // 2022
    }

    @Test
    fun `livres sans date emission apparaissent en dernier`() = runTest {
        val dao = mock<AuteursDao>()
        whenever(dao.getAuteurById(any())).thenReturn(makeAuteurEntity("a1", "Victor Hugo"))
        whenever(dao.getLivresParAuteur(any())).thenReturn(
            listOf(
                makeLivreParAuteurRow("l1", "Notre-Dame de Paris", 8.0, null),
                makeLivreParAuteurRow("l2", "Les Misérables", 9.0, "2024-06-15"),
            )
        )

        val repo = AuteursRepository(dao)
        val result = repo.getAuteurDetail("a1")!!

        assertEquals("l2", result.livres[0].livreId)  // avec date
        assertEquals("l1", result.livres[1].livreId)  // sans date
    }

    @Test
    fun `noteMoyenne null si livre sans avis dans palmares`() = runTest {
        val dao = mock<AuteursDao>()
        whenever(dao.getAuteurById(any())).thenReturn(makeAuteurEntity("a1", "Auteur"))
        whenever(dao.getLivresParAuteur(any())).thenReturn(
            listOf(
                makeLivreParAuteurRow("l1", "Livre sans avis", null, "2023-01-01"),
            )
        )

        val repo = AuteursRepository(dao)
        val result = repo.getAuteurDetail("a1")!!

        assertNull(result.livres[0].noteMoyenne)
    }
}
