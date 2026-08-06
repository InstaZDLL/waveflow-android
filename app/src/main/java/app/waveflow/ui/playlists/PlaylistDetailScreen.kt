package app.waveflow.ui.playlists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.Playlist
import app.waveflow.model.Song
import app.waveflow.ui.browse.DetailHeader
import app.waveflow.ui.components.CenteredMessage
import app.waveflow.ui.components.SongRow

/**
 * Détail d'une playlist : en-tête puis morceaux.
 *
 * L'appui long sur une ligne propose de la retirer — c'est l'action attendue
 * ici, là où ailleurs il propose l'ajout à une playlist.
 */
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist?,
    songs: List<Song>,
    nowPlayingId: Long?,
    onSongClick: (Song) -> Unit,
    onRemoveSong: (Song) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    if (playlist == null) {
        Box(modifier = modifier.fillMaxSize()) {
            CenteredMessage(
                message = "Cette playlist n'existe plus.",
                modifier = Modifier.align(Alignment.Center),
            )
        }
        return
    }

    var songToRemove by remember { mutableStateOf<Song?>(null) }

    LazyColumn(
        contentPadding = PaddingValues(bottom = bottomPadding),
        modifier = modifier.fillMaxSize(),
    ) {
        item(key = "header") {
            DetailHeader(
                artworkUri = songs.firstOrNull()?.artworkUri,
                title = playlist.name,
                subtitle = "Playlist",
                trackCount = songs.size,
                durationMs = songs.sumOf { it.durationMs },
                onPlay = onPlay,
                onShuffle = onShuffle,
                playEnabled = songs.isNotEmpty(),
            )
        }

        if (songs.isEmpty()) {
            item(key = "empty") {
                CenteredMessage(
                    message = "Playlist vide. Appuyez longuement sur un morceau, " +
                        "ailleurs dans l'app, pour l'ajouter ici.",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        items(songs, key = { it.id }) { song ->
            SongRow(
                song = song,
                isCurrent = song.id == nowPlayingId,
                onClick = { onSongClick(song) },
                onLongClick = { songToRemove = song },
            )
        }
    }

    songToRemove?.let { song ->
        AlertDialog(
            onDismissRequest = { songToRemove = null },
            title = { Text("Retirer de la playlist") },
            text = { Text("Retirer « ${song.title} » de « ${playlist.name} » ?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRemoveSong(song)
                        songToRemove = null
                    },
                ) {
                    Text("Retirer")
                }
            },
            dismissButton = {
                TextButton(onClick = { songToRemove = null }) { Text("Annuler") }
            },
        )
    }
}
