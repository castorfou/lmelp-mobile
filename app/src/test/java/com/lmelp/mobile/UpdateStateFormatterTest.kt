package com.lmelp.mobile

import com.lmelp.mobile.data.model.DataUpdateState
import com.lmelp.mobile.data.remote.RemoteMetadata
import com.lmelp.mobile.ui.about.formatUpdateStateLabel
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests unitaires pour le formatage de DataUpdateState affiché dans AboutScreen (issue #118).
 */
class UpdateStateFormatterTest {

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
    fun `Idle produit un label neutre`() {
        assertEquals("Vérifier les mises à jour", formatUpdateStateLabel(DataUpdateState.Idle))
    }

    @Test
    fun `Checking indique une verification en cours`() {
        assertEquals(true, formatUpdateStateLabel(DataUpdateState.Checking).contains("Vérification"))
    }

    @Test
    fun `UpdateAvailable mentionne la date d export distante`() {
        val label = formatUpdateStateLabel(DataUpdateState.UpdateAvailable(remote))
        assertEquals(true, label.contains("2026-08-19"))
    }

    @Test
    fun `Downloading indique un telechargement en cours`() {
        assertEquals(true, formatUpdateStateLabel(DataUpdateState.Downloading).contains("Téléchargement"))
    }

    @Test
    fun `Success indique un succes`() {
        assertEquals(true, formatUpdateStateLabel(DataUpdateState.Success).contains("jour"))
    }

    @Test
    fun `Error affiche le message d erreur`() {
        val label = formatUpdateStateLabel(DataUpdateState.Error("pas de réseau"))
        assertEquals(true, label.contains("pas de réseau"))
    }

    @Test
    fun `Restarting demande explicitement a l'utilisateur de rouvrir l'application`() {
        // Le redémarrage automatique (AlarmManager) échoue silencieusement sur Android 12+
        // à cause des restrictions de lancement d'activité en arrière-plan (confirmé en test
        // manuel sur Android 17). Le message doit donc guider l'utilisateur à rouvrir lui-même.
        val label = formatUpdateStateLabel(DataUpdateState.Restarting)
        assertEquals(true, label.contains("rouvrir"))
    }
}
