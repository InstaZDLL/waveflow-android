package app.waveflow.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.waveflow.model.Playlist
import app.waveflow.model.Song
import app.waveflow.ui.components.SheetOption
import app.waveflow.ui.components.WaveFlowSheet
import app.waveflow.ui.trackCountLabel

/**
 * Feuille « Ajouter à une playlist », ouverte par un appui long sur un
 * morceau.
 *
 * Créer une playlist depuis cette feuille y ajoute directement le morceau :
 * c'est le geste attendu quand on part d'un titre plutôt que de l'onglet
 * Playlists.
 */
@Composable
fun AddToPlaylistSheet(
    song: Song,
    playlists: List<Playlist>,
    songCountOf: (Playlist) -> Int,
    onAddTo: (Playlist) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }

    WaveFlowSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Column(modifier = Modifier.padding(horizontal = 24.dp)) {
                Text(
                    text = "Ajouter à une playlist",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(12.dp))

            // Une rangée comme les autres, devant elles : ni boîte d'icône
            // colorée ni accent, que la direction réserve à ce qui marque un état.
            SheetOption(
                text = "Nouvelle playlist",
                interaction = Modifier.clickable { showCreateDialog = true },
                leading = Icons.Filled.Add,
            )

            if (playlists.isNotEmpty()) {
                HorizontalDivider()
            }

            playlists.forEach { playlist ->
                SheetOption(
                    text = playlist.name,
                    interaction = Modifier.clickable {
                        onAddTo(playlist)
                        onDismiss()
                    },
                    supporting = trackCountLabel(songCountOf(playlist)),
                    leading = Icons.AutoMirrored.Filled.PlaylistAdd,
                )
            }
        }
    }

    if (showCreateDialog) {
        PlaylistNameDialog(
            title = "Nouvelle playlist",
            confirmLabel = "Créer",
            onConfirm = { name ->
                onCreateAndAdd(name)
                onDismiss()
            },
            onDismiss = { showCreateDialog = false },
        )
    }
}
