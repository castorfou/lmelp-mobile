package com.lmelp.mobile.data.update

import java.io.File
import java.io.IOException

/**
 * Lit PRAGMA user_version directement depuis le header binaire d'un fichier SQLite
 * (octets 60-63, big-endian), sans passer par SQLiteDatabase/JDBC (issue #132).
 *
 * Permet de comparer le schéma Room de deux fichiers .db (local vs téléchargé, ou pour
 * résoudre le tag de release GitHub data-v{N}) sans dépendance Android — testable en JVM pur.
 */
object SqliteSchemaVersion {

    private const val HEADER_SIZE = 100
    private val MAGIC_HEADER_BYTES = byteArrayOf(
        'S'.code.toByte(), 'Q'.code.toByte(), 'L'.code.toByte(), 'i'.code.toByte(),
        't'.code.toByte(), 'e'.code.toByte(), ' '.code.toByte(), 'f'.code.toByte(),
        'o'.code.toByte(), 'r'.code.toByte(), 'm'.code.toByte(), 'a'.code.toByte(),
        't'.code.toByte(), ' '.code.toByte(), '3'.code.toByte(), 0
    )
    private const val USER_VERSION_OFFSET = 60

    fun read(dbFile: File): Int {
        if (!dbFile.exists()) {
            throw IOException("Fichier SQLite introuvable : ${dbFile.path}")
        }
        val header = dbFile.inputStream().use { input ->
            val buffer = ByteArray(HEADER_SIZE)
            val bytesRead = input.read(buffer)
            if (bytesRead < HEADER_SIZE) {
                throw IOException("Fichier trop court pour être une base SQLite valide : ${dbFile.path}")
            }
            buffer
        }

        val magic = header.copyOfRange(0, MAGIC_HEADER_BYTES.size)
        if (!magic.contentEquals(MAGIC_HEADER_BYTES)) {
            throw IOException("Header SQLite invalide (magic string incorrect) : ${dbFile.path}")
        }

        return ((header[USER_VERSION_OFFSET].toInt() and 0xFF) shl 24) or
            ((header[USER_VERSION_OFFSET + 1].toInt() and 0xFF) shl 16) or
            ((header[USER_VERSION_OFFSET + 2].toInt() and 0xFF) shl 8) or
            (header[USER_VERSION_OFFSET + 3].toInt() and 0xFF)
    }
}
