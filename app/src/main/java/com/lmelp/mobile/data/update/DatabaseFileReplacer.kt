package com.lmelp.mobile.data.update

import java.io.File

/**
 * Remplace physiquement lmelp.db par un fichier téléchargé.
 *
 * Room utilise le mode WAL (Write-Ahead Logging) : lmelp.db-shm et lmelp.db-wal
 * doivent être supprimés avant de remplacer lmelp.db, sinon SQLite détecte une
 * incohérence DB/WAL → erreur "no such table: search_index" → rollback (issue #101).
 * Reproduit en Kotlin la logique de scripts/docker_export_and_push.sh.
 */
object DatabaseFileReplacer {
    fun replace(downloadedFile: File, targetDbFile: File) {
        val shmFile = File(targetDbFile.parentFile, "${targetDbFile.name}-shm")
        val walFile = File(targetDbFile.parentFile, "${targetDbFile.name}-wal")
        shmFile.delete()
        walFile.delete()

        downloadedFile.copyTo(targetDbFile, overwrite = true)
        downloadedFile.delete()
    }
}
