package com.lmelp.mobile

import com.lmelp.mobile.data.model.DbInfoUi
import com.lmelp.mobile.ui.about.formatDbInfoSummary
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests unitaires pour le formatage des informations de la base de données
 * affichées dans AboutScreen (issue #116 : visibilité de la version des
 * données en local, en préparation du futur téléchargement HTTP).
 */
class DbInfoFormatterTest {

    private fun fakeDbInfo(
        exportDate: String = "2026-08-19",
        nbEmissions: String = "179",
        nbLivres: String = "1679",
        nbAvis: String = "4251"
    ) = DbInfoUi(
        exportDate = exportDate,
        version = "1786912562",
        nbEmissions = nbEmissions,
        nbLivres = nbLivres,
        nbAvis = nbAvis
    )

    @Test
    fun `resume contient la date d export`() {
        val summary = formatDbInfoSummary(fakeDbInfo(exportDate = "2026-08-19"))
        assertEquals(true, summary.contains("2026-08-19"))
    }

    @Test
    fun `resume contient le nombre d emissions`() {
        val summary = formatDbInfoSummary(fakeDbInfo(nbEmissions = "179"))
        assertEquals(true, summary.contains("179"))
    }

    @Test
    fun `resume contient le nombre de livres`() {
        val summary = formatDbInfoSummary(fakeDbInfo(nbLivres = "1679"))
        assertEquals(true, summary.contains("1679"))
    }

    @Test
    fun `resume contient le nombre d avis`() {
        val summary = formatDbInfoSummary(fakeDbInfo(nbAvis = "4251"))
        assertEquals(true, summary.contains("4251"))
    }
}
