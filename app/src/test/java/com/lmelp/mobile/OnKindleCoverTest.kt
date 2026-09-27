package com.lmelp.mobile

import com.lmelp.mobile.data.db.OnKindleAvecConseilRow
import com.lmelp.mobile.data.db.OnKindleDao
import com.lmelp.mobile.data.repository.OnKindleRepository
import com.lmelp.mobile.viewmodel.TriMode
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Vignette de couverture Calibre des livres de la liseuse sans couverture Babelio (issue #145).
 */
class OnKindleCoverTest {

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 1, 2)

    private fun row(livreId: String, urlCover: String?, cover: ByteArray?) = OnKindleAvecConseilRow(
        livreId = livreId,
        titre = "Titre $livreId",
        auteurNom = null,
        urlBabelio = null,
        urlCover = urlCover,
        calibreLu = 0,
        calibreRating = null,
        noteMoyenne = null,
        nbAvis = 0,
        scoreHybride = null,
        cover = cover
    )

    @Test
    fun `getOnKindle propage la vignette Calibre et l url Babelio`() = runTest {
        val dao = mock<OnKindleDao>()
        whenever(dao.getOnKindleAvecConseil(any(), any())).thenReturn(
            listOf(
                row("calibre_1768", urlCover = null, cover = jpeg),
                row("l1", urlCover = "https://babelio/c.jpg", cover = null)
            )
        )

        val result = OnKindleRepository(dao).getOnKindle(true, true, TriMode.ALPHA)
            .associateBy { it.livreId }

        assertArrayEquals(jpeg, result.getValue("calibre_1768").coverData)
        assertNull(result.getValue("calibre_1768").urlCover)
        assertEquals("https://babelio/c.jpg", result.getValue("l1").urlCover)
        assertNull(result.getValue("l1").coverData)
    }
}
