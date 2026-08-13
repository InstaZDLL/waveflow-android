package app.waveflow.ui.server.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import app.waveflow.playback.mediaId
import app.waveflow.model.orUnknownArtist
import app.waveflow.ui.albumCountLabel
import app.waveflow.ui.components.CenteredMessage
import app.waveflow.ui.components.MediaRow
import app.waveflow.ui.formatDuration
import app.waveflow.ui.trackCountLabel

/** Un album distant et ses morceaux. */
@Composable
fun RemoteAlbumDetailScreen(
    state: AlbumDetailState,
    nowPlayingMediaId: String?,
    onSongClick: (RemoteSong) -> Unit,
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
                RemoteDetailHeader(
                    artworkUri = detail.album.artworkUri,
                    title = detail.album.title,
                    subtitle = detail.album.artist.orUnknownArtist(),
                    summary = listOfNotNull(
                        trackCountLabel(detail.songs.size),
                        detail.album.year?.toString(),
                    ).joinToString(" · "),
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
        LazyColumn(
            contentPadding = PaddingValues(bottom = bottomPadding),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                RemoteDetailHeader(
                    artworkUri = detail.artist.artworkUri,
                    title = detail.artist.name,
                    subtitle = "Artiste",
                    summary = albumCountLabel(detail.artist.albumCount ?: detail.albums.size),
                )
            }

            items(detail.albums, key = { it.id }) { album ->
                MediaRow(
                    artworkUri = album.artworkUri,
                    title = album.title,
                    subtitle = album.year?.toString().orEmpty(),
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

/**
 * En-tête d'un détail distant.
 *
 * `DetailHeader` de la navigation locale n'est pas réutilisé : il porte les
 * boutons Lecture et Aléatoire, or rien n'est encore lisible depuis le serveur.
 * Proposer des commandes inertes serait pire que de ne pas les montrer.
 */
@Composable
private fun RemoteDetailHeader(
    artworkUri: android.net.Uri?,
    title: String,
    subtitle: String,
    summary: String,
) {
    MediaRow(
        artworkUri = artworkUri,
        title = title,
        subtitle = listOf(subtitle, summary).filter { it.isNotBlank() }.joinToString(" · "),
        artworkShape = CircleShape,
        modifier = Modifier.fillMaxWidth(),
    )
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
