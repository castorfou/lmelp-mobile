package com.lmelp.mobile

import com.lmelp.mobile.data.update.DatabaseFileReplacer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tests unitaires pour DatabaseFileReplacer (issue #118).
 * Reproduit en Kotlin la logique de scripts/docker_export_and_push.sh :
 * supprimer les fichiers WAL/SHM résiduels avant de remplacer lmelp.db
 * (bug connu, issue #101 : sinon "no such table: search_index" + rollback).
 */
class DatabaseFileReplacerTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    @Test
    fun `replace supprime les fichiers WAL et SHM residuels avant remplacement`() {
        val databasesDir = tmpFolder.newFolder("databases")
        val currentDb = databasesDir.resolve("lmelp.db").apply { writeText("ancienne base") }
        val shmFile = databasesDir.resolve("lmelp.db-shm").apply { writeText("shm résiduel") }
        val walFile = databasesDir.resolve("lmelp.db-wal").apply { writeText("wal résiduel") }
        val downloadedDb = tmpFolder.newFile("downloaded.db").apply { writeText("nouvelle base") }

        DatabaseFileReplacer.replace(downloadedFile = downloadedDb, targetDbFile = currentDb)

        assertFalse("le fichier -shm doit être supprimé", shmFile.exists())
        assertFalse("le fichier -wal doit être supprimé", walFile.exists())
    }

    @Test
    fun `replace ne plante pas si les fichiers WAL et SHM sont absents`() {
        val databasesDir = tmpFolder.newFolder("databases")
        val currentDb = databasesDir.resolve("lmelp.db").apply { writeText("ancienne base") }
        val downloadedDb = tmpFolder.newFile("downloaded.db").apply { writeText("nouvelle base") }

        DatabaseFileReplacer.replace(downloadedFile = downloadedDb, targetDbFile = currentDb)

        assertEquals("nouvelle base", currentDb.readText())
    }

    @Test
    fun `replace remplace le contenu de lmelp db par le fichier telecharge`() {
        val databasesDir = tmpFolder.newFolder("databases")
        val currentDb = databasesDir.resolve("lmelp.db").apply { writeText("ancienne base") }
        val downloadedDb = tmpFolder.newFile("downloaded.db").apply { writeText("contenu attendu") }

        DatabaseFileReplacer.replace(downloadedFile = downloadedDb, targetDbFile = currentDb)

        assertEquals("contenu attendu", currentDb.readText())
    }

    @Test
    fun `replace nettoie le fichier telecharge temporaire apres succes`() {
        val databasesDir = tmpFolder.newFolder("databases")
        val currentDb = databasesDir.resolve("lmelp.db").apply { writeText("ancienne base") }
        val downloadedDb = tmpFolder.newFile("downloaded.db").apply { writeText("nouvelle base") }

        DatabaseFileReplacer.replace(downloadedFile = downloadedDb, targetDbFile = currentDb)

        assertFalse("le fichier temporaire téléchargé doit être nettoyé", downloadedDb.exists())
    }

    @Test
    fun `replace fonctionne si lmelp db n'existe pas encore`() {
        val databasesDir = tmpFolder.newFolder("databases")
        val currentDb = databasesDir.resolve("lmelp.db")
        val downloadedDb = tmpFolder.newFile("downloaded.db").apply { writeText("premiere base") }

        DatabaseFileReplacer.replace(downloadedFile = downloadedDb, targetDbFile = currentDb)

        assertTrue(currentDb.exists())
        assertEquals("premiere base", currentDb.readText())
    }
}
