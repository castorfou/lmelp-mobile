package com.lmelp.mobile

import androidx.car.app.AppInfo
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Issue #99 : Android Auto affichait l'appli mais refusait de l'ouvrir
 * (« Le Masque et la Plume has encountered an unexpected error »), avec dans logcat :
 * `IllegalArgumentException: Min API level not declared in manifest (androidx.car.app.minCarApiLevel)`.
 *
 * `AppInfo.create()` lit cette clé dans les meta-data de `<application>` uniquement.
 * Le manifest déclarait `androidx.car.app.minApiLevel` (mauvais nom) dans `<service>`
 * (mauvais endroit).
 */
class CarAppManifestTest {

    private val androidNs = "http://schemas.android.com/apk/res/android"

    private fun applicationElement(): Element {
        val manifest = File("src/main/AndroidManifest.xml")
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val doc = factory.newDocumentBuilder().parse(manifest)
        return doc.getElementsByTagName("application").item(0) as Element
    }

    /** meta-data enfants directs de l'élément (pas ceux des services ou activités). */
    private fun Element.directMetaData(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val children = childNodes
        for (i in 0 until children.length) {
            val node = children.item(i)
            if (node is Element && node.tagName == "meta-data") {
                result[node.getAttributeNS(androidNs, "name")] = node.getAttributeNS(androidNs, "value")
            }
        }
        return result
    }

    @Test
    fun `minCarApiLevel est declare dans application, ou AppInfo le cherche`() {
        val metaData = applicationElement().directMetaData()
        val value = metaData[AppInfo.MIN_API_LEVEL_METADATA_KEY]
        assertNotNull(
            "${AppInfo.MIN_API_LEVEL_METADATA_KEY} absent des meta-data de <application> : " +
                "Android Auto refusera d'ouvrir l'appli",
            value
        )
        assertTrue("minCarApiLevel doit être un entier ≥ 1, trouvé : $value", (value!!.toIntOrNull() ?: 0) >= 1)
    }
}
