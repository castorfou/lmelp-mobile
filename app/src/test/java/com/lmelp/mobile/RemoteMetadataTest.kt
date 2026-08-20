package com.lmelp.mobile

import com.lmelp.mobile.data.remote.RemoteMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * Tests unitaires pour RemoteMetadata.parse (issue #118).
 * Format confirmé en conditions réelles contre la release data-latest.
 */
class RemoteMetadataTest {

    private val validJson = """
        {
          "schema_version": 1,
          "export_date": "2026-08-19",
          "export_datetime": "2026-08-19T19:35:48.818072",
          "export_version": "1787168148",
          "nb_emissions": 180,
          "nb_livres": 1688,
          "nb_avis": 4275,
          "file_size_bytes": 4616192,
          "sha256": "0000000000000000000000000000000000000000000000000000000000000000",
          "filename": "lmelp.db"
        }
    """.trimIndent()

    @Test
    fun `parse extrait tous les champs du JSON`() {
        val metadata = RemoteMetadata.parse(validJson)

        assertEquals(1, metadata.schemaVersion)
        assertEquals("2026-08-19", metadata.exportDate)
        assertEquals("2026-08-19T19:35:48.818072", metadata.exportDatetime)
        assertEquals("1787168148", metadata.exportVersion)
        assertEquals(180, metadata.nbEmissions)
        assertEquals(1688, metadata.nbLivres)
        assertEquals(4275, metadata.nbAvis)
        assertEquals(4616192L, metadata.fileSizeBytes)
        assertEquals("0000000000000000000000000000000000000000000000000000000000000000", metadata.sha256)
        assertEquals("lmelp.db", metadata.filename)
    }

    @Test
    fun `parse leve une exception si export_version est absent`() {
        val invalidJson = """{"schema_version": 1, "filename": "lmelp.db"}"""

        assertThrows(IllegalArgumentException::class.java) {
            RemoteMetadata.parse(invalidJson)
        }
    }

    @Test
    fun `parse leve une exception si le JSON est invalide`() {
        assertThrows(IllegalArgumentException::class.java) {
            RemoteMetadata.parse("not json")
        }
    }
}
