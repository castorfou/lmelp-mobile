package com.lmelp.mobile

import com.lmelp.mobile.data.db.AvisParCritiqueRow
import com.lmelp.mobile.data.db.CritiquesDao
import com.lmelp.mobile.data.model.CritiqueEntity
import com.lmelp.mobile.data.repository.CritiquesRepository
import com.lmelp.mobile.ui.critiques.coupDeCoeurKey
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
        emissionDate: String? = "2024-01-01",
        avisId: String = "avis_${livreId}_$emissionId",
        urlCover: String? = null
    ) = AvisParCritiqueRow(
        avisId = avisId,
        livreId = livreId,
        livreTitre = livreTitre,
        auteurNom = auteurNom,
        note = note,
        emissionId = emissionId,
        emissionDate = emissionDate,
        urlCover = urlCover
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

    // Issue #140 : un critique peut noter le même livre dans deux émissions
    // (ex. Beigbeder / « Le voyant d'Étampes », 29/08 puis 31/10/2021).
    // Les deux avis doivent rester visibles et produire des clés LazyColumn distinctes.

    @Test
    fun `getCritiqueDetail propage l'avisId dans les coups de coeur`() = runTest {
        val dao = mock<CritiquesDao>()
        whenever(dao.getCritiqueById("c1")).thenReturn(makeCritiqueEntity("c1", "Alice"))
        whenever(dao.getAvisByCritique("c1")).thenReturn(
            listOf(makeAvisRow("l1", "Livre A", null, 9.0, avisId = "avis42"))
        )

        val result = CritiquesRepository(dao).getCritiqueDetail("c1")!!

        assertEquals("avis42", result.coupsDeCoeur.single().avisId)
    }

    @Test
    fun `getCritiqueDetail meme livre coup de coeur dans deux emissions donne deux cartes aux cles distinctes`() = runTest {
        val dao = mock<CritiquesDao>()
        whenever(dao.getCritiqueById("c1")).thenReturn(makeCritiqueEntity("c1", "Frédéric Beigbeder"))
        whenever(dao.getAvisByCritique("c1")).thenReturn(
            listOf(
                makeAvisRow("l1", "Le voyant des tempêtes", "Abel Quentin", 9.0,
                    emissionId = "em_aout", emissionDate = "2021-08-29T00:00:00Z", avisId = "a1"),
                makeAvisRow("l1", "Le Voyant d’Etampes", "Abel Quentin", 9.0,
                    emissionId = "em_oct", emissionDate = "2021-10-31T00:00:00Z", avisId = "a2"),
            )
        )

        val result = CritiquesRepository(dao).getCritiqueDetail("c1")!!

        assertEquals(2, result.coupsDeCoeur.size)
        val keys = result.coupsDeCoeur.map { coupDeCoeurKey(it) }
        assertEquals(keys.size, keys.toSet().size)
    }

    @Test
    fun `getCritiqueDetail coups de coeur a note egale tries du plus recent au plus ancien`() = runTest {
        val dao = mock<CritiquesDao>()
        whenever(dao.getCritiqueById("c1")).thenReturn(makeCritiqueEntity("c1", "Alice"))
        whenever(dao.getAvisByCritique("c1")).thenReturn(
            listOf(
                makeAvisRow("l1", "Ancien", null, 9.0, emissionId = "e1", emissionDate = "2021-03-28T00:00:00Z"),
                makeAvisRow("l2", "Récent", null, 9.0, emissionId = "e2", emissionDate = "2021-10-31T00:00:00Z"),
                makeAvisRow("l3", "Top", null, 10.0, emissionId = "e3", emissionDate = "2020-01-01T00:00:00Z"),
            )
        )

        val result = CritiquesRepository(dao).getCritiqueDetail("c1")!!

        assertEquals(listOf("l3", "l2", "l1"), result.coupsDeCoeur.map { it.livreId })
    }

    // Issue #143 : les coups de cœur d'un critique affichent la couverture du livre

    @Test
    fun `getCritiqueDetail propage la couverture dans les coups de coeur`() = runTest {
        val dao = mock<CritiquesDao>()
        whenever(dao.getCritiqueById("c1")).thenReturn(makeCritiqueEntity("c1", "Alice"))
        whenever(dao.getAvisByCritique("c1")).thenReturn(
            listOf(
                makeAvisRow("l1", "Avec couverture", null, 10.0, urlCover = "https://covers/l1.jpg"),
                makeAvisRow("l2", "Sans couverture", null, 9.0, urlCover = null),
            )
        )

        val result = CritiquesRepository(dao).getCritiqueDetail("c1")!!

        assertEquals("https://covers/l1.jpg", result.coupsDeCoeur[0].urlCover)
        assertNull(result.coupsDeCoeur[1].urlCover)
    }
}
