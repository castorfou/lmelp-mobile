package com.lmelp.mobile

import com.lmelp.mobile.data.model.UpdateCheckResult
import com.lmelp.mobile.data.remote.RemoteMetadata
import com.lmelp.mobile.viewmodel.hasUpdateAvailable
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests unitaires pour la dérivation du badge "mise à jour disponible" sur
 * l'icône réglages de HomeScreen (issue #118, retour utilisateur).
 * Fonction pure testée isolément : HomeViewModel n'est jamais instancié
 * directement en test (son ticker infini bloquerait runTest, voir
 * HomeViewModelTickerTest.kt qui teste déjà uniquement les fonctions pures).
 */
class HomeViewModelUpdateBadgeTest {

    private val remote = RemoteMetadata(
        schemaVersion = 1,
        exportDate = "2026-08-19",
        exportDatetime = "2026-08-19T19:35:48.818072",
        exportVersion = "200",
        nbEmissions = 180,
        nbLivres = 1688,
        nbAvis = 4275,
        fileSizeBytes = 4616192L,
        sha256 = "abc",
        filename = "lmelp.db"
    )

    @Test
    fun `hasUpdateAvailable est vrai si le resultat est UpdateAvailable`() {
        assertTrue(hasUpdateAvailable(UpdateCheckResult.UpdateAvailable(remote)))
    }

    @Test
    fun `hasUpdateAvailable est faux si le resultat est UpToDate`() {
        assertFalse(hasUpdateAvailable(UpdateCheckResult.UpToDate))
    }

    @Test
    fun `hasUpdateAvailable est faux si le resultat est une Error`() {
        assertFalse(hasUpdateAvailable(UpdateCheckResult.Error("pas de réseau")))
    }
}
