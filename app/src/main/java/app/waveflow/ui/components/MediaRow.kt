package app.waveflow.ui.components

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Ligne de liste pour une entité autre qu'un morceau : album ou artiste.
 *
 * [SongRow] reste distincte — elle porte la durée, l'indicateur de lecture en
 * cours et l'appui long. Ici il n'y a qu'une pochette, deux lignes de texte et
 * une destination.
 *
 * @param artworkShape ronde pour un artiste, arrondie pour un album.
 * @param onClick `null` pour une ligne purement informative. Rendre le clic
 *   facultatif plutôt que d'en passer un vide : un `Modifier.clickable` inerte
 *   annonce quand même la ligne comme actionnable à TalkBack, et l'ondulation
 *   promet une navigation qui n'arrive pas.
 */
@Composable
fun MediaRow(
    artworkUri: Uri?,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    artworkShape: Shape = RoundedCornerShape(6.dp),
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Artwork(
            artworkUri = artworkUri,
            shape = artworkShape,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
