package com.lmelp.mobile.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LmelpRed = Color(0xFFB71C1C)
private val LmelpRedLight = Color(0xFFEF5350)
private val LmelpBackground = Color(0xFFFAFAFA)

private val LightColors = lightColorScheme(
    primary = LmelpRed,
    onPrimary = Color.White,
    primaryContainer = LmelpRedLight,
    onPrimaryContainer = Color.White,
    background = LmelpBackground,
    surface = Color.White,
)

private val DarkColors = darkColorScheme(
    primary = LmelpRedLight,
    onPrimary = Color.Black,
    primaryContainer = LmelpRed,
    onPrimaryContainer = Color.White,
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
