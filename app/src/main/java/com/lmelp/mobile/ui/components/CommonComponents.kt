package com.lmelp.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lmelp.mobile.ui.theme.couleurNote
import com.lmelp.mobile.ui.theme.couleurTexteNote
import com.lmelp.mobile.ui.theme.formatNote

@Composable
fun LoadingIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorMessage(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = "Erreur : $message")
    }
}

@Composable
fun EmptyState(message: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = message)
    }
}

/**
 * Badge coloré pour afficher une note, identique au back-office lmelp.
 * Fond coloré selon le seuil, texte blanc (noir pour le jaune-vert), gras.
 *
 * @param note    La note (Double)
 * @param suffix  Suffixe optionnel affiché après la note (ex: "/10")
 */
@Composable
fun NoteBadge(
    note: Double,
    suffix: String = "",
    fontSize: TextUnit = 14.sp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(couleurNote(note))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = "${formatNote(note)}$suffix",
            color = couleurTexteNote(note),
            fontWeight = FontWeight.Bold,
            fontSize = fontSize
        )
    }
}
