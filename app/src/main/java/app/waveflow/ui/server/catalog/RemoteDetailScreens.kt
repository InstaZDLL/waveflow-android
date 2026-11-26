package app.waveflow.ui.server.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.RemoteAlbum
import app.waveflow.model.RemoteSong
import app.waveflow.model.orUnknownArtist
import app.waveflow.playback.mediaId
import app.waveflow.ui.albumCountLabel
import app.waveflow.ui.browse.DetailHeader
import app.waveflow.ui.components.CenteredMessage
import app.waveflow.ui.components.CoverTile
import app.waveflow.ui.components.CoverTileMinWidth
import app.waveflow.ui.components.MediaRow
import app.waveflow.ui.components.TileGutter
import app.waveflow.ui.components.TileRowSpacing
import app.waveflow.ui.components.TileSpacing
import app.waveflow.ui.formatDuration
import app.waveflow.ui.theme.WaveFlowTheme
import app.waveflow.ui.trackCountLabel

/** Un album distant et ses morceaux. */
@Composable
fun RemoteAlbumDetailScreen(
    state: AlbumDetailState,
    nowPlayingMediaId: String?,
    onSongClick: (RemoteSong) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    DetailContainer(state = state, onRetry = onRetry, modifier = modifier) { detail ->
        LazyColumn(
            contentPadding = PaddingValues(bottom = bottomPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                // Le même en-tête que les albums locaux : il ne connaît que des
                // chaînes, une image et deux rappels. Rien ne justifiait d'en
                // maintenir un second une fois la lecture distante branchée.
                DetailHeader(
                    artworkUri = detail.album.artworkUri,
                    kicker = "Album",
                    title = detail.album.title,
                    subtitle = detail.album.artist.orUnknownArtist(),
                    trackCount = detail.songs.size,
                    durationMs = detail.songs.sumOf { it.durationMs },
                    onPlay = onPlay,
                    onShuffle = onShuffle,
                    playEnabled = detail.songs.isNotEmpty(),
                )
            }

            items(detail.songs, key = { it.id }) { song ->
                RemoteSongRow(
                    song = song,
                    isCurrent = song.mediaId == nowPlayingMediaId,
                    onClick = { onSongClick(song) },
                )
            }
        }
    }
}

/** Un artiste distant et ses albums. */
@Composable
fun RemoteArtistDetailScreen(
    state: ArtistDetailState,
    onAlbumClick: (RemoteAlbum) -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    DetailContainer(state = state, onRetry = onRetry, modifier = modifier) { detail ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = CoverTileMinWidth),
            contentPadding = PaddingValues(start = TileGutter, end = TileGutter, bottom = bottomPadding + TileGutter),
            horizontalArrangement = Arrangement.spacedBy(TileSpacing),
            verticalArrangement = Arrangement.spacedBy(TileRowSpacing),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "entete", span = { GridItemSpan(maxLineSpan) }) {
                // Sans pilules, contrairement à l'album : le détail d'un
                // artiste rend ses albums, pas ses pistes. Proposer « Lecture »
                // ici demanderait de charger chaque album d'abord. La grille a
                // déjà sa marge : l'en-tête ne la redouble pas.
                DetailHeader(
                    artworkUri = detail.artist.artworkUri,
                    kicker = "Artiste",
                    title = detail.artist.name,
                    subtitle = albumCountLabel(detail.artist.albumCount ?: detail.albums.size),
                    trackCount = null,
                    durationMs = 0L,
                    onPlay = null,
                    onShuffle = {},
                    artworkShape = WaveFlowTheme.shapes.portrait,
                    margins = PaddingValues(top = 16.dp, bottom = 28.dp),
                )
            }

            items(detail.albums, key = { it.id }) { album ->
                CoverTile(
                    artworkUri = album.artworkUri,
                    title = album.title,
                    subtitle = album.year?.toString(),
                    onClick = { onAlbumClick(album) },
                )
            }
        }
    }
}

/**
 * Chargement, échec, ou contenu.
 *
 * Un détail se charge d'un seul appel : contrairement aux listes paginées, il
 * n'y a pas de contenu partiel à préserver derrière une erreur.
 */
@Composable
private fun <T> DetailContainer(
    state: DetailState<T>,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    val value = state.value
    when {
        // Le modificateur de l'appelant porte la mise en page attendue par
        // l'écran : le perdre ici la laisserait à la LazyColumn par défaut.
        value != null -> Box(modifier = modifier) { content(value) }

        state.isLoading -> Box(modifier = modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        else -> Box(modifier = modifier.fillMaxSize()) {
            CenteredMessage(
                message = state.errorMessage ?: "Introuvable sur le serveur.",
                modifier = Modifier.align(Alignment.Center),
                action = { Button(onClick = onRetry) { Text("Réessayer") } },
            )
        }
    }
}

@Composable
private fun RemoteSongRow(
    song: RemoteSong,
    isCurrent: Boolean,
    onClick: () -> Unit,
) {
    MediaRow(
        artworkUri = song.artworkUri,
        title = song.title,
        subtitle = listOfNotNull(
            song.artist?.takeIf { it.isNotBlank() },
            formatDuration(song.durationMs),
        ).joinToString(" · "),
        onClick = onClick,
        titleColor = if (isCurrent) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
}
