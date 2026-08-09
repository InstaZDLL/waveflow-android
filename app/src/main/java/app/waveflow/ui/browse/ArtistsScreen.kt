package app.waveflow.ui.browse

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.Artist
import app.waveflow.model.Library
import app.waveflow.ui.albumCountLabel
import app.waveflow.ui.components.LibraryStateContainer
import app.waveflow.ui.components.MediaRow
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
                MediaRow(
                    artworkUri = artist.artworkUri,
                    title = artist.name,
                    subtitle = "${albumCountLabel(artist.albumCount)} · ${trackCountLabel(artist.trackCount)}",
                    onClick = { onArtistClick(artist) },
                    // Rond plutôt que carré : le repère visuel habituel pour
                    // un artiste.
                    artworkShape = CircleShape,
                )
            }
        }
    }
}
