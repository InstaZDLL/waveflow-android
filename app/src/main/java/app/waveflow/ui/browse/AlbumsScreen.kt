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
import app.waveflow.model.Album
import app.waveflow.model.Library
import app.waveflow.ui.components.CoverTile
import app.waveflow.ui.components.CoverTileMinWidth
import app.waveflow.ui.components.LibraryStateContainer
import app.waveflow.ui.components.TileGutter
import app.waveflow.ui.components.TileRowSpacing
import app.waveflow.ui.components.TileSpacing

/**
 * Onglet Albums : une grille de tuiles — la pochette, le titre, l'artiste —,
 * adaptative selon la largeur d'écran.
 */
@Composable
fun AlbumsScreen(
    library: Library,
    onAlbumClick: (Album) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    LibraryStateContainer(
        isLoading = library.isLoading,
        errorMessage = library.errorMessage,
        isEmpty = library.isEmpty,
        emptyMessage = "Aucun album trouvé sur cet appareil.",
        onRetry = onRetry,
        modifier = modifier,
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = CoverTileMinWidth),
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
            items(library.albums, key = { it.id }) { album ->
                CoverTile(
                    artworkUri = album.artworkUri,
                    title = album.title,
                    subtitle = album.displayArtist,
                    onClick = { onAlbumClick(album) },
                )
            }
        }
    }
}
