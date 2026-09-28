package com.lmelp.mobile

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Issue #150 : toutes les cartes livre, y compris celles du détail d'une émission,
 * passent par BookListCard (couverture pleine hauteur, issue #143).
 * Garde-fou contre le retour de la petite vignette BookCoverThumbnail.
 */
class BookListCardUsageTest {

    private val uiSourceDir = File("src/main/java/com/lmelp/mobile/ui")

    @Test
    fun `aucun ecran n'utilise plus la petite vignette BookCoverThumbnail`() {
        assertTrue("Sources UI introuvables : ${uiSourceDir.absolutePath}", uiSourceDir.isDirectory)
        val fichiers = uiSourceDir.walkTopDown()
            .filter { it.extension == "kt" && it.readText().contains("BookCoverThumbnail(") }
            .map { it.name }
            .toList()
        assertTrue("BookCoverThumbnail encore présent dans : $fichiers", fichiers.isEmpty())
    }
}
