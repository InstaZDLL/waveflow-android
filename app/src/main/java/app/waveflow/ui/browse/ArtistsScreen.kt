package app.waveflow.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.Artist
import app.waveflow.model.Library
import app.waveflow.ui.albumCountLabel
import app.waveflow.ui.components.LibraryStateContainer
import app.waveflow.ui.components.PortraitTile
import app.waveflow.ui.components.PortraitTileMinWidth
import app.waveflow.ui.components.TileGutter
import app.waveflow.ui.components.TileRowSpacing
import app.waveflow.ui.components.TileSpacing
import app.waveflow.ui.trackCountLabel

/**
 * Onglet Artistes : une grille de portraits, en disques — le repère habituel
 * d'un artiste —, le nom et ce qu'on a de lui dessous.
 */
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
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = PortraitTileMinWidth),
            contentPadding = PaddingValues(
                start = TileGutter,
                end = TileGutter,
                top = 16.dp,
                bottom = bottomPadding + TileGutter,
            ),
            horizontalArrangement = Arrangement.spacedBy(TileSpacing),
            verticalArrangement = Arrangement.spacedBy(TileRowSpacing),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(library.artists, key = { it.id }) { artist ->
                PortraitTile(
                    artworkUri = artist.artworkUri,
                    name = artist.name,
                    subtitle = "${albumCountLabel(artist.albumCount)} · ${trackCountLabel(artist.trackCount)}",
                    onClick = { onArtistClick(artist) },
                )
            }
        }
    }
}
