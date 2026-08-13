package app.waveflow.ui.server.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.RemoteAlbum
import app.waveflow.model.RemoteArtist
import app.waveflow.model.orUnknownArtist
import app.waveflow.ui.albumCountLabel
import app.waveflow.ui.components.MediaRow

private enum class CatalogTab(val label: String) {
    Albums("Albums"),
    Artists("Artistes"),
}

/**
 * Catalogue d'un serveur connecté.
 *
 * En listes et non en grille de pochettes, contrairement aux albums locaux :
 * le catalogue d'un serveur se parcourt volontiers par recherche et par nom,
 * là où la bibliothèque de l'appareil tient dans quelques écrans.
 */
@Composable
fun ServerCatalogScreen(
    albums: PagedList<RemoteAlbum>,
    artists: PagedList<RemoteArtist>,
    onAlbumClick: (RemoteAlbum) -> Unit,
    onArtistClick: (RemoteArtist) -> Unit,
    onLoadMoreAlbums: () -> Unit,
    onLoadMoreArtists: () -> Unit,
    onRetryAlbums: () -> Unit,
    onRetryArtists: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    var tab by rememberSaveable { mutableStateOf(CatalogTab.Albums) }

    // Remontés ici : l'onglet masqué quitte la composition, et un état déclaré
    // à l'intérieur repartirait donc du haut à chaque retour. `rememberSaveable`
    // les fait aussi survivre à une rotation.
    val albumsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    val artistsListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }

    Column(modifier = modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = tab.ordinal) {
            CatalogTab.entries.forEach { entry ->
                Tab(
                    selected = tab == entry,
                    onClick = { tab = entry },
                    text = { Text(entry.label) },
                )
            }
        }

        when (tab) {
            CatalogTab.Albums -> AlbumsTab(
                state = albums,
                listState = albumsListState,
                onAlbumClick = onAlbumClick,
                onLoadMore = onLoadMoreAlbums,
                onRetry = onRetryAlbums,
                bottomPadding = bottomPadding,
            )

            CatalogTab.Artists -> ArtistsTab(
                state = artists,
                listState = artistsListState,
                onArtistClick = onArtistClick,
                onLoadMore = onLoadMoreArtists,
                onRetry = onRetryArtists,
                bottomPadding = bottomPadding,
            )
        }
    }
}

@Composable
private fun AlbumsTab(
    state: PagedList<RemoteAlbum>,
    listState: LazyListState,
    onAlbumClick: (RemoteAlbum) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    bottomPadding: Dp,
) {
    LoadMoreOnApproachingEnd(listState, state.items.size, onLoadMore)

    PagedListContainer(
        state = state,
        emptyMessage = "Ce serveur n'a aucun album. Lancez une analyse depuis son administration.",
        onRetry = onRetry,
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = bottomPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.items, key = { it.id }) { album ->
                MediaRow(
                    artworkUri = album.artworkUri,
                    title = album.title,
                    subtitle = album.artist.orUnknownArtist(),
                    onClick = { onAlbumClick(album) },
                )
            }
            item { PagedListFooter(state = state, onRetry = onRetry) }
        }
    }
}

@Composable
private fun ArtistsTab(
    state: PagedList<RemoteArtist>,
    listState: LazyListState,
    onArtistClick: (RemoteArtist) -> Unit,
    onLoadMore: () -> Unit,
    onRetry: () -> Unit,
    bottomPadding: Dp,
) {
    LoadMoreOnApproachingEnd(listState, state.items.size, onLoadMore)

    PagedListContainer(
        state = state,
        emptyMessage = "Ce serveur n'a aucun artiste.",
        onRetry = onRetry,
    ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(bottom = bottomPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(state.items, key = { it.id }) { artist ->
                MediaRow(
                    artworkUri = artist.artworkUri,
                    title = artist.name,
                    subtitle = artist.albumCount?.let(::albumCountLabel).orEmpty(),
                    onClick = { onArtistClick(artist) },
                    artworkShape = CircleShape,
                )
            }
            item { PagedListFooter(state = state, onRetry = onRetry) }
        }
    }
}

