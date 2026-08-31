package app.waveflow.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.waveflow.playback.PlayingTrack
import app.waveflow.ui.components.MediaRow

/**
 * La file d'attente : ce qui a été joué, ce qui joue, ce qui suit.
 *
 * L'ordre affiché est celui de la file telle qu'elle a été posée, et non celui
 * du parcours aléatoire. C'est la liste que l'utilisateur a constituée, et
 * celle qu'il retrouve en coupant la lecture aléatoire ; montrer un ordre tiré
 * au sort lui ferait croire que sa file a été remaniée.
 *
 * Le déplacement se fait par flèches plutôt qu'au glisser-déposer : celui-ci
 * demande une liste qui sache porter le geste, et la file se manipule souvent
 * d'une main, en marchant. Deux boutons touchent mieux qu'une poignée.
 */
@Composable
fun QueueScreen(
    state: PlayerUiState,
    onPlayAt: (Int) -> Unit,
    onMove: (from: Int, to: Int) -> Unit,
    onRemove: (Int) -> Unit,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 0.dp,
) {
    if (state.queue.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "La file est vide",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    LazyColumn(modifier = modifier.fillMaxSize()) {
        itemsIndexed(
            items = state.queue,
            // Le rang fait partie de la clé : un même morceau peut figurer
            // deux fois dans une file, et deux lignes ne peuvent pas partager
            // une identité sans que la liste s'y perde au réordonnancement.
            key = { rang, piste -> "$rang:${piste.mediaId}" },
        ) { rang, piste ->
            QueueRow(
                track = piste,
                isCurrent = rang == state.queueIndex,
                canMoveUp = rang > 0,
                canMoveDown = rang < state.queue.lastIndex,
                onClick = { onPlayAt(rang) },
                onUp = { onMove(rang, rang - 1) },
                onDown = { onMove(rang, rang + 1) },
                onRemove = { onRemove(rang) },
            )
        }
        item { Spacer(Modifier.height(bottomPadding)) }
    }
}

@Composable
private fun QueueRow(
    track: PlayingTrack,
    isCurrent: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        MediaRow(
            artworkUri = track.artworkUri,
            title = track.title,
            subtitle = track.artist ?: "Artiste inconnu",
            onClick = onClick,
            titleColor = if (isCurrent) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier.weight(1f),
        )

        QueueAction(
            icon = Icons.Filled.KeyboardArrowUp,
            description = "Monter ${track.title}",
            enabled = canMoveUp,
            onClick = onUp,
        )
        QueueAction(
            icon = Icons.Filled.KeyboardArrowDown,
            description = "Descendre ${track.title}",
            enabled = canMoveDown,
            onClick = onDown,
        )
        QueueAction(
            icon = Icons.Filled.Close,
            description = "Retirer ${track.title} de la file",
            enabled = true,
            onClick = onRemove,
        )
    }
}

/**
 * Une action de ligne.
 *
 * La description nomme le morceau : trois fois la même étiquette sur une liste
 * ne dirait rien à qui l'écoute plutôt qu'il ne la voit.
 */
@Composable
private fun QueueAction(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.size(ACTION),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

private val ACTION = 40.dp
