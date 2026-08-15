package app.waveflow.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.waveflow.model.orUnknownArtist
import app.waveflow.playback.PlayingTrack
import app.waveflow.playback.RepeatMode
import app.waveflow.ui.components.Artwork
import app.waveflow.ui.formatDuration

/**
 * Lecteur plein écran.
 *
 * Le fond reprend la couleur dominante de la pochette (voir
 * [rememberArtworkAccent]), fondue vers le fond du thème : c'est ce qui donne
 * l'impression que l'écran « habite » l'album en cours.
 */
@Composable
fun NowPlayingScreen(
    state: PlayerUiState,
    onCollapse: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // La file peut se vider pendant l'animation de fermeture : on continue
    // d'afficher le dernier morceau connu le temps que l'écran redescende,
    // plutôt que de le faire disparaître d'un coup.
    var lastKnownTrack by remember { mutableStateOf(state.track) }
    LaunchedEffect(state.track) {
        state.track?.let { lastKnownTrack = it }
    }

    val track = state.track ?: lastKnownTrack ?: return
    val accent = rememberArtworkAccent(track.artworkUri)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(artworkGradient(accent))
            // Le lecteur recouvre la bibliothèque : on absorbe les taps pour
            // qu'ils n'atteignent pas la liste en dessous.
            .pointerInput(Unit) { detectTapGestures {} },
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 24.dp),
        ) {
            PlayerHeader(track = track, onCollapse = onCollapse)

            Spacer(Modifier.weight(1f))

            Artwork(
                artworkUri = track.artworkUri,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .shadow(elevation = 24.dp, shape = RoundedCornerShape(16.dp)),
            )

            Spacer(Modifier.weight(1f))

            TrackTitle(track = track)

            Spacer(Modifier.height(16.dp))

            SeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                trackKey = track.mediaId,
                onSeek = onSeek,
            )

            Spacer(Modifier.height(8.dp))

            PlayerControls(
                isPlaying = state.isPlaying,
                isBuffering = state.isBuffering,
                shuffleEnabled = state.shuffleEnabled,
                repeatMode = state.repeatMode,
                onTogglePlayPause = onTogglePlayPause,
                onSkipNext = onSkipNext,
                onSkipPrevious = onSkipPrevious,
                onToggleShuffle = onToggleShuffle,
                onCycleRepeat = onCycleRepeat,
            )

            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PlayerHeader(track: PlayingTrack, onCollapse: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCollapse) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = "Réduire le lecteur",
            )
        }

        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "EN LECTURE",
                style = MaterialTheme.typography.labelSmall,
                letterSpacing = 1.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = track.album ?: track.source.label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Contrepoids du bouton de gauche pour garder le titre centré.
        Spacer(Modifier.size(48.dp))
    }
}

@Composable
private fun TrackTitle(track: PlayingTrack) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = track.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = track.artist.orUnknownArtist(),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    trackKey: String,
    onSeek: (Long) -> Unit,
) {
    // Pendant un glissement, la position affichée suit le doigt et non le
    // lecteur ; remise à zéro dès qu'on change de morceau.
    var scrubProgress by remember(trackKey) { mutableStateOf<Float?>(null) }

    val hasDuration = durationMs > 0L
    val playedProgress = if (hasDuration) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val displayedProgress = scrubProgress ?: playedProgress
    val displayedPositionMs = if (scrubProgress != null) {
        (displayedProgress * durationMs).toLong()
    } else {
        positionMs
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = displayedProgress,
            onValueChange = { scrubProgress = it },
            onValueChangeFinished = {
                scrubProgress?.let { onSeek((it * durationMs).toLong()) }
                scrubProgress = null
            },
            enabled = hasDuration,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = formatDuration(displayedPositionMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = if (hasDuration) formatDuration(durationMs) else "--:--",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlayerControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    onTogglePlayPause: () -> Unit,
    onSkipNext: () -> Unit,
    onSkipPrevious: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToggleControl(
            icon = Icons.Filled.Shuffle,
            contentDescription = if (shuffleEnabled) "Désactiver la lecture aléatoire" else "Activer la lecture aléatoire",
            active = shuffleEnabled,
            onClick = onToggleShuffle,
        )

        IconButton(onClick = onSkipPrevious) {
            Icon(
                imageVector = Icons.Filled.SkipPrevious,
                contentDescription = "Morceau précédent",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(36.dp),
            )
        }

        // Le bouton reste en place et garde sa taille pendant l'attente : sa
        // disparition ferait sauter toute la rangée de commandes.
        FilledIconButton(
            onClick = onTogglePlayPause,
            enabled = !isBuffering,
            shape = CircleShape,
            modifier = Modifier.size(72.dp),
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .size(28.dp)
                        .semantics { contentDescription = "Chargement du morceau" },
                )
            } else {
                Icon(
                    imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Lecture",
                    modifier = Modifier.size(36.dp),
                )
            }
        }

        IconButton(onClick = onSkipNext) {
            Icon(
                imageVector = Icons.Filled.SkipNext,
                contentDescription = "Morceau suivant",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(36.dp),
            )
        }

        ToggleControl(
            icon = if (repeatMode == RepeatMode.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            contentDescription = when (repeatMode) {
                RepeatMode.Off -> "Activer la répétition de la file"
                RepeatMode.All -> "Répéter uniquement ce morceau"
                RepeatMode.One -> "Désactiver la répétition"
            },
            active = repeatMode != RepeatMode.Off,
            onClick = onCycleRepeat,
        )
    }
}

/** Bouton secondaire dont la teinte signale l'état actif. */
@Composable
private fun ToggleControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    val tint: Color = if (active) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    IconButton(onClick = onClick) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint)
    }
}
