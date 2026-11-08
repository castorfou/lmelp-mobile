package com.lmelp.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Issue #160 : l'APK debug a son propre identifiant, pour s'installer à côté de
 * l'APK de release (com.lmelp.mobile) sans conflit de signature.
 * Les tests unitaires tournent sur la variante debug (testDebugUnitTest).
 */
class DebugApplicationIdTest {

    @Test
    fun `le build debug a l'identifiant com_lmelp_mobile_debug`() {
        assertEquals("com.lmelp.mobile.debug", BuildConfig.APPLICATION_ID)
    }

    @Test
    fun `le namespace du code reste com_lmelp_mobile`() {
        assertEquals("com.lmelp.mobile", BuildConfig::class.java.`package`?.name)
    }
}
