package app.waveflow.ui.browse

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.Artist
import app.waveflow.model.Song
import app.waveflow.ui.albumCountLabel
import app.waveflow.ui.components.CenteredMessage
import app.waveflow.ui.components.SongRow

/** Détail d'un artiste : en-tête puis tous ses morceaux. */
@Composable
fun ArtistDetailScreen(
    artist: Artist?,
    songs: List<Song>,
    nowPlayingId: Long?,
    onSongClick: (Song) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
    onSongLongClick: (Song) -> Unit = {},
) {
    if (artist == null) {
        Box(modifier = modifier.fillMaxSize()) {
            CenteredMessage(
                message = "Cet artiste n'est plus disponible.",
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
                artworkUri = artist.artworkUri,
                title = artist.name,
                subtitle = albumCountLabel(artist.albumCount),
                trackCount = artist.trackCount,
                durationMs = songs.sumOf { it.durationMs },
                onPlay = onPlay,
                onShuffle = onShuffle,
                artworkShape = CircleShape,
            )
        }

        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                isCurrent = song.id == nowPlayingId,
                onClick = { onSongClick(song) },
                onLongClick = { onSongLongClick(song) },
            )
        }
    }
}
