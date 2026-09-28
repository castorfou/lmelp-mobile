package com.lmelp.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
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

/** Géométrie des cartes des listes d'œuvres (issue #143), alignée sur la carte Émission. */
object BookListCardDefaults {
    /** Même hauteur que la carte Émission : 120dp moins 2×4dp de padding vertical. */
    val CardHeight: Dp = 112.dp
    /** Largeur de couverture au ratio livre 2:3. */
    val CoverWidth: Dp = 75.dp
}

/**
 * Carte d'une liste d'œuvres : couverture pleine hauteur collée au bord gauche
 * (clippée par les coins de la carte, comme la carte Émission), puis [content] centré verticalement.
 * La couverture vient de [urlCover] (livres du Masque) ou, à défaut, de [coverData]
 * (vignette Calibre embarquée des livres hors Masque, issue #145).
 * Sans couverture, un bloc neutre de même taille garde les textes alignés d'une carte à l'autre.
 *
 * Le padding externe et le clic sont fournis par l'appelant via [modifier].
 */
@Composable
fun BookListCard(
    urlCover: String?,
    modifier: Modifier = Modifier,
    coverData: ByteArray? = null,
    content: @Composable RowScope.() -> Unit
) {
    val coverModel: Any? = urlCover ?: coverData
    Card(modifier = modifier.fillMaxWidth().height(BookListCardDefaults.CardHeight)) {
        Row(modifier = Modifier.fillMaxSize()) {
            if (coverModel != null) {
                AsyncImage(
                    model = coverModel,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(BookListCardDefaults.CoverWidth)
                        .fillMaxHeight()
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(BookListCardDefaults.CoverWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )
        }
    }
}

/**
 * Badge Calibre : affiche ✓ (vert) si lu, ◯ (gris) si dans la bibliothèque mais non lu,
 * et la note personnelle (X/10) si le livre est lu et noté.
 * N'affiche rien si le livre n'est pas dans la bibliothèque Calibre.
 */
@Composable
fun CalibreBadge(
    calibreInLibrary: Boolean,
    calibreLu: Boolean,
    calibreRating: Double?,
    modifier: Modifier = Modifier
) {
    if (!calibreInLibrary) return
    Column(modifier = modifier, horizontalAlignment = Alignment.End) {
        Text(
            text = if (calibreLu) "✓" else "◯",
            style = MaterialTheme.typography.bodyMedium,
            color = if (calibreLu) Color(0xFF2E7D32) else Color.Gray
        )
        if (calibreLu) {
            calibreRating?.let {
                Text(
                    text = "${it.toInt()}/10",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF2E7D32)
                )
            }
        }
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
