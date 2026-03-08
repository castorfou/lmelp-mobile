package com.lmelp.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LmelpRed = Color(0xFFB71C1C)
private val LmelpRedLight = Color(0xFFEF5350)

// Bleu nuit — couleur principale du bandeau hero HomeScreen
val LmelpNightBlue = Color(0xFF12192C)
val LmelpNightBlueEnd = Color(0xFF1E2D4A)

// Couleurs des tuiles de navigation (réutilisées dans les TopAppBar)
val LmelpBleu = Color(0xFF1565C0)       // Émissions
val LmelpBordeaux = Color(0xFFA10127)   // Critiques, Conseils
val LmelpVert = Color(0xFF00897B)       // Palmarès, Recherche

private val LightColors = lightColorScheme(
    primary = LmelpRed,
    onPrimary = Color.White,
    primaryContainer = LmelpRedLight,
    onPrimaryContainer = Color.White,
    background = Color.White,
    surface = Color.White,
    onBackground = Color(0xFF1A1A1A),
    onSurface = Color(0xFF1A1A1A),
)

private val DarkColors = darkColorScheme(
    primary = LmelpRedLight,
    onPrimary = Color.Black,
    primaryContainer = LmelpRed,
    onPrimaryContainer = Color.White,
    background = LmelpNightBlue,
    surface = LmelpNightBlueEnd,
    onBackground = Color.White,
    onSurface = Color.White,
)

@Composable
fun LmelpTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
