package app.waveflow.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.Album
import app.waveflow.model.Song
import app.waveflow.ui.components.CenteredMessage
import app.waveflow.ui.components.SongRow

/**
 * Détail d'un album : en-tête puis pistes.
 *
 * [album] peut être `null` si la bibliothèque a changé pendant que l'écran
 * était ouvert (fichier supprimé, re-scan) — on le dit plutôt que d'afficher
 * une page vide.
 */
@Composable
fun AlbumDetailScreen(
    album: Album?,
    songs: List<Song>,
    nowPlayingId: Long?,
    onSongClick: (Song) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    if (album == null) {
        Box(modifier = modifier.fillMaxSize()) {
            CenteredMessage(
                message = "Cet album n'est plus disponible.",
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(bottom = bottomPadding),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            DetailHeader(
                artworkUri = album.artworkUri,
                title = album.title,
                subtitle = album.displayArtist,
                trackCount = album.trackCount,
                durationMs = album.durationMs,
                onPlay = onPlay,
                onShuffle = onShuffle,
            )
        }

        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                isCurrent = song.id == nowPlayingId,
                onClick = { onSongClick(song) },
                // La pochette est déjà en grand juste au-dessus.
                showArtwork = false,
            )
        }
    }
}
