package app.waveflow.ui.playlists

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.waveflow.model.Playlist

/**
 * Menu de la barre de titre sur l'écran d'une playlist : renommer, supprimer.
 *
 * Il porte lui-même ses boîtes de dialogue, pour que l'assemblage racine n'ait
 * qu'à le placer et à recevoir les deux actions.
 */
@Composable
fun PlaylistMenu(
    playlist: Playlist,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    IconButton(onClick = { menuExpanded = true }) {
        Icon(
            imageVector = Icons.Filled.MoreVert,
            contentDescription = "Options de la playlist",
        )
    }

    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(
            text = { Text("Renommer") },
            onClick = {
                menuExpanded = false
                showRenameDialog = true
            },
        )
        DropdownMenuItem(
            text = { Text("Supprimer") },
            onClick = {
                menuExpanded = false
                showDeleteDialog = true
            },
        )
    }

    if (showRenameDialog) {
        PlaylistNameDialog(
            title = "Renommer la playlist",
            confirmLabel = "Renommer",
            initialName = playlist.name,
            onConfirm = onRename,
            onDismiss = { showRenameDialog = false },
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Supprimer la playlist") },
            text = { Text("Supprimer « ${playlist.name} » ? Les morceaux restent sur l'appareil.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Annuler") }
            },
        )
    }
}
