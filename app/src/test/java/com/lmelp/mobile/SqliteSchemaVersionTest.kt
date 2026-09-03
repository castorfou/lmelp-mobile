package com.lmelp.mobile

import com.lmelp.mobile.data.update.SqliteSchemaVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests unitaires pour SqliteSchemaVersion (issue #132) : lecture de PRAGMA user_version
 * directement depuis le header binaire SQLite (octets 60-63, big-endian), sans dépendance
 * Android/JDBC — vérifié manuellement avec `sqlite3 ... "PRAGMA user_version"` sur une vraie
 * base lmelp.db (même valeur que la lecture binaire).
 */
class SqliteSchemaVersionTest {

    @get:Rule
    val tmpFolder = TemporaryFolder()

    private val magicHeaderBytes = byteArrayOf(
        'S'.code.toByte(), 'Q'.code.toByte(), 'L'.code.toByte(), 'i'.code.toByte(),
        't'.code.toByte(), 'e'.code.toByte(), ' '.code.toByte(), 'f'.code.toByte(),
        'o'.code.toByte(), 'r'.code.toByte(), 'm'.code.toByte(), 'a'.code.toByte(),
        't'.code.toByte(), ' '.code.toByte(), '3'.code.toByte(), 0
    )

    /** Construit un header SQLite minimal (100 octets) avec le user_version donné. */
    private fun writeMinimalSqliteFile(file: File, userVersion: Int) {
        val header = ByteArray(100)
        magicHeaderBytes.copyInto(header, 0)
        header[60] = (userVersion ushr 24 and 0xFF).toByte()
        header[61] = (userVersion ushr 16 and 0xFF).toByte()
        header[62] = (userVersion ushr 8 and 0xFF).toByte()
        header[63] = (userVersion and 0xFF).toByte()
        file.writeBytes(header)
    }

    @Test
    fun `lit correctement un user_version connu`() {
        val file = tmpFolder.newFile("test.db")
        writeMinimalSqliteFile(file, 8)

        assertEquals(8, SqliteSchemaVersion.read(file))
    }

    @Test
    fun `deux fichiers avec des user_version differents renvoient des valeurs differentes`() {
        val fileV7 = tmpFolder.newFile("v7.db")
        writeMinimalSqliteFile(fileV7, 7)
        val fileV8 = tmpFolder.newFile("v8.db")
        writeMinimalSqliteFile(fileV8, 8)

        assertEquals(7, SqliteSchemaVersion.read(fileV7))
        assertEquals(8, SqliteSchemaVersion.read(fileV8))
    }

    @Test
    fun `leve une exception si le fichier est absent`() {
        val missing = File(tmpFolder.root, "absent.db")

        assertThrows(Exception::class.java) {
            SqliteSchemaVersion.read(missing)
        }
    }

    @Test
    fun `leve une exception si le fichier est trop court`() {
        val tooShort = tmpFolder.newFile("tooshort.db").apply { writeBytes(ByteArray(10)) }

        assertThrows(Exception::class.java) {
            SqliteSchemaVersion.read(tooShort)
        }
    }

    @Test
    fun `leve une exception si le magic header SQLite est invalide`() {
        val notSqlite = tmpFolder.newFile("notsqlite.db").apply {
            writeBytes(ByteArray(100) { 0x42 })
        }

        assertThrows(Exception::class.java) {
            SqliteSchemaVersion.read(notSqlite)
        }
    }
}
