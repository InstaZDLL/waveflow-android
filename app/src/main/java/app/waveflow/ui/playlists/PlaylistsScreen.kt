package app.waveflow.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.model.Playlist
import app.waveflow.model.Song
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.components.CenteredMessage
import app.waveflow.ui.trackCountLabel

/** Onglet Playlists : création et accès aux playlists locales. */
@Composable
fun PlaylistsScreen(
    state: PlaylistsUiState,
    onPlaylistClick: (Playlist) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        if (state.isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = bottomPadding),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "create") {
                    CreatePlaylistRow(onClick = { showCreateDialog = true })
                }

                if (state.playlists.isEmpty()) {
                    item(key = "empty") {
                        CenteredMessage(
                            message = "Aucune playlist. Créez-en une, puis appuyez longuement " +
                                "sur un morceau pour l'y ajouter.",
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }

                items(state.playlists, key = { it.id }) { playlist ->
                    PlaylistRow(
                        playlist = playlist,
                        songs = state.songs(playlist.id),
                        onClick = { onPlaylistClick(playlist) },
                    )
                }
            }
        }
    }

    if (showCreateDialog) {
        PlaylistNameDialog(
            title = "Nouvelle playlist",
            confirmLabel = "Créer",
            onConfirm = onCreatePlaylist,
            onDismiss = { showCreateDialog = false },
        )
    }
}

@Composable
private fun CreatePlaylistRow(onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(6.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Nouvelle playlist",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    songs: List<Song>,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // La pochette du premier morceau tient lieu de visuel de playlist.
        Artwork(
            artworkUri = songs.firstOrNull()?.artworkUri,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = trackCountLabel(songs.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
