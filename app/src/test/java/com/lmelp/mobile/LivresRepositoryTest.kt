package com.lmelp.mobile

import com.lmelp.mobile.data.db.AvisAvecEmissionRow
import com.lmelp.mobile.data.db.LivresDao
import com.lmelp.mobile.data.model.AvisEntity
import com.lmelp.mobile.data.model.LivreEntity
import com.lmelp.mobile.data.repository.LivresRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class LivresRepositoryTest {

    private fun makeAvisEntity(
        id: String,
        emissionId: String,
        livreId: String,
        critiqueNom: String?,
        note: Double?
    ) = AvisEntity(
        id = id,
        emissionId = emissionId,
        livreId = livreId,
        critiqueId = "c1",
        note = note,
        commentaire = null,
        livreTitre = null,
        auteurNom = null,
        critiqueNom = critiqueNom,
        matchPhase = null,
        section = null,
        createdAt = null
    )

    private fun makeLivreEntity(id: String) = LivreEntity(
        id = id,
        titre = "Titre $id",
        auteurId = null,
        auteurNom = "Auteur",
        editeur = null,
        urlBabelio = null,
        createdAt = null,
        updatedAt = null
    )

    @Test
    fun `noteMoyenne calculee comme moyenne des notes non nulles`() = runTest {
        val dao = mock<LivresDao>()
        whenever(dao.getLivreById(any())).thenReturn(makeLivreEntity("l1"))
        whenever(dao.getAvisAvecEmissionByLivre(any())).thenReturn(
            listOf(
                AvisAvecEmissionRow(makeAvisEntity("a1", "e1", "l1", "Zola", 8.0), "Emission 1", "2023-01-10"),
                AvisAvecEmissionRow(makeAvisEntity("a2", "e1", "l1", "Arnaud", 6.0), "Emission 1", "2023-01-10"),
                AvisAvecEmissionRow(makeAvisEntity("a3", "e1", "l1", "Berenice", null), "Emission 1", "2023-01-10"),
            )
        )

        val repo = LivresRepository(dao)
        val result = repo.getLivreDetail("l1")

        // noteMoyenne = (8.0 + 6.0) / 2 = 7.0 (note nulle ignorée)
        assertEquals(7.0, result!!.noteMoyenne!!, 0.001)
    }

    @Test
    fun `noteMoyenne est null si aucun avis na de note`() = runTest {
        val dao = mock<LivresDao>()
        whenever(dao.getLivreById(any())).thenReturn(makeLivreEntity("l1"))
        whenever(dao.getAvisAvecEmissionByLivre(any())).thenReturn(
            listOf(
                AvisAvecEmissionRow(makeAvisEntity("a1", "e1", "l1", "Zola", null), "Emission 1", "2023-01-10"),
            )
        )

        val repo = LivresRepository(dao)
        val result = repo.getLivreDetail("l1")

        assertNull(result!!.noteMoyenne)
    }

    @Test
    fun `avis regroupes par emission dans l ordre chronologique inverse`() = runTest {
        val dao = mock<LivresDao>()
        whenever(dao.getLivreById(any())).thenReturn(makeLivreEntity("l1"))
        // e2 est plus récente (2024), e1 est plus ancienne (2023)
        whenever(dao.getAvisAvecEmissionByLivre(any())).thenReturn(
            listOf(
                AvisAvecEmissionRow(makeAvisEntity("a1", "e1", "l1", "Arnaud", 7.0), "Ancienne Emission", "2023-03-01"),
                AvisAvecEmissionRow(makeAvisEntity("a2", "e2", "l1", "Zola", 9.0), "Recente Emission", "2024-06-15"),
            )
        )

        val repo = LivresRepository(dao)
        val result = repo.getLivreDetail("l1")!!

        assertEquals(2, result.avisParEmission.size)
        // La plus récente (e2) doit apparaître en premier
        assertEquals("e2", result.avisParEmission[0].emissionId)
        assertEquals("e1", result.avisParEmission[1].emissionId)
    }

    @Test
    fun `avis tries par critique alphabetique dans chaque groupe`() = runTest {
        val dao = mock<LivresDao>()
        whenever(dao.getLivreById(any())).thenReturn(makeLivreEntity("l1"))
        whenever(dao.getAvisAvecEmissionByLivre(any())).thenReturn(
            listOf(
                AvisAvecEmissionRow(makeAvisEntity("a1", "e1", "l1", "Zola", 8.0), "Emission 1", "2023-01-10"),
                AvisAvecEmissionRow(makeAvisEntity("a2", "e1", "l1", "Arnaud", 6.0), "Emission 1", "2023-01-10"),
                AvisAvecEmissionRow(makeAvisEntity("a3", "e1", "l1", "Berenice", 7.0), "Emission 1", "2023-01-10"),
            )
        )

        val repo = LivresRepository(dao)
        val result = repo.getLivreDetail("l1")!!

        val avis = result.avisParEmission[0].avis
        assertEquals(3, avis.size)
        assertEquals("Arnaud", avis[0].critiqueNom)
        assertEquals("Berenice", avis[1].critiqueNom)
        assertEquals("Zola", avis[2].critiqueNom)
    }
}
