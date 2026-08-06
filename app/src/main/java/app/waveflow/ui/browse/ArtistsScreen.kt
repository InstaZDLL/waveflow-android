package app.waveflow.ui.browse

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.Artist
import app.waveflow.model.Library
import app.waveflow.ui.albumCountLabel
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.components.LibraryStateContainer
import app.waveflow.ui.trackCountLabel

/** Onglet Artistes. */
@Composable
fun ArtistsScreen(
    library: Library,
    onArtistClick: (Artist) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    LibraryStateContainer(
        isLoading = library.isLoading,
        errorMessage = library.errorMessage,
        isEmpty = library.isEmpty,
        emptyMessage = "Aucun artiste trouvé sur cet appareil.",
        onRetry = onRetry,
        modifier = modifier,
    ) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = bottomPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(library.artists, key = { it.id }) { artist ->
                ArtistRow(artist = artist, onClick = { onArtistClick(artist) })
            }
        }
    }
}

@Composable
private fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // Rond plutôt que carré : le repère visuel habituel pour un artiste.
        Artwork(
            artworkUri = artist.artworkUri,
            shape = CircleShape,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${albumCountLabel(artist.albumCount)} · ${trackCountLabel(artist.trackCount)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}
