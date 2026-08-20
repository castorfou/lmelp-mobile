package com.lmelp.mobile

import com.lmelp.mobile.data.update.Sha256Verifier
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tests unitaires pour Sha256Verifier (issue #118).
 */
class Sha256VerifierTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `sha256 calcule le hash correct d'un fichier`() {
        val file = tmpFolder.newFile("test.txt")
        file.writeText("hello world")

        // sha256("hello world") calculé indépendamment (référence connue)
        val expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9" // pragma: allowlist secret

        assertEquals(expected, Sha256Verifier.sha256(file))
    }

    @Test
    fun `sha256 produit des hash differents pour des contenus differents`() {
        val fileA = tmpFolder.newFile("a.txt").apply { writeText("contenu A") }
        val fileB = tmpFolder.newFile("b.txt").apply { writeText("contenu B") }

        assert(Sha256Verifier.sha256(fileA) != Sha256Verifier.sha256(fileB))
    }
}
