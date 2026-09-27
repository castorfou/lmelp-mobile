package com.lmelp.mobile

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathNode
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import com.lmelp.mobile.ui.theme.LmelpBleu
import com.lmelp.mobile.ui.theme.LmelpBordeaux
import com.lmelp.mobile.ui.theme.LmelpVert
import com.lmelp.mobile.ui.theme.navIndicatorColor
import com.lmelp.mobile.ui.theme.navSelectedIconColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Issue #142 : barre de navigation du bas inspirée de WhatsApp.
 *
 *   - icône contour au repos, icône pleine quand l'onglet est sélectionné ;
 *   - pastille de sélection très claire (pastel de la couleur de l'onglet) ;
 *   - icône sélectionnée dans une version très foncée de cette même couleur.
 *
 * Retour test device : pastille trop pâle (paraissait grise), icône si foncée
 * qu'elle paraissait noire, micro « contour » en fait plein — d'où les contrôles
 * de saturation et la comparaison des tracés plutôt que des instances.
 */
class BottomNavStyleTest {

    private val accents = mapOf(
        "bleu" to LmelpBleu,
        "bordeaux" to LmelpBordeaux,
        "vert" to LmelpVert,
    )

    // --- Icônes plein / contour ---

    @Test
    fun `icon renvoie l'icone pleine si selectionne et l'icone contour sinon`() {
        bottomNavItems.forEach { item ->
            assertEquals(item.label, item.selectedIcon, item.icon(selected = true))
            assertEquals(item.label, item.unselectedIcon, item.icon(selected = false))
        }
    }

    @Test
    fun `les onglets ont une vraie paire d'icones plein et contour`() {
        // Une icône contour a un contour intérieur de plus (le « creux ») que sa version
        // pleine. Comparer les instances ne suffit pas : Icons.Outlined.Mic est dessiné
        // plein, avec autant de sous-tracés qu'Icons.Filled.Mic (retour test device)
        val onglets = bottomNavItems.filter { it.route != Routes.SEARCH }
        assertEquals(4, onglets.size)
        onglets.forEach { item ->
            val plein = sousTraces(item.selectedIcon)
            val contour = sousTraces(item.unselectedIcon)
            assertTrue("${item.label} : $contour sous-tracés contour vs $plein plein", contour > plein)
        }
    }

    @Test
    fun `la loupe selectionnee a un verre plein avec un reflet`() {
        // Aucune icône Material n'a de verre plein : icône dessinée à la main, dont le
        // reflet est un creux obtenu par remplissage pair-impair dans le verre plein
        val recherche = bottomNavItems.single { it.route == Routes.SEARCH }
        assertEquals(LoupePleine, recherche.selectedIcon)
        val chemins = chemins(recherche.selectedIcon)
        assertTrue(chemins.any { it.pathFillType == PathFillType.EvenOdd })
        assertEquals(2, sousTraces(recherche.selectedIcon))
    }

    @Test
    fun `l'ordre des onglets suit l'ordre du swipe`() {
        assertEquals(
            listOf(Routes.HOME, Routes.EMISSIONS, Routes.PALMARES, Routes.RECOMMENDATIONS, Routes.SEARCH),
            bottomNavItems.map { it.route }
        )
    }

    @Test
    fun `chaque onglet hors Accueil garde la couleur de son ecran`() {
        val accentParRoute = bottomNavItems.associate { it.route to it.accentColor }
        assertEquals(LmelpBleu, accentParRoute[Routes.EMISSIONS])
        assertEquals(LmelpVert, accentParRoute[Routes.PALMARES])
        assertEquals(LmelpBordeaux, accentParRoute[Routes.RECOMMENDATIONS])
        assertEquals(LmelpVert, accentParRoute[Routes.SEARCH])
    }

    // --- Couleurs dérivées ---

    @Test
    fun `la pastille de selection est tres claire`() {
        accents.forEach { (nom, accent) ->
            val luminance = navIndicatorColor(accent).luminance()
            assertTrue("$nom : luminance $luminance", luminance > 0.6f)
        }
    }

    @Test
    fun `la pastille de selection garde la teinte de la page et ne parait pas grise`() {
        accents.forEach { (nom, accent) ->
            val saturation = saturationHsl(navIndicatorColor(accent))
            assertTrue("$nom : saturation $saturation", saturation >= 0.6f)
        }
    }

    @Test
    fun `l'icone selectionnee est foncee mais coloree, pas noire`() {
        accents.forEach { (nom, accent) ->
            val icone = navSelectedIconColor(accent)
            val luminance = icone.luminance()
            assertTrue("$nom : luminance $luminance", luminance in 0.02f..0.15f)
            val saturation = saturationHsl(icone)
            assertTrue("$nom : saturation $saturation", saturation >= 0.6f)
        }
    }

    @Test
    fun `les couleurs derivees gardent la teinte de l'onglet`() {
        accents.forEach { (nom, accent) ->
            val attendue = composanteDominante(accent)
            assertEquals("$nom (pastille)", attendue, composanteDominante(navIndicatorColor(accent)))
            assertEquals("$nom (icône)", attendue, composanteDominante(navSelectedIconColor(accent)))
        }
    }

    @Test
    fun `l'icone est lisible sur la pastille`() {
        accents.forEach { (nom, accent) ->
            val ratio = contraste(navIndicatorColor(accent), navSelectedIconColor(accent))
            assertTrue("$nom : contraste $ratio", ratio >= 4.5f)
        }
    }

    @Test
    fun `sans accent les couleurs restent neutres et lisibles`() {
        val pastille = navIndicatorColor(null)
        val icone = navSelectedIconColor(null)
        assertTrue(pastille.luminance() > 0.6f)
        assertTrue(icone.luminance() < 0.15f)
    }

    private fun chemins(icon: ImageVector): List<VectorPath> {
        fun collecte(group: VectorGroup): List<VectorPath> = group.flatMap { node ->
            when (node) {
                is VectorPath -> listOf(node)
                is VectorGroup -> collecte(node)
            }
        }
        return collecte(icon.root)
    }

    /** Nombre de sous-tracés (MoveTo) du dessin de l'icône. */
    private fun sousTraces(icon: ImageVector): Int =
        chemins(icon).flatMap { it.pathData }.count { it is PathNode.MoveTo || it is PathNode.RelativeMoveTo }

    /** Saturation HSL (0 = gris, 1 = couleur pure). */
    private fun saturationHsl(color: Color): Float {
        val max = maxOf(color.red, color.green, color.blue)
        val min = minOf(color.red, color.green, color.blue)
        val l = (max + min) / 2f
        if (max == min) return 0f
        return (max - min) / (1f - kotlin.math.abs(2f * l - 1f))
    }

    private fun composanteDominante(color: Color): String =
        mapOf("r" to color.red, "g" to color.green, "b" to color.blue).maxBy { it.value }.key

    /** Ratio de contraste WCAG entre deux couleurs. */
    private fun contraste(a: Color, b: Color): Float {
        val (clair, fonce) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (clair + 0.05f) / (fonce + 0.05f)
    }
}
