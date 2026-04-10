package com.lmelp.mobile

import com.lmelp.mobile.data.db.AvisParCritiqueRow
import com.lmelp.mobile.data.db.CritiquesDao
import com.lmelp.mobile.data.model.CritiqueEntity
import com.lmelp.mobile.data.repository.CritiquesRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CritiquesRepositoryTest {

    private fun makeCritiqueEntity(id: String, nom: String, nbAvis: Int = 0) = CritiqueEntity(
        id = id,
        nom = nom,
        animateur = 0,
        nbAvis = nbAvis
    )

    private fun makeAvisRow(
        livreId: String,
        livreTitre: String,
        auteurNom: String?,
        note: Double?,
        emissionId: String = "em1",
        emissionDate: String? = "2024-01-01"
    ) = AvisParCritiqueRow(
        livreId = livreId,
        livreTitre = livreTitre,
        auteurNom = auteurNom,
        note = note,
        emissionId = emissionId,
        emissionDate = emissionDate
    )

    @Test
    fun `getCritiqueDetail retourne null si critique introuvable`() = runTest {
        val dao = mock<CritiquesDao>()
        whenever(dao.getCritiqueById(any())).thenReturn(null)

        val repo = CritiquesRepository(dao)
        val result = repo.getCritiqueDetail("inexistant")

        assertNull(result)
    }

    @Test
    fun `getCritiqueDetail retourne les infos de base du critique`() = runTest {
        val dao = mock<CritiquesDao>()
        whenever(dao.getCritiqueById("c1")).thenReturn(makeCritiqueEntity("c1", "Arnaud Viviant", 50))
        whenever(dao.getAvisByCritique("c1")).thenReturn(emptyList())

        val repo = CritiquesRepository(dao)
        val result = repo.getCritiqueDetail("c1")!!

        assertEquals("c1", result.id)
        assertEquals("Arnaud Viviant", result.nom)
        assertEquals(50, result.nbAvis)
    }

    @Test
    fun `getCritiqueDetail calcule la note moyenne`() = runTest {
        val dao = mock<CritiquesDao>()
        whenever(dao.getCritiqueById("c1")).thenReturn(makeCritiqueEntity("c1", "Alice"))
        whenever(dao.getAvisByCritique("c1")).thenReturn(
            listOf(
                makeAvisRow("l1", "Livre A", null, 8.0),
                makeAvisRow("l2", "Livre B", null, 6.0),
                makeAvisRow("l3", "Livre C", null, 10.0),
            )
        )

        val repo = CritiquesRepository(dao)
        val result = repo.getCritiqueDetail("c1")!!

        assertEquals(8.0, result.noteMoyenne!!, 0.01)
    }

    @Test
    fun `getCritiqueDetail noteMoyenne null si aucun avis`() = runTest {
        val dao = mock<CritiquesDao>()
        whenever(dao.getCritiqueById("c1")).thenReturn(makeCritiqueEntity("c1", "Alice"))
        whenever(dao.getAvisByCritique("c1")).thenReturn(emptyList())

        val repo = CritiquesRepository(dao)
        val result = repo.getCritiqueDetail("c1")!!

        assertNull(result.noteMoyenne)
    }

    @Test
    fun `getCritiqueDetail calcule la distribution des notes`() = runTest {
        val dao = mock<CritiquesDao>()
        whenever(dao.getCritiqueById("c1")).thenReturn(makeCritiqueEntity("c1", "Alice"))
        whenever(dao.getAvisByCritique("c1")).thenReturn(
            listOf(
                makeAvisRow("l1", "Livre A", null, 8.0),
                makeAvisRow("l2", "Livre B", null, 8.0),
                makeAvisRow("l3", "Livre C", null, 9.0),
                makeAvisRow("l4", "Livre D", null, null),  // note null ignorée
            )
        )

        val repo = CritiquesRepository(dao)
        val result = repo.getCritiqueDetail("c1")!!

        assertEquals(2, result.distribution[8])
        assertEquals(1, result.distribution[9])
        assertTrue(result.distribution[7] == null || result.distribution[7] == 0)
    }

    @Test
    fun `getCritiqueDetail coups de coeur sont notes 9 et 10`() = runTest {
        val dao = mock<CritiquesDao>()
        whenever(dao.getCritiqueById("c1")).thenReturn(makeCritiqueEntity("c1", "Alice"))
        whenever(dao.getAvisByCritique("c1")).thenReturn(
            listOf(
                makeAvisRow("l1", "Chef d'oeuvre", "Auteur A", 10.0),
                makeAvisRow("l2", "Excellent", "Auteur B", 9.0),
                makeAvisRow("l3", "Bien", "Auteur C", 8.0),
                makeAvisRow("l4", "Moyen", "Auteur D", 5.0),
            )
        )

        val repo = CritiquesRepository(dao)
        val result = repo.getCritiqueDetail("c1")!!

        assertEquals(2, result.coupsDeCoeur.size)
        assertTrue(result.coupsDeCoeur.all { it.note!! >= 9.0 })
        assertEquals("l1", result.coupsDeCoeur[0].livreId)
        assertEquals("l2", result.coupsDeCoeur[1].livreId)
    }

    @Test
    fun `getCritiqueDetail coups de coeur tries par note decroissante`() = runTest {
        val dao = mock<CritiquesDao>()
        whenever(dao.getCritiqueById("c1")).thenReturn(makeCritiqueEntity("c1", "Alice"))
        whenever(dao.getAvisByCritique("c1")).thenReturn(
            listOf(
                makeAvisRow("l1", "Livre 9", null, 9.0),
                makeAvisRow("l2", "Livre 10", null, 10.0),
                makeAvisRow("l3", "Livre 9b", null, 9.0),
            )
        )

        val repo = CritiquesRepository(dao)
        val result = repo.getCritiqueDetail("c1")!!

        assertEquals(10.0, result.coupsDeCoeur[0].note!!, 0.01)
    }

    @Test
    fun `getCritiqueDetail animateur flag mappé correctement`() = runTest {
        val dao = mock<CritiquesDao>()
        val animateur = CritiqueEntity(id = "c1", nom = "Patrick Sherr", animateur = 1, nbAvis = 5)
        whenever(dao.getCritiqueById("c1")).thenReturn(animateur)
        whenever(dao.getAvisByCritique("c1")).thenReturn(emptyList())

        val repo = CritiquesRepository(dao)
        val result = repo.getCritiqueDetail("c1")!!

        assertTrue(result.animateur)
    }
}
