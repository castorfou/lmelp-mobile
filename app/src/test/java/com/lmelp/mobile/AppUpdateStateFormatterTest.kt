package com.lmelp.mobile

import com.lmelp.mobile.data.model.AppRelease
import com.lmelp.mobile.data.model.AppUpdateCheckResult
import com.lmelp.mobile.data.model.AppUpdateState
import com.lmelp.mobile.data.model.UpdateCheckResult
import com.lmelp.mobile.ui.about.formatAppUpdateButtonLabel
import com.lmelp.mobile.ui.about.formatAppUpdateTitle
import com.lmelp.mobile.viewmodel.hasUpdateAvailable
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Libellés du bloc « nouvelle version de l'appli » et badge de l'accueil (issue #162). */
class AppUpdateStateFormatterTest {

    private val release = AppRelease(
        tag = "v1.3.0",
        prerelease = false,
        draft = false,
        apkUrl = "https://example.org/a.apk",
        apkSha256 = null,
        htmlUrl = "https://example.org/v1.3.0"
    )

    @Test
    fun `pas de titre tant qu'aucune version n'est disponible`() {
        assertNull(formatAppUpdateTitle(AppUpdateState.Idle))
    }

    @Test
    fun `le titre annonce la version disponible`() {
        assertEquals("Nouvelle version 1.3.0 disponible", formatAppUpdateTitle(AppUpdateState.UpdateAvailable(release)))
        assertEquals("Nouvelle version 1.3.0 disponible", formatAppUpdateTitle(AppUpdateState.Downloading(release)))
    }

    @Test
    fun `le titre annonce une preversion comme telle`() {
        val preview = AppRelease(
            tag = "app-preview",
            name = "1.2.0+3",
            prerelease = true,
            draft = false,
            apkUrl = "https://example.org/p.apk",
            apkSha256 = null,
            htmlUrl = "https://example.org/app-preview"
        )
        assertEquals("Préversion 1.2.0+3 disponible", formatAppUpdateTitle(AppUpdateState.UpdateAvailable(preview)))
        assertEquals("Préversion 1.2.0+3 disponible", formatAppUpdateTitle(AppUpdateState.Downloading(preview)))
        assertEquals("Préversion 1.2.0+3 disponible", formatAppUpdateTitle(AppUpdateState.Error(preview, "x")))
    }

    @Test
    fun `le bouton suit l'etat du telechargement et de l'installation`() {
        assertEquals("Télécharger et installer", formatAppUpdateButtonLabel(AppUpdateState.UpdateAvailable(release)))
        assertEquals("Téléchargement en cours…", formatAppUpdateButtonLabel(AppUpdateState.Downloading(release)))
        assertEquals(
            "Autoriser LMELP à installer des applications, puis réessayer",
            formatAppUpdateButtonLabel(AppUpdateState.NeedsPermission(release))
        )
        assertEquals("Relancer l'installation", formatAppUpdateButtonLabel(AppUpdateState.Installing(release)))
        // Message déjà rédigé par updateErrorMessage (issue #168) : affiché tel quel,
        // sans « Erreur : » doublé.
        assertEquals(
            "Pas de connexion Internet.",
            formatAppUpdateButtonLabel(AppUpdateState.Error(release, "Pas de connexion Internet."))
        )
    }

    @Test
    fun `le badge de l'accueil s'allume pour une MAJ de donnees ou d'appli`() {
        assertFalse(hasUpdateAvailable(UpdateCheckResult.UpToDate, AppUpdateCheckResult.UpToDate))
        assertTrue(hasUpdateAvailable(UpdateCheckResult.UpToDate, AppUpdateCheckResult.UpdateAvailable(release)))
        assertFalse(hasUpdateAvailable(UpdateCheckResult.Error("x"), AppUpdateCheckResult.Error("y")))
    }
}
