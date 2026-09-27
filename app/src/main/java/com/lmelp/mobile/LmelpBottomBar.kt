package com.lmelp.mobile

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.lmelp.mobile.ui.theme.LmelpBleu
import com.lmelp.mobile.ui.theme.LmelpBordeaux
import com.lmelp.mobile.ui.theme.LmelpVert
import com.lmelp.mobile.ui.theme.navIndicatorColor
import com.lmelp.mobile.ui.theme.navSelectedIconColor

/**
 * Onglet de la barre de navigation du bas (issue #142, style WhatsApp) :
 * icône contour au repos, icône pleine quand l'onglet est sélectionné.
 */
data class BottomNavItem(
    val label: String,
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val accentColor: Color? = null
) {
    fun icon(selected: Boolean): ImageVector = if (selected) selectedIcon else unselectedIcon
}

/**
 * Loupe au verre plein avec un reflet en croissant dans l'angle haut-gauche : version
 * « sélectionnée » de Search, qu'aucune icône Material ne propose (Filled.Search a un
 * verre creux, identique à Outlined.Search). Contour de Filled.Search sans son creux
 * intérieur, et reflet percé dans le verre par remplissage pair-impair.
 */
val LoupePleine: ImageVector = ImageVector.Builder(
    name = "LoupePleine",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f
).addPath(
    pathData = addPathNodes(
        // Verre plein + manche (contour extérieur de Filled.Search)
        "M15.5,14h-0.79l-0.28,-0.27C15.41,12.59 16,11.11 16,9.5 16,5.91 13.09,3 9.5,3" +
            "S3,5.91 3,9.5 5.91,16 9.5,16c1.61,0 3.09,-0.59 4.23,-1.57l0.27,0.28v0.79" +
            "l5,4.99L20.49,19l-4.99,-5z" +
            // Reflet : croissant entre les rayons 3 et 4.2 du verre (centre 9.5,9.5),
            // de 195° à 255°, extrémités arrondies
            "M5.44,8.41A4.2,4.2 0,0 1,8.41 5.44A0.6,0.6 0,0 1,8.72 6.6" +
            "A3,3 0,0 0,6.6 8.72A0.6,0.6 0,0 1,5.44 8.41z"
    ),
    pathFillType = PathFillType.EvenOdd,
    fill = SolidColor(Color.Black)
).build()

/** Onglets dans l'ordre circulaire du swipe (Home inclus, bien que la barre y soit masquée). */
val bottomNavItems = listOf(
    BottomNavItem("Accueil", Routes.HOME, Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem("Émissions", Routes.EMISSIONS, Icons.Filled.Mic, Icons.Outlined.MicNone, LmelpBleu),
    BottomNavItem("Palmarès", Routes.PALMARES, Icons.Filled.Star, Icons.Outlined.StarOutline, LmelpVert),
    BottomNavItem("Conseils", Routes.RECOMMENDATIONS, Icons.Filled.Lightbulb, Icons.Outlined.Lightbulb, LmelpBordeaux),
    BottomNavItem("Recherche", Routes.SEARCH, LoupePleine, Icons.Outlined.Search, LmelpVert),
)

@Composable
fun LmelpBottomBar(
    currentRoute: String?,
    showLabels: Boolean,
    onItemClick: (String) -> Unit
) {
    val onSurface = MaterialTheme.colorScheme.onSurface
    Column {
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
        NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
            bottomNavItems.forEach { item ->
                val selected = currentRoute == item.route
                NavigationBarItem(
                    selected = selected,
                    onClick = { onItemClick(item.route) },
                    icon = { Icon(item.icon(selected), contentDescription = item.label) },
                    label = if (showLabels) {
                        {
                            Text(
                                item.label,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    } else null,
                    colors = NavigationBarItemDefaults.colors(
                        indicatorColor = navIndicatorColor(item.accentColor),
                        selectedIconColor = navSelectedIconColor(item.accentColor),
                        selectedTextColor = onSurface,
                        unselectedIconColor = onSurface,
                        unselectedTextColor = onSurface
                    )
                )
            }
        }
    }
}
