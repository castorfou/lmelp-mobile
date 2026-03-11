package com.lmelp.mobile

import com.lmelp.mobile.ui.emissions.formatDateLong
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests unitaires pour formatDateLong — formatage de date ISO en français long.
 * Ex: "2026-03-01T10:59:39Z" → "01 mars 2026"
 */
class EmissionFormatTest {

    @Test
    fun `janvier`() = assertEquals("15 janvier 2025", formatDateLong("2025-01-15"))

    @Test
    fun `fevrier`() = assertEquals("28 février 2024", formatDateLong("2024-02-28"))

    @Test
    fun `mars`() = assertEquals("01 mars 2026", formatDateLong("2026-03-01T10:59:39Z"))

    @Test
    fun `avril`() = assertEquals("10 avril 2023", formatDateLong("2023-04-10"))

    @Test
    fun `mai`() = assertEquals("05 mai 2022", formatDateLong("2022-05-05"))

    @Test
    fun `juin`() = assertEquals("20 juin 2021", formatDateLong("2021-06-20"))

    @Test
    fun `juillet`() = assertEquals("14 juillet 2020", formatDateLong("2020-07-14"))

    @Test
    fun `aout`() = assertEquals("01 août 2019", formatDateLong("2019-08-01"))

    @Test
    fun `septembre`() = assertEquals("30 septembre 2018", formatDateLong("2018-09-30"))

    @Test
    fun `octobre`() = assertEquals("11 octobre 2017", formatDateLong("2017-10-11"))

    @Test
    fun `novembre`() = assertEquals("25 novembre 2016", formatDateLong("2016-11-25"))

    @Test
    fun `decembre`() = assertEquals("31 décembre 2015", formatDateLong("2015-12-31"))
}
