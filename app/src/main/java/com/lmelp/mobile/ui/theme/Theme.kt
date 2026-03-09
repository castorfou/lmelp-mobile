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

// Codes couleur pour les notes (identiques au back-office)
val NoteExcellent = Color(0xFF00C851)   // >= 9  : vert vif
val NoteBien      = Color(0xFF8BC34A)   // >= 7  : vert clair
val NoteMoyen     = Color(0xFFCDDC39)   // >= 5  : jaune-vert
val NoteFaible    = Color(0xFFF44336)   // < 5   : rouge

/** Retourne la couleur de fond du badge selon les seuils du back-office. */
fun couleurNote(note: Double): Color = when {
    note >= 9.0 -> NoteExcellent
    note >= 7.0 -> NoteBien
    note >= 5.0 -> NoteMoyen
    else        -> NoteFaible
}

/** Retourne la couleur du texte sur le badge : noir pour le jaune-vert (faible contraste), blanc sinon. */
fun couleurTexteNote(note: Double): Color =
    if (note >= 5.0 && note < 7.0) Color(0xFF333333) else Color.White

/**
 * Formate une note en supprimant le ".0" pour les entiers.
 * Ex: 8.0 → "8", 8.5 → "8.5"
 */
fun formatNote(note: Double): String =
    if (note == note.toLong().toDouble()) "${note.toLong()}" else "%.1f".format(note)

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
