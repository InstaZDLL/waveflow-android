package app.waveflow.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.waveflow.model.Playlist
import app.waveflow.model.Song
import app.waveflow.ui.trackCountLabel

/**
 * Feuille « Ajouter à une playlist », ouverte par un appui long sur un
 * morceau.
 *
 * Créer une playlist depuis cette feuille y ajoute directement le morceau :
 * c'est le geste attendu quand on part d'un titre plutôt que de l'onglet
 * Playlists.
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    ModalBottomSheet(onDismissRequest = onDismiss) {
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

            SheetRow(
                icon = Icons.Filled.Add,
                title = "Nouvelle playlist",
                subtitle = null,
                highlighted = true,
                onClick = { showCreateDialog = true },
            )

            if (playlists.isNotEmpty()) {
                HorizontalDivider()
            }

            playlists.forEach { playlist ->
                SheetRow(
                    icon = Icons.AutoMirrored.Filled.PlaylistAdd,
                    title = playlist.name,
                    subtitle = trackCountLabel(songCountOf(playlist)),
                    highlighted = false,
                    onClick = {
                        onAddTo(playlist)
                        onDismiss()
                    },
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

@Composable
private fun SheetRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val tint = if (highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = tint)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = tint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
